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

@file:Suppress("UNUSED_PARAMETER", "unused", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")

package dorkbox

import dorkbox.executor.Executor
import dorkbox.executor.exceptions.InvalidExitValueException
import dorkbox.executor.processResults.SyncProcessResult
import dorkbox.executor.stream.LogOutputStream
import dorkbox.executor.stream.slf4j.Slf4jStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Examples of the readme.
 */
internal class ReadmeExamples {
    @Throws(Exception::class)
    fun justExecute() {
        runBlocking {
            Executor()
                .command("java", "-version")
                .start()
        }
    }

    @Throws(Exception::class)
    fun justExecuteOutsideCoroutine() {
        Executor()
            .command("java", "-version")
            .startBlocking()
    }

    @Throws(Exception::class)
    fun exitCode(): Int {
        return runBlocking {
            Executor()
                .command("java", "-version")
                .start()
                .exitValue
        }
    }


    @Throws(Exception::class)
    fun output(): String {
        return runBlocking {
            Executor()
                .command("java", "-version")
                .enableRead()
                .start()
                .output.utf8()
        }
    }

    fun outputAsShell() {
        runBlocking {
            println(Executor()
                        .command("java", "-version")
                        .enableRead()
                        .startAsShell()
                        .output.utf8())
        }
    }

    @Throws(Exception::class)
    fun pumpOutputToLogger() {
        runBlocking {
            Executor()
                .command("java", "-version")
                .redirectOutput(Slf4jStream.asInfo(LoggerFactory.getLogger(javaClass.name + ".MyProcess")))
                .start()
        }
    }

    @Throws(Exception::class)
    fun pumpOutputToLoggerOfCaller() {
        runBlocking {
            Executor()
                .command("java", "-version")
                .redirectOutput(Slf4jStream.asInfo())
                .start()
        }
    }

    @Throws(Exception::class)
    fun pumpOutputToLoggerAndGetOutput(): String {
        return runBlocking {
            Executor()
                .command("java", "-version")
                .redirectOutput(Slf4jStream.asInfo())
                .enableRead()
                .start()
                .output.utf8()
        }
    }

    @Throws(Exception::class)
    fun pumpErrorToLoggerAndGetOutput(): String {
        return runBlocking {
            Executor()
                .command("java", "-version")
                .redirectError(Slf4jStream.asInfo())
                .enableRead()
                .start()
                .output.utf8()
        }
    }

    @Throws(Exception::class)
    fun executeWithTimeout() {
        try {
            runBlocking {
                Executor()
                    .command("java", "-version")
                    .start(60, TimeUnit.SECONDS)
            }
        } catch (e: TimeoutException) {
            // process is automatically destroyed
        }
    }

    @Throws(Exception::class)
    fun pumpOutputToStream(out: OutputStream?) {
        runBlocking {
            Executor()
                .command("java", "-version")
                .redirectOutput(out)
                .start()
        }
    }

    @Throws(Exception::class)
    fun pumpOutputToLogStreamV1(out: OutputStream) {
        runBlocking {
            Executor()
                .command("java", "-version")
                .redirectOutput(object : LogOutputStream() {
                    override fun processLine(line: String) {
                        // ...
                    }
                })
                .start()
        }
    }

    @ExperimentalCoroutinesApi
    @Throws(Exception::class)
    fun pumpOutputToLogStreamV2() {
        val result = Executor()
            .command("java", "-version")
            .enableRead()
            .startAsync()

        runBlocking {
            val fullOutput = mutableListOf<String>()

            val output = result.output
            while (output.isOpen) {
                fullOutput.add(output.utf8())
            }

            val outputString: String = fullOutput.joinToString()
            Assertions.assertFalse(outputString.isEmpty())
        }
    }

    @Throws(Exception::class)
    fun destroyProcessOnJvmExit() {
        runBlocking {
            Executor()
                .command("java", "-version")
                .destroyOnExit()
                .start()
        }
    }

    @Throws(Exception::class)
    fun executeWithEnvironmentVariable() {
        runBlocking {
            Executor()
                .command("java", "-version")
                .environment("foo", "bar")
                .start()
        }
    }

    @Throws(Exception::class)
    fun executeWithEnvironment(env: Map<String, String?>) {
        runBlocking {
            Executor()
                .command("java", "-version")
                .environment(env)
                .start()
        }
    }

    @Throws(Exception::class)
    fun checkExitCode() {
        try {
            runBlocking {
                Executor()
                    .command("java", "-version")
                    .exitValues(3)
                    .start()
            }
        } catch (e: InvalidExitValueException) {
            println("Process exited with " + e.exitValue)
        }
    }

    @Throws(Exception::class)
    fun checkExitCodeAndGetOutput() {
        var output: String
        output = try {
            runBlocking {
                Executor().command("java", "-version")
                    .enableRead()
                    .exitValues(3)
                    .start().output.utf8()
            }
        } catch (e: InvalidExitValueException) {
            println("Process exited with " + e.exitValue)
            (e.result as SyncProcessResult).output.utf8()
        }

        println(output)
    }

    @Throws(Exception::class)
    fun startInBackground() {
        val deferredProcess = Executor()
            .command("java", "-version")
            .startAsync()

        //do some stuff

        deferredProcess.awaitBlocking(60, TimeUnit.SECONDS)
    }

    @Throws(Exception::class)
    fun startInBackgroundAndGetOutput(): String {
        val deferredProcess = Executor()
            .command("java", "-version")
            .enableRead()
            .startAsync()

        //do some stuff

        deferredProcess.awaitBlocking(60, TimeUnit.SECONDS)

        return runBlocking {
            deferredProcess.output.utf8()
        }
    }
}
