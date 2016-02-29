/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import play.Logger;
import util.BackupUtils;
import util.datatable.DataTable;

import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.emc.vipr.model.sys.backup.BackupUploadStatus.Status;
import com.google.common.collect.Lists;

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
        addColumn("creationtime").setCssClass("time").setRenderFunction(
                "render.localDate");
        if (type == Type.LOCAL) {
            addColumn("size").setRenderFunction("render.backupSize");
        }
        addColumn("actionstatus").setSearchable(false).setRenderFunction(
                "render.uploadAndRestoreProgress");
        if (type == Type.LOCAL) {
            addColumn("action").setSearchable(false).setRenderFunction(
                    "render.uploadAndRestoreBtn");
        } else if (type == Type.REMOTE) {
            addColumn("action").setSearchable(false).setRenderFunction(
                    "render.restoreBtn");
        }
        sortAllExcept("action", "actionstatus");
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<Backup> fetch(Type type) {
        List<Backup> results = Lists.newArrayList();
        if (type == Type.LOCAL) {
            for (BackupSet backup : BackupUtils.getBackups()) {
                results.add(new Backup(backup));
            }
        } else if (type == Type.REMOTE) {
            try {
                for (String name : BackupUtils.getExternalBackups()) {
                    results.add(new Backup(name));
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
                    || status.equals(Status.FAILED.toString())) {
                action = backup.getName() + "_enable";
            } else {
                action = backup.getName() + "_disable";
            }
        }

        public Backup(String externalBackupName) {
            try {
                id = URLEncoder.encode(externalBackupName, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.error("Could not encode backup name");
            }
            name = externalBackupName;
            status = "LOADING"; // Async to get the detail backup info
        }
    }
}
