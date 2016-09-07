/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.mapper;

import java.net.URI;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;

public class StorageSystemTypeMapper {
    
    public static StorageSystemTypeRestRep map(StorageSystemType from) {
        if (from == null) {
            return null;
        }
        StorageSystemTypeRestRep to = new StorageSystemTypeRestRep();
        to.setStorageTypeName(from.getStorageTypeName());
        to.setMetaType(from.getMetaType());
        to.setIsSmiProvider(from.getIsSmiProvider());
        to.setStorageTypeId(from.getStorageTypeId());
        to.setStorageTypeDispName(from.getStorageTypeDispName());
        to.setIsDefaultSsl(from.getIsDefaultSsl());
        to.setIsDefaultMDM(from.getIsDefaultMDM());
        to.setIsOnlyMDM(from.getIsOnlyMDM());
        to.setIsElementMgr(from.getIsElementMgr());
        to.setNonSslPort(from.getNonSslPort());
        to.setSslPort(from.getSslPort());
        to.setDriverClassName(from.getDriverClassName());
        to.setIsSecretKey(from.getIsSecretKey());

        return to;
    }

    public static StorageSystemType map(StorageSystemTypeAddParam param) {
        StorageSystemType type = new StorageSystemType();

        URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
        type.setId(ssTyeUri);
        type.setStorageTypeId(ssTyeUri.toString());

        type.setStorageTypeName(param.getStorageTypeName());
        type.setMetaType(param.getMetaType());
        type.setDriverClassName(param.getDriverClassName());

        if (param.getStorageTypeDispName() != null) {
            type.setStorageTypeDispName(param.getStorageTypeDispName());
        }

        if (param.getNonSslPort() != null) {
            type.setNonSslPort(param.getNonSslPort());
        }

        if (param.getSslPort() != null) {
            type.setSslPort(param.getSslPort());
        }

        type.setIsSmiProvider(param.getIsSmiProvider());
        type.setIsDefaultSsl(param.getIsDefaultSsl());
        type.setIsDefaultMDM(param.getIsDefaultMDM());
        type.setIsOnlyMDM(param.getIsOnlyMDM());
        type.setIsElementMgr(param.getIsElementMgr());
        type.setIsSecretKey(param.getIsSecretKey());
        // Here set to installing, will set to completed when finished
        // No done yet
        type.setInstallStatus("Installing");
        return type;
    }

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
