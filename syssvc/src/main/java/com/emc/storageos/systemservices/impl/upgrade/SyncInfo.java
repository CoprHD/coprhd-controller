/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.upgrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.emc.storageos.coordinator.client.model.SoftwareVersion;

public class SyncInfo {
    private final List<SoftwareVersion>       _toInstall;
    private final List<SoftwareVersion> _toRemove;
    
    public List<SoftwareVersion> getToInstall() {
        return _toInstall;
    }
    
    public List<SoftwareVersion> getToRemove() {
        return _toRemove;
    }

    public SyncInfo(final List<SoftwareVersion> toInstall, final List<SoftwareVersion> toRemove) {
        _toInstall = Collections.unmodifiableList(toInstall);
        _toRemove  = Collections.unmodifiableList(toRemove);
    }

    public SyncInfo(final SoftwareVersion toInstall, final List<SoftwareVersion> toRemove) {
        _toInstall = new ArrayList<SoftwareVersion>();
        _toInstall.add(toInstall);
        _toRemove  = Collections.unmodifiableList(toRemove);
    }

    public SyncInfo(final List<SoftwareVersion> toRemove) {
        _toInstall = new ArrayList<SoftwareVersion>();
        _toRemove  = toRemove;
    }

    public SyncInfo() {
        this._toInstall = new ArrayList<SoftwareVersion>();
        this._toRemove = new ArrayList<SoftwareVersion>();
    }

    public boolean isEmpty() {
        return (_toInstall == null || _toInstall.isEmpty()) &&
                (_toRemove == null || _toRemove.isEmpty());
    }

    @Override
    public boolean equals(Object o) {
        SyncInfo s = (SyncInfo) o;
        return _toInstall.equals(s._toInstall) && _toRemove.equals(s._toRemove);
    }

    @Override
    public int hashCode() {
        return _toInstall.hashCode();
    }
    
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("install=");
        s.append(Arrays.toString(_toInstall.toArray()));
        s.append(" remove=");
        s.append(Arrays.toString(_toRemove.toArray()));
        return s.toString();
    }
}
