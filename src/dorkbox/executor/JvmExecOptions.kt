/*
 * Copyright 2022 dorkbox, llc

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
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.*

/**
 * Options for configuring a process to run using the same JVM as the currently launched jvm
 */
class JvmExecOptions(private val executor: Executor, private val javaExecutable: String? = null) {

    companion object {
        private val log = LoggerFactory.getLogger(JvmExecOptions::class.java)

        private val SPACE_REGEX = " ".toRegex()
    }


    // this is NOT related to JAVA_HOME, but is instead the location of the JRE that was used to launch java initially.
    internal var javaLocation = JvmHelper.getJvmPath(Executor.IS_OS_MAC, Executor.IS_OS_WINDOWS)
    internal var mainClass: String? = null

    internal var initialHeapSizeInMegabytes = 0
    internal var maximumHeapSizeInMegabytes = 0

    internal val jvmOptions = mutableListOf<String>()
    internal val classpathEntries = mutableListOf<String>()

    internal val mainClassArguments = mutableListOf<String>()
    internal var jarFile: String? = null

    internal var cloneClasspath: Boolean = false

    /**
     * Get the classpath for this JAVA process
     */
    fun getClasspath(): String {
        val builder = StringBuilder()

        var count = 0
        val totalSize: Int = classpathEntries.size
        val pathSeparator = File.pathSeparator

        // DO NOT QUOTE the elements in the classpath!
        classpathEntries.forEach {
            try {
                // fix a nasty problem when spaces aren't properly escaped!
                var classpathEntry = it.replace(SPACE_REGEX, "\\ ")

                // make sure the classpath is ABSOLUTE pathname
                classpathEntry = File(classpathEntry).absolutePath
                builder.append(classpathEntry)

                count++
            } catch (e: Exception) {
                log.error("Error processing classpath!!", e)
            }

            if (count < totalSize) {
                builder.append(pathSeparator) // ; on windows, : on linux
            }
        }

        // if specified, copy the current JVM process settings to the new process
        if (cloneClasspath) {
            // classpath
            val additionalClasspath = System.getProperty("java.class.path")
            if (additionalClasspath.isNotEmpty()) {
                if (count > 0) {
                    // have to add a separator
                    builder.append(pathSeparator)
                }

                builder.append(additionalClasspath)
            }
        }

        return builder.toString()
    }

    /**
     * Set the JVM initial heap size, in megabytes.
     */
    fun setInitialHeapSizeInMegabytes(initialHeapSizeInMegabytes: Int): JvmExecOptions {
        this.initialHeapSizeInMegabytes = initialHeapSizeInMegabytes
        return this
    }

    /**
     * Set the JVM maximum heap size, in megabytes.
     */
    fun setMaximumHeapSizeInMegabytes(maximumHeapSizeInMegabytes: Int): JvmExecOptions {
        this.maximumHeapSizeInMegabytes = maximumHeapSizeInMegabytes
        return this
    }

    /**
     * Copies the original JVM classpath in this newly created JVM
     */
    fun cloneClasspath(): JvmExecOptions {
        this.cloneClasspath = true
        return this
    }

    /**
     * Set the main class to launch (optional, as a JAR can have this in the manifest)
     */
    fun setMainClass(mainClass: String): JvmExecOptions {
        // we have to normalize the main class path.
        this.mainClass = mainClass
        return this
    }

    /**
     * Add JVM classpath. This cannot be combined with a JAR entry. They are mutually exclusive!
     */
    fun addJvmClasspath(vararg classpath: String): JvmExecOptions {
        classpath.forEach {
            classpathEntries.add(it)
        }
        return this
    }

    /**
     * Add JVM options for the launched process
     */
    fun addJvmOption(vararg arguments: String): JvmExecOptions {
        arguments.forEach {
            jvmOptions.add(it)
        }
        return this
    }

    /**
     * Specify the JAR file to use. This cannot be combined with a JVM classpath. They are mutually exclusive!
     */
    fun setJarFile(jarFile: String): JvmExecOptions {
        this.jarFile = jarFile
        return this
    }

    /**
     * Specify the JAVA executable to launch this process.
     *
     * By default, this will use the same java executable as was used to start the current JVM.
     */
    fun setJava(javaLocation: String): JvmExecOptions {
        this.javaLocation = File(javaLocation)
        return this
    }

    /**
     * Specify the JAVA executable to launch this process.
     *
     * By default, this will use the same java executable as was used to start the current JVM.
     */
    fun setJava(javaLocation: File): JvmExecOptions {
        this.javaLocation = javaLocation
        return this
    }

