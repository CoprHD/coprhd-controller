/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.MigrationRestRep;

/**
 * Mapper class for mapping Migration instances to their corresponding REST response.
 */
public class BlockMigrationMapper {

    /**
     * Maps the passed migration instance to its corresponding REST response.
     * 
     * @param from A reference to a migration instance.
     * 
     * @return The response instance.
     */
    public static MigrationRestRep map(final Migration from) {
        if (from == null) {
            return null;
        }
        MigrationRestRep to = new MigrationRestRep();
        DbObjectMapper.mapDataObjectFields(from, to);
        if (!NullColumnValueGetter.isNullURI(from.getVolume())) {
            to.setVolume(toRelatedResource(ResourceTypeEnum.VOLUME, from.getVolume()));
            to.setSource(toRelatedResource(ResourceTypeEnum.VOLUME, from.getSource()));
            to.setTarget(toRelatedResource(ResourceTypeEnum.VOLUME, from.getTarget()));
        } else if (!NullColumnValueGetter.isNullURI(from.getConsistencyGroup())) {
            to.setConsistencyGroup(toRelatedResource(ResourceTypeEnum.BLOCK_CONSISTENCY_GROUP, from.getConsistencyGroup()));
            to.setSource(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getSource()));
            to.setTarget(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, from.getTarget()));
        }
        to.setStartTime(from.getStartTime());
        to.setPercentageDone(from.getPercentDone());
        to.setStatus(from.getMigrationStatus());
        return to;
    }
}
