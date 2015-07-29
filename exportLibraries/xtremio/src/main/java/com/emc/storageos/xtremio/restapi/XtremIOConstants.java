/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;
import java.net.URLEncoder;

public class XtremIOConstants {
	
	public static final String CONTENT = "content";
	public static final String AUTH_TOKEN = "X-XIO-AUTH-TOKEN";
	public static final String AUTH_TOKEN_HEADER = "X-XIO-AUTH-TOKEN-HEADER";
	public static final String ERROR_CODE = "error_code";
	public static final String ROOT_FOLDER = "/";
	public static final String V2_VOLUME_ROOT_FOLDER = "/Volumes";
	public static final String UNDERSCORE = "_";

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
    
    public static final URI XTREMIO_BASE_CLUSTERS_URI = URI.create(XTREMIO_BASE_CLUSTERS_STR);
    public static final URI XTREMIO_VOLUMES_URI = URI.create(XTREMIO_VOLUMES_STR);
    public static final URI XTREMIO_SNAPS_URI = URI.create(XTREMIO_SNAPS_STR);
    public static final URI XTREMIO_VOLUME_FOLDERS_URI = URI.create(XTREMIO_VOLUME_FOLDERS_STR);
    public static final URI XTREMIO_INITIATOR_GROUPS_FOLDER_URI = URI.create(XTREMIO_INITIATOR_GROUPS_FOLDER_STR);
    public static final URI XTREMIO_TARGETS_URI = URI.create(XTREMIO_TARGETS_STR);
    public static final URI XTREMIO_INITIATORS_URI = URI.create(XTREMIO_INITIATORS_STR);
    public static final URI XTREMIO_INITIATOR_GROUPS_URI = URI.create(XTREMIO_INITIATOR_GROUPS_STR);
    public static final URI XTREMIO_LUNMAPS_URI = URI.create(XTREMIO_LUNMAPS_STR);
    
    //Version 2 API strings and uris
	public static final String XTREMIO_V2_BASE_STR = "/api/json/v2/types";
	public static final String XTREMIO_V2_VOLUMES_STR = XTREMIO_V2_BASE_STR.concat("/volumes");
    public static final String XTREMIO_V2_SNAPS_STR = XTREMIO_V2_BASE_STR.concat("/snapshots");
    public static final String XTREMIO_CONSISTENCY_GROUPS_STR = XTREMIO_V2_BASE_STR.concat("/consistency-groups");
    public static final String XTREMIO_CONSISTENCY_GROUP_VOLUMES_STR = XTREMIO_V2_BASE_STR.concat("/consistency-group-volumes");
    public static final String XTREMIO_TAGS_STR = XTREMIO_V2_BASE_STR.concat("/tags");
    public static final String XTREMIO_XMS_STR = XTREMIO_BASE_STR.concat("/xms");
    
	public static final URI XTREMIO_V2_VOLUMES_URI = URI.create(XTREMIO_V2_VOLUMES_STR);
    public static final URI XTREMIO_V2_SNAPS_URI = URI.create(XTREMIO_V2_SNAPS_STR);
    public static final URI XTREMIO_CONSISTENCY_GROUPS_URI = URI.create(XTREMIO_CONSISTENCY_GROUPS_STR);
    public static final URI XTREMIO_CONSISTENCY_GROUP_VOLUMES_URI = URI.create(XTREMIO_CONSISTENCY_GROUP_VOLUMES_STR);
    public static final URI XTREMIO_TAGS_URI = URI.create(XTREMIO_TAGS_STR);
    public static final URI XTREMIO_XMS_URI = URI.create(XTREMIO_XMS_STR);
    
    public static final String CAPTION_NOT_UNIQUE = "caption_not_unique";
    public static final String VOLUME_MAPPED = "vol_already_mapped";
    public static final String XTREMIO_INPUT_NAME_STR = "?name=%s";
    public static final String XTREMIO_INPUT_NAME_CLUSTER_STR = "?name=%s&cluster-name=%s";
    public static final String XTREMIO_XMS_FILTER_STR = "?prop=restapi-protocol-version";
    public static final String XTREMIO_REGULAR_TYPE="regular";
    public static final String XTREMIO_READ_ONLY_TYPE="readonly";
    public static final int XTREMIO_MAX_VOL_LENGTH= 55;
    
