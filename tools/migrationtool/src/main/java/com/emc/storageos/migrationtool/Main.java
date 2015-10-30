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
                if (args != null && args.length == 4) {
                    return 0;
                } else {
                    return 1;
                }

            }
        },
        RGNAMEUPDATER {
            @Override
            int validArgs(String[] args) {
                if (args != null && args.length == 2) {
                    return 0;
                } else {
                    return 1;
                }
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

        for (String arg : args) {
            log.info(arg);
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
                    CGRelationshipEstablisher cgEstablisher =
                            (CGRelationshipEstablisher) ctx.getBean("cgRelationEstablisher"); // NOSONAR
                    /*
                     * cgEstablisher.setHost(args[1]); // ("squid:S2444")
                     * cgEstablisher.setUser(args[2]);
                     * cgEstablisher.setPass(args[3]);
                     * cgEstablisher.setPort(args[4]);
                     * cgEstablisher.setUseSSL(Boolean.getBoolean(args[5]));
                     */
                    cgEstablisher.setHost("lglw9071.lss.emc.com");
                    cgEstablisher.setUser("admin");
                    cgEstablisher.setPass("#1Password");
                    cgEstablisher.setPort("5988");
                    cgEstablisher.setUseSSL(Boolean.getBoolean("false"));
                    // cgEstablisher.start();
                    result = cgEstablisher.execute();
                    break;

                case RGNAMEUPDATER:
                    cmd.validArgs(args);
                    executor = (ReplicationGroupUpdater) ctx.getBean("rgNameUpdater"); // NOSONAR ("squid:S2444")
                    // to start dbclient
                    executor.start();
                    String providerID = args[0] + ":" + args[1];
                    result = executor.execute(providerID);
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
