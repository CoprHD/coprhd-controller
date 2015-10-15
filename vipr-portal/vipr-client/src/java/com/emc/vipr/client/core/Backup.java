package com.emc.vipr.client.core;

import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;
import static com.emc.vipr.client.system.impl.PathConstants.CONTROL_POWER_OFF_CLUSTER_URL;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.sys.backup.BackupSets;

import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_LIST_URL;
import static com.emc.vipr.client.system.impl.PathConstants.BACKUP_CREATE_URL;

public class Backup {
	protected final RestClient client;

	public Backup(RestClient client) {
		this.client = client;
	}

	public BackupSets getBackups() {
		return client.get(BackupSets.class, BACKUP_LIST_URL, "");
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
}
