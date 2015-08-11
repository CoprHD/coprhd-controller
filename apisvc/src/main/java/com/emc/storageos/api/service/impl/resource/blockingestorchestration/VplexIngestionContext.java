package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.BidiMap;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.vplex.api.VPlexApiClient;

public class VplexIngestionContext {

    private URI vplexUri;
    private DbClient dbClient; 
    private VPlexApiClient apiClient; 

    private BidiMap storageVolumeToWwnMap;
    private Map<String, String> storageVolumeToSupportingDeviceMap;
    private Map<String, URI> supportingDeviceToUnManagedVolumeUriMap;
    private Map<String, List<String>> deviceToMirrorMap;
    
    public VplexIngestionContext( URI vplexUri, DbClient dbClient, VPlexApiClient apiClient ) {
        this.vplexUri = vplexUri;
        this.dbClient = dbClient;
        this.apiClient = apiClient;
    }
    
    
}
