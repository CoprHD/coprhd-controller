/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.net.URI;

/**
 * Defines constants relative to using the VPlex HTTP Element Manager API.
 */
public class VPlexApiConstants {

    // Constants define the headers required when making HTTP requests to the
    // VPlex Management Station using the Element Manager API.
    public static final String USER_NAME_HEADER = "Username";
    public static final String PASS_WORD_HEADER = "Password";
    public static final String LOCATION_HEADER = "Location";
    public static final String CONNECTION_HEADER = "Connection";
    public static final String CONNECTION_HEADER_VALUE_CLOSE = "close";
    public static final String SESSION_COOKIE = "JSESSIONID";

    // Constants related to JSON response formatting from the VPLEX API
    //
    // JSON format 0 is a very expansive JSON format that includes lots of
    // unnecessary spacing and maps of maps of maps. Quite a
    // few existing ViPR calls use this format still, but the
    // best practice would be to move towards JSON format 1.
    // JSON format 1 is a compacted JSON format that can reduce the size of
    // the response payload by 50% or more and also removes the
    // need to set attributes on VPlexResourceInfo classes, since
    // the format is more compatible with GSON.
    // Cache Control settings affect a response cache on the VPLEX. Settings
    // greater than zero will allow the VPLEX to avoid fetching
    // data internally again if nothing has changed within the
    // max-age value time frame. Setting cache-control to zero
    // will force the VPLEX to fetch the most recent data.
    public static final String ACCEPT_HEADER = "Accept";
    public static final String ACCEPT_JSON_FORMAT_0 = ";format=0";
    public static final String ACCEPT_JSON_FORMAT_1 = ";format=1";
    public static final String CACHE_CONTROL_HEADER = "Cache-control";
    public static final String CACHE_CONTROL_MAXAGE_KEY = "max-age=";
    public static final String CACHE_CONTROL_MAXAGE_DEFAULT_VALUE = "600";
    public static final String CACHE_CONTROL_MAXAGE_ZERO = "0";

