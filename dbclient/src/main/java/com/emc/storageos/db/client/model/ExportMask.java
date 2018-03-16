/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.collectionString;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.google.common.collect.Collections2;

/**
 * Export mask on a storage-system - represents a lun masking setup on a storage system.
 * Export mask is used for volume simple exports and for per storage-system export group
 * operations.
 */
@Cf("ExportMask")
public class ExportMask extends DataObject {

    private URI _storageDevice;
    private String _maskName;
    private String _nativeId;

    // Volumes in the mask
    // Map URI --> HLU
    private StringMap _volumes;

    // Initiators in the mask
    // Initiators known by ViPR in the compute resource of the mask
    private StringSet _initiators;
    private StringSet _storagePorts;

    // These parameters will be used for tracking purposes
    // so that export operations can be performed on existing
    // array masking components

    // Volumes that were added to the mask by ViPR user.
    // Map WWN --> BlockObject::URI
    private StringMap _userAddedVolumes;

    // Volumes that were not added to the mask by ViPR user.
    // Note: Regardless of ViPR's knowledge of volume in our own DB.
    // Volumes in this list do not appear in _volumes or _userAddedVolumes.
    // Map WWN --> HLU
    private StringMap _existingVolumes;

    // Initiators that were added to the mask by ViPR user.
    // Map portName --> Initiator::URI
    private StringMap _userAddedInitiators;

    // Initiators that were not added to the mask by ViPR user and not
    // associated with the compute resource(s) of the mask.
    // Note: initiators in this list do not appear in _initiators or _userAddedInitiators.
    // Set portName
    private StringSet _existingInitiators;

    // Flag. If true => the mask was created as a result of a Bourne ExportGroup
    // create. Otherwise, it was already existing on the array (created outside of
    // Bourne).
    private Boolean _createdBySystem;

    // Not technically part of the ExportMask on the array, but this is the
    // map of initiators to storagePorts used for zoning. If there is an
    // entry here, then the corresponding SAN zone will be created between
    // the Initiator and StoragePort. If there is no entry here, then
    // no zone will be created. If _zoningMap is null, then all initiators
    // are zoned to all ports within the ExportMask.
    // Initiator::id -> Set<StoragePort::id>
    private StringSetMap _zoningMap;

    // This is the name of the resource that this mask applies to. It can be a
    // hostname, cluster name, or some identifier that allows the mask to be easily
    // associated to the thing that we would to export volumes to. It is here mostly
    // as a convenience.
    private String _resource;

    // port group uri
    private URI _portGroup;
    
    // Captures the Device Specific information that are created for this export mask.
    private StringSetMap _deviceDataMap;

    public static enum State {
        initializing,       // export mask is being created
        ready,              // export mask is created
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        _storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    @Name("maskName")
    @AlternateId("AltIdIndex")
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

    @Name("volumes")
    public StringMap getVolumes() {
        return _volumes;
    }

    public void setVolumes(StringMap volumes) {
        _volumes = volumes;
    }

    public boolean emptyVolumes() {
        return _volumes == null || _volumes.isEmpty();
    }

    @Name("initiators")
    @AlternateId("ExportMaskInitiators")
    public StringSet getInitiators() {
        if (_initiators == null) {
            _initiators = new StringSet();
        }
        return _initiators;
    }

    public void setInitiators(StringSet initiators) {
        _initiators = initiators;
    }

    @Name("storagePorts")
    @AlternateId("ExportMaskStoragePorts")
    public StringSet getStoragePorts() {
        return _storagePorts;
    }

    public void setStoragePorts(StringSet storagePorts) {
        _storagePorts = storagePorts;
        setChanged("storagePorts");
    }

    public void setStoragePorts(Collection<String> storagePorts) {
        _storagePorts = new StringSet(storagePorts);
        setChanged("storagePorts");
    }

    public boolean hasTargets(Collection<URI> storagePorts) {
        boolean result = false;
        if (_storagePorts != null) {
            Collection<String> ports = Collections2.transform(storagePorts,
                    CommonTransformerFunctions.FCTN_URI_TO_STRING);
            result = _storagePorts.containsAll(ports);
        }
        return result;
    }

