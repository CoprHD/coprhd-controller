/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.apidiff.serializer;

import com.emc.storageos.apidiff.ServiceCatalogDiff;

import java.io.File;
import java.util.List;

/**
 * Abstract serialization class for API differences of all services
 */
public abstract class AbstractSerializer {

    protected final List<ServiceCatalogDiff> diffList;
    protected File file;

    public AbstractSerializer(List<ServiceCatalogDiff> diffList, File folder) {
        this.diffList = diffList;
        // Clean old files before new operations
        if (!folder.exists()) {
            folder.mkdirs();
        } else if (!folder.isDirectory()) {
            throw new IllegalArgumentException("Output directory is not invalid: " + folder.toString());
        }
    }

    /**
     * The absolute path of output file
     */
    public File getFile() {
        return file;
    }

    /**
     * Outputs differences to file with specific format
     */
    public abstract void output();

}
