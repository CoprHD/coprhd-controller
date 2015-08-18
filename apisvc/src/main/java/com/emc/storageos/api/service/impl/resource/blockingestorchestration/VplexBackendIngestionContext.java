package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;

public class VplexBackendIngestionContext {

    private static Logger _logger = LoggerFactory.getLogger(VplexBackendIngestionContext.class);
    private DbClient _dbClient;
    private UnManagedVolume _unmanagedVirtualVolume;
    
    private List<UnManagedVolume> unmanagedBackendVolumes;
    private List<UnManagedVolume> unmanagedSnapshots;
    private Map<UnManagedVolume, UnManagedVolume> unmanagedFullClones;
    private Map<UnManagedVolume, UnManagedVolume> unmanagedBackendOnlyClones;
    private List<UnManagedVolume> unmanagedMirrors;
    
    Map<String, UnManagedVolume> processedUnManagedVolumeMap = new HashMap<String, UnManagedVolume>();
    Map<String, BlockObject> createdObjectMap = new HashMap<String, BlockObject>();
    Map<String, List<DataObject>> updatedObjectMap = new HashMap<String, List<DataObject>>();
    List<BlockObject> ingestedObjects = new ArrayList<BlockObject>();
    
    public VplexBackendIngestionContext(UnManagedVolume unManagedVolume, DbClient dbClient) {
        this._unmanagedVirtualVolume = unManagedVolume;
        this._dbClient = dbClient;
    }
    
    public UnManagedVolume getUnmanagedVirtualVolume() {
        return this._unmanagedVirtualVolume;
    }
    
    public List<UnManagedVolume> getAllUnmanagedVolumes() {
        List<UnManagedVolume> allVolumes = new ArrayList<UnManagedVolume>();
        allVolumes.addAll(getUnmanagedBackendVolumes());
        allVolumes.addAll(getUnmanagedSnapshots());
        allVolumes.addAll(getUnmanagedFullClones().keySet());
        allVolumes.addAll(getUnmanagedFullClones().values());
        allVolumes.addAll(getUnmanagedBackendOnlyClones().values());
        // allVolumes.addAll(getUnmanagedMirrors());  TODO disabled for QE drop1 build
        return allVolumes;
    }
    
    public List<UnManagedVolume> getUnmanagedBackendVolumes() {
        if (null != unmanagedBackendVolumes) {
            return unmanagedBackendVolumes;
        }
        
        unmanagedBackendVolumes = new ArrayList<UnManagedVolume>();
        List<URI> associatedVolumeUris = new ArrayList<URI>();
        String deviceName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_SUPPORTING_DEVICE_NAME.toString(),
                _unmanagedVirtualVolume.getVolumeInformation());
        
