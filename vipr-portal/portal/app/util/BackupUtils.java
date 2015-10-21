/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.List;

import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;

/**
 * Utility for backup.
 * 
 * @author mridhr
 */

public class BackupUtils {
	
	 public static List<BackupSet> getBackups() {
		 return BourneUtil.getSysClient().backup().getBackups().getBackupSets();
	 }
	 
	 public static void createBackup(String name, boolean force){
		 BourneUtil.getSysClient().backup().createBackup(name,force);
	 }
	 
	 public static void deleteBackup(String name){
		 BourneUtil.getSysClient().backup().deleteBackup(name);
	 }
}
