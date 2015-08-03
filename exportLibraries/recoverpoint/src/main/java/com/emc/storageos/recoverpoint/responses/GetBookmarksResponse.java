/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.emc.storageos.recoverpoint.impl.RecoverPointClient.RecoverPointReturnCode;
import com.emc.storageos.recoverpoint.objectmodel.RPBookmark;

/**
 * Response to a get bookmark request for multiple CGs
 * 
 */
@SuppressWarnings("serial")
public class GetBookmarksResponse implements Serializable {
    private RecoverPointReturnCode _returnCode;
    private Map<Integer, List<RPBookmark>> _cgBookmarkMap;

    public Map<Integer, List<RPBookmark>> getCgBookmarkMap() {
        return _cgBookmarkMap;
    }

    public void setCgBookmarkMap(
            Map<Integer, List<RPBookmark>> cgBookmarkMap) {
        this._cgBookmarkMap = cgBookmarkMap;
    }

    public RecoverPointReturnCode getReturnCode() {
        return _returnCode;
    }

    public void setReturnCode(RecoverPointReturnCode returnCode) {
        this._returnCode = returnCode;
    }
}
