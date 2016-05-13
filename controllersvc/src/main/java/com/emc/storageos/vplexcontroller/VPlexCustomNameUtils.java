/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

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
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;

/**
 * A utility class that supports custom naming of VPLEX volumes on a VPLEX system.
 */
public class VPlexCustomNameUtils {
    
    public static final String VPLEX_VIRTUAL_VOLUME_RENAME_STEP = "renameVolumeStep";
    public static final String VPLEX_VIRTUAL_VOLUME_RENAME_METHOD = "renameVolume";
    
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
     * Renames the volume represented by the passed VPLEX volume info with the passed name.
     * 
     * @param client A reference to the VPLEX API client.
     * @param vvInfo A reference to the VPLEX volume info.
     * @param newVolumeName The new name for the volume.
     * 
     * @return A reference to the updated virtual volume info.
     */
    public static VPlexVirtualVolumeInfo renameVPlexVolume(VPlexApiClient client, VPlexVirtualVolumeInfo vvInfo, String newVolumeName) {
        VPlexVirtualVolumeInfo updateVolumeInfo = vvInfo;
        try {
            s_logger.info("Renaming VPLEX volume {} to custom name {}", vvInfo.getName(), newVolumeName);
            updateVolumeInfo = client.renameResource(vvInfo, newVolumeName);
        } catch (Exception e) {
            s_logger.warn(String.format("Error attempting to rename VPLEX volume %s to %s", vvInfo.getName(), newVolumeName), e);
        }       
        return updateVolumeInfo;
    }
}
