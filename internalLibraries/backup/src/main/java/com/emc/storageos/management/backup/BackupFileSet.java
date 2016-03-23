/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup;

import java.util.List;
import java.util.TreeSet;

public class BackupFileSet extends TreeSet<BackupFile> {
    int quorumSize;

    public BackupFileSet(int quorumSize) {
        this.quorumSize = quorumSize;
    }

    public BackupFileSet subsetOf(String tag, BackupType type, String node) {
        BackupFileSet set = new BackupFileSet(this.quorumSize);
        for (BackupFile file : this) {
            if (file.matches(tag, type, node)) {
                set.add(file);
            }
        }
        return set;
    }

    public void addAll(List<BackupSetInfo> infos, String node) {
        for (BackupSetInfo info : infos) {
            this.add(new BackupFile(info, node));
        }
    }

    public TreeSet<String> uniqueNodes() {
        TreeSet<String> uniqueNodes = new TreeSet<>();
        for (BackupFile file : this) {
            uniqueNodes.add(file.node);
        }

        return uniqueNodes;
    }

    public TreeSet<String> uniqueTags() {
        TreeSet<String> tagSet = new TreeSet<>();
        for (BackupFile file : this) {
            tagSet.add(file.tag);
        }
        return tagSet;
    }

    /**
     * Given a list of files for a tag, check if they can form a valid backup set
     */
    public boolean isValid() {
        if (size() == 0) {
            return false;
        }

        String backupTag = this.first().tag;

        BackupFileSet zkFiles = subsetOf(backupTag, BackupType.zk, null);
        BackupFileSet dbFiles = subsetOf(backupTag, BackupType.db, null);
        BackupFileSet geodbFiles = subsetOf(backupTag, BackupType.geodb, null);
        BackupFileSet geodbmultivdcFiles = subsetOf(backupTag, BackupType.geodbmultivdc, null);
        BackupFileSet infoFile = subsetOf(backupTag, BackupType.info, null);

        if (zkFiles.size() == 0 || dbFiles.size() < this.quorumSize || geodbFiles.size() < this.quorumSize
                && geodbmultivdcFiles.size() < this.quorumSize || infoFile.size() == 0) {
            return false;
        }

        return true;
    }
}