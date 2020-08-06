/*
 * Copyright 2020 dorkbox, llc
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

package dorkbox.executor

import dorkbox.executor.stream.slf4j.Level
import org.slf4j.Logger

/**
 * Contains [MessageLogger] instances for various log levels. This is so we can set the logger level FROM
 */
object MessageLoggers {
    val NOP = object : MessageLogger {
        override fun message(log: Logger, format: String, arguments: Array<out Any>) {
            // do nothing
        }
    }

    val TRACE = object : MessageLogger {
        override fun message(log: Logger, format: String, arguments: Array<out Any>) {
            log.trace(format, *arguments)
        }
    }

    val DEBUG = object : MessageLogger {
        override fun message(log: Logger, format: String, arguments: Array<out Any>) {
            log.debug(format, *arguments)
        }
    }

    val INFO = object : MessageLogger {
        override fun message(log: Logger, format: String, arguments: Array<out Any>) {
            log.info(format, *arguments)
        }
    }

    operator fun get(level: Level): MessageLogger {
        return when (level) {
            Level.TRACE -> TRACE
            Level.DEBUG -> DEBUG
            Level.INFO -> INFO
            else -> throw IllegalArgumentException("Invalid level $level")
        }
    }
}
