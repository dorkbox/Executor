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

package dorkbox.executor.stream

/**
 * Constructs name for the caller logger.
 */
object CallerLoggerUtil {

    /**
     * Returns full name for the caller class' logger.
     *
     * @param name name of the logger.
     *      In case of full name (it contains dots) same value is just returned.
     *      In case of short names (no dots) the given name is prefixed by caller's class name and a dot.
     *      In case of `null` the caller's class name is just returned.
     *
     * @return full name for the caller class' logger.
     */
    fun getName(name: String? = null): String {
        return getName(name, 2)
    }

    /**
     * Returns full name for the caller class' logger.
     *
     * @param name name of the logger. In case of full name (it contains dots) same value is just returned.
     * In case of short names (no dots) the given name is prefixed by caller's class name and a dot.
     * In case of `null` the caller's class name is just returned.
     *
     * @param level number of call stack levels to get the caller (0 means the caller of this method).
     *
     * @return full name for the caller class' logger.
     */
    fun getName(name: String?, level: Int): String {
        return when {
            name == null -> {
                StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { s ->
                    s.map(StackWalker.StackFrame::getDeclaringClass).skip(level.toLong()).findFirst()
                }.get().name
            }
            name.contains(".") -> {
                name
            }
            else -> {
                StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { s ->
                    s.map(StackWalker.StackFrame::getDeclaringClass).skip(level.toLong()).findFirst()
                }.get().name + "." + name
            }
        }
    }
}
