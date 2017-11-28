/*
 * Copyright 2010 dorkbox, llc
 *
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
package dorkbox.executor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This will FORK the java process initially used to start the currently running JVM. Changing the java executable will change this behaviors
 */
public
class JvmExecutor extends ShellExecutor {

    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "1.0";
    }

    /**
     * Reconstructs the path to the JVM used to launch this process. It will always use the "console" version, even on windows.
     */
    public static
    String getJvmPath() {
        // use the VM in which we're already running
        String jvmPath = checkJvmPath(System.getProperty("java.home"));

        // then throw up our hands and hope for the best
        if (jvmPath == null) {
            System.err.println("Unable to find java JVM [java.home=" + System.getProperty("java.home") + "]!");
            jvmPath = "java";
        }

        // Oddly, the Mac OS X specific java flag -Xdock:name will only work if java is launched
        // from /usr/bin/java, and not if launched by directly referring to <java.home>/bin/java,
        // even though the former is a symlink to the latter! To work around this, see if the
        // desired jvm is in fact pointed to by /usr/bin/java and, if so, use that instead.
        if (isMacOsX) {
            try {
                File binDir = new File("/usr/bin");
                File javaParentDir = new File(jvmPath).getParentFile().getCanonicalFile();

                if (javaParentDir.equals(binDir)) {
                    jvmPath = "/usr/bin/java";
                }
            } catch (IOException ignored) {
            }
        }

        return jvmPath;
    }

    /**
     * Checks whether a Java Virtual Machine can be located in the supplied path.
     *
     * @param jvmLocation the location of the JVM to check
     */
    private static
    String checkJvmPath(String jvmLocation) {
        // linux does this...
        String vmbase = jvmLocation + File.separator + "bin" + File.separator;
        String vmpath = vmbase + "java";

        if (new File(vmpath).exists()) {
            return vmpath;
        }

        // windows does this
        // open a console on windows (alternatively could open "javaw.exe", but we want the ability to redirect IO to the process.
        vmpath = vmbase + "java.exe";

        if (new File(vmpath).exists()) {
            return vmpath;
        }

        return null;
    }
    // this is NOT related to JAVA_HOME, but is instead the location of the JRE that was used to launch java initially.
    private String javaLocation = getJvmPath();
    private String mainClass;

    private int initialHeapSizeInMegabytes = 0;
    private int maximumHeapSizeInMegabytes = 0;

    private List<String> jvmOptions = new ArrayList<String>();
    private List<String> classpathEntries = new ArrayList<String>();

    private List<String> mainClassArguments = new ArrayList<String>();
    private String jarFile;

    // what version of java??
    // so, this starts a NEW JVM, from an ALREADY existing JVM.
    public
    JvmExecutor() {
        super(null, null, null);
    }

    public
    JvmExecutor(InputStream in, PrintStream out, PrintStream err) {
        super(in, out, err);
    }

    public final
    void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public final
    void setInitialHeapSizeInMegabytes(int startingHeapSizeInMegabytes) {
        this.initialHeapSizeInMegabytes = startingHeapSizeInMegabytes;
    }

    public final
    void setMaximumHeapSizeInMegabytes(int maximumHeapSizeInMegabytes) {
        this.maximumHeapSizeInMegabytes = maximumHeapSizeInMegabytes;
    }

    public final
    void addJvmClasspath(String classpathEntry) {
        this.classpathEntries.add(classpathEntry);
    }

    public final
    void addJvmClasspath(List<String> paths) {
        this.classpathEntries.addAll(paths);
    }

    public final
    void addJvmOption(String argument) {
        this.jvmOptions.add(argument);
    }

    public final
    void addJvmOptions(List<String> paths) {
        this.jvmOptions.addAll(paths);
    }

    public final
    void setJarFile(String jarFile) {
        this.jarFile = jarFile;
    }

    private
    String getClasspath() {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        final int totalSize = this.classpathEntries.size();
        final String pathseparator = File.pathSeparator;

        // DO NOT QUOTE the elements in the classpath!
        for (String classpathEntry : this.classpathEntries) {
            try {
                // fix a nasty problem when spaces aren't properly escaped!
                classpathEntry = classpathEntry.replaceAll(" ", "\\ ");

                // make sure the classpath is ABSOLUTE pathname
                classpathEntry = new File(classpathEntry).getAbsolutePath();

                builder.append(classpathEntry);
                count++;
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (count < totalSize) {
                builder.append(pathseparator); // ; on windows, : on linux
            }
        }
        return builder.toString();
    }

    /**
     * Specify the JAVA executable to launch this process. By default, this will use the same java executable
     * as was used to start the current JVM.
     */
    public
    void setJava(String javaLocation) {
        this.javaLocation = javaLocation;
    }

    @SuppressWarnings({"UseBulkOperation", "ManualArrayToCollectionCopy"})
    @Override
    public
    int start() {
        setExecutable(this.javaLocation);

        // save off the original arguments
        List<String> origArguments = new ArrayList<String>(this.arguments.size());
        origArguments.addAll(this.arguments);
        this.arguments = new ArrayList<String>(0);


        // two versions, java vs not-java
        if (initialHeapSizeInMegabytes != 0) {
            this.arguments.add("-Xms" + this.initialHeapSizeInMegabytes + "M");
        }
        if (maximumHeapSizeInMegabytes != 0) {
            this.arguments.add("-Xmx" + this.maximumHeapSizeInMegabytes + "M");
        }

        // always run the server version
        this.arguments.add("-server");

        for (String option : this.jvmOptions) {
            this.arguments.add(option);
        }

        // same as -cp
        String classpath = getClasspath();

        // two more versions. jar vs class
        if (this.jarFile != null) {
            this.arguments.add("-jar");
            this.arguments.add(this.jarFile);

            // interesting note. You CANNOT have a classpath specified on the commandline
            // when using JARs!! It must be set in the jar's MANIFEST.
            if (!classpath.isEmpty()) {
                throw new IllegalArgumentException("WHOOPS.  You CANNOT have a classpath specified on the commandline when using JARs, " +
                                                   "   It must be set in the JARs MANIFEST instead.");
            }
        }
        // if we are running classes!
        else if (this.mainClass != null) {
            if (!classpath.isEmpty()) {
                this.arguments.add("-classpath");
                this.arguments.add(classpath);
            }

            // main class must happen AFTER the classpath!
            this.arguments.add(this.mainClass);
        }
        else {
            throw new IllegalArgumentException("You must specify a jar or main class when running a java process!");
        }


        for (String arg : this.mainClassArguments) {
            if (arg.contains(" ")) {
                // individual arguments MUST be in their own element in order to
                //  be processed properly (this is how it works on the command line!)
                String[] split = arg.split(" ");
                for (String s : split) {
                    this.arguments.add(s);
                }
            }
            else {
                this.arguments.add(arg);
            }
        }

        this.arguments.addAll(origArguments);

        return super.start();
    }
}
