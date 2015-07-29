/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.zkutils;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import org.apache.zookeeper.server.persistence.FileTxnLog;

/**
 * Handle commands about Zookeeper Txn logs
 */
public class ZkTxnHandler {
    private static final Logger log = LoggerFactory.getLogger(ZkTxnHandler.class);

    private String dataPath = "/data/zk/";
    private String BACKUP_PREFIX = "backup.";
    private int VERSION = 2;
    private String version = "version-";
    private File dataDir = null;

    public ZkTxnHandler() {
    }

    /**
     * Get last valid logged txn id.
     */
    public long getLastValidZxid() {
        dataDir = new File(dataPath, version + VERSION);
        String sDataDir = dataDir.getAbsolutePath();

        FileTxnLog txnLog = new FileTxnLog(dataDir);

        // If there is corrupted txn before the last txn log file,
        // txnLog.getLastLoggedZxid() would incorrectly return the 1st txn of the last txn log file.
        // We could cover this ZK bug by moving the txnlog files (useless and already snapshoped) but the last.
        File[] files = txnLog.getLogFiles(dataDir.listFiles(), 0);
        if (files.length > 1) {
            for (int i = 0; i < files.length - 1; i++) {
                File targetFile = new File(sDataDir, BACKUP_PREFIX + files[i].getName());
                files[i].renameTo(targetFile);
            }
        }

        long lastValidZxid = txnLog.getLastLoggedZxid();

        String tmpstr = String.format("last valid logged zxid:%s(hex)",
                Long.toHexString(lastValidZxid));
        log.info(tmpstr);
        System.out.println(tmpstr);
        return lastValidZxid;
    }

    /**
     * Truncate to the specific txn.
     * 
     * @param zxid The id(hex) of the txn to be truncated to
     */
    public boolean truncateToZxid(String zxid) {
        long lastValidZxid = getLastValidZxid();
        long targetZxid = Long.parseLong(zxid, 16);
        if (targetZxid < lastValidZxid) {
            String errstr = String.format(
                    "It is not allowed to truncate to the txn %s(hex) which is prior to the last valid txn %s(hex)! It would lose data!",
                    Long.toHexString(targetZxid), Long.toHexString(lastValidZxid));
            log.error(errstr);
            System.out.println(errstr);
            return false;
        }

        try {
            dataDir = new File(dataPath, version + VERSION);
            FileTxnLog truncLog = new FileTxnLog(dataDir);
            truncLog.truncate(targetZxid);
            truncLog.close();
            System.out.println(String.format("Successfully truncated to zxid: %s(hex)",
                    Long.toHexString(targetZxid)));
        } catch (IOException e) {
            String errstr = String.format("Failed to truncated to zxid: %s(hex). Please check it manually.", Long.toHexString(targetZxid));
            log.error(errstr);
            System.out.println(errstr);
            return false;
        }
        return true;
    }

    /**
     * Truncate to the last valid logged txn.
     * 
     * @param zxid The id of the last valid txn.
     */
    public boolean truncateToZxid() {
        return truncateToZxid(Long.toHexString(getLastValidZxid()));
    }
}
