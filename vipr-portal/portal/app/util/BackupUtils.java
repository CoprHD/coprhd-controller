/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.List;
import java.util.Map;

import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;
import com.emc.vipr.model.sys.backup.BackupInfo;

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

    public static BackupInfo getBackupInfo(String name, boolean isLocal) {
        return BourneUtil.getSysClient().backup().getBackupInfo(name, isLocal);
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
        return BourneUtil.getSysClient().backup().getRestoreStatus(name, isLocal);
    }

    public static boolean isExternalServerConfigured() {
        Map<String, String> propInfo = ConfigPropertyUtils.getPropertiesFromCoordinator();
        String url = propInfo.get(ConfigProperty.BACKUP_EXTERNAL_URL);
        return !(url == null || (url.equals("")));
    }

    public static boolean isScheduledBackupEnabled() {
        Map<String, String> propInfo = ConfigPropertyUtils.getPropertiesFromCoordinator();
        String enable = propInfo.get(ConfigProperty.BACKUP_SCHEDULER_ENABLE);
        return (enable != null) && (enable.equals("true"));
    }
}
