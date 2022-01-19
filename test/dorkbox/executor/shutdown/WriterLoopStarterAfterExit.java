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

import dorkbox.executor.Executor;
import dorkbox.executor.samples.TestSetup;

/**
 * Starts [WriterLoop] inside shutdown hook and destroys it.
 */
public
class WriterLoopStarterAfterExit implements Runnable {
    private static Long SLEEP_AFTER_START = 2000L;


    public static
    void main(String[] args) {
        System.out.println("Starting output: " + WriterLoop.getFile());

        Runtime.getRuntime().addShutdownHook(new Thread(new WriterLoopStarterAfterExit()));

        // Launch the process and also destroy it
        System.exit(0);
    }

    @Override
    public
    void run() {
        try {
            new Executor("java", TestSetup.INSTANCE.getFile(WriterLoop.class))
                .destroyOnExit()
                .startBlocking();

            Thread.sleep(SLEEP_AFTER_START);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
