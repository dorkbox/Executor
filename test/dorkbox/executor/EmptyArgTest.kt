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

import dorkbox.executor.samples.ArgumentsAsList
import dorkbox.executor.samples.TestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Tests passing empty arguments.
 *
 * @see Executor
 *
 * @see ArgumentsAsList
 */
class EmptyArgTest {
    @Test
    @Throws(Exception::class)
    fun testGetArgs() {
        val output = argumentsAsList("arg1", "", "arg3", "")

        Assertions.assertEquals(output.getArgs().size, 5) // because the java file is also one of the args.
    }

    @Test
    @Throws(Exception::class)
    fun testReadOutputAndError() {
        val output: String = runBlocking {
            argumentsAsList("arg1", "", "arg3", "").enableRead()
                .start().output.utf8()
        }

        Assertions.assertEquals("[arg1, , arg3, ]", output)
//        println(output)
    }

    private fun argumentsAsList(vararg args: String): Executor {
        val command: MutableList<String> = ArrayList()
        command.addAll(listOf("java", TestSetup.getFile(ArgumentsAsList::class.java)))
        command.addAll(listOf(*args))

        return Executor(command)
    }
}
