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

import dorkbox.executor.stream.CallerLoggerUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CallerLoggerUtilTest {
    @Test
    @Throws(Exception::class)
    fun testFullName() {
        val fullName = "my.full.Logger"
        Assertions.assertEquals(fullName, CallerLoggerUtil.getName(fullName))
    }

    @Test
    @Throws(Exception::class)
    fun testShortName() {
        val shortName = "MyLogger"
        val fullName = CallerLoggerUtilTest::class.java.name + "." + shortName
        Assertions.assertEquals(fullName, CallerLoggerUtil.getName(shortName))
    }

    @Test
    @Throws(Exception::class)
    fun testMyClassName() {
        val fullName = CallerLoggerUtilTest::class.java.name
        Assertions.assertEquals(fullName, CallerLoggerUtil.getName(null))
    }
}