    /**
     * @return the _deviceData
     */
    @Name("deviceDataMap")
    public StringSetMap getDeviceDataMap() {
        return _deviceDataMap;
    }

    /**
     * Returns a StringSet from the deviceDataMap entries corresponding to the supplied key.
     * If the key is not present, an empty StringSet is returned.
     * 
     * @param key -- String Key
     * @return -- StringSet (will be empty if no entries for key)
     */
    public StringSet fetchDeviceDataMapEntry(String key) {
        if (getDeviceDataMap() == null || !getDeviceDataMap().containsKey(key)) {
            return new StringSet();
        }
        return getDeviceDataMap().get(key);
    }

    /**
     * @param _deviceDataMap the _deviceData to set
     */
    public void setDeviceDataMap(StringSetMap deviceData) {
        this._deviceDataMap = deviceData;
    }

    public void addDeviceDataMap(StringSetMap deviceDataMapEntries) {
        if (this._deviceDataMap == null) {
            setDeviceDataMap(deviceDataMapEntries);
        } else {
            this._deviceDataMap.putAll(deviceDataMapEntries);
        }
    }

    public void replaceDeviceDataMapEntries(StringSetMap deviceDataMapEntries) {
        if (null != deviceDataMapEntries
                && !deviceDataMapEntries.isEmpty()) {
            _deviceDataMap.replace(deviceDataMapEntries);
        }
    }

    public void removeDeviceDataMapEntry(String key) {
        if (this._deviceDataMap != null) {
            // This seemingly consorted logic is to avoid
            // a concurrent update error.
            StringSet set = _deviceDataMap.get(key);
            if (set != null && !set.isEmpty()) {
                StringSet values = new StringSet();
                values.addAll(set);
                for (String value : values) {
                    _deviceDataMap.remove(key, value);
                }
            }
        }
    }

    /**
     * Enumeration of key values for the DeviceDataMap.
     */
    public enum DeviceDataMapKeys {
        // allows over-riding default VMAX Consistent LUNS Initiator Group Setting
        VMAXConsistentLUNs,
        // prevents the Networking code from deleting zoningMap entries when removing zones
        ImmutableZoningMap
    };

    /**
     * Add a mapping of the specified block object to an HLU value.
     * 
     * @param blockObjectURI - URI of a BlockObject instance
     * @param lun - Integer HLU value
     * 
     */
    public void addVolume(URI blockObjectURI, Integer lun) {
        if (getVolumes() == null) {
            setVolumes(new StringMap());
        }
        getVolumes().put(blockObjectURI.toString(), lun.toString());
    }

    public void addVolumes(Map<URI, Integer> volumeMap) {
        if (volumeMap != null) {
            if (_volumes == null) {
                _volumes = new StringMap();
            }
            for (Map.Entry<URI, Integer> entry : volumeMap.entrySet()) {
                Integer hlu = entry.getValue();
                // Non unassigned values are placed by the
                // ExportMaskOperationsHelper#setHLUFromProtocolControllers call,
                // which pulls the HLU from the provider. Add the volume entry only if:
                // - it doesn't exist in the map
                // OR
                // - it is not the LUN_UNASSIGNED value
                if (!_volumes.containsKey(entry.getKey().toString()) ||
                        (hlu != null && hlu != ExportGroup.LUN_UNASSIGNED)) {
                    _volumes.put(entry.getKey().toString(), hlu.toString());
                }
            }
        }
    }

    public void removeVolume(URI blockObjectURI) {
        if (_volumes != null) {
            _volumes.remove(blockObjectURI.toString());
        }
    }

    public void removeVolumes(List<URI> volumes) {
        if (_volumes != null) {
            for (URI uri : volumes) {
                _volumes.remove(uri.toString());
                // TODO: Remove user added volumes
            }
        }
    }

    /**
     * 
     * @param initiator
     * @return true if newly added, else false
     */
    public void addInitiator(Initiator initiator) {
        if (checkForNull(initiator)) {
            return;
        }
        if (_initiators == null) {
            _initiators = new StringSet();
        }
        if (!_initiators.contains(initiator.getId().toString())) {
            _initiators.add(initiator.getId().toString());
        }
    }

