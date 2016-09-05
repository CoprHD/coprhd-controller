/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.mapper;

import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;

public class StorageSystemTypeMapper {

    public static StorageSystemTypeAddParam map(StorageSystemType type, String tmpDriverPath) {
        StorageSystemTypeAddParam addParam = new StorageSystemTypeAddParam();
        addParam.setStorageTypeName(type.getStorageTypeName());
        addParam.setMetaType(type.getMetaType());
        addParam.setIsSmiProvider(type.getIsSmiProvider());
        addParam.setStorageTypeId(type.getStorageTypeId());
        addParam.setStorageTypeDispName(type.getStorageTypeDispName());
        addParam.setIsDefaultSsl(type.getIsDefaultSsl());
        addParam.setIsDefaultMDM(type.getIsDefaultMDM());
        addParam.setIsOnlyMDM(type.getIsOnlyMDM());
        addParam.setIsElementMgr(type.getIsElementMgr());
        addParam.setNonSslPort(type.getNonSslPort());
        addParam.setSslPort(type.getSslPort());
        addParam.setDriverClassName(type.getDriverClassName());
        addParam.setIsSecretKey(type.getIsSecretKey());
        addParam.setDriverFilePath(tmpDriverPath);
        return addParam;
    }
}
