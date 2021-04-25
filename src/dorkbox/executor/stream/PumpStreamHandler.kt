/*
 * Copyright 2020 dorkbox, llc
 * Copyright (C) 2014 ZeroTurnaround <support@zeroturnaround.com>
 * Contains fragments of code from Apache Commons Exec, rights owned
 * by Apache Software Foundation (ASF).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTICE: This file originates from the Apache Commons Exec package.
 * It has been modified to fit our needs.
 *
 * The following is the original header of the file in Apache Commons Exec:
 *
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

@file:Suppress("BlockingMethodInNonBlockingContext")
package dorkbox.executor.stream

import dorkbox.executor.Executor
import dorkbox.executor.stream.nopStreams.NopInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface IOStream {
    /**
     * Closes this output stream and releases any system resources
     * associated with this stream. The general contract of `close`
     * is that it closes the output stream. A closed stream cannot perform
     * output operations and cannot be reopened.
     *
     *
     * The `close` method of `OutputStream` does nothing.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun close()

    /**
     * Transfer data between IO streams
     */
    suspend fun pump(length: Int, inputStream: InputStream)
}



inline class PumpedOutputStream(private val out: OutputStream) : IOStream {
    override suspend fun pump(length: Int, inputStream: InputStream) {
        (0 until length.coerceAtMost(PumpStreamHandler.DEFAULT_SIZE)).forEach { _ ->
            out.write(inputStream.read())
        }
        out.flush()
    }

    override fun close() {
        out.close()
    }
}

class PumpedOutputChannel(private val channel: Channel<Byte>) : IOStream {
    override suspend fun pump(length: Int, inputStream: InputStream) {
        (0 until length.coerceAtMost(PumpStreamHandler.DEFAULT_SIZE)).forEach { _ ->
            channel.send(inputStream.read().toByte())
        }
    }

    override fun close() {
        channel.close()
    }
}


