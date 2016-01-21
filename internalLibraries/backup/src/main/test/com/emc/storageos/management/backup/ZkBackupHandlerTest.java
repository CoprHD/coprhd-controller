/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import org.junit.Test;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;

import com.emc.storageos.management.backup.util.ValidationUtil;
import com.emc.storageos.management.backup.util.ValidationUtil.*;

public class ZkBackupHandlerTest extends BackupTestBase {

    @Test
    public void testValidateQuorumStatus() throws IOException {
        zkBackupHandler.validateQuorumStatus();
    }

    @Test
    public void testCheckEligibleForBackup() throws IOException {
        Assert.assertTrue(zkBackupHandler.isEligibleForBackup());
    }

    @Test
    public void testCheckEpoch() {
        Assert.assertTrue(zkBackupHandler.checkEpochEqual());
    }

    @Test
    public void testBackupFolder() throws IOException {
        ValidationUtil.validateFile(zkBackupHandler.getZkDir(), FileType.Dir,
                NotExistEnum.NOT_EXSIT_CREATE);

        File targetDir = new File(backupManager.getBackupContext().getBackupDir(), "ut_test"
                + File.separator + "zk");
        ValidationUtil.validateFile(targetDir, FileType.Dir,
                NotExistEnum.NOT_EXSIT_CREATE);

        zkBackupHandler.backupFolder(targetDir, zkBackupHandler.getZkDir());
        Assert.assertTrue(targetDir.exists());
    }
}
