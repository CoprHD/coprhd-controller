/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;

/**
 * A utility class that supports custom naming of volumes.
 */
public class CustomVolumeNamingUtils {

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(CustomVolumeNamingUtils.class);

    /**
     * Returns whether or not custom volume naming is enabled.
     * 
     * @param customConfigHandler A reference to the custom configuration handler.
     * @param String scope The custom configuration scope.
     * 
     * @return true if custom volume naming is enabled, else false.
     */
    public static Boolean isCustomVolumeNamingEnabled(CustomConfigHandler customConfigHandler, String scope) {
        return customConfigHandler.getComputedCustomConfigBooleanValue(
                CustomConfigConstants.CUSTOM_VOLUME_NAMING_ENABLED, scope, null);
    }
    
    /**
     * Create the custom volume naming datasource for the passed volume.
     * 
     * @param project A reference to the volume's project.
     * @param tenant A reference to the project's tenant.
     * @param volumeLabel The user specified volume name.
     * @param volumeWWN The WWN for the volume.
     * @param hostOrClusterName The name of the host or cluster to which the volume will be exported, or null.
     * @param dataSourceFactory A reference to a data source factory.
     * @param customConfigName The name of the volume naming custom configuration.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the created data source or null on error.
     */
    public static DataSource getCustomConfigDataSource(Project project, TenantOrg tenant, String volumeLabel, String volumeWWN,
            String hostOrClusterName, DataSourceFactory dataSourceFactory, String customConfigName, 
            DbClient dbClient) {
        DataSource dataSource = null;
        try {
            // Create the data source.
            dataSource = dataSourceFactory.createCustomVolumeNameDataSource(project, tenant, volumeLabel,
                    volumeWWN, hostOrClusterName, customConfigName);
        } catch (Exception e) {
            s_logger.warn(String.format("Error creating the custom volume name datasource for volume %s", volumeLabel), e);
        }
        
        return dataSource;
    }
    
    /**
     * Get the volume custom configuration name.
     * 
     * @param withExport true if the volume will be provisioned to a specific host or cluster, false otherwise.
     * 
     * @return The volume custom configuration name.
     */
    public static String getCustomConfigName(boolean withExport) {
        String customConfigName = null;
        if (withExport) {
            customConfigName =  CustomConfigConstants.CUSTOM_VOLUME_NAME_WITH_EXPORT;
        } else {
            customConfigName =  CustomConfigConstants.CUSTOM_VOLUME_NAME;
        }
        
        return customConfigName;
    }
    
    /**
     * Returns the volume's custom name.
     * 
     * @param customConfigHandler A reference to the custom configuration handler.
     * @param customConfigName The custom configuration name;
     * @param dataSource The custom volume name datasource for the volume.
     * @param scope The custom configuration scope.
     * 
     * @return The volume's custom name.
     */
    public static String getCustomName(CustomConfigHandler customConfigHandler,
            String customConfigName, DataSource dataSource, String scope) {
        return customConfigHandler.getComputedCustomConfigValue(customConfigName,
                scope, dataSource);
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
            if (vplexVolume == null) {
                s_logger.warn("The passed volume is null.");
                return;
            }
            
            if (!VPlexUtil.isVplexVolume(vplexVolume, dbClient)) {
                s_logger.warn("Volume {} can not be renamed because it is not a VPLEX volume.", vplexVolume.getId());
                return;                
            }
            
            // Find and rename the volume on the VPLEX and in ViPR.
            VPlexVirtualVolumeInfo vvInfo = client.findVirtualVolume(vplexVolume.getDeviceLabel());
            vvInfo = renameVolumeOnVPlex(vvInfo, newVolumeName, client);
            vplexVolume.setNativeId(vvInfo.getPath());
            vplexVolume.setNativeGuid(vvInfo.getPath());
            vplexVolume.setLabel(vvInfo.getName());
            vplexVolume.setDeviceLabel(vvInfo.getName());
            dbClient.updateObject(vplexVolume);
        } catch (Exception e) {
            s_logger.warn(String.format("Error attempting to rename VPLEX volume %s", vplexVolume.getDeviceLabel()), e);
            throw e;
        }       
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
}
