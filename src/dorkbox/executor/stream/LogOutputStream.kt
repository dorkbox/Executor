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

package dorkbox.executor.stream

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Base class to connect a logging system to the output and/or
 * error stream of then external process. The implementation
 * parses the incoming data to construct a line and passes
 * the complete line to an user-defined implementation.
 */
abstract class LogOutputStream : OutputStream() {
    companion object {
        /**
         * Initial buffer size.
         */
        private const val INTIAL_SIZE = 132

        /**
         * Carriage return
         */
        private const val CR = 0x0d.toByte()

        /**
         * Linefeed
         */
        private const val LF = 0x0a.toByte()
    }

    /**
     * the internal buffer
     */
    private val buffer = ByteArrayOutputStream(INTIAL_SIZE)

    var lastReceivedChar: Char = ' '

    /**
     * Write the data to the buffer and flush the buffer, if a line separator is
     * detected.
     *
     * @param cc data to log (byte).
     *
     * @see java.io.OutputStream.write
     */
    @Throws(IOException::class)
    override fun write(cc: Int) {
        val c = cc.toChar()
        if (c == '\n' || c == '\r') {
            // new line is started in case of
            // - CR (regardless of previous character)
            // - LF if previous character was not CR and not LF
            if (c == '\r' ||
                c == '\n' && lastReceivedChar != '\r' && lastReceivedChar != '\n') {
                processBuffer()
            }
        } else {
            buffer.write(cc)
        }
        lastReceivedChar = c
    }

    /**
     * Flush this log stream.
     *
     * @see java.io.OutputStream.flush
     */
    override fun flush() {
        if (buffer.size() > 0) {
            processBuffer()
        }
    }

    /**
     * Writes all remaining data from the buffer.
     *
     * @see java.io.OutputStream.close
     */
    @Throws(IOException::class)
    override fun close() {
        if (buffer.size() > 0) {
            processBuffer()
        }
        super.close()
    }

    /**
     * Write a block of characters to the output stream
     *
     * @param b the array containing the data
     * @param off the offset into the array where data starts
     * @param len the length of block
     *
     * @throws java.io.IOException if the data cannot be written into the stream.
     * @see java.io.OutputStream.write
     */
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        // find the line breaks and pass other chars through in blocks
        var offset = off
        var blockStartOffset = offset
        var remaining = len
        while (remaining > 0) {
            while (remaining > 0 && b[offset] != LF && b[offset] != CR) {
                offset++
                remaining--
            }
            // either end of buffer or a line separator char
            val blockLength = offset - blockStartOffset
            if (blockLength > 0) {
                buffer.write(b, blockStartOffset, blockLength)
                lastReceivedChar = ' '
            }
            while (remaining > 0 && (b[offset] == LF || b[offset] == CR)) {
                write(b[offset].toInt())
                offset++
                remaining--
            }
            blockStartOffset = offset
        }
    }

    /**
     * Converts the buffer to a string and sends it to `processLine`.
     */
    protected fun processBuffer() {
        processLine(buffer.toString())
        buffer.reset()
    }

    /**
     * Logs a line to the log system of the user.
     *
     * @param line the line to log.
     */
    protected abstract fun processLine(line: String)
}
