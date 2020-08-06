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

package dorkbox.executor.test.shutdown

import dorkbox.executor.Executor
import org.junit.Assert
import org.junit.Test
import java.io.File

/**
 * Tests destroying processes on JVM exit.
 */
class ProcessExecutorShutdownHookTest {
    @Test
    @Throws(Exception::class)
    fun testDestroyOnExit() {
        testDestroyOnExit(WriterLoopStarterBeforeExit::class.java, true)
    }

    @Test
    @Throws(Exception::class)
    fun testDestroyOnExitInShutdownHook() {
        testDestroyOnExit(WriterLoopStarterAfterExit::class.java, false)
    }

    @Throws(Exception::class)
    private fun testDestroyOnExit(loopStarterClassFile: Class<*>, fileIsAlwaysCreated: Boolean) {
        val file = WriterLoop.getFile()
        println(file)
        if (file.exists()) file.delete()

        Executor()
            .redirectOutputAsInfo()
            .asJvmProcess()
            .cloneClasspath()
            .setMainClass(loopStarterClassFile.name)
            .startBlocking()

        // After WriterLoopStarter has finished we expect that WriterLoop is also finished - no-one is updating the file
        if (fileIsAlwaysCreated || file.exists()) {
            checkFileStaysTheSame(file)
            file.delete()
        }
    }

    companion object {
        private const val SLEEP_FOR_RECHECKING_FILE: Long = 4000

        @Throws(InterruptedException::class)
        private fun checkFileStaysTheSame(file: File) {
            Assert.assertTrue(file.exists())
            val length = file.length()

            Thread.sleep(SLEEP_FOR_RECHECKING_FILE)
            Assert.assertEquals("File '$file' was still updated.", length, file.length())
        }
    }
}
