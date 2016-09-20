package com.emc.storageosplugin.model.vce;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageosplugin.model.vce.AuthenticationSession;
//import com.emc.storageosplugin.model.ViPRClientFactory;
import com.emc.storageosplugin.model.vce.ClusterCreateResult;
import com.emc.storageosplugin.model.vce.ClusterInfo;
import com.emc.storageosplugin.model.vce.VCEProvisionHelper;
import com.emc.storageosplugin.model.vce.ViPRClientUtils;
//import com.emc.storageosplugin.model.vce.AuthenticationSession;





import com.emc.vipr.client.Task;







//import static com.emc.storageosplugin.model.vce.AuthenticationSession.getViprClient;
import static com.emc.storageosplugin.model.vce.AuthenticationSession.startSession;
import static com.emc.storageosplugin.model.vce.AuthenticationSession.isSessionActive;
import static com.emc.storageosplugin.model.vce.AuthenticationSession.endSession;

public class TestWorkflowAPI {
	

	    /*
	     isActive = AuthenticationSession.isSessionActive();

System.log("ViPR Client Session is "+isActive );

if (isActive == false) {
	AuthenticationSession.startSession(host,parseInt(port), userName, password);
}

isActive = AuthenticationSession.isSessionActive();

System.log("ViPR Client Session is "+isActive );

return isActive;
	     */
	    
	    public static void main(String[] args) throws Exception {
	    	
	    	
	    	//String host="lglbv239.lss.emc.com";
	    	//Integer port=4443;
	    	//String userName="manoj@cli.vipr"; 
	    	//String userName="root"; 
	    	//String userPassword="Dangerous@123";
			//ViPRClientFactory.getViprClient(host,  port, userName, userPassword);
	    	
	    	
//	    	String host="lglbv239.lss.emc.com";
//	    	Integer port=4443;
//	    	String userName="root"; 
//	    	String userPassword="Dangerous@123";
	    	
	    	String host="10.247.142.203";
	    	Integer port=4443;
	    	String userName="root"; 
	    	String userPassword="ChangeMe1!";
	    	
	    	boolean isActive = AuthenticationSession.isSessionActive();
	    	System.out.println("ViPR Client Session is "+isActive );
	    	
	    	if (!isActive){
	    		AuthenticationSession.startSession(host, port, userName, userPassword);
	    	}

	    	isActive = AuthenticationSession.isSessionActive();
	    	System.out.println("ViPR Client Session is "+isActive );
	    	
//			
//	    	 TenantInfo[] tenantInfos = new VCEProvisionHelper().getUserTenants();
//	    	 
//	    	 for(int i=0 ; i < tenantInfos.length ; i++){
//	    		 System.out.println(tenantInfos[i].toString());
//	    	 }
//	    	
//			
//			List<TenantOrgRestRep> tenants = ViPRClientUtils.getUserTenants();
//			
//			for (TenantOrgRestRep tenant : tenants){
//				
//
//				System.out.println("************tenant "+tenant.getName()+tenant.getId());
//				
//				
//			}
//		
//	
//			
//			
//
//			List<ProjectRestRep> projects = ViPRClientUtils.getProjectsByUserTenant();
//			for (ProjectRestRep project : projects){
//				
//				System.out.println("**************project "+project.getName() + project.getId());
//				
//			}
//			
//			List<VirtualArrayRestRep> varrays = ViPRClientUtils.getVirtualArraysForTenant();
//			
//			for (VirtualArrayRestRep varray : varrays){
//				System.out.println("***********varray "+varray.getName() +varray.getId());
//				
//				
//			}
//			
//			List<BlockVirtualPoolRestRep> blockVpools = ViPRClientUtils.getBlockVirtualPools();
//			
//			for (BlockVirtualPoolRestRep blockVpool : blockVpools){
//				System.out.println("***********blockVpool "+blockVpool.getName() +blockVpool.getId());
//				
//			}
//
//			List<FileVirtualPoolRestRep> fileVpools = ViPRClientUtils.getFileVirtualPools();
//
//			for (FileVirtualPoolRestRep fileVpool : fileVpools){
//				System.out.println("***********fileVpool "+fileVpool.getName() +fileVpool.getId());
//			}
// 	
//			URI tenantURI=ViPRClientUtils.getUserTenant().getId();
//			
//			System.out.println("MANOJ tenantURI is "+tenantURI.toString());
//			
//			
//			TenantInfo tenantInfo = new VCEProvisionHelper().getUserTenants()[0];
//			 
//			tenantURI = tenantInfo.getId();
//
//			 ClusterInfo cluster = new VCEProvisionHelper().createCluster(tenantURI, "VCECluster3");
//			
//			System.out.println("***********createCluster "+cluster.getId());
//			
			
//			ArrayList<URI> volumeIds = new ArrayList<URI>(   Arrays.asList(new URI("urn:storageos:Volume:adcdfc8d-a8e3-4b0b-865a-d0450d93353b:vdc1") ));
//			URI hostUri= new URI("urn:storageos:Host:37c7e322-dede-4252-abe8-4c102802d43f:vdc1");
//			Task<ExportGroupRestRep> export = ViPRClientUtils.createHostOrClusterExport(varrayId,projectId, volumeIds ,-1,false,hostUri);
		

	    	
	    	String tenantId = ("urn:storageos:TenantOrg:f1838af8-67db-49ab-873f-4802dac98391:global"); 

	        String projectId = ("urn:storageos:Project:d1ebe4ef-b7a0-4ae2-a923-11819acd7476:global"); 

	        String varrayId = ("urn:storageos:VirtualArray:49845148-1bef-42ad-943d-a98fcf1e39bc:vdc1"); 

	        String blockVpoolId = ("urn:storageos:VirtualPool:b0e5e75f-d0df-4186-82a5-ccaa361d0dfc:vdc1");

	        
//	    	
//	    	String tenantId= "urn:storageos:TenantOrg:fea5951f-1d80-45d4-bae3-1da3b4616391:global";
//			
//
//			String projectId = ( "urn:storageos:Project:85e6dead-79af-4f88-b22c-877ef4f6875f:global");
//
//			String varrayId = ("urn:storageos:VirtualArray:7e394bb0-00d0-4ae3-b51a-656571e53f81:vdc1");
//
//
//			String blockVpoolId = ("urn:storageos:VirtualPool:db533e71-fb95-421f-b6e7-b7099bdf4baa:vdc1");
			
			String volName ="MANOJ-VOL-EXP-2";
			
			String volSize=String.valueOf( (Double) (2.0*1024*1024*1024));
			String count = "1";
			
			String consistencyGroupId = null;
			
			VolumeCreationResult[] volResults = new VCEProvisionHelper().createVolumes(varrayId, projectId, blockVpoolId, volName, volSize, count, consistencyGroupId);
			
			
			
//			String hostOrClusterId="urn:storageos:Cluster:0afc607c-fe83-420c-93e2-b00957df3408:vdc1";
//			String isCluster = "true";
//			String hlu= "-1";
//			String[] volumeIds = new String[volResults.length];
//			
//			for (int i=0; i<volResults.length; i++) {
//				volumeIds[i]=volResults[i].getVolId();
//			}
//			
//			VolumeExportResult[] export = new VCEProvisionHelper().createHostOrClusterExport(varrayId, projectId, volumeIds, hlu, isCluster, hostOrClusterId);
//			
//			System.out.println("Volume exported to host "+export);
			

			
			
			endSession();
		}
}
