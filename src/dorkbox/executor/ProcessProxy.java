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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import dorkbox.console.Console;
import dorkbox.console.input.Terminal;

public
class ProcessProxy extends Thread {

    private final InputStream is;
    private final OutputStream os;

    private final boolean isSystemIn;

    private final CountDownLatch startUpLatch = new CountDownLatch(1);
    private final CountDownLatch shutDownLatch = new CountDownLatch(1);

    // when reading from the stdin and outputting to the process
    public
    ProcessProxy(String processName, InputStream inputStreamFromConsole, OutputStream outputStreamToProcess) {
        boolean isSystemIn = false;

        // basic check to see if we are System.in
        if (inputStreamFromConsole.equals(System.in)) {

            // more exact check: basically unwrap everything and see if it's a FileInputStream
            try {
                Field in = FilterInputStream.class.getDeclaredField("in");
                in.setAccessible(true);

                Object unwrapped = in.get(inputStreamFromConsole);

                while (unwrapped instanceof FilterInputStream) {
                    unwrapped = in.get(unwrapped);
                }

                isSystemIn = unwrapped instanceof FileInputStream;
                if (isSystemIn) {
                    inputStreamFromConsole = (InputStream) unwrapped;
                }
            } catch (Exception ignored) {
            }
        }

        // if we are actually System.in, we want to use the Console.in INSTEAD, because it will let us do things we could otherwise not do.
        this.isSystemIn = isSystemIn;

        this.is = inputStreamFromConsole;
        this.os = outputStreamToProcess;

        setName(processName);
        setDaemon(true);
    }

    private AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public synchronized
    void start() {
        super.start();

        // now we have to for it to actually start up. The process can run & complete before this starts, resulting in no input/output
        // captured
        try {
            startUpLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public
    void close() {
        // this.interrupt();
        running.set(false);

        try {
            shutDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public
    void run() {
        // if we are system in, we can ONLY read the line input, unless the Console project is present!
        if (isSystemIn) {

        }



        // we rely on buferredReader.ready(), so that we can know if there is input or not (and read/block/etc if necessary)
        final BufferedReader reader = new BufferedReader(new InputStreamReader(this.is));


        Terminal in = Console.in();



        running.set(true);

        final OutputStream os = this.os;
        // final BufferedReader reader = this.reader;
        final long timeout = 200L;

        startUpLatch.countDown();

        try {
            // this thread will read until there is no more data to read. (this is generally what you want)
            // the stream will be closed when the process closes it (usually on exit)
            int readInt;

            if (os == null) {
                while (!reader.ready()) {
                    Thread.sleep(timeout);

                    if (!running.get()) {
                        if (isSystemIn) {
                            System.err.println("DONE sysin " + this);
                            // should attempt to process anything more.
                            return;
                        }

                        // should process whatever is left.
                        System.err.println("DONE a " + this);
                        break;
                    }
                }

                // just read so it won't block.
                reader.readLine();
            }
            else {
                while (running.get()) {
                    try {
                        while (!reader.ready()) {
                            Thread.sleep(timeout);

                            if (!running.get()) {
                                if (isSystemIn) {
                                    System.err.println("DONE sysin " + this);
                                    // should attempt to process anything more.
                                    return;
                                }

                                // should process whatever is left.
                                System.err.println("DONE a " + this);
                                break;
                            }
                        }
                    } catch (InterruptedException ignored) {
                    }


                    while ((readInt = reader.read()) != -1) {
                        System.err.println(".");
                        os.write(readInt);

                        // flush the output on new line. (same for both windows '\r\n' and linux '\n')
                        if (readInt == '\n') {
                            os.flush();

                            synchronized (os) {
                                os.notifyAll();
                            }
                        }
                    }
                }


            }
        } catch (Exception ignore) {
            ignore.printStackTrace();
        } finally {
            System.err.println("DONE c " + this);

            try {
                // this.reader.close();
                if (os != null) {
                    os.flush(); // this goes to the console, so we don't want to close it!
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            shutDownLatch.countDown();
        }
    }
}
