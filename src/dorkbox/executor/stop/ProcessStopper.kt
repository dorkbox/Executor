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

package dorkbox.executor.stop

/**
 * Abstraction for stopping sub processes.
 *
 *
 * This is used in case a process runs too long (timeout is reached) or it's cancelled via [Future.cancel].
 */
interface ProcessStopper {
    /**
     * Stops a given sub process.
     *
     * It does not wait for the process to actually stop and it has no guarantee that the process terminates.
     *
     * @param process sub process being stopped (not `null`).
     */
    fun stop(process: Process)
}
