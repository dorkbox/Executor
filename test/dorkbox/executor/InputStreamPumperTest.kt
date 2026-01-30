/*
 * Copyright 2026 dorkbox, llc
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

import dorkbox.executor.samples.PrintInputToOutput
import dorkbox.executor.samples.TestSetup
import dorkbox.executor.stream.PumpStreamHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests that test redirected input for the process to be run.
 */
class InputStreamPumperTest {
    @Test
    @Throws(Exception::class)
    fun testPumpFromInputToOutput() {
        val str = "Tere Minu Uus vihik"
        val bais = ByteArrayInputStream((str + "\n\n\n\n").toByteArray()) // 3\n necessary to tell the java side to stop
        val baos = ByteArrayOutputStream()
        val handler = PumpStreamHandler(baos, System.err, bais)

        val exec = Executor("java", TestSetup.getFile(PrintInputToOutput::class.java))
            .enableRead()
        exec.streams(handler)

        val result: String = runBlocking {
            exec.start().output.utf8()
        }

        Assert.assertEquals(str, result)
    }

    @Test
    @Throws(Exception::class)
    fun testPumpFromInputToOutputWithInput() {
        val str = "Tere Minu Uus vihik"
        val bais = ByteArrayInputStream((str + "\n\n\n\n").toByteArray()) // 3\n necessary to tell the java side to stop

        val exec = Executor("java", TestSetup.getFile(PrintInputToOutput::class.java))
            .enableRead()
            .redirectInput(bais)

        val result: String = runBlocking {
            exec.start().output.utf8()
        }

        Assert.assertEquals(str, result)
    }


    @Test
    fun testConstantReadOutput() {
        val exec = Executor("java", TestSetup.getFile(PrintInputToOutput::class.java))
            .enableRead()
//            .highPerformanceIO()

        val output = runBlocking {
            val async = exec.startAsShellAsync()

            launch {
                (0..10).forEach {
                    // our test uses a buffered input stream reader, so we have to write full lines for it to process.
                    // this is an implementation quirk. This is only necessary in this specific example.
                    async.writeLine("Testing: $it")
                    delay(1000L)
                }
                async.write("\n\n\n")
            }

            println("Gathering the values")
            while (async.output.isOpen) {
                print(async.output.utf8())
            }
            println("Done")

            async.await()
            async.output.utf8()
        }

        Assert.assertEquals("", output)
    }
    @Test
    fun testConstantReadOutputBuffered() {
        val exec = Executor("java", TestSetup.getFile(PrintInputToOutput::class.java))
            .enableRead()
//            .highPerformanceIO()


        val output = runBlocking {
            val async = exec.startAsShellAsync()

            launch {
                (0..10).forEach {
                    async.writeLine("Testing the next value: $it")
                    delay(1000L)
                }
                async.write("\n\n\n")
            }

            println("Gathering the values")
            while (async.output.isOpen) {
                println(async.output.utf8Buffered())
            }
            println("xxx")

            async.await()
            async.output.utf8()
        }

        Assert.assertEquals("", output)
    }
}
