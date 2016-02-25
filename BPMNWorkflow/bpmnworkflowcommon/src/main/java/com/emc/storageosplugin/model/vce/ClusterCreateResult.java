package com.emc.storageosplugin.model.vce;



public class ClusterCreateResult extends OperationResult {


	String vdcId;
	String tenantId;
	String projectId;
	
	String clusterId;
	String clusterName;



	public ClusterCreateResult() {
		this.vdcId=null;
		this.tenantId=null;
		this.projectId=null;
		this.clusterId=null;
		this.clusterName=null;	
		
		this.setSuccess(false);
		this.setMsg("Creating cluser operation failed");
		
	}

	
	public ClusterCreateResult(String vdcId,  String tenantId, String projectId, String clusterId, String clusterName) {
		this.vdcId=vdcId;
		this.tenantId=tenantId;
		this.projectId=projectId;
		this.clusterId=clusterId;
		this.clusterName=clusterName;
				
	}

	public String getVdcId() {
		return vdcId;
	}


	public void setVdcId(String vdcId) {
		this.vdcId = vdcId;
	}


	public String getTenantId() {
		return tenantId;
	}


	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}


	public String getProjectId() {
		return projectId;
	}


	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}


	public String getClusterId() {
		return clusterId;
	}


	public void setClusterId(String clusterId) {
		this.clusterId = clusterId;
	}


	public String getClusterName() {
		return clusterName;
	}


	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}


}
