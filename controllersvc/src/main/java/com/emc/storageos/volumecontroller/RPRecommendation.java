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
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.Protection.ProtectionType;

@SuppressWarnings("serial")
public class RPRecommendation extends Recommendation { 
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
		
	/**
	 * @param destInternalSiteName
	 * @return
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
	 * @param dbClient
	 * @param ps
	 * @return
	 */
	public String toString(DbClient dbClient, ProtectionSystem ps) {
		StringBuffer buff = new StringBuffer();
		final String SPACE = " ";
		VirtualArray varray = dbClient.queryObject(VirtualArray.class, getVirtualArray());
		VirtualPool vpool = getVirtualPool();
		StoragePool storagePool = dbClient.queryObject(StoragePool.class, getSourceStoragePool());
		StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getSourceStorageSystem());		
		buff.append("\tResource Count	: " + this.getResourceCount() + "\n");
		String siteName = ((ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(this.getInternalSiteName()) : "");
		buff.append("\tInternal Site	: " + siteName + SPACE + this.getInternalSiteName() + "\n");
		buff.append("\tVirtual Array 	: " + varray.getLabel() + "\n");
		buff.append("\tVirtual Pool  	: " + vpool.getLabel() + "\n");
		if (virtualVolumeRecommendation != null && virtualVolumeRecommendation.getVPlexStorageSystem() != null) {
			StorageSystem vplexStorageSystem = dbClient.queryObject(StorageSystem.class, virtualVolumeRecommendation.getVPlexStorageSystem());
			buff.append("\tVPLEX Storage	: " + vplexStorageSystem.getLabel() + "\n");
		}
		buff.append("\tStorage Pool 	: " + storagePool.getLabel() + "\n");
		buff.append("\tStorage System	: " + storageSystem.getLabel() + "\n");				
		buff.append("----------------------\n");
		
		if (this.getHaRecommendation() != null) {
			buff.append("\tHigh Availability Recommendation :" + "\n");
			buff.append(getHaRecommendation().toString(dbClient, ps));
			buff.append("----------------------\n");
			if (this.getHaRecommendation().getTargetRecommendations() != null && !this.getHaRecommendation().getTargetRecommendations().isEmpty()){
				buff.append("\tHA target :" + "\n");
				buff.append(this.getHaRecommendation().getTargetRecommendations().get(0).toString(dbClient, ps));
				buff.append("----------------------\n");
			}
		}
		return buff.toString();
	}	
}
