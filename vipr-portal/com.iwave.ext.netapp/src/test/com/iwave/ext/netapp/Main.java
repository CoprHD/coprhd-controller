/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.utils.ExportRule;

import netapp.manage.NaElement;
import netapp.manage.NaServer;


public class Main {

	static private NetAppFacade netAppFacade = null;
	private static Server server = null;
	private NaServer server2 = null;
	
	public static void main(String[] args) {
		
	    String arrayIp = null;
	    int arrayPort = 443;
	    String arrayUser = null, arrayPassword = null;
		
	    if(args.length == 3) {
		    arrayIp = args[0];
		    arrayUser = args[1];
		    arrayPassword = args[2];
	    }
		System.out.println("Entering OnTap Test Client");
		netAppFacade =  new NetAppFacade(arrayIp, arrayPort, arrayUser, arrayPassword, true);
       
		/*List<ExportsRuleInfo> exports = netAppFacade.listNFSExportRules("/vol/GOPINETAPP");
        for (ExportsRuleInfo export : exports) {
            String filesystem = export.getPathname();
        }*/
        
     
        
        //List<AggregateInfo> temp2 =netAppFacade.listAggregates(null);
        //Step1:List aggregate(storage groups)
//        Aggregate aggr = new Aggregate(server.getNaServer(), "name");
//		List<AggregateInfo> temp =  aggr.listAllAggregates(true);
//        List<String> volumes =  netAppFacade.listVolumes();
        
        Map<String, String> value = netAppFacade.systemInfo();
       // Boolean status1 = createFS("rajaFS2", "aggr0", "25m");
       
        String exportPath = "/vol/GOPI_INGESTION_TEST";
        List<ExportRule> exportRules = new ArrayList<ExportRule>();
        
        ExportRule exportRule1 = new ExportRule();
        exportRule1.setAnon("0");
        Set<String> readOnlyHosts = new HashSet<String>();
        readOnlyHosts.add("hostReadOnly");
        exportRule1.setReadOnlyHosts(readOnlyHosts);
        exportRule1.setSecFlavor("sys");
        
        ExportRule exportRule3 = new ExportRule();
        exportRule3.setAnon("0");
        readOnlyHosts = new HashSet<String>();
        readOnlyHosts.add("hostReadOnlyOT");
        exportRule3.setReadOnlyHosts(readOnlyHosts);
        exportRule3.setSecFlavor("sys");
        
        
        ExportRule exportRule2 = new ExportRule();
        exportRule2.setAnon("root");
        Set<String> readWriteHosts = new HashSet<String>();
        readWriteHosts.add("hostWriteOnly");
        exportRule2.setReadWriteHosts(readWriteHosts);
        exportRule2.setSecFlavor("sys");

        ExportRule rule = new ExportRule();
		rule.setAnon("root");
		Set<String>  rwHosts = new HashSet<>();
		rwHosts.add("127.0.0.1");
		rule.setReadWriteHosts(rwHosts);
		rule.setSecFlavor("sys");
		
		//exportRules.add(rule);
        
        //exportRules.add(exportRule1);
        //exportRules.add(exportRule2);
        //exportRules.add(exportRule3);
        
        
        
        //deleteExportRules(exportPath);
        //exportNewFS(exportPath, exportRules);
		try{
			//exportModifyFS(exportPath, exportRules);
			deleteNFSExport(exportPath);
		}
		catch(Exception e){
			e.printStackTrace();
		}
        
        
//        List<String> endpointsList = new ArrayList<String>();
//        endpointsList.add("");
//        
//        Boolean status2 = exportFS(endpointsList, "rw", "6553", "rajaFS", NFSSecurityStyle.sys.toString());
//        
        
        //List<String> endpointsList2 = null;
        //Boolean status3 = unexportFS(endpointsList2, "null", "root", "rajaFS");
        //Boolean status4 = deleteFS("rajaFS");
     
	}

public static Boolean createVolume(String volName, String aggregate, String size){
	Boolean status = netAppFacade.createFlexibleVolume(volName, aggregate, false, null, size, null, "file", false, null);
	return status;
}

public static Boolean createFS(String fsName, String aggregate, String size){

   Boolean SuccessStatus = false;
   try {
	   
	    boolean createVolStatus = createVolume(fsName, aggregate,size);
	    if(createVolStatus){	    	
		    //Create an NFS file-share
	        System.out.println("");
	        System.out.println("Adding NFS share...");
	        String mountPath = "/vol" + "/" + fsName; 
	        String exportPath="/" + fsName;
	      	int anonymousUid = -1;
	      	List<String> roHosts = new ArrayList<String>();
			roHosts.add("127.0.0.1");
	      	List<String> rwHosts = new ArrayList<String>();
	      	List<String> rootHosts = new ArrayList<String>();
	      	boolean roAddAll = false;;
	      	boolean rwAddAll = false;
	      	boolean rootAddAll = false;
	      	List<NFSSecurityStyle> securityStyle = new ArrayList<NFSSecurityStyle>();	       
	        securityStyle.add(NFSSecurityStyle.sys);
	        List<String> share = 
	        netAppFacade.addNFSShare(mountPath, exportPath, anonymousUid, roHosts, 
	        		roAddAll, rwHosts, rwAddAll, rootHosts, rootAddAll, securityStyle);
	        System.out.println("Shares: ");
		    if(share.isEmpty()) {
		        return SuccessStatus;
		    }
	   }
	   else{
		   System.out.println("FS creation failed...");
		   return SuccessStatus;
	   }   
   } catch (Exception e){
	   System.out.println("FS creation failed...");
	   String exception = "myexception";
   }
   
   return SuccessStatus;
}


public static Boolean exportFS(List<String> endpointsList, String permissions, String root_user, String fsName, String securityStyle) {
	
	if(endpointsList == null){
		System.out.println("End points list is null...");
		return false; 
	}
	else{	
		List<String> roHosts = new ArrayList<String>();
		List<String> rwHosts = new ArrayList<String>();
		List<String> rootHosts = new ArrayList<String>();
		List<NFSSecurityStyle> secruityStyleList = new ArrayList<NFSSecurityStyle>();
		secruityStyleList.add(NFSSecurityStyle.valueOfLabel(securityStyle));
		boolean roAddAll=false;
		boolean rwAddAll=false;
		boolean rootAddAll=false;
		//TODO: Should we preserve existing exports???
		// TODO: Should we preserve existing exports???
        for (int endpointCount = 0; endpointCount < endpointsList
                .size(); endpointCount++) {
            if ((null == permissions) || (permissions.contains("rw"))) {
                rwHosts.add(endpointsList.get(endpointCount));
            } else if((null == permissions) || (permissions.contains("ro"))){
                roHosts.add(endpointsList.get(endpointCount));
            } else { 
                rootHosts.add(endpointsList.get(endpointCount));
            }
        }

        if (roHosts.isEmpty()) {
            roHosts = null;
        }

        if (rwHosts.isEmpty()) {
            rwHosts = null;
        }
        
        int rootMappingUid = 0;
        if(root_user.equals("root")){
        	rootMappingUid = 0;
        } else if (root_user.equals("nobody")) {
        	rootMappingUid = 65535;
    	} else {
    		//If UID is specified other than root or nobody default it to this value.
    		rootMappingUid = 65534;
    	}
		
		String mountPath = "/vol"+"/"+fsName;
		String exportPath = "/" + fsName;
		
		List<String> FsList = netAppFacade.addNFSShare(mountPath, exportPath, rootMappingUid, roHosts, roAddAll, rwHosts, rwAddAll, rootHosts, rootAddAll, secruityStyleList);
		if(FsList.isEmpty()) {
		        return false;
		}
		return true;
	}	
}

public static Boolean deleteExportRules(String exportPath) {
	List<String> FsList = netAppFacade.deleteNFSShare(exportPath, false);
	if(FsList.isEmpty()) {
	        return false;
	}
	return true;
}

public static Boolean exportNewFS(String exportPath, List<ExportRule> exportRules) {
	List<String> FsList = netAppFacade.addNewNFSShare(exportPath, exportRules);
	if(FsList.isEmpty()) {
	        return false;
	}
	return true;
}


public static Boolean exportModifyFS(String exportPath, List<ExportRule> exportRules) {
	List<String> FsList = netAppFacade.modifyNFSShare(exportPath, exportRules);
	if(FsList.isEmpty()) {
	        return false;
	}
	return true;
}

public static Boolean deleteNFSExport(String exportPath) {
	List<String> FsList = netAppFacade.deleteNFSShare(exportPath, false);
	if(FsList.isEmpty()) {
	        return false;
	}
	return true;
}

public static Boolean unexportFS(List<String> endpointsList, String permissions, String root_user, String fsName){
	
	  System.out.println("");
      System.out.println("Adding NFS share...");
      String mountPath = "/vol" + "/" + fsName; 
      String exportPath="/" + fsName;
      int anonymousUid = -1;
      List<String> roHosts = new ArrayList<String>();
	  roHosts.add("127.0.0.1");
      List<String> rwHosts = new ArrayList<String>();
      List<String> rootHosts = new ArrayList<String>();
      boolean roAddAll = false;;
      boolean rwAddAll = false;
      boolean rootAddAll = false;
      List<NFSSecurityStyle> securityStyle = new ArrayList<NFSSecurityStyle>();	       
      securityStyle.add(NFSSecurityStyle.sys);
		
	  List<String> FsList = netAppFacade.addNFSShare(mountPath, exportPath, -1, roHosts, roAddAll, rwHosts, rwAddAll, rootHosts, rootAddAll, securityStyle);
	  if(FsList.isEmpty()) {
		        return false;
	  }
	  return true;
		
}

public static Boolean deleteFS(String volName){
	String exportPath = "/" + volName;
	List<String> deletedPaths = netAppFacade.deleteNFSShare(exportPath, false);
	netAppFacade.setVolumeOffline(volName, 1);
	netAppFacade.destroyVolume(volName, true);
    return false;
}

}
	