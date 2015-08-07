/* **********************************************************
 * Copyright 2008 VMware, Inc.  All rights reserved. -- VMware Confidential
 * **********************************************************/

package com.emc.storageos.vasa.util.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Simplistic time counter for code profiling
 */
public class SimpleTimeCounter {
    private static Log log = LogFactory.getLog(SimpleTimeCounter.class);
    private String name;
    private long startTime;

    /**
     * Creates and starts the counter
     * Side affect: the start time is printed to the log
     * 
     * @param name: name of code segment to be profiled,
     *            which will be printed in the log
     */
    public SimpleTimeCounter(String name) {
        start(name);
    }

    /**
     * Stops and starts the counter for a different code segment.
     * This allows the counter to be re-used
     * 
     * @param name: name of code segment to be profiled,
     *            which will be printed in the log
     */
    public long reset(String name) {
        stop();
        return start(name);
    }

    /**
     * Starts the counter. Side affect: the start time is printed to
     * the log
     * 
     * @param name: name of code segment to be profiled,
     *            which will be printed in the log
     */
    public long start(String name) {
        this.name = name;
        startTime = System.currentTimeMillis();
        log.debug("TIMER STARTED: " + name);
        return startTime;
    }

    /**
     * Stops the counter. Side affect: the stop time and time
     * difference since start is printed to the log
     */
    public long stop() {
        long stopTime = System.currentTimeMillis();
        long timeTaken = stopTime - startTime;
        log.debug("TIMER STOPPED: " + name);
        log.debug("TIME TAKEN: " + name + ": " + (timeTaken / 1000.0));
        return timeTaken;
    }
}
