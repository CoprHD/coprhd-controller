/*
 * Copyright 2016 Intel Corporation
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
package com.emc.storageos.security.authentication;

import com.emc.storageos.model.project.ProjectElement;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.security.helpers.BaseServiceClient;
import com.sun.jersey.api.client.WebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class InternalTenantSvcClient extends BaseServiceClient{

    private static final String INTERNAL_TENANT_ROOT = "/internal/tenants";
    private static final String INTERNAL_CREATE_TENANT = INTERNAL_TENANT_ROOT;
    private static final String INTERNAL_CREATE_PROJECT = INTERNAL_TENANT_ROOT + "/%s/projects";

    final private Logger _log = LoggerFactory.getLogger(InternalTenantSvcClient.class);

    public InternalTenantSvcClient() {
    }

    public InternalTenantSvcClient(String server) {
        setServer(server);
    }

    @Override
    public void setServer(String server) {
        setServiceURI(URI.create("https://" + server + ":8443"));
    }


    public TenantOrgRestRep createTenant(TenantCreateParam param) {

        WebResource rRoot = createRequest(INTERNAL_CREATE_TENANT);
        TenantOrgRestRep resp = null;
        try {
            resp = addSignature(rRoot)
                    .post(TenantOrgRestRep.class, param);
        } catch (Exception e) {
            _log.error("Could not create tenant. Err:{}", e.getStackTrace());
        }
        return resp;
    }

    public ProjectElement createProject(URI id, ProjectParam param) {

        String path = String.format(INTERNAL_CREATE_PROJECT, id.toString());
        WebResource rRoot = createRequest(path);
        ProjectElement resp = null;
        try {
            resp = addSignature(rRoot)
                    .post(ProjectElement.class, param);
        } catch (Exception e) {
            _log.error("Could not create project. Err:{}", e.getStackTrace());
        }
        return resp;
    }

}
