/*
 * Copyright 2020 dorkbox, llc
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

package dorkbox.executor.samples

import dorkbox.executor.Executor
import java.io.File

object TestSetup {
    fun getFile(javaClass: Class<*>): String {
        val properName = if (Executor.IS_OS_WINDOWS) {
            javaClass.name.replace('.', '\\')
        } else {
            javaClass.name.replace('.', '/')
        }

        val file = File("test", "$properName.java").absoluteFile.canonicalFile
        return file.path
    }

    fun getParentDir(javaClass: Class<*>): File {
        val properName = if (Executor.IS_OS_WINDOWS) {
            javaClass.name.replace('.', '\\')
        } else {
            javaClass.name.replace('.', '/')
        }

        val file = File("test", "$properName.java").absoluteFile.canonicalFile
        return file.parentFile
    }
}
