/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to initialize RecoverPoint BlockConsistencyGroups. We need to create
 * a single BlockConsistencyGroup for each ProtectionSet in the DB.
 * 
 */
public class ProtectionSetToBlockConsistencyGroupMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(ProtectionSetToBlockConsistencyGroupMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        createRpBlockConsistencyGroups();
    }

    /**
     * Create RP BlockConsistencyGroup objects for each ProtectionSet.
     */
    private void createRpBlockConsistencyGroups() {
        DbClient dbClient = this.getDbClient();
        List<URI> protectionSetURIs = dbClient.queryByType(ProtectionSet.class, false);

        Iterator<ProtectionSet> protectionSets =
                dbClient.queryIterativeObjects(ProtectionSet.class, protectionSetURIs);

        while (protectionSets.hasNext()) {
            ProtectionSet ps = protectionSets.next();
            Project project = dbClient.queryObject(Project.class, ps.getProject());

            BlockConsistencyGroup cg = new BlockConsistencyGroup();
            cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
            cg.setLabel(ps.getLabel());
            cg.setDeviceName(ps.getLabel());
            cg.setType(BlockConsistencyGroup.Types.RP.toString());
            cg.setProject(new NamedURI(project.getId(), ps.getLabel()));
            cg.setTenant(new NamedURI(project.getTenantOrg().getURI(), ps.getLabel()));

            dbClient.createObject(cg);

            log.debug("Created ConsistencyGroup (id={}) based on ProtectionSet (id={})",
                    cg.getId().toString(), ps.getId().toString());

            // Organize the volumes by replication set
            for (String protectionVolumeID : ps.getVolumes()) {
                URI uri = URI.create(protectionVolumeID);
                Volume protectionVolume = dbClient.queryObject(Volume.class, uri);
                protectionVolume.addConsistencyGroup(cg.getId().toString());

                dbClient.persistObject(protectionVolume);

                log.debug("Volume (id={}) added to ConsistencyGroup (id={})",
                        protectionVolume.getId().toString(), cg.getId().toString());
            }
        }
    }
}
