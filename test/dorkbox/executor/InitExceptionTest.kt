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

import dorkbox.executor.exceptions.ProcessInitException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.IOException

class InitExceptionTest {
    @Test
    @Throws(Exception::class)
    fun testNull() {
        Assertions.assertNull(ProcessInitException.newInstance(null, IOException()))
    }

    @Test
    @Throws(Exception::class)
    fun testEmpty() {
        Assertions.assertNull(ProcessInitException.newInstance(null, IOException("")))
    }

    @Test
    @Throws(Exception::class)
    fun testSimple() {
        val e = ProcessInitException.newInstance("Could not run test.",
                                                 IOException("java.io.IOException: Cannot run program \"ls\": java.io.IOException: error=12, Cannot allocate memory"))

        e!!
        Assertions.assertNotNull(e)
        Assertions.assertEquals("Could not run test. Error=12, Cannot allocate memory", e.message)
        Assertions.assertEquals(12, e.errorCode)
    }

    @Test
    @Throws(Exception::class)
    fun testBeforeCode() {
        val e = ProcessInitException.newInstance("Could not run test.",
                                                 IOException("java.io.IOException: Cannot run program \"sleep\": java.io.IOException: CreateProcess error=2, The system cannot find the file specified"))

        e!!
        Assertions.assertNotNull(e)
        Assertions.assertEquals("Could not run test. Error=2, The system cannot find the file specified", e.message)
        Assertions.assertEquals(2, e.errorCode)
    }
}
