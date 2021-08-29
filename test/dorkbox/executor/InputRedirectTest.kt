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

import dorkbox.executor.samples.PrintArguments
import dorkbox.executor.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Reported in https://github.com/zeroturnaround/zt-exec/issues/30
 */
class InputRedirectTest {
    companion object {
        private val log = LoggerFactory.getLogger(InputRedirectTest::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun testRedirectInput() {
        var bin = File("/usr/bin/true")
        if (!bin.exists()) {
            bin = File("/bin/true")
        }
        if (!bin.exists()) {
            // maybe windows?
            bin = TestSetup.getParentDir(PrintArguments::class.java)
                .resolve("true.exe")
//            bin = File("C:/Windows/System32/whoami.exe")
        }

        if (!bin.exists()) {
            throw RuntimeException("Unsupported OS! Cannot find /usr/bin/true OR /bin/true...")
        }

        // We need to put something in the buffer
        val bais = ByteArrayInputStream("foo".toByteArray())
        val exec = Executor()
            .command(bin.absolutePath)

        // Test that we don't get IOException: Stream closed
        val exit: Int = runBlocking {
            exec.redirectInput(bais)
                .enableRead()
                .start()
                .exitValue
        }

        log.debug("Exit: {}", exit)
    }
}

// 00:22:59.583 [main @coroutine#1] DEBUG orgWIP.zeroturnaround.exec.ProcessExecutor - Executing [D:\Code\dorkbox\public_projects_libraries\Executor\test\orgWIP\zeroturnaround\exec\test\samples\true.exe].
//00:22:59.594 [main @coroutine#1] DEBUG orgWIP.zeroturnaround.exec.ProcessExecutor - Started Process[pid=6312, exitValue="not exited"]
//00:22:59.611 [main @coroutine#1] DEBUG orgWIP.zeroturnaround.exec.ProcessHandler - Process[pid=6312, exitValue=0] stopped with exit code 0
//00:22:59.617 [DefaultDispatcher-worker-4 @coroutine#2] ERROR orgWIP.zeroturnaround.exec.stream.InputStreamPumper - Got exception while reading/writing the stream
//java.io.IOException: The pipe is being closed
//	at java.base/java.io.FileOutputStream.writeBytes(Native Method)
//	at java.base/java.io.FileOutputStream.write(FileOutputStream.java:354)
//	at java.base/java.io.BufferedOutputStream.flushBuffer(BufferedOutputStream.java:81)
//	at java.base/java.io.BufferedOutputStream.flush(BufferedOutputStream.java:142)
//	at orgWIP.zeroturnaround.exec.stream.InputStreamPumper$run$2.invokeSuspend(InputStreamPumper.kt:95)
//	at orgWIP.zeroturnaround.exec.stream.InputStreamPumper$run$2.invoke(InputStreamPumper.kt)
//	at kotlinx.coroutines.intrinsics.UndispatchedKt.startUndispatchedOrReturn(Undispatched.kt:91)
//	at kotlinx.coroutines.BuildersKt__Builders_commonKt.withContext(Builders.common.kt:160)
//	at kotlinx.coroutines.BuildersKt.withContext(Unknown Source)
//	at orgWIP.zeroturnaround.exec.stream.InputStreamPumper.run(InputStreamPumper.kt:83)
//	at orgWIP.zeroturnaround.exec.stream.PumpStreamHandler$newJob$2.invokeSuspend(PumpStreamHandler.kt:121)
//	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
//	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:56)
//	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:571)
//	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:738)
//	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:678)
//	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:665)
//00:22:59.618 [DefaultDispatcher-worker-4 @coroutine#2] ERROR orgWIP.zeroturnaround.exec.stream.InputStreamPumper - Exception while closing output stream
//java.io.IOException: The pipe is being closed
//	at java.base/java.io.FileOutputStream.writeBytes(Native Method)
//	at java.base/java.io.FileOutputStream.write(FileOutputStream.java:354)
//	at java.base/java.io.BufferedOutputStream.flushBuffer(BufferedOutputStream.java:81)
//	at java.base/java.io.BufferedOutputStream.flush(BufferedOutputStream.java:142)
//	at java.base/java.io.FilterOutputStream.close(FilterOutputStream.java:182)
//	at orgWIP.zeroturnaround.exec.stream.InputStreamPumper$run$2.invokeSuspend(InputStreamPumper.kt:121)
//	at orgWIP.zeroturnaround.exec.stream.InputStreamPumper$run$2.invoke(InputStreamPumper.kt)
//	at kotlinx.coroutines.intrinsics.UndispatchedKt.startUndispatchedOrReturn(Undispatched.kt:91)
//	at kotlinx.coroutines.BuildersKt__Builders_commonKt.withContext(Builders.common.kt:160)
//	at kotlinx.coroutines.BuildersKt.withContext(Unknown Source)
//	at orgWIP.zeroturnaround.exec.stream.InputStreamPumper.run(InputStreamPumper.kt:83)
//	at orgWIP.zeroturnaround.exec.stream.PumpStreamHandler$newJob$2.invokeSuspend(PumpStreamHandler.kt:121)
//	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
//	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:56)
//	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:571)
//	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:738)
//	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:678)
//	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:665)
//00:22:59.647 [main] DEBUG orgWIP.zeroturnaround.exec.test.InputRedirectTest - Exit: 0
//
//Process finished with exit code 0
