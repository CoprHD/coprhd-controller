/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.google.common.base.Joiner;

/**
 * This callback function will be invoked from within in the SmisCommandHelper
 * .callRefreshSystem method. Reason: we need to run this after the refresh is complete
 * to update any *other* snapshots or volumes are known to also require a refresh. The
 * EMCRefresh call updates the SMI-S provider's database. Presumably,
 * all changes on the array will be all sync'ed after the call,
 * so we will need to reflect that against any other snapshots/volumes that required it,
 * but may not have yet invoked an operation that required the refresh. Essentially,
 * we preemptively updating these because we know they should be in-sync now.
 */
public class RefreshRequiredUpdateFunction implements SimpleFunction {
    private static final Logger _log =
            LoggerFactory.getLogger(RefreshRequiredUpdateFunction.class);
    public static final String REFRESH_REQUIRED = "refreshRequired";
    public static final String STORAGE_DEVICE = "storageDevice";

    private URI storageURI;
    private List<URI> objsRequiringRefresh;
    private DbClient dbClient;

    public RefreshRequiredUpdateFunction(URI storageURI, List<URI> originalList,
            DbClient dbClient) {
        this.storageURI = storageURI;
        this.objsRequiringRefresh = originalList;
        this.dbClient = dbClient;
    }

    @Override
    public void call() {
        _log.info(String.format("Original list of uris requiring EMCRefresh:%n%s",
                Joiner.on(',').join(objsRequiringRefresh)));
        handleBlockObjects(Volume.class);
        handleBlockObjects(BlockSnapshot.class);
    }

    /**
     * Refresh block objects of given type
     * 
     * @param clazz CF subclass of BlockObject
     */
    private <T extends BlockObject> void handleBlockObjects(Class<T> clazz) {
        // get all object URIs contained by the StorageSystem
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getContainedObjectsConstraint(storageURI,
                clazz, STORAGE_DEVICE), queryResults);

        Iterator<URI> iQueryResults = queryResults.iterator();
        List<URI> blockObjectURIs = new ArrayList<URI>();
        while (iQueryResults.hasNext()) {
            blockObjectURIs.add(iQueryResults.next());
        }

        // merge with objsRequiringRefresh
        for (URI uri : objsRequiringRefresh) {
            if (URIUtil.isType(uri, clazz)) {
                // uri could have already been in blockObjectURIs
                // queryIterativeObjectField won't return duplicate objects even if there are duplicate URIs in the list
                blockObjectURIs.add(uri);
            }
        }

        // query all objects, only need the REFRESH_REQUIRED field
        Iterator<T> iBlockObjects = dbClient.queryIterativeObjectField(clazz, REFRESH_REQUIRED, blockObjectURIs);
        List<T> objsNeedRefresh = new ArrayList<T>();
        // loop all the objects, check if the refreshRequired is true.
        while (iBlockObjects.hasNext()) {
            T blockObject = iBlockObjects.next();
            if (blockObject.getRefreshRequired()) {
                blockObject.setRefreshRequired(false);
                objsNeedRefresh.add(blockObject);
            }
        }

        dbClient.persistObject(objsNeedRefresh);
    }
}
