/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.platform;

import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import static com.iwave.platform.ConfigurationConstants.*;

/**
 * Service class for running the Framework.
 *
 * @author cdail
 */
public class PlatformService {

    /** Logger Instance */
    private static Logger log = Logger.getLogger(PlatformService.class);

    /** Shutdown hook for handling stopping the services. */
    private static final ShutdownHook shutdownHook = new ShutdownHook();

    /** The deployer of the actual services. */
    private static Platform framework = new Platform();

    public static void main(String[] args) {
        start();

        // Apache Daemon's procrun terminates as soon as the main thread returns.
        // In order to keep that from happening, we need to delay here until
        // the framework is shutdown by calling the stop() method.
        while (framework.getApplicationContext() != null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public static FileSystemXmlApplicationContext start() {
        try {
            framework.start();
        } catch (Exception e) {
            framework.stop();
            System.exit(-1);
        }

        try {
            // Add the shutdown hook
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalArgumentException e) {
        }

        return framework.getApplicationContext();
    }

    public synchronized static void restart() {
        if (log.isInfoEnabled()) {
            log.info("Restarting " + PRODUCT_STRING);
        }

        framework.stop();
        start();
    }

    public synchronized static void stop() {
        framework.stop();
    }

    /**
     * Shutdown hook for shutting down the transport process.
     *
     * @author cdail
     */
    private static class ShutdownHook extends Thread {
        public void run() {
            try {
                PlatformService.framework.stop();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
