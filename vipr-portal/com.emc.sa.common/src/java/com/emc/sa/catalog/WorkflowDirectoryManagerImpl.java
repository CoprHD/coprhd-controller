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

import java.net.URI;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

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
        // validate that the children are empty before deleting
        checkChildren(id);

        // delete empty children
        deleteDirectoryAndChildren(id);
    }

    private void checkChildren(URI id) {
        WFDirectory wfDirectory = client.wfDirectory().findById(id);
        if (null == wfDirectory) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }

        // Disallow operation if this node has children that contain workflows/ primitives
        List<WFDirectory> children = getWFDirectoryChildren(id);
        if (CollectionUtils.isNotEmpty(wfDirectory.getWorkflows())) {
            throw APIException.methodNotAllowed.notSupportedWithReason("Directory has workflows/primitives. Cannot be deleted");
        }
        for (final WFDirectory child : children) {
            if (CollectionUtils.isNotEmpty(child.getWorkflows())) {
                throw APIException.methodNotAllowed
                        .notSupportedWithReason("Directory has children that contain workflows/primitives. Cannot be deleted");
            }
            // check the children
            checkChildren(child.getId());
        }
    }

    private boolean deleteDirectoryAndChildren(URI id) {
        WFDirectory wfDirectory = client.wfDirectory().findById(id);
        if (null == wfDirectory) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }

        List<WFDirectory> children = getWFDirectoryChildren(id);
        // start deleting from inner nodes
        if (CollectionUtils.isEmpty(children)) {
            //delete only if the wfDirectory does not have any children
            client.delete(wfDirectory);
            return true;
        } else {
            for (final WFDirectory child : children) {
                deleteDirectoryAndChildren(child.getId());
            }
            //all children deleted. delete the empty folder
            client.delete(wfDirectory);
        }
        return true;
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
