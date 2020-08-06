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
import dorkbox.executor.exceptions.InvalidExitValueException
import dorkbox.executor.test.samples.ExitLikeABoss
import dorkbox.executor.test.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProcessExecutorExitValueTest {
    companion object {
        private fun exitLikeABoss(exitValue: Int): List<String> {
            return listOf("java", TestSetup.getFile(ExitLikeABoss::class.java), exitValue.toString())
        }
    }

    @Test(expected = InvalidExitValueException::class)
    @Throws(Exception::class)
    fun testJavaVersionExitValueCheck() {
        runBlocking {
            Executor()
                .command("java", "-version")
                .exitValues(3)
                .start()
        }
    }

    @Test(expected = InvalidExitValueException::class)
    @Throws(Exception::class)
    fun testJavaVersionExitValueCheckTimeout() {
        runBlocking {
            Executor()
                .command("java", "-version")
                .exitValues(3)
                .timeout(60, TimeUnit.SECONDS)
                .start()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNonZeroExitValueByDefault() {
        runBlocking {
            Executor(exitLikeABoss(17))
                .start()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testCustomExitValueValid() {
        runBlocking {
            Executor(exitLikeABoss(17))
                .exitValues(17)
                .start()
        }
    }

    @Test(expected = InvalidExitValueException::class)
    @Throws(Exception::class)
    fun testCustomExitValueInvalid() {
        runBlocking {
            Executor(exitLikeABoss(17))
                .exitValues(15)
                .start()
        }
    }
}
