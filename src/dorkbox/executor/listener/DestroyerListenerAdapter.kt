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

/**
 * Process event handler that wraps a process destroyer.
 *
 * @author Rein Raudjärv
 * @see ProcessDestroyer
 */
class DestroyerListenerAdapter(destroyer: ProcessDestroyer?) : ProcessListener() {
    private val destroyer: ProcessDestroyer

    override fun afterStart(process: Process, executor: Executor) {
        destroyer.add(process)
    }

    override fun afterStop(process: Process) {
        destroyer.remove(process)
    }

    init {
        requireNotNull(destroyer) { "Process destroyer must be provided." }
        this.destroyer = destroyer
    }
}
