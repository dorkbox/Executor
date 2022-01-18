package dorkbox.executor

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

object JvmHelper {
    private val log = LoggerFactory.getLogger(JvmHelper::class.java)

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
            return jvmExecutable.absoluteFile.canonicalFile
        }

        if (Executor.IS_OS_WINDOWS) {
            // windows does this
            // open a console on windows (alternatively could open "javaw.exe", but we want the ability to redirect IO to the process.
            jvmExecutable = jvmBase.resolve("java.exe")

            if (jvmExecutable.exists()) {
                return  jvmExecutable.absoluteFile.canonicalFile
            }
        }

        return null
    }

    /**
     * Reconstructs the path to the JVM used to launch this process of java. It will always use the "console" version, even on windows.
     */
    fun getJvmPath(): File {
        // use the VM in which we're already running --- MAYBE
        // THIS DOES NOT ALWAYS WORK CORRECTLY, especially if the JVM launched is NOT the JVM for which the path is set!
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

            log.error("Unable to find JVM executable [java.home=" + System.getProperty("java.home") + "]! Using '$jvmExecutable' as the default")
        }

        return jvmExecutable!!
    }
}
