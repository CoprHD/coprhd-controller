package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl;

import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CustomQueryUtility;

public class RPVolumeIngestionContext extends BlockVolumeIngestionContext {

    private static final Logger _logger = LoggerFactory.getLogger(RPVolumeIngestionContext.class);

    private Volume ingestedVolume;
    private UnManagedProtectionSet unManagedProtectionSet;
    private ProtectionSet ingestedProtectionSet;
    private BlockConsistencyGroup ingestedBlockConsistencyGroup;

    public RPVolumeIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient) {
        super(unManagedVolume, dbClient);
    }
    
    /**
     * @return the ingestedVolume
     */
    public Volume getIngestedVolume() {
        return ingestedVolume;
    }

    /**
     * @param ingestedVolume the ingestedVolume to set
     */
    public void setIngestedVolume(Volume ingestedVolume) {
        this.ingestedVolume = ingestedVolume;
    }

    /**
     * @return the unManagedProtectionSet
     */
    public UnManagedProtectionSet getUnManagedProtectionSet() {
        
        // Find the UnManagedProtectionSet associated with this unmanaged volume
        List<UnManagedProtectionSet> umpsets = 
                CustomQueryUtility.getUnManagedProtectionSetByUnManagedVolumeId(_dbClient, getUnmanagedVolume().getId().toString());
        Iterator<UnManagedProtectionSet> umpsetsItr = umpsets.iterator();
        if (!umpsetsItr.hasNext()) {
            _logger.error("Unable to find unmanaged protection set associated with volume: " + getUnmanagedVolume().getId());
            // caller will throw exception
            return null;
        }
        
        unManagedProtectionSet = umpsetsItr.next();

        return unManagedProtectionSet;
    }
    
    /**
     * @param ingestedProtectionSet the ingestedProtectionSet to set
     */
    public void setIngestedProtectionSet(ProtectionSet ingestedProtectionSet) {
        this.ingestedProtectionSet = ingestedProtectionSet;
    }

    /**
     * @param ingestedBlockConsistencyGroup the ingestedBlockConsistencyGroup to set
     */
    public void setIngestedBlockConsistencyGroup(BlockConsistencyGroup ingestedBlockConsistencyGroup) {
        this.ingestedBlockConsistencyGroup = ingestedBlockConsistencyGroup;
    }

    @Override
    public void commit() {
        // save everything to the database

        // dbClient.updateObject(getUpdatedObjectMap());
        
        // if everything ingested, mark umpset for deletion
        
        // etc etc
    }

    @Override
    public void rollback() {
        // remove / rollback any changes to the data objects that were actually
        
        // if exportGroupWasCreated, delete ExportGroup
        
        // etc etc
        
    }

}
