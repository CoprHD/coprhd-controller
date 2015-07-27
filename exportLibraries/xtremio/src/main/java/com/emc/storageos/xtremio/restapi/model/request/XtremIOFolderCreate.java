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
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.emc.storageos.xtremio.restapi.model.XtremIOResponseContent;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_folder_create")
public class XtremIOFolderCreate {
	
    @SerializedName("folders")
	@JsonProperty(value = "folders")
	private XtremIOResponseContent[] volumeFolders;

    public XtremIOResponseContent[] getVolumeFolders() {
        return volumeFolders!=null ? volumeFolders.clone() : volumeFolders;
    }

    public void setVolumeFolders(XtremIOResponseContent[] volumeFolders) {
    	if(volumeFolders!=null){
    		this.volumeFolders = volumeFolders.clone();
    	}
    }

}
