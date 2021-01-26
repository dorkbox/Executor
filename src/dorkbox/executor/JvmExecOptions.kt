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
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * Options for configuring a process to run using the same JVM as the currently launched jvm
 */
class JvmExecOptions(private val executor: Executor) {

    companion object {
        private val log = KotlinLogging.logger { }

        private val SPACE_REGEX = " ".toRegex()

        /**
         * Checks whether a Java Virtual Machine can be located in the supplied path.
         *
         * @param jvmLocation the location of the JVM to check
         *
         * @return the absolute path to the java executable (based on the path) if it exists, or null
         */
        private fun getJvmExecutable(jvmLocation: String): File? {
            // linux does this...
            val jvmBase = File(jvmLocation).resolve("bin")

            var jvmExecutable = jvmBase.resolve("java")
            if (jvmExecutable.exists()) {
                return jvmExecutable
            }

            if (Executor.IS_OS_WINDOWS) {
                // windows does this
                // open a console on windows (alternatively could open "javaw.exe", but we want the ability to redirect IO to the process.
                jvmExecutable = jvmBase.resolve("java.exe")

                if (jvmExecutable.exists()) {
                    return  jvmExecutable
                }
            }

            return null
        }

        /**
         * Reconstructs the path to the JVM used to launch this process of java. It will always use the "console" version, even on windows.
         */
        fun getJvmPath(): File {
            // use the VM in which we're already running
            var jvmExecutable = getJvmExecutable(System.getProperty("java.home"))

            // Oddly, the Mac OS X specific java flag -Xdock:name will only work if java is launched
            // from /usr/bin/java, and not if launched by directly referring to <java.home>/bin/java,
            // even though the former is a symlink to the latter! To work around this, see if the
            // desired jvm is in fact pointed to by /usr/bin/java and, if so, use that instead.
            if (Executor.IS_OS_MAC) {
                try {
                    val binDir = File("/usr/bin")

                    val javaParentDir = jvmExecutable?.parentFile?.canonicalFile
                    if (javaParentDir == binDir) {
                        jvmExecutable = File("/usr/bin/java")
                    }
                } catch (ignored: IOException) {
                }
            }

            if (jvmExecutable == null && Executor.IS_OS_WINDOWS) {
                // maybe java.library.path System Property has it. We use the first one that matches.
                System.getProperty("java.library.path").split(";").forEach {
                    val path = File(it).resolve("java.exe")
                    if (path.exists()) {
                        jvmExecutable = path
                        return@forEach
                    }
                }
            }

            // hope for the best, maybe it's on the path
            if (jvmExecutable == null) {
                jvmExecutable = if (Executor.IS_OS_WINDOWS) {
                    File("java.exe")
                } else {
                    File("java")
                }

                log.error { "Unable to find JVM executable [java.home=" + System.getProperty("java.home") + "]! Using '$jvmExecutable' as the default" }
            }

            return jvmExecutable!!
        }
    }


    // this is NOT related to JAVA_HOME, but is instead the location of the JRE that was used to launch java initially.
    internal var javaLocation = getJvmPath()
    internal var mainClass: String? = null

    internal var initialHeapSizeInMegabytes = 0
    internal var maximumHeapSizeInMegabytes = 0

    internal val jvmOptions = mutableListOf<String>()
    internal val classpathEntries = mutableListOf<String>()

    internal val mainClassArguments = mutableListOf<String>()
    internal var jarFile: String? = null

    internal var cloneClasspath: Boolean = false

    /**
     * Get's the classpath for this JAVA process
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
                log.error(e) { "Error processing classpath!!" }
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
                builder.append(pathSeparator) // have to add a seperator
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
        executor.addArg(*arguments)
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
        executor.addArg(arguments)
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
    @Throws(IOException::class, InterruptedException::class, TimeoutException::class, InvalidExitValueException::class)
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
     * @return [DeferredProcessResult] representing the process results (value/completed outputstreams/etc) of the finished process.
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

    internal fun configure() {
        // save off the original arguments
        val commandLineArgs = executor.builder.command()
        val newArgs = mutableListOf<String>()

        newArgs.add(javaLocation.absolutePath)


        // setup heap information
        if (initialHeapSizeInMegabytes != 0) {
            newArgs.add("-Xms")
            newArgs.add("${initialHeapSizeInMegabytes}M")
        }
        if (maximumHeapSizeInMegabytes != 0) {
            newArgs.add("-Xmx")
            newArgs.add("${maximumHeapSizeInMegabytes}M")
        }

        // always run the server version
        newArgs.add("-server")

        // add the JVM options if specified
        if (jvmOptions.isNotEmpty()) {
            newArgs.addAll(Executor.fixArguments(jvmOptions))
        }

        // get the classpath, which is the same as using -cp
        val classpath: String = getClasspath()

        // two versions. JAR vs CLASSES (raw)
        // JAR has precedence
        when {
            jarFile != null -> {
                newArgs.add("-jar")
                newArgs.add(jarFile!!)


                // interesting note. You CANNOT have a classpath specified on the commandline
                // when using JARs!! It must be set in the jar's MANIFEST.
                require(classpath.isEmpty()) {
                    "WHOOPS.  You CANNOT have a classpath specified on the commandline when using JARs, it must be set in the JARs MANIFEST instead."
                }
            }
            mainClass != null -> {
                if (classpath.isNotEmpty()) {
                    newArgs.add("-classpath")
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

        // now add the original arguments
        newArgs.addAll(commandLineArgs)

        // set the arguments
        executor.builder.command(newArgs)
    }
}
