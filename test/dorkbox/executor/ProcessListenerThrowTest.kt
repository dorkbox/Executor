/*
 * Copyright 2020 dorkbox, llc
 * Copyright (C) 2014 ZeroTurnaround <support@zeroturnaround.com>

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

import dorkbox.executor.SuccessTest.ProcessListenerImpl
import dorkbox.executor.exceptions.InvalidOutputException
import dorkbox.executor.processResults.ProcessResult
import dorkbox.executor.processResults.SyncProcessResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProcessListenerThrowTest {

    @Test(expected = InvalidOutputException::class)
    @Throws(Exception::class)
    fun testJavaVersion() {
        runBlocking {
            Executor("java", "-version")
                .enableRead()
                .addListener(ProcessListenerThrowImpl())
                .start()
        }
    }

    @Test(expected = InvalidOutputException::class)
    @Throws(Exception::class)
    fun testJavaVersionWithTimeout() {
        runBlocking {
            Executor("java", "-version")
                .enableRead()
                .addListener(ProcessListenerThrowImpl())
                .timeout(1, TimeUnit.MINUTES)
                .start()
        }
    }

    private class ProcessListenerThrowImpl : ProcessListenerImpl() {
        override fun afterFinish(process: Process, result: ProcessResult) {
            super.afterFinish(process, result)

            val string = (result as SyncProcessResult).output.string()
            if (string.contains("java version") || string.contains("openjdk version")) {
                throw InvalidOutputException("Test", result)
            }
        }
    }
}
