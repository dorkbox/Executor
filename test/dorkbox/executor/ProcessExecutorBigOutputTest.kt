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

import dorkbox.executor.samples.BigOutput
import dorkbox.executor.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Tests reading large output that doesn't fit into a buffer between this process and sub process.
 *
 * @author Rein Raudjärv
 *
 * @see Executor
 *
 * @see BigOutput
 */
class ProcessExecutorBigOutputTest {
    companion object {
        private fun repeat(s: String): String {
            val sb = StringBuffer(BigOutput.LENGTH)
            for (i in 0 until BigOutput.LENGTH) sb.append(s)
            return sb.toString()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDevNull() {
        runBlocking {
            bigOutput().start()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDevNullAsShell() {
        runBlocking {
            // this is faster, because when as a shell, the process is piped to null instead, so there is no output
            bigOutput().startAsShell()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDevNullSeparate() {
        runBlocking {
            bigOutput().redirectErrorStream(false)
                .start()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testReadOutputAndError() {
        val output: String = bigOutput()
                                .readOutput()
                                .highPerformanceIO()
                                .startBlocking()
                                .output.utf8()

        Assert.assertEquals(repeat("+-"), output)
    }

    @Test
    @Throws(Exception::class)
    fun testReadOutputOnly() {
        val output: String = bigOutput()
                                .readOutput()
                                .redirectErrorStream(false)
                                .startBlocking()
                                .output.utf8()

        Assert.assertEquals(repeat("+"), output)
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectOutputOnly() {
        val out = ByteArrayOutputStream()

        bigOutput().redirectOutput(out)
            .redirectErrorStream(false)
            .startBlocking()

        Assert.assertEquals(repeat("+"), String(out.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectErrorOnly() {
        val err = ByteArrayOutputStream()

        bigOutput().redirectError(err)
            .redirectErrorStream(false)
            .startBlocking()

        Assert.assertEquals(repeat("-"), String(err.toByteArray()))
    }

    private fun bigOutput(): Executor {
        // Use timeout in case we get stuck
        return Executor("java", TestSetup.getFile(BigOutput::class.java))
            .timeout(1, TimeUnit.MINUTES)
    }
}
