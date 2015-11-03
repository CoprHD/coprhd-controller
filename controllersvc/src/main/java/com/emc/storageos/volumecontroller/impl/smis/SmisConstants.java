/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger16;
import javax.cim.UnsignedInteger32;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.plugins.common.Constants;

public interface SmisConstants {
    static final int INITIATOR_GROUP_TYPE = 2;
    static final int TARGET_PORT_GROUP_TYPE = 3;
    static final int VOLUME_GROUP_TYPE = 4;
    static final int REMOVE_GROUP_TYPE = 3;
    static final int ADD_SYNC_PAIR = 5;
    static final int REMOVE_SYNC_PAIR = 13;
    static final int BROKEN = 5;
    static final int FRACTURED = 6;
    static final int SPLIT = 7;
    static final int SYNCHRONIZED = 4;
    static final int SUSPENDED = 9;
    static final int FAILED_OVER = 10;
    static final int FAILBACK_SYNC_PAIR = 11;
    static final int FAILOVER_SYNC_PAIR = 10;
    static final int SUSPEND_SYNC_PAIR = 22;
    static final int SWAP_SYNC_PAIR = 20;
    static final int RESUME_SYNC_PAIR = 16;
    static final int ELEMENT_TYPE = 1;
    static final int READ_WRITE_DISABLED = 4;
    static final int RESYNC_SYNC_PAIR = 14;
    static final int MAX_CG_NAME_LENGTH = 8;
    static final int DELETE_SNAPSHOT = 19;
    static final int DEACTIVATE_SNAPSHOT = 7;
    static final int RESTORE_FROM_REPLICA = 15;
    static final int RESTORE_FROM_SYNC_SETTINGS = 7;
    static final int DELETE_FROM_SYNC_SETTINGS = 4;
    static final int RESUME_FROM_SYNC_SETTINGS = 0x8000;
    static final int STORAGE_VOLUME_VALUE = 2;
    static final int STORAGE_VOLUME_TYPE_THIN = 5;
    static final int STORAGE_VOLUME_FULLY_ALLOCATED = 7;
    static final int THIN_STORAGE_VOLUME = 32768;
    static final int MIRROR_VALUE = 6;
    static final int LOCAL_LOCALITY_VALUE = 2;
    static final int REMOTE_LOCALITY_VALUE = 3;
    static final int SYNCHRONOUS_MODE_VALUE = 2;
    static final int CLONE_VALUE = 8;
    static final int FRACTURE_VALUE = 12;
    static final int RESTORED_COPY_STATE = 18;
    static final String SNAPSHOT_SYNC_TYPE_STR = "7";
    static final String RESTORED_SYNC_STATE_STR = "7";
    static final int RESYNC_VALUE = 14;
    static final int ACTIVATE_CONSISTENCY_VALUE = 3;
    static final int RESET_TO_SYNC_VALUE = 17;
    static final int RESET_TO_ASYNC_VALUE = 18;
    static final int RESET_TO_ADAPTIVE_VALUE = 25;
    static final int SPLIT_VALUE = 21;
    static final int DETACH_VALUE = 8;
    static final int NON_COPY_STATE = -1;
    static final int SNAPSHOT_VALUE = 7;
    static final int READ_WRITE_VALUE = 2;
    static final int MAX_VMAX_RELATIONSHIP_NAME = 5;
    static final int MAX_SMI80_RELATIONSHIP_NAME = 63;
    static final int MAX_SNAPSHOT_NAME_LENGTH = 63;
    static final int MAX_SMI80_SNAPSHOT_NAME_LENGTH = 31;
    static final int MAX_VOLUME_NAME_LENGTH = 63;
    static final int ACTIVATE_VALUE = 4;
    static final int INACTIVE_VALUE = 8;
    static final int PREPARED_VALUE = 11;
    static final int COPY_TO_TARGET_VALUE = 5;
    static final int PORT_WWN_ID_TYPE_VALUE = 2;
    static final int PORT_ISCSI_ID_TYPE_VALUE = 5;
    static final int CONCATENATED_META_VOLUME_TYPE = 2;
    static final int STRIPED_META_VOLUME_TYPE = 3;
    static final int TIERING_GROUP_TYPE = 5;
    static final int TIERING_POLICY_REMOVE_TYPE = 6;
    static final int DELTA_REPLICA_TARGET_VALUE = 12;
    static final int DEFAULT_SETTING_TYPE_VALUE = 2;
    static final int RETURN_TO_RESOURCE_POOL = 19;
    static final int MAX_STORAGE_GROUP_NAME_LENGTH = 64;
    static final int COMPOSITE_ELEMENT_MEMBER = 15;
    static final int NO_CONSISTENCY = 2;
    static final int FILTER_CONNECTED_VALUE = 2;
    static final int STORAGE_SYNCHRONIZED_VALUE = 2;
    static final int VP_SNAP_VALUE = 32768;
    static final int INSTRUMENTATION_DECIDES_VALUE = 2;
    static final int CREATE_NEW_TARGET_VALUE = 2;
    static final int STORAGE_ELEMENT_CAPACITY_EXPANSION_VALUE = 12;