    // Constants defining HTTP resource paths.
    public static final String WILDCARD = "*";
    public static final String SLASH = "/";
    public static final String EQUALS = "=";
    public static final String QUESTION_MARK = "?";
    public static final String VPLEX_PATH = "/vplex";
    public static final URI URI_MANAGEMENT_SERVER = URI.create("/vplex/management-server/");
    public static final URI URI_ENGINES = URI.create("/vplex/engines/");
    public static final URI URI_DIRECTORS = URI.create("/directors/");
    public static final URI URI_DIRECTOR_PORTS = URI.create("/hardware/ports/");
    public static final URI URI_CLUSTERS = URI.create("/vplex/clusters/");
    public static final URI URI_CLUSTERS_RELATIVE = URI.create("/clusters/");
    public static final URI URI_STORAGE_SYSTEMS = URI.create("/storage-elements/storage-arrays/");
    public static final URI URI_STORAGE_VOLUMES = URI.create("/storage-elements/storage-volumes/");
    public static final URI URI_STORAGE_VOLUMES_DETAILS = URI.create("/storage-elements/storage-volumes/*");
    public static final URI URI_SYSTEM_VOLUMES = URI.create("/system-volumes/");
    public static final URI URI_EXTENTS = URI.create("/storage-elements/extents/");
    public static final URI URI_DEVICES = URI.create("/devices/");
    public static final URI URI_VIRTUAL_VOLUMES = URI.create("/virtual-volumes/");
    public static final URI URI_INITIATORS = URI.create("/exports/initiator-ports/");
    public static final URI URI_REGISTER_INITIATOR = URI.create("/vplex/export+initiator-port+register");
    public static final URI URI_UNREGISTER_INITIATORS = URI.create("/vplex/export+initiator-port+unregister");
    public static final URI URI_INITIATOR_DISCOVERY = URI.create("/vplex/export+initiator-port+discovery");
    public static final URI URI_TARGETS = URI.create("/exports/ports/");
    public static final URI URI_DISTRIBUTED_DEVICES = URI.create("/vplex/distributed-storage/distributed-devices/");
    public static final URI URI_CLAIM_VOLUME = URI.create("/vplex/storage-volume+claim");
    public static final URI URI_UNCLAIM_VOLUME = URI.create("/vplex/storage-volume+unclaim");
    public static final URI URI_STORAGE_VOLUME_USED_BY = URI.create("/vplex/storage-volume+used-by");
    public static final URI URI_CREATE_EXTENT = URI.create("/vplex/extent+create");
    public static final URI URI_DESTROY_EXTENT = URI.create("/vplex/extent+destroy");
    public static final URI URI_CREATE_LOCAL_DEVICE = URI.create("/vplex/local-device+create");
    public static final URI URI_CREATE_VIRTUAL_VOLUME = URI.create("/vplex/virtual-volume+create");
    public static final URI URI_DESTROY_VIRTUAL_VOLUME = URI.create("/vplex/virtual-volume+destroy");
    public static final URI URI_CREATE_DIST_DEVICE = URI.create("/vplex/ds+dd+create");
    public static final URI URI_REDISCOVER_ARRAY = URI.create("/vplex/array+re-discover");
    public static final URI URI_DISMANTLE = URI.create("/vplex/advadm+dismantle");
    public static final URI URI_STORAGE_VIEWS = URI.create("/exports/storage-views/");
    public static final URI URI_FIND_STORAGE_VIEW = URI.create("/vplex/export+storage-view+find");
    public static final URI URI_CREATE_STORAGE_VIEW = URI.create("/vplex/export+storage-view+create");
    public static final URI URI_DESTROY_STORAGE_VIEW = URI.create("/vplex/export+storage-view+destroy");
    public static final URI URI_STORAGE_VIEW_ADD_INITIATORS = URI.create("/vplex/export+storage-view+addinitiatorport");
    public static final URI URI_STORAGE_VIEW_REMOVE_INITIATORS = URI.create("/vplex/export+storage-view+removeinitiatorport");
    public static final URI URI_STORAGE_VIEW_ADD_TARGETS = URI.create("/vplex/export+storage-view+addport");
    public static final URI URI_STORAGE_VIEW_REMOVE_TARGETS = URI.create("/vplex/export+storage-view+removeport");
    public static final URI URI_STORAGE_VIEW_ADD_VOLUMES = URI.create("/vplex/export+storage-view+addvirtualvolume");
    public static final URI URI_STORAGE_VIEW_REMOVE_VOLUMES = URI.create("/vplex/export+storage-view+removevirtualvolume");
    public static final URI URI_VERSION_INFO = URI.create("/vplex/version");
    public static final URI URI_START_MIGRATION = URI.create("/vplex/dm+migration+start");
    public static final URI URI_EXTENT_MIGRATIONS = URI.create("/vplex/data-migrations/extent-migrations/");
    public static final URI URI_DEVICE_MIGRATIONS = URI.create("/vplex/data-migrations/device-migrations/");
    public static final URI URI_COMMIT_MIGRATIONS = URI.create("/vplex/dm+migration+commit");
    public static final URI URI_PAUSE_MIGRATIONS = URI.create("/vplex/dm+migration+pause");
    public static final URI URI_RESUME_MIGRATIONS = URI.create("/vplex/dm+migration+resume");
    public static final URI URI_CLEAN_MIGRATIONS = URI.create("/vplex/dm+migration+clean");
    public static final URI URI_REMOVE_MIGRATIONS = URI.create("/vplex/dm+migration+remove");
    public static final URI URI_CANCEL_MIGRATIONS = URI.create("/vplex/dm+migration+cancel");
    public static final URI URI_DISTRIBUTED_DEVICE_COMP = URI.create("/distributed-device-components/");
    public static final URI URI_COMPONENTS = URI.create("/components/");
    public static final URI URI_DEVICE_ATTACH_MIRROR = URI.create("/vplex/device+attach-mirror");
    public static final URI URI_REBUILD_SET_TRANSFER_SIZE = URI.create("/vplex/rebuild+set-transfer-size");
    public static final URI URI_EXPAND_VIRTUAL_VOLUME = URI.create("/vplex/virtual-volume+expand");
    public static final URI URI_CREATE_CG = URI.create("/vplex/consistency-group+create");
    public static final URI URI_ADD_VOLUMES_TO_CG = URI.create("/vplex/consistency-group+add-virtual-volumes");
    public static final URI URI_REMOVE_VOLUMES_FROM_CG = URI.create("/vplex/consistency-group+remove-virtual-volumes");
    public static final URI URI_CGS = URI.create("/consistency-groups/");
    public static final URI URI_CGS_ADVANCED = URI.create("/advanced");
    public static final URI URI_DELETE_CG = URI.create("/vplex/consistency-group+destroy");
    public static final URI URI_LOGICAL_UNITS = URI.create("/logical-units/");
    public static final URI URI_FORGET_LOG_UNIT = URI.create("/vplex/logical-unit+forget");
    public static final URI URI_CG_DETACH_RULE_WINNER = URI.create("/vplex/consistency-group+set-detach-rule+winner");
    public static final URI URI_CG_DETACH_RULE_NO_AUTO_WINNER = URI.create("/vplex/consistency-group+set-detach-rule+no-automatic-winner");
    public static final URI URI_INVALIDATE_VOLUME_CACHE = URI.create("/vplex/virtual-volume+cache-invalidate");
    public static final URI URI_INVALIDATE_VOLUME_CACHE_STATUS = URI.create("/vplex/virtual-volume+cache-invalidate-status");
    public static final URI URI_DEVICE_DETACH_MIRROR = URI.create("/vplex/device+detach-mirror");
    public static final URI URI_DEVICE_COLLAPSE = URI.create("/vplex/device+collapse");
    public static final URI URI_REFRESH_CONTEXT = URI.create("/vplex/ls");
    public static final URI URI_DRILL_DOWN = URI.create("/vplex/drill-down");

