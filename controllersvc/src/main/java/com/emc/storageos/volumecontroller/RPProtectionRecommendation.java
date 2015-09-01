/**
 * Copyright (c) 2015 EMC Corporation
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
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.VirtualPool;

/**
 * Top level recommendation for RP protection placement
 */
@SuppressWarnings("serial")
public class RPProtectionRecommendation extends Recommendation {
	private URI protectionDevice;

	//RP source recommendation
    List<RPRecommendation> sourceRecommendations;
    
    //Journal recommendations
    private RPRecommendation sourceJournalRecommendation;   
	private RPRecommendation standbyJournalRecommendation;  
    private List<RPRecommendation> targetJournalRecommendations;
          
    private URI vpoolChangeVolume;
    private URI vpoolChangeVpool;          
    private boolean vpoolChangeProtectionAlreadyExists;

    //Placement status
    private PlacementProgress placementStepsCompleted;
    private String protectionSystemCriteriaError;   
        
	public static enum PlacementProgress {
		NONE, IDENTIFIED_SOLUTION_FOR_SOURCE, IDENTIFIED_SOLUTION_FOR_SUBSET_OF_TARGETS, 
		IDENTIFIED_SOLUTION_FOR_ALL_TARGETS, PROTECTION_SYSTEM_CANNOT_FULFILL_REQUEST
	}

    public RPProtectionRecommendation() {    	
    	this.sourceRecommendations = new ArrayList<RPRecommendation>();
    	this.targetJournalRecommendations = new ArrayList<RPRecommendation>();    	
    	placementStepsCompleted = PlacementProgress.NONE;    	
    }
    
    public RPProtectionRecommendation(RPProtectionRecommendation copy) {
    	this.setSourceStoragePool(copy.getSourceStoragePool());
    	this.setSourceStorageSystem(copy.getSourceStorageSystem());
    	this.setDeviceType(copy.getDeviceType());
    	this.setResourceCount(copy.getResourceCount());
    	this.placementStepsCompleted = copy.getPlacementStepsCompleted();
    	this.protectionDevice = copy.getProtectionDevice();
    	this.sourceJournalRecommendation = copy.getSourceJournalRecommendation();
    	this.setStandbyJournalRecommendation(copy.getStandbyJournalRecommendation());
    	this.vpoolChangeVolume = copy.getVpoolChangeVolume();
    	this.vpoolChangeVpool = copy.getVpoolChangeVpool();    	
    	
    	this.sourceRecommendations = new ArrayList<RPRecommendation>();
    	this.getSourceRecommendations().addAll(copy.getSourceRecommendations());    	
    	this.targetJournalRecommendations = new ArrayList<RPRecommendation>();
    	this.getTargetJournalRecommendations().addAll(copy.getTargetJournalRecommendations());
   	}
    
    public RPRecommendation getSourceJournalRecommendation() {
		return sourceJournalRecommendation;
	}

	public void setSourceJournalRecommendation(
			RPRecommendation sourceJournalRecommendation) {
		this.sourceJournalRecommendation = sourceJournalRecommendation;
	}

	public List<RPRecommendation> getSourceRecommendations() {
		return sourceRecommendations;
	}

	public void setSourceRecommendations(List<RPRecommendation> sourceRecommendations) {
		this.sourceRecommendations = sourceRecommendations;
	}
    
    public List<RPRecommendation> getTargetJournalRecommendations() {
		return targetJournalRecommendations;
	}

	public void setTargetJournalRecommendations(
			List<RPRecommendation> targetJournalRecommendations) {
		this.targetJournalRecommendations = targetJournalRecommendations;
	}

	public RPRecommendation getStandbyJournalRecommendation() {
		return standbyJournalRecommendation;
	}

	public void setStandbyJournalRecommendation(
			RPRecommendation standbyJournalRecommendation) {
		this.standbyJournalRecommendation = standbyJournalRecommendation;
	}
	
