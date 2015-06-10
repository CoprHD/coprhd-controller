/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.Migration;
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
        to.setVolume(DbObjectMapper.toRelatedResource(ResourceTypeEnum.VOLUME,
            from.getVolume()));
        to.setSource(DbObjectMapper.toRelatedResource(ResourceTypeEnum.VOLUME,
            from.getSource()));
        to.setTarget(DbObjectMapper.toRelatedResource(ResourceTypeEnum.VOLUME,
            from.getTarget()));
        to.setStartTime(from.getStartTime());
        to.setPercentageDone(from.getPercentDone());
        to.setStatus(from.getMigrationStatus());
        return to;
    }
}
