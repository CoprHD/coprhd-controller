/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;

/**
 * A utility class that supports custom naming of VPLEX volumes on a VPLEX system.
 */
public class VPlexCustomNameUtils {

    // Volume lock prefix.
    private static final String VPLEX_VOLUME_LOCK_PREFIX = "Lock-VPLEX-Volume-";
    
    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(VPlexConsistencyGroupManager.class);

    /**
     * Returns whether or not custom naming is enabled.
     * 
     * @param customConfigHandler A reference to the custom configuration handler.
     * 
     * @return true if custom VPLEX volume naming is enabled, else false.
     */
    public static Boolean isCustomNamingEnabled(CustomConfigHandler customConfigHandler) {
        return customConfigHandler.getComputedCustomConfigBooleanValue(
                CustomConfigConstants.VPLEX_CUSTOM_VOLUME_NAMING_ENABLED,
                DiscoveredDataObject.Type.vplex.name(), null);
    }
    
    /**
     * Create the custom VPLEX volume name datasource for the passed VPLEX volume.
     * 
     * @param vplexVolume A reference to the VPLEX volume.
     * @param mostRecentExportGroup If exported, a reference to the most recent export group, else null.
     * @param dataSourceFactory A reference to a data source factory.
     * @param customConfigName The name of the VPLEX volume name custom config.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the created data source or null on error.
     */
    public static DataSource getCustomConfigDataSource(Volume vplexVolume, ExportGroup mostRecentExportGroup,
            DataSourceFactory dataSourceFactory, String customConfigName, DbClient dbClient) {
        DataSource dataSource = null;
        try {
            // Verify passed volume is not null.
            if (vplexVolume == null) {
                return dataSource;
            }
            
            // Get the VPLEX volume project.
            Project project = dbClient.queryObject(Project.class, vplexVolume.getProject().getURI());
            
            // Get the backend volumes for the VPLEX volume. There may not be any for an
            // ingested VPLEX volume if the backend volumes were not ingested.
            Volume srcSideBEVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, dbClient, false);
            Volume haSideBEVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, false, dbClient, false);
            
            // Get the source side associated volume native id and storage system.
            String srcSideBEVolumeNativeId = null;
            StorageSystem srcSideBESystem = null;
            if (srcSideBEVolume != null) {
                srcSideBEVolumeNativeId = srcSideBEVolume.getNativeId();
                srcSideBESystem = dbClient.queryObject(StorageSystem.class, srcSideBEVolume.getStorageController());
            }
            
            // Do the same for the HA side. Of course this could also be null for local VLPEX volumes.
            String haSideBEVolumeNativeId = null;
            StorageSystem haSideBESystem = null;
            if (haSideBEVolume != null) {
                haSideBEVolumeNativeId = haSideBEVolume.getNativeId();
                haSideBESystem = dbClient.queryObject(StorageSystem.class, haSideBEVolume.getStorageController());                
            }
            
            // If the volume is exported then the passed export group will not be null. If so,
            // then get the export type and the name of the host or cluster for the passed 
            // export group.
            String exportType = null;
            String hostOrClusterName = null;
            if (mostRecentExportGroup != null) {
                exportType = mostRecentExportGroup.getType();
                hostOrClusterName = mostRecentExportGroup.getLabel();
            }
            
