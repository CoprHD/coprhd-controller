/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.Socket;
import java.util.List;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Comparator;

import org.apache.commons.io.FileUtils;

import com.emc.storageos.management.backup.util.ValidationUtil;
import com.emc.storageos.management.backup.util.ValidationUtil.*;
import com.emc.storageos.management.backup.exceptions.BackupException;
import com.emc.storageos.services.util.Exec;

public class ZkBackupHandler extends BackupHandler {
    private static final Logger log = LoggerFactory.getLogger(ZkBackupHandler.class);
    private static final String ZK_ACCEPTED_EPOCH = "acceptedEpoch";
    private static final String ZK_CURRENT_EPOCH = "currentEpoch";
    private static final String CONNECT_ZK_HOST = "localhost";
    private static final int CONNECT_ZK_PORT = 2181;
    private File zkDir;
    private List<String> fileTypeList;
    private File siteIdFile;

    public File getSiteIdFile() {
        return siteIdFile;
    }

    public void setSiteIdFile(File siteIdFile) {
        this.siteIdFile = siteIdFile;
    }

    /**
     * Sets zk file location
     * 
     * @param zkDir
     *            The path of ZK location
     */
    public void setZkDir(File zkDir) {
        this.zkDir = zkDir;
    }

    /**
     * Gets zk file location
     * 
     * @return zkDir
     *         The path of ZK location
     */
    public File getZkDir() {
        return zkDir;
    }

    /**
     * Sets file type list that need to be backuped
     * 
     * @param fileTypeList
     *            The list of zk file type
     */
    public void setFileTypeList(List<String> fileTypeList) {
        this.fileTypeList = fileTypeList;
    }

    /**
     * Gets file type list that need to be backuped
     * 
     * @return fileTypeList
     *         The list of zk file type
     */
    public List<String> getFileTypeList() {
        return fileTypeList;
    }

    /**
     * Make sure the quorum service is ready
     */
    public void validateQuorumStatus() {
        String result = readZkInfo("ruok", "imok");
        log.info("Validate Zookeeper status result = {}", result);
        if (result == null) {
            throw BackupException.retryables.quorumServiceNotReady();
        }
    }

    /**
     * Just backup the zk files in the leader node
     */
    public boolean isEligibleForBackup() {
        String result = readZkInfo("stat", "Mode");
        if (result == null || !result.contains(": ")) {
            throw BackupException.fatals.failedToParseLeaderStatus(result);
        }
        String mode = (result.split(": "))[1];
        if (mode.equals("leader") || mode.equals("standalone") || mode.equals("observer")) {
            return true;
        } else {
            log.info("Status mode is: {}", mode);
            return false;
        }
    }

