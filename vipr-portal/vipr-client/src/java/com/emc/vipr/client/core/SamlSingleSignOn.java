/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.client.core;

import com.emc.storageos.model.auth.SamlMetadata;
import com.emc.storageos.model.auth.SamlMetadataResponse;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.system.impl.PathConstants;
import com.sun.jersey.api.client.ClientResponse;

import javax.ws.rs.core.Response;

public class SamlSingleSignOn {
    private RestClient client;

    public SamlSingleSignOn(RestClient client) {
        this.client = client;
    }

    public SamlMetadata getSamlMetadataObject() {
        return client.get(SamlMetadata.class, PathConstants.SAML_METADATA_OBJECT_URL);
    }

    public ClientResponse getSamlMetadataXML() {
        return client.get(ClientResponse.class, PathConstants.SAML_METADATA_URL);
    }

    public SamlMetadata createSamlMetadata(SamlMetadata metadata) {
        return client.post(SamlMetadata.class, metadata, PathConstants.SAML_METADATA_URL);
    }

    public ClientResponse deleteSamlMetadata() {
        return client.post(ClientResponse.class, PathConstants.SAML_METADATA_DELETE_URL);
    }
}
