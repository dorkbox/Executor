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

package dorkbox.executor.test

import dorkbox.executor.Executor
import dorkbox.executor.test.samples.HelloWorld
import dorkbox.executor.test.samples.TestSetup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Test

class ProcessExecutorCancelJavaTest {
    private val javaCommand: String by lazy {
        TestSetup.getFile(HelloWorld::class.java)
    }

    @Test
    fun testStart() {
        runBlocking {
            val asyncProcess = Executor()
                .readOutput()
                .asJvmProcess()
                .setMainClass(javaCommand)
                .start().output.utf8()

            println(asyncProcess)
            Assert.assertEquals(asyncProcess, "Hello world!")
        }
    }

    @Test
    fun testStartAsyncCancel() {
        val exceptionMessage = "CANCELLED ASYNC"

        // Use timeout in case we get stuck
        val asyncProcess = Executor()
            .asJvmProcess()
            .setMainClass(javaCommand)
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
