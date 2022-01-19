/*
 * Copyright 2020 dorkbox llc
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package dorkbox.executor

import org.junit.jupiter.api.Test


class TestShell {
    @Test
    fun testPing() {
        // println(Executor().command("ping 1.1.1.1").enableRead().startAsShellBlocking(10).output.utf8())
    }
}
