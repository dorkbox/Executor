/*
 * Copyright 2023 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dorkbox.executor

import dorkbox.executor.Executor.Companion.IO_DISPATCH
import dorkbox.executor.exceptions.InvalidExitValueException
import dorkbox.executor.listener.ProcessListener
import dorkbox.executor.processResults.AsyncProcessOutput
import dorkbox.executor.processResults.ProcessResult
import dorkbox.executor.processResults.SyncProcessResult
import dorkbox.executor.stop.ProcessStopper
import dorkbox.executor.stream.IOStreamHandler
import dorkbox.executor.stream.PumpStreamHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.*
import kotlin.text.Charsets.UTF_8



internal data class Params(
        /**
         * Set of main attributes used to start the process.
         */
        val processAttributes: ProcessAttributes,

        /**
         * Helper for stopping the process in case of interruption.
         */
        val stopper: ProcessStopper,

        /**
         * Process event listener (not `null`).
         */
        val listener: ProcessListener,

        /**
         * Used to handle input and output stream of subprocesses.
         */
        val streams: IOStreamHandler,

        /**
         * Logger for logging messages about starting and waiting for the processes.
         */
        val logger: Logger?,

        /**
         * ONLY called if there is an exception while waiting for the process to complete.
         */
        val errorMessageHandler: (StringBuilder) -> Unit,

    val closeTimeout: Long, val closeTimeoutUnit: TimeUnit,

    val asyncProcessStart: Boolean)


