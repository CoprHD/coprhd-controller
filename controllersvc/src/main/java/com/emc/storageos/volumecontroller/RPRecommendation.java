/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.MetroPointType;
import com.emc.storageos.db.client.util.SizeUtil;

/*
 * Represents recommendation for an RP protected volume.  
 */
@SuppressWarnings("serial")
public class RPRecommendation extends Recommendation {
    public static enum ProtectionType {
        REMOTE,
        LOCAL
    }
    
	private VPlexRecommendation virtualVolumeRecommendation;
	private RPRecommendation haRecommendation;	
	private List<RPRecommendation> targetRecommendations;
	private String internalSiteName;
	// This is needed for MetroPoint.  The concatenated string containing
	// both the RP internal site name + associated storage system.
	private String rpSiteAssociateStorageSystem;
	// This is the Storage System that was chosen by placement for connectivity/visibility to the RP Cluster
	private URI internalSiteStorageSystem;	
	private ProtectionType protectionType;
	//Size in Bytes of each resource
	private Long size;
	private String rpCopyName;
		 	 
	public VPlexRecommendation getVirtualVolumeRecommendation() {
		return virtualVolumeRecommendation;
	}

	public void setVirtualVolumeRecommendation(
			VPlexRecommendation virtualVolumeRecommendation) {
		this.virtualVolumeRecommendation = virtualVolumeRecommendation;
	}

	public RPRecommendation getHaRecommendation() {
		return haRecommendation;
	}
	
	public void setHaRecommendation(RPRecommendation haRecommendation) {
		this.haRecommendation = haRecommendation;
	}
	
	public List<RPRecommendation> getTargetRecommendations() {
		return targetRecommendations;
	}

	public void setTargetRecommendations(List<RPRecommendation> targetRecommendations) {
		this.targetRecommendations = targetRecommendations;
	}
		
	 public String getInternalSiteName() {
		return internalSiteName;
	 }

	public void setInternalSiteName(String sourceInternalSiteName) {
		this.internalSiteName = sourceInternalSiteName;
	}
	
	public String getRpSiteAssociateStorageSystem() {
		return rpSiteAssociateStorageSystem;
	}

	public void setRpSiteAssociateStorageSystem(
			String rpSiteAssociateStorageSystem) {
		this.rpSiteAssociateStorageSystem = rpSiteAssociateStorageSystem;
	}
	
	public URI getInternalSiteStorageSystem() {
		return internalSiteStorageSystem;
	}

	public void setInternalSiteStorageSystem(
			URI sourceInternalSiteStorageSystem) {
		this.internalSiteStorageSystem = sourceInternalSiteStorageSystem;
	}

	public ProtectionType getProtectionType() {
		return protectionType;
	}

