package com.emc.vipr.client.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.file.FileDataBodyPart;

public class StorageDriver {

    protected final RestClient client;

    public StorageDriver(RestClient client) {
        this.client = client;
    }

    public void install(StorageSystemTypeAddParam input) {
        client.post(StorageSystemTypeRestRep.class, input, PathConstants.STORAGE_DRIVER_INSTALL_URL);
    }

    public StorageSystemTypeAddParam upload(File f, String name) throws FileNotFoundException {
        String path = String.format(PathConstants.STORAGE_DRIVER_STORE_AND_PARSE_URL, name);
        final FormDataMultiPart multiPart = new FormDataMultiPart();
        multiPart.bodyPart(new FileDataBodyPart("driver", f, MediaType.APPLICATION_OCTET_STREAM_TYPE));
        return client.postMultiPart(StorageSystemTypeAddParam.class, multiPart, path);
    }
}
