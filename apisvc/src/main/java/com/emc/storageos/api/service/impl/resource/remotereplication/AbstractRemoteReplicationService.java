/* Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.remotereplication;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Abstract resource class for holding generic methods of remote replication
 * category.
 */
public abstract class AbstractRemoteReplicationService extends TaskResourceService {

    private static final Logger log = LoggerFactory.getLogger(AbstractRemoteReplicationService.class);

    /**
     * Assume a consistency group is empty if it has no underlying storage
     * system
     */
    protected boolean isConsistencyGroupEmpty(BlockConsistencyGroup group) {
        return group.getStorageController() == null;
    }

    /**
     * Validate URI and find consistency group by URI
     */
    protected BlockConsistencyGroup findConsistencyGroupById(URI uri) {
        ArgValidator.checkUri(uri);
        ArgValidator.checkFieldUriType(uri, BlockConsistencyGroup.class, "id");
        BlockConsistencyGroup cGroup = _dbClient.queryObject(BlockConsistencyGroup.class, uri);
        if (cGroup == null) {
            throw APIException.badRequests.invalidConsistencyGroup();
        }
        return cGroup;
    }

    protected boolean isConsistencyGroupSupportRemoteReplication(BlockConsistencyGroup cGroup) {
        return cGroup.checkForRequestedType(Types.RR);
    }

    /**
     * Find all consistency groups by given alternateLabel, then return storage
     * systems of these CGs excluding the one specified by exclude parameter.
     */
    protected Set<String> findAllRRConsistencyGrroupSystemsByLabel(String label, BlockConsistencyGroup exclude) {
        DataObjectType doType = TypeMap.getDoType(BlockConsistencyGroup.class);
        AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField("alternateLabel"),
                label);
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(constraint, uris);
        Set<String> systems = new HashSet<>();
        for (URI uri : uris) {
            if (uri.equals(exclude.getStorageController())) {
                continue;
            }
            BlockConsistencyGroup group = _dbClient.queryObject(BlockConsistencyGroup.class, uri);
            if (isConsistencyGroupSupportRemoteReplication(group)) {
                systems.add(group.getStorageController().toString());
            }
        }
        return systems;
    }

    protected Iterator<RemoteReplicationSet> findAllRemoteRepliationSetsIteratively() {
        List<URI> ids = _dbClient.queryByType(RemoteReplicationSet.class, true);
        log.info("Found sets: {}", ids);
        return _dbClient.queryIterativeObjects(RemoteReplicationSet.class, ids);
    }

    protected Iterator<RemoteReplicationGroup> findAllRemoteRepliationGroupsIteratively() {
        List<URI> ids = _dbClient.queryByType(RemoteReplicationGroup.class, true);
        log.info("Found groups: {}", ids);
        return _dbClient.queryIterativeObjects(RemoteReplicationGroup.class, ids);
    }

    /**
     * @return true if cGroup's _storageController is of rrSet's
     *         storageSystemType, otherwise return false
     */
    protected boolean haveSameStorageSystemType(RemoteReplicationSet rrSet, BlockConsistencyGroup cGroup) {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, cGroup.getStorageController());
        return StringUtils.equals(system.getSystemType(), rrSet.getStorageSystemType());
    }

    /**
     * @return true if cGroup's _storageController is of rrGroup's
     *         storageSystemType, otherwise return false
     */
    protected boolean haveSameStorageSystemType(RemoteReplicationGroup rrGroup, BlockConsistencyGroup cGroup) {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, cGroup.getStorageController());
        return StringUtils.equals(system.getSystemType(), rrGroup.getStorageSystemType());
    }
}
