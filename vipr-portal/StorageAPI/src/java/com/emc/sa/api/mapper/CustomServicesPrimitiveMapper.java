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
package com.emc.sa.api.mapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.impl.response.ResourceTypeMapping;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.CustomServicesAnsiblePrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitiveResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesUserPrimitive;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep.Attribute;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.InputParameterRestRep;
import com.emc.storageos.model.customservices.OutputParameterRestRep;
import com.emc.storageos.primitives.Parameter.ParameterType;
import com.emc.storageos.primitives.Primitive.StepType;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.google.common.collect.ImmutableList;

public final class  CustomServicesPrimitiveMapper extends DbObjectMapper {
    public final static CustomServicesPrimitiveMapper instance = new CustomServicesPrimitiveMapper();

    private CustomServicesPrimitiveMapper() {
    };

    public static CustomServicesPrimitiveMapper getInstance() {
        return instance;
    }
    
    public static CustomServicesPrimitiveResourceRestRep map(final CustomServicesPrimitiveResource from ) {
        final CustomServicesPrimitiveResourceRestRep to = new CustomServicesPrimitiveResourceRestRep();
        mapDataObjectFields(from, to);
        if( from.isCustomServiceAnsiblePackage()) {
            final Attribute playbooks = new Attribute();
            playbooks.setName("playbooks");
            playbooks.setValues(Arrays.asList(from.asCustomServiceAnsiblePackage().getPlaybooks().toArray(new String[0])));
            to.setAttributes(Collections.singletonList(playbooks));
        } else if(from.isCustomServiceScriptResource()) {
            
        } else {
            throw new RuntimeException("Uknown resource type: " + from );
        }
        return to;
    }

    public static CustomServicesPrimitiveRestRep map(final CustomServicesUserPrimitive from) {
        final CustomServicesPrimitiveRestRep to = new CustomServicesPrimitiveRestRep();

        mapDataObjectFields(from, to);
        mapPrimitiveFields(from, to);
        
        if(from.isCustomServiceAnsiblePrimitive()) {
            mapAnsible(from.asCustomServiceAnsiblePrimitive(), to);
        } else if(from.isCustomServiceScriptPrimitive()) {
            mapScript(from.asCustomServiceScriptPrimitive(), to);
        }
        
        try {
            to.setResource(makeResourceLink(from));
        } catch (final URISyntaxException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Making prmitive resource link failed", e);
        }
        return to;
    }

    private static NamedRelatedResourceRep makeResourceLink(CustomServicesUserPrimitive resource) throws URISyntaxException {
        final ResourceTypeEnum type = ResourceTypeMapping.getResourceType(resource);
        if(type == null) {
            return null;
        }
        
        switch(type) {
        case ANSIBLE:
           return makeResourceLink(type.getService(), "ansible", resource.asCustomServiceAnsiblePrimitive().getArchive());
        case SCRIPT_PRIMITIVE:
            return makeResourceLink(type.getService(), "script", resource.asCustomServiceScriptPrimitive().getScript());
        default:
            return null;
        }
    }


    private static NamedRelatedResourceRep makeResourceLink(final String service, final String type, final NamedURI id) throws URISyntaxException {
        StringBuilder builder = new StringBuilder(service).append("/resource/")
                .append(type).append("/").append(id);
        return new NamedRelatedResourceRep(id.getURI(), new RestLinkRep("resource", new URI(builder.toString())), id.getName());
    }

    private static void mapAnsible(final CustomServicesAnsiblePrimitive from,
            final CustomServicesPrimitiveRestRep to) {
        to.setType(StepType.LOCAL_ANSIBLE.toString());
        final Map<String, CustomServicesPrimitiveRestRep.InputGroup> input = new HashMap<String, CustomServicesPrimitiveRestRep.InputGroup>();
        if (null != from.getExtraVars()) {
            final List<InputParameterRestRep> inputParam = new ArrayList<InputParameterRestRep>();
            for (final String extraVar : from.getExtraVars()) {
                final InputParameterRestRep param = new InputParameterRestRep();
                param.setName(extraVar);
                param.setType(ParameterType.STRING.name());
                inputParam.add(param);
            }
            CustomServicesPrimitiveRestRep.InputGroup inputGroup = new CustomServicesPrimitiveRestRep.InputGroup(){{
                setInputGroup(inputParam);
            }};
            input.put("input_params",inputGroup);
        }
        to.setInputGroups(input);
   
        Map<String, String> attributes = new HashMap<String, String>();
        if (null != from.getPlaybook()) {
            attributes.put("playbook", from.getPlaybook());
        }
        to.setAttributes(attributes);
    }
    
    private static void mapScript(final CustomServicesScriptPrimitive from, final CustomServicesPrimitiveRestRep to) {
        to.setType(StepType.SHELL_SCRIPT.toString());
        final Map<String, CustomServicesPrimitiveRestRep.InputGroup> input = new HashMap<String, CustomServicesPrimitiveRestRep.InputGroup>();
        if (null != from.getInput()) {
            final List<InputParameterRestRep> inputParam = new ArrayList<InputParameterRestRep>();
            for (final String arg : from.getInput()) {
                final InputParameterRestRep param = new InputParameterRestRep();
                param.setName(arg);
                param.setType(ParameterType.STRING.name());
                inputParam.add(param);
            }
            CustomServicesPrimitiveRestRep.InputGroup inputGroup = new CustomServicesPrimitiveRestRep.InputGroup(){{
                setInputGroup(inputParam);
            }};
            input.put("input_params",inputGroup);
        }
        to.setInputGroups(input);
    }

    public static void mapPrimitiveFields(final CustomServicesUserPrimitive from,
            CustomServicesPrimitiveRestRep to) {
        to.setFriendlyName(from.getFriendlyName());
        to.setDescription(from.getDescription());
        to.setSuccessCriteria(from.getSuccessCriteria());
        to.setOutput(mapOutput(from.getOutput()));
    }

    private static List<OutputParameterRestRep> mapOutput(StringSet from) {
        final List<OutputParameterRestRep> to = new ArrayList<OutputParameterRestRep>();
        if (null != from) {
            for (final String parameter : from) {
                final OutputParameterRestRep paramRestRep = new OutputParameterRestRep();
                paramRestRep.setName(parameter);
                paramRestRep.setType(ParameterType.STRING.name());
                to.add(paramRestRep);
            }
        }
        return to;
    }

    public static <T extends CustomServicesPrimitiveResource> CustomServicesPrimitiveResourceList toCustomServicesPrimitiveResourceList(
            final Class<T> type, final List<NamedElement> fromList) {
        final ImmutableList.Builder<NamedRelatedResourceRep> builder = ImmutableList.builder();

        for( final NamedElement resource : fromList) {
            builder.add(toNamedRelatedResource(ResourceTypeMapping.getResourceType(type), resource.getId(), resource.getName()));
        }
        return new CustomServicesPrimitiveResourceList(builder.build());
    }
}
