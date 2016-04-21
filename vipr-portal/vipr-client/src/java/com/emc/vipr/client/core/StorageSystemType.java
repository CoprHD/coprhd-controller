package com.emc.vipr.client.core;

import java.io.File;
import java.io.InputStream;

import javax.ws.rs.core.Response;
import com.sun.jersey.multipart.MultiPart;
import com.sun.jersey.multipart.file.StreamDataBodyPart;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
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

	public StorageSystemTypeRestRep createStorageSystemType(StorageSystemTypeAddParam input) {
		return client.post(StorageSystemTypeRestRep.class, input, PathConstants.STORAGE_SYSTEM_TYPE_URL);
	}

	public ClientResponse deleteStorageSystemType(String id) {
		return client.post(ClientResponse.class, PathConstants.STORAGE_SYSTEM_TYPE_URL + "/" + id + "/deactivate");
	}

	public StorageSystemTypeRestRep getStorageSystemTypeRestRep(String uuid) {
		return client.get(StorageSystemTypeRestRep.class, PathConstants.STORAGE_SYSTEM_TYPE_URL + "/" + uuid);
	}

	public StorageSystemTypeList listStorageSystemTypeTypes(String type) {
		return client.get(StorageSystemTypeList.class,
				PathConstants.STORAGE_SYSTEM_TYPE_URL + "/type/" + type);
	}

	public ClientResponse uploadDeviceDriver(MultiPart fileInputStream) {
		return client.postMultiPart(ClientResponse.class, fileInputStream, PathConstants.STORAGE_SYSTEM_TYPE_URL + "/upload");
	}
}
