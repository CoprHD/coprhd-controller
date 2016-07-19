/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package util;

import static util.BourneUtil.getViprClient;

import java.io.File;
import java.io.InputStream;

import javax.ws.rs.core.Response;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.multipart.MultiPart;

public class StorageSystemTypeUtils {

    public static final String ALL_TYPE = "all";
    public static final String BLOCK_TYPE = "block";
    public static final String FILE_TYPE = "file";
    public static final String OBJECT_TYPE = "object";
    public static final String BLOCK_AND_FILE_TYPE = "block_and_file";

    private StorageSystemTypeUtils() {
    }

	public static StorageSystemTypeList getAllStorageSystemTypes(String metaType) {
		return getViprClient().storageSystemType().listStorageSystemTypes(metaType);
	}

	public static StorageSystemTypeRestRep getStorageSystemType(String id) {
		return getViprClient().storageSystemType().getStorageSystemTypeRestRep(id);
	}

	public static ClientResponse deleteStorageSystemType(String id) {
		return getViprClient().storageSystemType().deleteStorageSystemType(id);
	}

	public static StorageSystemTypeRestRep addStorageSystemType(StorageSystemTypeAddParam addparam) {
		return getViprClient().storageSystemType().createStorageSystemType(addparam);
	}

	// Keeping commented code for future use.
	//public static ClientResponse uploadDriver(MultiPart deviceDriverFile) {
	//	return getViprClient().storageSystemType().uploadDeviceDriver(deviceDriverFile);
	//}
}