    public void addInitiators(Collection<Initiator> initiators) {
        if (_initiators == null) {
            _initiators = new StringSet();
        }
        filterOutNulls(initiators);
        for (Initiator initiator : initiators) {
            if (!_initiators.contains(initiator.getId().toString())) {
                _initiators.add(initiator.getId().toString());
            }
        }
    }

    public void removeInitiator(Initiator initiator) {
        if (checkForNull(initiator)) {
            return;
        }
        if (_initiators != null) {
            _initiators.remove(initiator.getId().toString());
            removeZoningMapEntry(initiator.getId().toString());
        }
    }

    public void removeInitiator(URI initiatorURI) {
        if (_initiators != null) {
            _initiators.remove(initiatorURI.toString());
            removeZoningMapEntry(initiatorURI.toString());
        }
    }

    public void removeInitiators(Collection<Initiator> initiators) {
        filterOutNulls(initiators);
        if (_initiators != null) {
            for (Initiator initiator : initiators) {
                _initiators.remove(initiator.getId().toString());
                removeZoningMapEntry(initiator.getId().toString());
            }
        }
    }

    public void removeInitiatorURIs(Collection<URI> initiatorURIs) {
        if (_initiators != null) {
            for (URI uri : initiatorURIs) {
                _initiators.remove(uri.toString());
                removeZoningMapEntry(uri.toString());
            }
        }
    }

    /**
     * 
     * @param target
     * @return true if newly added, else false
     */
    public boolean addTarget(URI target) {
        if (_storagePorts == null) {
            _storagePorts = new StringSet();
        }
        return _storagePorts.add(target.toString());
    }

    public void removeTarget(URI target) {
        _storagePorts.remove(target.toString());
    }

    public void removeTargets(Collection<URI> targetURIs) {
        if (_storagePorts != null) {
            for (URI uri : targetURIs) {
                _storagePorts.remove(uri.toString());
            }
        }
    }

    @Name("userAddedVolumes")
    public StringMap getUserAddedVolumes() {
        return _userAddedVolumes;
    }

    public void setUserAddedVolumes(StringMap userAddedVolumes) {
        _userAddedVolumes = userAddedVolumes;
    }

    @Name("existingVolumes")
    public StringMap getExistingVolumes() {
        return _existingVolumes;
    }

    public void setExistingVolumes(StringMap existingVolumes) {
        _existingVolumes = existingVolumes;
    }

    public boolean hasUserCreatedVolume(String wwn) {
        boolean result = false;
        if (_userAddedVolumes != null) {
            result = _userAddedVolumes.containsKey(BlockObject.normalizeWWN(wwn));
        }
        return result;
    }

    public boolean hasUserCreatedVolume(URI volumeId) {
        boolean result = false;
        if (_userAddedVolumes != null) {
            result = _userAddedVolumes.containsValue(volumeId.toString());
        }
        return result;
    }

    @Name("userAddedInitiators")
    public StringMap getUserAddedInitiators() {
        return _userAddedInitiators;
    }

    public void setUserAddedInitiators(StringMap userAddedInitiators) {
        _userAddedInitiators = userAddedInitiators;
    }

    @Name("existingInitiators")
    @AlternateId("ExportMaskExistingInitiators")
    public StringSet getExistingInitiators() {
        return _existingInitiators;
    }

    public void setExistingInitiators(StringSet existingInitiators) {
        _existingInitiators = existingInitiators;
    }

    public boolean hasAnyExistingInitiators() {
        return (_existingInitiators != null && !_existingInitiators.isEmpty());
    }

    public void addToUserCreatedInitiators(Initiator initiator) {
        if (checkForNull(initiator)) {
            return;
        }
        if (_userAddedInitiators == null) {
            _userAddedInitiators = new StringMap();
        }
        String normalizedPort = Initiator.normalizePort(initiator.getInitiatorPort());
        _userAddedInitiators.put(normalizedPort, initiator.getId().toString());
    }

    public void addToUserCreatedInitiators(List<Initiator> initiators) {
        if (_userAddedInitiators == null) {
            _userAddedInitiators = new StringMap();
        }
        for (Initiator initiator : initiators) {
            addToUserCreatedInitiators(initiator);
        }
    }