    /**
     * Get zk info by zk cli
     */
    private String readZkInfo(String cmd, String matchPattern) {
        String result = null;
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        log.debug("cmd={}, match={}", cmd, matchPattern);
        try {
            socket = new Socket(CONNECT_ZK_HOST, CONNECT_ZK_PORT);
            in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream()));
            out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())), true);
            out.println(cmd);

            String line = null;
            while ((line = in.readLine()) != null) {
                log.debug(line);
                if (line.contains(matchPattern)) {
                    result = line;
                }
            }
        } catch (IOException e) {
            throw BackupException.fatals.failedToReadZkInfo(e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    log.error("close input stream failed. e=", e);
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    log.error("close output stream failed. e=", e);
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    log.error("close socket failed. e=", e);
                }
            }
        }

        return result;
    }

    /**
     * Make sure the accepted epoch is equal to current epoch
     */
    public boolean checkEpochEqual() {
        int acceptedEpoch = readEpoch(new File(zkDir, ZK_ACCEPTED_EPOCH));
        int currentEpoch = readEpoch(new File(zkDir, ZK_CURRENT_EPOCH));
        return (acceptedEpoch == currentEpoch);
    }

    /**
     * Read epoch from epoch file
     */
    private int readEpoch(File epochFile) {
        int epoch = -1;
        if (!epochFile.exists()) {
            return epoch;
        }

        try {
            Scanner scanner = new Scanner(epochFile);
            epoch = scanner.nextInt();
            log.debug("Got epoch {} from file {}", epoch, epochFile.getName());
        } catch (IOException e) {
            // TODO: error handling
            log.error("read epoch from file({}) failed. e=", epochFile.getName(), e);
        }
        return epoch;
    }

    /**
     * Sanity check after backup
     */
    private boolean checkZkConditionAfterBackup() {
        validateQuorumStatus();
        if (!isEligibleForBackup()) {
            log.error("This node is not leader any more");
            return false;
        }
        if (!checkEpochEqual()) {
            log.error("The accepted and current epoch are not equal");
            return false;
        }
        return true;
    }

    /**
     * Backup all the files with type in the defined type list
     */
    public void backupFolder(File targetDir, File sourceDir) throws IOException {
        for (String type : fileTypeList) {
            backupFolderByType(targetDir, sourceDir, type);
        }
    }

    /**
     * Backup files with specific type in a folder:
     * copy the latest file and create hard link for the rest
     */
    private void backupFolderByType(File targetDir, File sourceDir, final String type)
            throws IOException {
        File[] sourceFileList = sourceDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                log.debug("file name={}, type={}", name, type);
                return name.contains(type);
            }
        });
        if (sourceFileList == null || sourceFileList.length == 0) {
            log.debug("No file with type equals to {} in directory({})", type, sourceDir);
            return;
        }
        Arrays.sort(sourceFileList, new Comparator<File>() {
            public int compare(File f1, File f2) {
                return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
            }
        });
        boolean latest = true;
        for (File zkFile : sourceFileList) {
            log.debug("file name={}, time={}", zkFile.getName(), zkFile.lastModified());
            if (zkFile.isDirectory()) {
                continue;
            }
            File targetFile = new File(targetDir, zkFile.getName());
            if (latest) {
                FileUtils.copyFile(zkFile, targetFile);
                latest = false;
                continue;
            }
            createFileLink(targetFile.toPath(), zkFile.toPath());
        }
    }

    /**
     * Create file hard link
     */
    private void createFileLink(Path targetDir, Path sourceFile) {
        try {
            Files.createLink(targetDir, sourceFile);
            log.debug("The link({} to {}) was successfully created!",
                    sourceFile.toString(), targetDir.toString());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            throw BackupException.fatals.failedToCreateFileLink(
                    sourceFile.toString(), targetDir.toString(), e);
        }
    }

    @Override
    public boolean isNeed() {
        validateQuorumStatus();
        boolean ret = isEligibleForBackup();
        if (!ret) {
            log.info("Skip current zk instance during backup");
        }
        return ret;
    }

    @Override
    public String createBackup(final String backupTag) {
        String fullBackupTag = backupTag + BackupConstants.BACKUP_NAME_DELIMITER +
                backupType.name();
        checkBackupFileExist(backupTag, fullBackupTag);
        return fullBackupTag;
    }

    private void backupSiteId(File targetDir) throws IOException {
        FileUtils.copyFileToDirectory(siteIdFile, targetDir);
    }

    /**
     * Collect current site's vdc properties and output to a props file
     * @param folder
     */
    private void backupVdcProps(File folder) throws IOException {
        String[] cmds = {"/etc/systool", "--getvdcprops"};
        Exec.Result result = Exec.sudo(BackupConstants.SYSTOOL_TIMEOUT_MILLIS, cmds);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            log.error("Filed to get vdcprops via /etc/systool --getvdcprops");
            throw new IOException("Can't backup vdc properties for current site");
        }
        Writer writer = new PrintWriter(new FileOutputStream(new File(folder, BackupConstants.VDC_PROPS_FILE_NAME)));
        try {
            writer.write(result.getStdOutput());
        } finally {
            writer.close();
        }
    }

    @Override
    public File dumpBackup(final String backupTag, final String fullBackupTag) {
        File targetDir = new File(backupContext.getBackupDir(), backupTag);
        File targetFolder = new File(targetDir, fullBackupTag);
        try {
            ValidationUtil.validateFile(zkDir, FileType.Dir,
                    NotExistEnum.NOT_EXSIT_ERROR);
            ValidationUtil.validateFile(targetDir, FileType.Dir,
                    NotExistEnum.NOT_EXSIT_CREATE);
            ValidationUtil.validateFile(targetFolder, FileType.Dir,
                    NotExistEnum.NOT_EXSIT_CREATE);
            backupFolder(targetFolder, zkDir);
            backupSiteId(targetFolder);
            backupVdcProps(targetFolder);
        } catch (IOException ex) {
            throw BackupException.fatals.failedToDumpZkData(fullBackupTag, ex);
        }
        if (!checkZkConditionAfterBackup()) {
            throw BackupException.retryables.leaderHasBeenChanged();
        }
        log.info("ZK backup files have been moved to ({}) successfully", targetFolder.getAbsolutePath());
        return targetFolder;
    }
}
