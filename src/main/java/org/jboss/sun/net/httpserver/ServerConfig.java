/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.jboss.sun.net.httpserver;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parameters that users will not likely need to set but are useful for debugging
 */
class ServerConfig {

    static int clockTick;

    static final int DEFAULT_CLOCK_TICK = 10000; // 10 sec.

    /* These values must be a reasonable multiple of clockTick */
    static final long DEFAULT_IDLE_INTERVAL = 30; // 5 min
    static final int DEFAULT_MAX_IDLE_CONNECTIONS = 200;

    static final long DEFAULT_MAX_REQ_TIME = -1; // default: forever
    static final long DEFAULT_MAX_RSP_TIME = -1; // default: forever
    static final long DEFAULT_TIMER_MILLIS = 1000;

    static final long DEFAULT_DRAIN_AMOUNT = 64 * 1024;

    final long idleInterval;
    final long drainAmount; // max # of bytes to drain from an inputstream
    final int maxIdleConnections;

    // max time a request or response is allowed to take
    final long maxReqTime;
    final long maxRspTime;
    final long timerMillis;
    final boolean debug;

    public ServerConfig() {
        this(null);
    }

    public ServerConfig(Map<String, String> configuration) {
        idleInterval = getLongProperty(configuration, "sun.net.httpserver.idleInterval", DEFAULT_IDLE_INTERVAL) * 1000;
        clockTick = getIntegerProperty(configuration, "sun.net.httpserver.clockTick", DEFAULT_CLOCK_TICK);
        maxIdleConnections = getIntegerProperty(configuration, "sun.net.httpserver.maxIdleConnections", DEFAULT_MAX_IDLE_CONNECTIONS);
        drainAmount = getLongProperty(configuration, "sun.net.httpserver.drainAmount", DEFAULT_DRAIN_AMOUNT);
        maxReqTime = getLongProperty(configuration, "sun.net.httpserver.maxReqTime", DEFAULT_MAX_REQ_TIME);
        maxRspTime = getLongProperty(configuration, "sun.net.httpserver.maxRspTime", DEFAULT_MAX_RSP_TIME);
        timerMillis = getLongProperty(configuration, "sun.net.httpserver.timerMillis", DEFAULT_TIMER_MILLIS);
        debug = getBooleanProperty(configuration, "sun.net.httpserver.debug");
    }

    void checkLegacyProperties(final Logger logger) {

        // legacy properties that are no longer used
        // print a warning to logger if they are set.

        java.security.AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                if (System.getProperty("sun.net.httpserver.readTimeout") != null) {
                    logger.warning("sun.net.httpserver.readTimeout " + "property is no longer used. "
                            + "Use sun.net.httpserver.maxReqTime instead.");
                }
                if (System.getProperty("sun.net.httpserver.writeTimeout") != null) {
                    logger.warning("sun.net.httpserver.writeTimeout " + "property is no longer used. Use "
                            + "sun.net.httpserver.maxRspTime instead.");
                }
                if (System.getProperty("sun.net.httpserver.selCacheTimeout") != null) {
                    logger.warning("sun.net.httpserver.selCacheTimeout " + "property is no longer used.");
                }
                return null;
            }
        });
    }

    boolean debugEnabled() {
        return debug;
    }

    long getIdleInterval() {
        return idleInterval;
    }

    int getClockTick() {
        return clockTick;
    }

    int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    long getDrainAmount() {
        return drainAmount;
    }

    long getMaxReqTime() {
        return maxReqTime;
    }

    long getMaxRspTime() {
        return maxRspTime;
    }

    long getTimerMillis() {
        return timerMillis;
    }

    private long getLongProperty(final Map<String, String> configuration, final String property, final long defaultVal) {
        if (configuration != null && configuration.containsKey(property)) {
            return Long.parseLong(configuration.get(property));
        }

        return AccessController.doPrivileged(new PrivilegedAction<Long>() {
            public Long run() {
                return Long.getLong(property, defaultVal);
            }
        }).longValue();
    }

    private int getIntegerProperty(final Map<String, String> configuration, final String property, final int defaultVal) {
        if (configuration != null && configuration.containsKey(property)) {
            return Integer.parseInt(configuration.get(property));
        }

        return AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Integer.getInteger(property, defaultVal);
            }
        }).intValue();
    }

    private boolean getBooleanProperty(final Map<String, String> configuration, final String property) {
        if (configuration != null && configuration.containsKey(property)) {
            return Boolean.parseBoolean(configuration.get(property));
        }

        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                return Boolean.valueOf(Boolean.getBoolean(property));
            }
        }).booleanValue();
    }

}
