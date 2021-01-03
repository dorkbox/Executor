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

import dorkbox.executor.samples.HelloWorld
import dorkbox.executor.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Tests redirecting stream.
 *
 * @author Rein Raudjärv
 *
 * @see Executor
 *
 * @see HelloWorld
 */
class HelloWorldTest {
    @Test
    @Throws(Exception::class)
    fun testReadOutputAndError() {
        val output: String = runBlocking {
            helloWorld().enableRead()
                .start().output.utf8()
        }

        Assert.assertEquals("Hello world!", output)
    }

    @Test
    @Throws(Exception::class)
    fun testReadOutputOnly() {
        val output: String = runBlocking {
            helloWorld().enableRead()
                .redirectErrorStream(false)
                .start().output.utf8()
        }

        Assert.assertEquals("Hello ", output)
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectOutputAndError() {
        val out = ByteArrayOutputStream()
        runBlocking {
            helloWorld().redirectOutput(out)
                .start()
        }

        Assert.assertEquals("Hello world!", String(out.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectOutputAndErrorMerged() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        runBlocking {
            helloWorld().redirectOutput(out)
                .redirectError(err)
                .start()
        }

        Assert.assertEquals("Hello ", String(out.toByteArray()))
        Assert.assertEquals("world!", String(err.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectOutputAndErrorAndReadOutput() {
        val out = ByteArrayOutputStream()

        val output: String = runBlocking {
            helloWorld().redirectOutput(out)
                .enableRead()
                .start().output.utf8()
        }

        Assert.assertEquals("Hello world!", output)
        Assert.assertEquals("Hello world!", String(out.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectOutputOnly() {
        val out = ByteArrayOutputStream()

        runBlocking {
            helloWorld().redirectOutput(out)
                .redirectErrorStream(false)
                .start()
        }

        Assert.assertEquals("Hello ", String(out.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectOutputOnlyAndReadOutput() {
        val out = ByteArrayOutputStream()

        val output: String = runBlocking {
            helloWorld().redirectOutput(out)
                .redirectErrorStream(false)
                .enableRead()
                .start().output.utf8()
        }

        Assert.assertEquals("Hello ", output)
        Assert.assertEquals("Hello ", String(out.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectErrorOnly() {
        val err = ByteArrayOutputStream()

        runBlocking {
            helloWorld().redirectError(err)
                .redirectErrorStream(false)
                .start()
        }

        Assert.assertEquals("world!", String(err.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectErrorOnlyAndReadOutput() {
        val err = ByteArrayOutputStream()

        val output: String = runBlocking {
            helloWorld().redirectError(err)
                .redirectErrorStream(false)
                .enableRead()
                .start().output.utf8()
        }

        Assert.assertEquals("Hello ", output)
        Assert.assertEquals("world!", String(err.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectOutputAndErrorSeparate() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        runBlocking {
            helloWorld().redirectOutput(out)
                .redirectError(err)
                .redirectErrorStream(false)
                .start()
        }

        Assert.assertEquals("Hello ", String(out.toByteArray()))
        Assert.assertEquals("world!", String(err.toByteArray()))
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectOutputAndErrorSeparateAndReadOutput() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val output: String = runBlocking {
            helloWorld().redirectOutput(out)
                .redirectError(err)
                .redirectErrorStream(false)
                .enableRead()
                .start().output.utf8()
        }

        Assert.assertEquals("Hello ", output)
        Assert.assertEquals("Hello ", String(out.toByteArray()))
        Assert.assertEquals("world!", String(err.toByteArray()))
    }

    private fun helloWorld(): Executor {
        return Executor("java", TestSetup.getFile(HelloWorld::class.java))
    }
}
