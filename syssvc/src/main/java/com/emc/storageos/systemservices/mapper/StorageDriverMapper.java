/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.mapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.coordinator.client.model.StorageDriverMetaData;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagedriver.StorageDriverRestRep;

public final class StorageDriverMapper {

    private StorageDriverMapper() {}

    /**
     * @return supported StorageSystemTypes by this driver. The first is the
     *         storage system, and the second is storage provider if there is
     */
    public static List<StorageSystemType> map(StorageDriverMetaData driver) {
        List<StorageSystemType> types = new ArrayList<StorageSystemType>();
        StorageSystemType type = new StorageSystemType();
        type.setStorageTypeName(driver.getStorageName());
        type.setStorageTypeDispName(driver.getStorageDisplayName());
        type.setDriverName(driver.getDriverName());
        type.setDriverVersion(driver.getDriverVersion());
        type.setDriverFileName(driver.getDriverFileName());
        type.setMetaType(driver.getMetaType());
        URI uri = URIUtil.createId(StorageSystemType.class);
        type.setId(uri);
        type.setStorageTypeId(uri.toString());
        type.setIsDefaultSsl(driver.isEnableSsl());
        type.setSslPort(Long.toString(driver.getSslPort()));
        type.setNonSslPort(Long.toString(driver.getNonSslPort()));
        type.setSupportAutoTierPolicy(driver.isSupportAutoTierPolicy());
        type.setDriverClassName(driver.getDriverClassName());
        types.add(type);

        if (StringUtils.isNotEmpty(driver.getProviderName())
                && StringUtils.isNotEmpty(driver.getProviderDisplayName())) {
            StorageSystemType provider = new StorageSystemType();
            provider.setStorageTypeName(driver.getProviderName());
            provider.setStorageTypeDispName(driver.getProviderDisplayName());
            provider.setIsSmiProvider(true);
            provider.setDriverName(driver.getDriverName());
            provider.setDriverVersion(driver.getDriverVersion());
            provider.setDriverFileName(driver.getDriverFileName());
            provider.setMetaType(driver.getMetaType());
            uri = URIUtil.createId(StorageSystemType.class);
            provider.setId(uri);
            provider.setStorageTypeId(uri.toString());
            provider.setIsDefaultSsl(driver.isEnableSsl());
            provider.setSslPort(Long.toString(driver.getSslPort()));
            provider.setNonSslPort(Long.toString(driver.getNonSslPort()));
            provider.setSupportAutoTierPolicy(driver.isSupportAutoTierPolicy());
            provider.setDriverClassName(driver.getDriverClassName());
            type.setManagedBy(provider.getStorageTypeId());
            types.add(provider);
        }
        return types;
    }
    
    public static StorageDriverRestRep map(StorageSystemType type) {
        StorageDriverRestRep rep = new StorageDriverRestRep();
        rep.setDriverName(type.getDriverName());
        rep.setDriverVersion(type.getDriverVersion());
        rep.setDriverFileName(type.getDriverFileName());
        rep.setDriverStatus(type.getDriverStatus());
        rep.setDriverClassName(type.getDriverClassName());
        rep.setMetaType(type.getMetaType());
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
