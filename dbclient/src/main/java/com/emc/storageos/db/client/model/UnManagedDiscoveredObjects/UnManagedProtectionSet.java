/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;

/**
 * An unmanaged protection set describes a RP Consistency Group and its
 * contents:
 * - The Consistency Group's Attributes and Policies
 * - The CG's Copy's Attributes and Policies
 * - List of volume IDs associated with this CG
 *   Note: Attributes about each volume that are gleaned from the process of discovery
 *         are applied to the UnManagedCG object.
 * 
 * This information is collected during an UNMANAGED discovery of the protection system,
 * and is used to assist in ingestion of RP protected volumes.
 *
 */
@Cf("UnManagedProtectionSet")
public class UnManagedProtectionSet extends UnManagedDiscoveredObject {

    // protection system
    private URI _protectionSystemUri;

    // Key: Characteristic, Value: TRUE/FALSE
    private StringMap _cgCharacterstics;

    // Key: Attribute, Value: String (the value of that attribute)
    private StringSetMap _cgInformation;

    // Volume IDs (to UnManagedVolumes)
    private StringSet _unManagedVolumeIds;
    
    // Volume IDs (to Volumes)
    private StringSet _managedVolumeIds;
    
    // Name of the CG on the RP
    private String _cgName;
    
    /**
     * These are characteristics that an RP CG can take on.
     */
    public enum SupportedCGCharacterstics {
        IS_MP("MetroPoint"),
        IS_SYNC("Synchronous"),
        IS_ENABLED("Enabled");
 
        private final String _charactersticsKey;

        SupportedCGCharacterstics(String charactersticsKey) {
            _charactersticsKey = charactersticsKey;
        }

        public String getCharacterstic() {
            return _charactersticsKey;
        }

        public static String getCGCharacterstic(String charactersticsKey) {
            for (SupportedCGCharacterstics characterstic : values()) {
                if (characterstic.getCharacterstic().equalsIgnoreCase(charactersticsKey)) {
                    return characterstic.toString();
                }
            }
            return null;
        }
    }

    /**
     * Specific information about CGs retrieved from RP that we can use
     * to validate against vpool settings, such as policy settings.
     */
    public enum SupportedCGInformation {
        RPO("RPO");

        private final String _infoKey;

        SupportedCGInformation(String infoKey) {
            _infoKey = infoKey;
        }

        public String getInfoKey() {
            return _infoKey;
        }

        public static String getCGInformation(String infoKey) {
            for (SupportedCGInformation info : values()) {
                if (info.getInfoKey().equalsIgnoreCase(infoKey)) {
                    return info.toString();
                }
            }
            return null;
        }
    }

    // Replaces key entry in the volumeInformation map with the new set.
    public void putCGInfo(String key, StringSet values) {
        if (null == _cgInformation) {
            setCGInformation(new StringSetMap());
        }

        StringSet oldValues = _cgInformation.get(key);
        if (oldValues != null) {
            oldValues.replace(values);
        } else {
            _cgInformation.put(key, values);
        }
    }

    public void addCGInformation(Map<String, StringSet> volumeInfo) {
        if (null == _cgInformation) {
            setCGInformation(new StringSetMap());
        } else {
            _cgInformation.clear();
        }

        if (volumeInfo.size() > 0) {
            _cgInformation.putAll(volumeInfo);
        }
    }

    public void setCGInformation(StringSetMap volumeInfo) {
        _cgInformation = volumeInfo;
    }

    @Name("cgInformation")
    public StringSetMap getCGInformation() {
        return _cgInformation;
    }

    public void putCGCharacterstics(String key, String value) {
        if (null == _cgCharacterstics) {
            setCGCharacterstics(new StringMap());
        } else {
            _cgCharacterstics.put(key, value);
        }
    }

    public void setCGCharacterstics(StringMap cgCharacterstics) {
        _cgCharacterstics = cgCharacterstics;
    }

    @Name("cgCharacterstics")
    public StringMap getCGCharacterstics() {
        return _cgCharacterstics;
    }

    @RelationIndex(cf = "UnManagedCGRelationIndex", type = ProtectionSystem.class)
    @Name("protectionDevice")
    public URI getProtectionSystemUri() {
        return _protectionSystemUri;
    }

    public void setProtectionSystemUri(URI protectionSystemUri) {
        _protectionSystemUri = protectionSystemUri;
        setChanged("protectionDevice");
    }

    @AlternateId("UnManagedProtectionSetCgIndex")
    @Name("cgName")
    public String getCgName() {
        return _cgName;
    }

    public void setCgName(String cgName) {
        _cgName = cgName;
        setChanged("cgName");
    }

    @IndexByKey
    @AlternateId("UnManagedVolumesIdIndex")
    @Name("unManagedVolumeIds")
    public StringSet getUnManagedVolumeIds() {
        if (null == _unManagedVolumeIds) {
            this.setUnManagedVolumeIds(new StringSet());
        }
        return _unManagedVolumeIds;
    }

    public void setUnManagedVolumeIds(StringSet unManagedVolumesIds) {
        this._unManagedVolumeIds = unManagedVolumesIds;
    }

    @IndexByKey
    @AlternateId("ManagedVolumesIdIndex")
    @Name("managedVolumeIds")
    public StringSet getManagedVolumeIds() {
        if (null == _managedVolumeIds) {
            this.setManagedVolumeIds(new StringSet());
        }
        return _managedVolumeIds;
    }

    public void setManagedVolumeIds(StringSet managedVolumesIds) {
        this._managedVolumeIds = managedVolumesIds;
    }

    @Override
    public String toString() {
        return this.getLabel() + " (" + this.getId() + ")";
    }
}
