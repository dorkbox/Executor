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

package dorkbox.executor.exceptions

import java.io.IOException

/**
 * Creating a process failed providing an error code.
 *
 * Outputs an [IOException] like:
 *
 *  `java.io.IOException: Cannot run program "ls": java.io.IOException: error=12, Cannot allocate memory`
 *  `java.io.IOException: Cannot run program "ls": error=316, Unknown error: 316`
 */
class ProcessInitException(message: String?, cause: Throwable?, val errorCode: Int) : IOException(message, cause) {
    /**
     * @return error code raised when a process failed to start.
     */
    companion object {
        private const val BEFORE_CODE = " error="
        private const val AFTER_CODE = ", "
        private const val NEW_INFIX = " Error="

        /**
         * Try to wrap a given [Exception] into a [ProcessInitException].
         *
         * @param prefix prefix to be added in the message.
         * @param e existing exception possibly containing an error code in its message.
         *
         * @return new exception containing the prefix, error code and its description in the message plus the error code value as a field,
         * `null` if we were unable to find an error code from the original message.
         */
        fun newInstance(prefix: String? = "", e: Exception): ProcessInitException? {
            val m = e.message ?: return null
            val i = m.lastIndexOf(BEFORE_CODE)
            if (i == -1) {
                return null
            }

            val j = m.indexOf(AFTER_CODE, i)
            if (j == -1) {
                return null
            }

            val code = try {
                m.substring(i + BEFORE_CODE.length, j).toInt()
            } catch (n: NumberFormatException) {
                return null
            }

            return ProcessInitException(prefix + NEW_INFIX + m.substring(i + BEFORE_CODE.length), e, code)
        }
    }
}
