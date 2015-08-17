/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim.utility;

/**
 * Constants Defined for both Alert and Instance Related Cim indications
 * 
 */
public abstract class CIMConstants {

    // Volume View related Indication Constants
    public static final String VOLUME_VIEW_INDICATION_FREE_CAPACITY = "SourceInstanceEMCSPRemainingManagedSpace";
    public static final String VOLUME_VIEW_INDICATION_POOL_NAME = "SourceInstanceSPPoolID";
    public static final String VOLUME_VIEW_INDICATION_TOTAL_CAPACITY = "SourceInstanceEMCSPTotalManagedSpace";

    // Storage Pool related Indication Constants
    public static final String STORAGE_POOL_INDICATION_FREE_CAPACITY = "SourceInstanceRemainingManagedSpace";
    public static final String STORAGE_POOL_INDICATION_POOL_NAME = "SourceInstancePoolID";
    public static final String STORAGE_POOL_INDICATION_TOTAL_CAPACITY = "SourceInstanceTotalManagedSpace";
    public static final String STORAGE_POOL_INDICATION_SUBSCRIBED_CAPACITY = "SourceInstanceEMCSubscribedCapacity";

    // Instance Indication Constants
    public static final String SOURCE_INSTANCE_SYSTEM_NAME = "SourceInstanceSystemName";
    public static final String SOURCE_INSTANCE_DEVICE_ID = "SourceInstanceDeviceID";
    public static final String SOURCE_INSTANCE_NAME = "SourceInstanceName";
    public static final String ASSOCIATE_STORAGEPOOL_EMC_TOTAL_RAW_CAPACITY = "AssociatedStoragePoolEMCTotalRawCapacity";
    public static final String SOURCE_INSTANCE_PURPOSE = "SourceInstancePurpose";
    public static final String INDICATION_TIME = "IndicationTime";
    public static final String SOURCE_INSTANCE_SS_INSTANCE_ID = "SourceInstanceSSInstanceID";
    public static final String SOURCE_INSTANCE_SP_CREATION_CLASS_NAME = "SourceInstanceSPCreationClassName";

    public static final String SOURCE_INSTANCE_MODEL_PATH_CLASS_SUFFIX_TAG = "SourceInstanceModelPathClassSuffixTag";
    public static final String SOURCE_INSTANCE_MODEL_PATH_CLASS_PREFIX_TAG = "SourceInstanceModelPathClassPrefixTag";
    public static final String SOURCE_INSTANCE_MODEL_PATH_COMPOSITE_ID = "SourceInstanceModelPathCompositeID";
    public static final String SOURCE_INSTANCE_MODEL_PATH_SP_INSTANCE_ID = "SourceInstanceModelPathSPInstanceID";
    public static final String SOURCE_INSTANCE_MODEL_PATH_INSTANCE_ID = "SourceInstanceModelPathInstanceID";

    // Alert Indication Constants
    public static final String ALERT_MANAGED_ELEMENT_CLASS_SUFFIX_TAG = "AlertingManagedElementClassSuffixTag";
    public static final String ALERT_MANAGED_ELEMENT_COMPOSITE_ID = "AlertingManagedElementCompositeID";
    public static final String ALERT_TYPE_TAG = "AlertTypeTag";
    public static final String OTHER_ALERT_TYPE_EVENT_ID = "OtherAlertTypeEventID";
    public static final String PROBABLE_CAUSE = "ProbableCause";
    public static final String INDICATION_CLASS_NAME = "IndicationClassName";
    public static final String PROBABLE_CAUSE_DESCRIPTION = "ProbableCauseDescription";
    // Specific to VMAX.
    public static final String DESCRIPTION = "Description";

    public static final String PROVIDER_NAME = "ProviderName";
    // Usually this severity will be used in block related alerts
    public static final String PERCEIVED_SEVERITY = "PerceivedSeverity";
    // Usually this severity will be available in file related alerts
    public static final String OTHER_SEVERITY = "OtherSeverity";
    public static final String SOURCE_INSTANCE_OPERATIONAL_STATUS_CODES = "SourceInstanceOperationalStatus";
    public static final String SOURCE_INSTANCE_OPERATIONAL_STATUS_DESCRIPTIONS = "SourceInstanceStatusDescriptions";

    // COMMON Constants
    public static final String INDICATION_SOURCE = "IndicationSource";
    public static final String CIM_INDICATION_TYPE = "CimIndicationType";
    public static final String INDICATION_IDENTIFIER = "IndicationIdentifier";
    public static final String VOLUME_PREFIX = "+VOLUME+";
    public static final String CLARIION_PREFIX = "CLARiiON";
    public static final String SYMMETRIX_PREFIX = "SYMMETRIX";
    public static final String CLARIION_PREFIX_TO_UPPER = "CLARIION";

    /**
     * This constant represents the event type to find from the CIM indication.
     * The same field will also be used to determine Alert Type as well.
     */
    public static final String INDICATION_CLASS_TAG = "IndicationClassTag";
    // Event type representations
    public static final String INST_CREATION_EVENT = "InstCreation";
    public static final String INST_MODIFICATION_EVENT = "InstModification";
    public static final String INST_DELETION_EVENT = "InstDeletion";

    // The separator for event extensions

    public static final char EXTENSION_SEPARATOR = ';';
    public static final String COMMA_SEPERATOR = ",";

    // Labels for extension properties for Events.
    public static final String OPERATIONAL_STATUS_EXTENSION = "Operational Status";
    public static final String EVENT_SOURCE = "EventSource";

    // Interval processor in Minutes
    public static final long INDICATION_PROCESS_INTERVAL = 2;

    public static final String FC_PORT_CLASS_SUFFIX = "FrontEndFCPort";
    public static final String iSCSI_PORT_CLASS_SUFFIX = "iSCSIProtocolEndpoint";

}
