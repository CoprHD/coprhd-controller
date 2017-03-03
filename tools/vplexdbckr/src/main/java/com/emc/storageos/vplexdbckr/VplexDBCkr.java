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
import java.util.HashMap;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexResourceInfo;
import com.emc.storageos.vplex.api.VPlexStorageVolumeInfo;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.vplex.api.VPlexStorageViewInfo;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.model.BlockMirror;

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
    
	 public void cleanupForViPROnlyDelete(List<VolumeDescriptor> volumeDescriptors) {
        // Remove volumes from ExportGroup(s) and ExportMask(s).
        List<URI> volumeURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        for (URI volumeURI : volumeURIs) {
            cleanBlockObjectFromExports(volumeURI, true);
        }
		
		// Clean up the relationship between vplex volumes that are full
        // copies and and their source vplex volumes.
        List<VolumeDescriptor> vplexVolumeDescriptors = VolumeDescriptor
                .getDescriptors(volumeDescriptors, VolumeDescriptor.Type.VPLEX_VIRT_VOLUME);
        List<URI> vplexvolumeURIs = VolumeDescriptor.getVolumeURIs(volumeDescriptors);
        for (URI volumeURI : vplexvolumeURIs) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            URI sourceVolumeURI = volume.getAssociatedSourceVolume();
            if (!NullColumnValueGetter.isNullURI(sourceVolumeURI)) {
                // The volume being removed is a full copy. Make sure the copies
                // list of the source no longer references this volume. Note
                // that it is possible that the source was already deleted but
                // we left the source URI set in the copy, so one could always
                // know the source of the copy. So, check for a null source
                // volume.
                Volume sourceVolume = dbClient.queryObject(Volume.class,
                        sourceVolumeURI);
                if (sourceVolume != null) {
                    StringSet fullCopyIds = sourceVolume.getFullCopies();
                    if (fullCopyIds.contains(volumeURI.toString())) {
                        fullCopyIds.remove(volumeURI.toString());
                        dbClient.updateObject(sourceVolume);
                    }
                }
            }
        }
    }
	public void cleanBlockObjectFromExports(URI boURI, boolean addToExisting) {
        writeLog(String.format("Cleaning block object from exports %s", boURI));
		Map<URI, ExportGroup> exportGroupMap = new HashMap<URI, ExportGroup>();
        Map<URI, ExportGroup> updatedExportGroupMap = new HashMap<URI, ExportGroup>();
        Map<String, ExportMask> exportMaskMap = new HashMap<String, ExportMask>();
        Map<String, ExportMask> updatedExportMaskMap = new HashMap<String, ExportMask>();
        BlockObject bo = BlockObject.fetch(dbClient, boURI);
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(boURI), exportGroupURIs);
        for (URI exportGroupURI : exportGroupURIs) {
            writeLog(String.format("Cleaning block object from export group %s",exportGroupURI));
			ExportGroup exportGroup = null;
            if (exportGroupMap.containsKey(exportGroupURI)) {
                exportGroup = exportGroupMap.get(exportGroupURI);
            } else {
                exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
                exportGroupMap.put(exportGroupURI, exportGroup);
            }

            if (exportGroup.hasBlockObject(boURI)) {
                writeLog(String.format("Removing block object from export group"));
				exportGroup.removeVolume(boURI);
                if (!updatedExportGroupMap.containsKey(exportGroupURI)) {
                    updatedExportGroupMap.put(exportGroupURI, exportGroup);
                }
            }

            StringSet exportMaskIds = exportGroup.getExportMasks();
            for (String exportMaskId : exportMaskIds) {
                ExportMask exportMask = null;
                if (exportMaskMap.containsKey(exportMaskId)) {
                    exportMask = exportMaskMap.get(exportMaskId);
                } else {
                    exportMask = dbClient.queryObject(ExportMask.class, URI.create(exportMaskId));
                    exportMaskMap.put(exportMaskId, exportMask);
                }
                if (exportMask.hasVolume(boURI)) {
                    writeLog(String.format("Cleaning block object from export mask %s", exportMaskId));
					StringMap exportMaskVolumeMap = exportMask.getVolumes();
                    String hluStr = exportMaskVolumeMap.get(boURI.toString());
                    exportMask.removeVolume(boURI);
                    exportMask.removeFromUserCreatedVolumes(bo);
                    // Add this volume to the existing volumes map for the
                    // mask, so that if the last ViPR created volume goes
                    // away, the physical mask will not be deleted.
                    if (addToExisting) {
                        writeLog(String.format("Adding to existing volumes"));
						exportMask.addToExistingVolumesIfAbsent(bo, hluStr);
                    }
                    if (!updatedExportMaskMap.containsKey(exportMaskId)) {
                        updatedExportMaskMap.put(exportMaskId, exportMask);
                    }
                }
            }
        }
        if (!updatedExportGroupMap.isEmpty()) {
            List<ExportGroup> updatedExportGroups = new ArrayList<ExportGroup>(
                    updatedExportGroupMap.values());
            dbClient.updateObject(updatedExportGroups);
        }

        if (!updatedExportMaskMap.isEmpty()) {
            List<ExportMask> updatedExportMasks = new ArrayList<ExportMask>(
                    updatedExportMaskMap.values());
            dbClient.updateObject(updatedExportMasks);
        }
    }
	
	public List<ExportMask> isVolumeExported(URI boURI) {
        writeLog(String.format("checking block object belong to any export groups %s", boURI));
		boolean isExported = false;
		Map<String, ExportMask> exportMaskMap = new HashMap<String, ExportMask>();
		URIQueryResultList exportGroupURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(boURI), exportGroupURIs);
        isExported = exportGroupURIs.iterator().hasNext();
		if (isExported == false) {
		 return null;
		}
		for (URI exportGroupURI : exportGroupURIs) {
            //writeLog(String.format("Cleaning block object from export group %s",exportGroupURI));
			ExportGroup exportGroup = null;
            exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
            if (!exportGroup.hasBlockObject(boURI)) {
                continue;
            }
			
            StringSet exportMaskIds = exportGroup.getExportMasks();
			for (String exportMaskId : exportMaskIds) {
                ExportMask exportMask = null;
                exportMask = dbClient.queryObject(ExportMask.class, URI.create(exportMaskId));
				writeLog(String.format("checking block object belongs or not to exportmask %s %s and exportgroup %s", URI.create(exportMaskId),exportMask.getMaskName(),exportGroupURI));
                if (exportMask.hasVolume(boURI)) {
                   if (!exportMaskMap.containsKey(exportMaskId)) {
				   exportMaskMap.put(exportMaskId, exportMask);
				   }
                }
            }
	   }
        if (!exportMaskMap.isEmpty()) {
            List<ExportMask> updatedExportMasks = new ArrayList<ExportMask>(exportMaskMap.values());
			return updatedExportMasks;
        }
		else {
		 return null;
		}
    }

	/*
	public List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI,
            List<URI> volumeURIs, String deletionType) {
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        for (URI volumeURI : volumeURIs) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            VolumeDescriptor descriptor = new VolumeDescriptor(VolumeDescriptor.Type.VPLEX_VIRT_VOLUME,
                    systemURI, volumeURI, null, null);
            volumeDescriptors.add(descriptor);
            // Add a descriptor for each of the associated volumes.
            if (!volume.isIngestedVolume(dbClient)) {
                for (String assocVolId : volume.getAssociatedVolumes()) {
                    Volume assocVolume = dbClient.queryObject(Volume.class, URI.create(assocVolId));
                    if (null != assocVolume && !assocVolume.getInactive() && assocVolume.getNativeId() != null) {
                        VolumeDescriptor assocDesc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                                assocVolume.getStorageController(), assocVolume.getId(), null, null);
                        volumeDescriptors.add(assocDesc);
                    }
                }
                // If there were any Vplex Mirrors, add a descriptors for them.
                addDescriptorsForVplexMirrors(volumeDescriptors, volume);
            }
        }
        return volumeDescriptors;
    }
	*/
	
	public void addDescriptorsForVplexMirrors(List<VolumeDescriptor> descriptors, Volume vplexVolume) {
        if (vplexVolume.getMirrors() != null && vplexVolume.getMirrors().isEmpty() == false) {
            for (String mirrorId : vplexVolume.getMirrors()) {
                VplexMirror mirror = dbClient.queryObject(VplexMirror.class, URI.create(mirrorId));
                if (mirror != null && !mirror.getInactive()) {
                    if (null != mirror.getAssociatedVolumes()) {
                        for (String assocVolumeId : mirror.getAssociatedVolumes()) {
                            Volume volume = dbClient.queryObject(Volume.class, URI.create(assocVolumeId));
                            if (volume != null && !volume.getInactive()) {
                                VolumeDescriptor volDesc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                                        volume.getStorageController(), URI.create(assocVolumeId), null, null);
                                descriptors.add(volDesc);
                            }
                        }
                    }
                }
            }
        }
    }
	
	public void checkVolumesOnVplex(URI vplexSystemURI, boolean deleteInvalidVolumes, boolean checkStorageViews) {
        URIQueryResultList result = new URIQueryResultList();
		List<URI> deletevirtualvolumeURIs = new ArrayList<URI>();
		int nerrors = 0;
		int invalidVolumeCount = 0;
        
		dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceVolumeConstraint(vplexSystemURI), result);
        Iterator<URI> iter = result.iterator();
		VPlexApiClient client = getVPlexApiClient(vplexSystemURI);
        // Get all the virtual volumes. We elect for shallow here as it's quicker-
        // we will spend time below getting details.
        writeLog("Retrieving all virtual volumes... this will take some time...");
        Map<String, VPlexVirtualVolumeInfo> vvInfoMap = client.getVirtualVolumes(true);
		List<VPlexStorageViewInfo> storageViews = client.getStorageViewsLite();
        writeLog("... done");
       
	   try {
		while(iter.hasNext()) {
            Volume volume = dbClient.queryObject(Volume.class, iter.next());
            if (volume == null || volume.getInactive()) {
                continue;
            }
			
            if (!checkStorageViews) {
			writeLog(String.format("Checking volume %s (%s)", volume.getLabel(), volume.getDeviceLabel()));
            if (volume.getAssociatedVolumes() == null || volume.getAssociatedVolumes().isEmpty()) {
                writeLog(String.format("Volume %s (%s) has no associated volumes... skipping", 
                        volume.getLabel(), volume.getDeviceLabel()));
                continue;
            }
            
            VPlexVirtualVolumeInfo vvInfo = vvInfoMap.get(volume.getDeviceLabel());
            if (vvInfo == null) {
                writeLog(String.format("ERROR: Volume %s (%s) had no VirtualVolumeInfo in VPlex", 
                        volume.getLabel(), volume.getDeviceLabel()));
                deletevirtualvolumeURIs.add(volume.getId());  
				nerrors++;
				invalidVolumeCount++;
                continue;
            }
			
			if (null == volume.getWWN()) {
                 if (vvInfo.getName().equals(volume.getDeviceLabel())) {
                writeLog(String.format("ERROR: Volume %s (%s) wwn is null and in vplex wwn is %s",
                        volume.getLabel(), volume.getDeviceLabel(),vvInfo.getWwn()));
                                nerrors++;
            }
			}
			
			if ((null != vvInfo.getWwn()) && (null != volume.getWWN())) {
			 if (vvInfo.getName().equals(volume.getDeviceLabel())) {
			  if (vvInfo.getWwn().toUpperCase().equals(volume.getWWN().toUpperCase())) {
			    writeLog(String.format("Virtual Volume %s wwn %s matches VPLEX", vvInfo.getName(), vvInfo.getWwn()));
			  }  else {
                    writeLog(String.format("ERROR: Virtual Volume %s wwn %s in VPLEX mismatch with viprdb %s", 
                            vvInfo.getName(), vvInfo.getWwn(), volume.getWWN()));
                    deletevirtualvolumeURIs.add(volume.getId());  
					invalidVolumeCount++;
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
			
			List<ExportMask> exportMaskListInDB = isVolumeExported(volume.getId());
			if (null != exportMaskListInDB) {
			for (ExportMask exportMaskInDB : exportMaskListInDB) {
			 boolean found = false;
			 boolean storageviewfound = false;
			  writeLog(String.format("INFO: exportmask PORT %s",exportMaskInDB.getStoragePorts())); 
			 for (VPlexStorageViewInfo storageView : storageViews) {
			  if (storageView.getName().equals(exportMaskInDB.getMaskName())) {
			   storageviewfound = true;
			  for (String portNameStr : storageView.getPorts()) {
                 writeLog(String.format("INFO: PORT %s",portNameStr));          
			   }
			  for (String volumeNameStr : storageView.getVirtualVolumes()) {
                String[] tokens = volumeNameStr.split(",");
                String volumeName = tokens[1];
                if (volumeName.equals(volume.getDeviceLabel())) {
			     found = true;
			     break;
			    }
			   }
			    if(!found) {
			     writeLog(String.format("ERROR: volume %s is in exportmask %s in viprdb  but not in vplex storageview %s",volume.getDeviceLabel(),exportMaskInDB.getMaskName(),storageView.getName()));
			     deletevirtualvolumeURIs.add(volume.getId());
				 nerrors++;
				}
			   break; 
			  }
		   	 }
			 if (!storageviewfound) {
			  writeLog(String.format("ERROR: volume %s is in exportmask %s in viprdb  but storageview not found in vplex",volume.getDeviceLabel(),exportMaskInDB.getMaskName()));
			  deletevirtualvolumeURIs.add(volume.getId());
			  nerrors++;
			 }
			}
			}
			
			for (VPlexStorageViewInfo storageView : storageViews) {
             writeLog(String.format("Checking Storageview %s",storageView.getName()));
			 for (String volumeNameStr : storageView.getVirtualVolumes()) {
          	  String[] tokens = volumeNameStr.split(",");
              String volumeName = tokens[1];
              if (volumeName.equals(volume.getDeviceLabel())) {
			    boolean storageviewfound = false;
				if (null != exportMaskListInDB) {
				for (ExportMask exportMaskInDB : exportMaskListInDB) {
				 if (storageView.getName().equals(exportMaskInDB.getMaskName())) {
				  storageviewfound = true;
				  break;
				 }
				}
				}
				if (!storageviewfound) {
				 deletevirtualvolumeURIs.add(volume.getId());
				 writeLog(String.format("ERROR: volume %s is in vplex storageview %s but not in viprdb exportmask",volumeName,storageView.getName()));
				 nerrors++;
		        }
		
			  } 
             }
            
			}
          
		   
		 }
		   
		/*
		if (deleteInvalidVolumes)
		{
		  writeLog("deleting invalid volumes");
		  // deleting virtual volumes that no longer exist in vplex
		  List<VolumeDescriptor> volumeDescriptors = getDescriptorsForVolumesToBeDeleted(vplexSystemURI, deletevirtualvolumeURIs, VolumeDeleteTypeEnum.VIPR_ONLY.name());
		  cleanupForViPROnlyDelete(volumeDescriptors);
		
		    // Mark them inactive. Note that some of the volumes may be mirrors,
            // which have a different database type.
            List<VolumeDescriptor> descriptorsForMirrors = VolumeDescriptor.getDescriptors(
                    volumeDescriptors, VolumeDescriptor.Type.BLOCK_MIRROR);
            dbClient.markForDeletion(dbClient.queryObject(BlockMirror.class,
                    VolumeDescriptor.getVolumeURIs(descriptorsForMirrors)));
            List<VolumeDescriptor> descriptorsForVolumes = VolumeDescriptor.filterByType(
                    volumeDescriptors, null, new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_MIRROR });
            dbClient.markForDeletion(dbClient.queryObject(Volume.class,
                    VolumeDescriptor.getVolumeURIs(descriptorsForVolumes)));

            // Update the task status for each volume
            for (URI volumeURI : deletevirtualvolumeURIs) {
                Volume volume = dbClient.queryObject(Volume.class, volumeURI);
                dbClient.updateObject(volume);
            }
		  }
		  */
		  } catch (Exception e) {
                    writeLog(String.format("Exception: while verifying virtual volumes", e));
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
