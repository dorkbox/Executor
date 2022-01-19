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

import dorkbox.executor.exceptions.InvalidExitValueException
import dorkbox.executor.samples.ExitLikeABoss
import dorkbox.executor.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.*

class ExitValueTest {
    companion object {
        private fun exitLikeABoss(exitValue: Int): List<String> {
            return listOf("java", TestSetup.getFile(ExitLikeABoss::class.java), exitValue.toString())
        }
    }

    @Test
    @Throws(Exception::class)
    fun testJavaVersionExitValueCheck() {
        Assertions.assertThrows(InvalidExitValueException::class.java) {
            runBlocking {
                Executor().command("java", "-version").exitValues(3).start()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testJavaVersionExitValueCheckTimeout() {
        Assertions.assertThrows(InvalidExitValueException::class.java) {
            runBlocking {
                Executor().command("java", "-version").exitValues(3).start(60, TimeUnit.SECONDS)
            }
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

    @Test
    @Throws(Exception::class)
    fun testCustomExitValueInvalid() {
        Assertions.assertThrows(InvalidExitValueException::class.java) {
            runBlocking {
                Executor(exitLikeABoss(17)).exitValues(15).start()
            }
        }
    }
}
