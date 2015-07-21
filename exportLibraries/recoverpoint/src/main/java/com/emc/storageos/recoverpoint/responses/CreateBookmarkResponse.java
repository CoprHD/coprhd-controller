/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.recoverpoint.impl.RecoverPointClient.RecoverPointReturnCode;
import com.emc.storageos.recoverpoint.objectmodel.RPBookmark;
import com.emc.storageos.recoverpoint.objectmodel.RPConsistencyGroup;

/**
 * Response to a create bookmark request
 * 
 */
@SuppressWarnings("serial")
public class CreateBookmarkResponse implements Serializable {
		private RecoverPointReturnCode _returnCode;
		private Map<String, Date> _volumeWWNBookmarkDateMap;
		private Map<RPConsistencyGroup, Set<RPBookmark>> _cgBookmarkMap;

		public Map <String, Date> getVolumeWWNBookmarkDateMap() {
			return _volumeWWNBookmarkDateMap;
		}

		public void setVolumeWWNBookmarkDateMap(
				Map <String, Date> volumeWWNBookmarkDateMap) {
			this._volumeWWNBookmarkDateMap = volumeWWNBookmarkDateMap;
		}

		public RecoverPointReturnCode getReturnCode() {
			return _returnCode;
		}

		public void setReturnCode(RecoverPointReturnCode returnCode) {
			this._returnCode = returnCode;
		}

        public Map<RPConsistencyGroup, Set<RPBookmark>> getCgBookmarkMap() {
            return _cgBookmarkMap;
        }

        public void setCgBookmarkMap(Map<RPConsistencyGroup, Set<RPBookmark>> cgBookmarkMap) {
            this._cgBookmarkMap = cgBookmarkMap;
        }

}
