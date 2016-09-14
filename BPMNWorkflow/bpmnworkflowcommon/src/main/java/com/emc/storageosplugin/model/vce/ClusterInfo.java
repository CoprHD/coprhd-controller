package com.emc.storageosplugin.model.vce;

import java.net.URI;

import com.emc.storageos.model.host.cluster.ClusterRestRep;

public class ClusterInfo extends ViPRObjectInfo{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	URI vdcId;
	URI tenantId;
	URI projectId;
	
	public ClusterInfo(String name, String id ) {
		super(name, id);
		
	}

	public ClusterInfo(ClusterRestRep cluster) {
		super(cluster.getName(),cluster.getId().toString());
		if (cluster.getId() != null) {

			if (cluster.getProject() != null)
				setProjectId(cluster.getProject().getId());
			if (cluster.getTenant() != null)
				setTenantId(cluster.getTenant().getId());
			if (cluster.getVcenterDataCenter() != null)
				setVdcId(cluster.getVcenterDataCenter().getId());
		}
	}



	public URI getVdcId() {
		return vdcId;
	}



	public void setVdcId(URI vdcId) {
		this.vdcId = vdcId;
	}



	public URI getTenantId() {
		return tenantId;
	}



	public void setTenantId(URI tenantId) {
		this.tenantId = tenantId;
	}



	public URI getProjectId() {
		return projectId;
	}



	public void setProjectId(URI projectId) {
		this.projectId = projectId;
	}

	
	
	

}