    static final String SE_REPLICATIONSETTING_DATA = "SE_ReplicationSettingData";
    static final String CP_REPLICATIONSETTING_DATA = "ReplicationSettingData";

    static final int DISSOLVE_VALUE = 9;
    static final int SNAPSHOT_REPLICATION_TYPE = 6;
    static final int MIRROR_REPLICATION_TYPE = 2;
    static final int EMULATION_VALUE = 10;

    static final String DEFAULT_INSTANCE = "DefaultInstance";
    static final String EMC_CONSISTENCY_EXEMPT = "EMCConsistencyExempt";
    static final String CONSISTENCY_EXEMPT = "ConsistencyExempt";
    static final String EMPTY_STRING = "";
    static final String ROOT_EMC_NAMESPACE = "root/emc";
    static final String STORAGE_CONFIGURATION_SERVICE = "_StorageConfigurationService";
    static final String CONTROLLER_CONFIGURATION_SERVICE = "_ControllerConfigurationService";
    static final String TIER_POLICY_SERVICE = "_TierPolicyService";
    static final String TIER_POLICY_RULE = "_TierPolicyRule";
    static final String STORAGE_POOL_SETTING = "_StoragePoolSetting";
    static final String DEVICE_STORAGE_POOL = "_DeviceStoragePool";
    static final String STORAGE_VOLUME = "_StorageVolume";
    static final String FC_SCSI_PROTOCOL_ENDPOINT = "_FCSCSIProtocolEndpoint";
    static final String ISCSI_PROTOCOL_ENDPOINT = "_iSCSIProtocolEndpoint";
    static final String STORAGE_SYSTEM = "_StorageSystem";
    static final String STORAGE_PROCESSOR_SYSTEM = "_StorageProcessorSystem";
    static final String LUN_MASKING_VIEW = "_LunMaskingView";
    static final String REPLICATION_SERVICE = "_ReplicationService";
    static final String PROTECTION_SERVICE = "_StorageProtectionService";
    static final String REPLICATION_SERVICE_CAPABILTIES = "_ReplicationServiceCapabilities";
    static final String STORAGE_HARDWARE_ID_MANAGEMENT_SERVICE = "_StorageHardwareIDManagementService";
    static final String EMC_STORAGE_HARDWARE_ID_MANAGEMENT_SERVICE = "EMCStorageHardwareIDManagementService";
    static final String SE_STORAGE_SYNCHRONIZED_SV_SV = "SE_StorageSynchronized_SV_SV";
    static final String MODIFY_REPLICA_SYNCHRONIZATION = "ModifyReplicaSynchronization";
    static final String MODIFY_LIST_REPLICA_SYNCHRONIZATION = "ModifyListSynchronization";
    static final String GET_REPLICATION_RELATIONSHIPS = "GetReplicationRelationships";
    static final String PROTECT = "Protect";
    static final String CREATE_ELEMENT_REPLICA = "CreateElementReplica";
    static final String CREATE_OR_MODIFY_ELEMENT_FROM_STORAGE_POOL = "CreateOrModifyElementFromStoragePool";
    static final String CREATE_OR_MODIFY_ELEMENTS_FROM_STORAGE_POOL = "CreateOrModifyElementsFromStoragePool";
    static final String EMC_CREATE_MULTIPLE_TYPE_ELEMENTS_FROM_STORAGE_POOL = "EMCCreateMultipleTypeElementsFromStoragePool";
    static final String JOB = "Job";
    static final String CREATE_GROUP = "CreateGroup";
    static final String SE_REPLICATION_GROUP = "SE_ReplicationGroup";
    static final String SE_MEMBER_OF_COLLECTION_DMG_DMG = "SE_MemberOfCollection_DMG_DMG";
    static final String SE_MEMBER_OF_COLLECTION_IMG_IMG = "SE_MemberOfCollection_IMG_IMG";
    static final String MEMBER = "Member";
    static final String CREATE_GROUP_REPLICA = "CreateGroupReplica";
    static final String CREATE_LIST_REPLICA = "CreateListReplica";
    static final String CREATE_GROUP_REPLICA_FROM_ELEMENT_SYNCHRONIZATIONS = "CreateGroupReplicaFromElementSynchronizations";
    static final String CLAR_SETTINGS_DEFINE_STATE_SV_SAFS = "Clar_SettingsDefineState_SV_SAFS";
    static final String SYMM_SETTINGS_DEFINE_STATE_SV_SAFS = "Symm_SettingsDefineState_SV_SAFS";
    static final String CLAR_SYNCHRONIZATION_ASPECT_FOR_SOURCE = "Clar_SynchronizationAspectForSource";
    static final String SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE = "Symm_SynchronizationAspectForSource";
    static final String CLAR_STORAGE_POOL_SETTING = "Clar_StoragePoolSetting";
    static final String SYMM_STORAGE_POOL_SETTING = "Symm_StoragePoolSetting";
    static final String CIM_STORAGE_VOLUME = "CIM_StorageVolume";
    static final String CIM_ALLOCATED_FROM_STORAGEPOOL = "CIM_AllocatedFromStoragePool";
    static final String CIM_SETTINGS_DEFINE_STATE = "CIM_SettingsDefineState";
    static final String CIM_PROTOCOL_CONTROLLER_FOR_UNIT = "CIM_ProtocolControllerForUnit";
    static final String CIM_ELEMENTCAPABILITIES = "CIM_ElementCapabilities";
    static final String EMC_COPY_STATE_DESC = "EMCCopyStateDesc";
    static final String ACTIVE = "ACTIVE";
    static final String CIM_ORDERED_MEMBER_OF_COLLECTION = "CIM_OrderedMemberOfCollection";
    static final String ADD_MEMBERS = "AddMembers";
    static final String MOVE_MEMBERS = "MoveMembers";
    static final String EMC_RETURN_TO_STORAGE_POOL = "EMCReturnToStoragePool";
    static final String RETURN_TO_STORAGE_POOL = "ReturnToStoragePool";
    static final String RETURN_ELEMENTS_TO_STORAGE_POOL = "ReturnElementsToStoragePool";
    static final int CONTINUE_ON_NONEXISTENT_ELEMENT = 2;
    static final String REMOVE_MEMBERS = "RemoveMembers";
    static final String RELATIONSHIP_NAME = "RelationshipName";
    static final String SE_GROUP_SYNCHRONIZED_RG_RG = "SE_GroupSynchronized_RG_RG";
    static final String DELETE_GROUP = "DeleteGroup";
    static final String MODIFY_SETTINGS_DEFINE_STATE = "ModifySettingsDefineState";
    static final String CLAR_SETTINGS_DEFINE_STATE_RG_SAFS = "Clar_SettingsDefineState_RG_SAFS";
    static final String SYMM_SETTINGS_DEFINE_STATE_RG_SAFS = "Symm_SettingsDefineState_RG_SAFS";
    static final String CLAR_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP = "Clar_SynchronizationAspectForSourceGroup";
    static final String SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP = "Symm_SynchronizationAspectForSourceGroup";
    static final String SE_SYSTEM_REGISTRATION_SERVICE = "SE_SystemRegistrationService";
    static final String SYSTEM_REGISTRATION_SERVICE = "SystemRegistrationService";
    static final String EMC_SYSTEM_REGISTRATION_SERVICE = "EMC_SystemRegistrationService";
    static final String EMC_SMI_S_PROVIDER = "EMC_SMI-S_Provider";
    static final String EMC_REPLICATION_SERVICE = "EMCReplicationService";
    static final String EMC_PROTECTION_SERVICE = "StorageProtectionService";
    static final String EMC_CONTROLLER_CONFIGURATION_SERVICE = "EMCControllerConfigurationService";
    static final String EMC_STORAGE_CONFIGURATION_SERVICE = "EMCStorageConfigurationService";
    static final String EMC_TIER_POLICY_SERVICE = "EMC_TierPolicyService";
    static final String EMC_REFRESH_SYSTEM = "EMCRefreshSystem";
    static final String EMC_ADD_SYSTEM = "EMCAddSystem";
    static final String EMC_REMOVE_SYSTEM = "EMCRemoveSystem";
    static final String CREATE_SYNCHRONIZATION_ASPECT = "CreateSynchronizationAspect";
    static final String EMC_BIND_ELEMENT = "EMCBindElement";
    static final String EMC_MANUALLY_REGISTER_HOST_INITIATORS = "EMCManuallyRegisterHostInitiators";
    static final String CP_CREATE_SETTING = "createSetting";
    static final String CP_SETTING_TYPE = "SettingType";
    static final String CP_NEWSETTING = "NewSetting";
    static final int DEFAULT_SETTING_TYPE = 2;
    static final String CP_SYNCPAIR = "SyncPair";
    static final String CP_THIN_VOLUME_INITIAL_RESERVE = "ThinProvisionedInitialReserve";
    static final String CLAR_LUN_MASKING_SCSI_PROTOCOL_CONTROLLER = "Clar_LunMaskingSCSIProtocolController";
    static final String STORAGE_HARDWARE_ID_MGMT_SVC = "_StorageHardwareIDManagementService";
    static final String SYMM_LUN_MASKING_VIEW = "Symm_LunMaskingView";
    static final String LUN_MASKING_SCSI_PROTOCOL_CONTROLLER = "_LunMaskingSCSIProtocolController";
    static final String CREATE_STORAGE_HARDWARE_ID = "CreateStorageHardwareID";
    static final String SYMM_LUNMASKINGVIEW = "Symm_LunMaskingView";
    static final String SYMM_STORAGEPOOL_CAPABILITIES = "Symm_StoragePoolCapabilities";
    static final String SYMM_VIRTUAL_PROVISIONING_POOL = "Symm_VirtualProvisioningPool";
    static final String SE_DEVICE_MASKING_GROUP = "SE_DeviceMaskingGroup";
    static final String DELETE_STORAGE_HARDWARE_ID = "DeleteStorageHardwareID";
    static final String ELEMENT_COMPOSITION_SERVICE = "_ElementCompositionService";
    static final String EMC_ELEMENT_COMPOSITION_SERVICE = "EMCElementCompositionService";
    static final String CREATE_OR_MODIFY_COMPOSITE_ELEMENT = "CreateOrModifyCompositeElement";
    static final String CIM_STORAGE_SYNCHRONIZED = "CIM_StorageSynchronized";
    static final String CIM_GROUP_SYNCHRONIZED = "CIM_GroupSynchronized";
    static final String CP_CLAR_FCSCSI_PROTOCOL_ENDPOINT = "Clar_FCSCSIProtocolEndpoint";
    static final String CP_SYMM_FCSCSI_PROTOCOL_ENDPOINT = "Symm_FCSCSIProtocolEndpoint";
    static final String CP_CLAR_ISCSI_PROTOCOL_ENDPOINT = "Clar_iSCSIProtocolEndpoint";
    static final String CP_SYMM_ISCSI_PROTOCOL_ENDPOINT = "Symm_iSCSIProtocolEndpoint";
    static final String CP_CLAR_FRONTEND_FC_PORT = "Clar_FrontEndFCPort";
    static final String CP_PERMANENT_ADDRESS = "PermanentAddress";
    static final String CIM_PROTOCOL_ENDPOINT = "CIM_ProtocolEndpoint";
    static final String CREATE_SETTING = "CreateSetting";
    static final String NEW_SETTING = "NewSetting";
    static final String LUNMASKING = "LunMasking";
    static final String ANTECEDENT = "Antecedent";
    static final String SYMM_STORAGE_POOL_CAPABILITIES = "Symm_StoragePoolCapabilities";
    static final String SYMM_SNAP_STORAGE_POOL = "Symm_SnapStoragePool";
    static final String VDEV_STORAGE_SETTING = "VDEV_STORAGE_SETTING";
    static final String TARGET_GROUP_NAME_FMT = "%s-TARGET-GROUP";
    static final String SE_INITIATOR_MASKING_GROUP = "SE_InitiatorMaskingGroup";
    static final String EMC_CLAR_PRIVILEGE = "Clar_Privilege";
    static final String SE_RemoteReplicationCollection = "Symm_RemoteReplicationCollection";
    static final String CP_CONNECTIVITY_COLLECTION = "ConnectivityCollection";
    static final String CP_MODE = "Mode";
    static final String NAME = "NAME";
    static final String CP_CONSISTENCY = "Consistency";
    static final String GET_SUPPORTED_OPERATIONS = "GetSupportedOperations";
    static final String SUPPORTED_OPERATIONS = "SupportedOperations";
    static final String COPY_STATE_RESTORED_INT_VALUE = "18";
    static final String COPY_STATE_MIXED_INT_VALUE = "14";
    static final String STORAGE_VOLUME_CLASS = "CIM_StorageVolume";
    static final String NOT_READY = "NOT_READY";
    static final String EMC_GET_TARGET_ENDPOINTS = "EMCGetTargetEndpoints";
    static final String EMC_STORAGE_TIER_METHODOLOGY = "EMCStorageTierMethodology";
    static final String CP_CLAR_PRIVILEGE_MGMT_SVC = "Clar_PrivilegeManagementService";
    static final String EMC_PRIVILEGE_MANAGEMENT_SERVICE = "EMC_PrivilegeManagementService";
    static final String GET_DEFAULT_REPLICATION_SETTING_DATA = "GetDefaultReplicationSettingData";
    static final String SYNCHRONIZATIONS = "Synchronizations";
    static final String DESIRED_COPY_METHODOLOGY = "DesiredCopyMethodology";
    static final String TARGET_ELEMENT_SUPPLIER = "TargetElementSupplier";
    static final String THIN_PROVISIONING_POLICY = "ThinProvisioningPolicy";
    static final String EMC_SETUNSET_RECOVERPOINT = "EMCSetUnsetRecoverPoint";
    public static final String EMC_BOUND_TO_THIN_STORAGE_POOL = "EMCBoundToThinStoragePool";
    static final String EMC_STORAGE_CONFIGURATION_CAPABILITIES = "EMC_StorageConfigurationCapabilities";

