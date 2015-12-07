package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public interface IngestionRequestContext extends Iterator<UnManagedVolume> {

    /**
     * @return the current UnManagedVolume via the current VolumeIngestionContext
     */
    public UnManagedVolume getCurrentUnmanagedVolume();

    /**
     * @return the currentUnManagedVolumeUri
     */
    public URI getCurrentUnManagedVolumeUri();

    /**
     * @return the current VolumeIngestionContext
     */
    public VolumeIngestionContext getVolumeContext();

    /**
     * 
     * @param unmanagedVolumeGuid
     * @return
     */
    public VolumeIngestionContext getVolumeContext(String unmanagedVolumeGuid);

    /**
     * @return the storageSystem
     */
    public StorageSystem getStorageSystem();

    /**
     * @return the vpool
     */
    public VirtualPool getVpool();

    /**
     * @return the virtualArray
     */
    public VirtualArray getVarray();

    /**
     * @return the project
     */
    public Project getProject();

    /**
     * @return the tenant
     */
    public TenantOrg getTenant();

    /**
     * @return the vplexIngestionMethod
     */
    public String getVplexIngestionMethod();

    /**
     * @return the systemMap
     */
    public Map<String, StorageSystem> getSystemMap();

    /**
     * @return the systemCache
     */
    public List<URI> getSystemCache();

    /**
     * @return the poolCache
     */
    public List<URI> getPoolCache();

    /**
     * @return the unManagedVolumesToBeDeleted
     */
    public List<UnManagedVolume> getUnManagedVolumesToBeDeleted();

    /**
     * @return the createdObjectMap
     */
    public Map<String, BlockObject> getCreatedObjectMap();

    /**
     * @return the updatedObjectMap
     */
    public Map<String, List<DataObject>> getUpdatedObjectMap();

    /**
     * @return the taskStatusMap
     */
    public Map<String, StringBuffer> getTaskStatusMap();

    /**
     * @return the processedUnManagedVolumeMap
     */
    public Map<String, VolumeIngestionContext> getProcessedUnManagedVolumeMap();

    /**
     * Returns the UnManagedVolume that has been processed for the given nativeGuid,
     * or null if none was found.
     * 
     * @param nativeGuid the UnManagedVolume to check
     * @return an UnManagedVolume
     */
    public UnManagedVolume getProcessedUnManagedVolume(String nativeGuid);

    /**
     * Returns the VolumeIngestionContext for the given nativeGuid,
     * or null if none was found in the processed UnManagedVolume Map.
     * 
     * @param nativeGuid the UnManagedVolume to check
     * @return a VolumeIngestionContext
     */
    public VolumeIngestionContext getProcessedVolumeContext(String nativeGuid);

    /**
     * @return the ingestedObjects
     */
    public List<BlockObject> getIngestedObjects();

    /**
     * @return the exportGroupCreated
     */
    public boolean isExportGroupCreated();

    /**
     * @param exportGroupCreated the exportGroupCreated to set
     */
    public void setExportGroupCreated(boolean exportGroupCreated);

    /**
     * @return the exportGroup
     */
    public ExportGroup getExportGroup();

    /**
     * @param exportGroup the exportGroup to set
     */
    public void setExportGroup(ExportGroup exportGroup);

    /**
     * @return the host
     */
    public URI getHost();

    /**
     * @param host the host to set
     */
    public void setHost(URI host);

    /**
     * @return the cluster
     */
    public URI getCluster();

    /**
     * @param cluster the cluster to set
     */
    public void setCluster(URI cluster);

    /**
     * @return the deviceInitiators
     */
    public List<Initiator> getDeviceInitiators();

    /**
     * @param deviceInitiators the deviceInitiators to set
     */
    public void setDeviceInitiators(List<Initiator> deviceInitiators);

    /**
     * 
     * @param unmanagedVolumeGuid
     * @return
     */
    public BlockObject getProcessedBlockObject(String unmanagedVolumeGuid);

}