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
	public static final String UNDERSCORE = "_";
	
	public static final String XTREMIO_BASE_STR = "/api/json/types";
    public static final String XTREMIO_VOLUMES_STR = XTREMIO_BASE_STR.concat("/volumes");
    public static final String XTREMIO_SNAPS_STR = XTREMIO_BASE_STR.concat("/snapshots");
    public static final String XTREMIO_BASE_CLUSTERS_STR = XTREMIO_BASE_STR.concat("/clusters");
    public static final String XTREMIO_VOLUME_FOLDERS_STR = XTREMIO_BASE_STR.concat("/volume-folders");
    public static final String XTREMIO_TARGETS_STR = XTREMIO_BASE_STR.concat("/targets");
    public static final String XTREMIO_INITIATORS_STR = XTREMIO_BASE_STR.concat("/initiators");
    public static final String XTREMIO_INITIATOR_GROUPS_STR = XTREMIO_BASE_STR.concat("/initiator-groups");
    public static final String XTREMIO_INITIATOR_GROUPS_FOLDER_STR = XTREMIO_BASE_STR.concat("/ig-folders");
    public static final String XTREMIO_LUNMAPS_STR = XTREMIO_BASE_STR.concat("/lun-maps");
    public static final String XTREMIO_INPUT_NAME_STR = "?name=%s";
    public static final int    XTREMIO_MAX_VOL_LENGTH= 55;
    
    
	public static final URI XTREMIO_BASE_CLUSTERS_URI = URI.create(XTREMIO_BASE_CLUSTERS_STR);
    public static final URI XTREMIO_VOLUME_FOLDERS_URI = URI.create(XTREMIO_VOLUME_FOLDERS_STR);
    public static final URI XTREMIO_VOLUMES_URI = URI.create(XTREMIO_VOLUMES_STR);
    public static final URI XTREMIO_SNAPS_URI = URI.create(XTREMIO_SNAPS_STR);
    public static final URI XTREMIO_TARGETS_URI = URI.create(XTREMIO_TARGETS_STR);
    public static final URI XTREMIO_INITIATORS_URI = URI.create(XTREMIO_INITIATORS_STR);
    public static final URI XTREMIO_INITIATOR_GROUPS_URI = URI.create(XTREMIO_INITIATOR_GROUPS_STR);
    public static final URI XTREMIO_INITIATOR_GROUPS_FOLDER_URI = URI.create(XTREMIO_INITIATOR_GROUPS_FOLDER_STR);
    public static final URI XTREMIO_LUNMAPS_URI = URI.create(XTREMIO_LUNMAPS_STR);
    public static final String CAPTION_NOT_UNIQUE = "caption_not_unique";
    public static final String VOLUME_MAPPED = "vol_already_mapped";
    
	
	public static String getXIOBaseURI(String ipAddress, int port) {
		return String.format("https://%1$s:%2$d", ipAddress,port);
	}
	
	public static String getXIOSnapURI(String snapName) throws Exception {
		return XTREMIO_SNAPS_STR.concat(getInputNameString(snapName));
	}
	
	public static String getXIOVolumeURI(String volumeName) throws Exception {
        return XTREMIO_VOLUMES_STR.concat(getInputNameString(volumeName));
    }
	
	public static String getXIOVolumeFolderURI(String volumeFolderName) throws Exception {
		return XTREMIO_VOLUME_FOLDERS_STR.concat(getInputNameString(volumeFolderName));
	}
	
	public static String getXIOIGFolderURI(String igFolderName) throws Exception {
        return XTREMIO_INITIATOR_GROUPS_FOLDER_STR.concat(getInputNameString(igFolderName));
    }
	
	public static String getXIOVolumeInitiatorUri(String initiatorName) throws Exception {
        return XTREMIO_INITIATORS_STR.concat(getInputNameString(initiatorName));
    }
	
	public static String getXIOInitiatorGroupUri(String initiatorGroupName) throws Exception {
        return XTREMIO_INITIATOR_GROUPS_STR.concat(getInputNameString(initiatorGroupName));
    }
	
	public static String getXIOLunMapUri(String lunMap) throws Exception {
        return XTREMIO_LUNMAPS_STR.concat(getInputNameString(lunMap));
    }
	
	public static String getInputNameString(String name) throws Exception {
	    return String.format(XTREMIO_INPUT_NAME_STR, URLEncoder.encode(name, "UTF-8"));
	}
	
	
}