    // Constants used for VMAX Masking names
    static public char MASK_NAME_DELIMITER = '_';
    static public String PORT_GROUP_SUFFIX = "PG";
    static public String CASCADED_IG_SUFFIX = "CIG";
    static public String CASCADED_SG_SUFFIX = "CSG";
    static public String INITIATOR_GROUP_SUFFIX = "IG";
    // CIMProperty (CP) names used in this class
    static final String CP_EMC_HOST_NAME = "EMCHostName";
    static final String CP_TARGET_PORT_IDS = "TargetPortIDs";
    static final String CP_CREATION_CLASS_NAME = "CreationClassName";
    static final String CP_SYSTEM_CREATION_CLASS_NAME = "SystemCreationClassName";
    static final String CP_SYSTEM_NAME = "SystemName";
    static final String CP_NAME = "Name";
    static final String CP_DEVICE_ID = "DeviceID";
    static final String CP_SYNC_STATE = "SyncState";
    static final String CP_SYNC_TYPE = "SyncType";
    static final String CP_SOURCE_ELEMENT = "SourceElement";
    static final String CP_SOURCE_ELEMENTS = "SourceElements";
    static final String CP_TARGET_ELEMENTS = "TargetElements";
    static final String CP_INSTANCE_ID = "InstanceID";
    static final String CP_ELEMENT_TYPE = "ElementType";
    static final String CP_IN_POOL = "InPool";
    static final String CP_GOAL = "Goal";
    static final String CP_SIZE = "Size";
    static final String CP_GROUP_NAME = "GroupName";
    static final String CP_POLICY_RULE = "PolicyRule";
    static final String CP_TYPE = "Type";
    static final String CP_MEMBERS = "Members";
    static final String CP_DELETE_WHEN_BECOMES_UNASSOCIATED = "DeleteWhenBecomesUnassociated";
    static final String CP_MASKING_GROUP = "MaskingGroup";
    static final String CP_ELEMENT_NAME = "ElementName";
    static final String CP_ELEMENT_NAMES = "ElementNames";
    static final String CP_ASSOCIATED_TO_VIEW = "EMCAssociatedToView";
    static final String CP_FAST_SETTING = "EMCFastSetting";
    static final String CP_DISK_DRIVE_TYPE = "EMCDiskDriveType";
    static final String CP_DEVICE_MASKING_GROUP = "DeviceMaskingGroup";
    static final String CP_SOURCE_MASKING_GROUP = "SourceMaskingGroup";
    static final String CP_TARGET_MASKING_GROUP = "TargetMaskingGroup";
    static final String CP_INITIATOR_MASKING_GROUP = "InitiatorMaskingGroup";
    static final String CP_PROTOCOL_CONTROLLER = "ProtocolController";
    static final String CP_EMC_ELEMENT_NAME = "EMCElementName";
    static final String CP_DEVICE_ACCESSES = "DeviceAccesses";
    static final String CP_LU_NAMES = "LUNames";
    static final String CP_DEVICE_NUMBER = "DeviceNumber";
    static final String CP_DEVICE_NUMBERS = "DeviceNumbers";
    static final String CP_INITIATOR_PORT_IDS = "InitiatorPortIDs";
    static final String CP_PROTOCOL_CONTROLLERS = "ProtocolControllers";
    static final String CP_SE_STORAGE_HARDWARE_ID = "SE_StorageHardwareID";
    static final String CP_THE_ELEMENTS = "TheElements";
    static final String CP_THE_ELEMENT = "TheElement";
    static final String CP_OPERATION = "Operation";
    static final String CP_STORAGE_ID = "StorageID";
    static final String CP_ID_TYPE = "IDType";
    static final String CP_EMC_NODE_ID = "EMCNodeID";
    static final String CP_FORCE = "Force";
    static final String CP_REPLICATION_SETTING_DATA = "ReplicationSettingData";
    static final String CP_ELEMENT_SYNCHRONIZATIONS = "ElementSynchronizations";
    static final String CP_DELETE_ON_EMPTY_ELEMENT = "DeleteOnEmptyElement";
    static final String CP_ERROR_DESCRIPTION = "ErrorDescription";
    static final String CP_SYNCHRONIZATION = "Synchronization";
    static final String CP_ELEMENT = "Element";
    static final String CP_ELEMENTTYPE = "ElementType";
    static final String CP_ACCESS = "Access";
    static final String CP_SOURCE_GROUP = "SourceGroup";
    static final String CP_SOURCE_VOLUME = "SourceElement";
    static final String CP_REPLICATION_GROUP = "ReplicationGroup";
    static final String CP_OPERATIONAL_STATUS = "OperationalStatus";
    static final String CP_PERCENT_COMPLETE = "PercentComplete";
    static final String CP_EMC_SYNCHRONOUS_ACTION = "EMCSynchronousAction";
    static final String CP_SETTINGS_STATE = "SettingsState";
    static final String CP_MANAGED_ELEMENT = "ManagedElement";
    static final String CP_SETTING_DATA = "SettingData";
    static final String CP_WWN_NAME = "EMCWWN";
    static final String CP_WWN_NAME_ALT = "EMCSVWWN";
    static final String CP_SYNCED_ELEMENT = "SyncedElement";
    static final String CP_SYSTEM_ELEMENT = "SystemElement";
    static final String CP_REMOVE_ELEMENTS = "RemoveElements";
    static final String CP_THINLY_PROVISIONED = "ThinlyProvisioned";
    static final String CP_EMC_NUMBER_OF_DEVICES = "EMCNumberOfDevices";
    static final String CP_EMC_NUMBER_OF_DEVICE_FOR_EACH_CONFIG = "EMCNumberOfDeviceForEachConfig";
    static final String CP_WAIT_FOR_COPY_STATE = "WaitForCopyState";
    static final String CP_SYSTEMS = "Systems";
    static final String CP_EMC_SKIP_REFRESH = "EMCSkipRefresh";
    static final String CP_COPY_STATE = "CopyState";
    static final String CP_TARGET_ELEMENT = "TargetElement";
    static final String CP_SPACE_CONSUMED = "SpaceConsumed";
    static final String CP_CONSUMABLE_BLOCKS = "ConsumableBlocks";
    static final String CP_BLOCK_SIZE = "BlockSize";
    static final String CP_DEPENDENT = "Dependent";
    static final String CP_SYMMETRIX = "SYMMETRIX";
    static final String CP_HARDWARE_ID = "HardwareID";
    static final String CP_COMPOSITE_TYPE = "CompositeType";
    static final String CP_IN_ELEMENTS = "InElements";
    static final String CP_EMC_BIND_ELEMENTS = "EMCBindElements";
    static final String CP_TARGET_POOL = "TargetPool";
    static final String CP_EMC_ADAPTER_ROLE = "EMCAdapterRole";
    static final String CP_POLICY_NAME = "PolicyRuleName";
    static final String CP_POLICY_SET = "PolicySet";
    static final String CP_REPLICATION_TYPE = "ReplicationType";
    static final String CP_STATUS_DESCRIPTIONS = "StatusDescriptions";
    static final String CP_CONSISTENT_LUNS = "ConsistentLogicalUnitNumber";
    static final String CP_TARGET_ENDPOINTS = "TargetEndpoints";
    static final String CP_FILTER = "Filter";
    static final String CP_HOSTNAME = "Hostname";
    static final String CP_UNREGISTERED_NODE_IDS = "UnRegisteredNodeIDs";
    static final String CP_UNREGISTERED_STORAGE_IDS = "UnRegisteredStorageIDs";
    static final String CP_UNREGISTERED_STORAGE_TYPE = "UnRegisteredStorageIDType";