        String locality = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_LOCALITY.toString(),
                _unmanagedVirtualVolume.getVolumeInformation());
        
        Set<String> backendVolumeWwns = null;
        try {
            backendVolumeWwns = 
                    VPlexControllerUtils.getStorageVolumeInfoForDevice(
                            deviceName, locality, 
                            _unmanagedVirtualVolume.getStorageSystemUri(), _dbClient);
            
        } catch (VPlexApiException ex) {
            _logger.error("could not determine backend storage volumes for {}: ", _unmanagedVirtualVolume.getLabel(), ex);
        }
        
        if (null != backendVolumeWwns) {
            for (String backendWwn : backendVolumeWwns) {
                _logger.info("attempting to find unmanaged backend volume by wwn {}", 
                        backendWwn);
                
                URIQueryResultList results = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.
                        Factory.getUnmanagedVolumeWwnConstraint(
                                BlockObject.normalizeWWN(backendWwn)), results);
                if (results.iterator() != null) {
                    for (URI uri : results) {
                        associatedVolumeUris.add(uri);
                    }
                }
            }
        }
        

        unmanagedBackendVolumes = _dbClient.queryObject(UnManagedVolume.class, associatedVolumeUris);
        
        _logger.info("for VPLEX UnManagedVolume {} found these associated volumes: " + unmanagedBackendVolumes, _unmanagedVirtualVolume.getId());
        return unmanagedBackendVolumes;
    }
    
    public List<String> getBackendVolumeGuids() {

        List<String> associatedVolumeGuids = new ArrayList<String>();

        for (UnManagedVolume vol : this.getUnmanagedBackendVolumes()) {
            associatedVolumeGuids.add(vol.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                    VolumeIngestionUtil.VOLUME));
        }
        
        return associatedVolumeGuids;
    }

    public List<UnManagedVolume> getUnmanagedSnapshots() {
        if (null != unmanagedSnapshots) {
            return unmanagedSnapshots;
        }
        
        unmanagedSnapshots = new ArrayList<UnManagedVolume>();
        
        for (UnManagedVolume sourceVolume : this.getUnmanagedBackendVolumes()) {
            unmanagedSnapshots.addAll(VolumeIngestionUtil.getUnManagedSnaphots(sourceVolume, _dbClient));
        }
        
        _logger.info("found these associated snapshots: " + unmanagedSnapshots);
        return unmanagedSnapshots;
    }

    public Map<UnManagedVolume, UnManagedVolume> getUnmanagedFullClones() {
        if (null != unmanagedFullClones) {
            return unmanagedFullClones;
        }
        
        unmanagedFullClones = new HashMap<UnManagedVolume, UnManagedVolume>();
        
        List<UnManagedVolume> backendClonesFound = new ArrayList<UnManagedVolume>();
        for (UnManagedVolume sourceVolume : getUnmanagedBackendVolumes()) {
            backendClonesFound.addAll(VolumeIngestionUtil.getUnManagedClones(sourceVolume, _dbClient));
        }
        
        if (backendClonesFound.isEmpty()) {
            _logger.info("no clones found for source volumes: " + getUnmanagedBackendVolumes());
        } else {
            
            Map<String, URI> deviceToUnManagedVolumeMap = 
                    VolumeIngestionUtil.getVplexDeviceToUnManagedVolumeMap(_unmanagedVirtualVolume, _dbClient);

            
            for (UnManagedVolume backendClone : backendClonesFound) {
                String volumeNativeId = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.NATIVE_ID.toString(),
                        backendClone.getVolumeInformation());
                
                StorageSystem backendSystem = 
                        _dbClient.queryObject(StorageSystem.class, backendClone.getStorageSystemUri());
                
                String deviceName = VPlexControllerUtils.getDeviceForStorageVolume(
                        volumeNativeId, backendClone.getWwn(), backendSystem.getSerialNumber(), 
                        _unmanagedVirtualVolume.getStorageSystemUri(), _dbClient);
                
                if (null != deviceName) {
                    _logger.info("found device name {} for native id {}", deviceName, volumeNativeId);
                    URI umvUri = deviceToUnManagedVolumeMap.get(deviceName);
                    if (null != umvUri) {
                        UnManagedVolume virtualVolumeClone = _dbClient.queryObject(UnManagedVolume.class, umvUri);
                        if (null != virtualVolumeClone) {
                            _logger.info("adding mapping for vvol clone {} to backend clone {}", virtualVolumeClone, backendClone);
                            unmanagedFullClones.put(virtualVolumeClone, backendClone);
                        }
                    }
                } else {
                    _logger.info("could not determine supporting device name for native id " + volumeNativeId);
                }
            }
        }
        
        return unmanagedFullClones;
    }

    public Map<UnManagedVolume, UnManagedVolume> getUnmanagedBackendOnlyClones() {
        if (null != unmanagedBackendOnlyClones) {
            return unmanagedBackendOnlyClones;
        }
        
        // TODO: implement
        unmanagedBackendOnlyClones = new HashMap<UnManagedVolume, UnManagedVolume>(); 
        return unmanagedBackendOnlyClones;
    }

    public List<UnManagedVolume> getUnmanagedMirrors() {
        if (null != unmanagedMirrors) {
            return unmanagedMirrors;
        }
        
        unmanagedMirrors = new ArrayList<UnManagedVolume>();
        
        // check for mirrors
        boolean isSyncActive = false;
        Iterator<UnManagedVolume> it = this.getUnmanagedBackendVolumes().iterator();
        while (it.hasNext() && !isSyncActive) {
            if (VolumeIngestionUtil.isSyncActive(it.next())){
                isSyncActive = true;
            }
        }
        if (isSyncActive) {
            _logger.info("sync is active on backend volumes, generating mirror map...");
            Map<UnManagedVolume, UnManagedVolume> mirrorsMap = 
                    checkForMirrors(_unmanagedVirtualVolume, this.getUnmanagedBackendVolumes());
            if (null != mirrorsMap && !mirrorsMap.isEmpty()) {
                for (Entry<UnManagedVolume, UnManagedVolume> entry : mirrorsMap.entrySet()) {
                    UnManagedVolume mirrorVolume = entry.getValue();
                    _logger.info("removing mirror volume {} from associated "
                            + "vols and adding to mirrors", mirrorVolume.getLabel());
                    this.getUnmanagedBackendVolumes().remove(mirrorVolume);
                    unmanagedMirrors.add(mirrorVolume);
                }
            }
        }
        
        // TODO need to account for mirrors on both sides of distributed
        if (!unmanagedMirrors.isEmpty()) {
            StringSet set = new StringSet();
            for (UnManagedVolume mirror : unmanagedMirrors) {
                set.add(mirror.getNativeGuid());
            }
            _logger.info("mirror set is " + set);
            for (UnManagedVolume vol : this.getUnmanagedBackendVolumes()) {
                vol.getVolumeInformation().put(SupportedVolumeInformation.MIRRORS.toString(), set);
            }
        }
        
        return unmanagedMirrors;
    }

    private Map<UnManagedVolume, UnManagedVolume> checkForMirrors(UnManagedVolume unManagedVolume, 
            List<UnManagedVolume> associatedVolumes) {
        _logger.info("checking for mirrors on volume " + unManagedVolume.getNativeGuid());
        Map<UnManagedVolume, UnManagedVolume> mirrorMap = new HashMap<UnManagedVolume, UnManagedVolume>();
        
        Map<String, String> associatedVolumesInfo = new HashMap<String, String>();
        for (UnManagedVolume vol : associatedVolumes) {
            associatedVolumesInfo.put(vol.getNativeGuid(), vol.getWwn());
        }
        
        String deviceName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_SUPPORTING_DEVICE_NAME.toString(),
                    unManagedVolume.getVolumeInformation());
        
        String locality = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_LOCALITY.toString(),
                    unManagedVolume.getVolumeInformation());

        // map of the components of a top level device: device slot-# to device wwn
        // the assumption is that the source device will be in slot-0 and any mirrors above that
        Map<String, String> topLevelDeviceMap = VPlexControllerUtils.getTopLevelDeviceMap(deviceName, locality, 
                unManagedVolume.getStorageSystemUri(), _dbClient);
        
        if (null != topLevelDeviceMap && !topLevelDeviceMap.isEmpty()) {
            Map<String, UnManagedVolume> wwnToVolMap = new HashMap<String, UnManagedVolume>();
            for (UnManagedVolume vol : associatedVolumes) {
                wwnToVolMap.put(vol.getWwn(), vol);
            }
            
            UnManagedVolume slot0Vol= null;
            for (Entry<String, String> entry : topLevelDeviceMap.entrySet()) {
                if (null == slot0Vol) {
                    slot0Vol = wwnToVolMap.get(BlockObject.normalizeWWN(entry.getValue()));
                } else {
                    mirrorMap.put(slot0Vol, 
                        wwnToVolMap.get(BlockObject.normalizeWWN(entry.getValue())));
                }
            }
        }
        
        _logger.info("generated mirror map: " + mirrorMap);
        return mirrorMap;
    }

    /**
     * @return the processedUnManagedVolumeMap
     */
    public Map<String, UnManagedVolume> getProcessedUnManagedVolumeMap() {
        return processedUnManagedVolumeMap;
    }

    /**
     * @return the createdObjectMap
     */
    public Map<String, BlockObject> getCreatedObjectMap() {
        return createdObjectMap;
    }

    /**
     * @return the updatedObjectMap
     */
    public Map<String, List<DataObject>> getUpdatedObjectMap() {
        return updatedObjectMap;
    }

    /**
     * @return the ingestedObjects
     */
    public List<BlockObject> getIngestedObjects() {
        return ingestedObjects;
    }
    
}
