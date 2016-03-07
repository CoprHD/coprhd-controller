/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

//Suppress Sonar violation of Lazy initialization of static fields should be synchronized
//This is a CLI application and main method will not be called by multiple threads
@SuppressWarnings("squid:S2444")
public class BackupCmd {

    private static final Logger log = LoggerFactory.getLogger(BackupCmd.class);

    private static final Options options = new Options();
    private static final String TOOL_NAME = "bkutils";
    private static final String ONLY_RESTORE_SITE_ID = "osi";
    private static final String LOG_EXT = ".log";
    private static BackupOps backupOps;
    private static CommandLine cli;
    private static RestoreManager restoreManager;

    private enum CommandType {
        create("Create backup, default name is timestamp"),
        list("List all backups"),
        delete("Delete specific backup"),
        restore("Purge ViPR data and restore specific backup\n" +
                "with args: <backup dir> <name> osi(optional)\n" +
                "If \"osi\" is used, only site id will be retored\n"),
        quota("Get backup quota info, unit:GB\n"),
        force("Execute operation on quorum nodes"),
        purge("Purge the existing ViPR data with arg\n" +
                "[ismultivdc], yes or no(default)");

        private String description;

        private CommandType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    static {
        Option createOption = OptionBuilder.hasOptionalArg()
                .withArgName("[backup]")
                .withDescription(CommandType.create.getDescription())
                .withLongOpt(CommandType.create.name())
                .create("c");
        options.addOption(createOption);
        options.addOption("l", CommandType.list.name(), false, CommandType.list.getDescription());
        options.addOption("d", CommandType.delete.name(), true, CommandType.delete.getDescription());
        Option restoreOption = OptionBuilder.hasArgs()
                .withArgName("args")
                .withDescription(CommandType.restore.getDescription())
                .withLongOpt(CommandType.restore.name())
                .create("r");
        options.addOption(restoreOption);
        options.addOption("q", CommandType.quota.name(), false, CommandType.quota.getDescription());
        options.addOption("f", CommandType.force.name(), false, CommandType.force.getDescription());
        Option purgeOption = OptionBuilder.hasOptionalArg()
                .withArgName("[ismultivdc]")
                .withDescription(CommandType.purge.getDescription())
                .withLongOpt(CommandType.purge.name())
                .create("p");
        options.addOption(purgeOption);
    }

    private static void initRestoreManager() {
        if (restoreManager == null) {
            ApplicationContext context = new ClassPathXmlApplicationContext("backup-restore-conf.xml");
            restoreManager = context.getBean("restoreManager", RestoreManager.class);
        }
    }

    private static void initCommandLine(String[] args) {
        CommandLineParser parser = new PosixParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("");
            }
            cli = parser.parse(options, args);
            if (cli.getOptions().length == 0) {
                throw new IllegalArgumentException(
                        String.format("Invalid argument: %s%n", Arrays.toString(args)));
            }
            String[] invalidArgs = cli.getArgs();
            if (invalidArgs != null && invalidArgs.length != 0) {
                throw new IllegalArgumentException(
                        String.format("Invalid argument: %s%n", Arrays.toString(invalidArgs)));
            }
        } catch (Exception p) {
            System.err.print(p.getMessage());
            log.error("Caught Exception when parsing command line input: ", p);
            formatter.printHelp(TOOL_NAME, options);
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        init(args);

        try {
            createBackup();
            listBackup();
            deleteBackup();
            purgeViprData();
            restoreBackup();
            getQuota();
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            System.exit(-1);
        }

        System.exit(0);
    }

    private static void init(String[] args) {
        initLogPermission();

        initCommandLine(args);
        initRestoreManager();

        if (!cli.hasOption(CommandType.restore.name()) && !cli.hasOption(CommandType.purge.name())) {        
            initBackupOps();
        }
    }

    private static void initBackupOps() {
        if (backupOps == null) {
            ApplicationContext context = new ClassPathXmlApplicationContext("backup-client-conf.xml");
            backupOps = context.getBean("backupOps", BackupOps.class);
        }
    }

    private static void createBackup() {
        if (!cli.hasOption(CommandType.create.name())) {
            return;
        }
        String backupName = cli.getOptionValue(CommandType.create.name());
        if (backupName == null || backupName.isEmpty()) {
            backupName = BackupOps.createBackupName();
        }

        boolean force = false;
        if (cli.hasOption(CommandType.force.name())) {
            force = true;
        }

        System.out.println("Start to create backup...");
        backupOps.createBackup(backupName, force);
        System.out.println(
                String.format("Backup (%s) is created successfully", backupName));
    }

