/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.util.VPlexUtil;

/**
 * 
 */
public class VPlexBlockSnapshotSessionApiImpl extends AbstractBlockSnapshotSessionApiImpl {
    
    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private VPlexBlockSnapshotSessionApiImpl() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     */
    public VPlexBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator) {
        super(dbClient, coordinator);
    }
    
    /**
     * {@inheritDoc}
     * 
     * TBD Reconcile with the fact that both side could be snapped in Darth
     * 
     * TBD Reconcile with implementation in VPlexBlockServiceApiImpl
     */
    @Override
    protected Integer getNumNativeSnapshots(Volume vplexVolume){
        Volume snapshotSourceVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
        return super.getNumNativeSnapshots(snapshotSourceVolume);
    }
    
    /**
     * {@inheritDoc}
     * 
     * TBD Reconcile with the fact that both side could be snapped in Darth
     * 
     * TBD Reconcile with implementation in VPlexBlockServiceApiImpl
     */
    @Override
    protected void checkForDuplicatSnapshotName(String name, Volume vplexVolume) {
        Volume snapshotSourceVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
        super.checkForDuplicatSnapshotName(name, snapshotSourceVolume);
    }

}