    public void addToExistingInitiatorIfAbsent(List<Initiator> initiators) {
        for (Initiator initiator : initiators) {
            addToExistingInitiatorsIfAbsent(initiator);
        }
    }

    public void removeFromUserCreatedInitiators(Initiator initiator) {
        if (checkForNull(initiator)) {
            return;
        }
        if (_userAddedInitiators != null) {
            String normalizedPort = Initiator.normalizePort(initiator.getInitiatorPort());
            _userAddedInitiators.remove(normalizedPort);
        }
    }

    public void removeFromUserCreatedInitiators(List<Initiator> initiators) {
        if (_userAddedInitiators != null) {
            for (Initiator initiator : initiators) {
                removeFromUserCreatedInitiators(initiator);
            }
        }
    }

    public void removeFromUserAddedInitiatorsByURI(Collection<URI> initiatorURIs) {
        if (_userAddedInitiators != null && initiatorURIs != null && !initiatorURIs.isEmpty()) {
            Set<String> keysToRemove = new HashSet<>();
            for (Map.Entry<String, String> entry : _userAddedInitiators.entrySet()) {
                if (initiatorURIs.contains(URI.create(entry.getValue()))) {
                    keysToRemove.add(entry.getKey());
                }
            }
            for (String key : keysToRemove) {
                _userAddedInitiators.remove(key);
            }
        }
    }

    public void removeFromExistingInitiator(List<Initiator> initiators) {
        for (Initiator ini : initiators) {
            removeFromExistingInitiators(ini.getInitiatorPort());
        }
    }

    public void removeFromExistingInitiators(Collection<String> initiatorWWNs) {
        if (!CollectionUtils.isEmpty(_existingInitiators) && !CollectionUtils.isEmpty(initiatorWWNs)) {
            _existingInitiators.removeAll(initiatorWWNs);
        }
    }

    public boolean hasAnyVolumes() {
        return (_existingVolumes != null && !_existingVolumes.isEmpty()) ||
                (_userAddedVolumes != null && !_userAddedVolumes.isEmpty());
    }

    public boolean hasAnyUserAddedVolumes() {
        return (_userAddedVolumes != null && !_userAddedVolumes.isEmpty());
    }

    public void addToUserCreatedVolumes(Collection<BlockObject> blockObjects) {
        if (_userAddedVolumes == null) {
            _userAddedVolumes = new StringMap();
        }
        filterOutNulls(blockObjects);
        for (BlockObject blockObject : blockObjects) {
            _userAddedVolumes.put(BlockObject.normalizeWWN(blockObject.getWWN()),
                    blockObject.getId().toString());
        }
    }

    public void addToUserCreatedVolumes(BlockObject blockObject) {
        if (checkForNull(blockObject)) {
            return;
        }
        if (_userAddedVolumes == null) {
            _userAddedVolumes = new StringMap();
        }
        _userAddedVolumes.put(BlockObject.normalizeWWN(blockObject.getWWN()),
                blockObject.getId().toString());
    }

    public void removeFromUserCreatedVolumes(BlockObject blockObject) {
        if (checkForNull(blockObject)) {
            return;
        }
        if (_userAddedVolumes != null) {
            _userAddedVolumes.remove(BlockObject.normalizeWWN(blockObject.getWWN()));
        }
    }

    public void removeFromUserAddedVolumesByURI(Collection<URI> blockObjectURIs) {
        if (_userAddedVolumes != null && blockObjectURIs != null && !blockObjectURIs.isEmpty()) {
            Set<String> keysToRemove = new HashSet<>();
            for (Map.Entry<String, String> entry : _userAddedVolumes.entrySet()) {
                if (blockObjectURIs.contains(URI.create(entry.getValue()))) {
                    keysToRemove.add(entry.getKey());
                }
            }
            for (String key : keysToRemove) {
                _userAddedVolumes.remove(key);
            }
        }
    }

    public void addToExistingInitiatorsIfAbsent(Initiator initiator) {
        if (checkForNull(initiator)) {
            return;
        }
        if (!_initiators.contains(initiator.getId().toString())) {
            addToExistingInitiatorsIfAbsent(initiator.getInitiatorPort());
        }
    }

