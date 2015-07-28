/*
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

    public Map<String, Date> getVolumeWWNBookmarkDateMap() {
        return _volumeWWNBookmarkDateMap;
    }

    public void setVolumeWWNBookmarkDateMap(
            Map<String, Date> volumeWWNBookmarkDateMap) {
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
