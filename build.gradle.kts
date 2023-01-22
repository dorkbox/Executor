/*
 * Copyright 2023 dorkbox, llc
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

import java.time.Instant

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
////// TESTING : (to local maven repo) <'publish and release' - 'publishToMavenLocal'>
////// RELEASE : (to sonatype/maven central), <'publish and release' - 'publishToSonatypeAndRelease'>
///////////////////////////////

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS   // always show the stacktrace!

plugins {
    id("com.dorkbox.GradleUtils") version "3.9"
    id("com.dorkbox.Licensing") version "2.19.1"
    id("com.dorkbox.VersionUpdate") version "2.5"
    id("com.dorkbox.GradlePublish") version "1.17"

    kotlin("jvm") version "1.8.0"
}

object Extras {
    const val name = "Executor"
    const val description = "Shell, JVM, and SSH command execution on Linux, MacOS, or Windows for Java 8+"
    const val group = "com.dorkbox"
    const val id = "Executor"
    const val version = "3.12"

    const val vendor = "Dorkbox LLC"
    const val vendorUrl = "https://dorkbox.com"
    const val url = "https://git.dorkbox.com/dorkbox/Executor"

    val buildDate = Instant.now().toString()

    const val coroutineVer = "1.6.4"
    const val sshjVer = "0.34.0"
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_1_8) {
    // see: https://kotlinlang.org/docs/reference/using-gradle.html
    freeCompilerArgs = listOf(
            // enable the use of inline classes. see https://kotlinlang.org/docs/reference/inline-classes.html
            "-Xinline-classes",
            // enable the use of experimental methods
            "-Xopt-in=kotlin.RequiresOptIn"
    )
}
GradleUtils.jpms(JavaVersion.VERSION_1_9)

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

kotlin {
    sourceSets {
        test {
            // we have some java we depend on
            kotlin.include("**/*.java", "**/*.kt")
        }
    }
}

tasks.jar.get().apply {
    manifest {
        // https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
        attributes["Name"] = Extras.name

        attributes["Specification-Title"] = Extras.name
        attributes["Specification-Version"] = Extras.version
        attributes["Specification-Vendor"] = Extras.vendor

        attributes["Implementation-Title"] = "${Extras.group}.${Extras.id}"
        attributes["Implementation-Version"] = Extras.buildDate
        attributes["Implementation-Vendor"] = Extras.vendor
    }
}

dependencies {
    // API is used here to allow these dependencies to be transitive, so projects that depend on Executor, do not
    // have to EXPLICITLY add these as dependencies.
    api("org.jetbrains.kotlin:kotlin-stdlib")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${Extras.coroutineVer}")

    api("com.dorkbox:Updates:1.1")

    api("org.slf4j:slf4j-api:1.8.0-beta4")

    compileOnly("ch.qos.logback:logback-classic:1.3.0-alpha4") // ONLY used to fixup the SSHJ logger (in LogHelper)

    // NOTE: JSCH is no longer maintained.
    //  The fork from https://github.com/mwiede/jsch fixes many issues, but STILL cannot connect to an ubuntu 18.04 instance
    // implementation("com.jcraft:jsch:0.1.55")
    // NOTE: The SSHJ implementation works and is well documented. It is also used by Intellij 2019.2+, so it is also well tested and used
    // https://github.com/hierynomus/sshj
    // This is *compileOnly* because SSH command execution is not common, and the developer that needs it can just add the appropriate
    // library to enable SSH support
    compileOnly("com.hierynomus:sshj:${Extras.sshjVer}")

    testImplementation(kotlin("test"))

//    testImplementation("junit:junit:4.13.2")
    testImplementation("ch.qos.logback:logback-classic:1.3.0-alpha4")

    // we want to test SSH functions. Comment this out to view the exception when sshj is not available
    testImplementation("com.hierynomus:sshj:${Extras.sshjVer}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks.test {
    useJUnitPlatform()
}

publishToSonatype {
    groupId = Extras.group
    artifactId = Extras.id
    version = Extras.version

    name = Extras.name
    description = Extras.description
    url = Extras.url

    vendor = Extras.vendor
    vendorUrl = Extras.vendorUrl

    issueManagement {
        url = "${Extras.url}/issues"
        nickname = "Gitea Issues"
    }

    developer {
        id = "dorkbox"
        name = Extras.vendor
        email = "email@dorkbox.com"
    }
}
