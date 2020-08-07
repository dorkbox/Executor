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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Test
import java.util.*

class ProcessExecutorCancelTest {
    private val writerLoopCommand: List<String> by lazy {
        listOf("java", TestSetup.getFile(Loop::class.java))
    }

    @Test
    fun testStartAsyncCancelWithoutAwait() {
        try {
            // Use timeout in case we get stuck
            val args = writerLoopCommand
            val asyncProcess = Executor()
                .command(args)
                .startAsync()

            runBlocking {
                asyncProcess.cancel("took too long!")
            }

            Assert.fail("TimeoutException expected.")
        } catch (e: IllegalStateException) {
            Assert.assertEquals(e.message, "Unable to cancel a process is not waiting.")
        }
    }

    @Test
    fun testStartAsyncCancel() {
        val exceptionMessage = "CANCELLED ASYNC"

        // Use timeout in case we get stuck
        val args = writerLoopCommand
        val asyncProcess = Executor()
            .command(args)
            .startAsync()

        runBlocking {
            async {
                // wait
                try {
                    asyncProcess.await()
                    Assert.fail("CancellationException expected.")
                } catch (e: CancellationException) {
                    MatcherAssert.assertThat(e.message, CoreMatchers.containsString(exceptionMessage))
                }
            }

            launch {
                asyncProcess.cancel(exceptionMessage)
            }
        }
    }

    /**
     * This is a test copied from https://github.com/zeroturnaround/zt-exec/issues/56
     */
    @Test
    fun testStartTimeoutIssue56_Async() {
        val exceptionMessage = "CANCELLED ASYNC"

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

        val asyncProcess = Executor()
            .command(commands)
            .startAsync()


        runBlocking {
            async {
                // wait
                try {
                    asyncProcess.await()
                    Assert.fail("CancellationException expected.")
                } catch (e: CancellationException) {
                    MatcherAssert.assertThat(e.message, CoreMatchers.containsString(exceptionMessage))
                }
            }

            launch {
                asyncProcess.cancel(exceptionMessage)
            }
        }
    }
}
