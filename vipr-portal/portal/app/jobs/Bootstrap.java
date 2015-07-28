/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import org.slf4j.bridge.SLF4JBridgeHandler;

import play.jobs.Job;
import play.jobs.OnApplicationStart;

@OnApplicationStart
public class Bootstrap extends Job<String> {
    public void doJob() {
        initLogging();

        new DependencyInjectionJob().doJob();

        // Synchronously load initial data
        new LoadInitialData().doJob();
    }

    public void initLogging() {
        // Initialize the JUL -> SLF bridge so all log messages end up in Log4j
        java.util.logging.LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
    }

}