    public static enum XTREMIO_TAG_ENTITY {
    	ConsistencyGroup,
    	Volume,
    	Snapshot,
    	SnapshotSet,
    	InitiatorGroup,
    	Initiator,
    	Scheduler
    }
	
	public static String getXIOBaseURI(String ipAddress, int port) {
		return String.format("https://%1$s:%2$d", ipAddress,port);
	}
	
	public static String getXIOSnapURI(String snapName, String clusterName) throws Exception {
		if(clusterName != null && !clusterName.isEmpty()) {
			return XTREMIO_SNAPS_STR.concat(getInputNameForClusterString(snapName, clusterName));
		} else {
			return XTREMIO_SNAPS_STR.concat(getInputNameString(snapName));
		}
	}
	
	public static String getXIOVolumeURI(String volumeName, String clusterName) throws Exception {
		if(clusterName != null && !clusterName.isEmpty()) {
			return XTREMIO_VOLUMES_STR.concat(getInputNameForClusterString(volumeName, clusterName));
		} else {
			return XTREMIO_VOLUMES_STR.concat(getInputNameString(volumeName));
		}
    }
	
	public static String getXIOVolumeFolderURI(String volumeFolderName) throws Exception {
		return XTREMIO_VOLUME_FOLDERS_STR.concat(getInputNameString(volumeFolderName));
	}
	
	public static String getXIOIGFolderURI(String igFolderName) throws Exception {
		return XTREMIO_INITIATOR_GROUPS_FOLDER_STR.concat(getInputNameString(igFolderName));
    }
	
	public static String getXIOVolumeInitiatorUri(String initiatorName, String clusterName) throws Exception {
		if(clusterName != null && !clusterName.isEmpty()) {
			return XTREMIO_INITIATORS_STR.concat(getInputNameForClusterString(initiatorName, clusterName));
		} else {
			return XTREMIO_INITIATORS_STR.concat(getInputNameString(initiatorName));
		}
    }
	
	public static String getXIOInitiatorGroupUri(String initiatorGroupName, String clusterName) throws Exception {
		if(clusterName != null && !clusterName.isEmpty()) {
			return XTREMIO_INITIATOR_GROUPS_STR.concat(getInputNameForClusterString(initiatorGroupName, clusterName));
		} else {
			return XTREMIO_INITIATOR_GROUPS_STR.concat(getInputNameString(initiatorGroupName));
		}
    }
	
	public static String getXIOLunMapUri(String lunMap, String clusterName) throws Exception {
		if(clusterName != null && !clusterName.isEmpty()) {
			return XTREMIO_LUNMAPS_STR.concat(getInputNameForClusterString(lunMap, clusterName));
		} else {
			return XTREMIO_LUNMAPS_STR.concat(getInputNameString(lunMap));
		}
    }
	
	public static String getXIOConsistencyGroupsUri(String cgName, String clusterName) throws Exception {
		if(clusterName != null && !clusterName.isEmpty()) {
			return XTREMIO_CONSISTENCY_GROUPS_STR.concat(getInputNameForClusterString(cgName, clusterName));
		} else {
			return XTREMIO_CONSISTENCY_GROUPS_STR.concat(getInputNameString(cgName));
		}
	}
	
	public static String getXIOConsistencyGroupVolumesUri(String cgVolumeName, String clusterName) throws Exception {
		if(clusterName != null && !clusterName.isEmpty()) {
			return XTREMIO_CONSISTENCY_GROUPS_STR.concat(getInputNameForClusterString(cgVolumeName, clusterName));
		} else {
			return XTREMIO_CONSISTENCY_GROUPS_STR.concat(getInputNameString(cgVolumeName));
		}
	}
	
	public static String getInputNameString(String name) throws Exception {
	    return String.format(XTREMIO_INPUT_NAME_STR, URLEncoder.encode(name, "UTF-8"));
	}
	
	public static String getInputNameForClusterString(String name, String clusterName) throws Exception {
		return String.format(XTREMIO_INPUT_NAME_CLUSTER_STR, URLEncoder.encode(name, "UTF-8"), 
				URLEncoder.encode(clusterName, "UTF-8"));
	}
	
	
}
