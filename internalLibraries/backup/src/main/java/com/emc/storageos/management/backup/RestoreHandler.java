/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.management.backup;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.management.backup.util.ZipUtil;
import com.emc.storageos.services.util.Exec;
import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreHandler {

    private static final Logger log = LoggerFactory.getLogger(RestoreHandler.class);
    public static final String VIPR_USER = "storageos";
    public static final String VIPR_GROUP = "storageos";

    private File rootDir;
    private File viprDataDir;
    private List<String> extraCleanDirs = new ArrayList<>();
    private File backupArchive;

    public RestoreHandler(String rootDir, String viprDataDir) {
        Preconditions.checkArgument(rootDir != null && viprDataDir != null,
                "ViPR data directory is not configured");
        this.rootDir = new File(rootDir);
        this.viprDataDir = new File(viprDataDir);
    }

    RestoreHandler() {}

    /**
     * Sets root directory of ViPR db/zk
     * @param rootDir
     *          The path of ViPR db/zk root directory
     */
    void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    /**
     * Sets ViPR service data directory
     * @param viprDataDir
     *          The directory which saves ViPR service data
     */
    void setViprDataDir(File viprDataDir) {
        this.viprDataDir = viprDataDir;
    }

    /**
     * Sets extra directories which should be clean before restore
     * @param extraCleanDirs
     *          The extra clean directory list
     */
    public void setExtraCleanDirs(List<String> extraCleanDirs) {
        if (extraCleanDirs != null)
            this.extraCleanDirs = extraCleanDirs;
    }

    /**
     * Sets backup compress package
     * @param backupArchive
     *          The backup package
     */
    public void setBackupArchive(File backupArchive) {
        this.backupArchive = backupArchive;
    }

    /**
     * Purges ViPR data files before restore.
     */ 
    public void purge() throws IOException {
        if (!viprDataDir.getParentFile().exists())
            throw new FileNotFoundException(String.format(
                    "%s is not exist, please initialize ViPR first", viprDataDir.getParent()));
        log.info("\tDelete: {}", viprDataDir.getAbsolutePath());
        FileUtils.deleteDirectory(viprDataDir);
        for (String fileName : extraCleanDirs) {
            log.info("\tDelete: {}", fileName);
            File file = new File(fileName);
            if (file.exists())
                FileUtils.forceDelete(file);
        }
    }

    /**
     * Uncompresses backup file into vipr data directory.
     */
    public void replace() throws IOException {
        replace(false);
    }

    /**
     * Uncompresses backup file into vipr data directory.
     */ 
    public void replace(final boolean geoRestoreFromScratch) throws IOException {
        String backupName = backupArchive.getName().substring(0,
                backupArchive.getName().lastIndexOf('.'));
        // Check reinit flag for multi vdc env
        checkReinit(backupName, geoRestoreFromScratch);
        final File tmpDir = new File(viprDataDir.getParentFile(), backupName);
        log.debug("Temporary backup folder: {}", tmpDir.getAbsolutePath());
        try {
            ZipUtil.unpack(backupArchive, viprDataDir.getParentFile());
            tmpDir.renameTo(viprDataDir);
            String[] cmdArray = {"/bin/chown", "-R", 
                    VIPR_USER+":"+VIPR_GROUP, viprDataDir.getAbsolutePath()};
            Exec.Result result = Exec.sudo(BackupConstants.CMD_TIMEOUT, cmdArray);
            if (result.execFailed() || result.getExitValue() != 0)
                throw new IllegalStateException(String.format(
                       "Execute command failed: %s", result));
        } finally {
            if (tmpDir.exists())
                FileUtils.deleteQuietly(tmpDir);
        }
    }

    /**
     * Checks reinit flag for (geo)db to pull data from remote vdc/nodes
     * @param backupName
     *          The name of backup file
     * @param geoRestoreFromScratch
     *          True if restore geodb from scratch, or else if false
     * @throws IOException
     */
    private void checkReinit(final String backupName, final boolean geoRestoreFromScratch) throws IOException {
        // Add reinit file for multi vdc geodb synchronization
        String backupType = backupName.split(BackupConstants.BACKUP_NAME_DELIMITER)[1];
        if (BackupType.geodbmultivdc.name().equalsIgnoreCase(backupType)) {
            log.info("This backup was taken in multi vdc scenario");
            boolean needReinit = geoRestoreFromScratch ? false : true;
            checkReinitFile(needReinit);
        }
    }

    /**
     * Checks reinit file according to argument needReinit
     * @param needReinit
     *          Need to add reinit marker or not
     * @throws IOException
     */
    public void checkReinitFile(final boolean needReinit) throws IOException {
        File bootModeFile = new File(rootDir, Constants.STARTUPMODE);
        if (!needReinit) {
            log.info("Reinit flag is false");
            if (bootModeFile.exists())
                bootModeFile.delete();
            return;
        }
        if (!bootModeFile.exists()) {
            setDbStartupModeAsRestoreReinit(rootDir);
        }
        String[] cmdArray = {"/bin/chown", VIPR_USER+":"+VIPR_USER, bootModeFile.getAbsolutePath()};
        Exec.Result result = Exec.sudo(BackupConstants.CMD_TIMEOUT, cmdArray);
        if (result.execFailed() || result.getExitValue() != 0)
            throw new IllegalStateException(String.format("Execute command failed: %s", result));
        log.info("Startup mode file({}) has been created", bootModeFile.getAbsolutePath());
    }

    private void setDbStartupModeAsRestoreReinit(File dir) throws IOException {
        File bootModeFile = new File(dir, Constants.STARTUPMODE);
        try (OutputStream fos = new FileOutputStream(bootModeFile)) {
            Properties properties = new Properties();
            properties.setProperty(Constants.STARTUPMODE, Constants.STARTUPMODE_RESTORE_REINIT);
            properties.store(fos, null);
            log.info("Set startup mode as restore reinit under {} successful", dir);
        }
    }

}