	public void setProtectionType(ProtectionType protectionType) {
		this.protectionType = protectionType;
	}
	
	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}	
	
	public String getRpCopyName() {
        return rpCopyName;
    }

    public void setRpCopyName(String rpCopyName) {
        this.rpCopyName = rpCopyName;
    }
		
	/**
	 * Returns true of the specified internal site is already part of this recommendation.
	 * 
	 * @param destInternalSiteName
	 * @return true if the internal site is found for a target recommendation
	 */
	public boolean containsTargetInternalSiteName(String destInternalSiteName) {
		if (this.getTargetRecommendations() != null) {
		for (RPRecommendation targetRec : this.getTargetRecommendations()) {																
				if (targetRec.getInternalSiteName().equals(destInternalSiteName)) {
						return true;
				}
			}					
		}
		return false;
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
  
        List<RPRecommendation> targetRecs = this.getTargetRecommendations();

    	// Return invalid configuration if there is no primary protection specified.
    	if (targetRecs == null || targetRecs.isEmpty()) {
    		return MetroPointType.INVALID;
    	}
    	
    	for (RPRecommendation targetRec : targetRecs) {                                  	    
    		if (targetRec.getProtectionType() == null) {
    			// If even one protection type is missing, this is not a valid MetroPoint
    			// recommendation.  The protection type is only ever set in 
    			// MetroPoint specific code.
    			return MetroPointType.INVALID;
    		}
    		if (targetRec.getProtectionType() == ProtectionType.LOCAL) {
    			primaryLocalCopyCount++;
    		} else if (targetRec.getProtectionType() == ProtectionType.REMOTE) {
    			primaryRemoteCopyCount++;
    		}
    	}
    	
    	RPRecommendation secondaryRecommendation = null;    	
    	if (this.getHaRecommendation() != null ) {
    		// There will only ever be 1 secondary recommendation in a MetroPoint case.
        	secondaryRecommendation = 
        			this.getHaRecommendation();
        } else {
        	// There must be a secondary recommendation to satisfy a valid MetroPoint
        	// configuration.
        	return MetroPointType.INVALID;
        }
    	    	
        // Return invalid configuration if there is no secondary protection specified.
        if (secondaryRecommendation.getTargetRecommendations().isEmpty()) {
            return MetroPointType.INVALID;
        }
        
        for (RPRecommendation secondaryTargetRec : secondaryRecommendation.getTargetRecommendations()) {                              
    		if (secondaryTargetRec.getProtectionType() == null) {
    			// If even one protection type is missing, this is not a valid MetroPoint
    			// recommendation.  The protection type is only ever set in 
    			// MetroPoint specific code.
    			return MetroPointType.INVALID;
    		}
    		if (secondaryTargetRec.getProtectionType() == ProtectionType.LOCAL) {
    			secondaryLocalCopyCount++;
    		} else if (secondaryTargetRec.getProtectionType() == ProtectionType.REMOTE) {
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
    	    	    	
    	metroPointType = MetroPointType.INVALID;    	
    	if (singleRemoteCopy && primaryLocalCopy && secondaryLocalCopy) {
    		metroPointType = MetroPointType.TWO_LOCAL_REMOTE;
    	} else if (singleRemoteCopy && 
    			((!primaryLocalCopy && secondaryLocalCopy) || (primaryLocalCopy && !secondaryLocalCopy))) {
    		metroPointType = MetroPointType.ONE_LOCAL_REMOTE;
    	} else if (singleRemoteCopy && !primaryLocalCopy && !secondaryLocalCopy) {
    		metroPointType = MetroPointType.SINGLE_REMOTE;
    	} else if (!singleRemoteCopy && primaryLocalCopy && secondaryLocalCopy) {
    		metroPointType = MetroPointType.LOCAL_ONLY;
    	} 
    	
    	return metroPointType;
	}
	
	/**
	 * @param dbClient
	 * @param ps
	 * @return
	 */
	public String toString(DbClient dbClient, ProtectionSystem ps, int... noOfTabs) {
		StringBuffer buff = new StringBuffer();
		final String TAB = "\t";
	
		String printTabs = TAB;		   
    	if (noOfTabs.length> 0 && noOfTabs[0] > 0) {
    		for(int i=0;i<noOfTabs[0];i++) {
    			printTabs += TAB;
    		}
    	}
   	    			
		VirtualArray varray = dbClient.queryObject(VirtualArray.class, getVirtualArray());
		VirtualPool vpool = getVirtualPool();
		StoragePool storagePool = dbClient.queryObject(StoragePool.class, getSourceStoragePool());		
		StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getSourceStorageSystem());
		buff.append(String.format("%n"));
		buff.append(printTabs + String.format("Resource Count	: %s %n", this.getResourceCount()));
		String siteName = ((ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(this.getInternalSiteName()) : "");	
		String siteId =  this.getInternalSiteName();
		if (this.getInternalSiteName() == null) {
			siteName = "(no RP protection specified)";
			siteId = "";
		}
		buff.append(printTabs + String.format("Internal Site	: %s %s %n", siteName, siteId));
		buff.append(printTabs + String.format("RP Copy Name     : %s %n", 
		        (this.getRpCopyName() == null ? "Not determined yet" : this.getRpCopyName())));
		buff.append(printTabs + String.format("Virtual Array 	: %s %n", varray.getLabel()));
		buff.append(printTabs + String.format("Virtual Pool  	: %s %n", vpool.getLabel()));
		if (virtualVolumeRecommendation != null && virtualVolumeRecommendation.getVPlexStorageSystem() != null) {
			StorageSystem vplexStorageSystem = dbClient.queryObject(StorageSystem.class, 
					virtualVolumeRecommendation.getVPlexStorageSystem());
			buff.append(printTabs + String.format("VPLEX Storage	: %s %n", vplexStorageSystem.getLabel()));
		}
		buff.append(printTabs + String.format("Storage Pool 	: %s %n", storagePool.getLabel()));
		buff.append(printTabs + String.format("Storage System	: %s %n", storageSystem.getLabel()));
		buff.append(printTabs + String.format("Resource Size	: %s GB %n", SizeUtil.translateSize(this.getSize(), SizeUtil.SIZE_GB)));
		buff.append(String.format("----------------------%n"));

		if (this.getHaRecommendation() != null) {
			buff.append(printTabs + String.format("High Availability Recommendation : %n"));
			buff.append(getHaRecommendation().toString(dbClient, ps, 1));
			if (this.getHaRecommendation().getTargetRecommendations() != null && 
						!this.getHaRecommendation().getTargetRecommendations().isEmpty()){
				buff.append(printTabs + String.format("High Availability Target : %n"));
				for(RPRecommendation haTargetRec : this.getHaRecommendation().getTargetRecommendations()) {
					buff.append(String.format("%s", haTargetRec.toString(dbClient, ps, 1)));
				}
			}
		}
		return buff.toString();
	}
}
