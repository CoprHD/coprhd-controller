/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.sa.util;

import java.time.Duration;
import java.time.LocalTime;

public class PerfTimer {

    private LocalTime startTime;
    public void start() {
        startTime = LocalTime.now();
    }

    public long probe() {
        Duration duration = Duration.between(startTime, LocalTime.now());
        return duration.getSeconds();
    }

    public static void main(String[] args) {
        PerfTimer timer = new PerfTimer();
        timer.start();
        System.out.println(timer.probe());
    }
}