    // Keys found in JSON responses to HTTP requests
    public static final String RESPONSE_JSON_KEY = "response";
    public static final String CONTEXT_JSON_KEY = "context";
    public static final String PARENT_JSON_KEY = "parent";
    public static final String CHILDREN_JSON_KEY = "children";
    public static final String ATTRIBUTES_JSON_KEY = "attributes";
    public static final String ATTRIBUTE_NAME_JSON_KEY = "name";
    public static final String ATTRIBUTE_VALUE_JSON_KEY = "value";
    public static final String SERIAL_NO_ATT_KEY = "serial-number";
    public static final String CUSTOM_DATA_JSON_KEY = "custom-data";
    public static final String REBUILD_STATUS_ATT_KEY = "rebuild-status";
    public static final String REBUILD_STATUS_DONE = "done";
    public static final String REBUILD_STATUS_ERROR = "error";
    public static final String ATTRIBUTE_CG_RP_ENABLED = "recoverpoint-enabled";
    public static final String ATTRIBUTE_CG_AUTO_RESUME = "auto-resume-at-loser";
    public static final String ATTRIBUTE_DEVICE_VISIBILITY = "visibility";
    public static final String EXCEPTION_MSG_JSON_KEY = "exception";

    // Constant defines the JSON key for arguments passed as POST data in
    // a VPlex API POST request to execute a VPlex command.
    public static final String POST_DATA_ARG_KEY = "args";

