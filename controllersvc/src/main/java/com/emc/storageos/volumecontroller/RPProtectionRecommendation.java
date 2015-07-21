/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.MetroPointType;
import com.emc.storageos.volumecontroller.Protection.ProtectionType;

/**
 * Recommendation for a placement is a storage pool and its storage device.
 */
@SuppressWarnings("serial")
public class RPProtectionRecommendation extends VPlexRecommendation {

    // Source
    private String sourceInternalSiteName;    
    private URI vpoolChangeVolume;
    private URI vpoolChangeVpool;   
    private URI sourceJournalStoragePool;
    private URI sourceJournalVarray;
    private URI sourceJournalVpool;
    private URI standbySourceJournalVarray;
    private URI standbySourceJournalVpool;
    private URI protectionDevice;
    // This is the Storage System that was chosen by placement for connectivity/visibility to the RP Cluster
    private URI sourceInternalSiteStorageSystem;
    
    private boolean vpoolChangeProtectionAlreadyExists;
   
    // Protection/Replication mover (RP, for instance)  
    private PlacementProgress placementStepsCompleted;
    private String protectionSystemCriteriaError;
    // This is needed for MetroPoint.  The concatenated string containing
    // both the RP internal site name + associated storage system.
    private String rpSiteAssociateStorageSystem;
        
    private Map<URI, Protection> varrayProtectionMap;
    private List<Recommendation> sourceVPlexHaRecommendations;
    
	public static enum PlacementProgress {
		NONE, IDENTIFIED_SOLUTION_FOR_SOURCE, IDENTIFIED_SOLUTION_FOR_SUBSET_OF_TARGETS, 
		IDENTIFIED_SOLUTION_FOR_ALL_TARGETS, PROTECTION_SYSTEM_CANNOT_FULFILL_REQUEST
	}

    public RPProtectionRecommendation() {
    	varrayProtectionMap = new HashMap<URI, Protection>();
    	placementStepsCompleted = PlacementProgress.NONE;    	
    }
    
    public RPProtectionRecommendation(RPProtectionRecommendation copy) {
    	// properties of Recommendation
    	this.setSourcePool(copy.getSourcePool());
    	this.setSourceDevice(copy.getSourceDevice());
    	this.setDeviceType(copy.getDeviceType());
    	this.setResourceCount(copy.getResourceCount());
    	// properties of RPProtectionRecommendation
    	this.placementStepsCompleted = copy.getPlacementStepsCompleted();
    	this.protectionDevice = copy.getProtectionDevice();
    	this.sourceInternalSiteName = copy.getSourceInternalSiteName();
    	this.sourceJournalStoragePool = copy.getSourceJournalStoragePool();
    	this.sourceJournalVarray = copy.getSourceJournalVarray();
    	this.sourceJournalVpool = copy.getSourceJournalVpool();
    	this.standbySourceJournalVarray = copy.getStandbySourceJournalVarray();
    	this.standbySourceJournalVpool = copy.getStandbySourceJournalVpool();
    	this.vpoolChangeVolume = copy.getVpoolChangeVolume();
    	this.vpoolChangeVpool = copy.getVpoolChangeVpool();
    	// map
    	this.varrayProtectionMap = new HashMap<URI, Protection>();
    	this.varrayProtectionMap.putAll(copy.getVirtualArrayProtectionMap());
    }
	
    public void setSourceJournalStoragePool(URI sourceJournalStoragePool) {
        this.sourceJournalStoragePool = sourceJournalStoragePool;
    }

    public URI getSourceJournalStoragePool() {
        return this.sourceJournalStoragePool;
    }

	public String getSourceInternalSiteName() {
        return sourceInternalSiteName;
    }

    public URI getProtectionDevice() {
        return protectionDevice;
    }    


	public Map<URI, Protection> getVirtualArrayProtectionMap() {
        return varrayProtectionMap;
    }

    public void setVirtualArrayProtectionMap(
            Map<URI, Protection> varrayProtectionMap) {
        this.varrayProtectionMap = varrayProtectionMap;
    }

    public void setSourceInternalSiteName(String sourceInternalSiteName) {
        this.sourceInternalSiteName = sourceInternalSiteName;
    }

    public void setProtectionDevice(URI protectionDevice) {
        this.protectionDevice = protectionDevice;
    }

