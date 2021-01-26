/*
 * Copyright 2021 dorkbox, llc

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

import java.lang.reflect.Field

/**
 * In Java <9, this will return 0, since there is no consistent method to get the PID from the specified process
 */
internal object PidHelper {
    const val INVALID = -1L

    fun get(process: Process): Long {
        // cannot get the pid from a process in Java <9 (technically there are TONS of hacks to do this, but it's better to just update the JVM)

        val type = process::class.java.name

        try {
            // reflection is OK here **BECAUSE** this only runs in java <9 (where reflection permissions are allowed)
            if (type == "java.lang.UNIXProcess") {
                val f: Field = process.javaClass.getDeclaredField("pid")
                f.isAccessible = true
                return f.getInt(process).toLong()
            }
        } catch (ignored: Exception) {
        }

        // if (type == "java.lang.Win32Process" || type == "java.lang.ProcessImpl") {
        // windows is not supported UNLESS we use JNA, which I do not want to do, as we want minimal dependencies.
        return INVALID
    }
}
