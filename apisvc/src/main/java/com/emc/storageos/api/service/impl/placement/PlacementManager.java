/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolBucket;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class PlacementManager {

    // Enumeration specifying the valid scheduler keys.
    public enum SchedulerType {
        block, rp, srdf, vplex, rpvplex
    }
    
    public enum Schedulers {
        srdf,vplex,block
    }

    private DbClient dbClient;
    // Storage schedulers
    private Map<String, Scheduler> storageSchedulers;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setStorageSchedulers(Map<String, Scheduler> storageSchedulers) {
        this.storageSchedulers = storageSchedulers;
    }

    public Scheduler getStorageScheduler(String type) {
        return storageSchedulers.get(type);
    }

    public List getRecommendationsForVolumeCreateRequest(VirtualArray virtualArray,
            Project project, VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {

        // Get the volume placement based on passed parameters.
        Scheduler scheduler = getBlockServiceImpl(vPool);
        return scheduler.getRecommendationsForResources(virtualArray, project, vPool, capabilities);
    }

    /**
     * Returns the scheduler responsible for scheduling resources
     * 
     * @param vpool Virtual Pool
     * @return storage scheduler
     */
    public Scheduler getBlockServiceImpl(VirtualPool vpool) {

        // Select an implementation of the right scheduler
        Scheduler scheduler;
        if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
            scheduler = storageSchedulers.get("rp");
        } else if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
            scheduler = storageSchedulers.get("vplex");
        } else if (VirtualPool.vPoolSpecifiesSRDF(vpool)) {
            scheduler = storageSchedulers.get("srdf");
        } else {
            scheduler = storageSchedulers.get("block");
        }

        return scheduler;
    }
    
    public Scheduler getBlockServiceImpl(VirtualPool vpool, String type) {

        // Select an implementation of the right scheduler
        Scheduler scheduler;
        if ("rp".equalsIgnoreCase(type)) {
            scheduler = storageSchedulers.get("rp");
        } else if ("vplex".equalsIgnoreCase(type)) {
            scheduler = storageSchedulers.get("vplex");
        } else if ("srdf".equalsIgnoreCase(type)) {
            scheduler = storageSchedulers.get("srdf");
        } else {
            scheduler = storageSchedulers.get("block");
        }

        return scheduler;
    }


    /**
     * Determines if any of the RP targets has HA (VPlex local/distributed)
     * specified. This method should be called in determining if the
     * rpvplex scheduler should be used, specifically for non-virtual to
     * virtual protection. This is currently not supported and is why the
     * condition was removed from getBlockServiceImpl().
     * 
     * @return true if there is at least 1 HA RP copy target.
     */
    private boolean hasHaRpCopyTarget(VirtualPool vpool) {
        // Check if any of the RP copies contains a protection virtual pool that specifies
        // high availability. This would indicate mixed protection, therefore we need
        // to use the RP/VPLEX scheduler.
        boolean rpVPlex = false;
        if (vpool.getProtectionVarraySettings() != null &&
                !vpool.getProtectionVarraySettings().isEmpty()) {
            for (String protectionVarraURI : vpool.getProtectionVarraySettings().keySet()) {
                String settingsURI = vpool.getProtectionVarraySettings().get(protectionVarraURI);
                VpoolProtectionVarraySettings settings = dbClient.queryObject(
                        VpoolProtectionVarraySettings.class, URI.create(settingsURI));
                URI protectionVpoolId = vpool.getId();
                if (settings.getVirtualPool() != null) {
                    protectionVpoolId = settings.getVirtualPool();
                }
                VirtualPool protectionVpool = dbClient.queryObject(VirtualPool.class, protectionVpoolId);
                if (VirtualPool.vPoolSpecifiesHighAvailability(protectionVpool)) {
                    rpVPlex = true;
                    break;
                }
            }
        }

        return rpVPlex;
    }
    
    /**
     * This method runs recursive call to group master virtualPool into multiple buckets based on the configuration
     * vPoolBucketsByOrder will already be populated with root virtual pool. 
     * @param masterVirtualPool
     * @param order
     * @param vPoolBucketsByOrder
     * @throws CloneNotSupportedException 
     */
    public void groupMasterVirtualPoolIntoChildBuckets(VirtualPool masterVirtualPool, int order, Map<Integer,VirtualPool> vPoolBucketsByOrder ) throws CloneNotSupportedException {
    	//Virtual Pool with both VPlex & SRDF configured on the same site.
    	if (VirtualPool.vPoolSpecifiesHighAvailability(masterVirtualPool) && VirtualPool.vPoolSpecifiesSRDF(masterVirtualPool)) {
    		vPoolBucketsByOrder.put(++order, (VirtualPool)masterVirtualPool.clone());
    	}
    	
    	if (VirtualPool.vPoolSpecifiesHighAvailability(masterVirtualPool)) {
    		VirtualPool haVPool = VirtualPool.getHAVPool(masterVirtualPool, dbClient);
    		//use Clone of the virtual Pool, because we might be changing assigned pools
    		if (null != haVPool) {
    		vPoolBucketsByOrder.put(++order, (VirtualPool)haVPool.clone());
    		groupMasterVirtualPoolIntoChildBuckets(haVPool, order,vPoolBucketsByOrder);
    		}
    	}
    	
    	if (VirtualPool.vPoolSpecifiesSRDF(masterVirtualPool)) {
    		 Map<URI,VpoolRemoteCopyProtectionSettings> srdfSettings = VirtualPool.getRemoteProtectionSettings(masterVirtualPool, dbClient);
    		 for (Entry<URI, VpoolRemoteCopyProtectionSettings> srdfSetting : srdfSettings.entrySet()) {
    			 VpoolRemoteCopyProtectionSettings srdfSettingDetail = srdfSetting.getValue();
    			 URI vPoolUri = srdfSettingDetail.getVirtualPool();
    			 if (null != vPoolUri) {
    				 VirtualPool srdfTgtVpool = dbClient.queryObject(VirtualPool.class, vPoolUri);
    				 vPoolBucketsByOrder.put(++order, (VirtualPool)srdfTgtVpool.clone());
    				 groupMasterVirtualPoolIntoChildBuckets(srdfTgtVpool, order,vPoolBucketsByOrder);
    			 }
    		 }
    	}
		
    	
    }
    
   
    
    public void groupMasterVirtualPoolIntoBuckets(VirtualPool masterVirtualPool, int order,
            Map<Integer, VirtualPoolBucket> vPoolBucketsByOrder) throws CloneNotSupportedException {
        // Virtual Pool with both VPlex & SRDF configured on the same site.
        if (VirtualPool.vPoolSpecifiesHighAvailability(masterVirtualPool)
                && VirtualPool.vPoolSpecifiesSRDF(masterVirtualPool)) {
            vPoolBucketsByOrder.put(++order, new VirtualPoolBucket(SchedulerType.vplex.name(), masterVirtualPool));
            vPoolBucketsByOrder.put(++order, new VirtualPoolBucket(SchedulerType.srdf.name(), masterVirtualPool));

            VirtualPool haVPool = VirtualPool.getHAVPool(masterVirtualPool, dbClient);
            // use Clone of the virtual Pool, because we might be changing
            // assigned pools
            if (null != haVPool) {

                groupMasterVirtualPoolIntoBuckets(haVPool, order, vPoolBucketsByOrder);
            }

            Map<URI, VpoolRemoteCopyProtectionSettings> srdfSettings = VirtualPool.getRemoteProtectionSettings(
                    masterVirtualPool, dbClient);
            for (Entry<URI, VpoolRemoteCopyProtectionSettings> srdfSetting : srdfSettings.entrySet()) {
                VpoolRemoteCopyProtectionSettings srdfSettingDetail = srdfSetting.getValue();
                URI vPoolUri = srdfSettingDetail.getVirtualPool();
                if (null != vPoolUri) {
                    VirtualPool srdfTgtVpool = dbClient.queryObject(VirtualPool.class, vPoolUri);
                    groupMasterVirtualPoolIntoBuckets(srdfTgtVpool, order, vPoolBucketsByOrder);

                }
            }

        }

        else if (VirtualPool.vPoolSpecifiesHighAvailability(masterVirtualPool)) {
            vPoolBucketsByOrder.put(++order, new VirtualPoolBucket(SchedulerType.vplex.name(), masterVirtualPool));
            VirtualPool haVPool = VirtualPool.getHAVPool(masterVirtualPool, dbClient);
            // use Clone of the virtual Pool, because we might be changing
            // assigned pools
            if (null != haVPool) {

                groupMasterVirtualPoolIntoBuckets(haVPool, order, vPoolBucketsByOrder);
            }
        }

        else if (VirtualPool.vPoolSpecifiesSRDF(masterVirtualPool)) {
            vPoolBucketsByOrder.put(++order, new VirtualPoolBucket(SchedulerType.srdf.name(), masterVirtualPool));

            Map<URI, VpoolRemoteCopyProtectionSettings> srdfSettings = VirtualPool.getRemoteProtectionSettings(
                    masterVirtualPool, dbClient);
            for (Entry<URI, VpoolRemoteCopyProtectionSettings> srdfSetting : srdfSettings.entrySet()) {
                VpoolRemoteCopyProtectionSettings srdfSettingDetail = srdfSetting.getValue();
                URI vPoolUri = srdfSettingDetail.getVirtualPool();
                if (null != vPoolUri) {
                    VirtualPool srdfTgtVpool = dbClient.queryObject(VirtualPool.class, vPoolUri);
                    groupMasterVirtualPoolIntoBuckets(srdfTgtVpool, order, vPoolBucketsByOrder);

                }
            }
        } else {
            vPoolBucketsByOrder.put(++order, new VirtualPoolBucket(SchedulerType.block.name(), masterVirtualPool));
        }

    }
    
    public List<StoragePool> getStoragePoolsBasedOnRecommendations(VirtualPool vPool, List<Recommendation> recommendations) {
		return null;
    	
    }
    /**
     * The idea is we cannot have neither SRDF Protected|HA Pool defined on the source virtual Pool, hence if available, then they are actually
     * defined as targets for the source.
     * @param rootVirtualPool
     * @param childVirtualPool
     * @param recommendations
     * @return
     */
    public List<Recommendation> chooseRecommendations(VirtualPool rootVirtualPool, VirtualPool childVirtualPool, List<Recommendation> recommendations) {
    	//if child vpool is srdf protected, then it needs to be a child of root.On the root, the srdf tgt vpool cannot be srdf protected.
    	if (VirtualPool.vPoolSpecifiesSRDF(childVirtualPool) || VirtualPool.vPoolSpecifiesHighAvailability(childVirtualPool)) {
    		//return tgt recommendation
    	} else {
    		//return src recommendation
    	}
		return recommendations;
    	
    }
    
    /**
     * The idea is to integrate the recommended StoragePool as source for the next virtual pool.
     * @param vPool
     * @param recommendation
     */
    public void integrateRecommendationPoolstoVirtualPool(VirtualPool vPool, Recommendation recommendation) {
    	Set<String> recommendationPools = new HashSet<String>();
    	recommendationPools.add(recommendation.getSourceStoragePool().toString());
          if (null != vPool.getAssignedStoragePools()) {
        	  vPool.getAssignedStoragePools().replace(recommendationPools);
          }
          if (null != vPool.getMatchedStoragePools()) {
        	  vPool.getMatchedStoragePools().replace(recommendationPools);
          }
    }
    
    /**
     * build Cascaded Capabilities
     * @param vPool
     * @param capabilities
     */
    public void buildCascadedCapabilities(VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities, Project project) {
    	//build capabilities for given virtual Pool's cascaded children
    	//SRDF Child- Attributes.Remote_Copy- Protection Settings
    	if (null != VirtualPool.getRemoteProtectionSettings(vPool, dbClient)) {
    		capabilities.put(VirtualPoolCapabilityValuesWrapper.REMOTE_COPY_SETTINGS, VirtualPool.getRemoteProtectionSettings(vPool, dbClient));
    		capabilities.put(Attributes.project.toString(), project.getId().toString());
    	}
    	if (null != VirtualPool.getHAVPool(vPool, dbClient)) {
    		//TODO
    	}
    	
    }
    

}



