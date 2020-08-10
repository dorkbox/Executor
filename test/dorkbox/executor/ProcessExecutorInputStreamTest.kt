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

import dorkbox.executor.samples.PrintArguments
import dorkbox.executor.samples.PrintInputToOutput
import dorkbox.executor.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit

class ProcessExecutorInputStreamTest {
    @Test
    @Throws(Exception::class)
    fun testWritingToProcessAndReadingAfterProcessQuit() {
        val str = "Tere Minu Uus vihik"
        val exec = Executor("java", TestSetup.getFile(PrintInputToOutput::class.java))
        exec.enableRead()

        val output = runBlocking {
            val async = exec.startAsShellAsync()

            async.writeLine(str)
            async.write("\n\n\n")
            async.await()
            async.output.utf8()
        }

        Assert.assertEquals(str, output)
    }

    @Test
    @Throws(Exception::class)
    fun testWithInputAndRedirectOutput() {
        val str = "Tere Minu Uus vihik"
        val bais = ByteArrayInputStream(str.toByteArray() + "\n\n\n".toByteArray()) // PrintInputToOutput processes at most 3 lines. triggers the java side to exit
        val baos = ByteArrayOutputStream()

        val exec = Executor("java", TestSetup.getFile(PrintInputToOutput::class.java))
        exec.redirectInput(bais)
            .redirectOutput(baos)

        runBlocking {
            exec.start()
        }

        Assert.assertEquals(str, baos.toString())
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectPipedInputStream() {
        // Setup InputStream that will block on a read()
        val pos = PipedOutputStream()
        val pis = PipedInputStream(pos)

        val exec = Executor("java", TestSetup.getFile(PrintArguments::class.java))
        exec.redirectInput(pis)
        val startedProcess = exec.startAsync()


        // Assert that we don't get a TimeoutException
        startedProcess.awaitBlocking(5, TimeUnit.SECONDS)
    }

    @Test
    @Throws(Exception::class)
    fun testDataIsFlushedToProcessWithANonEndingInputStream() {
        val str = "Tere Minu Uus vihik " + System.nanoTime()

        // Setup InputStream that will block on a read()
        val pos = PipedOutputStream()
        val pis = PipedInputStream(pos)

        val baos = ByteArrayOutputStream()

        val exec = Executor("java", TestSetup.getFile(PrintInputToOutput::class.java))
        exec.redirectInput(pis)
            .redirectOutput(baos)

        val startedProcess = exec.startAsync()

        pos.write(str.toByteArray())
        pos.write("\n\n\n".toByteArray()) // PrintInputToOutput processes at most 3 lines. triggers the java side to exit

        // Assert that we don't get a TimeoutException
        startedProcess.awaitBlocking(5, TimeUnit.SECONDS)

        Assert.assertEquals(str, baos.toString())
    }
}
