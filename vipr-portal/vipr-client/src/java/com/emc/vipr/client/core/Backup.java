/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_CREATE_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_INFO_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_EXTERNAL_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_PULL_CANCEL_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_PULL_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_UPLOAD_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_URL;
import static com.emc.vipr.client.system.impl.PathConstants.RESTORE_STATUS_URL;
import static com.emc.vipr.client.system.impl.PathConstants.RESTORE_URL;

import javax.ws.rs.core.UriBuilder;

import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import com.emc.vipr.model.sys.backup.BackupSets;
import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;
import com.emc.vipr.model.sys.backup.BackupInfo;
import com.emc.vipr.model.sys.backup.ExternalBackups;

public class Backup {
	protected final RestClient client;

	public Backup(RestClient client) {
		this.client = client;
	}

	public BackupSets getBackups() {
		return client.get(BackupSets.class, BACKUP_URL, "");
	}

	public BackupSet getBackup(String name) {
		UriBuilder builder = client.uriBuilder(BACKUP_URL+"backup");
		addQueryParam(builder, "tag", name);
		return client.getURI(BackupSet.class, builder.build());
	}

    public ExternalBackups getExternalBackups() {
        return client.get(ExternalBackups.class, BACKUP_EXTERNAL_URL);
    }

    public BackupInfo getBackupInfo(String name, boolean isLocal) {
        UriBuilder builder = client.uriBuilder(BACKUP_INFO_URL);
        addQueryParam(builder, "backupname", name);
        if (isLocal) {
            addQueryParam(builder, "isLocal", isLocal);
        }
        return client.getURI(BackupInfo.class, builder.build());
    }

    public void createBackup(String name, boolean force) {
        int specialTimeout = 30 * 60 * 1000; // 30 minutes
        client.getConfig().withReadTimeout(specialTimeout);
        client.getConfig().withConnectionTimeout(specialTimeout);
        UriBuilder builder = client.uriBuilder(BACKUP_CREATE_URL);
		addQueryParam(builder, "tag", name);
		if (force) {
			addQueryParam(builder, "force", true);
		}
		client.postURI(String.class, builder.build());
	}

	public void deleteBackup(String name) {
		UriBuilder builder = client.uriBuilder(BACKUP_CREATE_URL);
		addQueryParam(builder, "tag", name);
		client.deleteURI(String.class, builder.build());
	}

	public void uploadBackup(String name) {
		UriBuilder builder = client.uriBuilder(BACKUP_UPLOAD_URL);
		addQueryParam(builder, "tag", name);
		client.postURI(String.class, builder.build());
	}

	public BackupUploadStatus uploadBackupStatus(String name) {
		BackupUploadStatus status = null;
		UriBuilder builder = client.uriBuilder(BACKUP_UPLOAD_URL);
		addQueryParam(builder, "tag", name);

		try {
			status = client.getURI(BackupUploadStatus.class, builder.build());
		} catch (Exception e) {
			status = new BackupUploadStatus();
		}

		return status;
	}

    public void pullBackup(String name) {
        UriBuilder builder = client.uriBuilder(BACKUP_PULL_URL);
        addQueryParam(builder, "file", name);
        client.postURI(String.class, builder.build());
    }

    public void cancelPullBackup() {
        UriBuilder builder = client.uriBuilder(BACKUP_PULL_CANCEL_URL);
        client.postURI(String.class, builder.build());
    }

    public void restore(String name, String password, boolean isLocal, boolean isGeoFromScratch) {
        UriBuilder builder = client.uriBuilder(RESTORE_URL);
        addQueryParam(builder, "backupname", name);
        addQueryParam(builder, "password", password);
        if (isGeoFromScratch) {
            addQueryParam(builder, "isgeofromscratch", true);
        }
        if (isLocal) {
            addQueryParam(builder, "isLocal", true);
        }
        client.postURI(String.class, builder.build());
    }

    public BackupRestoreStatus getRestoreStatus(String name, boolean isLocal) {
        BackupRestoreStatus status = null;
        UriBuilder builder = client.uriBuilder(RESTORE_STATUS_URL);
        addQueryParam(builder, "backupname", name);
        addQueryParam(builder, "isLocal", isLocal);

        try {
            status = client.getURI(BackupRestoreStatus.class, builder.build());
        } catch (Exception e) {
            status = new BackupRestoreStatus();
            status.setStatus(BackupRestoreStatus.Status.DOWNLOAD_FAILED);
            status.setDetails(e.getMessage());
        }

        return status;
    }
}
