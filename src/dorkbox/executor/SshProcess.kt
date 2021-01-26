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
import net.schmizz.sshj.connection.channel.direct.Signal
import net.schmizz.sshj.transport.TransportException
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
     * Send a signal to the remote command.
     *
     * @param signal the signal
     *
     * @throws TransportException if error sending the signal
     */
    @Throws(TransportException::class)
    fun signal(signal: Signal) {
        command.signal(signal)
    }

    /**
     * If the command exit violently [with a signal][.getExitSignal], information about whether a core dump
     * took place would have been received and can be retrieved via this method.
     */
    fun exitWasCoreDumped(): Boolean {
        return command.exitWasCoreDumped ?: false
    }

    override fun exitValue(): Int {
        return if (command.isOpen) {
            PidHelper.INVALID.toInt()
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
