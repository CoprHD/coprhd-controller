/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;
import java.net.URLEncoder;

public class XtremIOConstants {

    public static final String CONTENT = "content";
    public static final String AUTH_TOKEN = "X-XIO-AUTH-TOKEN";
    public static final String AUTH_TOKEN_HEADER = "X-XIO-AUTH-TOKEN-HEADER";
    public static final String ERROR_CODE = "error_code";
    public static final String V1_ROOT_FOLDER = "/";
    public static final String V2_VOLUME_ROOT_FOLDER = "/Volume/";
    public static final String V2_SNAPSHOT_ROOT_FOLDER = "/SnapshotSet/";
    public static final String V2_CONSISTENCY_GROUP_ROOT_FOLDER = "/ConsistencyGroup/";
    public static final String V2_INITIATOR_GROUP_ROOT_FOLDER = "/InitiatorGroup/";
    public static final String UNDERSCORE = "_";
    public static final String EMPTY_STRING = "";
    public static final String SLASH = "/";
    public static final String DASH = "-";
    public static final int XTREMIO_MAX_Filters =149; 
    public static final String XTREMIO_BULK_API_MINVERSION = "4.0.27";
    public static final String XTREMIO_BULK_XMS_MINVERSION = "6.0.0";

    public static final String VOLUME_KEY = "volume";
    public static final String SNAPSHOT_KEY = "snapshot";
    public static final String VOLUMES_SUBFOLDER = "/volumes";
    public static final String SNAPSHOTS_SUBFOLDER = "/snapshots";

    public static final String XTREMIO_BASE_STR = "/api/json/types";
    public static final String XTREMIO_VOLUMES_STR = XTREMIO_BASE_STR.concat("/volumes");
    public static final String XTREMIO_SNAPS_STR = XTREMIO_BASE_STR.concat("/snapshots");
    public static final String XTREMIO_VOLUME_FOLDERS_STR = XTREMIO_BASE_STR.concat("/volume-folders");
    public static final String XTREMIO_INITIATOR_GROUPS_FOLDER_STR = XTREMIO_BASE_STR.concat("/ig-folders");
    public static final String XTREMIO_BASE_CLUSTERS_STR = XTREMIO_BASE_STR.concat("/clusters");
    public static final String XTREMIO_TARGETS_STR = XTREMIO_BASE_STR.concat("/targets");
    public static final String XTREMIO_INITIATORS_STR = XTREMIO_BASE_STR.concat("/initiators");
    public static final String XTREMIO_INITIATOR_GROUPS_STR = XTREMIO_BASE_STR.concat("/initiator-groups");
    public static final String XTREMIO_LUNMAPS_STR = XTREMIO_BASE_STR.concat("/lun-maps");
    public static final String XTREMIO_XMS_STR = XTREMIO_BASE_STR.concat("/xms");

    public static final URI XTREMIO_BASE_CLUSTERS_URI = URI.create(XTREMIO_BASE_CLUSTERS_STR);
    public static final URI XTREMIO_VOLUMES_URI = URI.create(XTREMIO_VOLUMES_STR);
    public static final URI XTREMIO_SNAPS_URI = URI.create(XTREMIO_SNAPS_STR);
    public static final URI XTREMIO_VOLUME_FOLDERS_URI = URI.create(XTREMIO_VOLUME_FOLDERS_STR);
    public static final URI XTREMIO_INITIATOR_GROUPS_FOLDER_URI = URI.create(XTREMIO_INITIATOR_GROUPS_FOLDER_STR);
    public static final URI XTREMIO_TARGETS_URI = URI.create(XTREMIO_TARGETS_STR);
    public static final URI XTREMIO_INITIATORS_URI = URI.create(XTREMIO_INITIATORS_STR);
    public static final URI XTREMIO_INITIATOR_GROUPS_URI = URI.create(XTREMIO_INITIATOR_GROUPS_STR);
    public static final URI XTREMIO_LUNMAPS_URI = URI.create(XTREMIO_LUNMAPS_STR);
    public static final URI XTREMIO_XMS_URI = URI.create(XTREMIO_XMS_STR);