    public URI getProtectionDevice() {
        return protectionDevice;
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

    public boolean isVpoolChangeProtectionAlreadyExists() {
        return vpoolChangeProtectionAlreadyExists;
    }

    public void setVpoolChangeProtectionAlreadyExists(
            boolean vpoolChangeProtectionAlreadyExists) {
        this.vpoolChangeProtectionAlreadyExists = vpoolChangeProtectionAlreadyExists;
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
    	for (RPRecommendation rpSourceRecommendation: getSourceRecommendations()) {    		
    		if (rpSourceRecommendation.getInternalSiteName().equals(internalSiteName)) {
    			count += rpSourceRecommendation.getResourceCount();
    			continue;
    		}
    		
    		for(RPRecommendation targetRec : rpSourceRecommendation.getTargetRecommendations()) {
    			if (targetRec.getInternalSiteName().equals(internalSiteName)) {
    				count += targetRec.getResourceCount();
    			}
    		}    		
    	}
    	
    	if (getSourceJournalRecommendation() != null && getSourceJournalRecommendation().getInternalSiteName().equals(internalSiteName)) {
    		count += getSourceJournalRecommendation().getResourceCount();
    	}
    	
    	if (getStandbyJournalRecommendation() != null && getStandbyJournalRecommendation().getInternalSiteName().equals(internalSiteName)) {
    		count += getStandbyJournalRecommendation().getResourceCount();
    	}
    	
    	for(RPRecommendation targetJournalRecommendation : getTargetJournalRecommendations())
    	if ( targetJournalRecommendation != null && targetJournalRecommendation.getInternalSiteName().equals(internalSiteName)) {
    		count += targetJournalRecommendation.getResourceCount();
    	}

    	return count;
    }
    
    /**
     * @param dbClient
     * @return
     */
    public String toString(DbClient dbClient) {
    	    	
    	StringBuffer buff = new StringBuffer("%nRecoverPoint Placement Results : %n"); 
    	buff.append("--------------------------------------%n");

    	RPRecommendation rpRecommendation = this.getSourceRecommendations().get(0);    	
    	String protectionType = "Regular RP recommendation";
    	if (VirtualPool.vPoolSpecifiesMetroPoint(rpRecommendation.getVirtualPool())) {
    		protectionType = "Metropoint RP recommendation";
    	} else if(VirtualPool.vPoolSpecifiesRPVPlex(rpRecommendation.getVirtualPool())) {
    		protectionType = "RP/VPLEX recommendation";
    	}
    	
    	buff.append(protectionType + "%n");
    	ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, getProtectionDevice());
    	buff.append("Total volumes placed : " + this.getResourceCount() + "%n");
    	buff.append("Protection System : " + ps.getLabel() + "%n%n");
   
    	for (RPRecommendation sourceRecommendation : this.getSourceRecommendations()) {	    	
    		buff.append("Source Recommendation : " + "%n");    		    		
    		buff.append(sourceRecommendation.toString(dbClient, ps) + "%n");
    		for (RPRecommendation targetRecommendation : sourceRecommendation.getTargetRecommendations()) {
    			buff.append("Target Recommendation : " + "%n");
    			buff.append(targetRecommendation.toString(dbClient, ps) + "%n");
    		}
    	}    
    	buff.append("Journal Recommendation : ");
    	String sourceJournalString = "Source";
    	if (standbyJournalRecommendation != null) {
    		sourceJournalString = "Metropoint Active Source";
    	}
    	buff.append(sourceJournalString + " : "+ "%n");
    	buff.append(sourceJournalRecommendation.toString(dbClient, ps) + "%n");
	
    	if (standbyJournalRecommendation != null) {
    		buff.append("%nJournal Recommendation	: Metropoint Standby Source" + "%n");
    		buff.append(standbyJournalRecommendation.toString(dbClient, ps) + "%n");
    	}
    	
    	buff.append("%n");
    	buff.append("Journal Recommendation : Target" + "%n");
    	if (this.getTargetJournalRecommendations() != null) {
    		buff.append("Journals : " + "%n");
    		for (RPRecommendation targetJournalRecommendation : getTargetJournalRecommendations()) {
    	    	buff.append(targetJournalRecommendation.toString(dbClient, ps) + "%n");    	    	
    		}
    	}
    	
    	buff.append("--------------------------------------%n");
    	return buff.toString();
    } 	
}


