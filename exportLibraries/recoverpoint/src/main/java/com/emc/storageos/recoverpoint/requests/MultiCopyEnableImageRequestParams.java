/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
package com.emc.storageos.recoverpoint.requests;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;

/**
 * Parameters necessary to enable image for one or more volumes
 * 
 */
@SuppressWarnings("serial")
public class MultiCopyEnableImageRequestParams implements Serializable {
		private Set <String> volumeWWNSet;
		private Date apitTime;
		private String bookmark;
		
		public MultiCopyEnableImageRequestParams() {
			apitTime = null;
			bookmark = null;
		}
		
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
		public Date getAPITTime() {
			return apitTime;
		}
		public void setAPITTime(Date aPITTime) {
			apitTime = aPITTime;
		}
}
