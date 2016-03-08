/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.zkutils;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.MigrationStatus;

/**
 * Create simple cli for zk, implements these functions:
 * 1. Dump contents from zk in human readable form
 * 2. Control the upgrade process(including manually prevent, suspend, release, get info)
 */
// Suppress Sonar violation of Lazy initialization of static fields should be synchronized
// There's only one thread initializing static fields, so it's thread safe.
@SuppressWarnings("squid:S2444")
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String WITH_DATA = "-withdata";
    private static final String HOST_ARG = "-host";
    private static final String LOCK_UPGRADE = "-upgrade";
    private static final String RESET_MIFAIL = "-migrationfail";
    private static final String REGEX_IP = "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";

    private static LockCmdHandler lockCmdHandler;
    private static ZkCmdHandler zkCmdHandler;
    private static ZkTxnHandler zkTxnHandler;
    private static ServiceConfigCmdHandler serviceCmdHandler;
    private static String host = "localhost";
    private static int port = 2181;
    private static boolean withData = false;
    private static String path = null;
    private static KeystoreCmdHandler keystoreCmdHandler;

    private enum Command {
        LOCK, HOLD, RELEASE, INFO, PATH, EPHEMERAL, RESET, GETLASTVALIDZXID, TRUNCATETXNLOG, GETKEYANDCERT, EXPORTKEYSTORE, SAVE_SSH_KEYS,
        GEN_SSH_AUTH_KEYS, SET, ADD_DR_CONFIG
    }

    /**
     * Dump usage
     */
    private static void usage() {
        System.out.println("Usage: ");
        System.out.println("\tPrint Contents:");
        System.out.println(String.format("\t%s [%s] [%s <IP>] <Path Substring>",
                Command.PATH.name().toLowerCase(), WITH_DATA, HOST_ARG));
        System.out.println(String.format("\t%s [%s <IP>] <Path Substring>",
                Command.SET.name().toLowerCase(), HOST_ARG));
        System.out.println("\t\tData will be read from STDIN");
        System.out.println(String.format("\t%s [%s] [%s <IP>]",
                Command.EPHEMERAL.name().toLowerCase(), WITH_DATA, HOST_ARG));
        System.out.println(String.format("\t\t%s <IP>\tSpecify <IP>, default is localhost.", HOST_ARG));
        System.out.println(String.format("\t\t%s \tPrint with data content.", WITH_DATA));

        System.out.println("\n\tHandle ZK txn log:");
        System.out.println(String.format("\t%s \t\tGet last valid logged txn id.",
                Command.GETLASTVALIDZXID.name().toLowerCase()));
        System.out.println(String.format("\t%s \t\tTruncate to the last valid txnlog.", Command.TRUNCATETXNLOG.name().toLowerCase()));
        System.out.println(String.format("\t%s <arg>(in hex)\t\tTruncate to the specific txn log.", Command.TRUNCATETXNLOG.name()
                .toLowerCase()));
        System.out.println(String.format("\t%s <key> <value>\t\tAdd \"key=value\" line to DR configuration",
                Command.ADD_DR_CONFIG.name().toLowerCase()));

        System.out.println("\n\tHandle Lock Process:");
        System.out.println(String.format("\t%s <arg>\t\tLock to prevent starting <arg> process",
                Command.LOCK.name().toLowerCase()));
        System.out.println(String.format("\t%s <arg>\t\tHold on to suspend <arg> process",
                Command.HOLD.name().toLowerCase()));
        System.out.println(String.format("\t%s <arg>\t\tRelase all lock to <arg> continuously",
                Command.RELEASE.name().toLowerCase()));
        System.out.println(String.format("\t%s <arg>\t\tShow the infomation about <arg>",
                Command.INFO.name().toLowerCase()));
        System.out.println(String.format("\t\tAvailable Arguments:"));
        System.out.println(String.format("\t\t%s\tHandle upgrading process.", LOCK_UPGRADE));

        System.out.println("\n\tService Configuration:");
        System.out.println(String.format("\t%s <arg>\t\tReset the service configuration of <arg>",
                Command.RESET.name().toLowerCase()));
        System.out.println(String.format("\t\tAvailable Arguments:"));
        System.out.println(String.format("\t\t%s\tReset MIGRATION_FAILED status.", RESET_MIFAIL));
        System.out.println("\n\tSecurity operations:");
        System.out.println(String.format(
                "\t%s \t\t\tGet VDC's key and certificate chain.", Command.GETKEYANDCERT
                        .name().toLowerCase()));
        System.out.println(String.format(
                "\t%s \t\t\tSave the ssh keys and configs for host, root and svcuser",
                Command.SAVE_SSH_KEYS.name().toLowerCase()));
        System.out.println(String.format(
                "\t%s \t\t\tExport the keystore to the default location.",
                Command.EXPORTKEYSTORE.name().toLowerCase()));
        System.out.println(String.format(
                "\t%s \t\t\tGenerate AuthorizedKeys2 for each user.",
                Command.GEN_SSH_AUTH_KEYS.name().toLowerCase()));
    }

    /**
     * Zkutils entry method.
     * 
     * @param args
     *            command and arguments.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
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
            switch (cmd) {
                case PATH:
                    if (args.length < 2) {
                        throw new IllegalArgumentException("The path substring is missing");
                    }
                    processZkCmdArgs(args);
                    initZkCmdHandler(host, port, withData);
                    zkCmdHandler.printNodes(path);
                    break;
                case SET:
                    if (args.length < 2) {
                        throw new IllegalArgumentException("The path and/or data substring is missing");
                    }
                    processZkCmdArgs(args);
                    initZkCmdHandler(host, port, withData);
                    zkCmdHandler.setNodeData(path);
                    break;
                case EPHEMERAL:
                    processZkCmdArgs(args);
                    initZkCmdHandler(host, port, withData);
                    zkCmdHandler.printEphemeralNodes();
                    break;
                case ADD_DR_CONFIG:
                    if (args.length != 3) {
                        throw new IllegalArgumentException("Invalid parameters");
                    }
                    String key = args[1] != null ? args[1].trim() : "";
                    String value = args[2] != null ? args[2].trim() : "";
                    if (key.isEmpty() || value.isEmpty()) {
                        throw new IllegalArgumentException("Invalid parameters");
                    }
                    processZkCmdArgs(args);
                    initZkCmdHandler(host, port, withData);
                    zkCmdHandler.addDrConfig(key, value);
                    break;
                case GETLASTVALIDZXID:
                    if (args.length > 1) {
                        throw new IllegalArgumentException("Invalid parameters");
                    }
                    initZkTxnHandler();
                    zkTxnHandler.getLastValidZxid();
                    break;
                case TRUNCATETXNLOG:
                    if (args.length > 2) {
                        throw new IllegalArgumentException("Invalid parameters");
                    }
                    initZkTxnHandler();
                    boolean success = false;
                    if (args.length < 2) {
                        success = zkTxnHandler.truncateToZxid();
                    } else {
                        success = zkTxnHandler.truncateToZxid(args[1]);
                    }
                    if (!success) {
                        System.exit(1);
                    }
                    break;
                case LOCK:
                    processLockCmdArgs(args);
                    initLockCmdHandler();
                    lockCmdHandler.aquireUpgradeLocks();
                    // because using the non-persistent lock, need exit manually
                    System.out.println("If you want to upgrade, "
                            + "Please kill this thread and use 'release' command");
                    break;
                case HOLD:
                    processLockCmdArgs(args);
                    initLockCmdHandler();
                    lockCmdHandler.aquireUpgradeLockByLoop();
                    System.out.println("If you want to continue, Please use 'release' command");
                    stop();
                    break;
                case RELEASE:
                    processLockCmdArgs(args);
                    initLockCmdHandler();
                    lockCmdHandler.releaseAllLocks();
                    stop();
                    break;
                case INFO:
                    processLockCmdArgs(args);
                    initLockCmdHandler();
                    lockCmdHandler.getUpgradeLockOwner();
                    stop();
                    break;
                case RESET:
                    MigrationStatus status = processServiceCmdArgs(args);
                    initServiceCmdHandler();
                    serviceCmdHandler.resetMigrationStatus(status);
                    break;
                case GETKEYANDCERT:
                    if (args.length > 1) {
                        throw new IllegalArgumentException("Invalid parameters");
                    }
                    initKeystoreCmdHandler();
                    keystoreCmdHandler.getViPRKey();
                    System.out.println();
                    keystoreCmdHandler.getViPRCertificate();
                    break;
                case EXPORTKEYSTORE:
                    if (args.length > 1) {
                        throw new IllegalArgumentException("Invalid parameters");
                    }
                    initKeystoreCmdHandler();
                    try {
                        keystoreCmdHandler.exportKeystore();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        System.err.println("Exception e=" + e);
                        System.exit(1);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Invalid command");
            }
        } catch (Exception e) {
            System.err.println("Exception e=" + e);
            usage();
        } finally {
            if (!cmd.equals(Command.LOCK)) {
                stop();
            }
        }
    }

    /**
     * @throws InterruptedException
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * 
     */
    private static void initKeystoreCmdHandler() throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException, IOException,
            InterruptedException {
        keystoreCmdHandler = new KeystoreCmdHandler();
    }

    private static void initLockCmdHandler() {
        lockCmdHandler = new LockCmdHandler();
    }

    private static void initZkCmdHandler(String host, int port, boolean withData) {
        zkCmdHandler = new ZkCmdHandler(host, port, withData);
    }

    private static void initZkTxnHandler() {
        zkTxnHandler = new ZkTxnHandler();
    }

    private static void initServiceCmdHandler() {
        serviceCmdHandler = new ServiceConfigCmdHandler();
    }

    /**
     * Deal with zk cmd args.
     * 
     * @param args
     *            the args from Main
     */
    private static void processZkCmdArgs(String[] args) {
        if (args[0].trim().equalsIgnoreCase(Command.PATH.name()) || args[0].trim().equalsIgnoreCase(Command.SET.name())) {
            if (args[args.length - 1].trim().equalsIgnoreCase(WITH_DATA)
                    || args[args.length - 1].trim().equalsIgnoreCase(HOST_ARG)
                    || args[args.length - 1].trim().matches(REGEX_IP)
                    || args[args.length - 1].trim().startsWith("-")) {
                throw new IllegalArgumentException("The path substring is missing");
            }
            path = args[args.length - 1];
        }
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(WITH_DATA)) {
                withData = true;
            }
            if (args[i].equalsIgnoreCase(HOST_ARG)) {
                if (args.length - 1 == i || !args[i + 1].trim().matches(REGEX_IP)) {
                    throw new IllegalArgumentException("Empty or Wrong IP address!");
                }
                host = args[i + 1];
                i++;
            }
        }
    }

    /**
     * Deal with lock cmd args.
     * 
     * @param args
     *            the args from Main
     */
    private static void processLockCmdArgs(String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Too much arguments");
        }
        if (args.length == 1 || !args[args.length - 1].equalsIgnoreCase(LOCK_UPGRADE)) {
            throw new IllegalArgumentException(
                    "The <arg> is necessary and from the 'Available Arguments'.");
        }
    }

    /**
     * Deal with service configuration cmd args.
     * 
     * @param args
     *            the args from Main
     * @return MigrationStatus
     * 
     */
    private static MigrationStatus processServiceCmdArgs(String[] args) {
        String argument;
        if (args.length != 2) {
            throw new IllegalArgumentException("Wrong arguments");
        }
        argument = args[1];

        if (!argument.equalsIgnoreCase(RESET_MIFAIL)) {
            log.error("Invalid command:{} for {}", argument, Command.RESET);
            throw new IllegalArgumentException("Invalid command: " + argument);
        }
        return MigrationStatus.FAILED;
    }

    private static void stop() {
        if (zkCmdHandler != null) {
            zkCmdHandler.releaseConnection();
        }
        System.exit(0);
    }
}
