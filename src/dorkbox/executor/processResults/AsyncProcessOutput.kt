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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dorkbox.executor.processResults

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset

/**
 * Standard output of a finished process.
 *
 * @param channel Channel containing the async process output.
 */
open class AsyncProcessOutput(private val channel: Channel<Byte>, private val processResult: SyncProcessResult?) {
    companion object {
        private val NEW_LINE_WIN = "\r".toCharArray().first().toInt()
        private val NEW_LINE_NIX = "\n".toCharArray().first().toInt()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val isOpen: Boolean
    get() {
        return !channel.isClosedForReceive
    }


    var previousValue: Int? = null

    private suspend fun getBuffered(): ByteArray {
        // if the process has FINISHED running, then we have to get the output a different way
        val out = ByteArrayOutputStream()

        if (previousValue != null) {
            out.write(previousValue!!)
            previousValue = null
        }

        var toInt: Int
        try {
            while (true) {
                toInt = channel.receive().toInt()

                if (toInt == NEW_LINE_WIN) {
                    // do we have a *nix line also?
                    toInt = channel.receive().toInt()

                    if (toInt != NEW_LINE_NIX) {
                        // whoops, save this
                        previousValue = toInt
                    }

                    // now return the output.
                    break
                } else {
                    // save this!
                    out.write(toInt)
                    yield()
                }
            }
        } catch (ignored: ClosedReceiveChannelException) {
            // the process closed. Read the output from the processResult (if it was defined)
            // The processResult is defined when the process exits.
            val internalBytes = processResult?.output?.bytes_
            if (internalBytes != null) {
                if (out.size() == 0) {
                    return internalBytes
                }
                else {
                    out.write(internalBytes, 0, internalBytes.size)
                }
            }
        }

        return out.toByteArray()
    }

    /**
     * @return output of the finished process converted to a String using platform's default encoding.
     */
    suspend fun string(): String {
        return String(getBuffered())
    }

    /**
     * @return output of the finished process converted to UTF-8 String.
     */
    suspend fun utf8(): String {
        return string(charset = Charsets.UTF_8)
    }

    /**
     * @param charset The name of a supported char set.
     *
     * @return output of the finished process converted to a String.
     *
     * @throws IllegalStateException if the char set was not supported.
     */
    suspend fun string(charset: Charset): String {
        return try {
            String(getBuffered(), charset)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e.message)
        }
    }

    /**
     * @return output lines of the finished process converted using platform's default encoding.
     */
    suspend fun lines(): List<String> {
        return ProcessOutput.getLinesFrom(string())
    }

    /**
     * @return output lines of the finished process converted using UTF-8.
     */
    suspend fun linesAsUtf8(): List<String> {
        return ProcessOutput.getLinesFrom(utf8())
    }

    /**
     * @param charset The name of a supported char set.
     *
     * @return output lines of the finished process converted using a given char set.
     */
    suspend fun getLines(charset: Charset): List<String> {
        return ProcessOutput.getLinesFrom(string(charset))
    }
}