    /**
     * This method will add to the existing initiators list only if the port doesn't
     * already exist in either the existing or user-created initiator list.
     * 
     * @param port [in] - Port name to add to the existing initiator list.
     */
    public void addToExistingInitiatorsIfAbsent(String port) {
        String normalizedPort = Initiator.normalizePort(port);
        if ((_existingInitiators == null || !_existingInitiators.contains(normalizedPort)) &&
                (_userAddedInitiators == null ||
                !_userAddedInitiators.containsKey(normalizedPort))) {
            if (_existingInitiators == null) {
                _existingInitiators = new StringSet();
            }
            _existingInitiators.add(normalizedPort);
        }
    }

    /**
     * This method will add to the existing initiators list only if the port doesn't
     * already exist in either the existing or user-created initiator list.
     * 
     * @param ports [in] - List of port names to add to the existing initiator list.
     */
    public void addToExistingInitiatorsIfAbsent(List<String> ports) {
        for (String port : ports) {
            String normalizedPort = Initiator.normalizePort(port);
            if ((_existingInitiators == null || !_existingInitiators.contains(normalizedPort)) &&
                    (_userAddedInitiators == null ||
                    !_userAddedInitiators.containsKey(normalizedPort))) {
                if (_existingInitiators == null) {
                    _existingInitiators = new StringSet();
                }
                _existingInitiators.add(normalizedPort);
            }
        }
    }

    public void removeFromExistingInitiators(Initiator initiator) {
        if (checkForNull(initiator)) {
            return;
        }
        removeFromExistingInitiators(initiator.getInitiatorPort());
    }

    public void removeFromExistingInitiators(String port) {
        if (_existingInitiators != null) {
            String normalizedPort = Initiator.normalizePort(port);
            _existingInitiators.remove(normalizedPort);
        }
    }

    public void removeFromExistingInitiators(List<String> ports) {
        if (_existingInitiators != null) {
            for (String port : ports) {
                String normalizedPort = Initiator.normalizePort(port);
                _existingInitiators.remove(normalizedPort);
            }
        }
    }

    public void addToExistingVolumesIfAbsent(BlockObject bo, String hlu) {
        addToExistingVolumesIfAbsent(bo.getWWN(), hlu);
    }

    /**
     * This method will add to the existing volumes list only if the WWN doesn't
     * already exist in either the existing or user-created volume list.
     * 
     * @param volumeWWN [in] - World Wide Name of volume that will have to be added
     *            to the existing volumes list for this mask.
     */
    public void addToExistingVolumesIfAbsent(String volumeWWN, String hlu) {
        String normalizedWWN = BlockObject.normalizeWWN(volumeWWN);
        if ((_existingVolumes == null || !_existingVolumes.containsKey(normalizedWWN)) &&
                (_userAddedVolumes == null ||
                !_userAddedVolumes.containsKey(normalizedWWN))) {
            if (_existingVolumes == null) {
                _existingVolumes = new StringMap();
            }
            _existingVolumes.put(normalizedWWN, hlu);
        }
    }

    /**
     * This method will add to the existing volumes list only those members that don't
     * already exist in either the existing or user-created volume list.
     * 
     * @param volumeWWNs [in] - World Wide Names of volumes that will have to be added
     *            to the existing volumes list for this mask.
     */
    public void addToExistingVolumesIfAbsent(Map<String, Integer> volumeWWNs) {
        for (String wwn : volumeWWNs.keySet()) {
            String normalizedWWN = BlockObject.normalizeWWN(wwn);
            if ((_existingVolumes == null || !_existingVolumes.containsKey(normalizedWWN)) &&
                    (_userAddedVolumes == null ||
                    !_userAddedVolumes.containsKey(normalizedWWN))) {
                String hluStr = ExportGroup.LUN_UNASSIGNED_STR;
                Integer hlu = volumeWWNs.get(wwn);
                if (hlu != null) {
                    hluStr = hlu.toString();
                }
                if (_existingVolumes == null) {
                    _existingVolumes = new StringMap();
                }
                _existingVolumes.put(normalizedWWN, hluStr);
            }
        }
    }

