/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import java.net.URI;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;

@Cf("UnManagedExportMask")
public class UnManagedExportMask extends UnManagedDiscoveredObject {

    private static final long serialVersionUID = 1L;

    private URI _storageSystemUri;
    private String _maskingViewPath;
    private String _maskName;
    private String _nativeId;

    private StringSet _knownInitiatorUris;
    private StringSet _knownInitiatorNetworkIds;
    private StringSet _unmanagedInitiatorNetworkIds;
    private StringSet _knownStoragePortUris;
    private StringSet _unmanagedStoragePortNetworkIds;
    private StringSet _knownVolumeUris;
    private StringSet _unmanagedVolumeUris;
    private ZoneInfoMap _zoningMap;

    @RelationIndex(cf = "UnManagedExportMaskRelationIndex", type = StorageSystem.class)
    @Name("storageSystem")
    public URI getStorageSystemUri() {
        return _storageSystemUri;
    }

    public void setStorageSystemUri(URI storageSystemUri) {
        this._storageSystemUri = storageSystemUri;
        setChanged("storageSystem");
    }

    @Name("maskingViewPath")
    @AlternateId("AltIdIndex")
    public String getMaskingViewPath() {
        return _maskingViewPath;
    }

    public void setMaskingViewPath(String maskingViewPath) {
        this._maskingViewPath = maskingViewPath;
        setChanged("maskingViewPath");
    }

    @Name("maskName")
    public String getMaskName() {
        return _maskName;
    }

    public void setMaskName(String maskName) {
        _maskName = maskName;
        setChanged("maskName");
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        _nativeId = nativeId;
        setChanged("nativeId");
    }

    @Name("knownInitiatorUris")
    public StringSet getKnownInitiatorUris() {
        if (null == _knownInitiatorUris) {
            this.setKnownInitiatorUris(new StringSet());
        }
        return _knownInitiatorUris;
    }

    public void setKnownInitiatorUris(StringSet knownInitiatorUris) {
        this._knownInitiatorUris = knownInitiatorUris;
    }

    @IndexByKey
    @AlternateId("KnownInitiatorNetworkIdIndex")
    @Name("knownInitiatorNetworkIds")
    public StringSet getKnownInitiatorNetworkIds() {
        if (null == _knownInitiatorNetworkIds) {
            this.setKnownInitiatorNetworkIds(new StringSet());
        }
        return _knownInitiatorNetworkIds;
    }

    public void setKnownInitiatorNetworkIds(StringSet knownInitiatorNetworkIds) {
        this._knownInitiatorNetworkIds = knownInitiatorNetworkIds;
    }

    @Name("unmanagedInitiatorNetworkIds")
    public StringSet getUnmanagedInitiatorNetworkIds() {
        if (null == _unmanagedInitiatorNetworkIds) {
            this.setUnmanagedInitiatorNetworkIds(new StringSet());
        }
        return _unmanagedInitiatorNetworkIds;
    }

    public void setUnmanagedInitiatorNetworkIds(StringSet unmanagedInitiatorNetworkIds) {
        this._unmanagedInitiatorNetworkIds = unmanagedInitiatorNetworkIds;
    }

    @Name("knownStoragePortUris")
    @AlternateId("UnManagedMaskStoragePorts")
    public StringSet getKnownStoragePortUris() {
        if (null == _knownStoragePortUris) {
            setKnownStoragePortUris(new StringSet());
        }
        return _knownStoragePortUris;
    }

    public void setKnownStoragePortUris(StringSet knownStoragePortUris) {
        this._knownStoragePortUris = knownStoragePortUris;
    }

    @Name("unmanagedStoragePortNetworkIds")
    public StringSet getUnmanagedStoragePortNetworkIds() {
        if (null == _unmanagedStoragePortNetworkIds) {
            setUnmanagedStoragePortNetworkIds(new StringSet());
        }
        return _unmanagedStoragePortNetworkIds;
    }

    public void setUnmanagedStoragePortNetworkIds(
            StringSet unmanagedStoragePortNetworkIds) {
        this._unmanagedStoragePortNetworkIds = unmanagedStoragePortNetworkIds;
    }

