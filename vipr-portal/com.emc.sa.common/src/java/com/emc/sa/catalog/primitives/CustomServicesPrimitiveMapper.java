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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.impl.response.ResourceTypeMapping;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitiveResourceModel;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep.InputGroup;
import com.emc.storageos.model.customservices.CustomServicesValidationResponse;
import com.emc.storageos.model.customservices.InputParameterRestRep;
import com.emc.storageos.model.customservices.OutputParameterRestRep;
import com.emc.storageos.primitives.CustomServicesPrimitive;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Class that can map primitive java objects to the REST response entity
 *
 */
public final class CustomServicesPrimitiveMapper extends DbObjectMapper {
    public final static CustomServicesPrimitiveMapper instance = new CustomServicesPrimitiveMapper();

    private CustomServicesPrimitiveMapper() {
    };

    public static CustomServicesPrimitiveMapper getInstance() {
        return instance;
    }

    public static CustomServicesPrimitiveResourceRestRep map(final CustomServicesPrimitiveResourceType from) {
        final CustomServicesPrimitiveResourceRestRep to = new CustomServicesPrimitiveResourceRestRep();
        mapDataObjectFields(from.asModelObject(), to);
        final List<CustomServicesPrimitiveResourceRestRep.Attribute> attributes = new ArrayList<CustomServicesPrimitiveResourceRestRep.Attribute>();
        for (final Entry<String, Set<String>> entry : from.attributes().entrySet()) {
            final CustomServicesPrimitiveResourceRestRep.Attribute attribute = new CustomServicesPrimitiveResourceRestRep.Attribute() {
                {
                    setName(entry.getKey());
                    setValues(new ArrayList<>(entry.getValue()));
                }
            };
            attributes.add(attribute);
        }
        to.setAttributes(attributes);

        if(from.parentId() != null){
            to.setParentId(from.parentId());
        }

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
        if (null == primitiveType.resource()) {
            return null;
        }

        final ResourceTypeEnum type = ResourceTypeMapping.getResourceType(CustomServicesPrimitiveResourceModel.class);

        return makeResourceLink(type.getService(), primitiveType.resource());
    }

    private static NamedRelatedResourceRep makeResourceLink(final String service, final NamedURI id)
            throws URISyntaxException {
        StringBuilder builder = new StringBuilder(service)
                .append("/").append(id.getURI());
        return new NamedRelatedResourceRep(id.getURI(), new RestLinkRep("resource", new URI(builder.toString())), id.getName());
    }

    public static void mapPrimitiveFields(final CustomServicesPrimitive from,
            CustomServicesPrimitiveRestRep to) {
        to.setFriendlyName(from.friendlyName());
        to.setDescription(from.description());
        to.setSuccessCriteria(from.successCriteria());
        to.setType(from.stepType().toString());
        to.setAttributes(from.attributes());
        to.setInputGroups(mapInput(from.input()));
        to.setOutput(mapOutput(from.output()));

    }

    public static <T extends CustomServicesDBResource> CustomServicesPrimitiveResourceList toCustomServicesPrimitiveResourceList(
            final String type, final List<NamedElement> fromList) {
        final ImmutableList.Builder<NamedRelatedResourceRep> builder = ImmutableList.builder();
        for (final NamedElement resource : fromList) {
            builder.add(toNamedRelatedResource(ResourceTypeMapping.getResourceType(CustomServicesDBResource.class), resource.getId(),
                    resource.getName()));
        }
        return new CustomServicesPrimitiveResourceList(builder.build());
    }

    private static Map<String, InputGroup> mapInput(
            final Map<String, List<InputParameter>> input) {
        final ImmutableMap.Builder<String, InputGroup> builder = ImmutableMap.<String, InputGroup>builder();

        for(final Entry<String, List<InputParameter>> entry : input.entrySet()) {
            builder.put(entry.getKey().toString(), mapInputGroup(entry.getValue()));
        }
        
        return builder.build();
    }

    private static InputGroup mapInputGroup(final List<InputParameter> list) {
        final ImmutableList.Builder<InputParameterRestRep> builder = ImmutableList.<InputParameterRestRep> builder();
        if (list != null) {
            for (final InputParameter param : list) {
                builder.add(mapInputParameter(param.asBasicInputParameter()));
            }
        }
        final InputGroup inputGroup = new InputGroup();
        inputGroup.setInputGroup(builder.build());
        return inputGroup;
    }

    private static InputParameterRestRep mapInputParameter(final BasicInputParameter<?> param) {
        InputParameterRestRep restRep = new InputParameterRestRep();
        restRep.setName(param.getName());
        restRep.setRequired(param.getRequired());
        restRep.setFieldType(param.getType().name());

        return restRep;
    }

    private static List<OutputParameterRestRep> mapOutput(
            final List<OutputParameter> output) {
        ImmutableList.Builder<OutputParameterRestRep> builder = ImmutableList.<OutputParameterRestRep>builder();
        if(output != null ) {
            for(final OutputParameter param : output) {
                builder.add(mapOutputParameter(param));
            }
        }
        return builder.build();
    }

    private static OutputParameterRestRep mapOutputParameter(
            final OutputParameter param) {
        final OutputParameterRestRep restRep = new OutputParameterRestRep();
        
        restRep.setName(param.getName());
        restRep.setType(param.getType().name());
        
        return restRep;
    }
}
