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
import dorkbox.executor.test.samples.TestSetup
import kotlin.system.exitProcess


/**
 * Starts [WriterLoop] inside shutdown hook and destroys it.
 */
class WriterLoopStarterAfterExit : Runnable {
    companion object {
        private const val SLEEP_AFTER_START: Long = 2000

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            Runtime.getRuntime().addShutdownHook(Thread(WriterLoopStarterAfterExit()))

            // Launch the process and also destroy it
            exitProcess(0)
        }
    }

    override fun run() {
        try {
            Executor("java", TestSetup.getFile(WriterLoop::class.java))
                .destroyOnExit().startBlocking()
            Thread.sleep(SLEEP_AFTER_START)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