    public void setVpoolChangeVolume(URI id) {
        vpoolChangeVolume = id;
    }

    public URI getVpoolChangeVolume() {
        return vpoolChangeVolume;
    }

    public void setVpoolChangeVpool(URI id) {
        vpoolChangeVpool = id;
    }

    public URI getVpoolChangeVpool() {
        return vpoolChangeVpool;
    }

	public String getRpSiteAssociateStorageSystem() {
		return rpSiteAssociateStorageSystem;
	}

	public void setRpSiteAssociateStorageSystem(String rpSiteAssociateStorageSystem) {
		this.rpSiteAssociateStorageSystem = rpSiteAssociateStorageSystem;
	}

    public boolean isVpoolChangeProtectionAlreadyExists() {
        return vpoolChangeProtectionAlreadyExists;
    }

    public void setVpoolChangeProtectionAlreadyExists(
            boolean vpoolChangeProtectionAlreadyExists) {
        this.vpoolChangeProtectionAlreadyExists = vpoolChangeProtectionAlreadyExists;
    }
    
    public URI getSourceJournalVarray() {
		return sourceJournalVarray;
	}

	public void setSourceJournalVarray(URI _sourceJournalVarray) {
		this.sourceJournalVarray = _sourceJournalVarray;
	}

	public URI getSourceJournalVpool() {
		return sourceJournalVpool;
	}

	public void setSourceJournalVpool(URI _sourceJournalVpool) {
		this.sourceJournalVpool = _sourceJournalVpool;
	}

	public URI getStandbySourceJournalVarray() {
		return standbySourceJournalVarray;
	}

	public void setStandbySourceJournalVarray(
			URI _standbySourceJournalVarray) {
		this.standbySourceJournalVarray = _standbySourceJournalVarray;
	}

	public URI getStandbySourceJournalVpool() {
		return standbySourceJournalVpool;
	}

	public void setStandbySourceJournalVpool(URI _standbySourceJournalVpool) {
		this.standbySourceJournalVpool = _standbySourceJournalVpool;
	}

    public URI getSourceInternalSiteStorageSystem() {
        return sourceInternalSiteStorageSystem;
    }

    public void setSourceInternalSiteStorageSystem(
            URI sourceInternalSiteStorageSystem) {
        this.sourceInternalSiteStorageSystem = sourceInternalSiteStorageSystem;
    }

	public List<Recommendation> getSourceVPlexHaRecommendations() {
		return sourceVPlexHaRecommendations;
	}

	public void setSourceVPlexHaRecommendations(
			List<Recommendation> sourceVPlexHaRecommendations) {
		this.sourceVPlexHaRecommendations = sourceVPlexHaRecommendations;
	}
	
    public PlacementProgress getPlacementStepsCompleted() {
		return placementStepsCompleted;
	}

	public void setPlacementStepsCompleted(PlacementProgress identifiedProtectionSolution) {
		this.placementStepsCompleted = identifiedProtectionSolution;
	}

	public String getProtectionSystemCriteriaError() {
		return protectionSystemCriteriaError;
	}

	public void setProtectionSystemCriteriaError(String protectionSystemCriteriaError) {
		this.protectionSystemCriteriaError = protectionSystemCriteriaError;
	}
	
