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

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CustomServicesAnsiblePrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitiveResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesUserPrimitive;

public interface CustomServicesPrimitiveManager {

    public void save(final CustomServicesUserPrimitive primitive);
    public void save(final CustomServicesPrimitiveResource resource);
    public void deactivate(final URI id);
    
    public CustomServicesUserPrimitive findById(final URI id);
    public CustomServicesPrimitiveResource findResource(final URI id);
    public List<URI> findAllAnsibleIds();
    public List<CustomServicesAnsiblePrimitive> findAllAnsible();
    public List<URI> findAllScriptPrimitiveIds();
    public List<CustomServicesScriptPrimitive> findAllScriptPrimitives();
    public <T extends CustomServicesPrimitiveResource> List<NamedElement> getResources(
            Class<T> type);
}
