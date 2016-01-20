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
    private StringMap _cgCharacteristics;

    // Key: Attribute, Value: String (the value of that attribute)
    private StringSetMap _cgInformation;

    // Volume IDs (to UnManagedVolumes)
    private StringSet _unManagedVolumeIds;
    
    // Volume IDs (to Volumes)
    private StringSet _managedVolumeIds;

    // Volume WWNs (in case some volumes aren't in our UnManaged inventory)
    private StringSet _volumeWwns;
    
    // Name of the CG on the RP
    private String _cgName;
    
    /**
     * These are characteristics that an RP CG can take on.
     */
    public enum SupportedCGCharacteristics {
        IS_MP("MetroPoint"),
        IS_SYNC("Synchronous"),
        IS_HEALTHY("Healthy");
 
        private final String _characteristicsKey;

        SupportedCGCharacteristics(String characteristicsKey) {
            _characteristicsKey = characteristicsKey;
        }

        public String getCharacterstic() {
            return _characteristicsKey;
        }

        public static String getCGCharacterstic(String characteristicsKey) {
            for (SupportedCGCharacteristics characterstic : values()) {
                if (characterstic.getCharacterstic().equalsIgnoreCase(characteristicsKey)) {
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
        RPO_TYPE("RPOType"),
        RPO_VALUE("RPOValue"),
        PROTECTION_ID("ProtectionID");

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

    public void putCGCharacteristics(String key, String value) {
        if (null == _cgCharacteristics) {
            setCGCharacteristics(new StringMap());
        } else {
            _cgCharacteristics.put(key, value);
        }
    }

    public void setCGCharacteristics(StringMap cgCharacteristics) {
        _cgCharacteristics = cgCharacteristics;
    }

    @Name("cgCharacteristics")
    public StringMap getCGCharacteristics() {
        if (null == _cgCharacteristics) {
            setCGCharacteristics(new StringMap());
        }
        return _cgCharacteristics;
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
    @AlternateId("UnManagedVolumeIdsIndex")
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
    @AlternateId("ManagedVolumeIdsIndex")
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

    @IndexByKey
    @AlternateId("VolumeWwnIndex")
    @Name("volumeWwns")
    public StringSet getVolumeWwns() {
        if (null == _volumeWwns) {
            this.setVolumeWwns(new StringSet());
        }
        return _volumeWwns;
    }

    public void setVolumeWwns(StringSet volumeWwns) {
        this._volumeWwns = volumeWwns;
    }
    
    @Override
    public String toString() {
        return this.getLabel() + " (" + this.getId() + ")";
    }
}
