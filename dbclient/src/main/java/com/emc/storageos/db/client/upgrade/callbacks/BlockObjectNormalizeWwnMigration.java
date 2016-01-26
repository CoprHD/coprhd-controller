/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class BlockObjectNormalizeWwnMigration extends BaseCustomMigrationCallback {

    public static final Long FLAG_DEFAULT = 2L;
    private static final Logger log = LoggerFactory.getLogger(BlockObjectNormalizeWwnMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        processType(Volume.class);
        processType(BlockSnapshot.class);
        processType(BlockMirror.class);
    }

    private <T extends BlockObject> void processType(Class<T> clazz) {
        DbClient dbClient = getDbClient();
        List<URI> blockObjectKeys = dbClient.queryByType(clazz, true);

        for (URI blockObjectKey : blockObjectKeys) {
            List<T> blockObjects = dbClient.queryObjectField(clazz, "wwn", Arrays.asList(blockObjectKey));
            for (BlockObject blockObject : blockObjects) {
                String currentWwn = blockObject.getWWN();
                if (currentWwn != null && !NumberUtils.isDigits(currentWwn)) {
                    // BlockObject#setWWN has been updated to upper case the WWN
                    blockObject.setWWN(currentWwn);
                    log.info("Normalizing WWN of " + blockObject.getId() +
                            " from " + currentWwn + " to " + blockObject.getWWN());
                    dbClient.persistObject(blockObject);
                }
            }
        }
    }
}
