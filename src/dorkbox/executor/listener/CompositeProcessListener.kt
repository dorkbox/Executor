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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Composite process event handler.
 *
 * @author Rein Raudjärv
 */
class CompositeProcessListener : ProcessListener, Cloneable {
    private val children: MutableList<ProcessListener> = CopyOnWriteArrayList()

    constructor() {
        // no children
    }

    constructor(children: List<ProcessListener>) {
        this.children.addAll(children)
    }

    /**
     * Add new listener.
     *
     * @param listener listener to be added.
     */
    fun add(listener: ProcessListener) {
        children.add(listener)
    }

    /**
     * Remove existing listener.
     *
     * @param listener listener to be removed.
     */
    fun remove(listener: ProcessListener) {
        children.remove(listener)
    }

    /**
     * Remove existing listeners of given type or its sub-types.
     *
     * @param type listener type.
     */
    fun removeAll(type: Class<out ProcessListener>) {
        val it = children.iterator()
        while (it.hasNext()) {
            if (type.isInstance(it.next())) {
                it.remove()
            }
        }
    }

    /**
     * Remove all existing listeners.
     */
    fun clear() {
        children.clear()
    }

    public override fun clone(): CompositeProcessListener {
        return CompositeProcessListener(children)
    }

    override fun beforeStart(executor: Executor) {
        children.forEach {
            it.beforeStart(executor)
        }
    }

    override fun afterStart(process: Process, executor: Executor) {
        children.forEach {
            it.afterStart(process, executor)
        }
    }

    override fun afterFinish(process: Process, result: ProcessResult) {
        children.forEach {
            it.afterFinish(process, result)
        }
    }

    override fun afterStop(process: Process) {
        children.forEach {
            it.afterStop(process)
        }
    }
}
