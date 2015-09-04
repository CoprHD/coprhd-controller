/*
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

import com.emc.storageos.management.backup.exceptions.BackupException;

import java.io.File;
import java.io.IOException;

public abstract class BackupHandler {

    protected BackupType backupType;

    /**
     * Gets backup data type
     */
    public BackupType getBackupType() {
        return backupType;
    }

    /**
     * Sets backup data type
     * 
     * @param backupType
     *            The type of backup data
     */
    public void setBackupType(BackupType backupType) {
        this.backupType = backupType;
    }

    /**
     * Checks that it is necessary to backup or not
     * 
     * @return execute backup if true, nothing to do else
     */
    public abstract boolean isNeed();

    /**
     * Creates local backup, such as DB snapshot, ZK snapshot & txn logs.
     * 
     * @param backupTag
     *            The name of this backup
     * @return full backup name
     */
    public abstract String createBackup(final String backupTag);

    /**
     * Moves backup files to specified location
     * 
     * @param backupTag
     *            The name of this backup
     * @param fullBackupTag
     *            The snapshot which will be dumped
     * @return The zip file to contain snapshot files
     */
    public abstract File dumpBackup(final String backupTag, final String fullBackupTag);

    /**
     * Check backup file is exist or not
     * 
     * @param backupTag
     *            The name of backup
     * @param fullBackupTag
     *            The name of the backup file
     */
    protected void checkBackupFileExist(final String backupTag, final String fullBackupTag) {
        File backupFolder = new File(BackupManager.backupDir, backupTag);
        if (!backupFolder.exists())
            return;
        File backupFile = new File(backupFolder, fullBackupTag + BackupConstants.COMPRESS_SUFFIX);
        if (backupFile.exists())
            throw BackupException.fatals.backupFileAlreadyExist(backupFile.getName());
    }
}
