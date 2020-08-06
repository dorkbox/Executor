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

package dorkbox.executor.stream

import dorkbox.executor.stream.nopStreams.NopInputStream
import dorkbox.executor.stream.nopStreams.NopOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Used to handle input and output stream of subprocesses.
 */
abstract class IOStreamHandler(internal val out: OutputStream = System.out,
                               internal val err: OutputStream = System.err,
                               internal val input: InputStream = NopInputStream.INPUT_STREAM,
                               internal val asyncSupport: Boolean = false) {

    /**
     * Force the PumpStreamHandler to enable async read mode (which permits suspending/blocking reads from the process output/error streams)
     */
    fun asyncMode(): PumpStreamHandler {
        return PumpStreamHandler(out, err, input, true)
    }

    /**
     * creates a IO Stream handler with the input set
     */
    fun setInputStream(inputStream: InputStream): IOStreamHandler {
        return PumpStreamHandler(out, err, inputStream, asyncSupport)
    }

    /**
     * creates a IO Stream handler with the output set
     */
    fun setOutputStream(outputStream: OutputStream): IOStreamHandler {
        return PumpStreamHandler(outputStream, err, input, asyncSupport)
    }

    /**
     * creates a IO Stream handler with the error set
     */
    fun setErrorStream(errorStream: OutputStream): IOStreamHandler {
        return PumpStreamHandler(out, errorStream, input, asyncSupport)
    }


    /**
     * Tee's the process' output stream ALSO to the given output stream.
     *
     * If the origOutput stream is a NopOutputStream, a tee stream is not created
     *
     * @return new stream handler created.
     */
    fun teeOutputStream(outputStream: OutputStream): PumpStreamHandler {
        return if (out is NopOutputStream) {
            // don't tee the stream, just make one so we can read the output
            PumpStreamHandler(outputStream, err, input, asyncSupport)
        }
        else {
            // tee the output stream
            PumpStreamHandler(TeeOutputStream(out,
                                                                                                                            outputStream),
                                                                     err,
                                                                     input,
                                                                     asyncSupport)
        }
    }

    /**
     * Tee's the process' error stream ALSO to the given error stream.
     *
     * If the origOutput stream is a NopOutputStream, a tee stream is not created
     *
     * @return new stream handler created.
     */
    fun teeErrorStream(outputStream: OutputStream): PumpStreamHandler {
        return if (err is NopOutputStream) {
            // don't tee the stream, just make one so we can read the output
            PumpStreamHandler(out, outputStream, input, asyncSupport)
        }
        else {
            // tee the error stream
            PumpStreamHandler(out,
                                                                     TeeOutputStream(err,
                                                                                                                            outputStream),
                                                                     input,
                                                                     asyncSupport)
        }
    }


    /**
     *  Setup and start the IO stream processing for the subprocess
     *
     * @param process this is the process we are pumping IO for
     * @param separateErrorStream true to indicate we have separate error/output streams to pump.
     *                            false means error/output are both the "output" stream
     */
    open fun start(process: Process, separateErrorStream: Boolean, highPerformanceIO: Boolean) {}

    /**
     * Stop handling of the streams - will not be restarted.
     *
     * Will wait for pump threads to complete and closes the streams belonging to the given Process.
     */
    open suspend fun stop(process: Process, finishedCleanly: Boolean) {}
}
