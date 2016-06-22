/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexdbckr;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexResourceInfo;
import com.emc.storageos.vplex.api.VPlexStorageVolumeInfo;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.vplex.api.VPlexStorageViewInfo;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.URIUtil;

/**
 * A single bean instance of this class is started from the Spring configuration.
 * The various static variables such as dbClient and vplexApiFactory are injected by Spring.
 * The public constructor saves the instance so it can be returned by a static method.
 * 
 * VplexDBCkr provides methods for accessing the database, the vplex, and performing the
 * required checks.
 *
 */
public class VplexDBCkr {
    Logger log = LoggerFactory.getLogger(VplexDBCkr.class);
    private static VplexDBCkr bean = null;
    private static DbClient dbClient = null;
    private static VPlexApiFactory vplexApiFactory = null;
    
    public void checkVolumesOnVplex(URI vplexSystemURI) {
        URIQueryResultList result = new URIQueryResultList();
        int nerrors = 0;
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceVolumeConstraint(vplexSystemURI), result);
        Iterator<URI> iter = result.iterator();
        VPlexApiClient client = getVPlexApiClient(vplexSystemURI);
        // Get all the virtual volumes. We elect for shallow here as it's quicker-
        // we will spend time below getting details.
        writeLog("Retrieving all virtual volumes... this will take some time...");
        Map<String, VPlexVirtualVolumeInfo> vvInfoMap = client.getVirtualVolumes(true);
		List<VPlexStorageViewInfo> storageViews = client.getStorageViews();
        writeLog("... done");
        while(iter.hasNext()) {
            Volume volume = dbClient.queryObject(Volume.class, iter.next());
            if (volume == null || volume.getInactive()) {
                continue;
            }
            writeLog(String.format("\nChecking volume %s (%s)", volume.getLabel(), volume.getDeviceLabel()));
            if (volume.getAssociatedVolumes() == null || volume.getAssociatedVolumes().isEmpty()) {
                writeLog(String.format("Volume %s (%s) has no associated volumes... skipping", 
                        volume.getLabel(), volume.getDeviceLabel()));
                continue;
            }
            
            VPlexVirtualVolumeInfo vvInfo = vvInfoMap.get(volume.getDeviceLabel());
            if (vvInfo == null) {
                writeLog(String.format("ERROR: Volume %s (%s) had no VirtualVolumeInfo in VPlex", 
                        volume.getLabel(), volume.getDeviceLabel()));
                nerrors++;
                continue;
            }
			
			//writeLog(String.format("checking %s wwn %s ",vvInfo.getName(), vvInfo.getWwn()));
			if ((null != vvInfo.getWwn()) && (null != volume.getWWN())) {
			 if (vvInfo.getName().equals(volume.getDeviceLabel())) {
			  if (vvInfo.getWwn().toUpperCase().equals(volume.getWWN().toUpperCase())) {
			    writeLog(String.format("Virtual Volume %s wwn %s matches VPLEX", vvInfo.getName(), vvInfo.getWwn()));
			  }  else {
                    writeLog(String.format("ERROR: Virtual Volume %s wwn %s is not present in VPLEX", 
                            vvInfo.getName(), vvInfo.getWwn()));
                    nerrors++;
               }
			}
			}
			
			StringSet wwns = new StringSet();
            for (String cluster : vvInfo.getClusters()) {
                Map<String, VPlexStorageVolumeInfo> svInfoMap = client.getStorageVolumeInfoForDevice(
                        vvInfo.getSupportingDevice(), vvInfo.getLocality(), cluster, false);
                for (String wwn : svInfoMap.keySet()) {
                    //writeLog("adding wwn " + wwn.toUpperCase());
					wwns.add(wwn.toUpperCase());
                    VPlexStorageVolumeInfo svInfo = svInfoMap.get(wwn);
                    writeLog(String.format("StorageVolume wwn %s name %s cluster %s", wwn, svInfo.getName(), cluster));
                }
            }
            
			// Now check associated volumes against the wwns.
            for (String associatedVolume : volume.getAssociatedVolumes()) {
                Volume assocVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolume));
                if (assocVolume == null) {
                    writeLog("Associated volunme not found in database... skipping: " + associatedVolume);
                    continue;
                }
								
                if (wwns.contains(assocVolume.getWWN().toUpperCase())) {
                    writeLog(String.format("Volume %s wwn %s matches VPLEX", assocVolume.getLabel(), assocVolume.getWWN()));
                } else {
                    writeLog(String.format("ERROR: Volume %s wwn %s is not present in VPLEX", 
                            assocVolume.getLabel(), assocVolume.getWWN()));
                    nerrors++;
                }
            }
					
           }
		
		
		  for (VPlexStorageViewInfo storageView : storageViews) {
             List<URI> maskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMaskByNameConstraint(storageView.getName()));
			 writeLog(String.format("\nChecking Storageview %s",storageView.getName()));
			 for (String volumeNameStr : storageView.getVirtualVolumes()) {
              String[] tokens = volumeNameStr.split(",");
              String volumeName = tokens[1];
              ExportMask exportMask = null;
			  boolean found = false;
			  if (null != maskUris && !maskUris.isEmpty()) {
              for (URI maskUri : maskUris) {
                exportMask = dbClient.queryObject(ExportMask.class, maskUri);
                if (null != exportMask && exportMask.getInitiators() != null) {
				   for (Map.Entry<String, String> entry : exportMask.getVolumes().entrySet()) {
				      Volume maskvolume = dbClient.queryObject(Volume.class, URIUtil.uri(entry.getKey()));
					  if (volumeName.equals(maskvolume.getDeviceLabel())) {
					   writeLog(String.format("devicelabel found %s",volumeName));
					   found = true;
					  }
				    }
				  }
                }
				if(!found)
				{
				 writeLog(String.format("devicelabel not found %s",volumeName));
				}
               }
             }
            }
        writeLog("Total errors for this VPLEX: " + nerrors);
    }
    
    /**
     * Retrieves VPLEX systems from the database.
     * @return List<StorageSystem>
     */
    List<StorageSystem> getVPlexSystems() {
        List<StorageSystem> vplexSystems = new ArrayList<StorageSystem>();
        List<URI> storageSystems = dbClient.queryByType(StorageSystem.class, true);
        for (URI storageSystemUri : storageSystems) {
            StorageSystem system = dbClient.queryObject(StorageSystem.class, storageSystemUri);
            if (DiscoveredDataObject.Type.vplex.name().equals(system.getSystemType())) {
                log.info("VPLEX system: " + system.getLabel());
                vplexSystems.add(system);
            }
        }
        return vplexSystems;
    }
    
    /**
     * Returns a VPlexApiClient for the system with the specified URI.
     * @param vplexUri
     * @return
     */
    public VPlexApiClient getVPlexApiClient(URI vplexUri) {
        try {
            if (vplexApiFactory == null) {
                vplexApiFactory = VPlexApiFactory.getInstance();
            }
            VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
            return client;
        } catch (Exception ex) {
            log.error("Could not get VPlexApiClient");
            System.out.println("Could not connect to VPLEX: " + vplexUri);
            System.exit(2);;
        }
        return null;
    }
    
    public void writeLog(String s) {
        System.out.println(s);
        log.info(s);
    }
    
    public void dbClientStart() {
        dbClient.start();
    }
    
    public VplexDBCkr() {
        bean = this;
    }
    public DbClient getDbClient() {
        return dbClient;
    }
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    public static VplexDBCkr getBean() {
        return bean;
    }

}
