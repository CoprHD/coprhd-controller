package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class FileRPSchedular implements Scheduler {
	public final Logger _log = LoggerFactory
            .getLogger(FileRPSchedular.class);

    private DbClient _dbClient;
    private StorageScheduler _storageScheduler;
    private FileStorageScheduler _fileScheduler;
    
    public void setStorageScheduler(final StorageScheduler storageScheduler) {
        _storageScheduler = storageScheduler;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }
    
    public void setFileScheduler(FileStorageScheduler fileScheduler) {
        _fileScheduler = fileScheduler;
    }
    
    @Autowired
    protected PermissionsHelper _permissionsHelper = null;
    
	@Override
	public List getRecommendationsForResources(VirtualArray varray,
										Project project, VirtualPool vpool,
			VirtualPoolCapabilityValuesWrapper capabilities) {
		 // Get all storage pools that match the passed vpool params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        List<StoragePool> pools = _storageScheduler.getMatchingPools(varray, vpool, capabilities);

        if (pools == null || pools.isEmpty()) {
            _log.error(
                    "No matching storage pools found for the source varray: {0}. There are no storage pools that "
                            + "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to "
                            + "hold at least one resource of the requested size.",
                    varray.getLabel());
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getId(),
                    varray.getId());
        }
        List<StoragePool> candidatePools = new ArrayList();
        candidatePools.addAll(pools);
        
        

			
	        // Schedule storage based on the source pool constraint.
	    return scheduleStorageSourcePoolConstraint(varray, project, vpool, capabilities,
	                candidatePools);
	    
	}
	
	
    private List<Recommendation> scheduleStorageSourcePoolConstraint(final VirtualArray varray,
            final Project project, final VirtualPool vpool,
            final VirtualPoolCapabilityValuesWrapper capabilities,
            final List<StoragePool> candidatePools) {
        // Initialize a list of recommendations to be returned.
        List<Recommendation> recommendations = new ArrayList<Recommendation>();
        
        List<VirtualArray> targetVarrays = getTargetVirtualArraysForVirtualPool(project, vpool,
                _dbClient, _permissionsHelper);
        
        // Attempt to use these pools for selection based on target
        StringBuffer sb = new StringBuffer("Determining if SRDF is possible from " + varray.getId()
                + " to: ");
        for (VirtualArray targetVarray : targetVarrays) {
            sb.append(targetVarray.getId()).append(" ");
        }
        _log.info(sb.toString());

        Map<VirtualArray, List<StoragePool>> varrayPoolMap = getMatchingPools(targetVarrays, vpool,
                capabilities);
        if (varrayPoolMap == null || varrayPoolMap.isEmpty()) {
            // No matching storage pools found for any of the target varrays. There are no target
            // storage pools that match the passed vpool parameters and protocols and/or there are
            // no pools that have enough
            // capacity to hold at least one resource of the requested size.
            Set<String> tmpTargetVarrays = new HashSet<String>();
            sb = new StringBuffer(
                    "No matching storage pools found for any of the target varrays: [ ");

            for (VirtualArray targetVarray : targetVarrays) {
                sb.append(targetVarray.getId()).append(" ");
                tmpTargetVarrays.add(targetVarray.getId().toString());
            }

            sb.append("]. There are no storage pools that match the passed vpool parameters and protocols and/or "
                    + "there are no pools that have enough capacity to hold at least one resource of the requested size.");

            _log.error(sb.toString());
            throw APIException.badRequests.noMatchingRecoverPointStoragePoolsForVpoolAndVarrays(
                    vpool.getId(), tmpTargetVarrays);

        }

        
        
        return recommendations;
    }
	
	
	/**
     * Gets and verifies that the target varrays passed in the request are accessible to the tenant.
     * 
     * @param project
     *            A reference to the project.
     * @param vpool
     *            class of service, contains target varrays
     * @return A reference to the varrays
     * @throws java.net.URISyntaxException
     * @throws com.emc.storageos.db.exceptions.DatabaseException
     */
    static public List<VirtualArray> getTargetVirtualArraysForVirtualPool(final Project project,
            final VirtualPool vpool, final DbClient dbClient,
            final PermissionsHelper permissionHelper) {
        List<VirtualArray> targetVirtualArrays = new ArrayList<VirtualArray>();
        if (VirtualPool.getRemoteProtectionSettings(vpool, dbClient) != null) {
            for (URI targetVirtualArray : VirtualPool.getRemoteProtectionSettings(vpool, dbClient)
                    .keySet()) {
                VirtualArray nh = dbClient.queryObject(VirtualArray.class, targetVirtualArray);
                targetVirtualArrays.add(nh);
                permissionHelper.checkTenantHasAccessToVirtualArray(
                        project.getTenantOrg().getURI(), nh);
            }
        }
        return targetVirtualArrays;
    }
	
	/**
     * Gather matching pools for a collection of varrays
     * 
     * @param varrays
     *            The target varrays
     * @param vpool
     *            the requested vpool that must be satisfied by the storage pool
     * @param capabilities
     *            capabilities
     * @return A list of matching storage pools and varray mapping
     */
    private Map<VirtualArray, List<StoragePool>> getMatchingPools(final List<VirtualArray> varrays,
            final VirtualPool vpool, final VirtualPoolCapabilityValuesWrapper capabilities) {
        Map<VirtualArray, List<StoragePool>> varrayStoragePoolMap = new HashMap<VirtualArray, List<StoragePool>>();
        Map<URI, VpoolRemoteCopyProtectionSettings> settingsMap = VirtualPool
                .getRemoteProtectionSettings(vpool, _dbClient);

        for (VirtualArray varray : varrays) {
            // If there was no vpool specified with the target settings, use the base vpool for this
            // varray.
            VirtualPool targetVpool = vpool;
            VpoolRemoteCopyProtectionSettings settings = settingsMap.get(varray.getId());
            if (settings != null && settings.getVirtualPool() != null) {
                targetVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
            }
            capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, VirtualPoolCapabilityValuesWrapper.SRDF_TARGET);
            // Find a matching pool for the target vpool
            varrayStoragePoolMap.put(varray,
            		_storageScheduler.getMatchingPools(varray, targetVpool, capabilities));
        }

        return varrayStoragePoolMap;
    }
    
    
    /**
     * Prep work to call the orchestrator to create the volume descriptors
     * 
     * @param recommendation
     *            recommendation object from SRDFRecommendation
     * @param volumeURIs
     *            volumes already prepared
     * @param capabilities
     *            vpool capabilities
     * @return list of volume descriptors
     * @throws ControllerException
     */
    private List<FileDescriptor> createFileDescriptors1(final SRDFRecommendation recommendation,
            final List<URI> fileURIs, final VirtualPoolCapabilityValuesWrapper capabilities)
                    throws ControllerException {

        List<FileShare> preparedFileShares = _dbClient.queryObject(FileShare.class, fileURIs);

        List<FileDescriptor> descriptors = new ArrayList<FileDescriptor>();
//        // Package up the Volume descriptors
//        for (FileShare fileshare : preparedFileShares) {
//            FileDescriptor.Type fileType = FileDescriptor.Type.FILE_RP_SOURCE;
//
//            // CoS Change flow, mark the production volume as already existing, so it doesn't get
//            // created
//            if (recommendation.getVpoolChangeVolume() != null
//                    			&& recommendation.getVpoolChangeVolume().equals(fileshare.getId())) {
//            	fileType = FileDescriptor.Type.FILE_EXISTING_SOURCE;
//            	
//                FileDescriptor desc = new FileDescriptor(fileType, fileshare.getStorageDevice(), 
//                		fileshare.getId(), fileshare.getPool(), fileshare.getUsedCapacity(), capabilities, null, null);
//                
//                Map<String, Object> fileParams = new HashMap<String, Object>();
//                fileParams.put(FileDescriptor.PARAM_VPOOL_CHANGE_FILE_ID,
//                        recommendation.getVpoolChangeVolume());
//                volumeParams.put(FileDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID,
//                        recommendation.getVpoolChangeVpool());
//                volumeParams.put(FileDescriptor.PARAM_VPOOL_OLD_VPOOL_ID,
//                        volume.getVirtualPool());
//
//                desc.setParameters(volumeParams);
//                descriptors.add(desc);
//
//                _log.info("Adding Source Volume Descriptor for: " + desc.toString());
//            } else {
//                // Normal create-from-scratch flow
//            	
//                if (fileshare.getPersonality() == null) {
//                    throw APIException.badRequests.srdfVolumeMissingPersonalityAttribute(volume
//                            .getId());
//                }
//                if (fileshare.getPersonality().equals(fileshare.PersonalityTypes.TARGET.toString())) {
//                	fileType = FileDescriptor.Type.FILE_RP_TARGET;
//                }
//                FileDescriptor descriptor = new FileDescriptor(fileType, fileshare.getStorageDevice(), _fsURI, _poolURI, _fileSize, _capabilitiesValues, _migrationId, _suggestedNativeFsId)
//                FileDescriptor desc = new FileDescriptor(fileType,
//                        fileShare., volume.getId(), volume.getPool(), null,
//                        capabilities, volume.getCapacity());
//
//                descriptors.add(desc);
//
//                _log.info("Adding Non-Source Volume Descriptor for: " + desc.toString());
//            }
//        }

        return descriptors;
    }
    
   
    

}
