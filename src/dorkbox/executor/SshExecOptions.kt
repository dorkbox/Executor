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

import dorkbox.executor.exceptions.InvalidExitValueException
import dorkbox.executor.processResults.SyncProcessResult
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * see https://github.com/hierynomus/sshj
 *
 * NOTE: JSCH is no longer maintained.
 *  The fork from https://github.com/mwiede/jsch fixes many issues, but STILL does not connect to an ubuntu 18.04 instance
 */
class SshExecOptions(val executor: Executor) {
    private var host: String? = null
    private var port: Int = 22
    private var userName: String? = null
    private var password: String? = null

    private var privateKeyFile: String? = null

    private var verifier: HostKeyVerifier? = null
    private var strictHostCheck = true
    private var knownHostsFile: String? = null


    private val ssh = SSHClient()

    internal fun startProcess(timeout: Long, timeoutUnit: TimeUnit): Process {
        if (strictHostCheck) {
            if (knownHostsFile != null) {
                ssh.loadKnownHosts(File(knownHostsFile!!))
            } else {
                ssh.addHostKeyVerifier(verifier)
            }
        } else {
            ssh.addHostKeyVerifier(PromiscuousVerifier())
        }


        if (timeout > 0L) {
            ssh.connectTimeout = timeoutUnit.toMillis(timeout).toInt()
        }

        ssh.connect(host, port)

        if (privateKeyFile != null) {
            ssh.authPublickey(userName, privateKeyFile)
        } else {
            ssh.authPassword(userName, password)
        }

        return try {
            val session = ssh.startSession()

            val execCommand = executor.builder.command().joinToString(separator = " ")
            val command = session.exec(execCommand)

            SshProcess(ssh, session, command)
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
    }

    /**
     * @return the underlying SSH Client in case more specific configurations are necessary
     */
    fun ssh(): SSHClient {
        return ssh
    }


    fun userName(userName: String): SshExecOptions {
        this.userName = userName
        return this
    }

    fun host(host: String): SshExecOptions {
        this.host = host
        return this
    }

    fun port(port: Int): SshExecOptions {
        this.port = port
        return this
    }

    fun privateKeyFile(privateKeyFile: String): SshExecOptions {
        this.privateKeyFile = privateKeyFile
        return this
    }

    fun password(password: String): SshExecOptions {
        this.password = password
        return this
    }

    fun disableStrictHostChecking(): SshExecOptions {
        strictHostCheck = false
        return this
    }

    fun setHostVerifier(verifier: HostKeyVerifier): SshExecOptions {
        this.verifier = verifier
        return this
    }

    fun setKnownHostsFile(knownHostsFile: String): SshExecOptions {
        this.knownHostsFile = knownHostsFile
        return this
    }

    /**
     * Sets the program and its arguments which are being executed.
     *
     * @param command A string array containing the program and its arguments.
     *
     * @return This process executor.
     */
    fun command(vararg command: String): SshExecOptions {
        executor.command(Executor.fixArguments(listOf(*command)))
        return this
    }

    /**
     * Sets the program and its arguments which are being executed.
     *
     * @param command The iterable containing the program and its arguments.
     *
     * @return This process executor.
     */
    fun command(command: Iterable<String>): SshExecOptions {
        executor.command(command)
        return this
    }

    /**
     * Splits string by spaces and passes it to [Executor.command]<br></br>
     *
     * NB: this method do not handle whitespace escaping,
     * `"mkdir new\ folder"` would be interpreted as
     * `{"mkdir", "new\", "folder"}` command.
     *
     * @param commandWithArgs A string array containing the program and its arguments.
     *
     * @return This process executor.
     */
    fun commandSplit(commandWithArgs: String): SshExecOptions {
        executor.commandSplit(commandWithArgs)
        return this
    }


    /**
     * Executes the JAVA sub process.
     *
     * This method waits until the process exits, a timeout occurs or the caller thread gets interrupted.
     *
     * In the latter cases the process gets destroyed as well.
     *
     * @return exit code of the finished process.
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InterruptedException this thread was interrupted.
     * @throws TimeoutException timeout set by [.timeout] was reached.
     * @throws InvalidExitValueException if invalid exit value was returned (@see [.exitValues]).
     */
    suspend fun start(): SyncProcessResult {
        return executor.start()
    }

    /**
     * Start the sub process in a new coroutine. The calling thread will continue execution. This method does not wait until the process exits.
     *
     * Calling [SyncProcessResult.output] will result in a blocking read of process output.
     *
     * The value passed to [.timeout] is ignored. Use [DeferredProcessResult.await] to wait for the process to finish.
     *
     * Invoke [DeferredProcessResult.cancel] to destroy the process.
     *
     * @return [DeferredProcessResult] representing the process results (value/completed output-streams/etc) of the finished process.
     *
     * @throws IOException an error occurred when process was started.
     */
    @Throws(IOException::class)
    fun startAsync(): DeferredProcessResult {
        return executor.startAsync()
    }

    /**
     * The calling thread will immediately execute the sub process. When trying to close the input streams, the colling thread may block.
     *
     * Waits until:
     *  - the process stops
     *  - a timeout occurs and the caller thread gets interrupted. (In this case the process gets destroyed as well.)
     *
     * Calling [SyncProcessResult.output] will result in a non-blocking read of process output.
     *
     * @return results of the finished process (exit code and output, if any)
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InterruptedException this thread was interrupted.
     * @throws TimeoutException timeout set by [.timeout] was reached.
     * @throws InvalidExitValueException if invalid exit value was returned (@see [.exitValues]).
     */
    @Throws(IOException::class, InterruptedException::class, TimeoutException::class, InvalidExitValueException::class)
    fun startBlocking(): SyncProcessResult {
        return runBlocking {
            start()
        }
    }
}

