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
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBResource;

public interface CustomServicesPrimitiveManager {

    public void save(final CustomServicesDBPrimitive primitive);

    public void save(final CustomServicesDBResource resource);

    public <T extends CustomServicesDBPrimitive> void deactivate(final Class<T> clazz, final URI id);

    public <T extends CustomServicesDBPrimitive> T findById(final Class<T> clazz, final URI id);

    public <T extends CustomServicesDBResource> T findResource(final Class<T> clazz, final URI id);

    public <T extends CustomServicesDBResource> List<NamedElement> getResources(
            Class<T> type);

    public <T extends ModelObject> List<T> getByLabel(final Class<T> clazz, final String label);
}
