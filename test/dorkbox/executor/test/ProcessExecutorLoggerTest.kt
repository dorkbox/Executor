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

package dorkbox.executor.test

import dorkbox.executor.Executor
import dorkbox.executor.stream.LogOutputStream
import dorkbox.executor.stream.slf4j.Slf4jInfoOutputStream
import dorkbox.executor.stream.slf4j.Slf4jStream
import org.junit.Assert
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.OutputStream

class ProcessExecutorLoggerTest {
    @Test
    @Throws(Exception::class)
    fun testFullName() {
        val fullName = "my.full.Logger"
        testSlf4jLoggerName(fullName, Slf4jStream.asInfo(LoggerFactory.getLogger(fullName)))
    }

    @Test
    @Throws(Exception::class)
    fun testShortName() {
        val shortName = "MyLogger"
        testSlf4jLoggerName(shortName, Slf4jStream.asInfo(LoggerFactory.getLogger(shortName)))
    }

    @Test
    @Throws(Exception::class)
    fun testMyClassName() {
        val fullName = javaClass.name
        testSlf4jLoggerName(fullName, Slf4jStream.asInfo())
    }

    private fun testSlf4jLoggerName(fullName: String, stream: LogOutputStream) {
        val executor = Executor()
        executor.redirectOutput(stream)

        val out: OutputStream = executor.streams().out

        Assert.assertTrue("Slf4jInfoOutputStream expected", out is Slf4jInfoOutputStream)
        Assert.assertEquals(fullName, (out as Slf4jInfoOutputStream).logger.name)
    }
}
