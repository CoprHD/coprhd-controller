/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.VirtualPool.MetroPointType;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.Protection.ProtectionType;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.VPlexProtection;
import com.emc.storageos.volumecontroller.VPlexRecommendation;

@SuppressWarnings("serial")
public class VPlexProtectionRecommendation extends VPlexRecommendation {

    public VPlexProtectionRecommendation() {
    }
    
    public VPlexProtectionRecommendation(VPlexRecommendation recommendation) {
    	setVPlexStorageSystem(recommendation.getVPlexStorageSystem());
    	setVirtualArray(recommendation.getVirtualArray());
    	setVirtualPool(recommendation.getVirtualPool());
    	setSourceDevice(recommendation.getSourceDevice());
    	setSourcePool(recommendation.getSourcePool());
    	setDeviceType(recommendation.getDeviceType());
    	setResourceCount(recommendation.getResourceCount());
    }
    
    private Map<URI, VPlexProtection> varrayVPlexProtection;
    private List<Recommendation> sourceVPlexHaRecommendations;
    
	public Map<URI, VPlexProtection> getVarrayVPlexProtection() {
		return varrayVPlexProtection;
	}

	public void setVarrayVPlexProtection(
			Map<URI, VPlexProtection> varrayVPlexProtection) {
		this.varrayVPlexProtection = varrayVPlexProtection;
	}

	public List<Recommendation> getSourceVPlexHaRecommendations() {
		return sourceVPlexHaRecommendations;
	}

