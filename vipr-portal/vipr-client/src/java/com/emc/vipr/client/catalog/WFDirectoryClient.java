/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.vipr.client.catalog;

import com.emc.storageos.model.BulkIdParam;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.emc.vipr.model.catalog.WFDirectoryUpdateParam;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;

/**
 * WF Directory REST client util.
 */
public class WFDirectoryClient {

    protected final ViPRCatalogClient2 parent;
    protected final RestClient client;

    public WFDirectoryClient(ViPRCatalogClient2 parent, RestClient client) {
        this.parent = parent;
        this.client = client;
    }

    public WFBulkRep getAll() {
        UriBuilder builder = client.uriBuilder(PathConstants.WF_DIRECTORY_BULK);
        BulkIdParam bulkIdParam = client.getURI(BulkIdParam.class, builder.build());
        return client.postURI(WFBulkRep.class, bulkIdParam, builder.build());
    }

    public WFDirectoryRestRep edit(URI id, WFDirectoryUpdateParam param) {
        return client.put(WFDirectoryRestRep.class,param,PathConstants.WF_DIRECTORY,id);
    }

    public void delete(URI id) {
        client.post(String.class,PathConstants.WF_DIRECTORY_DELETE,id);
    }

    public WFDirectoryRestRep create(WFDirectoryParam param) {
        UriBuilder builder = client.uriBuilder(PathConstants.WF_DIRECTORIES);
        return client.postURI(WFDirectoryRestRep.class,param, builder.build());
    }
}
