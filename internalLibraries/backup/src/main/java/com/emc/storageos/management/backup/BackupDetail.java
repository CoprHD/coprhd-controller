/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup;

import java.util.ArrayList;
import java.util.List;

public class BackupDetail {
    private String name = "";
    private boolean isVersionValid = false;
    private boolean isGeo = false;
    private List<String> backupFiles = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVersionValid() {
        return isVersionValid;
    }

    public void setVersionValid(boolean isVersionValid) {
        this.isVersionValid = isVersionValid;
    }

    public boolean isGeo() {
        return isGeo;
    }

    public void setGeo(boolean geo) {
        isGeo = geo;
    }

    public List<String> getBackupFiles() {
        return backupFiles;
    }

    public void setBackupFiles(List<String> backupFiles) {
        this.backupFiles = backupFiles;
    }

    @Override
    public String toString() {
        String files = String.join(" ", backupFiles);
        return "name=" + name
                +"\nversion_checked=" + isVersionValid
                + "\ngeo=" + isGeo
                + "\nfiles=" + files
                +"\n";
    }
}
