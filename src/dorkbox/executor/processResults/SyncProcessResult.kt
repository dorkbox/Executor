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

/**
 * Exit value and output of a finished process.
 *
 * @param exitValue Exit value of the finished process.
 * @param out Process output or `null` if it wasn't read.
 */
open class SyncProcessResult(
        /**
         * @return the PID for the currently running process. This doesn't make sense for remotely executed processes (which return 0)
         */
        val pid: Long,

        /**
         * @return the exit value of the finished process.
         */
        override val exitValue: Int,
        private val out: ByteArray) : ProcessResult {


    /**
     * @return true if this result has output
     */
    override val hasOutput: Boolean = true

    /**
     * @return output of the finished process.
     *
     * You must invoke [dorkbox.executor.Executor.readOutput] to allow the process output to be read.
     *
     * @throws IllegalStateException if reading the output was not enabled.
     */
    @get:Throws(IllegalStateException::class)
    open val output: ProcessOutput by lazy {
        ProcessOutput(out)
    }
}
