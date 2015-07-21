/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;
import java.util.Set;

/**
 * Parameters necessary to enable image for one or more volumes
 * 
 */
@SuppressWarnings("serial")
public class MultiCopyDisableImageRequestParams implements Serializable {
		private String emName;
		private Set <String> volumeWWNSet;
		
		public Set <String> getVolumeWWNSet() {
			return volumeWWNSet;
		}
		public void setVolumeWWNSet(Set <String> volumeWWNSet) {
			this.volumeWWNSet = volumeWWNSet;
		}
		public String getEmName() {
			return emName;
		}
		public void setEmName(String emName) {
			this.emName = emName;
		}
}
