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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public
class ProcessStreamProxy extends Thread {

    private final InputStream is;
    private final OutputStream os;

    private final CountDownLatch startUpLatch = new CountDownLatch(1);
    private final CountDownLatch shutDownLatch = new CountDownLatch(1);

    // when reading from the stdin and outputting to the process
    public
    ProcessStreamProxy(String processName, InputStream inputStreamFromConsole, OutputStream outputStreamToProcess) {
        // basic check to see if we are System.in
        if (inputStreamFromConsole.equals(System.in)) {
            // optionally use "dorkbox.console.Console" to read from System.in as an optional dependency.
            // The "dorkbox.console.Console" allows us to have interruptable blocking reads from System.in -- which NORMALLY is not possible.
            //   additionally, it allows us to read individual characters one-at-a-time, instead of the normal behavior of line input.

            try {
                Class<?> console = Class.forName("dorkbox.console.Console");
                Method inputStream = console.getDeclaredMethod("inputStream");
                InputStream invoked = (InputStream) inputStream.invoke(console);

                // more exact check: basically unwrap everything and see if it's a FileInputStream (which it should be)
                Field in = FilterInputStream.class.getDeclaredField("in");
                in.setAccessible(true);

                Object unwrapped = in.get(inputStreamFromConsole);

                while (unwrapped instanceof FilterInputStream) {
                    unwrapped = in.get(unwrapped);
                }

                if (unwrapped instanceof FileInputStream && ((FileInputStream) unwrapped).getFD().equals(FileDescriptor.in)) {
                    inputStreamFromConsole = invoked;
                }
            } catch (Exception ignored) {
            }
        }

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
        running.set(false);

        try {
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            shutDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void run() {
        running.set(true);
        startUpLatch.countDown();


        final InputStream is = this.is;
        final OutputStream os = this.os;
        int readInt;

        try {
            // this thread will read until there is no more data to read. (this is generally what you want)
            // the stream will be closed when the process closes it (usually on exit)

            if (os == null) {
                //noinspection StatementWithEmptyBody
                while (is.read() != -1 && running.get()) {
                    // just read so it won't block or backup.
                }
            }
            else {
                while ((readInt = is.read()) != -1 && running.get()) {
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
        } catch (Exception ignored) {
        } finally {
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
