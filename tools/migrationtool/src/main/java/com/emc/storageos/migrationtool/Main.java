/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.migrationtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static ClassPathXmlApplicationContext ctx = null;

    private static Executor executor = null;

    private enum Command {
        HELP {
            @Override
            int validArgs(String[] args) {
                return 0;
            }
        },
        CGRELATIONUPDATE {
            @Override
            int validArgs(String[] args) {
                return 0;
            }
        },
        RGNAMEUPDATER {
            @Override
            int validArgs(String[] args) {
                return 0;
            }
        };

        // Validate your arguments here.
        abstract int validArgs(String[] args);
    }

    private static void usage() {
        System.out.println("Usage: ");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }

        Command cmd;
        try {
            cmd = Command.valueOf(args[0].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid command " + args[0]);
            usage();
            return;
        }

        try {

            // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
            // This is a CLI application and main method will not be called by multiple threads
            ctx = new ClassPathXmlApplicationContext("/migrationtool-conf.xml"); // NOSONAR ("squid:S2444")
            boolean result = false;
            log.info("Started executing command {}", cmd);
            switch (cmd) {
                case HELP:
                    usage();
                    break;
                case CGRELATIONUPDATE:
                    executor = (CGRelationshipEstablisher) ctx.getBean("cgRelationEstablisher"); // NOSONAR ("squid:S2444")
                    executor.start();
                    result = executor.execute();
                    break;

                case RGNAMEUPDATER:
                    executor = (ReplicationGroupUpdater) ctx.getBean("rgNameUpdater"); // NOSONAR ("squid:S2444")
                    executor.start();
                    result = executor.execute();
                default:
                    throw new IllegalArgumentException("Invalid command");
            }
            if (result) {
                log.info("Successfully executed command: {}", cmd);
            } else {
                log.info("Failed to execute command: {}", cmd);
            }
        } catch (Exception e) {
            log.error("Exception e=", e);
            usage();
        } finally {
            stop();
        }
    }

    /**
     * Stop client and exit
     */
    private static void stop() {
        if (executor != null) {
            executor.stop();
        }
        System.exit(0);
    }

}
