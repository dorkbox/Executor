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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * If you want to save off the output from the process, set a PrintStream to the following:
 * <pre> {@code
 *
 * ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
 * PrintStream outputStream = new PrintStream(byteArrayOutputStream);
 * ...
 *
 * String output = ShellProcessBuilder.getOutput(byteArrayOutputStream);
 * }</pre>
 */
@SuppressWarnings({"UnusedReturnValue", "unused", "ManualArrayToCollectionCopy", "UseBulkOperation", "Convert2Diamond", "Convert2Lambda",
                   "Anonymous2MethodRef", "WeakerAccess"})
public
class ShellExecutor {

    // TODO: Add the ability to get the process PID via java for mac/windows/linux. Linux is avail from jvm, windows needs JNA

    static final String LINE_SEPARATOR = System.getProperty("line.separator");
    static final boolean isWindows;
    static final boolean isMacOsX;

    static {
        String osName = System.getProperty("os.name");
        isWindows = osName.startsWith("windows");
        isMacOsX = osName.startsWith("mac") || osName.startsWith("darwin");
    }



    private static String defaultShell = null;

    private final PrintStream outputStream;
    private final PrintStream outputErrorStream;
    private final InputStream inputStream;

    protected List<String> arguments = new ArrayList<String>();
    private Map<String, String> environment = null;
    private String workingDirectory = null;
    private String executableName = null;
    private String executableDirectory = null;

    private Process process = null;

    private ProcessProxy writeToProcess_input = null;
    private ProcessProxy readFromProcess_output = null;
    private ProcessProxy readFromProcess_error = null;

    private boolean createReadWriterThreads = false;

    private boolean executeAsShell;
    private String pipeToNullString = "";
    private ByteArrayOutputStream byteArrayOutputStream;

