/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.ibm;

import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public interface IBMSmisConstants extends SmisConstants {
    static final String DUPLICATED_CG_NAME_ERROR = "Consistency Group name already exists.";
    static final String DUPLICATED_SG_NAME_ERROR = "Snapshot Group name already exists on array.";
    static final String STORAGE_CONFIGURATION_SERVICE_NAME = "StorageConfigurationService for ";
    static final String CP_STORAGE_VOLUME = "IBMTSDS_SEVolume";
    static final String DATA_TYPE_SETTING = "IBMTSDS_DataTypeSetting";
    static final String SYSTEM_BLOCK_SIZE = "IBMTSDS:SystemBlockSize";
    static final String CP_QUANTITY = "Quantity";
    
    static final String CP_VIRTUAL_SPACE_CONSUMED = "VirtualSpaceConsumed";
    static final String[] PS_POOL_CAPACITIES = new String[] { CP_REMAININGMANAGEDSPACE, CP_VIRTUAL_SPACE_CONSUMED };
    static final String CP_RETURN_CODE = "ReturnCode";
    static final String CP_RETURN_CODES = "ReturnCodes";
    
    static final String STORAGE_HARDWARE_ID_MANAGEMENT_SERVICE_NAME = "StorageHardwareIDManagementService for ";
    static final String CP_SYSTEM_SPECIFIC_COLLECTION = "IBMTSDS_SystemSpecificCollection";
    static final String CP_COLLECTION = "Collection";
    static final String CP_MEMBER = "Member";
    static final String CP_STORAGE_HARDWARE_ID = "IBMTSDS_StorageHardwareID";
    static final String CP_SYS_SPECIFIC_COLLECTION_TO_SHWID = "IBMTSDS_SystemSpecificCollectionToStorageHardwareID";
    static final String CP_SHWID_TO_SPC = "IBMTSDS_SHWIDToSPC";
    static final String CP_HARDWARE_IDS = "HardwareIDs";
    static final String CP_HARDWARE_ID_COLLECTION = "HardwareIDCollection";
    static final String CREATE_HARDWARE_ID_COLLECTION = "CreateHardwareIDCollection";
    static final String ADD_HARDWARE_IDS_TO_COLLECTION = "AddHardwareIDsToCollection";

    static final String CONTROLLER_CONFIGURATION_SERVICE_NAME = "ControllerConfigurationService for ";
    static final String CP_SCSI_PROTOCOL_CONTROLLER = "IBMTSDS_SCSIProtocolController";
        
    static final String CP_PROTOCOL_CONTROLLER_FOR_PORT = "CIM_ProtocolControllerForPort";
    static final String CP_LOGICALPORT = "CIM_LogicalPort";
    static final String[] PS_CNTRL_NAME_AND_ID = new String[] { CP_NAME, CP_SYSTEM_NAME, CP_DEVICE_ID };
    
    static final String CP_PERMANENT_ADDRESS = "PermanentAddress";
    static final String[] PS_PERMANENT_ADDRESS = new String[] { CP_PERMANENT_ADDRESS };    
    static final String PORT_NETWORK_ID = "portNetworkId";
    
    static final String EXPOSE_PATHS = "ExposePaths";
    static final String HIDE_PATHS = "HidePaths";
    static final String DELETE_PROTOCOL_CONTROLLER = "DeleteProtocolController";
    
    static final String REPLICATION_SERVICE_NAME = "ReplicationService for ";
    static final String CP_CONSISTENCY_GROUP = "IBMTSDS_ConsistencyGroup";
    static final String CP_SNAPSHOT_GROUP = "IBMTSDS_SnapshotGroup";
    static final String CP_GROUP_SYNCHRONIZED = "IBMTSDS_GroupSynchronized";
    static final String CP_STORAGE_SYNCHRONIZED = "IBMTSDS_StorageSynchronized";
    static final String CP_TARGET_ELEMENT = "TargetElement";
    static final String CP_TARGET_GROUP = "TargetGroup";
    static final String CP_SYNCHRONIZATION = "Synchronization";
    static final String CP_SNAPSHOT_GROUP_TO_ORDERED_MEMBERS = "IBMTSDS_SnapshotGroupToOrderedMembers";
    static final String MODIFY_LIST_SYNCHRONIZATION = "ModifyListSynchronization";
}
