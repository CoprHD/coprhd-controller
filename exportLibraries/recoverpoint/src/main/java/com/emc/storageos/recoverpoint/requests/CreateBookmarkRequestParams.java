/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;
import java.util.Set;

/**
 * Parameters necessary to create a bookmark against one or more volumes
 * 
 */
@SuppressWarnings("serial")
public class CreateBookmarkRequestParams implements Serializable {
		private Set <String> volumeWWNSet;
		private String bookmark;
		
		public Set <String> getVolumeWWNSet() {
			return volumeWWNSet;
		}
		public void setVolumeWWNSet(Set <String> volumeWWNSet) {
			this.volumeWWNSet = volumeWWNSet;
		}
		public String getBookmark() {
			return bookmark;
		}
		public void setBookmark(String bookmark) {
			this.bookmark = bookmark;
		}
}
