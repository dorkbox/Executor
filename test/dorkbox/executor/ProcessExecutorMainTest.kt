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

import dorkbox.executor.listener.ProcessDestroyer
import dorkbox.executor.samples.HelloWorld
import dorkbox.executor.samples.TestSetup
import dorkbox.executor.stream.IOStreamHandler
import dorkbox.executor.stream.slf4j.Slf4jStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.*

class MockProcessDestroyer : ProcessDestroyer {
    @Volatile
    var added: Process? = null

    @Volatile
    var removed: Process? = null

    override fun add(process: Process): Boolean {
        added = process
        return true
    }

    override fun remove(process: Process): Boolean {
        removed = process
        return true
    }

    override fun size(): Int {
        return 0
    }
}

class ProcessExecutorMainTest {
    @Test(expected = IllegalStateException::class)
//    @Throws(Exception::class)
    fun testNoCommand() {
        runBlocking {
            Executor()
                .start()
        }
    }

    @Test(expected = IOException::class)
    fun testNoSuchFile() {
        runBlocking {
            Executor()
                .command("unknown command")
                .start()
        }
    }

    @Test
    fun testJavaVersionAsShell() {
        val exit: Int = runBlocking {
            Executor()
                .command("java", "-version")
                .startAsShell()
                .getExitValue()
        }

        Assert.assertEquals(0, exit.toLong())
    }

    @Test
    fun testJavaVersion() {
        val exit: Int = runBlocking {
            Executor()
                .command("java", "-version")
                .start()
                .getExitValue()
        }

        Assert.assertEquals(0, exit.toLong())
    }

    @Test
    fun testJavaVersionCommandSplit() {
        val exit: Int = runBlocking {
            Executor()
                .commandSplit("java -version")
                .start()
                .getExitValue()
        }

        Assert.assertEquals(0, exit.toLong())
    }

    @Test
    fun testJavaVersionIterable() {
        val iterable: Iterable<String> = Arrays.asList("java", "-version")
        val exit: Int = runBlocking {
            Executor()
                .command(iterable)
                .start()
                .getExitValue()
        }

        Assert.assertEquals(0, exit.toLong())
    }

    @Test
    fun testJavaVersionFuture() {
        val exit: Int = Executor()
            .command("java", "-version")
            .startAsync()
            .awaitBlocking()
            .getExitValue()
        Assert.assertEquals(0, exit.toLong())
    }

    @Test
    fun testJavaVersionOutput() {
        val result = runBlocking {
            Executor()
                .command("java", "-version")
                .readOutput()
                .start()
        }

        val str: String = result.output.utf8()
        Assert.assertFalse(str.isEmpty())
    }

    @Test
    fun testJavaVersionOutputTwice() {
        val executor: Executor = Executor()
            .command("java", "-version")
            .readOutput()

        val result = runBlocking {
            executor.start()
        }

        val str: String = result.output.utf8()
        Assert.assertFalse(str.isEmpty())

        val utf8 = runBlocking {
            executor.start().output.utf8()
        }

        Assert.assertEquals(str, utf8)
    }

    @Test
    fun testJavaVersionOutputFuture() {
        val result = Executor()
            .command("java", "-version")
            .readOutput()
            .startAsync()
            .awaitBlocking()

        val str: String = result.output.utf8()
        Assert.assertFalse(str.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun testJavaVersionLogInfo() {
        // Just expect no errors - don't check the log file itself
        runBlocking {
            Executor()
                .command("java", "-version")
                .redirectOutput(Slf4jStream.asInfo())
                .start()
        }
    }

    @Test
    fun testJavaVersionLogInfoAndOutput() {
        // Just expect no errors - don't check the log file itself
        val result = runBlocking {
            Executor()
                .command("java", "-version")
                .redirectOutput(Slf4jStream.asInfo())
                .readOutput()
                .start()
        }

        val str: String = result.output.utf8()
        Assert.assertFalse(str.isEmpty())
    }

    @Test
    fun testJavaVersionLogInfoAndOutputFuture() {
        // Just expect no errors - don't check the log file itself
        val result = Executor()
            .command("java", "-version")
            .redirectOutput(Slf4jStream.asInfo())
            .readOutput()
            .startAsync()
            .awaitBlocking()

        val str: String = result.output.utf8()
        Assert.assertFalse(str.isEmpty())
    }

    @Test
    fun testJavaVersionAsync() {
        // Just expect no errors
        val result = Executor()
            .command("java", "-version")
            .readOutput()
            .startAsync()

        runBlocking {
            val fullOutput = mutableListOf<String>()

            val output = result.output
            while (output.isOpen) {
                fullOutput.add(output.utf8())
            }

            val str: String = fullOutput.joinToString()
            Assert.assertFalse(str.isEmpty())
        }
    }

    @Test
    fun testJavaVersionNoStreams() {
        // Just expect no errors
        runBlocking {
            Executor()
                .command("java", "-version")
                .start()
        }
    }

    @Test
    fun testProcessDestroyerEvents() {
        val mock = MockProcessDestroyer()
        runBlocking {
            Executor()
                .command("java", "-version")
                .destroyer(mock)
                .start()
        }

        Assert.assertNotNull(mock.added)
        Assert.assertEquals(mock.added, mock.removed)
    }

    @Test
    fun testProcessDestroyerEventsOnStreamsFail() {
        val mock = MockProcessDestroyer()
        val streams: IOStreamHandler = object : IOStreamHandler() {
            override fun start(process: Process, separateErrorStream: Boolean, highPerformanceIO: Boolean) {
                throw IOException()
            }
        }

        try {
            runBlocking {
                Executor()
                    .command("java", "-version")
                    .streams(streams)
                    .destroyer(mock)
                    .start()
            }

            Assert.fail("IOException expected")
        } catch (e: IOException) {
            // Good
        }

        Assert.assertNull(mock.added)
        Assert.assertNull(mock.removed)
    }

    @Test
    @Throws(Exception::class)
    fun testProcessExecutorListInit() {
        // Use timeout in case we get stuck
        val args = listOf("java", TestSetup.getFile(HelloWorld::class.java))

        val exec = Executor(args)
        val result = runBlocking {
            exec.readOutput()
                .start()
        }

        Assert.assertEquals("Hello world!", result.output.utf8())
    }


    @Test
    @Throws(Exception::class)
    fun testProcessExecutorCommand() {
        // Use timeout in case we get stuck
        val args = listOf("java", TestSetup.getFile(HelloWorld::class.java))

        val exec = Executor()
        exec.command(args)

        val result = runBlocking {
            exec.readOutput()
                .start()
        }
        Assert.assertEquals("Hello world!", result.output.utf8())
    }

    @Test
    @Throws(Exception::class)
    fun testProcessExecutorSetDirectory() {
        // Use timeout in case we get stuck
        val args = listOf("java", TestSetup.getFile(HelloWorld::class.java))

        val exec: Executor = Executor()
            .workingDirectory(TestSetup.getParentDir(HelloWorld::class.java))
        exec.command(args)

        val result = runBlocking {
            exec.readOutput()
                .start()
        }

        Assert.assertEquals("Hello world!", result.output.utf8())
    }
}
