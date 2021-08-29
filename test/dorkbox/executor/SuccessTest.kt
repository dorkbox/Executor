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

import dorkbox.executor.listener.ProcessListener
import dorkbox.executor.processResults.ProcessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class SuccessTest {
    @Test
    @Throws(Exception::class)
    fun testJavaVersion() {
        val listener = ProcessListenerImpl()
        val result: ProcessResult = runBlocking {
            Executor("java", "-version")
                .addListener(listener)
                .start()
        }

        Assert.assertEquals(0, result.exitValue)
        Assert.assertNotNull(listener.executor)
        Assert.assertNotNull(listener.process)
        Assert.assertNotNull(listener.result)
        Assert.assertEquals(result, listener.result)
    }

    internal open class ProcessListenerImpl : ProcessListener() {
        var executor: Executor? = null
        var process: Process? = null
        var result: ProcessResult? = null

        override fun beforeStart(executor: Executor) {
            Assert.assertNotNull(executor)
            Assert.assertNull(this.executor)
            Assert.assertNull(process)
            Assert.assertNull(result)
            this.executor = executor
        }

        override fun afterStart(process: Process, executor: Executor) {
            Assert.assertNotNull(process)
            Assert.assertNotNull(executor)
            Assert.assertNotNull(this.executor)
            Assert.assertNull(this.process)
            Assert.assertNull(result)
            Assert.assertEquals(this.executor, executor)
            this.process = process
        }

        override fun afterFinish(process: Process, result: ProcessResult) {
            Assert.assertNotNull(process)
            Assert.assertNotNull(result)
            Assert.assertNotNull(executor)
            Assert.assertNotNull(this.process)
            Assert.assertNull(this.result)
            Assert.assertEquals(this.process, process)
            this.result = result
        }

        override fun afterStop(process: Process) {
            Assert.assertNotNull(process)
            Assert.assertNotNull(executor)
            Assert.assertNotNull(this.process)
            Assert.assertNotNull(result)
            Assert.assertEquals(this.process, process)
        }
    }
}