/**
 * Copies standard output and error of subprocesses to standard output and error
 * of the parent process. If output or error stream are set to null, any feedback
 * from that stream will be lost.
 *
 * @param out the output [OutputStream].
 * @param err the error  [OutputStream].
 * @param input the input [InputStream].
*/
class PumpStreamHandler(out: OutputStream = System.out,
                        err: OutputStream = System.err,
                        input: InputStream = NopInputStream.INPUT_STREAM,
                        asyncSupport: Boolean = false) : IOStreamHandler(out, err, input, asyncSupport) {

    companion object {
        private val log = LoggerFactory.getLogger(PumpStreamHandler::class.java)

        /**
         * the default size of the internal buffer for copying the streams
         */
        internal const val DEFAULT_SIZE = 1024

        /**
         * Poll the input stream so we don't block forever when trying to read from it
         */
        const val POLL_TIMEOUT = 250L
    }


    // control stream IO pumping
    @Volatile
    private var stop = false

    @Volatile
    private var finishedCleanly = false

    // coroutine job for pumping the IO streams
    private lateinit var pumpJob: Job
    private var pumpJobB: Job? = null
    private var pumpJobC: Job? = null


    // size of this channel is the same as the byte array sizes we pump data from
    internal val channel = Channel<Byte>(DEFAULT_SIZE)

    private val output: IOStream
    private val error: IOStream

    init {
        if (asyncSupport) {
            // connect this to the receive part?
            output = PumpedOutputChannel(channel)
            error = PumpedOutputChannel(channel)
        } else {
            output = PumpedOutputStream(out)
            error = PumpedOutputStream(err)
        }
    }

    private fun runWithContext(action: suspend () -> Unit): Job {
        val contextMap: Map<String, String>? = MDC.getCopyOfContextMap()
        return if (contextMap != null) {
            Executor.IO_DISPATCH.launch {
                MDC.setContextMap(contextMap)
                try {
                    action()
                } finally {
                    MDC.clear()
                }
            }
        } else {
            Executor.IO_DISPATCH.launch {
                action()
            }
        }
    }

    /**
     * Setup and start the IO stream processing for the subprocess
     *
     * @param process this is the process we are pumping IO for
     * @param separateErrorStream true to indicate we have separate error/output streams to pump.
     *                            false means error/output are both the "output" stream
     */
    override fun start(process: Process, separateErrorStream: Boolean, highPerformanceIO: Boolean) {
        if (highPerformanceIO) {
            pumpJob = runWithContext {
                runInThread(process)
            }
            pumpJobB = runWithContext {
                runOutThread(process)
            }

            // if we have a separate error stream, start up an I/O pumper for it
            if (separateErrorStream) {
                pumpJobC = runWithContext {
                    runErrThread(process)
                }
            }
        } else {
            pumpJob = runWithContext {
                runAllSingleThread(process, separateErrorStream)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext", "DuplicatedCode")
    private suspend fun runAllSingleThread(process: Process, separateErrorStream: Boolean) = withContext(Dispatchers.IO) {
        var length: Int
        var readData: Boolean


        // these are the streams connected to the process I/O streams.
        // These are "flipped", since we write to the process input, and read from the process out/err
        val processIn: OutputStream
        val processOut: InputStream
        val processErr: InputStream

        try {
            // Set the [OutputStream] by means of which input can be sent to the process.
            processIn = process.outputStream

            // Set the [InputStream] from which to read the standard output of the process.
            processOut = process.inputStream

            // Set the [InputStream] from which to read the standard error of the process.
            processErr = if (separateErrorStream) {
                process.errorStream
            } else {
                // assign them to the same, since that's an easy check (without having to be a null check)
                processOut
            }
        } catch (e: IOException) {
            // hard abort if we can't do this
            process.destroy()
            throw e
        }


        try {
            // different than a while loop because we want to run this at least once
            // we pump each stream at a time in a single thread/coroutine
            while(true) {
                // keep track if we read any data. We suspend for a short timeout if we haven't ready anything
                readData = false

                // pump input -> process-input
                length = input.available()  // NOTE: These checks prevent blocking forever on stream.read(...)
                if (length > 0) {
                    readData = true

                    (0 until length.coerceAtMost(DEFAULT_SIZE)).forEach { _ ->
                        processIn.write(input.read())
                    }
                    processIn.flush()
                }

                // pump process-output -> out
                length = processOut.available()
                if (length > 0) {
                    readData = true
                    output.pump(length, processOut)
                }

                // pump process-error -> err
                if (processErr !== processOut) {
                    length = processErr.available()
                    if (length > 0) {
                        readData = true
                        error.pump(length, processErr)
                    }
                }

                // NOTE: We only ACTUALLY stop once all the data has been pumped.
                if (stop) {
                    //  - if the subprocess is aborted, then the process itself is killed
                    if (!finishedCleanly) {
                        break
                    }

                    //  - if the subprocess exits normally, make sure that all the data is pumped
                    if (readData) {
                        continue
                    }

                    // only ACTUALLY stop once all the data has be pumped
                    break
                }

                // DO NOT suspend if we have extra data to read!
                if (readData) {
                    continue
                }

                // suspend if we don't have any data to read.
                delay(POLL_TIMEOUT)
            }
        } catch (closingException: CancellationException) {
            // ignored, since we are explicitly closing the coroutine
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext", "DuplicatedCode")
    private suspend fun runInThread(process: Process) = withContext(Dispatchers.IO) {
        val buf = ByteArray(DEFAULT_SIZE)
        var length: Int
        var readData: Boolean


        // these are the streams connected to the process I/O streams.
        // These are "flipped", since we write to the process input, and read from the process out/err
        val processIn: OutputStream

        try {
            // Set the [OutputStream] by means of which input can be sent to the process.
            processIn = process.outputStream
        } catch (e: IOException) {
            // hard abort if we can't do this
            process.destroy()
            throw e
        }



        try {
            // different than a while loop because we want to run this at least once
            // we pump each stream at a time in a single thread/coroutine
            while(true) {
                // keep track if we read any data. We suspend for a short timeout if we haven't ready anything
                readData = false

                // pump input -> process-input
                length = input.available()  // NOTE: These checks prevent blocking forever on stream.read(...)

                if (length > 0) {
                    readData = true
                    length = input.read(buf, 0, length.coerceAtMost(DEFAULT_SIZE))

                    processIn.write(buf, 0, length)
                    processIn.flush()
                }

                // NOTE: We only ACTUALLY stop once all the data has been pumped.
                if (stop) {
                    //  - if the subprocess is aborted, then the process itself is killed
                    if (!finishedCleanly) {
                        break
                    }

                    //  - if the subprocess exits normally, make sure that all the data is pumped
                    if (readData) {
                        continue
                    }

                    // only ACTUALLY stop once all the data has be pumped
                    break
                }

                // DO NOT suspend if we have extra data to read!
                if (readData) {
                    continue
                }

                // suspend if we don't have any data to read.
                delay(POLL_TIMEOUT)
            }
        } catch (closingException: CancellationException) {
            // ignored, since we are explicitly closing the coroutine
        }
    }


    @Suppress("BlockingMethodInNonBlockingContext", "DuplicatedCode")
    private suspend fun runOutThread(process: Process) = withContext(Dispatchers.IO) {
        var length: Int
        var readData: Boolean


        // these are the streams connected to the process I/O streams.
        // These are "flipped", since we write to the process input, and read from the process out/err
        val processOut: InputStream

        try {
            // Set the [InputStream] from which to read the standard output of the process.
            processOut = process.inputStream
        } catch (e: IOException) {
            // hard abort if we can't do this
            process.destroy()
            throw e
        }

        try {
            // different than a while loop because we want to run this at least once
            // we pump each stream at a time in a single thread/coroutine
            while(true) {
                // keep track if we read any data. We suspend for a short timeout if we haven't ready anything
                readData = false

                // pump process-output -> out
                length = processOut.available()
                if (length > 0) {
                    readData = true
                    output.pump(length, processOut)
                }

                // NOTE: We only ACTUALLY stop once all the data has been pumped.
                if (stop) {
                    //  - if the subprocess is aborted, then the process itself is killed
                    if (!finishedCleanly) {
                        break
                    }

                    //  - if the subprocess exits normally, make sure that all the data is pumped
                    if (readData) {
                        continue
                    }

                    // only ACTUALLY stop once all the data has be pumped
                    break
                }

                // DO NOT suspend if we have extra data to read!
                if (readData) {
                    continue
                }

                // suspend if we don't have any data to read.
                delay(POLL_TIMEOUT)
            }
        } catch (closingException: CancellationException) {
            // ignored, since we are explicitly closing the coroutine
            log.error("pow")
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext", "DuplicatedCode")
    private suspend fun runErrThread(process: Process) = withContext(Dispatchers.IO) {
        var length: Int
        var readData: Boolean


        // these are the streams connected to the process I/O streams.
        // These are "flipped", since we write to the process input, and read from the process out/err
        val processErr: InputStream

        try {
            // Set the [InputStream] from which to read the standard error of the process.
            processErr = process.errorStream
        } catch (e: IOException) {
            // hard abort if we can't do this
            process.destroy()
            throw e
        }

        try {
            // different than a while loop because we want to run this at least once
            // we pump each stream at a time in a single thread/coroutine
            while(true) {
                // keep track if we read any data. We suspend for a short timeout if we haven't ready anything
                readData = false

                // pump process-error -> err
                length = processErr.available()
                if (length > 0) {
                    readData = true
                    error.pump(length, processErr)
                }

                // NOTE: We only ACTUALLY stop once all the data has been pumped.
                if (stop) {
                    //  - if the subprocess is aborted, then the process itself is killed
                    if (!finishedCleanly) {
                        break
                    }

                    //  - if the subprocess exits normally, make sure that all the data is pumped
                    if (readData) {
                        continue
                    }

                    // only ACTUALLY stop once all the data has be pumped
                    break
                }

                // DO NOT suspend if we have extra data to read!
                if (readData) {
                    continue
                }

                // suspend if we don't have any data to read.
                delay(POLL_TIMEOUT)
            }
        } catch (closingException: CancellationException) {
            // ignored, since we are explicitly closing the coroutine
        }
    }


    /**
     * Our subprocess has finished running. (when we "abort" the process, we interrupt the thread)
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun stop(process: Process, finishedCleanly: Boolean) {
        this.stop = true
        this.finishedCleanly = finishedCleanly

        // wait for the pump job to end
        log.trace("waiting for IO pump job")

        try {
            pumpJob.join()
        } catch (ignored: InterruptedException) {
        }

        try {
            pumpJobB?.join()
        } catch (ignored: InterruptedException) {
        }

        try {
            pumpJobC?.join()
        } catch (ignored: InterruptedException) {
        }

        /**
         * Close the streams belonging to the given Process.
         *
         * @throws IOException
         */
        var caught: IOException? = null

        try {
            process.outputStream.close()
        } catch (e: IOException) {
            if (e.message == "Stream closed") {
                /**
                 * OutputStream's contract for the close() method: If the stream is already closed then invoking this method has no effect.
                 *
                 * When a UNIXProcess exits ProcessPipeOutputStream automatically closes its target FileOutputStream and replaces it with NullOutputStream.
                 * However the ProcessPipeOutputStream doesn't close itself at that moment.
                 * As ProcessPipeOutputStream extends BufferedOutputStream extends FilterOutputStream closing it flushes the buffer first.
                 * In Java 7 closing FilterOutputStream ignores any exception thrown by the target OutputStream. Since Java 8 these exceptions are now thrown.
                 *
                 * So since Java 8 after UNIXProcess detects the exit and there's something in the output buffer closing this stream throws IOException
                 * with message "Stream closed" from NullOutputStream.
                 */
                log.trace("Failed to close process output stream", e)
            }
            else {
                caught = add(caught, e)
            }
        }

        try {
            process.inputStream.close()
        } catch (e: IOException) {
            caught = add(caught, e)
        }

        try {
            process.errorStream.close()
        } catch (e: IOException) {
            caught = add(caught, e)
        }

        if (caught != null) {
            throw caught
        }
    }

    fun add(exception: IOException?, newException: IOException): IOException {
        if (exception == null) {
            return newException
        }

        exception.addSuppressed(newException)
        return exception
    }
}
