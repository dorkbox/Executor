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

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

open class RememberCloseOutputStream(out: OutputStream?) : FilterOutputStream(out) {
    @Volatile
    var isClosed = false
        private set

    @Throws(IOException::class)
    override fun close() {
        isClosed = true
        super.close()
    }
}