    static final String CP_TIERPOLICY_APPLIES_TO_ELEMENT = "Symm_TierPolicySetAppliesToElement";
    static final String CIM_TIER_POLICY_RULE = "CIM_TierPolicyRule";
    static final String CP_TARGET_GROUP = "TargetGroup";
    static final String CP_TARGET_VOLUME = "TargetElement";
    static final String CP_STORAGE_EXTENT_INITIAL_USAGE = "StorageExtentInitialUsage";
    static final String CP_EMC_UNIQUE_ID = "EMCUniqueID";
    static final String CP_OPTIONS = "Options";
    static final String CP_EMC_VSA_ENABLED = "EMCVSAEnabled";
    static final String CP_EMC_IS_BOUND = "EMCIsBound";
    static final String CP_EMC_TOTAL_RAW_CAPACITY = "EMCTotalRawCapacity";
    static final String CP_EMC_REMAINING_RAW_CAPACITY = "EMCRemainingRawCapacity";
    static final String CP_REMAININGMANAGEDSPACE = "RemainingManagedSpace";
    static final String CP_SUBSCRIBEDCAPACITY = "EMCSubscribedCapacity";
    static final String CP_TOTALMANAGEDSPACE = "TotalManagedSpace";
    static final String CP_EMC_PRESERVE_DATA = "EMCPreserveData";
    static final String[] CP_EMC_CLAR_PRIVILEGE = new String[] { SmisConstants.EMC_CLAR_PRIVILEGE };
    static final String CP_EMC_FAILOVER_MODE = "EMCFailoverMode";
    static final String CP_EMC_INITIATOR_TYPE = "EMCInitiatorType";
    static final String CP_VOLUME_USAGE = "Usage";
    static final String CP_EMCMAXSUBSCRIPTIONPERCENT = "EMCMaxSubscriptionPercent";
    static final String CP_EMCNUMBEROFMEMBERS = "EMCNumberOfMembers";
    static final String CP_EMC_IN_POOLS = "EMCInPools";
    static final String CP_CONSISTENT_POINT_IN_TIME = "ConsistentPointInTime";
    static final String CP_EXTENT_STRIPE_LENGTH = "ExtentStripeLength";
    static final String CP_LOCALITY = "Locality";
    static final String CP_EMC_SLO = "EMCSLO";
    static final String CP_EMC_SRP = "EMCSRP";
    static final String CP_EMC_WORKLOAD = "EMCWorkload";
    static final String CP_EMC_COLLECTIONS = "EMCCollections";
    static final String CP_COLLECTIONS = "Collections";
    static final String CP_EMC_FORCE = "EMCForce";
    static final String CP_MAX_UNITS_CONTROLLED = "MaxUnitsControlled";
    static final String CP_SUPPORTED_STORAGE_ELEMENT_FEATURES = "SupportedStorageElementFeatures";
    static final String CP_EMC_UNMAP_ELEMENTS = "EMCUnmapElements";

