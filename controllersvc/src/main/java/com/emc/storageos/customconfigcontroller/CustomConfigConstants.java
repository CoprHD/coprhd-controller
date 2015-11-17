/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.customconfigcontroller;

public interface CustomConfigConstants {

    /** The key for the global-type scope */
    public String DEFAULT_KEY = "default";
    /** The value for the global-type scope */
    public String GLOBAL_KEY = "global";
    /** The key for the system-type scope */
    public String SYSTEM_TYPE_SCOPE = "systemType";
    /** The key for the host-type scope */
    public String HOST_TYPE_SCOPE = "hostType";
    /** The config_name for zone name */
    public String ZONE_MASK_NAME = "SanZoneName";
    /** The config_name for skipping zoning */
    public String ZONE_ADD_VOLUME= "SanZoneAddVolumeCheckZoning";

    public String HDS_STORAGE_PORT_NUMBER = "hdsPortNumber";
    public String AUTO_TIERING_POLICY_NAME = "auto_tiering_policy_name";
    public String HOST_IO_LIMIT_BANDWIDTH = "host_IO_limit_bandwidth";
    public String HOST_IO_LIMIT_IOPS = "host_IO_limit_iops";
    public String VPLEX_CLUSTER_NUMBER = "vplex_cluster_number";
    public String VPLEX_CLUSTER_SERIAL_NUMBER = "vplex_cluster_serial_number";
    public String ARRAY_PORT_NAME = "array_port_name";
    public String ARRAY_SERIAL_NUMBER = "array_serial_number";

    public String VNX_HOST_STORAGE_GROUP_MASK_NAME = "VNXHostStorageGroupName";

    public String VMAX_HOST_MASKING_VIEW_MASK_NAME = "VMAXHostMaskingViewName";
    public String VMAX_CLUSTER_MASKING_VIEW_MASK_NAME = "VMAXClusterMaskingViewName";

    public String VMAX_HOST_STORAGE_GROUP_MASK_NAME = "VMAXHostStorageGroupName";
    public String VMAX_CLUSTER_STORAGE_GROUP_MASK_NAME = "VMAXClusterStorageGroupName";

    public String VMAX_HOST_CASCADED_IG_MASK_NAME = "VMAXHostCascadedIGName";
    public String VMAX_CLUSTER_CASCADED_IG_MASK_NAME = "VMAXClusterCascadedIGName";

    public String VMAX_HOST_CASCADED_SG_MASK_NAME = "VMAXHostCascadedSGName";
    public String VMAX_CLUSTER_CASCADED_SG_MASK_NAME = "VMAXClusterCascadedSGName";

    public String VMAX_HOST_INITIATOR_GROUP_MASK_NAME = "VMAXHostInitiatorGroupName";
    public String VMAX_CLUSTER_INITIATOR_GROUP_MASK_NAME = "VMAXClusterInitiatorGroupName";

    public String VMAX_HOST_PORT_GROUP_MASK_NAME = "VMAXHostPortGroupName";
    public String VMAX_CLUSTER_PORT_GROUP_MASK_NAME = "VMAXClusterPortGroupName";

    public String VPLEX_VMAX_MASKING_VIEW_MAXIMUM_VOLUMES = "VPlexVMAXMaskingViewMaximumVolumes";

    public String XTREMIO_VOLUME_FOLDER_NAME = "XtremIOVolumeFolderName";
    public String XTREMIO_INITIATOR_GROUP_NAME = "XtremIOInitiatorGroupName";
    public String XTREMIO_HOST_INITIATOR_GROUP_FOLDER_NAME = "XtremIOHostInitiatorGroupFolderName";
    public String XTREMIO_CLUSTER_INITIATOR_GROUP_FOLDER_NAME = "XtremIOClusterInitiatorGroupFolderName";

    public String VPLEX_STORAGE_VIEW_NAME = "VPlexStorageViewName";

    public String HDS_HOST_STORAGE_DOMAIN_NAME_MASK_NAME = "HDSHostStorageDomainName";
    public String HDS_HOST_STORAGE_DOMAIN_NICKNAME_MASK_NAME = "HDSHostStorageDomainNickName";
    public String HDS_HOST_STORAGE_HOST_MODE_OPTION = "HDSHostModeOption";

    public String PORT_ALLOCATION_INITIATOR_CEILLING = "PortAllocationInitiatorCeiling";
    public String PORT_ALLOCATION_VOLUME_CEILLING = "PortAllocationVolumeCeiling";
    public String PORT_ALLOCATION_PORT_UTILIZATION_CEILLING = "PortAllocationPortUtilizationCeiling";
    public String PORT_ALLOCATION_CPU_UTILIZATION_CEILLING = "PortAllocationCpuUtilizationCeiling";
    public String PORT_ALLOCATION_DAYS_TO_AVERAGE_UTILIZATION = "PortAllocationDaysToAverageUtilization";
    public String PORT_ALLOCATION_EMA_FACTOR = "PortAllocationEmaFactor";
    public String PORT_ALLOCATION_METRICS_ENABLED = "PortAllocationMetricsEnabled";

    public String PORT_ALLOCATION_USE_PREZONED_PORT_FRONTEND = "PortAllocationUsePrezonedPortsFrontEnd";
    public String PORT_ALLOCATION_USE_PREZONED_PORT_BACKEND = "PortAllocationUsePrezonedPortsBackEnd";

    public String MIGRATION_SPEED = "VPlexMigrationSpeed";
    public String NAS_DYNAMIC_PERFORMANCE_PLACEMENT_ENABLED = "NasDynamicPerformancePlacementEnabled";
    public String USE_PHYSICAL_NAS_FOR_PROVISIONING = "NasUsePhysicalNASForProvisioning";
    
    
}
