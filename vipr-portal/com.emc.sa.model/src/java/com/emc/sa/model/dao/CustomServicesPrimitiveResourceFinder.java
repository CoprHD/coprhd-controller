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
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBResource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class CustomServicesPrimitiveResourceFinder extends
        ModelFinder<CustomServicesDBResource> {

    public CustomServicesPrimitiveResourceFinder(final DBClientWrapper client) {
        super(CustomServicesDBResource.class, client);
    }

    public <T extends CustomServicesDBResource> List<NamedElement> list(Class<T> type) {
        return prepareNamedElementFromURI(type, client.findAllIds(type));
    }

    public <T extends CustomServicesDBResource> List<NamedElement> listAllResourceByRefId(final Class<T> type, final String columnName,
            final String referenceId) {

        final List<URI> out = Lists.newArrayList();
        if (null != referenceId) {
            final List<NamedElement> results = client.findByAlternateId(type, columnName, referenceId);
            if (results != null) {
                for (final NamedElement namedElement : results) {
                    out.add(namedElement.getId());
                }
            }
        }
        return prepareNamedElementFromURI(type, out);
    }

    private <T extends CustomServicesDBResource> List<NamedElement> prepareNamedElementFromURI(final Class<T> type, final List<URI> ids) {

        final Iterator<T> it = client.findAllFields(type, ids, ImmutableList.<String> builder().add("label").build());
        final List<NamedElement> results = new ArrayList<NamedElement>();

        while (it.hasNext()) {
            final T element = it.next();
            results.add(NamedElement.createElement(element.getId(), element.getLabel()));
        }

        return results;
    }
}