    // Host IO Limit for VMAX
    static final String EMC_MAX_BANDWIDTH = "EMCMaximumBandwidth";
    static final String EMC_MAX_IO = "EMCMaximumIO";

    // Array of Property String (PS) constants
    static final String[] PS_HOST_IO = new String[] {
            SmisConstants.EMC_MAX_BANDWIDTH, SmisConstants.EMC_MAX_IO,
            SmisConstants.CP_ELEMENT_NAME, SmisConstants.CP_EMC_SLO,
            SmisConstants.CP_EMC_SRP, SmisConstants.CP_EMC_WORKLOAD,
            SmisConstants.CP_FAST_SETTING };
    static final String[] PS_ONLY_COPY_STATE = new String[] { CP_COPY_STATE };
    static final String[] PS_ELEMENT_NAME = new String[] { SmisConstants.CP_ELEMENT_NAME };
    static final String[] PS_V3_STORAGE_GROUP_PROPERTIES = new String[] { CP_ELEMENT_NAME, CP_FAST_SETTING, CP_ASSOCIATED_TO_VIEW,
            EMC_MAX_BANDWIDTH, EMC_MAX_IO };
    static final String[] PS_V3_FAST_SETTING_PROPERTIES = new String[] { CP_FAST_SETTING };
    static final String[] PS_V3_VIRTUAL_PROVISIONING_POOL_PROPERTIES = new String[] { CP_DISK_DRIVE_TYPE };
    static final String[] PS_SPACE_CONSUMED = new String[] { SmisConstants.CP_SPACE_CONSUMED };
    static final String[] PS_DEVICE_NUMBER = new String[] { SmisConstants.CP_DEVICE_NUMBER };
    static final String[] PS_THIN_VOLUME_INITIAL_RESERVE = new String[] { SmisConstants.CP_THIN_VOLUME_INITIAL_RESERVE };
    static final String[] PS_EMC_STORAGE_TIER_METHODOLOGY = new String[] { SmisConstants.EMC_STORAGE_TIER_METHODOLOGY };
    static final String[] PS_LUN_MASKING_CNTRL_NAME_AND_ROLE = new String[] { CP_ELEMENT_NAME, CP_EMC_ADAPTER_ROLE, CP_SYSTEM_NAME,
            CP_DEVICE_ID };
    static final String[] PS_EMCWWN = new String[] { CP_WWN_NAME };
    static final String[] PS_STORAGE_ID = new String[] { CP_STORAGE_ID };
    static final String[] PS_NAME = new String[] { CP_NAME };
    static final String[] PS_EMC_VSA_ENABLED = new String[] { CP_EMC_VSA_ENABLED };
    static final String[] PS_EMC_CLAR_PRIVILEGE = new String[] { CP_EMC_FAILOVER_MODE, CP_EMC_INITIATOR_TYPE };
    static final String[] PS_DEVICE_ID = new String[] { CP_DEVICE_ID };
    static final String[] PS_EMC_HOST_NAME = new String[] { CP_EMC_HOST_NAME };
    static final String[] PS_SUPPORTED_STORAGE_ELEMENT_FEATURES = new String[] { CP_SUPPORTED_STORAGE_ELEMENT_FEATURES };
    static final String NONE = "NONE";
    // EMCRecoverPointEnabled tag required on VMAX volumes for working with RP.

