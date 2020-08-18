/*
 * Copyright 2020 dorkbox, llc
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


import dorkbox.gradle.kotlin
import java.time.Instant

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
////// TESTING : (to local maven repo) <'publish and release' - 'publishToMavenLocal'>
////// RELEASE : (to sonatype/maven central), <'publish and release' - 'publishToSonatypeAndRelease'>
///////////////////////////////

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS   // always show the stacktrace!
gradle.startParameter.warningMode = WarningMode.All

plugins {
    java

    id("com.dorkbox.GradleUtils") version "1.10"
    id("com.dorkbox.Licensing") version "2.2"
    id("com.dorkbox.VersionUpdate") version "2.0"
    id("com.dorkbox.GradlePublish") version "1.5"
    id("com.dorkbox.GradleModuleInfo") version "1.0"

    kotlin("jvm") version "1.4.0"
}

object Extras {
    const val name = "Executor"
    const val description = "Shell, JVM, and SSH command execution on Linux, MacOS, or Windows for Java 11+"
    const val group = "com.dorkbox"
    const val id = "Executor"
    const val version = "1.0"

    const val vendor = "Dorkbox LLC"
    const val vendorUrl = "https://dorkbox.com"
    const val url = "https://git.dorkbox.com/dorkbox/Executor"

    val buildDate = Instant.now().toString()

    const val coroutineVer = "1.3.9"
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.fixIntellijPaths()
GradleUtils.defaultResolutionStrategy()
GradleUtils.compileConfiguration(JavaVersion.VERSION_11) {
    it.apply {
        // see: https://kotlinlang.org/docs/reference/using-gradle.html
        apiVersion = "1.3"
        languageVersion = "1.3"

        freeCompilerArgs = listOf(
                // enable the use of inline classes. see https://kotlinlang.org/docs/reference/inline-classes.html
                "-Xinline-classes",
                // enable the use of experimental methods
                "-Xopt-in=kotlin.RequiresOptIn"
        )
    }
}


licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        url(Extras.url)
        author(Extras.vendor)

        extra("ZT Process Executor", License.APACHE_2) {
            it.url("https://github.com/zeroturnaround/zt-exec")
            it.copyright(2014)
            it.author("ZeroTurnaround LLC")
        }
        extra("Apache Commons Exec", License.APACHE_2) {
            it.url("https://commons.apache.org/proper/commons-exec/")
            it.copyright(2014)
            it.author("The Apache Software Foundation")
        }
    }
}

sourceSets {
    main {
        kotlin {
            setSrcDirs(listOf("src"))

            // want to include kotlin files for the source. 'setSrcDirs' resets includes...
            include("**/*.kt")
        }
    }

    test {
        java {
            setSrcDirs(listOf("test"))

            // want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")
        }

        kotlin {
            setSrcDirs(listOf("test"))

            // want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java", "**/*.kt")
        }
    }
}

repositories {
    mavenLocal() // this must be first!
    jcenter()
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

        attributes["Automatic-Module-Name"] = Extras.id
    }
}


dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Extras.coroutineVer}")

    // https://github.com/MicroUtils/kotlin-logging
    implementation("io.github.microutils:kotlin-logging:1.8.3")  // kotlin wrapper for slf4j
    implementation("org.slf4j:slf4j-api:1.7.30")

    // NOTE: JSCH is no longer maintained.
    //  The fork from https://github.com/mwiede/jsch fixes many issues, but STILL cannot connect to an ubutnu 18.04 instance
    // api("com.jcraft:jsch:0.1.55")
    // NOTE: This SSH implementation works (and is well documented)
    // https://github.com/hierynomus/sshj
    implementation("com.hierynomus:sshj:0.30.0")


    testImplementation("junit:junit:4.13")
    testImplementation("ch.qos.logback:logback-classic:1.2.3")
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
