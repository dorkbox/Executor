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
package dorkbox.executor.stream

import dorkbox.executor.RememberCloseOutputStream
import dorkbox.executor.stream.nopStreams.NopOutputStream
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class TeeOutputStreamTest {
    class ExceptionOnCloseByteArrayOutputStream(out: OutputStream?) : RememberCloseOutputStream(out) {
        @Throws(IOException::class)
        override fun close() {
            super.close()
            throw IOException()
        }
    }

    @Test
    @Throws(IOException::class)
    fun shouldCopyContentsToBothStreams() {
        val left = ByteArrayOutputStream()
        val right = ByteArrayOutputStream()
        val teeOutputStream = TeeOutputStream(left, right)
        teeOutputStream.write(10)
        teeOutputStream.write(byteArrayOf(1, 2, 3))
        teeOutputStream.write(byteArrayOf(10, 11, 12, 13, 14, 15, 15, 16), 2, 3)
        Assert.assertArrayEquals(byteArrayOf(10, 1, 2, 3, 12, 13, 14), left.toByteArray())
        Assert.assertArrayEquals(byteArrayOf(10, 1, 2, 3, 12, 13, 14), right.toByteArray())
    }

    @Test
    @Throws(IOException::class)
    fun shouldCloseBothStreamsWhenClosingTee() {
        val left = RememberCloseOutputStream(NopOutputStream.OUTPUT_STREAM)
        val right = RememberCloseOutputStream(NopOutputStream.OUTPUT_STREAM)
        val teeOutputStream = TeeOutputStream(left, right)
        teeOutputStream.close()
        Assert.assertTrue(left.isClosed)
        Assert.assertTrue(right.isClosed)
    }

    @Test
    fun shouldCloseSecondStreamWhenClosingFirstFails() {
        val left = ExceptionOnCloseByteArrayOutputStream(NopOutputStream.OUTPUT_STREAM)
        val right = RememberCloseOutputStream(NopOutputStream.OUTPUT_STREAM)
        val teeOutputStream = TeeOutputStream(left, right)
        try {
            teeOutputStream.close()
            Assert.fail("Was expecting an exception!")
        } catch (expected: IOException) {
        }
        Assert.assertTrue(left.isClosed)
        Assert.assertTrue(right.isClosed)
    }
}
