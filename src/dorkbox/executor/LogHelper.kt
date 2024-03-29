/*
 * Copyright 2021 dorkbox, llc
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

@file:Suppress("DuplicatedCode")

package dorkbox.executor

import org.slf4j.Logger

object LogHelper {
    fun fixSshLogger(log: Logger?) {
        try {
            // exception is thrown if logback is not available.
            val logLevel = if (log != null) {
                (log as ch.qos.logback.classic.Logger).level
            } else {
                ch.qos.logback.classic.Level.ERROR
            }

            mutableListOf<Class<*>>(
                net.schmizz.sshj.transport.random.JCERandom::class.java,
                net.schmizz.sshj.transport.kex.Curve25519SHA256::class.java,
                net.schmizz.sshj.common.KeyType::class.java
            ).map {
                org.slf4j.LoggerFactory.getLogger(it) as ch.qos.logback.classic.Logger
            }.forEach {
                it.level = logLevel
            }
        } catch (ignored: Exception) {
        }
    }

    /**
     * see http://logback.qos.ch/manual/architecture.html for more info
     *  logger order goes (from lowest to highest) TRACE->DEBUG->INFO->WARN->ERROR->OFF
     */
    fun logAtLowestLevel(log: Logger?, message: String, vararg arguments: Any) {
        if (log == null) {
            return
        }

        when {
            log.isTraceEnabled -> {
                log.trace(message, *arguments)
            }
            log.isDebugEnabled -> {
                log.debug(message, *arguments)
            }
            log.isInfoEnabled -> {
                log.info(message, *arguments)
            }
            log.isWarnEnabled -> {
                log.warn(message, *arguments)
            }
            log.isErrorEnabled -> {
                log.error(message, *arguments)
            }
        }
    }

    fun getLogFactory(logger: Logger?): net.schmizz.sshj.common.LoggerFactory {
        if (logger == null) {
            return object : net.schmizz.sshj.common.LoggerFactory {
                override fun getLogger(name: String): Logger {
                    return org.slf4j.helpers.NOPLogger.NOP_LOGGER
                }

                override fun getLogger(clazz: Class<*>): Logger {
                    return org.slf4j.helpers.NOPLogger.NOP_LOGGER
                }
            }
        }

        return object : net.schmizz.sshj.common.LoggerFactory {
            override fun getLogger(name: String): Logger {
                return LogHelperLogger(name)
            }

            override fun getLogger(clazz: Class<*>): Logger {
                var name = clazz.name
                if (name.startsWith(SshExecOptions::class.java.name)) {
                    name = net.schmizz.sshj.SSHClient::class.java.name
                }
                return LogHelperLogger(name)
            }
        }
    }


    /**
     * A direct implementation of [Logger] that delegates to the lowest possible logger.
     *
     * This is NOT to be used for performant critical logs!
     */
    open class LogHelperLogger(name: String) : org.slf4j.helpers.MarkerIgnoringBase() {
        val logger = org.slf4j.LoggerFactory.getLogger(name)!!

        /**
         * Always returns the string value "NOP".
         */
        override fun getName(): String {
            return name
        }

        override fun isTraceEnabled(): Boolean {
            return true
        }

        fun log(msg: String) {
            when {
                logger.isTraceEnabled -> {
                    logger.trace(msg)
                }
                logger.isDebugEnabled -> {
                    logger.debug(msg)
                }
                logger.isInfoEnabled -> {
                    logger.info(msg)
                }
                logger.isWarnEnabled -> {
                    logger.warn(msg)
                }
                logger.isErrorEnabled -> {
                    logger.error(msg)
                }
            }
        }

        fun log(format: String, arg: Any) {
            when {
                logger.isTraceEnabled -> {
                    logger.trace(format, arg)
                }
                logger.isDebugEnabled -> {
                    logger.debug(format, arg)
                }
                logger.isInfoEnabled -> {
                    logger.info(format, arg)
                }
                logger.isWarnEnabled -> {
                    logger.warn(format, arg)
                }
                logger.isErrorEnabled -> {
                    logger.error(format, arg)
                }
            }
        }

        fun log(format: String, arg1: Any, arg2: Any) {
            when {
                logger.isTraceEnabled -> {
                    logger.trace(format, arg1, arg2)
                }
                logger.isDebugEnabled -> {
                    logger.debug(format, arg1, arg2)
                }
                logger.isInfoEnabled -> {
                    logger.info(format, arg1, arg2)
                }
                logger.isWarnEnabled -> {
                    logger.warn(format, arg1, arg2)
                }
                logger.isErrorEnabled -> {
                    logger.error(format, arg1, arg2)
                }
            }
        }

        fun log(format: String, vararg argArray: Any) {
            when {
                logger.isTraceEnabled -> {
                    logger.trace(format, argArray)
                }
                logger.isDebugEnabled -> {
                    logger.debug(format, argArray)
                }
                logger.isInfoEnabled -> {
                    logger.info(format, argArray)
                }
                logger.isWarnEnabled -> {
                    logger.warn(format, argArray)
                }
                logger.isErrorEnabled -> {
                    logger.error(format, argArray)
                }
            }
        }

        fun log(msg: String, t: Throwable) {
            when {
                logger.isTraceEnabled -> {
                    logger.trace(msg, t)
                }
                logger.isDebugEnabled -> {
                    logger.debug(msg, t)
                }
                logger.isInfoEnabled -> {
                    logger.info(msg, t)
                }
                logger.isWarnEnabled -> {
                    logger.warn(msg, t)
                }
                logger.isErrorEnabled -> {
                    logger.error(msg, t)
                }
            }
        }


        override fun trace(msg: String) {
            log(msg)
        }

        override fun trace(format: String, arg: Any) {
            log(format, arg)
        }

        override fun trace(format: String, arg1: Any, arg2: Any) {
            log(format, arg1, arg2)
        }

        override fun trace(format: String, vararg argArray: Any) {
            log(format, argArray)
        }

        override fun trace(msg: String, t: Throwable) {
            log(msg, t)
        }

        override fun isDebugEnabled(): Boolean {
            return true
        }

        override fun debug(msg: String) {
            log(msg)
        }

        override fun debug(format: String, arg: Any) {
            log(format, arg)
        }

        override fun debug(format: String, arg1: Any, arg2: Any) {
            log(format, arg1, arg2)
        }

        override fun debug(format: String, vararg argArray: Any) {
            log(format, argArray)
        }

        override fun debug(msg: String, t: Throwable) {
            log(msg, t)
        }

        override fun isInfoEnabled(): Boolean {
            return true
        }

        override fun info(msg: String) {
            log(msg)
        }

        override fun info(format: String, arg1: Any) {
            log(format, arg1)
        }

        override fun info(format: String, arg1: Any, arg2: Any) {
            log(format, arg1, arg2)
        }

        override fun info(format: String, vararg argArray: Any) {
            log(format, argArray)
        }

        override fun info(msg: String, t: Throwable) {
            log(msg, t)
        }

        override fun isWarnEnabled(): Boolean {
            return true
        }

        override fun warn(msg: String) {
            log(msg)
        }

        override fun warn(format: String, arg1: Any) {
            log(format, arg1)
        }

        override fun warn(format: String, arg1: Any, arg2: Any) {
            log(format, arg1, arg2)
        }

        override fun warn(format: String, vararg argArray: Any) {
            log(format, argArray)
        }

        override fun warn(msg: String, t: Throwable) {
            log(msg, t)
        }

        override fun isErrorEnabled(): Boolean {
            return true
        }

        override fun error(msg: String) {
            log(msg)
        }

        override fun error(format: String, arg1: Any) {
            log(format, arg1)
        }

        override fun error(format: String, arg1: Any, arg2: Any) {
            log(format, arg1, arg2)
        }

        override fun error(format: String, vararg argArray: Any) {
            log(format, argArray)
        }

        override fun error(msg: String, t: Throwable) {
            log(msg, t)
        }
    }
}