    private List<String> fullCommand;

    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "1.0";
    }

    /**
     * This is a convenience method to easily create a default process. Will block until the process is finished running
     *
     * @param executableName the name of the executable to run
     * @param args the arguments for the executable
     *
     * @return true if the process ran successfully (exit value was 0), otherwise false
     */
    public static boolean run(String executableName, String... args) {
        ShellExecutor shell = new ShellExecutor();
        shell.setExecutable(executableName);
        shell.addArguments(args);

        // blocks until finished
        return shell.start() == 0;
    }

    /**
     * This is a convenience method to easily create a default process. Will immediately return, and does not wait for the process to finish
     *
     * @param executableName the name of the executable to run
     * @param args the arguments for the executable
     *
     * @return true if the process ran successfully (exit value was 0), otherwise false
     */
    public static
    boolean runShell(String executableName, String... args) {
        ShellExecutor shell = new ShellExecutor();
        shell.setExecutable(executableName);
        shell.addArguments(args);
        shell.executeAsShellCommand();

        // blocks until finished
        return shell.start() == 0;
    }

    /**
     * This will cause the spawned process to pipe it's output to a String, so it can be retrieved.
     */
    public
    ShellExecutor() {
        byteArrayOutputStream = new ByteArrayOutputStream(8196);
        PrintStream outputStream = new PrintStream(byteArrayOutputStream);

        this.inputStream = null;
        this.outputStream = outputStream;
        this.outputErrorStream = outputStream;
    }

    public
    ShellExecutor(final PrintStream out) {
        this.inputStream = null;
        this.outputStream = out;
        this.outputErrorStream = out;
    }

    public
    ShellExecutor(final InputStream in, final PrintStream out) {
        this.inputStream = in;
        this.outputStream = out;
        this.outputErrorStream = out;
    }

    public
    ShellExecutor(final InputStream in, final PrintStream out, final PrintStream err) {
        this.inputStream = in;
        this.outputStream = out;
        this.outputErrorStream = err;
    }

    /**
     * Creates extra reader/writer threads for the sub-process. This is useful depending on how the sub-process is designed to run.
     * </p>
     * For a process you want interactive IO with, this is required.
     * </p>
     * For a long-running sub-process, with no interactive IO, this is what you'd want.
     * </p>
     * For a run-and-get-the-results process, this isn't recommended.
     *
     */
    public final
    ShellExecutor createReadWriterThreads() {
        createReadWriterThreads = true;
        return this;
    }

    /**
     * When launched from eclipse, the working directory is USUALLY the root of the project folder
     */
    public final
    ShellExecutor setWorkingDirectory(final String workingDirectory) {
        // MUST be absolute path!!
        this.workingDirectory = new File(workingDirectory).getAbsolutePath();
        return this;
    }

    /**
     * The Shell's execution environment variables. Set to `null` to only use the default environment variables (From what
     * {@link System#getenv} returns)
     */
    public final
    ShellExecutor setEnvironment(final Map<String,String> environment) {
        this.environment = environment;
        return this;
    }

    public final
    ShellExecutor addArgument(final String argument) {
        this.arguments.add(argument);
        return this;
    }

    public final
    ShellExecutor addArguments(final String... args) {
        for (String path : args) {
            this.arguments.add(path);
        }
        return this;
    }

    public final
    ShellExecutor addArguments(final List<String> paths) {
        this.arguments.addAll(paths);
        return this;
    }

    public final
    ShellExecutor setExecutable(final String executableName) {
        this.executableName = executableName;
        return this;
    }

    public
    ShellExecutor setExecutableDirectory(final String executableDirectory) {
        // MUST be absolute path!!
        this.executableDirectory = new File(executableDirectory).getAbsolutePath();
        return this;
    }

    /**
     * This will execute as a shell command (bash/cmd/etc) instead of as a forked process.
     */
    public
    ShellExecutor executeAsShellCommand() {
        this.executeAsShell = true;
        return this;
    }




    /**
     * Sends all output data for this process to "null" in a cross platform method
     */
    public
    ShellExecutor pipeOutputToNull() throws IllegalArgumentException {
        if (outputStream != null || outputErrorStream != null) {
            throw new IllegalArgumentException("Cannot pipe shell command to 'null' if an output stream is specified");
        }

        if (isWindows) {
            // >NUL on windows
            pipeToNullString = ">NUL";
        }
        else {
            // we will "pipe" it to /dev/null on *nix
            pipeToNullString = ">/dev/null 2>&1";
        }

        return this;
    }

    /**
     * @return the executable command issued to the shell
     */
    public
    String getCommand() {
        StringBuilder execCommand = new StringBuilder();

        Iterator<String> iterator = fullCommand.iterator();
        while (iterator.hasNext()) {
            String s = iterator.next();

            execCommand.append(s);

            if (iterator.hasNext()) {
                execCommand.append(" ");
            }
        }

        return execCommand.toString();
    }

    public
    int start() {
        return start(true);
    }

    public
    int start(final boolean waitForProcesses) {
        fullCommand = new ArrayList<String>();
        if (executeAsShell) {
            if (isWindows) {
                fullCommand.add("cmd");
                fullCommand.add("/c");
            }
            else {
                if (defaultShell == null) {
                    String[] shells = new String[] {"/bin/bash", "/usr/bin/bash",
                                                    "/bin/pfbash", "/usr/bin/pfbash",
                                                    "/bin/csh", "/usr/bin/csh",
                                                    "/bin/pfcsh", "/usr/bin/pfcsh",
                                                    "/bin/jsh", "/usr/bin/jsh",
                                                    "/bin/ksh", "/usr/bin/ksh",
                                                    "/bin/pfksh", "/usr/bin/pfksh",
                                                    "/bin/ksh93", "/usr/bin/ksh93",
                                                    "/bin/pfksh93", "/usr/bin/pfksh93",
                                                    "/bin/pfsh", "/usr/bin/pfsh",
                                                    "/bin/tcsh", "/usr/bin/tcsh",
                                                    "/bin/pftcsh", "/usr/bin/pftcsh",
                                                    "/usr/xpg4/bin/sh", "/usr/xp4/bin/pfsh",
                                                    "/bin/zsh", "/usr/bin/zsh",
                                                    "/bin/pfzsh", "/usr/bin/pfzsh",
                                                    "/bin/sh", "/usr/bin/sh",};

                    for (String shell : shells) {
                        if (new File(shell).canExecute()) {
                            defaultShell = shell;
                            break;
                        }
                    }
                }

                if (defaultShell == null) {
                    throw new RuntimeException("Unable to determine the default shell for the linux/unix environment.");
                }

                // *nix
                fullCommand.add(defaultShell);
                fullCommand.add("-c");
            }

            // fullCommand.add(this.executableName); // done elsewhere!
        } else {
            // shell and working/exe directory are mutually exclusive
            if (this.workingDirectory != null) {
                if (!this.workingDirectory.endsWith(File.separator)) {
                    this.workingDirectory += File.separator;
                }
            }

            if (this.executableDirectory != null) {
                if (!this.executableDirectory.endsWith(File.separator)) {
                    this.executableDirectory += File.separator;
                }

                fullCommand.add(0, this.executableDirectory + this.executableName);
            } else {
                fullCommand.add(this.executableName);
            }
        }


        // if we don't want output...
        boolean pipeToNull = !pipeToNullString.isEmpty();

        if (executeAsShell && !isWindows) {
            // when a shell AND on *nix, we have to place ALL the args into a single "arg" that is passed in
            final StringBuilder stringBuilder = new StringBuilder(1024);

            stringBuilder.append(this.executableName).append(" ");

            for (String arg : this.arguments) {
                stringBuilder.append(arg).append(" ");
            }

            if (!arguments.isEmpty()) {
                if (pipeToNull) {
                    stringBuilder.append(pipeToNullString);
                }
                else {
                    // delete last " "
                    stringBuilder.delete(stringBuilder.length() - 1, stringBuilder.length());
                }
            }

            fullCommand.add(stringBuilder.toString());

        } else {
            for (String arg : this.arguments) {
                if (arg.contains(" ")) {
                    // individual arguments MUST be in their own element in order to be processed properly
                    // (this is how it works on the command line!)
                    String[] split = arg.split(" ");
                    for (String s : split) {
                        s = s.trim();
                        if (!s.isEmpty()) {
                            fullCommand.add(s);
                        }
                    }
                } else {
                    fullCommand.add(arg);
                }
            }

            if (pipeToNull) {
                fullCommand.add(pipeToNullString);
            }
        }



        ProcessBuilder processBuilder = new ProcessBuilder(fullCommand);
        if (this.workingDirectory != null) {
            processBuilder.directory(new File(this.workingDirectory));
        }


        // These env variables are a copy of System.getenv()
        Map<String, String> environment = processBuilder.environment();

        // Make sure all shell calls are LANG=en_US.UTF-8    THIS CAN BE OVERRIDDEN
        if (isMacOsX) {
            // Enable LANG overrides
            environment.put("SOFTWARE", "");
        }

        // "export LANG=en_US.UTF-8"
        environment.put("LANG", "C");

        if (this.environment != null) {
            for (Map.Entry<String, String> e : this.environment.entrySet()) {
                environment.put(e.getKey(), e.getValue());
            }
        }

        // combine these so output is properly piped to null.
        if (pipeToNull || this.outputErrorStream == null) {
            processBuilder.redirectErrorStream(true);
        }

        try {
            this.process = processBuilder.start();
        } catch (Exception ex) {
            if (outputErrorStream != null) {
                this.outputErrorStream.println("There was a problem executing the program.  Details:");
                ex.printStackTrace(this.outputErrorStream);
            } else {
                System.err.println("There was a problem executing the program.  Details:");
                ex.printStackTrace();
            }

            if (this.process != null) {
                try {
                    this.process.destroy();
                    this.process = null;
                } catch (Exception e) {
                    if (outputErrorStream != null) {
                        this.outputErrorStream.println("Error destroying process:");
                    } else {
                        System.err.println("Error destroying process:");
                    }
                    e.printStackTrace(this.outputErrorStream);
                }
            }
        }

        if (this.process != null) {
            if (this.outputErrorStream == null && this.outputStream == null) {
                if (!pipeToNull) {
                    NullOutputStream nullOutputStream = new NullOutputStream();

                    // readers (read process -> write console)
                    // have to keep the output buffers from filling in the target process.
                    readFromProcess_output = new ProcessProxy("Process Reader: " + this.executableName,
                                                              this.process.getInputStream(),
                                                              nullOutputStream);
                }
            }
            // we want to pipe our input/output from process to ourselves
            else {
                /*
                 * Proxy the System.out and System.err from the spawned process back
                 * to the user's window. This is important or the spawned process could block.
                 */
                // readers (read process -> write console)
                readFromProcess_output = new ProcessProxy("Process Reader: " + this.executableName,
                                                          this.process.getInputStream(),
                                                          this.outputStream);

                if (this.outputErrorStream != this.outputStream) {
                    readFromProcess_error = new ProcessProxy("Process Reader: " + this.executableName,
                                                             this.process.getErrorStream(),
                                                             this.outputErrorStream);
                }
            }

            if (this.inputStream != null) {
                /*
                 * Proxy System.in from the user's window to the spawned process
                 */
                // writer (read console -> write process)
                writeToProcess_input = new ProcessProxy("Process Writer: " + this.executableName,
                                                        this.inputStream,
                                                        this.process.getOutputStream());
            }


            // the process can be killed in two ways
            // If not in IDE, by this shutdown hook. (clicking the red square to terminate a process will not run it's shutdown hooks)
            // Typing "exit" will always terminate the process
            Thread hook = new Thread(new Runnable() {
                @Override
                public
                void run() {
                    try {
                        // wait for the READER threads to die (meaning their streams have closed/EOF'd)
                        if (writeToProcess_input != null) {
                            // the INPUT (from stdin). It should be via the InputConsole, but if it's in eclipse,etc -- then this doesn't do anything
                            // We are done reading input, since our program has closed...
                            writeToProcess_input.close();
                            if (createReadWriterThreads) {
                                writeToProcess_input.join();
                            }
                        }

                        readFromProcess_output.close();
                        if (createReadWriterThreads) {
                            readFromProcess_output.join();
                        }

                        if (readFromProcess_error != null) {
                            readFromProcess_error.close();
                            if (createReadWriterThreads) {
                                readFromProcess_error.join();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread()
                              .interrupt();
                    }

                    // forcibly terminate the process when it's streams have closed.
                    // this is for cleanup ONLY, not to actually do anything.
                    ShellExecutor.this.process.destroy();
                }
            });
            hook.setName("ShellExecutor Shutdown Hook for " + this.executableName);

            // add a shutdown hook to make sure that we properly terminate our spawned processes.
            // hook is NOT set to daemon mode, because this is run during shutdown
            // add a shutdown hook to make sure that we properly terminate our spawned processes.
            try {
                Runtime.getRuntime()
                       .addShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // can happen, safe to ignore
            }

            if (writeToProcess_input != null) {
                if (createReadWriterThreads) {
                    writeToProcess_input.start();
                }
                else {
                    writeToProcess_input.run();
                }
            }

            if (createReadWriterThreads) {
                readFromProcess_output.start();
            }
            else {
                readFromProcess_output.run();
            }

            if (readFromProcess_error != null) {
                if (createReadWriterThreads) {
                    readFromProcess_error.start();
                }
                else {
                    readFromProcess_error.run();
                }
            }

            int exitValue = 0;

            if (waitForProcesses) {
                try {
                    this.process.waitFor();
                    exitValue = this.process.exitValue();
                    hook.run();
                } catch (InterruptedException e) {
                    Thread.currentThread()
                          .interrupt();
                }

                // remove the shutdown hook now that we've shutdown.
                try {
                    Runtime.getRuntime().removeShutdownHook(hook);
                } catch (IllegalStateException ignored) {
                    // can happen, safe to ignore
                }
            }

            return exitValue;
        }

        // 1 means a problem
        return 1;
    }

    /**
     * There will never be a trailing newline character at the end of this output.
     *
     * @return A string representing the output of the process, null if the thread for this was interrupted or the output wasn't saved
     */
    public
    String getOutput() {
        if (byteArrayOutputStream != null) {
            return getOutput(byteArrayOutputStream);
        }

        return null;
    }

    /**
     * Converts the baos to a string in a safe way. There will never be a trailing newline character at the end of this output. This will
     * block until there is a line of input available.
     *
     * @return A string representing the output of the process, null if the thread for this was interrupted or the output wasn't saved
     */
    public
    String getOutputLineBuffered() {
        if (byteArrayOutputStream != null) {
            return getOutputLineBuffered(byteArrayOutputStream);
        }

        return null;
    }

    /**
     * Converts the baos to a string in a safe way. There will never be a trailing newline character at the end of this output.
     *
     * @param byteArrayOutputStream the baos that is used in the {@link ShellExecutor#ShellExecutor(PrintStream)} (or similar
     *                              calls)
     *
     * @return A string representing the output of the process, null if the thread for this was interrupted
     */
    public static
    String getOutput(final ByteArrayOutputStream byteArrayOutputStream) {
        String s;
        synchronized (byteArrayOutputStream) {
            s = byteArrayOutputStream.toString();
            byteArrayOutputStream.reset();
        }

        // remove trailing newline character(s)
        int endIndex = s.lastIndexOf(LINE_SEPARATOR);
        if (endIndex > -1) {
            return s.substring(0, endIndex);
        }

        return s;
    }

    /**
     * Converts the baos to a string in a safe way. There will never be a trailing newline character at the end of this output. This will
     * block until there is a line of input available.
     *
     * @param byteArrayOutputStream the baos that is used in the {@link ShellExecutor#ShellExecutor(PrintStream)} (or similar
     * calls)
     *
     * @return A string representing the output of the process, null if the thread for this was interrupted
     */
    public static
    String getOutputLineBuffered(final ByteArrayOutputStream byteArrayOutputStream) {
        String s;
        synchronized (byteArrayOutputStream) {
            try {
                byteArrayOutputStream.wait();
            } catch (InterruptedException ignored) {
                return null;
            }

            s = byteArrayOutputStream.toString();
            byteArrayOutputStream.reset();
        }

        return s;
    }
}
