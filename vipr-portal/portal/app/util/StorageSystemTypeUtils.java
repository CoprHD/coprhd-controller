package util;

import static util.BourneUtil.getViprClient;

import java.util.List;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAdd;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.sun.jersey.api.client.ClientResponse;

public class StorageSystemTypeUtils {
	
    public static StorageSystemTypeList getAllStorageSystemTypes(String storageTypetype) {
        return getViprClient().storageSystemType().listStorageSystemTypeTypes(storageTypetype);
    }
    
    public static StorageSystemTypeRestRep getStorageSystemType(String id) {
        return getViprClient().storageSystemType().getStorageSystemTypeRestRep(id);
    }
    
    public static ClientResponse deleteStorageSystemType(String id) {
        return getViprClient().storageSystemType().deleteStorageSystemType(id);
    }
    
    public static StorageSystemTypeRestRep addStorageSystemType(StorageSystemTypeAdd addparam) {
        return getViprClient().storageSystemType().createStorageSystemType(addparam);
    }

}
