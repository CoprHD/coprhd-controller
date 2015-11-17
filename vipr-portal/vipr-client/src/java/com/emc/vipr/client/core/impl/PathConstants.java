/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.impl;

public class PathConstants {
    public static final String ID_URL_FORMAT = "%s/{id}";
    public static final String DEACTIVATE_URL_FORMAT = ID_URL_FORMAT + "/deactivate";
    public static final String REGISTER_URL_FORMAT = ID_URL_FORMAT + "/register";
    public static final String DEREGISTER_URL_FORMAT = ID_URL_FORMAT + "/deregister";
    public static final String DISCONNECT_URL_FORMAT = ID_URL_FORMAT + "/disconnect";
    public static final String RECONNECT_URL_FORMAT = ID_URL_FORMAT + "/reconnect";
    public static final String TAGS_URL_FORMAT = ID_URL_FORMAT + "/tags";
    public static final String ACL_URL_FORMAT = ID_URL_FORMAT + "/acl";
    public static final String ROLE_ASSIGNMENT_PATH = "/role-assignments";
    public static final String QUOTA_URL_FORMAT = ID_URL_FORMAT + "/quota";
    public static final String BULK_URL_FORMAT = "%s/bulk";
    public static final String SEARCH_URL_FORMAT = "%s/search";

    public static final String VDC_URL = "/vdc";
    public static final String VDC_CAPACITIES_URL = "/vdc/capacities";
    public static final String VDC_SECRET_KEY_URL = "/vdc/secret-key";
    public static final String AUDIT_LOGS_URL = "/audit/logs";
    public static final String MONITORING_EVENTS_URL = "/monitoring/events";
    public static final String METERING_STATS_URL = "/metering/stats";

