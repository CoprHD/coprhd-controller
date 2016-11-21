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
package com.emc.sa.catalog;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
public class WorkflowDirectoryManagerImpl implements WorkflowDirectoryManager {

    @Autowired
    private ModelClient client;

    public List<WFDirectory> getWFDirectories() {
        return client.wfDirectory().findAll();
    }

    public void createWFDirectory(WFDirectory wfDirectory) {
        client.save(wfDirectory);
    }

    public WFDirectory getWFDirectoryById(URI id) {
        WFDirectory wfDirectory = client.wfDirectory().findById(id);
        if (null == wfDirectory) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        return wfDirectory;
    }

    public void deactivateWFDirectory(URI id) {
        WFDirectory wfDirectory = client.wfDirectory().findById(id);
        if (null == wfDirectory) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }

        // Disallow operation if this node has children
        List<WFDirectory> children = getWFDirectoryChildren(id);
        if ((null != children && children.size() > 0) || (null != wfDirectory.getWorkflows() && wfDirectory.getWorkflows().size() > 0)) {
            throw APIException.methodNotAllowed.notSupportedWithReason("Directory has children. Cannot be deleted");
        }

        client.delete(wfDirectory);
    }

    public void updateWFDirectory(WFDirectory wfDirectory) {
        client.save(wfDirectory);
    }

    public List<WFDirectory> getWFDirectories(List<URI> ids) {
        List<WFDirectory> wfDirectories = client.wfDirectory().findByIds(ids);
        if (null == wfDirectories) {
            throw APIException.notFound.unableToFindEntityInURL(null);
        }
        return wfDirectories;
    }

    public List<WFDirectory> getWFDirectoryChildren(URI parentID) {
        return client.wfDirectory().getChildren(parentID);
    }
}