	public void setSourceVPlexHaRecommendations(
			List<Recommendation> sourceVPlexHaRecommendations) {
		this.sourceVPlexHaRecommendations = sourceVPlexHaRecommendations;
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
        allPrimaryTargetVarrayURIs.addAll(getVarrayVPlexProtection().keySet());    

    	// Return invalid configuration if there is no primary protection specified.
    	if (allPrimaryTargetVarrayURIs.isEmpty()) {
    		return MetroPointType.INVALID;
    	}
    	
    	for (URI tgtVirtualArrayURI : allPrimaryTargetVarrayURIs) {                    
            // Try to get the protection object from the regular target map   	    
    	    Protection protection = getVirtualArrayProtectionMap().get(tgtVirtualArrayURI);
    	    if (protection == null) {
                // If that doesn't work then get the vplex protection object from the vplex target map, leave
    	        // it auto-casted to it's parent.
    	        protection = getVarrayVPlexProtection().get(tgtVirtualArrayURI);
    	    }

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
    	
    	VPlexProtectionRecommendation secondaryRecommendation = null;
    	
    	if (getSourceVPlexHaRecommendations() != null 
        		&& !getSourceVPlexHaRecommendations().isEmpty()) {
    		// There will only ever be 1 secondary recommendation in a MetroPoint case.
        	secondaryRecommendation = 
        			(VPlexProtectionRecommendation) getSourceVPlexHaRecommendations().get(0);
        } else {
        	// There must be a secondary recommendation to satisfy a valid MetroPoint
        	// configuration.
        	return MetroPointType.INVALID;
        }
    	
    	// Consolidate all VPLEX and non-VPLEX secondary targets
        List<URI> allSecondaryTargetVarrayURIs = new ArrayList<URI>();    
        allSecondaryTargetVarrayURIs.addAll(secondaryRecommendation.getVirtualArrayProtectionMap().keySet());    
        allSecondaryTargetVarrayURIs.addAll(secondaryRecommendation.getVarrayVPlexProtection().keySet());    

        // Return invalid configuration if there is no secondary protection specified.
        if (allSecondaryTargetVarrayURIs.isEmpty()) {
            return MetroPointType.INVALID;
        }
        
        for (URI tgtVirtualArrayURI : allSecondaryTargetVarrayURIs) {                    
            // Try to get the protection object from the regular target map         
            Protection protection = secondaryRecommendation.getVirtualArrayProtectionMap().get(tgtVirtualArrayURI);
            if (protection == null) {
                // If that doesn't work then get the vplex protection object from the vplex target map, leave
                // it auto-casted to it's parent.
                protection = secondaryRecommendation.getVarrayVPlexProtection().get(tgtVirtualArrayURI);
            }

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
	
    public String toString(DbClient dbClient) {
    	StringBuffer buff = new StringBuffer("\nRP+VPlex/MetroPoint Placement Results\n");
    	ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, getProtectionDevice());
        String sourceRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(getSourceInternalSiteName()) : getSourceInternalSiteName();
    	StoragePool pool = (StoragePool)dbClient.queryObject(StoragePool.class, this.getSourcePool());
    	VirtualArray journalVarray = dbClient.queryObject(VirtualArray.class, this.getSourceJournalVarray());
    	VirtualPool journalVpool = dbClient.queryObject(VirtualPool.class, this.getSourceJournalVpool());
    	StoragePool journalStoragePool = (StoragePool)dbClient.queryObject(StoragePool.class, getSourceJournalStoragePool());
    	StorageSystem system = (StorageSystem)dbClient.queryObject(StorageSystem.class, this.getSourceDevice());
    	StorageSystem vplexSourceSystem  = dbClient.queryObject(StorageSystem.class, getVPlexStorageSystem());
    	buff.append("--------------------------------------\n");
    	buff.append("--------------------------------------\n");
    	buff.append("VPlex Type : ");
    	
    	boolean vplexDistributed = false;
    	
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
    	buff.append("VPLEX Source Cluster VPlex Storage System : " + vplexSourceSystem.getLabel() + "\n");
    	buff.append("VPLEX Source Cluster Backing Storage System : " + system.getLabel() + "\n");
    	buff.append("VPLEX Source Cluster Backing Storage Pool : " + pool.getLabel() + "\n");    	    	    	
    	buff.append("VPLEX Source Cluster RP Site Allocated : " + sourceRPSiteName + "\n");    	
    	buff.append("VPLEX Source Cluster Journal Virtual Array : " + journalVarray.getLabel() + "\n");
    	buff.append("VPLEX Source Cluster Journal Virtual Pool : " + journalVpool.getLabel() + "\n");
    	if (journalStoragePool != null) {
    		buff.append("VPLEX Source Cluster Journal Backing Storage Pool : " + journalStoragePool.getLabel() + "\n");
    	}

    	// VPLEX RP Targets
    	if (this.varrayVPlexProtection != null) {    	    
	    	for (Map.Entry<URI, VPlexProtection> varrayProtectionEntry : this.varrayVPlexProtection.entrySet()) {
	    		VirtualArray varray = (VirtualArray)dbClient.queryObject(VirtualArray.class, varrayProtectionEntry.getKey());
	    		VPlexProtection vplexProtection = varrayProtectionEntry.getValue();
	    		StoragePool targetPool = (StoragePool)dbClient.queryObject(StoragePool.class, vplexProtection.getTargetStoragePool());	    		
	    	    StoragePool targetJournalStoragePool = (StoragePool)dbClient.queryObject(StoragePool.class, vplexProtection.getTargetJournalStoragePool());
	    	    VirtualArray targetJournalVarray = dbClient.queryObject(VirtualArray.class, vplexProtection.getTargetJournalVarray());
	    	    VirtualPool targetJournalVpool = dbClient.queryObject(VirtualPool.class, vplexProtection.getTargetJournalVpool());
	        	StorageSystem targetSystem = (StorageSystem)dbClient.queryObject(StorageSystem.class, vplexProtection.getTargetDevice());
	        	String targetInternalSiteName = vplexProtection.getTargetInternalSiteName();
	        	StorageSystem vplexTargetSystem = dbClient.queryObject(StorageSystem.class, vplexProtection.getTargetVplexDevice());
	        	String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName) : targetInternalSiteName;
	    		buff.append("--------------------------------------\n");
	    		buff.append("\tVPLEX RP Target\n");	    		
	    		buff.append("\tVPlex Storage System : " + vplexTargetSystem.getLabel() + "\n");
			    buff.append("\tSource Cluster Storage System : " + targetSystem.getLabel() + "\n");
			    buff.append("\tProtection to RP Site : " + targetRPSiteName + "\n");
	        	buff.append("\tSource Cluster Virtual Array : " + varray.getLabel() + "\n");
	        	buff.append("\tSource Cluster Virtual Pool : " + vplexProtection.getTargetVpool().getLabel() + "\n");
                buff.append("\tSource Cluster Storage Pool : " + targetPool.getLabel() + "\n");	 
                
                if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalVpool)) {
                	buff.append("\tSource Cluster Journal storage on VPLEX" + "\n");
                } else {
                	buff.append("\tSource Cluster Journal storage on non-VPLEX" + "\n");
                }                                
	        	buff.append("\tSource Cluster Journal Virtual Array : " + targetJournalVarray.getLabel() + "\n");
	        	buff.append("\tSource Cluster Journal Virtual Pool : " + targetJournalVpool.getLabel() + "\n");
	        	if (targetJournalStoragePool != null) {
	        		buff.append("\tSource Cluster Journal Storage Pool : " + targetJournalStoragePool.getLabel() + "\n");
	        	}
                
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
                    buff.append("\tHA Cluster VPlex Storage System : " + targetHaVplex.getLabel() + "\n");
                    buff.append("\tHA Cluster Backing Storage System : " + targetHaStorageSystem.getLabel() + "\n");
                    buff.append("\tHA Cluster Backing Storage Pool : " + targetHaStoragePool.getLabel() + "\n");
                    buff.append("\tHA Cluster Virtual Array : " + targetHaVarrayStr + "\n");
                    buff.append("\tHA Cluster Virtual Pool : " + targetHaVpool + "\n");
                    
                }
	    	}  
    	}
    	
    	// Regular RP Targets
    	if (this.getVirtualArrayProtectionMap() != null) {
        	for (URI varrayId : this.getVirtualArrayProtectionMap().keySet()) {
                VirtualArray varray = (VirtualArray)dbClient.queryObject(VirtualArray.class,  varrayId);
                StoragePool targetPool = (StoragePool)dbClient.queryObject(StoragePool.class, this.getVirtualArrayProtectionMap().get(varrayId).getTargetStoragePool());
                StoragePool targetJournalStoragePool = (StoragePool)dbClient.queryObject(StoragePool.class, this.getVirtualArrayProtectionMap().get(varrayId).getTargetJournalStoragePool());
                VirtualArray targetJournalVarray = dbClient.queryObject(VirtualArray.class, this.getVirtualArrayProtectionMap().get(varrayId).getTargetJournalVarray());
	    	    VirtualPool targetJournalVpool = dbClient.queryObject(VirtualPool.class, this.getVirtualArrayProtectionMap().get(varrayId).getTargetJournalVpool());
                StorageSystem targetSystem = (StorageSystem)dbClient.queryObject(StorageSystem.class, this.getVirtualArrayProtectionMap().get(varrayId).getTargetDevice());
                String targetInternalSiteName = this.getVirtualArrayProtectionMap().get(varrayId).getTargetInternalSiteName();
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
           
            
            if (getSourceVPlexHaRecommendations().get(0) instanceof VPlexProtectionRecommendation) {
            	// Display the protection recommendations for the Distributed side
            	VPlexProtectionRecommendation protectionRec  = (VPlexProtectionRecommendation) getSourceVPlexHaRecommendations().get(0);

            	if (protectionRec.getProtectionDevice() != null && protectionRec.getSourceInternalSiteName() != null) {
	            	ProtectionSystem haPs = dbClient.queryObject(ProtectionSystem.class, protectionRec.getProtectionDevice());
	                String haSourceRPSiteName = (haPs.getRpSiteNames() != null) ? haPs.getRpSiteNames().get(protectionRec.getSourceInternalSiteName()) : protectionRec.getSourceInternalSiteName();
	                buff.append("Distributed RP Site Allocated: " + haSourceRPSiteName + "\n");	             
	                VirtualArray haJournalVarray = dbClient.queryObject(VirtualArray.class, vplexRec.getStandbySourceJournalVarray());
	                VirtualPool haJournalVpool = dbClient.queryObject(VirtualPool.class, vplexRec.getStandbySourceJournalVpool());	                	               	            
	                StoragePool haJournalStoragePool = dbClient.queryObject(StoragePool.class, vplexRec.getSourceJournalStoragePool());
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
                if (protectionRec.varrayVPlexProtection != null) {
                    for (Map.Entry<URI, VPlexProtection> varrayProtectionEntry : protectionRec.varrayVPlexProtection.entrySet()) {
                    	VPlexProtection vplexProtection = varrayProtectionEntry.getValue();
    	                VirtualArray protectionVarray = dbClient.queryObject(VirtualArray.class, vplexProtection.getTargetVarray());
    	                StorageSystem targetStorageSystem = dbClient.queryObject(StorageSystem.class, vplexProtection.getTargetDevice());
    	                StoragePool targetStoragePool = dbClient.queryObject(StoragePool.class, vplexProtection.getTargetStoragePool());
    	                StoragePool targetJournalStoragePool = dbClient.queryObject(StoragePool.class, vplexProtection.getTargetJournalStoragePool());
    	                VirtualArray targetJournalVarray = dbClient.queryObject(VirtualArray.class, vplexProtection.getTargetJournalVarray());
    		    	    VirtualPool targetJournalVpool = dbClient.queryObject(VirtualPool.class, vplexProtection.getTargetJournalVpool());
    	            	String targetInternalSiteName = vplexProtection.getTargetInternalSiteName();
    		        	StorageSystem vplexTargetSystem = dbClient.queryObject(StorageSystem.class, vplexProtection.getTargetVplexDevice());
    	            	String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName) : targetInternalSiteName;
    	        		buff.append("--------------------------------------\n");
    	        		buff.append("\tVPLEX RP Target\n");
    	        		buff.append("\tSource Cluster Virtual Array : " + protectionVarray.getLabel() + "\n");
    		    		buff.append("\tVPlex Storage System : " + vplexTargetSystem.getLabel() + "\n");
    	    		    buff.append("\tSource Cluster Storage System : " + targetStorageSystem.getLabel() + "\n");
    	            	buff.append("\tSource Cluster Storage Pool : " + targetStoragePool.getLabel() + "\n");    	            	    	            	
                        buff.append("\tProtection to RP Site : " + targetRPSiteName + "\n");
                        if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalVpool)) {
                        	buff.append("\tSource Cluster Journal storage on VPLEX" + "\n");
                        } else {
                        	buff.append("\tSource Cluster Journal storage on non-VPLEX" + "\n");
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
                
                // Regular RP Targets
                if (protectionRec.getVirtualArrayProtectionMap() != null) {
                    for (URI varrayId : protectionRec.getVirtualArrayProtectionMap().keySet()) {
                        VirtualArray varray = (VirtualArray)dbClient.queryObject(VirtualArray.class,  varrayId);
                        StoragePool targetPool = (StoragePool)dbClient.queryObject(StoragePool.class, protectionRec.getVirtualArrayProtectionMap().get(varrayId).getTargetStoragePool());                       
                        VirtualArray targetJournalVarray = dbClient.queryObject(VirtualArray.class, this.getVirtualArrayProtectionMap().get(varrayId).getTargetJournalVarray());
        	    	    VirtualPool targetJournalVpool = dbClient.queryObject(VirtualPool.class, this.getVirtualArrayProtectionMap().get(varrayId).getTargetJournalVpool());
        	    	    StoragePool targetJournalPool = dbClient.queryObject(StoragePool.class, this.getVirtualArrayProtectionMap().get(varrayId).getTargetJournalStoragePool());
                        StorageSystem targetSystem = (StorageSystem)dbClient.queryObject(StorageSystem.class, protectionRec.getVirtualArrayProtectionMap().get(varrayId).getTargetDevice());
                        String targetInternalSiteName = protectionRec.getVirtualArrayProtectionMap().get(varrayId).getTargetInternalSiteName();
                        String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName) : targetInternalSiteName;
                        buff.append("-----------------------------\n");
                        buff.append("\tRegular RP Target\n");
                        buff.append("\tProtecting to Virtual Array : " + varray.getLabel() + "\n");
                        buff.append("\tProtection to Storage System : " + targetSystem.getLabel() + "\n");
                        buff.append("\tProtection to Storage Pool : " + targetPool.getLabel() + "\n");                        
                        buff.append("\tProtection to RP Site : " + targetRPSiteName + "\n");
                        if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalVpool)) {
                        	buff.append("\tSource Cluster Journal storage on VPLEX" + "\n");
                        } else {
                        	buff.append("\tSource Cluster Journal storage on non-VPLEX" + "\n");
                        }                        
                        buff.append("\tProtection Cluster Journal Virtual Array : " + targetJournalVarray.getLabel() + "\n");
        	        	buff.append("\tProtection Cluster Journal Virtual Pool : " + targetJournalVpool.getLabel() + "\n");
        	        	buff.append("\tProtection Cluster Journal Storage Pool : " + targetJournalPool.getLabel() + "\n");
                    }
                } 
            }
    	}

    	buff.append("--------------------------------------\n");
    	buff.append("--------------------------------------\n");
    	return buff.toString();
    }
}
