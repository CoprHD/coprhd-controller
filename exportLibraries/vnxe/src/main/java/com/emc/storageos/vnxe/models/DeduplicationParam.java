/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

/*
 * Deduplicate settings
 */
public class DeduplicationParam {
    private DedupStateEnum state;
    /*
     * list of path names to include in deduplication operations. Any directory below
     * a specified path name that includes this setting will be excluded from deduplication.
     */
    private List<String> pathNameExcludeList;
    /*
     * list of filename extensions to be excluded from deduplication
     */
    private List<String> fileExtExcludeList;

    public DedupStateEnum getState() {
        return state;
    }

    public void setState(DedupStateEnum state) {
        this.state = state;
    }

    public List<String> getPathNameExcludeList() {
        return pathNameExcludeList;
    }

    public void setPathNameExcludeList(List<String> pathNameExcludeList) {
        this.pathNameExcludeList = pathNameExcludeList;
    }

    public List<String> getFileExtExcludeList() {
        return fileExtExcludeList;
    }

    public void setFileExtExcludeList(List<String> fileExtExcludeList) {
        this.fileExtExcludeList = fileExtExcludeList;
    }

    public static enum DedupStateEnum {
        ENABLED,
        SUSPEND,
        DISABLED;
    }
}
