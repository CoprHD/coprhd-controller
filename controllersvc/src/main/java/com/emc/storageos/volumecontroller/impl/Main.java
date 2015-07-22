/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.volumecontroller.ControllerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * Use this with a spring config to start a controller service.
 */
public class Main {
    private static final Logger _log = LoggerFactory.getLogger(Main.class);
    private static final String SERVICE_BEAN = "controllersvc";

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext(args);
            ControllerService svc = (ControllerService)ctx.getBean(SERVICE_BEAN);

            // set default uncaught exception handler (primarily to get thread dump on OOM error)
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException( final Thread t, final Throwable e ) {
                    try {
                        String errorMsg = String.format("Uncaught throwable: %s , message: %s, thread name: %s", e.getClass().getName(), e.getMessage(), t.getName());
                        _log.error(errorMsg, e);
                        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
                        StringBuilder stackTraceBuilder = new StringBuilder("Full JVM Thread Dump:\n");
                    for (int i=0 ; i< threadInfos.length; i++) {
                            stackTraceBuilder.append(threadInfos[i].toString());
                        }
                        _log.error(stackTraceBuilder.toString());
                    } catch(Exception ex) {
                        _log.error("Error in default uncaught exception handler for {}:", SERVICE_BEAN, ex);
                        System.exit(1);
                    }
                }
            });

            svc.start();
       } catch(Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
       }
   }
}
