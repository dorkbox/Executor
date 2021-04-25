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
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTICE: This file originates from the Apache Commons Exec package.
 * It has been modified to fit our needs.
 *
 * The following is the original header of the file in Apache Commons Exec:
 *
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dorkbox.executor.listener

import org.slf4j.LoggerFactory

/**
 * Destroys all registered Processes when the VM exits.
 *
 * This class is copied from `Commons Exec`.
 *
 * Constructs a `ProcessDestroyer` and obtains
 * `Runtime.addShutdownHook()` and
 * `Runtime.removeShutdownHook()` through reflection.
 *
 * The ProcessDestroyer manages a list of processes to be destroyed when the VM exits.
 *
 * If a process is added when the list is empty, this [ProcessDestroyer] is registered as a shutdown hook.
 * If removing a process results in an empty list, the [ProcessDestroyer] is removed as a shutdown hook.
 */
class ShutdownHookProcessDestroyer : ProcessDestroyer, Runnable {
    companion object {
        private val log = LoggerFactory.getLogger(ShutdownHookProcessDestroyer::class.java)

        /**
         * Singleton instance of the [ShutdownHookProcessDestroyer].
         */
        val INSTANCE: ProcessDestroyer = ShutdownHookProcessDestroyer()
    }

    /**
     * the list of currently running processes
     */
    private val processes = mutableListOf<Process>()

    /**
     * The thread registered at the JVM to execute the shutdown handler
     */
    private var destroyProcessThread: ProcessDestroyerImpl? = null

    /**
     * Returns whether or not the ProcessDestroyer is registered as as shutdown
     * hook
     *
     * @return true if this is currently added as shutdown hook
     */
    /**
     * Whether or not this ProcessDestroyer has been registered as a shutdown hook
     */
    var isAddedAsShutdownHook = false
        private set

    /**
     * Whether the shut down hook routine was already run
     */
    @Volatile
    private var shutDownHookExecuted = false

    /**
     * Whether or not this ProcessDestroyer is currently running as shutdown hook
     */
    @Volatile
    private var running = false

    private inner class ProcessDestroyerImpl : Thread("ProcessDestroyer Shutdown Hook") {
        private var shouldDestroy = true

        override fun run() {
            if (shouldDestroy) {
                this@ShutdownHookProcessDestroyer.run()
            }
        }

        fun setShouldDestroy(shouldDestroy: Boolean) {
            this.shouldDestroy = shouldDestroy
        }
    }

    /**
     * Registers this `ProcessDestroyer` as a shutdown hook
     */
    private fun addShutdownHook() {
        if (!running) {
            destroyProcessThread = ProcessDestroyerImpl()
            Runtime.getRuntime().addShutdownHook(destroyProcessThread)
            isAddedAsShutdownHook = true
        }
    }

    /**
     * Removes this [ProcessDestroyer] as a shutdown hook
     */
    private fun removeShutdownHook() {
        if (isAddedAsShutdownHook && !running) {
            val removed = Runtime.getRuntime().removeShutdownHook(destroyProcessThread)

            if (!removed) {
                log.error("Could not remove shutdown hook")
            }

            /*
             * start the hook thread, a un-started thread may not be eligible for garbage collection
             *
             * see: http://developer.java.sun.com/developer/bugParade/bugs/4533087.html
             */
            destroyProcessThread!!.setShouldDestroy(false)
            destroyProcessThread!!.start()

            // this should return quickly, since it basically is a NO-OP.
            try {
                destroyProcessThread!!.join(20000)
            } catch (ie: InterruptedException) {
                // the thread didn't die in time
                // it should not kill any processes unexpectedly
            }

            destroyProcessThread = null
            isAddedAsShutdownHook = false
        }
    }

    /**
     * Returns `true` if the specified [Process] was successfully added to the list of processes to destroy upon VM exit.
     *
     * @param process the process to add
     *
     * @return `true` if the specified [Process] was successfully added
     */
    override fun add(process: Process): Boolean {
        synchronized(processes) {
            // if this list is empty, register the shutdown hook
            if (processes.size == 0) {
                try {
                    check(!shutDownHookExecuted)
                    addShutdownHook()
                } // kill the process now if the JVM is currently shutting down
                catch (e: IllegalStateException) {
                    destroy(process)
                }
            }

            processes.add(process)
            return processes.contains(process)
        }
    }

    /**
     * Returns `true` if the specified [Process] was successfully removed from the list of processes to destroy upon VM exit.
     *
     * @param process the process to remove
     *
     * @return `true` if the specified [Process] was successfully removed
     */
    override fun remove(process: Process): Boolean {
        synchronized(processes) {
            val processRemoved = processes.remove(process)
            if (processRemoved && processes.size == 0) {
                try {
                    removeShutdownHook()
                } catch (e: IllegalStateException) {
                    /* if the JVM is shutting down, the hook cannot be removed */
                    shutDownHookExecuted = true
                }
            }
            return processRemoved
        }
    }

    /**
     * Returns the number of registered processes.
     *
     * @return the number of register process
     */
    override fun size(): Int {
        return processes.size
    }

    /**
     * Invoked by the VM when it is exiting.
     */
    override fun run() {
        /* check if running the routine is still necessary */
        if (shutDownHookExecuted) {
            return
        }

        synchronized(processes) {
            running = true

            processes.forEach {
                destroy(it)
            }

            processes.clear()
            shutDownHookExecuted = true
        }
    }

    private fun destroy(process: Process) {
        try {
            process.destroy()
        } catch (t: Throwable) {
            log.error("Unable to terminate process during process shutdown")
        }
    }
}
