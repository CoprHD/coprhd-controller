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
import java.net.URISyntaxException;
import java.util.List;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.impl.response.ResourceTypeMapping;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitiveResourceModel;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.primitives.CustomServicesPrimitive;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.google.common.collect.ImmutableList;

public final class  CustomServicesPrimitiveMapper extends DbObjectMapper {
    public final static CustomServicesPrimitiveMapper instance = new CustomServicesPrimitiveMapper();

    private CustomServicesPrimitiveMapper() {
    };

    public static CustomServicesPrimitiveMapper getInstance() {
        return instance;
    }
    
    public static CustomServicesPrimitiveResourceRestRep map(final CustomServicesPrimitiveResourceType from ) {
        final CustomServicesPrimitiveResourceRestRep to = new CustomServicesPrimitiveResourceRestRep();
        mapDataObjectFields(from.asModelObject(), to);
        return to;
    }

    public static CustomServicesPrimitiveRestRep map(CustomServicesPrimitiveType from) {
        final CustomServicesPrimitiveRestRep to = new CustomServicesPrimitiveRestRep();

        mapDataObjectFields(from.asModelObject(), to);
        mapPrimitiveFields(from, to);

        try {
            to.setResource(makeResourceLink(from));
        } catch (final URISyntaxException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Making prmitive resource link failed", e);
        }
        return to;
    }

    private static NamedRelatedResourceRep makeResourceLink(CustomServicesPrimitiveType primitiveType) throws URISyntaxException {
        if(null == primitiveType.resource()) {
            return null;
        }
        
        final ResourceTypeEnum type = ResourceTypeMapping.getResourceType(CustomServicesPrimitiveResourceModel.class);

        return makeResourceLink(type.getService(), primitiveType.type(), primitiveType.resource());
    }


    private static NamedRelatedResourceRep makeResourceLink(final String service, final String type, final NamedURI id) throws URISyntaxException {
        StringBuilder builder = new StringBuilder(service)
                .append(type).append("/").append(id.getURI());
        return new NamedRelatedResourceRep(id.getURI(), new RestLinkRep("resource", new URI(builder.toString())), id.getName());
    }
    


    public static void mapPrimitiveFields(final CustomServicesPrimitive from,
            CustomServicesPrimitiveRestRep to) {
        to.setFriendlyName(from.friendlyName());
        to.setDescription(from.description());
        to.setSuccessCriteria(from.successCriteria());
    }


    public static <T extends CustomServicesDBResource> CustomServicesPrimitiveResourceList toCustomServicesPrimitiveResourceList(
            final String type, final List<NamedElement> fromList) {
        final ImmutableList.Builder<NamedRelatedResourceRep> builder = ImmutableList.builder();
        for( final NamedElement resource : fromList) {
            builder.add(toNamedRelatedResource(ResourceTypeMapping.getResourceType(CustomServicesDBResource.class), resource.getId(), resource.getName()));
        }
        return new CustomServicesPrimitiveResourceList(builder.build());
    }
}
