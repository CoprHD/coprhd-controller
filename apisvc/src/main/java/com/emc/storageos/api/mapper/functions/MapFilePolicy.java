/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.FilePolicyMapper;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.google.common.base.Function;

public class MapFilePolicy implements Function<FilePolicy, FilePolicyRestRep> {
    public static final MapFilePolicy instance = new MapFilePolicy();

    public static MapFilePolicy getInstance() {
        return instance;
    }

    private MapFilePolicy() {
    }

    @Override
    public FilePolicyRestRep apply(FilePolicy resource) {
        return FilePolicyMapper.map(resource);
    }

    /**
     * Translate <code>FilePolicy</code> object to <code>FilePolicyRestRep</code>
     * 
     * @param vNas
     * @return
     */
    public FilePolicyRestRep toFilePolicyRestRep(FilePolicy filePolicy) {
        return apply(filePolicy);
    }

}