    public static final String BLOCK_VOLUMES_URL = "/block/volumes";
    public static final String BLOCK_FULL_COPIES_URL = "/block/full-copies";
    public static final String PROTECTION_SET_BY_VOLUME_URL = BLOCK_VOLUMES_URL
            + "/{volumeId}/protection/protection-sets";
    public static final String BLOCK_MIRROR_BY_VOLUME_URL = BLOCK_VOLUMES_URL
            + "/{volumeId}/protection/continuous-copies";
    public static final String FILESYSTEM_URL = "/file/filesystems";
    public static final String FILESYSTEM_QDIR_URL = "/file/quotadirectories";
    public static final String PROJECT_URL = "/projects";
    public static final String TENANT_URL = "/tenants";
    public static final String CURRENT_TENANT_URL = "/tenant";
    public static final String SUBTENANTS_URL = TENANT_URL + "/{tenantId}/subtenants";
    public static final String PROJECT_BY_TENANT_URL = TENANT_URL + "/{tenantId}/projects";
    public static final String VCENTER_BY_TENANT_URL = TENANT_URL + "/{tenantId}/vcenters";
    public static final String HOST_BY_TENANT_URL = TENANT_URL + "/{tenantId}/hosts";
    public static final String CLUSTER_BY_TENANT_URL = TENANT_URL + "/{tenantId}/clusters";
    public static final String BLOCK_VPOOL_URL = "/block/vpools";
    public static final String FILE_VPOOL_URL = "/file/vpools";
    public static final String OBJECT_VPOOL_URL = "/object/vpools";
    public static final String OBJECT_BUCKET_URL = "/object/buckets";
    public static final String COMPUTE_VPOOL_URL = "/compute/vpools";
    public static final String COMPUTE_SYSTEMS_URL = "/vdc/compute-systems";
    public static final String COMPUTE_ELEMENTS_URL = "/vdc/compute-elements";
    public static final String COMPUTE_ELEMENT_BY_COMPUTE_SYSTEM_URL = COMPUTE_SYSTEMS_URL
            + "/{computeSystemId}/compute-elements";
    public static final String COMPUTE_IMAGE_URL = "/compute/images";
    public static final String COMPUTE_IMAGE_SERVER_URL = "/compute/imageservers";
    public static final String VARRAY_URL = "/vdc/varrays";
    public static final String NETWORK_BY_VARRAY_URL = VARRAY_URL + "/{virtualArrayId}/networks";
    public static final String STORAGE_PORT_BY_VARRAY_URL = VARRAY_URL + "/{virtualArrayId}/storage-ports";
    public static final String AUTO_TIER_BY_VARRAY_URL = VARRAY_URL + "/{id}/auto-tier-policies";
    public static final String AUTO_TIER_FOR_ALL_VARRAY = VARRAY_URL + "/auto-tier-policies";
    public static final String AVAILABLE_ATTRIBUTES_FOR_ALL_VARRAY = VARRAY_URL + "/available-attributes";
    public static final String STORAGE_SYSTEM_URL = "/vdc/storage-systems";
    public static final String STORAGE_POOL_BY_STORAGE_SYSTEM_URL = STORAGE_SYSTEM_URL
            + "/{storageSystemId}/storage-pools";
    public static final String STORAGE_PORT_BY_STORAGE_SYSTEM_URL = STORAGE_SYSTEM_URL
            + "/{storageSystemId}/storage-ports";
    public static final String AUTO_TIER_BY_STORAGE_SYSTEM_URL = STORAGE_SYSTEM_URL
            + "/{storageSystemId}/auto-tier-policies";
    public static final String STORAGE_POOL_URL = "/vdc/storage-pools";
    public static final String STORAGE_TIER_BY_STORAGE_POOL = STORAGE_POOL_URL + "/{id}/storage-tiers";
    public static final String STORAGE_TIER_URL = "/vdc/storage-tiers";
    public static final String STORAGE_PORT_URL = "/vdc/storage-ports";
    public static final String PROTECTION_SYSTEM_URL = "/vdc/protection-systems";
    public static final String FILE_SNAPSHOT_URL = "/file/snapshots";
    public static final String BLOCK_SNAPSHOT_URL = "/block/snapshots";
    public static final String BLOCK_CONSISTENCY_GROUP_URL = "/block/consistency-groups";
    public static final String NETWORK_URL = "/vdc/networks";
    public static final String IP_INTERFACES_BY_NETWORK_URL = NETWORK_URL + "/{id}/ip-interfaces";
    public static final String INITIATORS_BY_NETWORK_URL = NETWORK_URL + "/{id}/initiators";
    public static final String STORAGE_PORT_BY_NETWORK_URL = NETWORK_URL + "/{id}/storage-ports";
    public static final String EXPORT_GROUP_URL = "/block/exports";
    public static final String STORAGE_PROVIDER_URL = "/vdc/storage-providers";
    public static final String STORAGE_SYSTEM_BY_PROVIDER_URL = STORAGE_PROVIDER_URL + "/{id}/storage-systems";
    public static final String NETWORK_SYSTEM_URL = "/vdc/network-systems";
    public static final String FC_ENDPOINT_URL = NETWORK_SYSTEM_URL + "/{id}/fc-endpoints";
    public static final String SAN_FABRIC_URL = NETWORK_SYSTEM_URL + "/{id}/san-fabrics";
    public static final String SAN_ZONE_URL = SAN_FABRIC_URL + "/{fabric}/san-zones";
    public static final String SAN_ALIAS_URL = NETWORK_SYSTEM_URL + "/{id}/san-aliases";
    public static final String FC_PORT_CONNECTION_URL = "/vdc/fc-port-connections";
    public static final String AUTHN_PROVIDER_URL = "/vdc/admin/authnproviders";
    public static final String WORKFLOW_URL = "/vdc/workflows";
    public static final String WORKFLOW_STEP_URL = "/vdc/workflows/steps";
    public static final String TASK_URL = "/vdc/tasks";
    public static final String HOST_URL = "/compute/hosts";
    public static final String HOST_DETACH_STORAGE_URL = HOST_URL + "/{hostId}/detach-storage";
    public static final String INITIATOR_BY_HOST_URL = HOST_URL + "/{hostId}/initiators";
    public static final String IPINTERFACE_BY_HOST_URL = HOST_URL + "/{hostId}/ip-interfaces";
    public static final String VCENTER_URL = "/compute/vcenters";
    public static final String VCENTER_DETACH_STORAGE_URL = VCENTER_URL + "/{vcenterId}/detach-storage";
    public static final String CLUSTER_BY_VCENTER_URL = VCENTER_URL + "/{vcenterId}/clusters";
    public static final String DATACENTER_BY_VCENTER = VCENTER_URL + "/{vcenterId}/vcenter-data-centers";
    public static final String CLUSTER_URL = "/compute/clusters";
    public static final String CLUSTER_DETACH_STORAGE_URL = CLUSTER_URL + "/{clusterId}/detach-storage";
    public static final String HOST_BY_CLUSTER_URL = CLUSTER_URL + "/{clusterId}/hosts";
    public static final String INITIATOR_URL = "/compute/initiators";
    public static final String IPINTERFACE_URL = "/compute/ip-interfaces";
    public static final String DATACENTER_URL = "/compute/vcenter-data-centers";
    public static final String DATACENTER_DETACH_STORAGE_URL = DATACENTER_URL + "/{dataCenterId}/detach-storage";
    public static final String DATACENTER_CREATE_CLUSTER_URL = DATACENTER_URL + "/{dataCenterId}/create-vcenter-cluster";
    public static final String DATACENTER_UPDATE_CLUSTER_URL = DATACENTER_URL + "/{dataCenterId}/update-vcenter-cluster";
    public static final String HOST_BY_DATACENTER_URL = DATACENTER_URL + "/{dataCenterId}/hosts";
    public static final String CLUSTER_BY_DATACENTER_URL = DATACENTER_URL + "/{dataCenterId}/clusters";
    public static final String AUTO_TIERING_POLICY_URL = "/vdc/auto-tier-policies";
    public static final String STORAGE_TIER_BY_AUTO_TIERING_POLICY_URL = AUTO_TIERING_POLICY_URL
            + "/{id}/storage-tiers";
    public static final String MIGRATION_URL = "/block/migrations";
    public static final String KEYSTORE_URL = "/vdc/keystore";
    public static final String TRUSTSTORE_URL = "/vdc/truststore";
    public static final String TRUSTSTORE_SETTINGS_URL = "/vdc/truststore/settings";
    public static final String PREPARE_VDC_URL = "/vdc/prepare-vdc";

