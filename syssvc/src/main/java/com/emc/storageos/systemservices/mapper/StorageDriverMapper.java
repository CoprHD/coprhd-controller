/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.mapper;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.coordinator.client.model.StorageDriverMetaData;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagedriver.StorageDriverRestRep;
import com.emc.storageos.storagedriver.util.DriverMetadataUtil;
import com.sun.tools.javac.util.StringUtils;

public final class StorageDriverMapper {

    private StorageDriverMapper() {}

    public static List<StorageSystemType> map(StorageDriverMetaData driver) {
        return DriverMetadataUtil.map(driver);
    }
    
    public static StorageDriverRestRep map(StorageSystemType type) {
        StorageDriverRestRep rep = new StorageDriverRestRep();
        rep.setDriverName(type.getDriverName());
        rep.setDriverVersion(type.getDriverVersion());
        rep.setDriverFileName(type.getDriverFileName());
        rep.setDriverStatus(type.getDriverStatus());
        rep.setDriverClassName(type.getDriverClassName());
        rep.setMetaType(StringUtils.toUpperCase(type.getMetaType()));
        List<String> supportedTypes = new ArrayList<String>();
        supportedTypes.add(type.getStorageTypeDispName());
        rep.setSupportedTypes(supportedTypes);
        rep.setDefaultSslOn(type.getIsDefaultSsl());
        rep.setSslPort(type.getSslPort());
        rep.setNonSslPort(type.getNonSslPort());
        rep.setSupportAutoTierPolicy(type.getSupportAutoTierPolicy());
        return rep;
    }
}
