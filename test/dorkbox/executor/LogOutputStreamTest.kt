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

import dorkbox.executor.stream.LogOutputStream
import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.*

class LogOutputStreamTest {
    @Throws(UnsupportedEncodingException::class, IOException::class)
    private fun testLogOutputStream(multiLineString: String, vararg expectedLines: String) {
        val processedLines: MutableList<String> = ArrayList()
        val logOutputStream: LogOutputStream = object : LogOutputStream() {
            override fun processLine(line: String) {
                processedLines.add(line)
            }
        }

        logOutputStream.use { stream ->
            stream.write(multiLineString.toByteArray(charset("UTF-8")))
        }

        Assert.assertEquals(listOf(*expectedLines), processedLines)
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testSimple() {
        testLogOutputStream("foo", "foo")
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testNewLine() {
        testLogOutputStream("foo\nbar", "foo", "bar")
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testNewLineWithMultipleLines() {
        testLogOutputStream("foo1\nbar1\nfoo2\nbar2", "foo1", "bar1", "foo2", "bar2")
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testCarriageReturn() {
        testLogOutputStream("foo\rbar", "foo", "bar")
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testCarriageReturnWithMultipleLines() {
        testLogOutputStream("foo1\rbar1\rfoo2\rbar2", "foo1", "bar1", "foo2", "bar2")
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testCarriageReturnAndNewLine() {
        testLogOutputStream("foo\r\nbar", "foo", "bar")
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testCarriageReturnAndNewLineWithMultipleLines() {
        testLogOutputStream("foo1\r\nbar1\r\nfoo2\r\nbar2", "foo1", "bar1", "foo2", "bar2")
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testTwoNewLines() {
        testLogOutputStream("foo\n\nbar", "foo", "bar")
    }

    @Test
    @Throws(UnsupportedEncodingException::class, IOException::class)
    fun testNewLineAtTheEnd() {
        testLogOutputStream("foo\n", "foo")
    }
}
