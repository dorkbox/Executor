/*
 * Copyright 2026 dorkbox, llc
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

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
////// TESTING : (to local maven repo) <'publish and release' - 'publishToMavenLocal'>
////// RELEASE : (to sonatype/maven central), <'publish and release' - 'publishToSonatypeAndRelease'>
///////////////////////////////

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS   // always show the stacktrace!

plugins {
    id("com.dorkbox.GradleUtils") version "4.8"
    id("com.dorkbox.Licensing") version "3.1"
    id("com.dorkbox.VersionUpdate") version "3.2"
    id("com.dorkbox.GradlePublish") version "2.2"

    kotlin("jvm") version "2.3.0"
}


GradleUtils.load {
    group = "com.dorkbox"
    id = "Executor"

    description = "Shell, JVM, and SSH command execution on Linux, MacOS, or Windows"
    name = "Executor"
    version = "4.0"

    vendor = "Dorkbox LLC"
    vendorUrl = "https://dorkbox.com"

    url = "https://git.dorkbox.com/dorkbox/Executor"

    issueManagement {
        url = "${url}/issues"
        nickname = "Gitea Issues"
    }

    developer {
        id = "dorkbox"
        name = vendor
        email = "email@dorkbox.com"
    }
}
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_25) {
    // see: https://kotlinlang.org/docs/reference/using-gradle.html
    freeCompilerArgs = listOf(
            // enable the use of inline classes. see https://kotlinlang.org/docs/reference/inline-classes.html
            "-Xinline-classes",
            // enable the use of experimental methods
            "-Xopt-in=kotlin.RequiresOptIn"
    )
}


licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        url(Extras.url)
        author(Extras.vendor)

        extra("ZT Process Executor", License.APACHE_2) {
            url("https://github.com/zeroturnaround/zt-exec")
            copyright(2014)
            author("ZeroTurnaround LLC")
        }
        extra("Apache Commons Exec", License.APACHE_2) {
            url("https://commons.apache.org/proper/commons-exec/")
            copyright(2014)
            author("The Apache Software Foundation")
        }
    }
}


dependencies {
    val logbackVer = "1.5.26"
    val slf4jVer = "2.0.17"
    val coroutineVer = "1.10.2"
    val sshjVer = "0.40.0"

    // API is used here to allow these dependencies to be transitive, so projects that depend on Executor, do not
    // have to EXPLICITLY add these as dependencies.
    api("org.jetbrains.kotlin:kotlin-stdlib")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${coroutineVer}")

    api("com.dorkbox:Updates:1.3")


    api("org.slf4j:slf4j-api:${slf4jVer}")


    compileOnly("ch.qos.logback:logback-classic:${logbackVer}") // ONLY used to fixup the SSHJ logger (in LogHelper)

    // NOTE: JSCH is no longer maintained.
    //  The fork from https://github.com/mwiede/jsch fixes many issues, but STILL cannot connect to an ubuntu 18.04 instance
    // implementation("com.jcraft:jsch:0.1.55")
    // NOTE: The SSHJ implementation works and is well documented. It is also used by Intellij 2019.2+, so it is also well tested and used
    // https://github.com/hierynomus/sshj
    // This is *compileOnly* because SSH command execution is not common, and the developer that needs it can just add the appropriate
    // library to enable SSH support
    compileOnly("com.hierynomus:sshj:${sshjVer}")


    testImplementation("junit:junit:4.13.2")
    testImplementation("com.dorkbox:OS:2.0")

    testImplementation("ch.qos.logback:logback-classic:${logbackVer}")

    // we want to test SSH functions. Comment this out to view the exception when sshj is not available
    testImplementation("com.hierynomus:sshj:${sshjVer}")
}