    /**
     * Add arguments to an existing command, which will be executed.
     *
     * This does not replace commands, it adds to them
     *
     * @param arguments A string array containing the program and/or its arguments.
     *
     * @return This process executor.
     */
    fun addArg(vararg arguments: String): JvmExecOptions {
        val fixed = Executor.fixArguments(listOf(*arguments))
        mainClassArguments.addAll(fixed)
        return this
    }

    /**
     * Add arguments to an existing command, which will be executed.
     *
     * This does not replace commands, it adds to them
     *
     * @param arguments A string array containing the program and/or its arguments.
     *
     * @return This process executor.
     */
    fun addArg(arguments: Iterable<String>): JvmExecOptions {
        val fixed = Executor.fixArguments(arguments)
        mainClassArguments.addAll(fixed)
        return this
    }

    /**
     * Executes the JAVA sub process.
     *
     * This method waits until the process exits, a timeout occurs or the caller thread gets interrupted.
     *
     * In the latter cases the process gets destroyed as well.
     *
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return exit code of the finished process.
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InterruptedException this thread was interrupted.
     * @throws TimeoutException timeout set by [.timeout] was reached.
     * @throws InvalidExitValueException if invalid exit value was returned (@see [.exitValues]).
     */
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class, TimeoutException::class, InvalidExitValueException::class)
    suspend fun start(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): SyncProcessResult {
        return executor.start(timeout, timeoutUnit)
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
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return [DeferredProcessResult] representing the process results (value/completed outputstreams/etc) of the finished process.
     *
     * @throws IOException an error occurred when process was started.
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun startAsync(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): DeferredProcessResult {
        return executor.startAsync(timeout, timeoutUnit)
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
     * @param timeout If specified (non-zero), then if the process is running longer than this
     *                  specified interval, a [TimeoutException] is thrown and the process is destroyed.
     *
     * @return results of the finished process (exit code and output, if any)
     *
     * @throws IOException an error occurred when process was started or stopped.
     * @throws InterruptedException this thread was interrupted.
     * @throws TimeoutException timeout set by [.timeout] was reached.
     * @throws InvalidExitValueException if invalid exit value was returned (@see [.exitValues]).
     */
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class, TimeoutException::class, InvalidExitValueException::class)
    fun startBlocking(timeout: Long = 0, timeoutUnit: TimeUnit = TimeUnit.SECONDS): SyncProcessResult {
        return runBlocking {
            start(timeout, timeoutUnit)
        }
    }

    internal fun configure() {
        // save off the original arguments
        val commandLineArgs = executor.builder.command()
        val newArgs = mutableListOf<String>()

        if (javaExecutable != null) {
            newArgs.add(javaExecutable)
        } else {
            newArgs.add(javaLocation.canonicalFile.path)
        }

        // setup heap information
        if (initialHeapSizeInMegabytes != 0) {
            newArgs.add("-Xms")
            newArgs.add("${initialHeapSizeInMegabytes}M")
        }
        if (maximumHeapSizeInMegabytes != 0) {
            newArgs.add("-Xmx")
            newArgs.add("${maximumHeapSizeInMegabytes}M")
        }

        // add the JVM options if specified
        if (jvmOptions.isNotEmpty()) {
            newArgs.addAll(Executor.fixArguments(jvmOptions))
        }

        // now add the original arguments
        newArgs.addAll(commandLineArgs)


        // get the classpath, which is the same as using -cp
        val classpath: String = getClasspath()

        // two versions. JAR vs CLASSES (classes are "raw", and read directly from disk)
        // JAR has precedence
        when {
            jarFile != null -> {
                newArgs.add("-jar")
                newArgs.add(jarFile!!)


                // You CANNOT have a classpath specified on the commandline when using JARs!!
                // It must be set in the jar's MANIFEST.
                require(classpath.isEmpty()) {
                    "WHOOPS.  You CANNOT have a classpath specified on the commandline when using JARs, it must be set in the JARs MANIFEST instead."
                }
            }
            mainClass != null -> {
                if (classpath.isNotEmpty()) {
                    newArgs.add("--class-path")
                    newArgs.add(classpath)
                }

                // main class must happen AFTER the classpath!
                newArgs.add(mainClass!!)
            }
            else -> {
                throw IllegalArgumentException("You must specify a jar or main class when running a java process!")
            }
        }

        // add the arguments for the java process main-class
        if (mainClassArguments.isNotEmpty()) {
            newArgs.addAll(Executor.fixArguments(mainClassArguments))
        }

        // set the arguments (this overwrites whatever is already there)
        executor.builder.command(newArgs)
    }
}
