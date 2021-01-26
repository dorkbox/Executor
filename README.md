Executor
========

###### [![Dorkbox](https://badge.dorkbox.com/dorkbox.svg "Dorkbox")](https://git.dorkbox.com/dorkbox/Executor) [![Github](https://badge.dorkbox.com/github.svg "Github")](https://github.com/dorkbox/Executor) [![Gitlab](https://badge.dorkbox.com/gitlab.svg "Gitlab")](https://gitlab.com/dorkbox/Executor)


Shell, JVM, and remote SSH command execution on Linux, MacOS, or Windows for Java 8 (no process PID information) and Java 9+ (with PID 
information)

 
Ironically, there are a considerable number of issues when launching shell/JVM processes via Java. This library solves problems
 with:
1. Redirecting in/out/err streams correctly
1. Enable reading a single-character from the console input-stream
1. Correctly reading out/err process output streams to prevent memory and threading issues
1. Executing a JVM process using the same JVM as is currently running
1. Executing remote processes via SSH
1. Using and supporting kotlin co-routines for thread suspension
1. Getting process PID information (Java 9+ only)

- This is for cross-platform use, specifically - linux 32/64, mac 32/64, and windows 32/64. Java 8+, kotlin.


This project is powerful but still simple to use. By using a single class **Executor**
the user gets the functionality from both **java.lang.ProcessBuilder** and [Apache Commons Exec](http://commons.apache.org/proper/commons-exec/), along with the ability to fork the currently running JVM process and remotely execute commands via SSH. 


&nbsp; 
&nbsp; 

Release Notes 
---------
 
## Examples (these are written in kotlin)

* Easiest, and simplest way to get UTF8 output from running an application. This is VERY simple, and designed for quickly running, small-output applications. For anything just a hint that it might be MORE complex, use the builder pattern with the appropriate configuration. 

```java
val output = Executor.run("java", "-version")
```

<hr/>

* Output is pumped to NullOutputStream

```java
Executor().command("java", "-version").start()
```

<hr/>

* Returning the exit code
* Output is pumped to NullOutputStream

```java
val exit = Executor().command("java", "-version").startBlocking().getExitValue()
```

<hr/>

* Return output as UTF8 String

```java
val output = Executor().command("java", "-version").enableRead().startBlocking().output.utf8()    
```

<hr/>

* Pumping the output to a logger

```java
Executor().command("java", "-version")
    .redirectOutput(Slf4jStream.asInfo(LoggerFactory.getLogger(javaClass.name + ".MyProcess")))
    .start()
```

<hr/>

* Pumping the output to a logger (short form for previous)

```java
Executor()
    .command("java", "-version")
    .redirectOutput(Slf4jStream.asInfo())
    .start()
```

<hr/>

* Pumping the output to the logger of the caller class

```java
Executor()
    .command("java", "-version")
    .redirectOutput(Slf4jStream.asInfo())
    .start()
```

<hr/>

* Pumping the output to a logger
* Returning output as UTF8 String

```java
val output = Executor()
                .command("java", "-version")
                .redirectOutput(Slf4jStream.asInfo())
                .enableRead()
                .start()
                .output.utf8()
```

<hr/>

* Pumping the stderr to a logger
* Returning the output as UTF8 String

```java
val output = Executor()
                .command("java", "-version")
                .redirectError(Slf4jStream.asInfo())
                .enableRead()
                .start()
                .output.utf8()
```

<hr/>

* Running with a timeout of **60** seconds
* Output pumped to NullOutputStream

```java
try {
    runBlocking {
        Executor()
            .command("java", "-version")
            .timeout(60, TimeUnit.SECONDS)
            .start()
    }
} catch (e: TimeoutException) {
    // process is automatically destroyed
}
```

<hr/>

* Pumping output to another OutputStream

```java
Executor()
    .command("java", "-version")
    .redirectOutput(out)
    .start()
```

<hr/>

* Handling output line-by-line while process is running (Version 1)

```java
 runBlocking {
    Executor()
        .command("java", "-version")
        .redirectOutput(object : LogOutputStream() {
            override fun processLine(line: String) {
                // ...
            }
        })
        .start()
}
```

<hr/>

* Handling output line-by-line while process is running (Version 2)

```java
val result = Executor()
    .command("java", "-version")
    .enableRead()
    .startAsync()

runBlocking {
    val fullOutput = mutableListOf<String>()

    val output = result.output
    while (output.isOpen) {
        fullOutput.add(output.utf8())
    }

    val outputString: String = fullOutput.joinToString()
    Assert.assertFalse(outputString.isEmpty())
}
```

<hr/>

* Destroy the running process when VM exits
* Output pumped to NullOutputStream

```java
Executor().command("java", "-version").destroyOnExit().start()
```

<hr/>

* Run process with a specific environment variable
* Output pumped to NullOutputStream

```java
Executor().command("java", "-version").environment("foo", "bar").start()
```

<hr/>

* Run process with a specific environment
* Output pumped to NullOutputStream

```java
val env = mapOf<String, String>(...)
Executor()
    .command("java", "-version")
    .environment(env)
    .start()
```

<hr/>

* Throw exception when wrong exit code
* Output is pumped to NullOutputStream

```java
try {
    runBlocking {
        Executor()
            .command("java", "-version")
            .exitValues(3)
            .start()
    }
} catch (e: InvalidExitValueException) {
    println("Process exited with " + e.exitValue)
}
```

<hr/>

* Throw exception when wrong exit code
* Return output as UTF8 String 

```java
 var output: String
try {
    output = runBlocking {
        Executor()
            .command("java", "-version")
            .enableRead()
            .exitValues(3)
            .start()
            .output.utf8()
    }
} catch (e: InvalidExitValueException) {
    println("Process exited with " + e.exitValue)
    output = (e.result as SyncProcessResult).output.utf8()
}
```

<hr/>

* Starting process in the background
* Output is pumped to NullOutputStream

```java
val deferredProcess = Executor()
    .command("java", "-version")
    .startAsync()

//do some stuff..

deferredProcess.awaitBlocking(60, TimeUnit.SECONDS)
```

<hr/>

* Start process in the background
* Return output as UTF8 String

```java
val deferredProcess = Executor()
    .command("java", "-version")
    .enableRead()
    .startAsync()

//do some stuff

deferredProcess.awaitBlocking(60, TimeUnit.SECONDS)

return runBlocking {
    deferredProcess.output.utf8()
}
```

 
  
Maven Info
---------
```
<dependencies>
    ...
    <dependency>
      <groupId>com.dorkbox</groupId>
      <artifactId>Executor</artifactId>
      <version>2.1</version>
    </dependency>
</dependencies>
```

Gradle Info
---------
```
dependencies {
    ...
    implementation("com.dorkbox:Executor:2.1")
}
```


License
---------
This project is © 2020 dorkbox llc, with modifications from software copyright 2014 ZeroTurnaround, and 2014 The Apache Software
 Foundation. 

This project is distributed under the terms of the Apache v2.0 License. See file "LICENSE" for further references.

