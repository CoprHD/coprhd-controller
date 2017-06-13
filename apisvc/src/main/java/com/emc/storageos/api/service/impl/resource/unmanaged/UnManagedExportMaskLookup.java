package com.emc.storageos.api.service.impl.resource.unmanaged;

import java.net.URI;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;

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
                            Templates.NONE_FOUND;
                break;
            default:
                try {
                    value = super.lookup(key);
                } catch (Exception ex) {
                    _log.error("Exception getting key {}: {}", key, ex.getLocalizedMessage());
                    value = Templates.NONE_FOUND;
                }
        }

        _log.info("returning value {} for key {}", value, key);
        return value;
    }
}
