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

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SshProcess(private val ssh: SSHClient,
                 private val session: Session,
                 private val command: Session.Command) : Process() {

    private val outputStream = command.outputStream
    private val inputStream  = command.inputStream
    private val errStream = command.errorStream


    override fun getOutputStream(): OutputStream {
        return outputStream
    }

    override fun getInputStream(): InputStream {
        return inputStream
    }

    override fun getErrorStream(): InputStream {
        return errStream
    }

    @Throws(InterruptedException::class)
    override fun waitFor(): Int {
        command.join()
        destroy()
        return exitValue()
    }

    /**
     * It doesn't make sense to have a pid, which is associated with a REMOTE ssh command execution, since there is nothing really that
     *  can be done about it
     */
    override fun pid(): Long {
        return 0
    }

    override fun exitValue(): Int {
        return if (command.isOpen) {
            -1
        } else {
            return command.exitStatus
        }
    }

    override fun destroy() {
        try {
            session.close()
        } catch (ignored: IOException) {
        }

        ssh.disconnect()
    }
}
