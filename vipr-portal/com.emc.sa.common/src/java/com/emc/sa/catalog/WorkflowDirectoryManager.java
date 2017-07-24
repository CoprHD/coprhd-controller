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

import com.emc.storageos.db.client.model.uimodels.WFDirectory;

import java.net.URI;
import java.util.List;

public interface WorkflowDirectoryManager {

    public List<WFDirectory> getWFDirectories();

    public void createWFDirectory(WFDirectory wfDirectory);

    public WFDirectory getWFDirectoryById(URI id);

    public void deactivateWFDirectory(URI id);

    public void updateWFDirectory(WFDirectory wfDirectory);

    public List<WFDirectory> getWFDirectories(List<URI> ids);

    public List<WFDirectory> getWFDirectoryChildren(URI parentID);
}