    public static final String UNMANAGED_VOLUMES_URL = "/vdc/unmanaged/volumes";
    public static final String UNMANAGED_VOLUME_BY_STORAGE_SYSTEM_URL = STORAGE_SYSTEM_URL
            + "/{storageSystemId}/unmanaged/volumes";
    public static final String UNMANAGED_VOLUME_BY_HOST_URL = HOST_URL
            + "/{hostId}/unmanaged-volumes";
    public static final String UNMANAGED_VOLUME_BY_CLUSTER_URL = CLUSTER_URL
            + "/{clusterId}/unmanaged-volumes";
    public static final String UNMANAGED_VOLUME_BY_STORAGE_SYSTEM_AND_VIRTUAL_POOL_URL = STORAGE_SYSTEM_URL
            + "/{storageSystemId}/unmanaged/{virtualPool}/volumes";

    public static final String UNMANAGED_CGS_URL = "/vdc/unmanaged/cgs";
    public static final String UNMANAGED_CG_BY_PROTECTION_SYSTEM_URL = PROTECTION_SYSTEM_URL
            + "/{protectionSystemId}/unmanaged/cgs";
    
    public static final String UNMANAGED_EXPORTS_URL = "/vdc/unmanaged/export-masks";
    public static final String UNMANAGED_EXPORTS_BY_HOST_URL = HOST_URL
            + "/{hostId}/unmanaged-export-masks";
    public static final String UNMANAGED_EXPORTS_BY_CLUSTER_URL = CLUSTER_URL
            + "/{clusterId}/unmanaged-export-masks";

    public static final String UNMANAGED_FILESYSTEMS_URL = "/vdc/unmanaged/filesystems";
    public static final String UNMANAGED_FILESYSTEM_BY_STORAGE_SYSTEM_URL = STORAGE_SYSTEM_URL
            + "/{storageSystemId}/unmanaged/filesystems";
    public static final String UNMANAGED_FILESYSTEM_BY_STORAGE_SYSTEM_VIRTUAL_POOL_URL = STORAGE_SYSTEM_URL
            + "/{storageSystemId}/unmanaged/{virtualPool}/filesystems";

    public static final String CUSTOM_CONFIG_URL = "/config/controller";
    public static final String CUSTOM_CONFIG_TYPE_URL = CUSTOM_CONFIG_URL + "/types";
    public static final String CUSTOM_CONFIG_PREVIEW_URL = CUSTOM_CONFIG_URL + "/preview";
    public static final String USER_GROUP_URL = "/vdc/admin/user-groups";
    public static final String CHECK_COMPATIBLE_VDC_URL = "/vdc/check-compatibility";
    public static final String CHECK_IS_GEO_DISTRIBUTED_VDC_URL = "/vdc/check-geo-distributed";

    public static final String VIRTUAL_NAS_SERVER_URL = "/vdc/vnas-servers";
    public static final String VIRTUAL_NAS_SERVER_BY_STORAGE_SYSTEM_URL = "/vdc/storage-systems/{storage-system-id}/vnasservers";
    public static final String VIRTUAL_NAS_SERVER_BY_VARRAY_URL = VIRTUAL_NAS_SERVER_URL + "/varray/{varray-id}";
    public static final String VIRTUAL_NAS_SERVER_BY_PROJECT_URL = "/project/{project-id}";
}
