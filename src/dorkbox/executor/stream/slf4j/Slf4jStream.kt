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

package dorkbox.executor.stream.slf4j

import dorkbox.executor.stream.CallerLoggerUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Creates output streams that write to [Logger]s.
 *
 * @author Rein Raudjärv
 */
object Slf4jStream {
    /**
     * @param level the desired logging level
     *
     * @return output stream that writes with a given level of the calling class logger.
     */
    fun `as`(level: Level): Slf4jOutputStream {
        return when (level) {
            Level.TRACE -> asTrace()
            Level.DEBUG -> asDebug()
            Level.INFO -> asInfo()
            Level.WARN -> asWarn()
            Level.ERROR -> asError()
        }
    }

    /**
     * @param level the desired logging level
     *
     * @return output stream that writes with a given level.
     */
    fun `as`(log: Logger, level: Level): Slf4jOutputStream {
        return when (level) {
            Level.TRACE -> asTrace(log)
            Level.DEBUG -> asDebug(log)
            Level.INFO -> asInfo(log)
            Level.WARN -> asWarn(log)
            Level.ERROR -> asError(log)
        }
    }



    /**
     * @return output stream that writes `trace` level of the calling class logger.
     */
    fun asTrace(): Slf4jOutputStream {
        return Slf4jTraceOutputStream(LoggerFactory.getLogger(CallerLoggerUtil.getName(
                null,
                2)))
    }

    /**
     * @return output stream that writes `debug` level of the calling class logger.
     */
    fun asDebug(): Slf4jOutputStream {
        return Slf4jDebugOutputStream(LoggerFactory.getLogger(CallerLoggerUtil.getName(
                null,
                2)))
    }

    /**
     * @return output stream that writes `info` level of the calling class logger.
     */
    fun asInfo(): Slf4jOutputStream {
        return Slf4jInfoOutputStream(LoggerFactory.getLogger(CallerLoggerUtil.getName(
                null,
                2)))
    }

    /**
     * @return output stream that writes `warn` level of the calling class logger.
     */
    fun asWarn(): Slf4jOutputStream {
        return Slf4jWarnOutputStream(LoggerFactory.getLogger(CallerLoggerUtil.getName(
                null,
                2)))
    }

    /**
     * @return output stream that writes `error` level of the calling class logger.
     */
    fun asError(): Slf4jOutputStream {
        return Slf4jErrorOutputStream(LoggerFactory.getLogger(CallerLoggerUtil.getName(
                null,
                2)))
    }





    /**
     * @return output stream that writes `trace` level.
     */
    fun asTrace(log: Logger): Slf4jOutputStream {
        return Slf4jTraceOutputStream(log)
    }

    /**
     * @return output stream that writes `debug` level.
     */
    fun asDebug(log: Logger): Slf4jOutputStream {
        return Slf4jDebugOutputStream(log)
    }

    /**
     * @return output stream that writes `info` level.
     */
    fun asInfo(log: Logger): Slf4jOutputStream {
        return Slf4jInfoOutputStream(log)
    }

    /**
     * @return output stream that writes `warn` level.
     */
    fun asWarn(log: Logger): Slf4jOutputStream {
        return Slf4jWarnOutputStream(log)
    }

    /**
     * @return output stream that writes `error` level.
     */
    fun asError(log: Logger): Slf4jOutputStream {
        return Slf4jErrorOutputStream(log)
    }
}
