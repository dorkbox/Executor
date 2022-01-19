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

import dorkbox.executor.samples.Loop
import dorkbox.executor.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.*

class TimeoutTest {
    private val writerLoopCommand: List<String> by lazy {
        listOf("java", TestSetup.getFile(Loop::class.java))
    }

    @Test
    fun testStartTimeout() {
        try {
            // Use timeout in case we get stuck
            val args = writerLoopCommand
            runBlocking {
                Executor()
                    .command(args)
                    .start(1, TimeUnit.SECONDS)
            }

            Assertions.fail<Void>("TimeoutException expected.")
        } catch (e: TimeoutException) {
            val message = e.message

            Assertions.assertTrue(message?.contains("1 SECOND") == true)
            Assertions.assertTrue(message?.contains(TestSetup.getFile(Loop::class.java)) == true)
        }
    }

    @Test
    fun testStartAsyncTimeout() {
        try {
            // Use timeout in case we get stuck
            val args = writerLoopCommand
            val asyncProcess = Executor()
                .command(args)
                .startAsync()

            runBlocking {
                // wait with timeout
                asyncProcess.await(1, TimeUnit.SECONDS)
            }

            Assertions.fail<Void>("TimeoutException expected.")
        } catch (e: TimeoutException) {
            Assertions.assertTrue(e.message?.contains("1 SECOND") == true)
        }
    }

    /**
     * This is a test copied from https://github.com/zeroturnaround/zt-exec/issues/56
     */
    @Test
    fun testExecuteTimeoutIssue56_Sync() {
        try {
            val commands: MutableList<String> = ArrayList()
            if (Executor.IS_OS_WINDOWS) {
                // native sleep command is not available on Windows platform
                // mock using standard ping to localhost instead
                // (Windows ping does 4 requests which takes about 3 seconds)
                commands.add("ping")
                commands.add("127.0.0.1")
            }
            else {
                commands.add("sleep")
                commands.add("3")
            }

            runBlocking {
                Executor()
                    .command(commands)
                    .start(1, TimeUnit.SECONDS)
            }

            Assertions.fail<Void>("TimeoutException expected.")
        } catch (e: TimeoutException) {
            Assertions.assertTrue(e.message?.contains("1 SECOND") == true)
        }
    }

    /**
     * This is a test copied from https://github.com/zeroturnaround/zt-exec/issues/56
     */
    @Test
    fun testStartTimeoutIssue56_Async() {
        try {
            val commands: MutableList<String> = ArrayList()
            if (Executor.IS_OS_WINDOWS) {
                // native sleep command is not available on Windows platform
                // mock using standard ping to localhost instead
                // (Windows ping does 4 requests which takes about 3 seconds)
                commands.add("ping")
                commands.add("127.0.0.1")
            }
            else {
                commands.add("sleep")
                commands.add("3")
            }

            val job = Executor()
                .command(commands)
                .startAsync()


            runBlocking {
                // wait with timeout
                job.await(1, TimeUnit.SECONDS)
            }

            Assertions.fail<Void>("TimeoutException expected.")
        } catch (e: TimeoutException) {
            Assertions.assertTrue(e.message?.contains("1 SECOND") == true)
        }
    }
}