    public void removeFromExistingVolumes(BlockObject blockObject) {
        removeFromExistingVolumes(blockObject.getWWN());
    }

    public void removeFromExistingVolumes(String volumeWWN) {
        if (_existingVolumes != null) {
            _existingVolumes.remove(BlockObject.normalizeWWN(volumeWWN));
        }
    }

    public void removeFromExistingVolumes(List<String> volumeWWNs) {
        if (_existingVolumes != null) {
            for (String volumeWWN : volumeWWNs) {
                _existingVolumes.remove(BlockObject.normalizeWWN(volumeWWN));
            }
        }
    }

    /**
     * 
     * @param String initiatorId
     * @return true if the initiator is in the mask, else false
     */
    public boolean hasInitiator(String initiatorId) {
        boolean hasInitiator = false;
        if (_initiators != null) {
            hasInitiator = _initiators.contains(initiatorId);
        }

        return hasInitiator;
    }

    public boolean hasUserInitiator(String port) {
        boolean hasInitiator = false;
        if (_userAddedInitiators != null) {
            hasInitiator =
                    _userAddedInitiators.containsKey(Initiator.normalizePort(port));
        }
        return hasInitiator;
    }

    public boolean hasUserInitiator(URI initiatorId) {
        boolean hasInitiator = false;
        if (_userAddedInitiators != null) {
            hasInitiator =
                    _userAddedInitiators.containsValue(initiatorId.toString());
        }
        return hasInitiator;
    }

    public boolean hasExistingInitiator(Initiator initiator) {
        return hasExistingInitiator(initiator.getInitiatorPort());
    }

    public boolean hasExistingInitiator(String port) {
        boolean hasInitiator = false;
        if (_existingInitiators != null) {
            String normalizedPort = Initiator.normalizePort(port);
            hasInitiator = _existingInitiators.contains(normalizedPort);
        }
        return hasInitiator;
    }