    // Constants define VPlex command arguments
    public static final String ARG_DASH_A = "-a";
    public static final String ARG_DASH_C = "-c";
    public static final String ARG_DASH_D = "-d";
    public static final String ARG_DASH_E = "-e";
    public static final String ARG_DASH_F = "-f";
    public static final String ARG_DASH_G = "-g";
    public static final String ARG_DASH_I = "-i";
    public static final String ARG_DASH_M = "-m";
    public static final String ARG_DASH_N = "-n";
    public static final String ARG_DASH_O = "-o";
    public static final String ARG_DASH_P = "-p";
    public static final String ARG_DASH_R = "-r";
    public static final String ARG_DASH_S = "-s";
    public static final String ARG_DASH_T = "-t";
    public static final String ARG_DASH_U = "-u";
    public static final String ARG_DASH_V = "-v";
    public static final String ARG_FORCE = "--force";
    public static final String ARG_HARD = "--hard";
    public static final String ARG_PAUSED = "--paused";
    public static final String ARG_DISCARD = "--discard";
    public static final String ARG_GEOMETRY_RAID0 = "raid-0";
    public static final String ARG_GEOMETRY_RAID1 = "raid-1";
    public static final String ARG_GEOMETRY_RAIDC = "raid-C";
    public static final String ARG_UNCLAIM = "--unclaim-storage-volumes";
    public static final String ARG_APPC = "--appc";
    public static final String ARG_THIN_REBUILD = "--thin-rebuild";
    public static final String ARG_TRANSFER_SIZE = "--transfer-size";
    public static final String ARG_DEVICES = "--devices";

    // Constants related to claimed storage volumes.
    public static final String VOLUME_NAME_PREFIX = "V";
    public static final int MAX_VOL_NAME_LENGTH = 27;
    public static final int MAX_DEVICE_NAME_LENGTH_FOR_ATTACH_MIRROR = 47;
    public static final String DOT_OPERATOR = ".";
    public static final String UNDERSCORE_OPERATOR = "_";
    public static final String PLUS_OPERATOR = "+";
    public static final String HYPHEN_OPERATOR = "-";

    // Virtual Volume Locality
    public static final String LOCAL_VIRTUAL_VOLUME = "local";
    public static final String DISTRIBUTED_VIRTUAL_VOLUME = "distributed";

    public static final String LOCAL_DEVICE = "local";

    // Virtual Volume VPD-ID
    public static final String VOLUME_WWN_PREFIX = "VPD83T3:";

    // The prefix and suffix VPlex applies to an extent when created
    // for a storage volume. Note that the suffix implies that only
    // one extent is created for the storage volumes.
    public static final String EXTENT_PREFIX = "extent_";
    public static final String EXTENT_SUFFIX = "_1";

    // The prefix we append to the storage volume name when creating
    // a local device.
    public static final String DEVICE_PREFIX = "device_";

    // The prefix we append when creating a distributed device.
    public static final String DIST_DEVICE_PREFIX = "dd";

    // Delimiter used when building a distributed device name
    public static final String DIST_DEVICE_NAME_DELIM = "_";

    // The suffix appended by VPlex to the device name when creating
    // a virtual volume for that device.
    public static final String VIRTUAL_VOLUME_SUFFIX = "_vol";

    // The prefix appended to the initiator port WWN to set the
    // initiator name when an initiator is registered.
    public static final String REGISTERED_INITIATOR_PREFIX = "REGISTERED_";
    public static final String UNREGISTERED_INITIATOR_PREFIX = "UNREGISTERED-";
    public static final String WWN_PREFIX = "0x";

    // The delimiter used to separate the port and node WWNs when
    // registering an initiator port.
    public static final String INITIATOR_REG_DELIM = "|";

    // Constants representing response status for VPlex requests
    public static final int SUCCESS_STATUS = 200;
    public static final int ASYNC_STATUS = 202;
    public static final int AUTHENTICATION_STATUS = 401;
    public static final int NOT_FOUND_STATUS = 404;
    public static final int TASK_PENDING_STATUS = 517;
    public static final int COULD_NOT_READ_STORAGE_VIEW_STATUS = 541;

    // Number of milliseconds to wait before checking the status of
    // a VPlex command that is running asynchronously.
    public static final int TASK_PENDING_WAIT_TIME = 10000;

