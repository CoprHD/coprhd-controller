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
package com.emc.sa.catalog.primitives;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;

public interface CustomServicesPrimitiveDAO<Primitive extends CustomServicesPrimitiveType,
Resource extends CustomServicesPrimitiveResourceType> {
    public String getType();
    public Primitive get(final URI id);
    public Primitive create(final CustomServicesPrimitiveCreateParam param );
    public Primitive update(final URI id, final CustomServicesPrimitiveUpdateParam param);
    public void deactivate(final URI id);
    public List<URI> list();
    public String getPrimitiveModel();  
    public Iterator<CustomServicesPrimitiveRestRep> bulk(final Collection<URI> ids);
    public Resource getResource(final URI id);
    public Resource createResource(final String name, byte[] stream);
    public Resource updateResource(URI id, String name, byte[] stream);
    public void deactivateResource(final URI id);
    public List<NamedElement> listResources();
    public Class<Resource> getResourceType();
    public boolean hasResource();
}
