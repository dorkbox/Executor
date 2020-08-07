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
 * Tests that redirect target stream are not closed.
 *
 * @author Rein Raudjärv
 *
 * @see Executor
 *
 * @see HelloWorld
 */
class ProcessExecutorStreamCloseTest {
    @Test
    @Throws(Exception::class)
    fun testRedirectOutputNotClosed() {
        val out = ByteArrayOutputStream()
        val close = RememberCloseOutputStream(out)

        runBlocking {
            helloWorld().redirectOutput(close)
                .redirectErrorStream(false)
                .start()
        }

        Assert.assertEquals("Hello ", String(out.toByteArray()))
        Assert.assertFalse(close.isClosed)
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectErrorNotClosed() {
        val out = ByteArrayOutputStream()
        val close = RememberCloseOutputStream(out)

        runBlocking {
            helloWorld().redirectError(close)
                .start()
        }

        Assert.assertEquals("world!", String(out.toByteArray()))
        Assert.assertFalse(close.isClosed)
    }

    private fun helloWorld(): Executor {
        return Executor("java", TestSetup.getFile(HelloWorld::class.java))
    }
}
