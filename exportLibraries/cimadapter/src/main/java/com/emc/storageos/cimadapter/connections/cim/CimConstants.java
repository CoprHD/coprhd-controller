/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
// UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
// notice above does not evidence any actual  or  intended publication of this
// software. Disclosure and dissemination are pursuant to separate agreements.
// Unauthorized use, distribution or dissemination are strictly prohibited.

package com.emc.storageos.cimadapter.connections.cim;

/**
 * Simple constants class.
 */
public abstract class CimConstants {

    // Connection type for a connection to standard CIM SMI-S provider.
    public static final String CIM_CONNECTION_TYPE = "cim";

    // Connection type for a connection to an ECOM provider.
    public static final String ECOM_CONNECTION_TYPE = "ecom";

    // Connection type for a connection to an ECOM provider for file based
    // storage arrays.
    public static final String ECOM_FILE_CONNECTION_TYPE = "ecom_file";

    // Constants used to extract connection parameters from the connection info
    // bean.
    public static final String CIM_TYPE = "cim.type";
    public static final String CIM_HOST = "cim.host";
    public static final String CIM_PORT = "cim.port";
    public static final String CIM_USER = "cim.user";
    public static final String CIM_INTEROP_NS = "cim.interopNS";
    public static final String CIM_IMPL_NS = "cim.implNS";
    public static final String CIM_PW = "cim.password";
    public static final String CIM_USE_SSL = "cim.useSSL";

    // Default host name for a CIM connection.
    public static final String DFLT_CIM_CONNECTION_HOST = "localhost";

    // Default port for a CIM connection
    public static final int DFLT_CIM_CONNECTION_PORT = 5988;

    // Default connection protocol.
    public static final String DEFAULT_PROTOCOL = "http";

    // Default interop namespace for a CIM connection
    public static final String DFLT_CIM_CONNECTION_INTEROP_NS = "interop";

    // Default impl namespace for a CIM connection
    public static final String DFLT_CIM_CONNECTION_IMPL_NS = "root/emc";

    // Default impl namespace for a CIM connection
    public static final String FILE_CIM_CONNECTION_IMPL_NS = "root/emc/celerra";

    // Default impl namespace for a CIM connection for IBM
    public static final String DFLT_IBM_CIM_CONNECTION_IMPL_NS = "root/ibm";

    // Secure connection protocol.
    public static final String SECURE_PROTOCOL = "https";

    // Default queue size for the listener.
    public static final int DEFAULT_QUEUE_SIZE = 1000;

    // The CIM client protocol.
    public static final String CIM_CLIENT_PROTOCOL = "CIM-XML";

    // The CIM client timeout.
    public static final String CIM_CLIENT_TIMEOUT = "7200000";

    // The object path used to verify a CIM connection.
    public static final String CIM_INDICATION_OBJ_PATH = "CIM_Indication";

    // The listener retry interval.
    public static final long LISTENER_RETRY_INTERVAL = 30000;

    // Default query language for managed filters.
    public static final String DEFAULT_QUERY_LANGUAGE = "WQL";

    // Various String constants used while processing the CIM indications.
    public static final String ALERT_INDICATION_KEY = "AlertingManagedElement";
    public static final String INST_INDICATION_KEY = "SourceInstanceModelPath";
    public static final String CLASS_NAME_KEY = "ClassName";
    public static final String INDICATION_SOURCE_KEY = "IndicationSource";
    public static final String INDICATION_CLASS_NAME_KEY = "IndicationClassName";
    public static final String INDICATION_CLASS_TAG_KEY = "IndicationClassTag";
    public static final String COMPOSITE_ID_KEY = "CompositeID";
    public static final String COUNT_KEY = "Count";
    public static final String ALERT_MANAGED_ELEM_CLASS_KEY = "AlertingManagedElementClass";
    public static final String SRC_INST_MODEL_PATH_CLASS_KEY = "SourceInstanceModelPathClass";
    public static final String NAME_KEY = "Name";
    public static final String PREFIX_TAG_KEY = "PrefixTag";
    public static final String SUFFIX_TAG_KEY = "SuffixTag";
    public static final String ALERT_TYPE_KEY = "AlertType";
    public static final String ALERT_TYPE_TAG_KEY = "AlertTypeTag";
    public static final String PROBABLE_CAUSE_DESCR_KEY = "ProbableCauseDescription";
    public static final String PROBABLE_CAUSE_TAG_KEY = "ProbableCauseTag";
    public static final String STATISTICAL_DATA_UPDATE_SUCCESS = "StatisticalDataUpdateSucceeded";
    public static final String DISK_DRIVE_KEY_SUFFIX = "_DiskDrive";
    public static final String STORAGE_VOLUME_KEY_SUFFIX = "_StorageVolume";
    public static final String CONCRETE_STORAGE_POOL_KEY = "EMC_ConcreteStoragePool";
    public static final String ASSOCIATED_STORAGE_POOL_KEY = "AssociatedStoragePool";
    public static final String IS_COMPOSITE_KEY = "EMCIsComposite";
    public static final String OTHER_ALERT_TYPE_KEY = "OtherAlertType";
    public static final String OTHER_ALERT_TYPE_COMP_KEY = "OtherAlertTypeComponent";
    public static final String OTHER_ALERT_TYPE_COMP_TAG_KEY = "OtherAlertTypeComponentTag";
    public static final String OTHER_ALERT_TYPE_FACILITY_KEY = "OtherAlertTypeFacility";
    public static final String OTHER_ALERT_TYPE_FACILITY_TAG_KEY = "OtherAlertTypeFacilityTag";
    public static final String OTHER_ALERT_TYPE_EVENT_ID_KEY = "OtherAlertTypeEventID";
    public static final String OTHER_ALERT_TYPE_EVENT_ID_TAG_KEY = "OtherAlertTypeEventIdTag";
    public static final String OTHER_SEVERITY_KEY = "OtherSeverity";
    public static final String UNKNOWN_KEY = "Unknown";
    public static final String PROBABLE_CAUSE_DESCR_MD_KEY = "ProbableCauseDescriptionMD";

    // Indication type key.
    public static final String CIM_INDICATION_TYPE_KEY = "CimIndicationType";

    // Types for CIM indications.
    public static final String CIM_ALERT_INDICATION_TYPE = "ALERT_INDICATION";
    public static final String CIM_INST_INDICATION_TYPE = "INST_INDICATION";
    public static final String CIM_INDICATION_TYPE = "INDICATION";

    // Constants used to by the subscription manager to manage subscriptions for
    // a CIM connection.
    public static final char PATH_NAME_DELIMITER = ':';
    public static final String CIM_FILTER_NAME = "CIM_IndicationFilter";
    public static final String CIM_HANDLER_NAME = "CIM_ListenerDestinationCIMXML";
    public static final String CIM_SUBSCRIPTION_NAME = "CIM_IndicationSubscription";
    public static final String SUBSCRIPTION_PROP_HANDLER = "Handler";
    public static final String SUBSCRIPTION_PROP_FILTER = "Filter";
    public static final String FILTER_PROP_SRC_NAMESPACE = "SourceNamespace";
    public static final String FILTER_PROP_SRC_NAMESPACES = "SourceNamespaces";
    public static final String FILTER_PROP_QUERY_LANGUAGE = "QueryLanguage";
    public static final String FILTER_PROP_QUERY = "Query";
    public static final String HANLDER_PROP_DESTINATION = "Destination";

    // Hash algorithm used by message digest in Celerra connection.
    public static final String MD5_HASH_ALGORITHM = "MD5";
}
