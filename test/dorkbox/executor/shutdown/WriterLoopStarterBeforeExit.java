/*
 * Copyright 2022 dorkbox, llc
 * Copyright (C) 2014 ZeroTurnaround <support@zeroturnaround.com>
 * Contains fragments of code from Apache Commons Exec, rights owned
 * by Apache Software Foundation (ASF).
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

package dorkbox.executor.shutdown;

import java.io.File;
import java.io.IOException;

import dorkbox.executor.Executor;

/**
 * Starts [WriterLoop] and destroys it on JVM exit.
 */
public
class WriterLoopStarterBeforeExit {
    public static
    void main(String[] args) {
        try {
            System.out.println("Starting output: " + new File("writeLoop.data").getAbsoluteFile());

            // silly workarounds, because executing java files from CLI, require SINGLE-FILE access!
            String path = getFile(WriterLoopStarterBeforeExit.class)
                    .replace(WriterLoopStarterBeforeExit.class.getSimpleName(), "WriterLoop");

            System.out.println("Starting output: " + path);

            new Executor()
                    .redirectOutputAsInfo()
                    .redirectErrorAsInfo()
                    .asJvmProcess()
                    .addArg("-add-opens java.base/java.lang=ALL-UNNAMED")
                    .cloneClasspath()
                    .setMainClass(path)
                    .startBlocking();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Launch the process and also destroy it
        System.exit(0);
    }

    private static String getFile(Class javaClass) throws IOException {
        String properName;

        if (Executor.Companion.getIS_OS_WINDOWS()) {
            properName = javaClass.getName().replace('.', '\\');
        } else {
            properName = javaClass.getName().replace('.', '/');
        }

        File file = new File("test", properName + ".java").getAbsoluteFile().getCanonicalFile();
        return file.getPath();
    }
}
