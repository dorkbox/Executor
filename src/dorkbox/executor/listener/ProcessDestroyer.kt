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

/**
 * Destroys all registered [java.lang.Process] after a certain event, typically when the VM exits
 *
 * @see ShutdownHookProcessDestroyer
 */
interface ProcessDestroyer {
    /**
     * Returns `true` if the specified [java.lang.Process] was successfully added to the list of processes to be destroy.
     *
     * @param process the process to add
     *
     * @return `true` if the specified [java.lang.Process] was successfully added
     */
    fun add(process: Process): Boolean

    /**
     * Returns `true` if the specified [java.lang.Process] was successfully removed from the list of processes to be destroy.
     *
     * @param process the process to remove
     *
     * @return `true` if the specified [java.lang.Process] was successfully removed
     */
    fun remove(process: Process): Boolean

    /**
     * Returns the number of registered processes.
     *
     * @return the number of register process
     */
    fun size(): Int
}
