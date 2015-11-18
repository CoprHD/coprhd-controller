/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_CREATE_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_UPLOAD_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_URL;

import javax.ws.rs.core.UriBuilder;

import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.sys.backup.BackupSets;
import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;

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
	
	public void createBackup(String name, boolean force) {
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
}