class DeferredProcessResult internal constructor(private val process: Process,
                                                 private val params: Params,
                                                 private val createProcessResults: (Long, Int) -> SyncProcessResult) {

    companion object {
        private val EOL = "\n".toByteArray(UTF_8)
        private val log = LoggerFactory.getLogger(DeferredProcessResult::class.java)

        /**
         * In case [InvalidExitValueException] is thrown and we have read the process output we include the output up to this length
         * in the error message.
         *
         * If the output is longer we truncate it.
         */
        private const val MAX_OUTPUT_SIZE_IN_ERROR_MESSAGE = 5000

        /**
         * Check the process exit value.
         */
        internal fun checkExit(attributes: ProcessAttributes, result: ProcessResult) {
            val allowedExitValues = attributes.allowedExitValues
            val exitValue = result.exitValue

            if (allowedExitValues.isNotEmpty() && !allowedExitValues.contains(exitValue)) {
                val sb = StringBuilder()

                sb.append("Unexpected exit value: ")
                    .append(exitValue)

                sb.append(", allowed exit values: ")
                    .append(allowedExitValues)

                if (result.hasOutput && result is SyncProcessResult) {
                    addExceptionMessageSuffix(attributes, sb, result.output.string())
                }

                throw InvalidExitValueException(sb.toString(), result)
            }
        }

        internal fun addExceptionMessageSuffix(attributes: ProcessAttributes, sb: StringBuilder, outputText: String) {
            sb.append(", executed command ")
                .append(attributes.command)

            if (attributes.directory != null) {
                sb.append(" in directory ")
                    .append(attributes.directory)
            }

            if (attributes.environment.isNotEmpty()) {
                sb.append(" with environment ")
                    .append(attributes.environment)
            }

            val length = outputText.length

            if (length <= MAX_OUTPUT_SIZE_IN_ERROR_MESSAGE) {
                sb.append(", output was ")
                    .append(length)
                    .append(" bytes:\n")
                    .append(outputText.trim())
            } else {
                sb.append(", output was ")
                    .append(length)
                    .append(" bytes (truncated):\n")

                val halfLimit = MAX_OUTPUT_SIZE_IN_ERROR_MESSAGE / 2
                sb.append(outputText.substring(0, halfLimit))
                    .append("\n...\n")
                    .append(outputText.substring(length - halfLimit).trim())
            }
        }
    }

    private val waiter = SuspendNotifier()
    private val launchingThread = Thread.currentThread()


    @Volatile
    var calledAwait = false

    @Volatile
    var waiting = false

    @Volatile
    lateinit var job: Job

    @Volatile
    lateinit var thread: Thread

    @Volatile
    private var processException: Throwable? = null

    @Volatile
    var processResult: SyncProcessResult? = null

    // Make sure that our process is destroyed if the JVM is shutdown while it is still running.
    private val shutdownHook: Thread

    init {
        shutdownHook = Thread({ process.destroyForcibly() })

        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    /**
     * Starts the process. this is always called.
     */
    @ExperimentalCoroutinesApi
    fun start() {
        // Preserve the MDC context of the caller thread.
        val contextMap: Map<String, String>? = MDC.getCopyOfContextMap()

        val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            processException = throwable
        }

        job = IO_DISPATCH.launch(coroutineExceptionHandler) {
            // BY DESIGN, coroutines are not meant to be interrupted, however we are VERY specifically only interrupting an
            // IO coroutine/thread (which is allowed for this to happen)
            // see: https://discuss.kotlinlang.org/t/calling-blocking-code-in-coroutines/2368/6

            thread = Thread.currentThread()

            try {
                processResult = if (contextMap == null) {
                    // @throws IOException an error occurred when process was started or stopped.
                    // @throws InvalidExitValueException if there is an invalid exit value
                    val exitCode = waitForProcessToComplete()
                    finishWaiting(exitCode)
                } else {
                    MDC.setContextMap(contextMap)
                    try {
                        // @throws IOException an error occurred when process was started or stopped.
                        // @throws InvalidExitValueException if there is an invalid exit value
                        val exitCode = waitForProcessToComplete()
                        finishWaiting(exitCode)
                    } finally {
                        MDC.clear()
                    }
                }
            } catch (exception: Exception) {
                processException = exception
            } finally {
                try {
                    // always do this
                    waiter.doNotify()
                } catch (ignored: Exception) {
                    // we want to ignore any cancellation exceptions
                }
            }
        }
    }


    /**
     * Awaits indefinitely for the process to finish running, without blocking a thread and resumes when process is done,
     * returning the [ProcessResult] containing exit code and (optionally) process output.
     *
     * This suspending function is cancellable.
     *
     * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting, this function
     * immediately resumes with [CancellationException].
     *
     * @throws TimeoutException if the process has been timed out while running
     * @throws CancellationException if the process has been cancelled while running
     */
    suspend fun await(): SyncProcessResult {
        return await(0L)
    }

    suspend fun await(timeoutInMs: Long): SyncProcessResult {
        return await(timeoutInMs, TimeUnit.MILLISECONDS)
    }

    fun awaitBlocking(): SyncProcessResult {
        return runBlocking {
            withContext(Dispatchers.IO) {
                await()
            }
        }
    }

    fun awaitBlocking(timeoutInMs: Long): SyncProcessResult {
        return runBlocking {
            withContext(Dispatchers.IO) {
                await(timeoutInMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun awaitBlocking(timeout: Long, timeoutUnit: TimeUnit): SyncProcessResult {
        return runBlocking {
            withContext(Dispatchers.IO) {
                await(timeout, timeoutUnit)
            }
        }
    }

    /**
     * @throws TimeoutException if the process has been timed out while running
     * @throws CancellationException if the process has been cancelled while running
     */
    @Suppress("NAME_SHADOWING")
    suspend fun await(timeout: Long, timeoutUnit: TimeUnit): SyncProcessResult {
        // a timeout of 0 means to wait forever, however we still want to be able to cancel this process, which is the ENTIRE point
        // of having a Deferred ProcessResult.  We cannot *realistically* have it "wait forever, so we really an absurdly long time
        var timeout = timeout
        var timeoutUnit = timeoutUnit

        if (timeout == 0L) {
            timeout = Long.MAX_VALUE
            timeoutUnit = TimeUnit.DAYS
        }

        calledAwait = true
        waiting = true

        // wait for our timeout, then get the result, if it exists.
        withTimeoutOrNull(timeoutUnit.toMillis(timeout)) {
            try {
                waiter.doWait()
            } catch (ignored: Exception) {
                // we want to ignore any cancellation exceptions
            }
        }


        waiting = false

        if (processResult != null) {
            // we know this cannot be reassigned.
            return processResult!!
        }

        var exception: Throwable? = processException
        if (exception == null) {
            exception = newTimeoutException(process = process, processStackTrace = thread.stackTrace,
                                            timeout = timeout, timeoutUnit = timeoutUnit)
        }

        // clean the stack trace. This is kind-of dumb to have to do this...
        // NOTE: we CANNOT get the location within the calling class suspend function, but we can get the START of the suspend function
        val stackTrace = Thread.currentThread().stackTrace
        val caller = stackTrace[2].className
        val newTrace = mutableListOf<StackTraceElement>()
        var foundCaller = false
        var doneWithCoroutineStack = false

        stackTrace.forEach {
            if (foundCaller) {
                if (doneWithCoroutineStack) {
                    newTrace.add(it)
                }
                else {
                    // remove all of the kotlin coroutine stack trace info (why would someone ever need to debug coroutines themselves?)
                    if (!it.className.startsWith("kotlin.coroutines") && !it.className.startsWith("kotlinx.coroutines")) {
                        doneWithCoroutineStack = true
                        newTrace.add(it)
                    }
                }
            } else if (it.className == caller) {
                // cleanup the stack elements which create the stacktrace
                foundCaller = true
            }
        }

        exception.stackTrace = newTrace.toTypedArray()

        job.cancel()
        thread.interrupt()

        throw exception
    }


    /**
     * Waits for the process to complete.
     *
     * Will block until the process is done running, will suspend while trying to close the streams for the process
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InvalidExitValueException if there is an invalid exit value
     * @throws InterruptedException
     */
    private suspend fun waitForProcessToComplete(): Int {
        val exit: Int
        var finished = false
        try {
            // blocks this thread until the process is done running
            @Suppress("BlockingMethodInNonBlockingContext")
            exit = process.waitFor()

            finished = true

            LogHelper.logAtLowestLevel(params.logger, "{} stopped with exit code {}", this, exit)
        } finally {
            if (!finished) {
                LogHelper.logAtLowestLevel(params.logger, "Stopping {}...", this)
                params.stopper.stop(process)
            }

            // Helper for closing the process' standard streams.
            if (params.closeTimeout == 0L) {
                params.streams.stop(process, finished)
            } else {
                // Only waits a fixed period for the closing.
                //
                // On timeout a warning is logged but no error is thrown.
                // This is primarily used on Windows where sometimes sub process' streams do not close properly.

                try {
                    // IO is used because it will generate as many threads as necessary (in case one thread blocks forever).
                    IO_DISPATCH.launch {
                        withTimeout(params.closeTimeoutUnit.toMillis(params.closeTimeout)) {
                            params.streams.stop(process, finished)
                        }
                    }
                } catch (e: ExecutionException) {
                    throw IllegalStateException("Could not close streams of $process", e.cause)
                } catch (e: TimeoutCancellationException) {
                    log.warn("Could not close streams of $process in ${params.closeTimeout} ${getUnitsAsString(params.closeTimeout, params.closeTimeoutUnit)}")
                }
            }
        }

        return exit
    }

    @ExperimentalCoroutinesApi
    private suspend fun finishWaiting(exitCode: Int): SyncProcessResult {
        return try {
            val result = if (params.asyncProcessStart) {
                // if we are async, then we have to read all of the data into a bytearray, since we are NO LONGER going to be reading it.
                // as this point, the thread has ended, so there is no more data being pumped. MAYBE this data has finished being read, maybe not...
                val out = ByteArrayOutputStream()

                val channel: Channel<Byte> = (params.streams as PumpStreamHandler).channel

                if (calledAwait) {
                    // if we called await(), then save up the extra data (since calls to await() can also return data)

                    while (!channel.isEmpty) {
                        out.write(channel.receive().toInt())
                    }
                }

                channel.close()

                // we have a new output, since we had to read it from the async channel
                SyncProcessResult(PidHelper.get(process), exitCode, out.toByteArray())
            } else {
                createProcessResults(PidHelper.get(process), exitCode)
            }

            checkExit(params.processAttributes, result)
            params.listener.afterFinish(process, result)
            result
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
            // Invoke listeners - regardless process finished or got cancelled
            params.listener.afterStop(process)
        }
    }

    val output: AsyncProcessOutput by lazy {
        if (params.asyncProcessStart) {
            val channel = (params.streams as PumpStreamHandler).channel
            AsyncProcessOutput(channel, processResult)
        } else {
            throw IllegalArgumentException("Cannot get synchronous output, the process must be started asynchronously (something is wrong!)")
        }
    }

    /**
     * Gets the PID for the currently running process. This doesn't make sense for remotely executed processes (which return 0)
     *
     *  SOMETIMES, this PID is invalid because it can be recycled by linux!
     * see: http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6469606
     *
     * @return 0 if there is no PID (failure to start the process), or -1 if getting the pid is not possible
     */
    val pid = PidHelper.get(process)

    /**
     * Writes the string to the process and send EOL in a safe way
     */
    fun writeLine(command: String) {
        val outputStream = process.outputStream
        outputStream.write(command.toByteArray(UTF_8))
        outputStream.write(EOL)
        try { outputStream.flush() } catch (ignored: Exception) {}
    }

    /**
     * Writes the string to the process in a safe way
     */
    fun write(command: String) {
        write(command.toByteArray(UTF_8))
    }

    /**
     * Writes the bytes to the process in a safe way
     */
    fun write(bytes: ByteArray) {
        process.outputStream.write(bytes)
        flush()
    }

    /**
     * Flushes the output stream to the process.
     */
    fun flush() {
        try { process.outputStream.flush() } catch (ignored: Exception) {}
    }

    /**
     * Cancel waiting for this process to complete
     *
     * @throws IllegalStateException if this process as not be "started" via await()
     */
    fun cancel(message: String = "", cause: Throwable? = null) {
        if (!waiting) {
            throw IllegalStateException("Unable to cancel a process is not waiting.")
        }

        val sb = StringBuilder()

        if (message.isEmpty()) {
            sb.append("Process [pid=${PidHelper.get(process)}] has been cancelled")
        } else {
            sb.append(message)
        }

        params.errorMessageHandler(sb)

        val exception = CancellationException(sb.toString(), cause)
        exception.stackTrace = launchingThread.stackTrace
        processException = exception

        waiter.cancel()
    }

    private fun newTimeoutException(process: Process,
                                    processStackTrace: Array<StackTraceElement>,
                                    timeout: Long,
                                    timeoutUnit: TimeUnit): TimeoutException {

        val sb = StringBuilder()
        val exitValue = getExitCodeOrNull(process)

        // set a generic "timed out" exception message
        if (exitValue == null) {
            sb.append("Timed out waiting for ")
                .append(process)
                .append(" to finish")
        }
        else {
            sb.append("Timed out finishing ")
                .append(process)
            sb.append(", exit value: ")
                .append(exitValue)
        }

        sb.append(", timeout: ")
            .append(timeout)
            .append(" ")
            .append(getUnitsAsString(timeout, timeoutUnit))


        params.errorMessageHandler(sb)

        val newException = TimeoutException(sb.toString())
        if (exitValue != null) {
            val cause = Exception("Stack dump of worker thread.")
            cause.stackTrace = processStackTrace
            newException.initCause(cause)
        }

        return newException
    }

    private fun getUnitsAsString(timeout: Long, timeUnit: TimeUnit): String {
        val result = when (timeUnit) {
            TimeUnit.NANOSECONDS -> "nano"
            TimeUnit.MICROSECONDS -> "micro"
            TimeUnit.MILLISECONDS -> "milli"
            TimeUnit.SECONDS -> "second"
            TimeUnit.MINUTES -> "minute"
            TimeUnit.HOURS -> "hour"
            TimeUnit.DAYS -> "day"
        }
        return if (timeout > 1L) {
            // fix plurality
            result + "s"
        } else {
            result
        }
    }

    private fun getExitCodeOrNull(process: Process?): Int? {
        return try {
            process!!.exitValue()
        } catch (e: IllegalThreadStateException) {
            null
        }
    }
}
