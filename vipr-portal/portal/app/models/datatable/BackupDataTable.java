/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import com.emc.vipr.model.sys.backup.BackupInfo;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus;

import play.Logger;
import util.BackupUtils;
import util.datatable.DataTable;

import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.emc.vipr.model.sys.backup.BackupUploadStatus.Status;
import com.google.common.collect.Lists;

import controllers.security.Security;

public class BackupDataTable extends DataTable {
    private static final int MINIMUM_PROGRESS = 10;

    public enum Type {
        LOCAL, REMOTE
    }

    public BackupDataTable() {
        this(Type.LOCAL);
    }

    public BackupDataTable(Type type) {
        addColumn("name");
        addColumn("sitename");
        addColumn("version");
        addColumn("size");
        addColumn("creationtime").setSearchable(false).setCssClass("time");
        addColumn("actionstatus").setSearchable(false).setRenderFunction(
                "render.uploadAndRestoreProgress");
        if (type == Type.LOCAL) {
            alterColumn("creationtime").setRenderFunction("render.localDate");
            alterColumn("size").setRenderFunction("render.backupSize");
			if (Security.isSystemAdmin() || Security.isRestrictedSystemAdmin()) {
				addColumn("action").setSearchable(false).setRenderFunction(
						"render.uploadAndRestoreBtn");
			}
        } else if (type == Type.REMOTE) {
            alterColumn("creationtime").setRenderFunction("render.externalLoading");
            alterColumn("sitename").setRenderFunction("render.externalLoading");
            alterColumn("version").setRenderFunction("render.externalLoading");
            alterColumn("size").setRenderFunction("render.externalLoading");
			if (Security.isSystemAdmin() || Security.isRestrictedSystemAdmin()) {
				addColumn("action").setSearchable(false).setRenderFunction(
						"render.restoreBtn");
			}
        }
        sortAllExcept("action", "actionstatus");
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<Backup> fetch(Type type) {
        List<Backup> results = Lists.newArrayList();
        if (type == Type.LOCAL) {
            for (BackupSet backupSet : BackupUtils.getBackups()) {
                Backup backup = new Backup(backupSet);
                BackupInfo backupInfo = BackupUtils.getBackupInfo(backupSet.getName(), true);
                backup.alterLocalBackupInfo(backupInfo);
                results.add(backup);
            }
        } else if (type == Type.REMOTE) {
            try {
                for (String name : BackupUtils.getExternalBackups()) {
                    results.add(new Backup(name, true));
                }
            } catch (Exception e) {
                //should trim the error message, otherwise datatable.js#getErrorMessage will fail to parse the response
                throw new RuntimeException(e.getMessage().trim());
            }
        }
        return results;
    }

    public static class Backup {
        public String name;
        public String version;
        public String sitename;
        public long creationtime;
        public long size;
        public String id;
        public String action;
        public String status;
        public String error;
        public Integer progress = 0;

        public Backup(BackupSet backup) {
            try {
                id = URLEncoder.encode(backup.getName(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.error("Could not encode backup name");
            }
            name = backup.getName();
            creationtime = backup.getCreateTime();
            size = backup.getSize();
            status = backup.getUploadStatus().getStatus().name();
            if (backup.getUploadStatus().getProgress() != null) {
                progress = Math.max(backup.getUploadStatus().getProgress(), MINIMUM_PROGRESS);
            }
            if (backup.getUploadStatus().getErrorCode() != null) {
                error = backup.getUploadStatus().getErrorCode().name();
            }
            if (status.equals(Status.FAILED.toString())) {
                progress = 100;
            }
            if (status.equals(Status.NOT_STARTED.toString())
                    || status.equals(Status.FAILED.toString())
                    || status.equals(Status.PENDING.toString())) {
                action = backup.getName() + "_enable";
            } else {
                action = backup.getName() + "_disable";
            }
        }

        public Backup(String externalBackupName, boolean isSettingLoadingStatus) {
            try {
                id = URLEncoder.encode(externalBackupName, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.error("Could not encode backup name");
            }
            name = externalBackupName;
            // Async to get the detail backup info, so mark loading first
            if (isSettingLoadingStatus) {
                status = "LOADING";
                creationtime = -1; // means Loading
            }
        }
        
        public void alterLocalBackupRestoreStatus(BackupRestoreStatus restoreStatus) {
            if (restoreStatus.getStatus() == BackupRestoreStatus.Status.RESTORE_FAILED
                    || restoreStatus.getStatus() == BackupRestoreStatus.Status.RESTORING) {
                this.status = restoreStatus.getStatus().name();
                if (restoreStatus.getStatus() == BackupRestoreStatus.Status.RESTORE_FAILED) {
                    this.error = restoreStatus.getDetails();
                }
            }
        }

        public void alterLocalBackupInfo(BackupInfo backupInfo) {
            this.version = backupInfo.getVersion();
            this.sitename = backupInfo.getSiteName();
            this.alterLocalBackupRestoreStatus(backupInfo.getRestoreStatus());
        }
    }
}
