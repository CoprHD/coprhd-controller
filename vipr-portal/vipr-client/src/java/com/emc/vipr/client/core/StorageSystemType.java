package com.emc.vipr.client.core;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAdd;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;


public class StorageSystemType {
	
private RestClient client;
    
    public StorageSystemType(RestClient client) {
        this.client = client;
    }	

    public StorageSystemTypeRestRep createStorageSystemType(StorageSystemTypeAdd input) {
        return client.post(StorageSystemTypeRestRep.class, input, PathConstants.STORAGE_SYSTEM_TYPE_URL);
    }

    public ClientResponse deleteStorageSystemType(String id) {
        return client.post(ClientResponse.class, PathConstants.STORAGE_SYSTEM_TYPE_URL + id + "/deactivate");
    }
    
    public StorageSystemTypeRestRep getStorageSystemTypeRestRep(String uuid) {
        return client.get(StorageSystemTypeRestRep.class, PathConstants.STORAGE_SYSTEM_TYPE_URL + "/" + uuid);
    }
    
    public StorageSystemTypeList listStorageSystemTypeTypes(String storageTypeType) {
        return client.get(StorageSystemTypeList.class, PathConstants.STORAGE_SYSTEM_URL + "/type/" +storageTypeType);
    }
    
}