    // Version 2 API strings and uris
    public static final String XTREMIO_V2_BASE_STR = "/api/json/v2/types";
    public static final String XTREMIO_V2_VOLUMES_STR = XTREMIO_V2_BASE_STR.concat("/volumes");
    public static final String XTREMIO_V2_SNAPS_STR = XTREMIO_V2_BASE_STR.concat("/snapshots");
    public static final String XTREMIO_V2_BASE_CLUSTERS_STR = XTREMIO_V2_BASE_STR.concat("/clusters");
    public static final String XTREMIO_V2_TARGETS_STR = XTREMIO_V2_BASE_STR.concat("/targets");
    public static final String XTREMIO_V2_INITIATORS_STR = XTREMIO_V2_BASE_STR.concat("/initiators");
    public static final String XTREMIO_V2_INITIATOR_GROUPS_STR = XTREMIO_V2_BASE_STR.concat("/initiator-groups");
    public static final String XTREMIO_V2_LUNMAPS_STR = XTREMIO_V2_BASE_STR.concat("/lun-maps");
    public static final String XTREMIO_V2_CONSISTENCY_GROUPS_STR = XTREMIO_V2_BASE_STR.concat("/consistency-groups");
    public static final String XTREMIO_V2_CONSISTENCY_GROUP_VOLUMES_STR = XTREMIO_V2_BASE_STR.concat("/consistency-group-volumes");
    public static final String XTREMIO_V2_TAGS_STR = XTREMIO_V2_BASE_STR.concat("/tags");
    public static final String XTREMIO_V2_XMS_STR = XTREMIO_V2_BASE_STR.concat("/xms");
    public static final String XTREMIO_V2_SNAPSHOT_SET_STR = XTREMIO_V2_BASE_STR.concat("/snapshot-sets");
    public static final String XTREMIO_V2_PERFORMANCE_STR = XTREMIO_V2_BASE_STR.concat("/performance");

    public static final URI XTREMIO_V2_BASE_CLUSTERS_URI = URI.create(XTREMIO_V2_BASE_CLUSTERS_STR);
    public static final URI XTREMIO_V2_VOLUMES_URI = URI.create(XTREMIO_V2_VOLUMES_STR);
    public static final URI XTREMIO_V2_SNAPS_URI = URI.create(XTREMIO_V2_SNAPS_STR);
    public static final URI XTREMIO_V2_TARGETS_URI = URI.create(XTREMIO_V2_TARGETS_STR);
    public static final URI XTREMIO_V2_INITIATORS_URI = URI.create(XTREMIO_V2_INITIATORS_STR);
    public static final URI XTREMIO_V2_INITIATOR_GROUPS_URI = URI.create(XTREMIO_V2_INITIATOR_GROUPS_STR);
    public static final URI XTREMIO_V2_LUNMAPS_URI = URI.create(XTREMIO_V2_LUNMAPS_STR);
    public static final URI XTREMIO_V2_CONSISTENCY_GROUPS_URI = URI.create(XTREMIO_V2_CONSISTENCY_GROUPS_STR);
    public static final URI XTREMIO_V2_CONSISTENCY_GROUP_VOLUMES_URI = URI.create(XTREMIO_V2_CONSISTENCY_GROUP_VOLUMES_STR);
    public static final URI XTREMIO_V2_TAGS_URI = URI.create(XTREMIO_V2_TAGS_STR);
    public static final URI XTREMIO_V2_XMS_URI = URI.create(XTREMIO_V2_XMS_STR);
    public static final URI XTREMIO_V2_SNAPSHOT_SET_URI = URI.create(XTREMIO_V2_SNAPSHOT_SET_STR);
    public static final URI XTREMIO_V2_PERFORMANCE_URI = URI.create(XTREMIO_V2_PERFORMANCE_STR);

