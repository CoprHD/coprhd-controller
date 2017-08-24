/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

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
    private URI vpoolChangeNewVpool;          
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
    	this.vpoolChangeNewVpool = copy.getVpoolChangeNewVpool();    	
    	
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

    public void setVpoolChangeNewVpool(URI id) {
        vpoolChangeNewVpool = id;
    }

    public URI getVpoolChangeNewVpool() {
        return vpoolChangeNewVpool;
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
    	
    	if (getSourceJournalRecommendation() != null && 
    			getSourceJournalRecommendation().getInternalSiteName().equals(internalSiteName)) {
    		count += getSourceJournalRecommendation().getResourceCount();
    	}
    	
    	if (getStandbyJournalRecommendation() != null && 
    			getStandbyJournalRecommendation().getInternalSiteName().equals(internalSiteName)) {
    		count += getStandbyJournalRecommendation().getResourceCount();
    	}
    	
    	for(RPRecommendation targetJournalRecommendation : getTargetJournalRecommendations())
    	if ( targetJournalRecommendation != null && targetJournalRecommendation.getInternalSiteName().equals(internalSiteName)) {
    		count += targetJournalRecommendation.getResourceCount();
    	}

    	return count;
    }
	
	
    /** Returns list of already determined recommendations. 
     * @return - List of recommendations
     */
    public List<RPRecommendation> getPoolsInAllRecommendations()  {
    	List<RPRecommendation> poolsAlreadyInRecommendation = new ArrayList<RPRecommendation>();
    	
    	poolsAlreadyInRecommendation.addAll(getJournalPoolsInRecommendation());
  
    	List<RPRecommendation> sourcePoolsInRecommendation = getSourcePoolsInRecommendation();
    	if (!sourcePoolsInRecommendation.isEmpty()) {
    		poolsAlreadyInRecommendation.addAll(getSourcePoolsInRecommendation());
    	}
    	
    	List<RPRecommendation> targetPoolsInRecommendation = getTargetPoolsInRecommendation();
    	if (!targetPoolsInRecommendation.isEmpty()) {
    		poolsAlreadyInRecommendation.addAll(targetPoolsInRecommendation);
    	}
   	    
    	return poolsAlreadyInRecommendation;
    }
    
    /**
     * Returns all recommendations corresponding to RP journals.   
     * @return - List of recommendations
     */
    public List<RPRecommendation> getJournalPoolsInRecommendation() {
    	List<RPRecommendation> journalRecs = new ArrayList<RPRecommendation>();
    	
    	if (getSourceJournalPoolsInRecommendation() != null) {
    		journalRecs.add(getSourceJournalPoolsInRecommendation());
    	}
    	
    	if (getStandbyJournalPoolsInRecommendation() != null) {
    		journalRecs.add(getStandbyJournalPoolsInRecommendation());    		
    	}
    	
    	List<RPRecommendation> targetJournalRecs = getTargetJournalPoolsInRecommendation();
    	if (null != targetJournalRecs && !targetJournalRecs.isEmpty()) {
    			journalRecs.addAll(targetJournalRecs);
    	}  
    	
    	return journalRecs;
    }

    /**
     * Returns all recommendations corresponding to RP source journals.
     * @return - List of recommendations
     */
	public RPRecommendation getSourceJournalPoolsInRecommendation() {
		if (this.getSourceJournalRecommendation() != null) {
    		return this.getSourceJournalRecommendation();
    	}
		return null;
	}
	 /**
     * Returns all recommendations corresponding to RP stand-by journals, applies to only Metropoint.
     * @return - List of recommendations
     */
	public RPRecommendation getStandbyJournalPoolsInRecommendation() {
		if (this.getStandbyJournalRecommendation() != null) {
    		return this.getStandbyJournalRecommendation();
    	}
		return null;
	}
	
	 /**
     * Returns all recommendations corresponding to RP source.
     * @return - List of recommendations
     */
	public List<RPRecommendation> getSourcePoolsInRecommendation() {
		List<RPRecommendation> sourcePoolsInRecommendation = new ArrayList<RPRecommendation>();
		if (this.getSourceRecommendations() != null) {
    		for(RPRecommendation srcRec : this.getSourceRecommendations()){
    			sourcePoolsInRecommendation.add(srcRec);
    			if (srcRec.getHaRecommendation() != null) {
    				sourcePoolsInRecommendation.add(srcRec.getHaRecommendation());
    			}
    		}
    	}
		return sourcePoolsInRecommendation;
	}
	
	 /**
     * Returns all recommendations corresponding to RP target journals.
     * @return - List of recommendations
     */
	public List<RPRecommendation> getTargetJournalPoolsInRecommendation() {
		List<RPRecommendation> tgtJrnlPoolsInRecommendation = new ArrayList<RPRecommendation>();
		if (this.getTargetJournalRecommendations() != null) {
    		for(RPRecommendation tgtJrnlRec : this.getTargetJournalRecommendations()){
    			tgtJrnlPoolsInRecommendation.add(tgtJrnlRec);
    		}
    	}
		return tgtJrnlPoolsInRecommendation;
	}
	
	 /**
     * Returns all recommendations corresponding to RP targets.
     * @return - List of recommendations
     */
	public List<RPRecommendation> getTargetPoolsInRecommendation() {
		List<RPRecommendation> targetPoolsInRecommendation = new ArrayList<RPRecommendation>();
		if (this.getSourceRecommendations() != null) {
    		for(RPRecommendation srcRec : this.getSourceRecommendations()){
    			if (srcRec.getTargetRecommendations() != null) {
	    			for(RPRecommendation tgtRec : srcRec.getTargetRecommendations()) {
	    				targetPoolsInRecommendation.add(tgtRec);
	    			}
    			}
    		}
    	}
		return targetPoolsInRecommendation;
	}        
    
    /**
     * @param dbClient
     * @return
     */
    public String toString(DbClient dbClient) {
    	    	
    	StringBuffer buff = new StringBuffer(String.format("%nRecoverPoint Placement Results : %n")); 
    	buff.append(String.format("--------------------------------------%n"));

    	RPRecommendation rpRecommendation = this.getSourceRecommendations().get(0);    	
    	String protectionType = "Regular RP recommendation";
    	if (VirtualPool.vPoolSpecifiesMetroPoint(rpRecommendation.getVirtualPool())) {
    		protectionType = "Metropoint RP recommendation";
    	} else if(VirtualPool.vPoolSpecifiesRPVPlex(rpRecommendation.getVirtualPool())) {
    		protectionType = "RP/VPLEX recommendation";
    	}
    	
    	buff.append(String.format(protectionType + "%n"));
    	ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, getProtectionDevice());
    	buff.append(String.format("Total volumes placed : %s %n", + this.getResourceCount()));
    	buff.append(String.format("Protection System : %s %n%n", ps.getLabel()));
   
    	for (RPRecommendation sourceRecommendation : this.getSourceRecommendations()) {	    	
    		buff.append(String.format("Source Recommendation : %n"));    		    		
    		buff.append(String.format("%s %n",sourceRecommendation.toString(dbClient, ps)));
    		for (RPRecommendation targetRecommendation : sourceRecommendation.getTargetRecommendations()) {
    			buff.append(String.format("Target Recommendation : %n"));
    			buff.append(String.format("%s %n", targetRecommendation.toString(dbClient, ps)));
    		}
    	}    
    	buff.append("Journal Recommendation : ");
    	String sourceJournalString = "Source";
    	if (standbyJournalRecommendation != null) {
    		sourceJournalString = "Metropoint Active Source ";
    	}
    	buff.append(String.format("%s %n", sourceJournalString ));
    	if (sourceJournalRecommendation != null) {
    	    buff.append(String.format("%s %n", sourceJournalRecommendation.toString(dbClient, ps)));
    	} else {
    	    buff.append("\tNo Source Journal Recommedation required. Re-use existing Journal(s).\n\n");
    	}
	
    	if (standbyJournalRecommendation != null) {
    		buff.append(String.format("Journal Recommendation : Metropoint Standby Source %n"));
    		buff.append(String.format("%s %n", standbyJournalRecommendation.toString(dbClient, ps)));
    	}
    	
    	buff.append(String.format("Journal Recommendation : Target(s) %n"));
    	if (!CollectionUtils.isEmpty(this.getTargetJournalRecommendations())) {    		
    		for (RPRecommendation targetJournalRecommendation : getTargetJournalRecommendations()) {
    	    	buff.append(String.format("%s", targetJournalRecommendation.toString(dbClient, ps)));    	    	
    		}
    	} else {
            buff.append("\tNo Target Journal Recommedation(s) required. Re-use existing Journal(s).\n\n");
        }
    	
    	buff.append(String.format("--------------------------------------%n"));
    	return buff.toString();
    } 	
}