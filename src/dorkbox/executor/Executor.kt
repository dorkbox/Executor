/*
 * Copyright 2022 dorkbox, llc
 * Copyright (C) 2014 ZeroTurnaround <support@zeroturnaround.com>

 * Contains fragments of code from Apache Commons Exec, rights owned
 * by Apache Software Foundation (ASF).
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

import dorkbox.executor.exceptions.InvalidExitValueException
import dorkbox.executor.exceptions.ProcessInitException
import dorkbox.executor.listener.CompositeProcessListener
import dorkbox.executor.listener.DestroyerListenerAdapter
import dorkbox.executor.listener.ProcessDestroyer
import dorkbox.executor.listener.ProcessListener
import dorkbox.executor.listener.ShutdownHookProcessDestroyer
import dorkbox.executor.processResults.NopProcessResult
import dorkbox.executor.processResults.ProcessResult
import dorkbox.executor.processResults.SyncProcessResult
import dorkbox.executor.stop.DestroyProcessStopper
import dorkbox.executor.stop.NopProcessStopper
import dorkbox.executor.stop.ProcessStopper
import dorkbox.executor.stream.CallerLoggerUtil
import dorkbox.executor.stream.IOStreamHandler
import dorkbox.executor.stream.NopPumpStreamHandler
import dorkbox.executor.stream.nopStreams.NopInputStream
import dorkbox.executor.stream.nopStreams.NopOutputStream
import dorkbox.executor.stream.slf4j.Slf4jStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.*



/**
 * Helper for executing a process.
 *
 *
 * It's implemented as a wrapper of [ProcessBuilder] complementing it with additional features such as:
 *
 *
 *  * Handling process streams (copied from Commons Exec library).
 *  * Destroying process on VM exit (copied from Commons Exec library).
 *  * Checking process exit code.
 *  * Setting a timeout for running the process and automatically stopping it in case of timeout.
 *  * Either waiting for the process to finish ([.execute]) or returning a [Future] ([.start].
 *  * Reading the process output stream into a buffer ([.readOutput], [ProcessResult]).
 *
 *
 *
 * The default configuration for executing a process is following:
 *
 *  * Process is not automatically destroyed on VM exit.
 *  * Error stream is redirected to its output stream. Use [.redirectErrorStream] to override it.
 *  * Output stream is pumped to a [NopOutputStream], Use [.streams], [.redirectOutput], or any of the `redirectOutputAs*` methods.to override it.
 *  * Any exit code is allowed. Use [.exitValues] to override it.
 *  * In case of timeout or cancellation [Process.destroy] is invoked.
 *
 *
 * NOTE: If you are launching using the same classpath as before, and you set the main-classpath to be executed as a
 *     "single file source code" file via java11+, YOU CAN GET THE FOLLOWING ERROR.
 *
 *     "error: class found on application class path .... "
 *
 *     What this REALLY means is:
 *
 *     "error: A compiled class <fully qualified class name> already exists on
 *     the application classpath and as a result the same class cannot be used
 *     as a source for launching single-file source code program".
 *
 *     https://mail.openjdk.java.net/pipermail/jdk-dev/2018-June/001438.html
 *
 *
 * @author Rein Raudjärv, Nathan Robinson
 * @see ProcessResult
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
open class Executor {
    companion object {
        /**
         * Gets the version number.
         */
        const val version = "3.7"

        val log = LoggerFactory.getLogger(Executor::class.java)!!
        val IS_OS_WINDOWS: Boolean
        val IS_OS_MAC: Boolean

        val DEFAULT_EXIT_VALUES: Array<Int>? = null
        private const val NORMAL_EXIT_VALUE = 0

        const val DEFAULT_REDIRECT_ERROR_STREAM = true

        internal val IO_DISPATCH = CoroutineScope(Dispatchers.IO)
        private val LINE_SEPARATOR = System.getProperty("line.separator")

        private val EXTRA_SPACE_REGEX = "\\s+".toRegex()

        // what is the detected shell for this OS?
        private var DEFAULT_SHELL: String? = null

        init {
            // cannot use any dependencies!
            val osName = System.getProperty("os.name").lowercase()
            IS_OS_WINDOWS = osName.startsWith("win")
            IS_OS_MAC = osName.startsWith("mac") || osName.startsWith("darwin")

            // Add this project to the updates system, which verifies this class + UUID + version information
            dorkbox.updates.Updates.add(Executor::class.java, "03fcf3762a2b4f68b5e968aaf79f3a72", version)
        }

        /**
         * Fixes the command line arguments on Windows by replacing empty arguments with `""`. Otherwise these arguments would be just skipped.
         *
         * See:
         * http://bugs.java.com/view_bug.do?bug_id=7028124
         * https://bugs.openjdk.java.net/browse/JDK-6518827
         */
        internal fun fixArguments(command: Iterable<String>): MutableList<String> {
            if (!IS_OS_WINDOWS) {
                return command.toMutableList()
            }

            val result = mutableListOf<String>().apply{ addAll(command) }
            val it: MutableListIterator<String> = result.listIterator()

            while (it.hasNext()) {
                if ("" == it.next()) {
                    it.set("\"\"")
                }
            }
            return result
        }


        /**
         * Quickly run, then read the output of a process. This is very simple. For advanced usage, use the full API.
         */
        fun run(command: Iterable<String>): String {
            return Executor()
                .command(command)
                .enableRead()
                .startBlocking()
                .output.utf8()
        }

        /**
         * Quickly run, then read the output of a process. This is very simple. For advanced usage, use the full API.
         */
        fun run(executable: File, args: Iterable<String>): String {
            return Executor()
                .executable(executable)
                .command(args)
                .enableRead()
                .startBlocking()
                .output.utf8()
        }

        /**
         * Quickly run, then read the output of a process. This is very simple. For advanced usage, use the full API.
         */
        fun run(vararg command: String): String {
            return Executor()
                .command(listOf(*command))
                .enableRead()
                .startBlocking()
                .output.utf8()
        }

        /**
         * Quickly run, then read the output of a process. This is very simple. For advanced usage, use the full API.
         */
        fun run(executable: File, vararg args: String): String {
            return Executor()
                .executable(executable)
                .command(listOf(*args))
                .enableRead()
                .startBlocking()
                .output.utf8()
        }
    }

    /**
     * Process builder used by this executor.
     */
    internal val builder = ProcessBuilder()

    /**
     * Environment variables which are added (removed in case of `null` values) to the process being started.
     */
    val environment: MutableMap<String, String?> = LinkedHashMap()


    /**
     * Execute this command as JAVA, using the same JVM as the currently running JVM, as a forked process.
     */
    private var jvmExecOptions: JvmExecOptions? = null


    /**
     * Execute this command on a remote host via SSH
     */
    private var sshExecOptions: SshExecOptions? = null

    /**
     * Execute this command a shell command (bash/cmd/etc), instead of directly invoking the command as a forked process.
     */
    private var executeAsShell: Boolean = false

    /**
     * Locations to be added to the path when running this executable.
     *
     * When paths are added to the path, [executeAsShell] will be set to true
     *
     * It is important to note, that only setting the path system environment variable WILL NOT affect the path of the running executable!
     */
    private var pathsToPrepend: MutableList<String> = mutableListOf()

    /**
     * Set of accepted exit codes or `null` if all exit codes are allowed.
     */
    private var allowedExitValues: Set<Int>? = null

    /**
     * Helper for stopping the process in case of timeout or cancellation.
     */
    private var stopper: ProcessStopper = NopProcessStopper.INSTANCE

    /**
     * Process stream Handler (copied from Commons Exec library). If [NopPumpStreamHandler] then streams are not handled.
     */
    private var streams: IOStreamHandler = NopPumpStreamHandler()

    /**
     * Timeout for closing process' standard streams. In case this timeout is reached we just log a warning but don't throw an error.
     */
    private var closeTimeout: Long = 0L
    private var closeTimeoutUnit: TimeUnit = TimeUnit.SECONDS

    /**
     * `true` if the process output should be read to a buffer and returned by [SyncProcessResult] or [DeferredProcessResult]
     */
    private var readOutput = false

    /**
     * Sets the new process to inherit the current Java process source and destination I/O stream
     */
    private var inheritIO = false

    /**
     * By default, there is only 1 thread for pumping I/O (as necessary) between processes. This setting enables
     * 1 thread per I/O stream that will be pumped
     */
    private var highPerformanceIO = false

    /**
     * Process event handlers.
     */
    private val listeners = CompositeProcessListener()

    /**
     * Helper for logging messages about starting and waiting for the processes.
     *
     * see http://logback.qos.ch/manual/architecture.html for more info
     *  logger order goes (from lowest to highest) TRACE->DEBUG->INFO->WARN->ERROR->OFF
     */
    private var logger: Logger? = null

    /**
     * Capture a snapshot of this process executor's main state.
     */
    private val attributes: ProcessAttributes
        // make a copy
        get() {
            val allowedExit = allowedExitValues

            return if (allowedExit != null) {
                ProcessAttributes(getCommand(),
                                  getWorkingDirectory(),
                                  LinkedHashMap(environment),
                                  allowedExit.toSet())
            } else {
                ProcessAttributes(getCommand(), getWorkingDirectory(), LinkedHashMap(environment))
            }
        }

    private val executingMessageParams: String
        get() {
            var result = builder.command().joinToString(separator = " ")
            if (builder.directory() != null) {
                result += " in " + builder.directory()
            }
            if (environment.isNotEmpty()) {
                result += " with environment $environment"
            }

            return result
        }

    /**
     * Creates new [Executor] instance.
     */
    constructor()

    /**
     * Creates new [Executor] instance for the given program and its arguments.
     *
     * @param command The iterable containing the program and its arguments.
     */
    constructor(command: Iterable<String>) {
        command(command)
    }

    /**
     * Creates new [Executor] instance for the given program and its arguments.
     *
     * @param command A string array containing the program and its arguments.
     */
    constructor(vararg command: String) {
        command(*command)
    }

    init {
        // Run in case of any constructor
        exitValueAny()

        stopper(DestroyProcessStopper.INSTANCE)
        redirectOutput(null)
        redirectError(null)
        destroyer(null)

        redirectErrorStream(DEFAULT_REDIRECT_ERROR_STREAM)
    }


    /**
     * Returns this process executor's operating system program and arguments.
     *
     * The returned list is a copy.
     *
     * @return this process executor's program and its arguments (not `null`).
     */
    open fun getCommand(): List<String> {
        return ArrayList(builder.command())
    }

    /**
     * Sets the program and it's arguments which are being executed.
     *
     * @param command The iterable containing the program and its arguments.
     *
     * @return This process executor.
     */
    fun command(command: Iterable<String>): Executor {
        val list = command.map { it }
        builder.command().addAll(fixArguments(list))
        return this
    }

    /**
     * Sets the program and its arguments which are being executed.
     *
     * @param command A string array containing the program and its arguments.
     *
     * @return This process executor.
     */
    fun command(vararg command: String): Executor {
        builder.command().addAll(fixArguments(listOf(*command)))
        return this
    }

    /**
     * Sets the program and its arguments which are being executed.
     *
     * @param command A file that is the executable
     * @param args A string array containing the arguments.
     *
     * @return This process executor.
     */
    fun command(command: File, vararg args: String): Executor {
        val combined = listOf(command.absolutePath, *args)
        builder.command().addAll(fixArguments(combined))
        return this
    }

    /**
     * Splits string by spaces and passes it to [Executor.command]
     *
     * Note: this method does not handle whitespace escaping,
     *   `"mkdir new\ folder"` would be interpreted as `{"mkdir", "new\", "folder"}` command.
     *
     * @param commandWithArgs A string array containing the program and its arguments.
     *
     * @return This process executor.
     */
    fun commandSplit(commandWithArgs: String): Executor {
        builder.command().addAll(commandWithArgs.split(EXTRA_SPACE_REGEX))
        return this
    }

    /**
     * Sets the command which will be executed. This allows for the executable and arguments to be set separately.
     *
     * @return This process executor.
     */
    fun executable(exe: String): Executor  {
        builder.command().add(0, exe)
        return this
    }

    /**
     * Sets the command which will be executed. This allows for the executable and arguments to be set separately.
     *
     * @return This process executor.
     */
    fun executable(exe: File): Executor {
        builder.command().add(0, exe.absolutePath)
        return this
    }

    /**
     * @return The executable assigned to this command. If only arguments are set, this will return the first argument.
     */
    fun getExecutable(): String {
        val command = builder.command()
        if (command.isEmpty()) {
            return ""
        }

        return command[0]
    }

    /**
     * Add arguments to an existing command, which will be executed.
     *
     * This does not replace commands, it adds to them
     *
     * @param arguments A string array containing the program and/or its arguments.
     *
     * @return This process executor.
     */
    fun addArg(vararg arguments: String): Executor {
        val fixed = fixArguments(listOf(*arguments))
        builder.command().addAll(fixed)
        return this
    }

    /**
     * Add arguments to an existing command, which will be executed.
     *
     * This does not replace commands, it adds to them
     *
     * @param arguments A string array containing the program and/or its arguments.
     *
     * @return This process executor.
     */
    fun addArg(arguments: Iterable<String>): Executor {
        val fixed = fixArguments(arguments)
        builder.command().addAll(fixed)
        return this
    }

    /**
     * @return the list of arguments assigned to the executable
     */
    fun getArgs(): List<String> {
        val command = builder.command()

        return if (command.size <= 1) {
            listOf()
        } else {
            command.subList(1, command.size)
        }
    }

    /**
     * Returns this process executor's working directory.
     *
     *
     * Subprocesses subsequently started by this object will use this as their working directory.
     *
     * The returned value may be `null` -- this means to use the working directory of the current Java process, usually the
     * directory named by the system property `user.dir` as the working directory of the child process.
     *
     * @return this process executor's working directory
     */
    fun getWorkingDirectory(): File? {
        return builder.directory()
    }


    /**
     * Sets this working directory for the process being executed.
     * The argument may be `null` -- this means to use the
     * working directory of the current Java process, usually the
     * directory named by the system property `user.dir`,
     * as the working directory of the child process.
     *
     * @param directory The new working directory
     *
     * @return This process executor.
     */
    fun workingDirectory(directory: File?): Executor {
        builder.directory(directory)
        return this
    }

    /**
     * Sets this working directory for the process being executed.
     * The argument may be `null` -- this means to use the
     * working directory of the current Java process, usually the
     * directory named by the system property `user.dir`,
     * as the working directory of the child process.
     *
     * @param directory The new working directory
     *
     * @return This process executor.
     */
    fun workingDirectory(directory: String?): Executor {
        if (directory != null) {
            builder.directory(File(directory))
        } else {
            builder.directory(directory)
        }
        return this
    }

    /**
     * Adds additional environment variables for the process being executed.
     *
     * @param env environment variables added to the process being executed.
     *
     * @return This process executor.
     */
    fun environment(env: Map<String, String?>): Executor {
        environment.putAll(env)
        return this
    }

    /**
     * Adds a single additional environment variable for the process being executed.
     *
     * @param name name of the environment variable added to the process being executed.
     * @param value value of the environment variable added to the process being executed.
     *
     * @return This process executor.
     */
    fun environment(name: String, value: String?): Executor {
        environment[name] = value
        return this
    }

    /**
     * Copies the current system environment into the executable's environment.
     *
     * Normally the system environment is not used for execution
     *
     * @return This process executor.
     */
    fun useSystemEnvironment(): Executor {
        environment.putAll(System.getenv())
        return this
    }

    /**
     * Adds the specified path (or paths) to the command getting executed.  This will ALSO enable [executeAsShell], as the only way
     * to add a path for the executed process, is to run as a subshell/fork.
     */
    fun addPath(vararg pathsToAdd: String): Executor {
        pathsToPrepend.addAll(pathsToAdd)
        executeAsShell = true
        return this
    }


    /**
     * It is possible, however unlikely (and never casually noticed), that the
     * PATH environment variable can be "PATH", "Path", or "path" -- all depending
     * on what is set.
     *
     * This will search for all three cases in the following precedence: PATH -> Path -> path
     *
     * @return the system path environment variable
     */
    fun getSystemPath(): String {
        val systemEnv = System.getenv()

        return systemEnv["PATH"] ?: systemEnv["Path"] ?: systemEnv["path"] ?: ""
    }

    /**
     * Sets this process executor's `redirectErrorStream` property.
     *
     *
     * If this property is `true`, then any error output generated by subprocesses will be merged with the standard output.
     * This makes it easier to correlate error messages with the corresponding output.
     * The initial value is `true`.
     *
     * @param redirectErrorStream The new property value
     *
     * @return This process executor.
     */
    fun redirectErrorStream(redirectErrorStream: Boolean): Executor {
        builder.redirectErrorStream(redirectErrorStream)
        return this
    }

    /**
     * Allows any exit value for the process being executed.
     *
     * @return This process executor.
     */
    fun exitValueAny(): Executor {
        allowedExitValues = null
        return this
    }

    /**
     * Allows only `0` as the exit value for the process being executed.
     *
     * @return This process executor.
     */
    fun exitValueNormal(): Executor {
        return exitValues(NORMAL_EXIT_VALUE)
    }

    /**
     * Sets the allowed exit value for the process being executed.
     *
     * @param exitValue single exit value or `null` if all exit values are allowed.
     *
     * @return This process executor.
     */
    fun exitValue(exitValue: Int): Executor {
        return exitValues(exitValue)
    }

    /**
     * Sets the allowed exit values for the process being executed.
     *
     * @param exitValues set of exit values or `null` if all exit values are allowed.
     *
     * @return This process executor.
     */
    fun exitValues(vararg exitValues: Int): Executor {
        allowedExitValues = exitValues.toSet()
        return this
    }

    /**
     * Sets the helper for stopping the process in case of timeout or cancellation.
     *
     * By default [DestroyProcessStopper] is used which just invokes [Process.destroy].
     *
     * @param stopper helper for stopping the process (`null` means [NopProcessStopper] - process is not stopped).
     *
     * @return This process executor.
     */
    fun stopper(stopper: ProcessStopper?): Executor {
        if (stopper == null) {
            this.stopper = NopProcessStopper.INSTANCE
        } else {
            this.stopper = stopper
        }

        return this
    }

    /**
     * @return current stream handler for the process being executed.
     */
    fun streams(): IOStreamHandler {
        return streams
    }

    /**
     * Sets a stream handler for the process being executed.
     *
     * This will overwrite any stream redirection that was previously set to use the provided handler.
     *
     * @param streams the stream handler
     *
     * @return This process executor.
     */
    fun streams(streams: IOStreamHandler): Executor {
        this.streams = streams
        return this
    }

    /**
     * Sets a timeout for closing standard streams of the process being executed.
     * When this timeout is reached we log a warning but consider that the process has finished.
     * We also flush the streams so that all output read so far is available.
     *
     *
     * This can be used on Windows in case a process exits quickly but closing the streams blocks forever.
     *
     *
     *
     * Closing timeout must fit into the general execution timeout (see [.timeout]).
     * By default there's no closing timeout.
     *
     * @param timeout timeout for closing streams of a process.
     * @param unit the time unit of the timeout
     *
     * @return This process executor.
     */
    fun closeTimeout(timeout: Long, unit: TimeUnit): Executor {
        closeTimeout = timeout
        closeTimeoutUnit = unit
        return this
    }

    /**
     * Explicitly permit reading the output from this process.
     *
     * If the process is started via [Executor.startAsync], then reading the process output will be **BLOCKING**.
     *
     * If the process is started via [Executor.start], the reading the process output will occur once the process has
     * exited (and it doesn't matter if reading the output is blocking or not).
     *
     * If you set the output stream manually - you can read the output as you choose. This method exists to make it easier to read the
     * output.
     *
     * By default, the process output is discarded
     *
     * @return This process executor.
     */
    fun enableRead(): Executor {
        this.readOutput = true
        return this
    }

    /**
     * Sets the new process to inherit the current Java process source and destination I/O stream
     */
    fun inheritIO(): Executor {
        this.inheritIO = true
        return this
    }

    /**
     * Sets the input stream to redirect to the process' input stream.
     *
     * If this method is invoked multiple times each call overwrites the previous.
     *
     * @param input input stream that will be written to the process input stream (`null` means nothing will be written to the process input stream).
     *
     * @return This process executor.
     */
    fun redirectInput(input: InputStream?): Executor {
        val inputStream = when (input) {
            null -> {
                NopInputStream.INPUT_STREAM
            }
            else -> {
                input
            }
        }

        // Only set the input stream handler, preserve the same output and error stream handler
        streams = streams.setInputStream(inputStream)
        return this
    }

    /**
     * Redirects the process' output stream to given output stream.
     *
     * If this method is invoked multiple times each call overwrites the previous.
     * Use [.redirectOutputAlsoTo] if you want to redirect the output to multiple streams.
     *
     * @param output output stream where the process output is redirected to (`null` means [NopOutputStream] which acts like a `/dev/null`).
     *
     * @return This process executor.
     */
    @JvmOverloads
    fun redirectOutput(output: OutputStream? = null): Executor {
        var outputStream = output
        if (outputStream == null) {
            outputStream = NopOutputStream.OUTPUT_STREAM
        }

        // Only set the output stream handler, preserve the same error stream handler
        streams = streams.setOutputStream(outputStream)
        return this
    }

    /**
     * Redirects the process' error stream to given output stream.
     *
     * If this method is invoked multiple times each call overwrites the previous.
     * Use [.redirectErrorAlsoTo] if you want to redirect the error to multiple streams.
     *
     *
     * Calling this method automatically disables merging the process error stream to its output stream.
     *
     *
     * @param output output stream where the process error is redirected to (`null` means [NopOutputStream] which acts like `/dev/null`).
     *
     * @return This process executor.
     */
    fun redirectError(output: OutputStream?): Executor {
        var outputStream = output
        if (outputStream == null) {
            outputStream = NopOutputStream.OUTPUT_STREAM
        }

        // Only set the error stream handler, preserve the same output stream handler
        streams = streams.setErrorStream(outputStream)
        redirectErrorStream(false)
        return this
    }

    /**
     * Redirects the process' output stream also to a given output stream.
     *
     * This method can be used to redirect output to multiple streams.
     *
     *
     * @param output the stream to redirect this output to
     *
     * @return This process executor.
     */
    fun redirectOutputAlsoTo(output: OutputStream): Executor {
        streams = streams.teeOutputStream(output)
        return this
    }

    /**
     * Redirects the process' error stream also to a given output stream.
     *
     * This method can be used to redirect error to multiple streams.
     *
     * Calling this method automatically disables merging the process error stream to its output stream.
     *
     *
     * @param output the output stream to redirect the error stream to
     *
     * @return This process executor.
     */
    fun redirectErrorAlsoTo(output: OutputStream): Executor {
        streams = streams.teeErrorStream(output)
        redirectErrorStream(false)
        return this
    }

    /**
     * Logs the process' output to a given [Logger] with `trace` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectOutputAsTrace(): Executor {
        return redirectOutput(Slf4jStream.asDebug(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' output to a given [Logger] with `debug` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectOutputAsDebug(): Executor {
        return redirectOutput(Slf4jStream.asDebug(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' output to a given [Logger] with `info` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectOutputAsInfo(): Executor {
        return redirectOutput(Slf4jStream.asInfo(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' output to a given [Logger] with `warn` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectOutputAsWarn(): Executor {
        return redirectOutput(Slf4jStream.asWarn(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' output to a given [Logger] with `error` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectOutputAsError(): Executor {
        return redirectOutput(Slf4jStream.asDebug(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' output to a given [Logger] with `trace` level.
     *
     * @param log the logger to output the message to
     *
     * @return This process executor.
     */
    fun redirectOutputAsTrace(log: Logger): Executor {
        return redirectOutput(Slf4jStream.asDebug(log))
    }

    /**
     * Logs the process' output to a given [Logger] with `debug` level.
     *
     * @param log the logger to output the message to
     *
     * @return This process executor.
     */
    fun redirectOutputAsDebug(log: Logger): Executor {
        return redirectOutput(Slf4jStream.asDebug(log))
    }

    /**
     * Logs the process' output to a given [Logger] with `info` level.
     *
     * @param log the logger to output the message to
     *
     * @return This process executor.
     */
    fun redirectOutputAsInfo(log: Logger): Executor {
        return redirectOutput(Slf4jStream.asInfo(log))
    }

    /**
     * Logs the process' output to a given [Logger] with `warn` level.
     *
     * @param log the logger to output the message to
     *
     * @return This process executor.
     */
    fun redirectOutputAsWarn(log: Logger): Executor {
        return redirectOutput(Slf4jStream.asWarn(log))
    }

    /**
     * Logs the process' output to a given [Logger] with `error` level.
     *
     * @param log the logger to output the message to
     *
     * @return This process executor.
     */
    fun redirectOutputAsError(log: Logger): Executor {
        return redirectOutput(Slf4jStream.asDebug(log))
    }

    /**
     * Logs the process' error to a given [Logger] with `trace` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectErrorAsTrace(): Executor {
        return redirectError(Slf4jStream.asTrace(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' error to a given [Logger] with `debug` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectErrorAsDebug(): Executor {
        return redirectError(Slf4jStream.asDebug(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' error to a given [Logger] with `info` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectErrorAsInfo(): Executor {
        return redirectError(Slf4jStream.asInfo(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' error to a given [Logger] with `warn` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectErrorAsWarn(): Executor {
        return redirectError(Slf4jStream.asWarn(LoggerFactory.getLogger(
                CallerLoggerUtil.getName(null, 1))))
    }

    /**
     * Logs the process' error to a given [Logger] with `error` level of the calling class logger.
     *
     * @return This process executor.
     */
    fun redirectErrorAsError(): Executor {
        return redirectError(Slf4jStream.asError(log))
    }

    /**
     * Logs the process' error to a given [Logger] with `trace` level.
     *
     * @param log the logger to process output to
     *
     * @return This process executor.
     */
    fun redirectErrorAsTrace(log: Logger): Executor {
        return redirectError(Slf4jStream.asTrace(log))
    }

    /**
     * Logs the process' error to a given [Logger] with `debug` level.
     *
     * @param log the logger to process the error to
     *
     * @return This process executor.
     */
    fun redirectErrorAsDebug(log: Logger): Executor {
        return redirectError(Slf4jStream.asDebug(log))
    }

    /**
     * Logs the process' error to a given [Logger] with `info` level.
     *
     * @param log the logger to process output to
     *
     * @return This process executor.
     */
    fun redirectErrorAsInfo(log: Logger): Executor {
        return redirectError(Slf4jStream.asInfo(log))
    }

    /**
     * Logs the process' error to a given [Logger] with `warn` level.
     *
     * @param log the logger to process output to
     *
     * @return This process executor.
     */
    fun redirectErrorAsWarn(log: Logger): Executor {
        return redirectError(Slf4jStream.asWarn(log))
    }

    /**
     * Logs the process' error to a given [Logger] with `error` level.
     *
     * @param log the logger to process output to
     *
     * @return This process executor.
     */
    fun redirectErrorAsError(log: Logger): Executor {
        return redirectError(Slf4jStream.asError(log))
    }

    /**
     * Adds a process destroyer to be notified when the process starts and stops.
     *
     * @param destroyer helper for destroying all processes on certain event such as VM exit (not `null`).
     *
     * @return This process executor.
     */
    fun addDestroyer(destroyer: ProcessDestroyer): Executor {
        return addListener(DestroyerListenerAdapter(destroyer))
    }

    /**
     * Sets the process destroyer to be notified when the process starts and stops.
     *
     * This methods always removes any other [ProcessDestroyer] registered. Use [.addDestroyer] to keep the existing ones.
     *
     * @param destroyer helper for destroying all processes on certain event such as VM exit (maybe `null`).
     *
     * @return This process executor.
     */
    fun destroyer(destroyer: ProcessDestroyer?): Executor {
        removeListeners(DestroyerListenerAdapter::class.java)
        if (destroyer != null) {
            addListener(DestroyerListenerAdapter(destroyer))
        }
        return this
    }

    /**
     * Sets the started process to be destroyed on VM exit (shutdown hooks are executed).
     * If this VM gets killed the started process may not get destroyed.
     *
     * To undo this command call `destroyer(null)`.
     *
     * @return This process executor.
     */
    fun destroyOnExit(): Executor {
        return destroyer(ShutdownHookProcessDestroyer.INSTANCE)
    }

    /**
     * Unregister all existing process event handlers and register new one.
     *
     * @param listener process event handler to be set (maybe `null`).
     *
     * @return This process executor.
     */
    fun listener(listener: ProcessListener?): Executor {
        clearListeners()

        listener?.let { addListener(it) }
        return this
    }

    /**
     * Register new process event handler.
     *
     * @param listener process event handler to be added.
     *
     * @return This process executor.
     */
    fun addListener(listener: ProcessListener): Executor {
        listeners.add(listener)
        return this
    }

    /**
     * Unregister existing process event handler.
     *
     * @param listener process event handler to be removed.
     *
     * @return This process executor.
     */
    fun removeListener(listener: ProcessListener): Executor {
        listeners.remove(listener)
        return this
    }

    /**
     * Unregister existing process event handlers of given type or its sub-types.
     *
     * @param listenerType process event handler type.
     *
     * @return This process executor.
     */
    fun removeListeners(listenerType: Class<out ProcessListener>): Executor {
        listeners.removeAll(listenerType)
        return this
    }

    /**
     * Unregister all existing process event handlers.
     *
     * @return This process executor.
     */
    fun clearListeners(): Executor {
        listeners.clear()
        return this
    }

    /**
     * Check the exit value of given process result. This can be used by unit tests.
     *
     * @param result process result which maybe constructed by a unit test.
     *
     * @throws InvalidExitValueException if the given exit value was rejected.
     */
    @Throws(InvalidExitValueException::class)
    fun checkExitValue(result: ProcessResult) {
        DeferredProcessResult.checkExit(attributes, result)
    }

    /**
     * Changes how most common messages about starting and waiting for processes are actually logged.
     * This is configures the Executor **execution logs** to use the Executor log (instead of no logger)
     *
     * This will use the log at whatever the highest level possible for that logger
     *
     * see http://logback.qos.ch/manual/architecture.html for more info
     *  logger order goes (from lowest to highest) TRACE->DEBUG->INFO->WARN->ERROR->OFF
     *
     * @return This process executor.
     */
    fun defaultLogger(): Executor {
        this.logger = log
        return this
    }

    /**
     * If there is high-performance I/O necessary (lots of I/O with the subprocess), then multiple threads
     * can be used for pumping the I/O.
     *
     * By default, there is only 1 thread.
     *
     * After calling this, there will be 1 thread per I/O stream to be pumped
     */
    fun highPerformanceIO(): Executor {
        this.highPerformanceIO = true
        return this
    }

    /**
     * Execute this command as JAVA, using the same JVM as the currently running JVM, as a forked process.
     *
     * Be aware that on MACOS there are two quirks that can both occur!
     *  - this *must* use the same java installation as pointed to by JAVA_HOME
     *  - if the macos specific java flag `-Xdock:name` was/is used -- then it *must* be `/usr/bin/java`, even if it's
     *    a symlink to the same location as JAVA_HOME!
     *
     * Because of these quirks, on MACOS, if the javaExecutable is not specified, then `/usr/bin/java` will always be used.
     *
     * This should be used last, as the only thing possible from here is [start] and [startAsync] variants
     */
    @JvmOverloads
    fun asJvmProcess(javaExecutable: String? = null): JvmExecOptions {
        jvmExecOptions = JvmExecOptions(this, javaExecutable)
        return jvmExecOptions!!
    }

    /**
     * Execute this command as JAVA, using the same JVM as the currently running JVM, as a forked process.
     *
     * Be aware that on MACOS there are two quirks that can both occur!
     *  - this *must* use the same java installation as pointed to by JAVA_HOME
     *  - if the macos specific java flag `-Xdock:name` was/is used -- then it *must* be `/usr/bin/java`, even if it's
     *    a symlink to the same location as JAVA_HOME!
     *
     * Because of these quirks, on MACOS, if the javaExecutable is not specified, then `/usr/bin/java` will always be used.
     *
     * This should be used last, as the only thing possible from here is [start] and [startAsync] variants
     */
    fun asJvmProcess(javaExecutable: File): JvmExecOptions {
        if (!javaExecutable.canExecute()) {
            throw IllegalArgumentException("The java executable if specified, must be exist and be executable. Error with: $javaExecutable")
        }
        jvmExecOptions = JvmExecOptions(this, javaExecutable.canonicalFile.path)
        return jvmExecOptions!!
    }

    /**
     * Execute this command on a remote host via SSH.
     *
     * This should be used last, as the only thing possible from here is [start] and [startAsync] variants
     */
    fun asSshProcess(): SshExecOptions {
        sshExecOptions = SshExecOptions(this)
        return sshExecOptions!!
    }

    /**
     * Start the sub process, as a shell command (bash/cmd/etc), instead of directly invoking the command as a forked process.
     *
     * This method does not wait until the process exits.
     *
     * The value passed to [.timeout] is ignored. Use [DeferredProcessResult.await] to wait for the process to finish.
     *
     * Invoke [DeferredProcessResult.cancel] to destroy the process.
     *
     * @return DeferredProcessResult representing the exit value of the finished process.
     *
     * @throws IOException an error occurred when process was started.
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun startAsShellAsync(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): DeferredProcessResult {
        executeAsShell = true
        return startAsync(timeout, timeoutUnit)
    }

    /**
     * Start the sub process in a new coroutine. The calling thread will continue execution. This method does not wait until the process exits.
     *
     * Calling [SyncProcessResult.output] will result in a blocking read of process output.
     *
     * The value passed to [.timeout] is ignored. Use [DeferredProcessResult.await] to wait for the process to finish.
     *
     * Invoke [DeferredProcessResult.cancel] to destroy the process.
     *
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return [DeferredProcessResult] representing the process results (value/completed output-streams/etc) of the finished process.
     *
     * @throws IOException an error occurred when process was started.
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun startAsync(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): DeferredProcessResult {
        return prepareProcess(timeout, timeoutUnit, true)
    }

    /**
     * The calling thread will immediately execute the sub process as a shell command (bash/cmd/etc).
     *
     * When trying to close the input streams, the calling thread may suspend.
     *
     * Waits until:
     *  - the process stops
     *  - a timeout occurs and the caller thread gets interrupted. (In this case the process gets destroyed as well.)
     *
     * Calling [SyncProcessResult.output] will result in a non-blocking read of process output.
     *
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return results of the finished process (exit code and output, if any)
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InterruptedException this thread was interrupted.
     * @throws TimeoutException timeout set by [.timeout] was reached.
     * @throws InvalidExitValueException if invalid exit value was returned (@see [.exitValues]).
     */
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class, TimeoutException::class, InvalidExitValueException::class)
    suspend fun startAsShell(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): SyncProcessResult {
        executeAsShell = true

        @Suppress("BlockingMethodInNonBlockingContext")
        return start(timeout, timeoutUnit)
    }

    /**
     * The calling thread will immediately execute the sub process as a shell command (bash/cmd/etc).
     *
     * When trying to close the input streams, the calling thread may block.
     *
     * Waits until:
     *  - the process stops
     *  - a timeout occurs and the caller thread gets interrupted. (In this case the process gets destroyed as well.)
     *
     * Calling [SyncProcessResult.output] will result in a non-blocking read of process output.
     *
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return results of the finished process (exit code and output, if any)
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InterruptedException this thread was interrupted.
     * @throws TimeoutException timeout set by [.timeout] was reached.
     * @throws InvalidExitValueException if invalid exit value was returned (@see [.exitValues]).
     */
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class, TimeoutException::class, InvalidExitValueException::class)
    fun startAsShellBlocking(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): SyncProcessResult {
        return runBlocking {
            startAsShell(timeout, timeoutUnit)
        }
    }




    /**
     * The calling thread will immediately execute the sub process. When trying to close the input streams, the calling thread may suspend.
     *
     * Waits until:
     *  - the process stops
     *  - a timeout occurs and the caller thread gets interrupted. (In this case the process gets destroyed as well.)
     *
     * Calling [SyncProcessResult.output] will result in a non-blocking read of process output.
     *
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return results of the finished process (exit code and output, if any)
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InterruptedException this thread was interrupted.
     * @throws TimeoutException timeout set by [.timeout] was reached.
     * @throws InvalidExitValueException if invalid exit value was returned (@see [.exitValues]).
     */
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class, TimeoutException::class, InvalidExitValueException::class)
    suspend fun start(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): SyncProcessResult {
        // we ALWAYS want to block/suspend the current running thread!
        // This is because we want the same blocking behavior if we have NO timeout or WITH timeout.

        // Always wait for it to finish. We cannot interrupt this (because we are blocking).
        // Use startAsync() to interrupt or wait without blocking
        return prepareProcess(timeout, timeoutUnit, false).await(timeout, timeoutUnit)
    }

    /**
     * The calling thread will immediately execute the sub process. When trying to close the input streams, the calling thread may block.
     *
     * Waits until:
     *  - the process stops
     *  - a timeout occurs and the caller thread gets interrupted. (In this case the process gets destroyed as well.)
     *
     * Calling [SyncProcessResult.output] will result in a non-blocking read of process output.
     *
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return results of the finished process (exit code and output, if any)
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InterruptedException this thread was interrupted.
     * @throws TimeoutException timeout set by [.timeout] was reached.
     * @throws InvalidExitValueException if invalid exit value was returned (@see [.exitValues]).
     */
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class, TimeoutException::class, InvalidExitValueException::class)
    fun startBlocking(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): SyncProcessResult {
        return runBlocking {
            start(timeout, timeoutUnit)
        }
    }

    /**
     * Start the process and its stream handlers.
     *
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return process the started process.
     *
     *  @throws IOException the process or its stream handlers couldn't start (in the latter case we also destroy the process).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun prepareProcess(timeout: Long, timeoutUnit: TimeUnit, asyncProcessStart: Boolean): DeferredProcessResult {
        // establish incompatible options
        check(inheritIO && highPerformanceIO) { "inheritIO & highPerformanceIO cannot be both set at the same time, they cancel each-other out" }



        // Invoke listeners - they can modify this executor
        listeners.beforeStart(this)
        val command = builder.command()

        if (jvmExecOptions == null) {
            // when running "normally", we must check if our commands has been set, otherwise... whoopsie!
            check(command.isNotEmpty()) { "Command has not been set." }
        }

        // configure the environment
        val hasPathToPrepend = pathsToPrepend.isNotEmpty()
        val prependPaths = if (hasPathToPrepend) pathsToPrepend.joinToString(separator = File.pathSeparator) else ""

        if (environment.isNotEmpty() || hasPathToPrepend) {
            // Take care of Windows environments that may contain "Path" OR "PATH" or "path" - both possibly existing, but not necessarily
            val systemEnv = System.getenv()
            val systemUsesAllCapsPath = systemEnv["PATH"] != null
            val systemUsesMixCasePath = systemEnv["Path"] != null
            val systemUsesLowCasePath = systemEnv["path"] != null

            val correctPathName = when {
                systemUsesAllCapsPath -> "PATH"
                systemUsesMixCasePath -> "Path"
                else -> "path"
            }

            if (systemUsesAllCapsPath || systemUsesMixCasePath || systemUsesLowCasePath) {
                // we have to make sure we use the same for our locally set env vars!
                val localEnvUsesAllCapsPath = environment.remove("PATH") ?: ""
                val localEnvUsesMixCasePath = environment.remove("Path") ?: ""
                val localEnvUsesLowCasePath = environment.remove("path") ?: ""

                if (localEnvUsesAllCapsPath.isNotEmpty() || localEnvUsesMixCasePath.isNotEmpty() || localEnvUsesLowCasePath.isNotEmpty()) {
                    // we jam these all together, if possible
                    var path = ""
                    if (localEnvUsesAllCapsPath.isNotEmpty()) {
                        path += localEnvUsesAllCapsPath
                    }

                    if (localEnvUsesMixCasePath.isNotEmpty()) {
                        if (path.isNotEmpty()) {
                            path += File.pathSeparator
                        }
                        path += localEnvUsesMixCasePath
                    }

                    if (localEnvUsesLowCasePath.isNotEmpty()) {
                        if (path.isNotEmpty()) {
                            path += File.pathSeparator
                        }
                        path += localEnvUsesLowCasePath
                    }

                    // we have to prepend our own paths to the environment variables.
                    if (hasPathToPrepend) {
                        path = prependPaths + File.pathSeparator + path
                    }

                    environment[correctPathName] = path
                } else {
                    // who knows WHAT is going on. Just slap on the path we want to add
                    environment[correctPathName] = prependPaths
                }
            } else if (hasPathToPrepend) {
                // who knows WHAT is going on. Just slap on the path we want to add
                environment[correctPathName] = prependPaths
            }

            val env = builder.environment()
            environment.forEach { (key, value) ->
                if (value == null) {
                    env.remove(key)
                } else {
                    env[key] = value
                }
            }
        }


        if (jvmExecOptions != null) {
            val jvm = jvmExecOptions!!

            // this means that we want to launch this as a forked JAVA process, using the same JVM as we were started with
            // the builder commands are seen as options to the JAVA process.
            jvm.configure()
        }

        if (executeAsShell) {
            // NOTE: Also run when we prepend paths to the executable, as this is the only way for it to work.
            // should we execute the command as a "shell command", or should we fork the process and run it directly?

            // if we are executing as a "shell", ALL THE PARAMETERS ARE A SINGLE PARAMETER!
            // dump all the parameters, and "start over" -- adding them as a single parameter.

            val shellCommand = command.joinToString(separator = " ")
            command.clear()


            if (IS_OS_WINDOWS) {
                // add our commands to the internal command list
                command.addAll(listOf("cmd", "/c"))
            } else {
                // do a quick invocation of the "echo $SHELL" command, and save that for all future runs of the executor,
                // to calculate what the shell is (and cache it)

                // it might be in the ENV var, for example: SHELL=/bin/zsh
                if (DEFAULT_SHELL == null) {
                    val shell = System.getenv()["SHELL"]
                    if (shell != null && File(shell).canExecute()) {
                        DEFAULT_SHELL = shell
                    }
                }

                // IF THIS DOES NOT WORK, then parse out the *potential* list of shells below.
                if (DEFAULT_SHELL == null) {
                    try {
                        // we don't support java 6, so the proper method of scanner + auto-closable via java7 is appropriate.
                        val result = Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo \$SHELL")).inputStream.use { inputStream ->
                            java.util.Scanner(inputStream).useDelimiter("\\A").use { s ->
                                if (s.hasNext()) {
                                    s.next().trim()
                                } else {
                                    null
                                }
                            }
                        }

                        if (result != null) {
                            if (result.isNotBlank() && File(result).canExecute()) {
                                DEFAULT_SHELL = result
                            }
                        }
                    } catch (ignored: IOException) {
                    }
                }

                // fall-back
                if (DEFAULT_SHELL == null) {
                    arrayOf("/bin/bash", "/usr/bin/bash",
                            "/bin/pfbash", "/usr/bin/pfbash",
                            "/bin/csh", "/usr/bin/csh",
                            "/bin/pfcsh", "/usr/bin/pfcsh",
                            "/bin/jsh", "/usr/bin/jsh",
                            "/bin/ksh", "/usr/bin/ksh",
                            "/bin/pfksh", "/usr/bin/pfksh",
                            "/bin/ksh93", "/usr/bin/ksh93",
                            "/bin/pfksh93", "/usr/bin/pfksh93",
                            "/bin/pfsh", "/usr/bin/pfsh",
                            "/bin/tcsh", "/usr/bin/tcsh",
                            "/bin/pftcsh", "/usr/bin/pftcsh",
                            "/usr/xpg4/bin/sh", "/usr/xp4/bin/pfsh",
                            "/bin/zsh", "/usr/bin/zsh",
                            "/bin/pfzsh", "/usr/bin/pfzsh",
                            "/bin/sh", "/usr/bin/sh").forEach { shell ->

                        if (File(shell).canExecute()) {
                            DEFAULT_SHELL = shell
                            return@forEach
                        }
                    }
                }

                if (DEFAULT_SHELL == null) {
                    throw IllegalStateException("Unable to determine the default shell for the linux/unix environment.")
                }

                // *nix
                // add our commands to the internal command list
                command.addAll(listOf(DEFAULT_SHELL!!, "-c"))
            }

            // now add our original command + parameters.
            command.add(shellCommand)


            if (IS_OS_MAC && !environment.containsKey("SOFTWARE")) {
                // Enable LANG overrides
                environment["SOFTWARE"] = ""
            }

            // Make sure all shell calls are the machine language default (UTF8)  THIS CAN BE OVERRIDDEN
            if (!environment.containsKey("LANG")) {
                // "export LANG=en_US.UTF-8"
                // the value of "C" makes this the machine language default
                environment["LANG"] = "C"
            }
        }

        if (inheritIO) {
            // Sets the child process to inherit the current Java process source and destination I/O stream
            builder.inheritIO()
        }


        val attributes = attributes // this makes a copy of the attributes!
        val newListeners = listeners.clone()


        // are we interested in process output?? THIS MUST BE THE VERY LAST THING!
        // we can read the output IN ADDITION TO having our own output stream for the process.
        if (!readOutput && executeAsShell && streams.out is NopOutputStream) {
            // NOTE: this ONLY works if we are running as a shell!

            // should we redirect our output to null? we are not interested in the program output
            if (IS_OS_WINDOWS) {
                // >NUL on windows
                command.add("2>&1>")
                command.add("nul")

            } else {
                // we will "pipe" it to /dev/null on *nix
                command.add(">/dev/null")
                command.add("2>&1")
            }
        }



        val nativeProcess = try {
            val timeoutInfo = if (timeout > 0L) {
                "(timeout: $timeout $timeoutUnit) "
            } else {
                ""
            }

            val executeText = if (executeAsShell) {
                "Executing as shell $timeoutInfo"
            } else {
                "Executing $timeoutInfo"
            }

            if (sshExecOptions != null) {
                LogHelper.logAtLowestLevel(logger, "$executeText on ${sshExecOptions!!.info()} $executingMessageParams")
                sshExecOptions!!.startProcess(timeout, timeoutUnit, logger)
            } else {
                LogHelper.logAtLowestLevel(logger, "$executeText $executingMessageParams")
                builder.start()
            }
        } catch (e: Exception) {
            val errorMessage = if (sshExecOptions != null) {
                "Could not execute on ${sshExecOptions!!.info()} $executingMessageParams"
            } else {
                "Could not execute $executingMessageParams"
            }

            throw ProcessInitException.newInstance(errorMessage, e) ?: IOException(errorMessage, e)
        }

        // we might reassign the streams if they are to be read
        var streams: IOStreamHandler

        /*
         * Defines how we create the process results (sync, async, no-op) based on what type of startup we have. This
         * unit function saves us from complicated code paths.
         *
         * This is only called once (either when the process is complete, or when there is an error)
         */
        val createProcessResults: (Long, Int) -> SyncProcessResult

        // this is ONLY called if there is an exception while waiting for the process to complete.
        val errorMessageHandler: (StringBuilder) -> Unit

        if (!readOutput) {
            // if we don't read the output, then we don't care about blocking/non-blocking reads

            streams = this.streams // note: this leaves the ORIGINAL streams alone!

            createProcessResults = { pid, exitValue ->
                // no byte output
                NopProcessResult(pid, exitValue)
            }

            errorMessageHandler = { sb ->
                // necessary because we throw an exception if we try to get the output from a NopProcessResult
                DeferredProcessResult.addExceptionMessageSuffix(attributes, sb, "")
            }
        } else {
            streams = this.streams

            if (asyncProcessStart) {
                // This means we want blocking reads from the output since we can read the output while the process is running
                // make the streams support async reads
                streams = streams.asyncMode()  // note: this leaves the ORIGINAL streams alone!
            }

            // this means we want a SINGLE read from the output, when the process has finished running
            val out = ByteArrayOutputStream()

            // tee the output, this will only happen if the output stream has previously been set
            streams = streams.teeOutputStream(out)  // note: this leaves the ORIGINAL streams alone!

            createProcessResults = { pid, exitValue ->
                // create output based on the ByteArrayOutputStream. This is read when the process is completed
                SyncProcessResult(pid, exitValue, out.toByteArray())
            }

            errorMessageHandler = { sb ->
                val results = SyncProcessResult(0, 0, out.toByteArray()) // don't care about the pid or exit value
                DeferredProcessResult.addExceptionMessageSuffix(attributes, sb, results.output.string())
            }
        }

        // setup and start the stream processing
        val separateErrorStream = !builder.redirectErrorStream()
        streams.start(nativeProcess, separateErrorStream, highPerformanceIO)

        // Invoke listeners - changing this executor does not affect the started process
        newListeners.afterStart(nativeProcess, this)

        val logger = logger
        val params = Params(processAttributes = attributes,
                            stopper = stopper,
                            listener = newListeners,
                            streams = streams,
                            logger = logger,
                            errorMessageHandler = errorMessageHandler,
                            closeTimeout = closeTimeout,
                            closeTimeoutUnit = closeTimeoutUnit,
                            asyncProcessStart = asyncProcessStart)

        val deferred = DeferredProcessResult(nativeProcess, params, createProcessResults)
        val processPid = deferred.pid
        if (processPid == PidHelper.INVALID) {
            LogHelper.logAtLowestLevel(logger, "Started process")
        } else {
            LogHelper.logAtLowestLevel(logger, "Started process [pid={}]", processPid)
        }

        deferred.start()

        return deferred
    }
}