    @Name("knownVolumeUris")
    public StringSet getKnownVolumeUris() {
        if (null == _knownVolumeUris) {
            setKnownVolumeUris(new StringSet());
        }
        return _knownVolumeUris;
    }

    public void setKnownVolumeUris(StringSet knownVolumeUris) {
        this._knownVolumeUris = knownVolumeUris;
    }

    @Name("unmanagedVolumeUris")
    public StringSet getUnmanagedVolumeUris() {
        if (null == _unmanagedVolumeUris) {
            setUnmanagedVolumeUris(new StringSet());
        }
        return _unmanagedVolumeUris;
    }

    public void setUnmanagedVolumeUris(
            StringSet unmanagedVolumeUris) {
        this._unmanagedVolumeUris = unmanagedVolumeUris;
    }

    @Name("zoningMap")
    public ZoneInfoMap getZoningMap() {
        if (_zoningMap == null) {
            _zoningMap = new ZoneInfoMap();
        }
        return _zoningMap;
    }

    public void setZoningMap(ZoneInfoMap zoningMap) {
        if (_zoningMap == null) {
            _zoningMap = zoningMap;
            setChanged("zoningMap");
        } else {
            _zoningMap.replace(zoningMap);
        }
    }

    public void addZoningMap(ZoneInfoMap zoningMapEntries) {
        if (this._zoningMap == null) {
            setZoningMap(zoningMapEntries);
        } else {
            this._zoningMap.putAll(zoningMapEntries);
        }
    }

    public void removeZoningMapEntry(String key) {
        if (this._zoningMap != null) {
            // This seemingly contorted logic is to avoid
            // a concurrent update error.
            _zoningMap.remove(key);
        }
    }

    /**
     * Add an entry to create a zone between an initiator and port.
     * 
     * @param initiator URI as String
     * @param storagePort URI as String
     */
    public void addZoningMapEntry(ZoneInfo zoningInfo) {
        if (this._zoningMap == null) {
            this._zoningMap = new ZoneInfoMap();
        }
        this._zoningMap.put(zoningInfo.getZoneReferenceKey(), zoningInfo);
    }

    /**
     * Update initiator/volumes/ports
     * 
     * @param knownIniSet
     * @param knownNetworkIdSet
     * @param knownVolumeSet
     * @param knownPortSet
     */
    public void replaceNewWithOldResources(StringSet knownIniSet, StringSet knownNetworkIdSet, StringSet knownVolumeSet,
            StringSet knownPortSet) {
        // CTRL - 8918 - always update the mask with new initiators and volumes.
        if (getKnownInitiatorUris() == null) {
            setKnownInitiatorUris(new StringSet());
        } else {
            getKnownInitiatorUris().replace(knownIniSet);
        }

        if (getKnownInitiatorNetworkIds() == null) {
            setKnownInitiatorNetworkIds(new StringSet());
        } else {
            getKnownInitiatorNetworkIds().replace(knownNetworkIdSet);
        }

        if (getKnownStoragePortUris() == null) {
            setKnownStoragePortUris(new StringSet());
        } else {
            getKnownStoragePortUris().replace(knownPortSet);
        }

        if (getKnownVolumeUris() == null) {
            setKnownVolumeUris(new StringSet());
        } else {
            getKnownVolumeUris().replace(knownVolumeSet);
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("UnManagedExportMask: ");
        str.append(_maskingViewPath);
        str.append("; maskName: ").append(_maskName);
        str.append("; nativeId: ").append(_nativeId);
        str.append("; known initiators: ").append(this.getKnownInitiatorUris());
        str.append("; known initiator network ids: ").append(this.getKnownInitiatorNetworkIds());
        str.append("; unmanaged initiators network ids: ").append(this.getUnmanagedInitiatorNetworkIds());
        str.append("; known storage ports: ").append(this.getKnownStoragePortUris());
        str.append("; unmanaged storage ports: ").append(this.getUnmanagedStoragePortNetworkIds());
        str.append("; known storage volumes: ").append(this.getKnownVolumeUris());
        str.append("; unmanaged storage volumes: ").append(this.getUnmanagedVolumeUris());
        str.append("; zoning map: ").append(this.getZoningMap());
        return str.toString();
    }

}