    private static void listBackup() {
        if (!cli.hasOption(CommandType.list.name())) {
            return;
        }
        System.out.println("Start to list backup...");
        List<BackupSetInfo> backupList = backupOps.listBackup();
        System.out.println(
                String.format("Backups are listed successfully, total: %d", backupList.size()));
        if (backupList.isEmpty()) {
            return;
        }
        int maxLength = Integer.MIN_VALUE;
        for (BackupSetInfo backupSetInfo : backupList) {
            if (backupSetInfo.getName().length() > maxLength) {
                maxLength = backupSetInfo.getName().length();
            }
        }
        maxLength = maxLength < 18 ? 20 : maxLength + 2;
        String titleFormat = String.format(BackupConstants.LIST_BACKUP_TITLE, maxLength);
        String infoFormat = String.format(BackupConstants.LIST_BACKUP_INFO, maxLength);

        System.out.println(String.format(titleFormat, "NAME", "SIZE(MB)", "CREATION TIME"));
        Format format = new SimpleDateFormat(BackupConstants.DATE_FORMAT);
        StringBuilder builder = new StringBuilder();
        for (BackupSetInfo backupSetInfo : backupList) {
            double sizeMb = backupSetInfo.getSize() * 1.0 / BackupConstants.MEGABYTE;
            builder.append(String.format(infoFormat,
                    backupSetInfo.getName(), sizeMb,
                    format.format(new Date(backupSetInfo.getCreateTime()))));
            builder.append("\n");
        }
        System.out.print(builder.toString());
    }

    private static void deleteBackup() {
        if (!cli.hasOption(CommandType.delete.name())) {
            return;
        }
        System.out.println("Start to delete backup...");
        String backupName = cli.getOptionValue(CommandType.delete.name());
        backupOps.deleteBackup(backupName);
        System.out.println(
                String.format("Backup (%s) is deleted successfully", backupName));
    }

    private static void purgeViprData() {
        if (!cli.hasOption(CommandType.purge.name())) {
            return;
        }
        String multiVdcStr = cli.getOptionValue(CommandType.purge.name());
        System.out.println("Start to purge ViPR data...");
        if (multiVdcStr == null || "no".equalsIgnoreCase(multiVdcStr.trim())) {
            restoreManager.purge(false);
        } else if ("yes".equalsIgnoreCase(multiVdcStr.trim())) {
            restoreManager.purge(true);
        } else {
            throw new IllegalArgumentException("Invalid argument, must be yes or no(default)");
        }
        System.out.println("ViPR data has been purged successfully!");
    }

    private static void restoreBackup() {
        if (!cli.hasOption(CommandType.restore.name())) {
            return;
        }
        String[] restoreArgs = cli.getOptionValues(CommandType.restore.name());
        if (restoreArgs.length < 2 || restoreArgs.length > 3) {
            System.out.println("Invalid number of restore args.");
            new HelpFormatter().printHelp(TOOL_NAME, options);
            System.exit(-1);
        }

        String restoreSrcDir = restoreArgs[0];
        String snapshotName = restoreArgs[1];
        if (restoreArgs.length == 3) {
            if (ONLY_RESTORE_SITE_ID.equals(restoreArgs[2])) {
                restoreManager.setOnlyRestoreSiteId(true);
            } else {
                System.out.println("If third parameter is specified for restore option, it can only be \"osi\"");
                new HelpFormatter().printHelp(TOOL_NAME, options);
                System.exit(-1);
            }
        }

        boolean geoRestoreFromScratch = false;
        if (cli.hasOption(CommandType.force.name())) {
            geoRestoreFromScratch = true;
        }

        restoreManager.restore(restoreSrcDir, snapshotName, geoRestoreFromScratch);

        System.out.println("***Important***");
        System.out.println("Please start ViPR service after all nodes have been " +
                "restored (command: \"service storageos start\").");
        System.out.println("ViPR has risk of data lost before data repair finished, " +
                "please check the db repair process by service log");
    }

    private static void getQuota() {
        if (!cli.hasOption(CommandType.quota.name())) {
            return;
        }
        System.out.println("Start to get quota of backup...");
        int quota = backupOps.getQuotaGb();
        System.out.println(String.format("Quota of backup is: %d GB", quota));
    }

    private static void initLogPermission() {
        String homeDir =System.getProperty("product.home");
        StringBuilder sb = new StringBuilder().append(homeDir).append(File.separator)
                .append("logs").append(File.separator).append(TOOL_NAME).append(LOG_EXT);
        File logFile = new File(sb.toString());
        //check if bkutils.log permission is 644
        Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.OTHERS_READ);
        try{
            if (logFile.exists()) {
                Set<PosixFilePermission> permsRead = Files.getPosixFilePermissions(logFile.toPath());
                if (perms.equals(permsRead)) {
                    return;
                }
            }else {
                boolean blnCreated = logFile.createNewFile();
            }
            Files.setPosixFilePermissions(logFile.toPath(),perms);
        }catch (IOException e ) {
            log.error("Failed to operate the log file {}. e={}",logFile,e);
        }
    }

}
