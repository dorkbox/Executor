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

import dorkbox.executor.processResults.ProcessOutput
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ProcessOutputTest {
    @Test
    fun testSimple() {
        Assertions.assertEquals(listOf("foo"), ProcessOutput.getLinesFrom("foo"))
    }

    @Test
    fun testNewLine() {
        Assertions.assertEquals(listOf("foo", "bar"), ProcessOutput.getLinesFrom("foo\nbar"))
    }

    @Test
    fun testNewLineWithMultipleLines() {
        Assertions.assertEquals(listOf("foo1", "bar1", "foo2", "bar2"), ProcessOutput.getLinesFrom("foo1\nbar1\nfoo2\nbar2"))
    }

    @Test
    fun testCarriageReturn() {
        Assertions.assertEquals(listOf("foo", "bar"), ProcessOutput.getLinesFrom("foo\rbar"))
    }

    @Test
    fun testCarriageReturnWithMultipleLines() {
        Assertions.assertEquals(listOf("foo1", "bar1", "foo2", "bar2"), ProcessOutput.getLinesFrom("foo1\rbar1\rfoo2\rbar2"))
    }

    @Test
    fun testCarriageReturnAndNewLine() {
        Assertions.assertEquals(listOf("foo", "bar"), ProcessOutput.getLinesFrom("foo\r\nbar"))
    }

    @Test
    fun testCarriageReturnAndNewLineWithMultipleLines() {
        Assertions.assertEquals(listOf("foo1", "bar1", "foo2", "bar2"), ProcessOutput.getLinesFrom("foo1\r\nbar1\r\nfoo2\r\nbar2"))
    }

    @Test
    fun testTwoNewLines() {
        Assertions.assertEquals(listOf("foo", "bar"), ProcessOutput.getLinesFrom("foo\n\nbar"))
    }

    @Test
    fun testNewLineAtTheEnd() {
        Assertions.assertEquals(listOf("foo"), ProcessOutput.getLinesFrom("foo\n"))
    }
}