    public boolean hasExistingInitiator(List<Initiator> initiators) {
        if (_existingInitiators != null) {
            for (Initiator initiator : initiators) {
                if (hasExistingInitiator(initiator)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasAnyExistingVolumes() {
        return (_existingVolumes != null && !_existingVolumes.isEmpty());
    }

    public boolean hasExistingVolume(BlockObject blockObject) {
        if (checkForNull(blockObject)) {
            return false;
        }
        return hasExistingVolume(blockObject.getWWN());
    }

    public boolean hasExistingVolume(String boWWN) {
        boolean hasVolume = false;
        if (_existingVolumes != null) {
            hasVolume = _existingVolumes.containsKey(BlockObject.normalizeWWN(boWWN));
        }
        return hasVolume;
    }

    public boolean hasUserAddedVolume(String boWWN) {
        boolean hasVolume = false;
        if (_userAddedVolumes != null) {
            hasVolume = _userAddedVolumes.containsKey(BlockObject.normalizeWWN(boWWN));
        }
        return hasVolume;
    }

    public boolean hasVolume(URI uri) {
        boolean hasVolume = false;
        if (_volumes != null) {
            hasVolume = _volumes.containsKey(uri.toString());
        }
        return hasVolume;
    }

    @Name("createdBySystem")
    public Boolean getCreatedBySystem() {
        return _createdBySystem;
    }

    public void setCreatedBySystem(Boolean createdBySystem) {
        _createdBySystem = createdBySystem;
        setChanged("createdBySystem");
    }

    @Name("zoningMap")
    public StringSetMap getZoningMap() {
        if (_zoningMap == null) {
            _zoningMap = new StringSetMap();
        }
        return _zoningMap;
    }

    public void setZoningMap(StringSetMap zoningMap) {
        this._zoningMap = zoningMap;
    }

    public void addZoningMap(StringSetMap zoningMapEntries) {
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
            StringSet set = _zoningMap.get(key);
            if (set != null && !set.isEmpty()) {
                StringSet values = new StringSet();
                values.addAll(set);
                for (String value : values) {
                    _zoningMap.remove(key, value);
                }
            }
        }
    }

    /**
     * Add an entry to create a zone between an initiator and port.
     * 
     * @param initiator URI as String
     * @param storagePort URI as String
     */
    public void addZoningMapEntry(String initiator, StringSet storagePorts) {
        if (this._zoningMap == null) {
            this._zoningMap = new StringSetMap();
        }
        this._zoningMap.put(initiator, storagePorts);
    }

    public void setResource(String name) {
        _resource = name;
        setChanged("resource");
    }

    @Name("resource")
    public String getResource() {
        return (_resource != null) ? _resource :
                NullColumnValueGetter.getNullURI().toString();
    }
   
    @Name("portGroup")
    @AlternateId("ExportMaskPortGroup")
    public URI getPortGroup() {
        return _portGroup;
    }

    public void setPortGroup(URI portGroup) {
        _portGroup = portGroup;
        setChanged("portGroup");
    }
    
    public boolean checkIfVolumeHLUSet(URI volumeURI) {
        boolean isHLUSet = false;
        String volumeURIStr = volumeURI.toString();
        if (_volumes != null && _volumes.containsKey(volumeURIStr)) {
            isHLUSet = _volumes.get(volumeURIStr).equals(ExportGroup.LUN_UNASSIGNED_STR);
        }
        return isHLUSet;
    }

    public boolean hasAnyInitiators() {
        if (_existingInitiators != null && !_existingInitiators.isEmpty()) {
            return true;
        }
        if (_userAddedInitiators != null && !_userAddedInitiators.isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * Convenience method to check if HLU for a volume is already in use.
     * 
     * @param hluValue HLU value
     * @return true if any volume has mentioned hluValue else false
     */
    public boolean anyVolumeHasHLU(String hluValue) {
        boolean hasHLU = false;
        StringMap existingVolumesInMask = getExistingVolumes();
        StringMap viprAddedVolumes = getVolumes();
        if ((existingVolumesInMask != null && existingVolumesInMask.containsValue(hluValue)) ||
                (viprAddedVolumes != null && viprAddedVolumes.containsValue(hluValue))) {
            hasHLU = true;
        }
        return hasHLU;
    }

    public int returnTotalVolumeCount() {
        int userVolumes = (_userAddedVolumes != null) ? _userAddedVolumes.size() : 0;
        int existingVolumes = (_existingVolumes != null) ? _existingVolumes.size() : 0;
        return userVolumes + existingVolumes;
    }

    /**
     * Returns the HLU for the specified Volume/BlockObject
     *
     * @param volumeURI [IN] - BlockObject URI for which to look up the HLU
     * @return String representing the volume HLU or ExportGroup.LUN_UNASSIGNED_DECIMAL_STR
     */
    public String returnVolumeHLU(URI volumeURI) {
        String hlu = ExportGroup.LUN_UNASSIGNED_DECIMAL_STR;
        if (_volumes != null) {
            String temp = _volumes.get(volumeURI.toString());
            hlu = (temp != null) ? temp : ExportGroup.LUN_UNASSIGNED_DECIMAL_STR;
        }
        return hlu;
    }

    @Override
    public String toString() {
        return String.format(
                "ExportMask %s (%s)\n" +
                        "\tInactive            : %s\n" +
                        "\tCreatedBySystem     : %s\n" +
                        "\tResource            : %s\n" +
                        "\tVolumes             : %s\n" +
                        "\tInitiators          : %s\n" +
                        "\tStoragePorts        : %s\n" +
                        "\tPortGroup           : %s\n" +
                        "\tUserAddedVolumes    : %s\n" +
                        "\tExistingVolumes     : %s\n" +
                        "\tUserAddedInitiators : %s\n" +
                        "\tExistingInitiators  : %s\n" +
                        "\tZoningMap           : %s\n",
                _maskName,
                _id,
                _inactive,
                _createdBySystem,
                getResource(),
                collectionString(_volumes),
                collectionString(_initiators),
                collectionString(_storagePorts),
                _portGroup,
                collectionString(_userAddedVolumes),
                collectionString(_existingVolumes),
                collectionString(_userAddedInitiators),
                collectionString(_existingInitiators),
                _zoningMap);
    }

    @Override
    public String forDisplay() {
        if (_maskName != null && !_maskName.isEmpty()) {
            return String.format("%s (%s)", _maskName, _id);
        } else {
            return super.forDisplay();
        }
    }
}