    static final String[] PS_COPY_STATE_AND_DESC = new String[] { CP_COPY_STATE, EMC_COPY_STATE_DESC };
    static final String[] PS_COPY_STATE_AND_DESC_SYNCTYPE = new String[] { CP_COPY_STATE, EMC_COPY_STATE_DESC, CP_SYNC_TYPE };
    static final String[] PS_THINLY_PROVISIONED = new String[] { CP_THINLY_PROVISIONED };

    // EMCRecoverPointEnabled tag required on VMAX volumes for working with RP.
    static final String EMC_RECOVERPOINT_ENABLED = "EMCRecoverPointEnabled";
    static final String[] CP_EMC_RECOVERPOINT_ENABLED = new String[] { SmisConstants.EMC_RECOVERPOINT_ENABLED };
    static final String CP_PERCENT_SYNCED = "PercentSynced";
    static final String[] PS_PERCENT_SYNCED = new String[] { CP_PERCENT_SYNCED };
    static final String CP_PROGRESS_STATUS = "ProgressStatus";

    // Network SMIS constants
    static final String CP_NSNAME = "NAME";
    static final String CP_CLASSNAME = "CLASSNAME";
    static final String CP_FABRIC = "FABRIC";
    static final String CP_ACTIVE = "ACTIVE";
    static final String CP_ZMTYPE = "ZMTYPE";
    static final String PATH_PROP_SEP = ";";
    static final String PATH_VAL_SEP = "=";

