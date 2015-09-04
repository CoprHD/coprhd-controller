/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
    public void testCheckLeader() throws IOException {
        Assert.assertTrue(zkBackupHandler.isLeader());
    }

    @Test
    public void testCheckEpoch() {
        Assert.assertTrue(zkBackupHandler.checkEpochEqual());
    }

    @Test
    public void testBackupFolder() throws IOException {
        ValidationUtil.validateFile(zkBackupHandler.getZkDir(), FileType.Dir,
                NotExistEnum.NOT_EXSIT_CREATE);

        File targetDir = new File(backupManager.getBackupDir(), "ut_test"
                + File.separator + "zk");
        ValidationUtil.validateFile(targetDir, FileType.Dir,
                NotExistEnum.NOT_EXSIT_CREATE);

        zkBackupHandler.backupFolder(targetDir, zkBackupHandler.getZkDir());
        Assert.assertTrue(targetDir.exists());
    }
}