    public static final String CAPTION_NOT_UNIQUE = "caption_not_unique";
    public static final String VOLUME_MAPPED = "vol_already_mapped";
    public static final String OBJECT_NOT_FOUND = "obj_not_found";
    public static final String XTREMIO_INPUT_NAME_STR = "?name=%s";
    public static final String XTREMIO_INPUT_ADDITIONAL_PARAM_STR = "&%s=%s";
    public static final String XTREMIO_INPUT_CLUSTER_STR = "?cluster-name=%s";
    public static final String XTREMIO_INPUT_NAME_CLUSTER_STR = "?name=%s&cluster-name=%s";
    public static final String XTREMIO_XMS_FILTER_STR = "?prop=restapi-protocol-version";
    public static final String XTREMIO_CLUSTER_FILTER_STR = "?filter=sys-psnt-serial-number:eq:%s";
    public static final String XTREMIO_LUNMAP_IG_FILTER_STR = "?filter=ig-name:eq:%s&cluster-name=%s";
    public static final String XTREMIO_REGULAR_TYPE = "regular";
    public static final String XTREMIO_READ_ONLY_TYPE = "readonly";
    public static final int XTREMIO_MAX_VOL_LENGTH = 55;
    public static final String XTREMIO_LUNMAP_IG_FILTER_FULL_STR = "?full=1&cluster-name=%s&prop=vol-index&prop=ig_name&filter=";
    public static final String XTREMIO_VOLUME_IG_FILTER_FULL_STR = "?full=1&cluster-name=%s&prop=vol-id&prop=lun-mapping-list&prop=naa-name&filter=";

    // Performance query
    public static final String ENTITY = "entity";
    public static final String TIME_FRAME = "time-frame";
    public static final String LAST_HOUR = "last_hour";
    public static final String LAST_DAY = "last_day";
    public static final String GRANULARITY = "granularity";
    public static final String TEN_MINUTES = "ten_minutes";
    public static final String ONE_HOUR = "one_hour";
    public static final String AVG_CPU_USAGE = "avg__cpu_usage";
    public static final String NAME = "name";
    
    public static final String SNAP_SIZE_MISMATCH_ERROR_KEY = "invalid_vol_size";

    public static enum XTREMIO_ENTITY_TYPE {
        ConsistencyGroup,
        Volume,
        SnapshotSet,
        InitiatorGroup,
        Initiator,
        Scheduler,
        XEnv
    }

    public static String getXIOBaseURI(String ipAddress, int port) {
        return String.format("https://%1$s:%2$d", ipAddress, port);
    }

    public static String getInputNameString(String name) throws Exception {
        return String.format(XTREMIO_INPUT_NAME_STR, URLEncoder.encode(name, "UTF-8"));
    }

    public static String getInputAdditionalParamString(String paramName, String paramValue) throws Exception {
        return String.format(XTREMIO_INPUT_ADDITIONAL_PARAM_STR, paramName, URLEncoder.encode(paramValue, "UTF-8"));
    }

    public static String getInputClusterString(String clusterName) throws Exception {
        if (clusterName != null && !clusterName.isEmpty()) {
            return String.format(XTREMIO_INPUT_CLUSTER_STR, URLEncoder.encode(clusterName, "UTF-8"));
        }

        return EMPTY_STRING;
    }

    public static String getInputNameForClusterString(String name, String clusterName) throws Exception {
        if (clusterName != null && !clusterName.isEmpty()) {
            return String.format(XTREMIO_INPUT_NAME_CLUSTER_STR, URLEncoder.encode(name, "UTF-8"),
                    URLEncoder.encode(clusterName, "UTF-8"));
        } else {
            return getInputNameString(name);
        }

    }

    public static String getV2RootFolderForEntityType(String entityType) {
        String rootFolder = "";
        if (XTREMIO_ENTITY_TYPE.Volume.name().equals(entityType)) {
            return V2_VOLUME_ROOT_FOLDER;
        } else if (XTREMIO_ENTITY_TYPE.ConsistencyGroup.name().equals(entityType)) {
            return V2_CONSISTENCY_GROUP_ROOT_FOLDER;
        } else if (XTREMIO_ENTITY_TYPE.SnapshotSet.name().equals(entityType)) {
            return V2_SNAPSHOT_ROOT_FOLDER;
        } else if (XTREMIO_ENTITY_TYPE.InitiatorGroup.name().equals(entityType)) {
            return V2_INITIATOR_GROUP_ROOT_FOLDER;
        }

        return rootFolder;
    }

}
