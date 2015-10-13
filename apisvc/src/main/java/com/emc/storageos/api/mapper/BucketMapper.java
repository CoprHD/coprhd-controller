/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.object.BucketRestRep;

public class BucketMapper {

    public static BucketRestRep map(Bucket from) {
        if (from == null) {
            return null;
        }
        BucketRestRep to = new BucketRestRep();
        mapDataObjectFields(from, to);

        if (null != from.getProject()) {
            to.setProject(toRelatedResource(ResourceTypeEnum.PROJECT, from.getProject().getURI()));
        }
        if (null != from.getTenant()) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant().getURI()));
        }
        if (null != from.getRetention()) {
            to.setRetention(from.getRetention().toString());
        }
        to.setHardQuota(CapacityUtils.convertBytesToGBInStr(from.getHardQuota()));
        to.setSoftQuota(CapacityUtils.convertBytesToGBInStr(from.getSoftQuota()));
        to.setVirtualPool(toRelatedResource(ResourceTypeEnum.OBJECT_VPOOL, from.getVirtualPool()));
        to.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY, from.getVirtualArray()));
        to.setProtocols(from.getProtocol());
        to.setNamespace(from.getNamespace());
        to.setOwner(from.getOwner());
        to.setPath(from.getPath());

        to.setNativeId(from.getNativeId());
        return to;
    }
}
