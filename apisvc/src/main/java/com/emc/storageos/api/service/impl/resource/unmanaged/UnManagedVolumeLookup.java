package com.emc.storageos.api.service.impl.resource.unmanaged;

import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;

public class UnManagedVolumeLookup extends UnManagedObjectLookup {

    protected UnManagedVolume _unManagedVolume = null;

    UnManagedVolumeLookup(UnManagedVolume unManagedVolume, ReportInfoCache reportInfoCache, 
            DbClient dbClient, CoordinatorClient coordinator) {
        super(unManagedVolume, reportInfoCache, dbClient, coordinator);
        this._unManagedVolume = unManagedVolume;
    }

    @Override
    public String lookup(String key) {

        String value = null;

        switch (key) {
            case "ingestionStatus":
                String volumeNativeGuid = _unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                        VolumeIngestionUtil.VOLUME);
                Volume volume = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);
                if (volume != null) {
                    String uri = String.format(Templates.URI_VOL, volume.getId());
                    value = String.format(Templates.PARTIALLY_INGESTED, uri);
                } else {
                    value = Templates.NOT_INGESTED;
                }
                break;
            case "storageDevice":
                value = _reportInfoCache.getStorageSystemName(_unManagedVolume.getStorageSystemUri());
                break;
            case "storagePool":
                if (_unManagedVolume.getStoragePoolUri() != null) {
                    value = _reportInfoCache.getStoragePoolName(_unManagedVolume.getStoragePoolUri());
                } else {
                    value = Templates.EMPTY_STRING;
                }
                break;
            case "consistencyGroup":
                UnManagedConsistencyGroup umcg = VolumeIngestionUtil.getUnManagedConsistencyGroup(_unManagedVolume, _dbClient);
                if (umcg != null) {
                    value = umcg.getName();
                } else {
                    value = Templates.EMPTY_STRING;
                }
                break;
            case "vplexRelationships":
                String label = "";
                Set<String> vplexRelationships = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                if (VolumeIngestionUtil.isVplexVolume(_unManagedVolume)) {
                    label = "VPLEX Backend Volumes: ";
                    // TODO probably make more efficient/cache vols
                    List<UnManagedVolume> uvols = VplexBackendIngestionContext.findBackendUnManagedVolumes(_unManagedVolume, _dbClient);
                    if (uvols != null && !uvols.isEmpty()) {
                        if (VolumeIngestionUtil.isVplexDistributedVolume(_unManagedVolume) && uvols.size() == 1) {
                            vplexRelationships.add(String.format(Templates.TEMPLATE_LI, "WARNING: not all backend volumes found - have both backend arrays been discovered?"));
                        }
                        for (UnManagedVolume uvol : uvols) {
                            String uri = String.format(Templates.URI_UMV, uvol.getId());
                            vplexRelationships.add(String.format(Templates.TEMPLATE_LI_LINK, uri, uvol.getLabel()));
                        }
                    } else {
                        vplexRelationships.add(String.format(Templates.TEMPLATE_LI, "WARNING: no backend volumes found - have backend arrays been discovered?"));
                    }
                } else if (VolumeIngestionUtil.isVplexBackendVolume(_unManagedVolume)) {
                    UnManagedVolume parentVolume = VolumeIngestionUtil.findVplexParentVolume(_unManagedVolume, _dbClient, null);
                    if (parentVolume != null) {
                        label = "VPLEX Parent Volume:";
                        String uri = String.format(Templates.URI_UMV, parentVolume.getId());
                        vplexRelationships.add(String.format(Templates.TEMPLATE_LI_LINK, uri, parentVolume.getLabel()));
                    }
                }

