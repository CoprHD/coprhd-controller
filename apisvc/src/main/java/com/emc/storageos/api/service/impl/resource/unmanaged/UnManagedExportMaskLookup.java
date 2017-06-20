package com.emc.storageos.api.service.impl.resource.unmanaged;

import java.net.URI;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public class UnManagedExportMaskLookup extends UnManagedObjectLookup {

    private UnManagedExportMask _unmanagedExportMask = null;
    
    UnManagedExportMaskLookup(UnManagedExportMask dataObject, ReportInfoCache reportInfoCache, DbClient dbClient, CoordinatorClient coordinator) {
        super(dataObject, reportInfoCache, dbClient, coordinator);

        this._unmanagedExportMask = dataObject;
    }

    @Override
    public String lookup(String key) {

        String value = null;

        switch(key) {
            case "ingestionStatus":
                value = Templates.NOT_INGESTED;
                break;
            case "storageSystem":
                value = _reportInfoCache.getStorageSystemName(_unmanagedExportMask.getStorageSystemUri());
                break;
            case "knownInitiatorNetworkIds":
                value = renderSimpleStringSet(_unmanagedExportMask.getKnownInitiatorNetworkIds());
                break;
            case "knownInitiatorUris":
                StringBuffer knownInitiators = new StringBuffer();
                if (_unmanagedExportMask.getKnownInitiatorUris() != null && !_unmanagedExportMask.getKnownInitiatorUris().isEmpty()) {
                    Iterator<Initiator> inits = _dbClient.queryIterativeObjects(Initiator.class, URIUtil.toURIList(_unmanagedExportMask.getKnownInitiatorUris()));
                    while (inits.hasNext()) {
                        Initiator init = inits.next();
                        knownInitiators.append(String.format(Templates.TEMPLATE_LI, init.forDisplay()));
                    }
                }
                value = knownInitiators.length() > 0 ? 
                        String.format(Templates.TEMPLATE_UL, knownInitiators.toString()) :
                            Templates.EMPTY_STRING;
                break;
            case "knownStoragePortUris":
                StringBuffer knownStoragePorts = new StringBuffer();
                if (_unmanagedExportMask.getKnownStoragePortUris() != null && !_unmanagedExportMask.getKnownStoragePortUris().isEmpty()) {
                    Iterator<StoragePort> ports = _dbClient.queryIterativeObjects(StoragePort.class, URIUtil.toURIList(_unmanagedExportMask.getKnownStoragePortUris()));
                    while (ports.hasNext()) {
                        StoragePort port = ports.next();
                        knownStoragePorts.append(String.format(Templates.TEMPLATE_LI, port.forDisplay()));
                    }
                }
                value = knownStoragePorts.length() > 0 ? 
                        String.format(Templates.TEMPLATE_UL, knownStoragePorts.toString()) :
                            Templates.EMPTY_STRING;
                break;
            case "unmanagedInitiatorNetworkIds":
                value = renderSimpleStringSet(_unmanagedExportMask.getUnmanagedInitiatorNetworkIds());
                break;
            case "unmanagedStoragePortNetworkIds":
                value = renderSimpleStringSet(_unmanagedExportMask.getUnmanagedStoragePortNetworkIds());
                break;
            case "unmanagedVolumeUris":
                Set<String> sortable = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                if (_unmanagedExportMask.getUnmanagedVolumeUris() != null && !_unmanagedExportMask.getUnmanagedVolumeUris().isEmpty()) {
                    Iterator<UnManagedVolume> umvs = _dbClient.queryIterativeObjects(UnManagedVolume.class, URIUtil.toURIList(_unmanagedExportMask.getUnmanagedVolumeUris()));
                    while (umvs.hasNext()) {
                        UnManagedVolume umv = umvs.next();
                        String uri = String.format(Templates.URI_UMV, umv.getId());
                        sortable.add(String.format(Templates.TEMPLATE_LI_LINK_SORTABLE, umv.getLabel(), uri, umv.getLabel()));
                    }
                }
                value = sortable.size() > 0 ? 
                        String.format(Templates.TEMPLATE_UL, StringUtils.join(sortable, " ")) :
                            Templates.EMPTY_STRING;
                break;
            case "supportedVpoolUris":
                StringBuffer supportedVpools = new StringBuffer();
                if (_unmanagedExportMask.getSupportedVpoolUris() != null && !_unmanagedExportMask.getSupportedVpoolUris().isEmpty()) {
                    for (URI uri : URIUtil.toURIList(_unmanagedExportMask.getSupportedVpoolUris())) {
                        String vpoolName = _reportInfoCache.getVirtualPoolName(uri);
                        String url = String.format(Templates.URI_VPOOL, _applicationBaseUrl, uri);
                        supportedVpools.append(String.format(Templates.TEMPLATE_LI_LINK, url, vpoolName));
                    }
                }
                value = supportedVpools.length() > 0 ? 
                        String.format(Templates.TEMPLATE_UL, supportedVpools.toString()) :
                            Templates.EMPTY_STRING;
                break;
            case "zoningMap":
                value = renderSimpleStringSet(_unmanagedExportMask.getZoningMap().keySet());
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
