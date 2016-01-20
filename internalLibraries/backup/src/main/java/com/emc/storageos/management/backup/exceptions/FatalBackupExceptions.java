/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an error condition
 * <p/>
 * Remember to add the English message associated to the method in FatalCoodinatorExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/ ErrorHandling#ErrorHandling-DevelopersGuide
 */
@MessageBundle
public interface FatalBackupExceptions {

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException invalidParameters(final String params);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToCreateFileLink(final String source,
            final String target, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToReadZkInfo(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToParseLeaderStatus(final String result);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToTakeDbSnapshot(final String backupTag,
            final String viprKeyspace, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToDumpDbSnapshot(final String backupTag,
            final String viprKeyspace, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToDumpZkData(final String backupTag, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException backupSizeExceedQuota(final String quota, final String size);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToCreateBackupFolder(final String backupDir);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException backupFileNotFound(final String backupTag);

    @DeclareServiceCode(ServiceCode.BACKUP_CREATE_EXSIT)
    public FatalBackupException backupFileAlreadyExist(final String backupTag);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToCompressBackupFolder(final String backupFolder,
            final String compressFile, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToComputeMd5(final String backupFile, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToDeleteBackupFile(final String backupFile,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_CONNECTION_FAILED)
    public FatalBackupException failedToGetHost(final String host, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_CREATE_FAILED)
    public FatalBackupException failedToCreateBackup(final String backupTag, final String hosts,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_DELETE_FAILED)
    public FatalBackupException failedToDeleteBackup(final String backupTag, final String hosts,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_LIST_FAILED)
    public FatalBackupException failedToListBackup(final String hosts, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_PURGE_FAILED)
    public FatalBackupException failedToPurgeViprData(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_RESTORE_FAILED)
    public FatalBackupException failedToRestoreBackup(final String backupTag,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_GET_LOCK_ERROR)
    public FatalBackupException failedToGetLock(final String lockName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_LOCK_OCCUPIED)
    public FatalBackupException unableToGetLock(final String lockName);

    @DeclareServiceCode(ServiceCode.BACKUP_LOCK_OCCUPIED)
    public FatalBackupException unableToGetRecoveryLock(final String lockName);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToSetQuota(final int quotaGb, final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToGetQuota(final Throwable cause);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_NOT_LEADER)
    public FatalBackupException noNeedBackup();

    @DeclareServiceCode(ServiceCode.BACKUP_DISABLED_AS_DISK_FULL)
    public FatalBackupException backupDisabledAsDiskFull(final int actualPercentage, final int maxPercentage);

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public FatalBackupException failedToGetValidDualInetAddress(final String message);
}
