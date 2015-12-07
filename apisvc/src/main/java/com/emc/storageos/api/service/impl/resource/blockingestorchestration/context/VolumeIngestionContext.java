/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context;

import java.util.List;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * This interface represent the ingestion context for a
 * single UnManagedVolume being processed.
 */
public interface VolumeIngestionContext {

    /**
     * Return the UnManagedVolume Object for this VolumeIngestionContext.
     * 
     * @return the UnManagedVolume Object for this VolumeIngestionContext
     */
    public UnManagedVolume getUnmanagedVolume();

    /**
     * Returns true if this VolumeIngestionContext's UnManagedVolume is exported
     * to a Host, Cluster, or set of Initiators.
     * 
     * @return true if the UnManagedVolume is exported
     */
    public boolean isVolumeExported();

    /**
     * A list of Strings for any error messages related to
     * the processing of this UnManagedVolume, used to assemble
     * an error message returned in the task status for this UnManagedVolume.
     * 
     * @return a List of error message Strings
     */
    public List<String> getErrorMessages();

    /**
     * Commits any changes to the database related to the
     * processing of this VolumeIngestionContext's UnManagedVolume.
     */
    public void commit();

    /**
     * Rolls back any changes to the database related to the
     * processing of this VolumeIngestionContext's UnManagedVolume.
     */
    public void rollback();

}
