/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.object.BucketRestRep;

public class BucketMapper {
    private static final Logger _log = LoggerFactory.getLogger(FileMapper.class);

    public static BucketRestRep map(Bucket from) {
        if (from == null) {
            return null;
        }
        BucketRestRep to = new BucketRestRep();
        mapDataObjectFields(from, to);

        if (from.getProject() != null) {
            to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));
        }
        if (from.getTenant() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant().getURI()));
        }
        to.setHardQuota(CapacityUtils.convertBytesToGBInStr(from.getHardQuota()));
        to.setSoftQuota(CapacityUtils.convertBytesToGBInStr(from.getSoftQuota()));
        to.setVirtualPool(toRelatedResource(ResourceTypeEnum.OBJECT_VPOOL, from.getVirtualPool()));
        to.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, from.getVirtualArray()));
        to.setProtocols(from.getProtocol());
        to.setNativeId(from.getNativeId());
        to.setStorageSystem(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getStorageDevice()));
        to.setPool(toRelatedResource(ResourceTypeEnum.STORAGE_POOL, from.getPool(), from.getStorageDevice()));
        return to;
    }
}
