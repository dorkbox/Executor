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

import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.*

/**
 * Standard output of a finished process.
 *
 * @param bytes_ Process output as bytes
 */
class ProcessOutput(private val bytes_: ByteArray) {
    companion object {
        fun getLinesFrom(output: String): List<String> {
            // NOTE: this DOES NOT work if there are double-empty-lines in the middle.
            //  It only trims the start/end so we do not use this!
            // return output.trim().lines()

            val result = mutableListOf<String>()

            // Split using both Windows and UNIX line separators (this actually works)
            val st = StringTokenizer(output, "\r\n")
            while (st.hasMoreTokens()) {
                result.add(st.nextToken())
            }
            return result
        }
    }

    /**
     * @return output of the finished process converted to a String using platform's default encoding.
     */
    fun string(): String {
            return String(bytes_)
        }

    /**
     * @return output of the finished process converted to UTF-8 String.
     */
    fun utf8(): String {
            return getString(Charsets.UTF_8)
        }

    /**
     * @param charset The name of a supported char set.
     *
     * @return output of the finished process converted to a String.
     *
     * @throws IllegalStateException if the char set was not supported.
     */
    fun getString(charset: Charset): String {
        return try {
            String(bytes_, charset)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e.message)
        }
    }

    /**
     * @return output lines of the finished process converted using platform's default encoding.
     */
    fun lines(): List<String> {
        return getLinesFrom(string())
    }

    /**
     * @return output lines of the finished process converted using UTF-8.
     */
    fun linesAsUtf8(): List<String> {
            return getLinesFrom(utf8())
        }

    /**
     * @param charset The name of a supported char set.
     *
     * @return output lines of the finished process converted using a given char set.
     */
    fun getLines(charset: Charset): List<String> {
        return getLinesFrom(getString(charset))
    }
}
