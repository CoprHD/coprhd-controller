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

package com.emc.storageos.vnxe.models;

import java.util.List;

/*
 * Deduplicate settings
 */
public class DeduplicationParam {
    private DedupStateEnum state;
    /*list of path names to include in deduplication operations. Any directory below 
    *a specified path name that includes this setting will be excluded from deduplication.
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
