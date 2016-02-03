/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;
import com.emc.vipr.model.sys.backup.ExternalBackupInfo;

/**
 * Utility for backup.
 */

public class BackupUtils {

    public static List<BackupSet> getBackups() {
        return BourneUtil.getSysClient().backup().getBackups().getBackupSets();
    }

    public static List<String> getExternalBackups() {
        return BourneUtil.getSysClient().backup().getExternalBackups().getBackups();
    }

    public static void createBackup(String name, boolean force) {
        BourneUtil.getSysClient().backup().createBackup(name, force);
    }

    public static void deleteBackup(String name) {
        BourneUtil.getSysClient().backup().deleteBackup(name);
    }

    public static void uploadBackup(String name) {
        BourneUtil.getSysClient().backup().uploadBackup(name);
    }

    public static BackupUploadStatus getUploadStatus(String name) {
        return BourneUtil.getSysClient().backup().uploadBackupStatus(name);
    }

    public static BackupSet getBackup(String name) {
        return BourneUtil.getSysClient().backup().getBackup(name);
    }

    public static ExternalBackupInfo getExternalBackup(String name) {
        return BourneUtil.getSysClient().backup().getExternalBackup(name);
    }

    public static void pullBackup(String name) {
        BourneUtil.getSysClient().backup().pullBackup(name);
    }

    public static void cancelPullBackup() {
        BourneUtil.getSysClient().backup().cancelPullBackup();
    }

    public static void restore(String name, String password, boolean isLocal, boolean isGeoFromScratch) {
        BourneUtil.getSysClient().backup().restore(name, password, isLocal, isGeoFromScratch);
    }

    public static BackupRestoreStatus getRestoreStatus(String name, boolean isLocal) {
        return BourneUtil.getSysClient().backup().restoreStatus(name, isLocal);
    }
}
