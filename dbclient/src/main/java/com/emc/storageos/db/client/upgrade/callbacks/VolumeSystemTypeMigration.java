package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class VolumeSystemTypeMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(VolumeSystemTypeMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();

        Map<URI, String> storageSystemTypeMap = new HashMap<URI, String>();
        URI nextId = null;
        List<URI> blockObjectUris;

        int pageSize = 100;
        int totalBlockObjectCount = 0;
        int blockObjectUpdatedCount = 0;

        while (true) {
            blockObjectUris = dbClient.queryByType(BlockObject.class, true, nextId, pageSize);

            if (blockObjectUris == null || blockObjectUris.isEmpty()) {
                break;
            }

            List<BlockObject> blockObjectsToUpdate = new ArrayList<BlockObject>();
            List<BlockObject> pageOfBlockObjects = dbClient.queryObject(BlockObject.class, blockObjectUris);
            logger.info("processing page of {} BlockObjects", pageOfBlockObjects.size());

            for (BlockObject blockObject : pageOfBlockObjects) {
                if (blockObject.getSystemType() == null || blockObject.getSystemType().isEmpty()) {
                    String systemType = null;
                    URI storageSystemUri = blockObject.getStorageController();
                    if (storageSystemTypeMap.containsKey(storageSystemUri)) {
                        systemType = storageSystemTypeMap.get(storageSystemUri);
                    } else {
                        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemUri);
                        if (storageSystem != null) {
                            systemType = storageSystem.checkIfVmax3() ? 
                                    DiscoveredDataObject.Type.vmax3.name() : storageSystem.getSystemType();
                            storageSystemTypeMap.put(storageSystemUri, systemType);
                            logger.info("adding storage system type {} for storage system URI {}", 
                                    systemType, storageSystemUri);
                        } else {
                            logger.warn("could not find storage system by URI {} for BlockObject {}",
                                    storageSystemUri, blockObject.forDisplay());
                        }
                    }
                    if (systemType != null) {
                        blockObject.setSystemType(systemType);
                        blockObjectsToUpdate.add(blockObject);
                        blockObjectUpdatedCount++;
                        logger.info("set storage system type to {} for BlockObject {}",
                                systemType, blockObject.forDisplay());
                    } else {
                        logger.warn("could not determine storage system type for BlockObject {}",
                                blockObject.forDisplay());
                    }
                }
            }

            logger.info("updating system type on {} BlockObjects", blockObjectsToUpdate.size());
            dbClient.updateObject(blockObjectsToUpdate);
            nextId = blockObjectUris.get(blockObjectUris.size() - 1);
            totalBlockObjectCount += blockObjectUris.size();
        }

        logger.info("Updated storage system type on {} of {} BlockObjects in the system",
                blockObjectUpdatedCount, totalBlockObjectCount);
    }

}
