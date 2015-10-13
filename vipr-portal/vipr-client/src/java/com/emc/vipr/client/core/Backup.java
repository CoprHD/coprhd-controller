package com.emc.vipr.client.core;

import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.sys.backup.BackupSets;

public class Backup {
	 protected final RestClient client;
	
	public Backup(RestClient client) {
        this.client = client;
    }

	 public BackupSets getBackups() {
		 return client.get(BackupSets.class, "/backupset/", "");
	}
}
