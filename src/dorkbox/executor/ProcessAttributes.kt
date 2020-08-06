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

package dorkbox.executor

import java.io.File

/**
 * Immutable set of attributes used to start a process.
 */
internal class ProcessAttributes(
        /**
         * The external program and its arguments.
         */
        val command: List<String>,

        /**
         * Working directory, `null` in case of current working directory.
         */
        val directory: File?,

        /**
         * Environment variables which are added (removed in case of `null` values) to the started process.
         */
        val environment: Map<String, String?>,

        /**
         * Set of accepted exit codes or `null` if all exit codes are allowed.
         */
        val allowedExitValues: Set<Int> = setOf())
