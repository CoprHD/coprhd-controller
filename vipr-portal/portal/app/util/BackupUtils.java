package util;

import java.util.List;

import com.emc.vipr.model.sys.backup.BackupSets.BackupSet;

public class BackupUtils {
	
	 public static List<BackupSet> getBackups() {
		 return BourneUtil.getSysClient().backup().getBackups().getBackupSets();
	    }
}