                if (!vplexRelationships.isEmpty()) {
                    value = String.format(Templates.TEMPLATE_TR, label,
                            String.format(Templates.TEMPLATE_UL, StringUtils.join(vplexRelationships, " ")));
                } else {
                    value = Templates.EMPTY_STRING;
                }
                break;
            case "supportedVpoolUris":
                StringBuffer supportedVpools = new StringBuffer();
                if (_unManagedVolume.getSupportedVpoolUris() != null && !_unManagedVolume.getSupportedVpoolUris().isEmpty()) {
                    for (URI uri : URIUtil.toURIList(_unManagedVolume.getSupportedVpoolUris())) {
                        String vpoolName = _reportInfoCache.getVirtualPoolName(uri);
                        String url = String.format(Templates.URI_VPOOL, _applicationBaseUrl, uri);
                        supportedVpools.append(String.format(Templates.TEMPLATE_LI_LINK, url, vpoolName));
                    }
                }
                value = supportedVpools.length() > 0 ? String.format(Templates.TEMPLATE_UL, supportedVpools.toString())
                        : Templates.EMPTY_STRING;
                break;
            case "unmanagedExportMasks":
                StringBuffer unmanagedExportMasks = new StringBuffer();
                if (_unManagedVolume.getUnmanagedExportMasks() != null && !_unManagedVolume.getUnmanagedExportMasks().isEmpty()) {
                    for (URI uri : URIUtil.toURIList(_unManagedVolume.getUnmanagedExportMasks())) {
                        String uemName = _reportInfoCache.getUnManagedExportMaskName(uri);
                        String url = String.format(Templates.URI_UEM, uri);
                        unmanagedExportMasks.append(String.format(Templates.TEMPLATE_LI_LINK, url, uemName));
                    }
                }
                value = String.format(Templates.TEMPLATE_UL, unmanagedExportMasks.toString());
                break;
            case "managedExportMasks":
                value = Templates.EMPTY_STRING;
                break;
            case "unmanagedSnapshots":
                Set<String> snaps = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                if (VolumeIngestionUtil.checkUnManagedVolumeHasReplicas(_unManagedVolume)) {
                    // TODO probably make more efficient/cache snap vols
                    for (UnManagedVolume uvol : VolumeIngestionUtil.getUnManagedSnaphots(_unManagedVolume, _dbClient)) {
                        String uri = String.format(Templates.URI_UMV, uvol.getId());
                        snaps.add(String.format(Templates.TEMPLATE_LI_LINK, uri, uvol.getLabel()));
                    }
                    value = String.format(Templates.TEMPLATE_UL, StringUtils.join(snaps, " "));
                } else {
                    value = Templates.EMPTY_STRING;
                }
                break;
            case "unmanagedClones":
                Set<String> clones = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                if (VolumeIngestionUtil.checkUnManagedVolumeHasReplicas(_unManagedVolume)) {
                    // TODO probably make more efficient/cache clones vols
                    for (UnManagedVolume uvol : VolumeIngestionUtil.getUnManagedClones(_unManagedVolume, _dbClient)) {
                        String uri = String.format(Templates.URI_UMV, uvol.getId());
                        clones.add(String.format(Templates.TEMPLATE_LI_LINK, uri, uvol.getLabel()));
                    }
                    value = String.format(Templates.TEMPLATE_UL, StringUtils.join(clones, " "));
                } else {
                    value = Templates.EMPTY_STRING;
                }
                break;
            case "managedReplicas":
                value = Templates.EMPTY_STRING;
                break;
            case "volumeCharacterstics":
                Set<String> volumeCharSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                for (Entry<String, String> entry : _unManagedVolume.getVolumeCharacterstics().entrySet()) {
                    String item = entry.getKey() + "=" + entry.getValue();
                    volumeCharSet.add(String.format(Templates.TEMPLATE_LI, item));
                }
                value = String.format(Templates.TEMPLATE_UL, StringUtils.join(volumeCharSet, " "));
                break;
            case "volumeInformation":
                Set<String> volumeInfoSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                for (Entry<String, AbstractChangeTrackingSet<String>> entry : _unManagedVolume.getVolumeInformation().entrySet()) {
                    String item = entry.getKey() + "=" + entry.getValue();
                    volumeInfoSet.add(String.format(Templates.TEMPLATE_LI, item));
                }
                value = String.format(Templates.TEMPLATE_UL, StringUtils.join(volumeInfoSet, " "));
                break;
            default:
                try {
                    value = super.lookup(key);
                } catch (Exception ex) {
                    _log.error("Exception getting key {}: {}", key, ex.getLocalizedMessage());
                    value = Templates.EMPTY_STRING;
                }
        }

        _log.info("returning value {} for key {}", value, key);
        return value;
    }

    
}
