/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.sun.net.httpserver;

import java.security.PrivilegedAction;

/**
 * Parameters that users will not likely need to set
 * but are useful for debugging
 */

class ServerConfig {

    static int clockTick;

    static int defaultClockTick = 10000 ; // 10 sec.

    /* These values must be a reasonable multiple of clockTick */
    static long defaultReadTimeout = 20 ; // 20 sec.
    static long defaultWriteTimeout = 60 ; // 60 sec.
    static long defaultIdleInterval = 300 ; // 5 min
    static long defaultSelCacheTimeout = 120 ;  // seconds
    static int defaultMaxIdleConnections = 200 ;

    static long defaultDrainAmount = 64 * 1024;

    static long readTimeout;
    static long writeTimeout;
    static long idleInterval;
    static long selCacheTimeout;
    static long drainAmount;    // max # of bytes to drain from an inputstream
    static int maxIdleConnections;
    static boolean debug = false;

    static {

        idleInterval = ((Long)java.security.AccessController.doPrivileged(
                new GetLongAction(
                "sun.net.httpserver.idleInterval",
                defaultIdleInterval))).longValue() * 1000;

        clockTick = ((Integer)java.security.AccessController.doPrivileged(
                new GetIntegerAction(
                "sun.net.httpserver.clockTick",
                defaultClockTick))).intValue();

        maxIdleConnections = ((Integer)java.security.AccessController.doPrivileged(
                new GetIntegerAction(
                "sun.net.httpserver.maxIdleConnections",
                defaultMaxIdleConnections))).intValue();

        readTimeout = ((Long)java.security.AccessController.doPrivileged(
                new GetLongAction(
                "sun.net.httpserver.readTimeout",
                defaultReadTimeout))).longValue()* 1000;

        selCacheTimeout = ((Long)java.security.AccessController.doPrivileged(
                new GetLongAction(
                "sun.net.httpserver.selCacheTimeout",
                defaultSelCacheTimeout))).longValue()* 1000;

        writeTimeout = ((Long)java.security.AccessController.doPrivileged(
                new GetLongAction(
                "sun.net.httpserver.writeTimeout",
                defaultWriteTimeout))).longValue()* 1000;

        drainAmount = ((Long)java.security.AccessController.doPrivileged(
                new GetLongAction(
                "sun.net.httpserver.drainAmount",
                defaultDrainAmount))).longValue();

        debug = ((Boolean)java.security.AccessController.doPrivileged(
                new GetBooleanAction(
                "sun.net.httpserver.debug"))).booleanValue();
    }

    static long getReadTimeout () {
        return readTimeout;
    }

    static long getSelCacheTimeout () {
        return selCacheTimeout;
    }

    static boolean debugEnabled () {
        return debug;
    }

    static long getIdleInterval () {
        return idleInterval;
    }

    static int getClockTick () {
        return clockTick;
    }

    static int getMaxIdleConnections () {
        return maxIdleConnections;
    }

    static long getWriteTimeout () {
        return writeTimeout;
    }

    static long getDrainAmount () {
        return drainAmount;
    }

    private static class GetLongAction implements PrivilegedAction<Long> {

        private final String property;

        private final long defaultVal;

        public GetLongAction(final String property, final long defaultVal) {
            this.property = property;
            this.defaultVal = defaultVal;
        }

        public Long run() {
            return Long.getLong(property, defaultVal);
        }
    }

    private static class GetIntegerAction implements PrivilegedAction<Integer> {

        private final String property;

        private final int defaultVal;

        public GetIntegerAction(final String property, final int defaultVal) {
            this.property = property;
            this.defaultVal = defaultVal;
        }

        public Integer run() {
            return Integer.getInteger(property, defaultVal);
        }
    }

    private static class GetBooleanAction implements PrivilegedAction<Boolean> {

        private final String property;

        public GetBooleanAction(final String property) {
            this.property = property;
        }

        public Boolean run() {
            return Boolean.valueOf(Boolean.getBoolean(property));
        }
    }
}
