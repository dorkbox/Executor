/*
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

import dorkbox.executor.samples.PrintArguments
import dorkbox.executor.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.*
import java.util.concurrent.*

/**
 * Tests passing command line arguments to a Java process.
 */
class CommandLineTest {
    @Test
    @Throws(Exception::class)
    fun testOneArg() {
        testArguments("foo")
    }

    @Test
    @Throws(Exception::class)
    fun testTwoArgs() {
        testArguments("foo", "bar")
    }

    @Test
    @Throws(Exception::class)
    fun testSpaces() {
        testArguments("foo foo", "bar bar")
    }

    @Test
    @Throws(Exception::class)
    fun testQuotes() {
        val args = arrayOf("\"a\"", "b \"c\" d", "f \"e\"", "\"g\" h")
        var expected = listOf("\"a\"", "b \"c\" d", "f \"e\"", "\"g\" h")
        if (Executor.IS_OS_WINDOWS) expected = listOf("a", "b c d", "f e", "g h")
        testArguments(expected, *args)
    }

    @Test
    @Throws(Exception::class)
    fun testSlashes() {
        testArguments("/o\\", "\\/.*")
    }

    @Throws(IOException::class, InterruptedException::class, TimeoutException::class)
    private fun testArguments(vararg args: String) {
        val actual = runBlocking {
            printArguments(*args).start().output.linesAsUtf8()
        }

        val expected = listOf(*args)
        Assert.assertEquals(expected, actual)
    }

    @Throws(IOException::class, InterruptedException::class, TimeoutException::class)
    private fun testArguments(expected: List<String>, vararg args: String) {
        val actual = runBlocking {
            printArguments(*args).start().output.linesAsUtf8()
        }

        Assert.assertEquals(expected, actual)
    }

    private fun printArguments(vararg args: String): Executor {
        val command: MutableList<String> = ArrayList()
        command.addAll(listOf("java", TestSetup.getFile(PrintArguments::class.java)))
        command.addAll(listOf(*args))
        return Executor(command)
            .enableRead()
    }
}