    // Maximum number of retries while checking the status of an
    // asynchronous command. This value times the wait time
    // determines how long we'll wait for the asynchronous
    // command to complete.
    public static final int MAX_RETRIES = 60;

    // VPlex API null attribute value
    public static final String NULL_ATT_VAL = "null";

    // Type for a system volume that is a logging volume
    public static final String LOGGING_VOLUME_TYPE = "logging-volume";

    // Component type for a storage volume
    public static final String STORAGE_VOLUME_TYPE = "storage-volume";

    // When trying to find storage volumes these parameters control how
    // long we will try and find those volumes before giving up.
    public static final int FIND_STORAGE_VOLUME_RETRY_COUNT = 5;
    public static final long FIND_STORAGE_VOLUME_SLEEP_TIME_MS = 3000;

    // Controls how long we sleep before retrying StorageView create
    public static final int STORAGE_VIEW_CREATE_RETRY_TIME_MS = 60000;
    public static final int STORAGE_VIEW_CREATE_MAX_RETRIES = 1;

    // Controls how long we wait for a volume expansion to complete.
    public static final String EXPANSION_STATUS_RETRY_COUNT = "controller_vplex_volume_expansion_status_check_retry_count";
    public static final String EXPANSION_STATUS_SLEEP_TIME_MS = "controller_vplex_volume_expansion_status_check_retry_max_wait";

    // When waiting on a rebuild operation to complete these parameters
    // control how long we wait.
    public static final int REBUILD_WAIT_RETRY_COUNT = 240;
    public static final long REBUILD_WAIT_SLEEP_TIME_MS = 60000;

    // Retry parameters for when trying to reattach the HA mirror
    // for a distributed virtual volume.
    public static final int REATTACH_HA_MIRROR_RETRY_COUNT = 10;
    public static final long REATTACH_HA_MIRROR_SLEEP_TIME_MS = 60000;

    // Value to specify for virtual volumes in storage views, when
    // the VPlex is to assign the LUN ID.
    public static final int LUN_UNASSIGNED = -1;

    // Migration status
    public static final String MIGRATION_COMPLETE = "complete";
    public static final String MIGRATION_INPROGRESS = "in-progress";
    public static final String MIGRATION_PAUSED = "paused";
    public static final String MIGRATION_COMMITTED = "committed";
    public static final String MIGRATION_CANCELED = "cancelled";
    public static final String MIGRATION_ERROR = "error";
    public static final String MIGRATION_QUEUED = "queued";
    public static final String MIGRATION_PART_CANCELED = "partially-cancelled";

    // Constant defines a string found in the exception message returned
    // in the response to a cache-invalidate request that fails because
    // the operation timed out prior to the invalidation completing.
    public static final String CACHE_INVALIDATE_IN_PROGRESS_MSG = "Please execute 'virtual-volume cache-invalidate-status";

    // Dismantle error message
    public static final String DISMANTLE_ERROR_MSG = "will not be dismantled because";

    // Cluster ids
    public static final String CLUSTER_1_ID = "1";
    public static final String CLUSTER_2_ID = "2";

    // Rule set names
    public static final String CLUSTER_1_DETACHES = "cluster-1-detaches";
    public static final String CLUSTER_2_DETACHES = "cluster-2-detaches";

    // Default EMC recommended detach delay in seconds.
    public static final int DETACH_DELAY = 5;

    public static final String HDS_SYSTEM = "HDS";

    public static final int FIND_NEW_ARTIFACT_MAX_TRIES = 60;
    public static final int FIND_NEW_ARTIFACT_SLEEP_TIME_MS = 10000;

    // VPLEX API error response fragment used to check if
    // the failure were due to duplicate storage view
    public static final String DUPLICATE_STORAGE_VIEW_ERROR_FRAGMENT = "failed with Status 537: Duplicate view";

    // delimiter for cause in VPLEX API error responses
    public static final String CAUSE_DELIM = "cause:";

}
