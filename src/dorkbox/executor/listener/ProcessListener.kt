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

package dorkbox.executor.listener

import dorkbox.executor.Executor
import dorkbox.executor.processResults.ProcessResult

/**
 * Event handler for process events.
 *
 * This is a class instead of interface in order to add new methods without updating all implementations.
 *
 * @author Rein Raudjärv
 *
 * @see Executor.addListener
 */
abstract class ProcessListener {
    /**
     * Invoked before a process is started.
     *
     * @param executor executor used for starting a process.
     *
     * Any changes made here apply to the starting process.
     * Once the process has started it is not affected by the [Executor] any more.
     */
    open fun beforeStart(executor: Executor) {
        // do nothing
    }

    /**
     * Invoked after a process has started.
     *
     * @param process the process started.
     * @param executor executor used for starting the process.
     *
     * Modifying the [Executor] only affects the following processes not the one just started.
     */
    open fun afterStart(process: Process, executor: Executor) {
        // do nothing
    }

    /**
     * Invoked after a process has finished successfully.
     *
     * @param process process just finished.
     * @param result result of the finished process.
     */
    open fun afterFinish(process: Process, result: ProcessResult) {
        // do nothing
    }

    /**
     * Invoked after a process has exited (whether finished or cancelled).
     *
     * @param process process just stopped.
     */
    open fun afterStop(process: Process) {
        // do nothing
    }
}
