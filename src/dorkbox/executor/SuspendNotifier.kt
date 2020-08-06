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

package dorkbox.executor

import kotlinx.coroutines.channels.Channel

// this is waiting, where the notifier DOES NOT BLOCK. The receiver will always block.
//
// This IS NOT bi-directional waiting. The method names to not reflect this, however there is no possibility of race conditions w.r.t. waiting
// https://stackoverflow.com/questions/55421710/how-to-suspend-kotlin-coroutine-until-notified
// https://kotlinlang.org/docs/reference/coroutines/channels.html
inline class SuspendNotifier(private val channel: Channel<Unit> = Channel(2)) {
    // "receive' suspends until another coroutine invokes "send"
    // and
    // "send" WILL NOT suspend. If there nothing waiting, then nothing happens
    suspend fun doWait() { channel.receive() }
    suspend fun doNotify() { channel.send(Unit) }
    fun cancel() { channel.cancel() }
}
