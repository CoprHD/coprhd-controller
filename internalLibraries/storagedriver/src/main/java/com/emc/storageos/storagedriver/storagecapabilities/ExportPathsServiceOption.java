/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;


import com.emc.storageos.storagedriver.model.ServiceOption;

public class ExportPathsServiceOption extends ServiceOption {
    private int minPath;
    private int maxPath;

    public ExportPathsServiceOption(int minPath, int maxPath) {
        this.minPath = minPath;
        this.maxPath = maxPath;
    }

    public int getMaxPath() {
        return maxPath;
    }

    public int getMinPath() {
        return minPath;
    }
}