    // Enums and other constants
    static enum MASKING_GROUP_TYPE {
        SE_DeviceMaskingGroup,
        SE_TargetMaskingGroup,
        SE_InitiatorMaskingGroup
    }

    // sync type enum
    static enum SYNC_TYPE {
        SNAPSHOT(SNAPSHOT_VALUE),
        CLONE(CLONE_VALUE),
        MIRROR(MIRROR_VALUE);

        private final int value;

        private SYNC_TYPE(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final UnsignedInteger16 READ_WRITE_UINT16 = new UnsignedInteger16(READ_WRITE_VALUE);

    public static final CIMObjectPath NULL_CIM_OBJECT_PATH = CimObjectPathCreator.createInstance("CIM_System", Constants.EMC_NAMESPACE);
    public final static CIMObjectPath NULL_IBM_CIM_OBJECT_PATH = CimObjectPathCreator
            .createInstance("CIM_System", Constants.IBM_NAMESPACE);

    public static final UnsignedInteger32 JOB_COMPLETED_NO_ERROR = new UnsignedInteger32(0);
    public static int DIFFERENTIAL_CLONE_VALUE = 5;
    public static int COPY_BEFORE_ACTIVATE = 10;
    public static int PROVISIONING_TARGET_SAME_AS_SOURCE = 5;
    public static int SMIS810_TF_DIFFERENTIAL_CLONE_VALUE = 32770;

}
