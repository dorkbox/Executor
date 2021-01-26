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
 * Exit value of a finished process.
 *
 * @param exitValue Exit value of the finished process.
 */
class NopProcessResult(pid: Long, exitValue: Int) : SyncProcessResult(pid, exitValue, "".toByteArray()) {
    /**
     * @return true if this result has output
     */
    override val hasOutput: Boolean = false

    /**
     * @return output of the finished process.
     *
     * You must invoke [dorkbox.executor.Executor.readOutput] to allow the process output to be read.
     *
     * @throws IllegalStateException if reading the output was not enabled.
     */
    @get:Throws(IllegalStateException::class)
    override val output: ProcessOutput
        get() {
            throw IllegalStateException("Process output was not read. " +
                                        "To enable output reading please call ProcessExecutor.readOutput() before starting the process.")
        }
}