            // Create the data source.
            dataSource = dataSourceFactory.createVPlexVolumeNameDataSource(project, vplexVolume.getLabel(),
                    srcSideBESystem, srcSideBEVolumeNativeId, haSideBESystem, haSideBEVolumeNativeId,
                    hostOrClusterName, exportType, customConfigName);
        } catch (Exception e) {
            s_logger.warn(String.format("Error creating the VPLEX custom volume name datasource for volume %s:%s",
                    vplexVolume.getId(), vplexVolume.getLabel()), e);
        }
        
        return dataSource;
    }
    
    /**
     * Get the VPLEX volume custom configuration name.
     * 
     * @param isDistributed true is the VPLEX volume is distributed, else false.
     * @param exportType If the VLPEX volume is export, the export type, else null.
     * 
     * @return The VPLEX volume custom configuration name.
     */
    public static String getCustomConfigName(boolean isDistributed, String exportType) {
        String customConfigName = null;
        if (exportType != null) {
            if (isDistributed) {
                customConfigName = ExportGroup.ExportGroupType.Host.name().equals(exportType) ?
                        CustomConfigConstants.VPLEX_HOST_EXPORT_DISTRIBUTED_VOLUME_NAME :
                            CustomConfigConstants.VPLEX_CLUSTER_EXPORT_DISTRIBUTED_VOLUME_NAME;
            } else {
                customConfigName = ExportGroup.ExportGroupType.Host.name().equals(exportType) ?
                        CustomConfigConstants.VPLEX_HOST_EXPORT_LOCAL_VOLUME_NAME :
                            CustomConfigConstants.VPLEX_CLUSTER_EXPORT_LOCAL_VOLUME_NAME;                
            }
        } else if (isDistributed) {
            customConfigName = CustomConfigConstants.VPLEX_DISTRIBUTED_VOLUME_NAME;
        } else {
            customConfigName = CustomConfigConstants.VPLEX_LOCAL_VOLUME_NAME;
        }
        
        return customConfigName;
    }
    
    /**
     * Returns the VPLEX volume custom name.
     * 
     * @param customConfigHandler A reference to the custom configuration handler.
     * @param customConfigName The VPLEX volume custom configuration name;
     * @param dataSource The custom VPLEX volume name datasource for a VPLEX volume.
     * 
     * @return The VPLEX volume custom name.
     */
    public static String getCustomName(CustomConfigHandler customConfigHandler,
            String customConfigName, DataSource dataSource) {
        return customConfigHandler.getComputedCustomConfigValue(customConfigName,
                DiscoveredDataObject.Type.vplex.name(), dataSource);
    }
    
    /**
     * Determines if the passed VPLEX volume is a distributed volume.
     * 
     * @param vplexVolume A reference to a VPLEX volume.
     * @param client A reference to a VPLEX API client.
     * 
     * @return true if the volume is distributed, false otherwise.
     */
    public static boolean isVolumeDistributed(Volume vplexVolume, VPlexApiClient client) {
        boolean isDistributed = false;
        StringSet associatedVolumeIds = vplexVolume.getAssociatedVolumes();
        if ((associatedVolumeIds != null) && (!associatedVolumeIds.isEmpty())) {
            isDistributed = associatedVolumeIds.size() == 2 ? true : false;
        } else {
            VPlexVirtualVolumeInfo vvInfo = client.findVirtualVolume(vplexVolume.getDeviceLabel());
            isDistributed = VPlexVirtualVolumeInfo.Locality.distributed.name().equals(
                    vvInfo.getLocality()) ? true : false;
        }
        
        return isDistributed;
    }
    
    /**
     * Renames the volume represented by the passed VPLEX volume info with the passed name.
     * 
     * @param vvInfo A reference to the VPLEX volume info.
     * @param newVolumeName The new name for the volume.
     * @param client A reference to the VPLEX API client.
     * 
     * @return A reference to the updated virtual volume info.
     */
    public static VPlexVirtualVolumeInfo renameVolumeOnVPlex(VPlexVirtualVolumeInfo vvInfo,
            String newVolumeName, VPlexApiClient client) {
        VPlexVirtualVolumeInfo updateVolumeInfo = vvInfo;
        try {
            String currentVolumeName = vvInfo.getName();
            if (!currentVolumeName.equals(newVolumeName)) {
                s_logger.info("Renaming VPLEX volume {} to custom name {}", vvInfo.getName(), newVolumeName);
                updateVolumeInfo = client.renameResource(vvInfo, newVolumeName);                
            } else {
                s_logger.info("Skip rename as volume is already named {}", newVolumeName);
            }
        } catch (Exception e) {
            s_logger.warn(String.format("Error attempting to rename VPLEX volume %s to %s", vvInfo.getName(), newVolumeName), e);
        }       
        return updateVolumeInfo;
    }
    
    /**
     * Renames the volume represented by the passed VPLEX volume info with the passed name.
     * 
     * @param vplexVolume A reference to the VPLEX volume.
     * @param newVolumeName The new name for the volume.
     * @param client A reference to the VPLEX API client.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the updated virtual volume info.
     * 
     * @throws Exception if the volume to rename cannot be found.
     */
    public static void renameVPlexVolume(Volume vplexVolume, String newVolumeName,
            VPlexApiClient client, DbClient dbClient) throws Exception {
        try {
            VPlexVirtualVolumeInfo vvInfo = client.findVirtualVolume(vplexVolume.getDeviceLabel());
            vvInfo = renameVolumeOnVPlex(vvInfo, newVolumeName, client);
            vplexVolume.setNativeId(vvInfo.getPath());
            vplexVolume.setNativeGuid(vvInfo.getPath());
            vplexVolume.setDeviceLabel(vvInfo.getName());
            dbClient.updateObject(vplexVolume);
        } catch (Exception e) {
            s_logger.warn(String.format("Error attempting to find VPLEX volume %s for rename", vplexVolume.getDeviceLabel()), e);
            throw e;
        }       
    }    
    
    /**
     * Given the passed set a volume URIs for the volumes on the passed VPLEX system,
     * returns a list containing the unique lock names for these volumes.
     * 
     * @param vplexSystem The VPLEX system for the passed volumes.
     * @param volumeURIs The URIs of the volumes for which to generate lock names.
     * 
     * @return The list of unique lock names for these volumes.
     */
    public static List<String> getVolumeLockNames(StorageSystem vplexSystem, Set<URI> volumeURIs) {
        List<String> lockNames = new ArrayList<>();
        Iterator<URI> volumeURIsIter = volumeURIs.iterator();
        while (volumeURIsIter.hasNext()) {
            URI volumeURI = volumeURIsIter.next();
            StringBuilder lockNameBuilder = new StringBuilder(VPLEX_VOLUME_LOCK_PREFIX);
            lockNameBuilder.append(vplexSystem.getSerialNumber());
            lockNameBuilder.append("-");
            lockNameBuilder.append(volumeURI);// TBD Maybe label for more readability
            lockNames.add(lockNameBuilder.toString());
        }
        return lockNames;
    }
}
