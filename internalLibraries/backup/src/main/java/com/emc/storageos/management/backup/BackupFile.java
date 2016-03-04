/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup;

/**
 * A class holding extra info parsed from backup file names
 */
public class BackupFile implements Comparable<BackupFile> {
    public final BackupSetInfo info;
    public final String tag;
    public final BackupType type;
    public final String node;

    public BackupFile(BackupSetInfo info, String node) {
        this.info = info;
        this.node = node;

        String[] backupNameSegs =
                info.getName().split(BackupConstants.BACKUP_NAME_DELIMITER);
        if (backupNameSegs.length < 2) {
            throw new IllegalStateException("Invalid backup file name:"
                    + info.getName());
        }

        this.tag = backupNameSegs[0];

        if (backupNameSegs[1].startsWith(BackupType.zk.toString())) {
            this.type = BackupType.zk;
        } else if (backupNameSegs[1].startsWith(BackupType.db.toString())) {
            this.type = BackupType.db;
        } else if (backupNameSegs[1].startsWith(BackupType.geodbmultivdc.toString())) {
            this.type = BackupType.geodbmultivdc;
        } else if (backupNameSegs[1].startsWith(BackupType.geodb.toString())) {
            this.type = BackupType.geodb;
        } else if (backupNameSegs[1].startsWith(BackupType.info.toString())) {
            this.type = BackupType.info;
        } else {
            throw new IllegalStateException("Unknown type of backup file name:"
                    + info.getName());
        }
    }

    @Override
    public int hashCode() {
        return this.info.getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BackupFile &&
                ((BackupFile) obj).info.getName().equals(this.info.getName());
    }

    public boolean matches(String tag, BackupType type, String node) {
        if (tag != null && !tag.equals(this.tag)) {
            return false;
        }

        if (type != null && !type.equals(this.type)) {
            return false;
        }

        if (node != null && !node.equals(this.node)) {
            return false;
        }

        return true;
    }

    @Override
    public int compareTo(BackupFile o) {
        return this.info.getName().compareTo(o.info.getName());
    }
}