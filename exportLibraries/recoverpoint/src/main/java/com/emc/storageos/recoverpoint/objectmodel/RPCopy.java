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
package com.emc.storageos.recoverpoint.objectmodel;

import com.emc.fapiclient.ws.ConsistencyGroupCopyUID;

/**
 * RP Copy object model object
 * 
 */
public class RPCopy {
    private ConsistencyGroupCopyUID _cgGroupCopyUID;
    private String _cgGroupCopyName;
    private boolean _CGGroupCopyEnabled;			// Note that this refers to the copy, NOT a copy image
    private boolean _isProductionCopy;
    private long _mostRecentProtectionWindow;

    public void cloneMe(RPCopy clone) {
        this.setCGGroupCopyUID(clone.getCGGroupCopyUID());
        this.setCGGroupCopyEnabled(clone.isCGGroupCopyEnabled());
        this.setProductionCopy(clone.isProductionCopy());
        this.setMostRecentProtectionWindow(clone.getMostRecentProtectionWindow());
    }

    public void setCGGroupCopyName(String name) {
        this._cgGroupCopyName = name;
    }

    public String getCGGroupCopyName() {
        return _cgGroupCopyName;
    }

    public void setCGGroupCopyEnabled(boolean enabled) {
        this._CGGroupCopyEnabled = enabled;
    }

    public boolean isCGGroupCopyEnabled() {
        return _CGGroupCopyEnabled;
    }

    public void setCGGroupCopyUID(ConsistencyGroupCopyUID cGGroupCopyUID) {
        _cgGroupCopyUID = cGGroupCopyUID;
    }

    public ConsistencyGroupCopyUID getCGGroupCopyUID() {
        return _cgGroupCopyUID;
    }

    public void setProductionCopy(boolean isProductionCopy) {
        this._isProductionCopy = isProductionCopy;
    }

    public boolean isProductionCopy() {
        return _isProductionCopy;
    }

    public long getMostRecentProtectionWindow() {
        return _mostRecentProtectionWindow;
    }

    public void setMostRecentProtectionWindow(long mostRecentProtectionWindow) {
        this._mostRecentProtectionWindow = mostRecentProtectionWindow;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        RPCopy that = (RPCopy) o;

        boolean CGCopyEqual = false;
        if ((_cgGroupCopyUID.getGlobalCopyUID().getCopyUID() == that._cgGroupCopyUID.getGlobalCopyUID().getCopyUID())
                &&
                (_cgGroupCopyUID.getGroupUID().getId() == that._cgGroupCopyUID.getGroupUID().getId())
                &&
                (_cgGroupCopyUID.getGlobalCopyUID().getClusterUID().getId() == that._cgGroupCopyUID.getGlobalCopyUID().getClusterUID()
                        .getId())) {
            CGCopyEqual = true;
        }
        return CGCopyEqual;
    }

    @Override
    public int hashCode() {
        if (_cgGroupCopyUID.getGlobalCopyUID().getCopyUID() > 0) {
            return _cgGroupCopyUID.getGlobalCopyUID().getCopyUID();
        }

        return super.hashCode();
    }

}
