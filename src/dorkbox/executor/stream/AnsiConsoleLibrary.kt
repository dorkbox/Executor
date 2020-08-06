/*
 * Copyright 2020 dorkbox, llc

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

import dorkbox.console.Console
import java.io.InputStream

/**
 * Permits us to access the Dorkbox ANSI console library, which lets us have single character, unbuffered character input from the console.
 *
 * Since this is not a "hard" dependency, we check to see if this library is loaded before we actually use it.
 */
object AnsiConsoleLibrary {
    fun getInputStream(): InputStream {
        return Console.inputStream()
    }
}