	/**
	 * @param internalSiteName
	 * @return
	 */
	public int getNumberOfVolumes(String internalSiteName) {
    	int count=0;

    	if (this.getSourceInternalSiteName().equals(internalSiteName)) {
    		count += this.getResourceCount();
    	}

    	if (getVirtualArrayProtectionMap() != null) {
			for (Protection protection : getVirtualArrayProtectionMap().values()) {
				if (protection.getTargetInternalSiteName().equals(internalSiteName)) {
					if (protection.getTargetJournalStoragePool()!=null) {
						count++; // Journal Volume
					}
					count += this.getResourceCount();
				}
			}
		}

    	return count;
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.volumecontroller.VPlexRecommendation#toString()
     */
    @Override
	public String toString() {
		return "RPProtectionRecommendation [_sourceInternalSiteName="
				+ sourceInternalSiteName + ", _varrayProtectionMap="
				+ varrayProtectionMap + ", _vpoolChangeVolume="
				+ vpoolChangeVolume + ", _sourceJournalPool=" 
				+ sourceJournalStoragePool + ", _vpoolChangeVpool="
				+ sourceJournalVarray +", _sourceJournalVarray="
				+ sourceJournalVpool +", _sourceJournalVpool="
				+ vpoolChangeVpool + ", _protectionDevice="
				+ protectionDevice + "]";
	}
    
    /**
     * @param dbClient
     * @return
     */
    public String toString(DbClient dbClient) {
    	StringBuffer buff = new StringBuffer("\n Placement Results : ");
    	boolean vplexDistributed = false;
    	
    	ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, getProtectionDevice());
    	boolean isRPVplex = (null != super.getVirtualPool() && VirtualPool.vPoolSpecifiesHighAvailability(super.getVirtualPool()));
    	if (isRPVplex) {    	    
	    	buff.append("RP+VPLEX/Metropoint \n");	    	
	        String sourceRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(getSourceInternalSiteName()) : getSourceInternalSiteName();
	    	StoragePool pool = (StoragePool)dbClient.queryObject(StoragePool.class, this.getSourcePool());
	    	VirtualArray journalVarray = dbClient.queryObject(VirtualArray.class, this.getSourceJournalVarray());
	    	VirtualPool journalVpool = dbClient.queryObject(VirtualPool.class, this.getSourceJournalVpool());
	    	StoragePool journalStoragePool = (StoragePool)dbClient.queryObject(StoragePool.class, getSourceJournalStoragePool());    	
			StorageSystem system = (StorageSystem)dbClient.queryObject(StorageSystem.class, this.getSourceDevice());
	    	
	    	buff.append("--------------------------------------\n");
	    	buff.append("--------------------------------------\n");
	    	buff.append("VPlex Type : ");	    		    
	    	
	    	if (getSourceVPlexHaRecommendations() == null
	                || getSourceVPlexHaRecommendations().isEmpty()) {
	            buff.append("Local\n");
	        } else {
	        	vplexDistributed = true;
	        	buff.append("Distributed\n");
	            }        	
	    	    	
	    	buff.append("Number of volumes placed in this result : " + this.getResourceCount() + "\n");
	    	buff.append("Protection System Allocated : " + ps.getLabel() + "\n");
	    	buff.append("--------------------------------------\n");   
	    	if (null != getVPlexStorageSystem()) {
	    		StorageSystem vplexSourceSystem  = dbClient.queryObject(StorageSystem.class, getVPlexStorageSystem());    	
	    		buff.append("VPLEX Source Cluster VPlex Storage System : " + vplexSourceSystem.getLabel() + "\n");
	    	}
	    	buff.append("VPLEX Source Cluster Backing Storage System : " + system.getLabel() + "\n");
	    	buff.append("VPLEX Source Cluster Backing Storage Pool : " + pool.getLabel() + "\n");    	    	    	
	    	buff.append("VPLEX Source Cluster RP Site Allocated : " + sourceRPSiteName + "\n");    
	    	if (VirtualPool.vPoolSpecifiesHighAvailability(journalVpool)) {
	    		buff.append("VPLEX Journal :" + "\n");	    		
	    	} else {
	    		buff.append("Regular Journal : " + "\n");
	    	}
	    	buff.append("VPLEX Source Cluster Journal Virtual Array : " + journalVarray.getLabel() + "\n");
	    	buff.append("VPLEX Source Cluster Journal Virtual Pool : " + journalVpool.getLabel() + "\n");
	    	if (journalStoragePool != null) {
	    		buff.append("VPLEX Source Cluster Journal Backing Storage Pool : " + journalStoragePool.getLabel() + "\n");
	    	}
    	} else {    
    		buff.append("Regular RP \n");
    		StoragePool pool = (StoragePool)dbClient.queryObject(StoragePool.class, this.getSourcePool());
        	StoragePool journalStoragePool = (StoragePool)dbClient.queryObject(StoragePool.class, this.sourceJournalStoragePool);
        	StorageSystem system = (StorageSystem)dbClient.queryObject(StorageSystem.class, this.getSourceDevice());
        	ps = dbClient.queryObject(ProtectionSystem.class, protectionDevice);
        	VirtualArray journalVarray = dbClient.queryObject(VirtualArray.class, getSourceJournalVarray());
        	VirtualPool journalVpool = dbClient.queryObject(VirtualPool.class, getSourceJournalVpool());
            String sourceRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(sourceInternalSiteName) : sourceInternalSiteName;
        	buff.append("--------------------------------------\n");
        	buff.append("--------------------------------------\n");
        	buff.append("Number of volumes placed in this result: " + this.getResourceCount() + "\n");
        	buff.append("Protection System Allocated : " + ps.getLabel() + "\n");
        	buff.append("Source RP Site Allocated : " + sourceRPSiteName + "\n");
        	buff.append("Source Storage System : " + system.getLabel() + "\n");
        	buff.append("Source Storage Pool : " + pool.getLabel() + "\n");    
        	buff.append("Source Journal Virtual Array :" + journalVarray.getLabel() + " \n");
        	buff.append("Source Journal Virtual Pool : " + journalVpool.getLabel() + " \n");
        	buff.append("Source Journal Storage Pool : " + journalStoragePool.getLabel() + "\n");    		
    	}

    	// RP Targets
    	if (null != getVirtualArrayProtectionMap()) {     		
    		for (Map.Entry<URI, Protection> varrayProtectionEntry : this.varrayProtectionMap.entrySet()) {
    		VirtualArray varray = (VirtualArray)dbClient.queryObject(VirtualArray.class, varrayProtectionEntry.getKey());
    		Protection protection = varrayProtectionEntry.getValue();
    		StoragePool targetPool = (StoragePool)dbClient.queryObject(StoragePool.class, protection.getTargetStoragePool());	    		
    	    StoragePool targetJournalStoragePool = (StoragePool)dbClient.queryObject(StoragePool.class, protection.getTargetJournalStoragePool());
    	    VirtualArray targetJournalVarray = dbClient.queryObject(VirtualArray.class, protection.getTargetJournalVarray());
    	    VirtualPool targetJournalVpool = dbClient.queryObject(VirtualPool.class, protection.getTargetJournalVpool());
        	StorageSystem targetSystem = (StorageSystem)dbClient.queryObject(StorageSystem.class, protection.getTargetStorageSystem());
        	String targetInternalSiteName = protection.getTargetInternalSiteName();
    		if (VirtualPool.vPoolSpecifiesHighAvailability(protection.getTargetVpool())) {		   
    			
	        	String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName) : targetInternalSiteName;
	    		buff.append("--------------------------------------\n");
	    		buff.append("\tVPLEX RP Target\n");	    	
	    		if (null != protection.getTargetVplexStorageSystem()) {
	    			buff.append("\tVplex Storage System : " +  dbClient.queryObject(StorageSystem.class, protection.getTargetVplexStorageSystem()).getLabel() + "\n");
    			}		    		
			    buff.append("\tSource Cluster Backing Storage System : " + targetSystem.getLabel() + "\n");
			    buff.append("\tProtection to RP Site : " + targetRPSiteName + "\n");
	        	buff.append("\tSource Cluster Virtual Array : " + varray.getLabel() + "\n");
	        	buff.append("\tSource Cluster Virtual Pool : " + protection.getTargetVpool().getLabel() + "\n");
                buff.append("\tSource Cluster Storage Pool : " + targetPool.getLabel() + "\n");	                
                
                if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalVpool)) {
                	buff.append("\tVPLEX Journal : " + "\n");
                	buff.append("\tVplex Storage System : " + dbClient.queryObject(StorageSystem.class, protection.getTargetVplexStorageSystem()).getLabel() + "\n");
                } else {
                	buff.append("\tRegular Journal : " + "\n");
                	buff.append("\tStorage System : " + dbClient.queryObject(StorageSystem.class, protection.getTargetStorageSystem()).getLabel() + "\n");
                }                                
	        	buff.append("\tSource Cluster Journal Virtual Array : " + targetJournalVarray.getLabel() + "\n");
	        	buff.append("\tSource Cluster Journal Virtual Pool : " + targetJournalVpool.getLabel() + "\n");
	        	if (targetJournalStoragePool != null) {
	        		buff.append("\tSource Cluster Journal Storage Pool : " + targetJournalStoragePool.getLabel() + "\n");
	        	}
                
                // Check for distributed leg of the target
                if (protection.getTargetVPlexHaRecommendations() != null
                        &&  !protection.getTargetVPlexHaRecommendations().isEmpty()) {
                    VPlexRecommendation vplexTargetHaRec  = (VPlexRecommendation) protection.getTargetVPlexHaRecommendations().get(0);
                    String targetHaVarrayStr = "N/A";
                    if (vplexTargetHaRec.getVirtualArray() != null) {
                    	VirtualArray targetHaVarray = dbClient.queryObject(VirtualArray.class, vplexTargetHaRec.getVirtualArray());
                    	targetHaVarrayStr = targetHaVarray.getLabel();
                    }

                    StorageSystem targetHaVplex = dbClient.queryObject(StorageSystem.class, vplexTargetHaRec.getVPlexStorageSystem());
                    StorageSystem targetHaStorageSystem = dbClient.queryObject(StorageSystem.class, vplexTargetHaRec.getSourceDevice());
                    StoragePool targetHaStoragePool = dbClient.queryObject(StoragePool.class, vplexTargetHaRec.getSourcePool());
                    String targetHaVpool = (vplexTargetHaRec.getVirtualPool() != null) ? vplexTargetHaRec.getVirtualPool().getLabel() : "N/A";            
                    buff.append("\tHA Cluster VPlex Storage System : " + targetHaVplex.getLabel() + "\n");
                    buff.append("\tHA Cluster Backing Storage System : " + targetHaStorageSystem.getLabel() + "\n");
                    buff.append("\tHA Cluster Backing Storage Pool : " + targetHaStoragePool.getLabel() + "\n");
                    buff.append("\tHA Cluster Virtual Array : " + targetHaVarrayStr + "\n");
                    buff.append("\tHA Cluster Virtual Pool : " + targetHaVpool + "\n");
                    
                }
	    	}  else {		    		
	                String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName) : targetInternalSiteName;
	                buff.append("-----------------------------\n");
	                buff.append("\tRegular RP Target\n");                
	                buff.append("\tProtection to Storage System : " + targetSystem.getLabel() + "\n");
	                buff.append("\tProtection to Storage Pool : " + targetPool.getLabel() + "\n");                
	                buff.append("\tProtecting to Virtual Array : " + varray.getLabel() + "\n");
	                buff.append("\tProtection to RP Site : " + targetRPSiteName + "\n");                
	                buff.append("\tProtection Cluster Journal Virtual Array : " + targetJournalVarray.getLabel() + "\n");
		        	buff.append("\tProtection Cluster Journal Virtual Pool : " + targetJournalVpool.getLabel() + "\n");
		        	buff.append("\tProtection Journal Storage Pool : " + targetJournalStoragePool.getLabel() + "\n");
	    		}
    		}
    	}
    	
    	if (vplexDistributed) {
    		buff.append("--------------------------------------\n");
            VPlexRecommendation vplexRec  = (VPlexRecommendation) getSourceVPlexHaRecommendations().get(0);
            String haVarrayStr = "N/A";
            if (vplexRec.getVirtualArray() != null) {
            	VirtualArray haVarray = dbClient.queryObject(VirtualArray.class, vplexRec.getVirtualArray());
            	haVarrayStr = haVarray.getLabel();
            }

            StorageSystem haVplex = dbClient.queryObject(StorageSystem.class, vplexRec.getVPlexStorageSystem());
            StorageSystem haStorageSystem = dbClient.queryObject(StorageSystem.class, vplexRec.getSourceDevice());
            StoragePool haStoragePool = dbClient.queryObject(StoragePool.class, vplexRec.getSourcePool());
           
            String haVpool = (vplexRec.getVirtualPool() != null) ? vplexRec.getVirtualPool().getLabel() : "N/A";            
            buff.append("VPLEX HA Cluster VPlex Storage System : " + haVplex.getLabel() + "\n");
            buff.append("VPLEX HA Cluster Backing Storage System : " + haStorageSystem.getLabel() + "\n");
            buff.append("VPLEX HA Cluster Backing Storage Pool : " + haStoragePool.getLabel() + "\n");
            buff.append("VPLEX HA Cluster Virtual Pool : " + haVpool + "\n");
            buff.append("VPLEX HA Cluster Virtual Array : " + haVarrayStr + "\n"); 
           
            
            if (getSourceVPlexHaRecommendations().get(0) instanceof RPProtectionRecommendation) {
            	// Display the protection recommendations for the Distributed side
            	RPProtectionRecommendation protectionRec  = (RPProtectionRecommendation) getSourceVPlexHaRecommendations().get(0);

            	if (protectionRec.getProtectionDevice() != null && protectionRec.getSourceInternalSiteName() != null) {
	            	ProtectionSystem haPs = dbClient.queryObject(ProtectionSystem.class, protectionRec.getProtectionDevice());
	                String haSourceRPSiteName = (haPs.getRpSiteNames() != null) ? haPs.getRpSiteNames().get(protectionRec.getSourceInternalSiteName()) : protectionRec.getSourceInternalSiteName();
	                buff.append("Distributed RP Site Allocated: " + haSourceRPSiteName + "\n");	             
	                VirtualArray haJournalVarray = dbClient.queryObject(VirtualArray.class, protectionRec.getStandbySourceJournalVarray());
	                VirtualPool haJournalVpool = dbClient.queryObject(VirtualPool.class, protectionRec.getStandbySourceJournalVpool());	                	               	            
	                StoragePool haJournalStoragePool = dbClient.queryObject(StoragePool.class, protectionRec.getSourceJournalStoragePool());
	                if (VirtualPool.vPoolSpecifiesHighAvailability(haJournalVpool)) {
	                	buff.append("Distributed MetroPoint Source Journal storage on VPLEX" + "\n");
	                } else {
	                	buff.append("Distributed MetroPoint Source Journal storage on non-VPLEX" + "\n");
	                }	            	
	            	buff.append("Distributed MetroPoint Source Journal Virtual Array : " + haJournalVarray.getLabel() + "\n");
	                buff.append("Distributed MetroPoint Source Journal Virtual Pool : " + haJournalVpool.getLabel() + "\n");
	                buff.append("Distributed MetroPoint Source Journal Storage Pool : " + haJournalStoragePool.getLabel() + "\n");
            	}
            	
                // VPLEX RP Targets
                if (protectionRec.varrayProtectionMap != null) {
                    for (Map.Entry<URI, Protection> varrayProtectionEntry : protectionRec.varrayProtectionMap.entrySet()) {
                    	Protection vplexProtection = varrayProtectionEntry.getValue();
    	                VirtualArray protectionVarray = dbClient.queryObject(VirtualArray.class, vplexProtection.getTargetVarray());
    	                StorageSystem targetStorageSystem = dbClient.queryObject(StorageSystem.class, vplexProtection.getTargetStorageSystem());
    	                StoragePool targetStoragePool = dbClient.queryObject(StoragePool.class, vplexProtection.getTargetStoragePool());
    	                StoragePool targetJournalStoragePool = dbClient.queryObject(StoragePool.class, vplexProtection.getTargetJournalStoragePool());
    	                VirtualArray targetJournalVarray = dbClient.queryObject(VirtualArray.class, vplexProtection.getTargetJournalVarray());
    		    	    VirtualPool targetJournalVpool = dbClient.queryObject(VirtualPool.class, vplexProtection.getTargetJournalVpool());
    	            	String targetInternalSiteName = vplexProtection.getTargetInternalSiteName();
    		        	StorageSystem vplexTargetSystem = dbClient.queryObject(StorageSystem.class, vplexProtection.getTargetStorageSystem());
    	            	String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName) : targetInternalSiteName;
    	        		buff.append("--------------------------------------\n");
    	        		buff.append("\tVPLEX RP Target\n");
    	        		buff.append("\tSource Cluster Virtual Array : " + protectionVarray.getLabel() + "\n");
    		    		buff.append("\tVPlex Storage System : " + vplexTargetSystem.getLabel() + "\n");
    	    		    buff.append("\tSource Cluster Storage System : " + targetStorageSystem.getLabel() + "\n");
    	            	buff.append("\tSource Cluster Storage Pool : " + targetStoragePool.getLabel() + "\n");    	            	    	            	
                        buff.append("\tProtection to RP Site : " + targetRPSiteName + "\n");
                        
                        
                        StorageSystem journalStorageSystem; 
                        if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalVpool)) {
                        	buff.append("\tVPLEX journal : " + "\n");                        	
                        } else {
                        	buff.append("\tRegular journal: " + "\n");
                        }                        
        	        	buff.append("\tSource Cluster Journal Virtual Array : " + targetJournalVarray.getLabel() + "\n");
        	        	buff.append("\tSource Cluster Journal Virtual Pool : " + targetJournalVpool.getLabel() + "\n");
        	        	buff.append("\tSource Cluster Journal Storage Pool : " + targetJournalStoragePool.getLabel() + "\n");
                        
                        // Check for distributed leg of the target
                        if (vplexProtection.getTargetVPlexHaRecommendations() != null
                                &&  !vplexProtection.getTargetVPlexHaRecommendations().isEmpty()) {
                            VPlexRecommendation vplexTargetHaRec  = (VPlexRecommendation) vplexProtection.getTargetVPlexHaRecommendations().get(0);
                            String targetHaVarrayStr = "N/A";
                            if (vplexTargetHaRec.getVirtualArray() != null) {
                            	VirtualArray targetHaVarray = dbClient.queryObject(VirtualArray.class, vplexTargetHaRec.getVirtualArray());
                            	targetHaVarrayStr = targetHaVarray.getLabel();
                            }

                            StorageSystem targetHaVplex = dbClient.queryObject(StorageSystem.class, vplexTargetHaRec.getVPlexStorageSystem());
                            StorageSystem targetHaStorageSystem = dbClient.queryObject(StorageSystem.class, vplexTargetHaRec.getSourceDevice());
                            StoragePool targetHaStoragePool = dbClient.queryObject(StoragePool.class, vplexTargetHaRec.getSourcePool());
                            String targetHaVpool = (vplexTargetHaRec.getVirtualPool() != null) ? vplexTargetHaRec.getVirtualPool().getLabel() : "N/A";
                            buff.append("\tHA Cluster Virtual Array : " + targetHaVarrayStr + "\n");
                            buff.append("\tHA Cluster Virtual Pool : " + targetHaVpool + "\n");
                            buff.append("\tHA Cluster VPlex Storage System : " + targetHaVplex.getLabel() + "\n");
                            buff.append("\tHA Cluster Backing Storage System : " + targetHaStorageSystem.getLabel() + "\n");
                            buff.append("\tHA Cluster Backing Storage Pool : " + targetHaStoragePool.getLabel() + "\n");                            
                            
                        }
                    }
                }              
            }
    	}

    	buff.append("--------------------------------------\n");
    	buff.append("--------------------------------------\n");
    	return buff.toString();
    } 
	
	/**
	 * Gets the MetroPoint configuration type for the recommendation.  Looks specifically
	 * at the protection copy types to figure out the configuration.  If any of the
	 * protection copy types is not set, we cannot properly determine the configuration
	 * so we must return null;
	 * 
	 * @return the MetroPoint configuration type
	 */
	public MetroPointType getMetroPointType() {
		MetroPointType metroPointType = null;
		
    	int primaryLocalCopyCount = 0;
    	int primaryRemoteCopyCount = 0;
    	int secondaryLocalCopyCount = 0;
    	int secondaryRemoteCopyCount = 0;
    	
    	// Consolidate all VPLEX and non-VPLEX primary targets
        List<URI> allPrimaryTargetVarrayURIs = new ArrayList<URI>();    
        allPrimaryTargetVarrayURIs.addAll(getVirtualArrayProtectionMap().keySet());    
        //allPrimaryTargetVarrayURIs.addAll(getVarrayVPlexProtection().keySet());    

    	// Return invalid configuration if there is no primary protection specified.
    	if (allPrimaryTargetVarrayURIs.isEmpty()) {
    		return MetroPointType.INVALID;
    	}
    	
    	for (URI tgtVirtualArrayURI : allPrimaryTargetVarrayURIs) {                    
            // Try to get the protection object from the regular target map   	    
    	    Protection protection = getVirtualArrayProtectionMap().get(tgtVirtualArrayURI);
    	    /* if (protection == null) {
                // If that doesn't work then get the vplex protection object from the vplex target map, leave
    	        // it auto-casted to it's parent.
    	        protection = getVarrayVPlexProtection().get(tgtVirtualArrayURI);
    	    }*/

    		if (protection.getProtectionType() == null) {
    			// If even one protection type is missing, this is not a valid MetroPoint
    			// recommendation.  The protection type is only ever set in 
    			// MetroPoint specific code.
    			return MetroPointType.INVALID;
    		}
    		if (protection.getProtectionType() == ProtectionType.LOCAL) {
    			primaryLocalCopyCount++;
    		} else if (protection.getProtectionType() == ProtectionType.REMOTE) {
    			primaryRemoteCopyCount++;
    		}
    	}
    	
    	RPProtectionRecommendation secondaryRecommendation = null;
    	
    	if (getSourceVPlexHaRecommendations() != null 
        		&& !getSourceVPlexHaRecommendations().isEmpty()) {
    		// There will only ever be 1 secondary recommendation in a MetroPoint case.
        	secondaryRecommendation = (RPProtectionRecommendation) this.getSourceVPlexHaRecommendations().get(0);
        } else {
        	// There must be a secondary recommendation to satisfy a valid MetroPoint
        	// configuration.
        	return MetroPointType.INVALID;
        }
    	
    	// Consolidate all VPLEX and non-VPLEX secondary targets
        List<URI> allSecondaryTargetVarrayURIs = new ArrayList<URI>();    
        allSecondaryTargetVarrayURIs.addAll(secondaryRecommendation.getVirtualArrayProtectionMap().keySet());    
        //allSecondaryTargetVarrayURIs.addAll(secondaryRecommendation.getVarrayVPlexProtection().keySet());    

        // Return invalid configuration if there is no secondary protection specified.
        if (allSecondaryTargetVarrayURIs.isEmpty()) {
            return MetroPointType.INVALID;
        }
        
        for (URI tgtVirtualArrayURI : allSecondaryTargetVarrayURIs) {                    
            // Try to get the protection object from the regular target map         
            Protection protection = secondaryRecommendation.getVirtualArrayProtectionMap().get(tgtVirtualArrayURI);
            /* 
            if (protection == null) {
                // If that doesn't work then get the vplex protection object from the vplex target map, leave
                // it auto-casted to it's parent.
                protection = secondaryRecommendation.getVarrayVPlexProtection().get(tgtVirtualArrayURI);
            } */

    		if (protection.getProtectionType() == null) {
    			// If even one protection type is missing, this is not a valid MetroPoint
    			// recommendation.  The protection type is only ever set in 
    			// MetroPoint specific code.
    			return MetroPointType.INVALID;
    		}
    		if (protection.getProtectionType() == ProtectionType.LOCAL) {
    			secondaryLocalCopyCount++;
    		} else if (protection.getProtectionType() == ProtectionType.REMOTE) {
    			secondaryRemoteCopyCount++;
    		}
    	}
    	
    	boolean singleRemoteCopy = false;
    	boolean primaryLocalCopy = false;
    	boolean secondaryLocalCopy = false;
    	
    	if (primaryRemoteCopyCount == 1 && secondaryRemoteCopyCount == 1) {
    		singleRemoteCopy = true;
    	}
    	
    	if (primaryLocalCopyCount == 1) {
    		primaryLocalCopy = true;
    	}
    	
    	if (secondaryLocalCopyCount == 1) {
    		secondaryLocalCopy = true;
    	}
    	
    	if (singleRemoteCopy && primaryLocalCopy && secondaryLocalCopy) {
    		metroPointType = MetroPointType.TWO_LOCAL_REMOTE;
    	} else if (singleRemoteCopy && 
    			((!primaryLocalCopy && secondaryLocalCopy) || (primaryLocalCopy && !secondaryLocalCopy))) {
    		metroPointType = MetroPointType.ONE_LOCAL_REMOTE;
    	} else if (singleRemoteCopy && !primaryLocalCopy && !secondaryLocalCopy) {
    		metroPointType = MetroPointType.SINGLE_REMOTE;
    	} else if (!singleRemoteCopy && primaryLocalCopy && secondaryLocalCopy) {
    		metroPointType = MetroPointType.LOCAL_ONLY;
    	} else {
    		metroPointType = MetroPointType.INVALID;
    	}
    	
    	return metroPointType;
	}
	
	/**
	 * @param destInternalSiteName
	 * @return
	 */
	public boolean containsTargetInternalSiteName(String destInternalSiteName) {
		if (getVirtualArrayProtectionMap() != null) {
			for (Protection protection : getVirtualArrayProtectionMap().values()) {
				if (protection.getTargetInternalSiteName().equals(destInternalSiteName)) {
					return true;
				}
			}
		}
		return false;
	}
}


