/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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

import com.emc.storageos.db.client.model.uimodels.Ansible;
import com.emc.storageos.db.client.model.uimodels.CustomServiceScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.PrimitiveResource;
import com.emc.storageos.db.client.model.uimodels.UserPrimitive;

public interface PrimitiveManager {

    public void save(final UserPrimitive primitive);
    public void save(final PrimitiveResource resource);
    public void deactivate(final URI id);
    
    public UserPrimitive findById(final URI id);
    public PrimitiveResource findResource(final URI id);
    public List<URI> findAllAnsibleIds();
    public List<Ansible> findAllAnsible();
    public List<URI> findAllScriptPrimitiveIds();
    public List<CustomServiceScriptPrimitive> findAllScriptPrimitives();
}
