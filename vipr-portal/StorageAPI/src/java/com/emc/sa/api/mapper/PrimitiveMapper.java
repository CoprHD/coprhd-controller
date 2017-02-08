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

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.api.service.impl.response.ResourceTypeMapping;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.Ansible;
import com.emc.storageos.db.client.model.uimodels.CustomServiceScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.PrimitiveResource;
import com.emc.storageos.db.client.model.uimodels.UserPrimitive;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.customservices.InputParameterRestRep;
import com.emc.storageos.model.customservices.OutputParameterRestRep;
import com.emc.storageos.model.customservices.PrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.PrimitiveResourceRestRep.Attribute;
import com.emc.storageos.model.customservices.PrimitiveRestRep;
import com.emc.storageos.primitives.Parameter.ParameterType;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public final class  PrimitiveMapper {
    public final static PrimitiveMapper instance = new PrimitiveMapper();

    private PrimitiveMapper() {
    };

    public static PrimitiveMapper getInstance() {
        return instance;
    }
    
    public static PrimitiveResourceRestRep map(final PrimitiveResource from ) {
        final PrimitiveResourceRestRep to = new PrimitiveResourceRestRep();
        mapDataObjectFields(from, to);
        if( from.isAnsiblePackage()) {
            final Attribute playbooks = new Attribute();
            playbooks.setName("playbooks");
            playbooks.setValues(Arrays.asList(from.asAnsiblePackage().getPlaybooks().toArray(new String[0])));
            to.setAttributes(Collections.singletonList(playbooks));
        } else if(from.isCustomerServiceScriptResource()) {
            
        } else {
            throw new RuntimeException("Uknown resource type: " + from );
        }
        return to;
    }

    public static PrimitiveRestRep map(final UserPrimitive from) {
        final PrimitiveRestRep to = new PrimitiveRestRep();

        mapDataObjectFields(from, to);
        mapPrimitiveFields(from, to);
        
        if(from.isAnsible()) {
            mapAnsible(from.asAnsible(), to);
        } else if(from.isCustomeServiceScript()) {
            mapScript(from.asCustomeServiceScript(), to);
        }
        
        try {
            to.setResource(makeResourceLink(from));
        } catch (final URISyntaxException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Making prmitive resource link failed", e);
        }
        return to;
    }

    private static RestLinkRep makeResourceLink(UserPrimitive resource) throws URISyntaxException {
        final ResourceTypeEnum type = ResourceTypeMapping.getResourceType(resource);
        if(type == null) {
            return null;
        }
        
        switch(type) {
        case ANSIBLE:
           return makeResourceLink(type.getService(), "ansible", resource.asAnsible().getArchive());
        case SCRIPT_PRIMITIVE:
            return makeResourceLink(type.getService(), "script", resource.asCustomeServiceScript().getScript());
        default:
            return null;
        }
    }


    private static RestLinkRep makeResourceLink(final String service, final String type, final URI id) throws URISyntaxException {
        StringBuilder builder = new StringBuilder(service).append("/resource/")
                .append(type).append("/").append(id);
        return new RestLinkRep("resource", new URI(builder.toString()));
    }

    private static void mapAnsible(final Ansible from,
            final PrimitiveRestRep to) {
        final Map<String, PrimitiveRestRep.InputGroup> input = new HashMap<String, PrimitiveRestRep.InputGroup>();
        if (null != from.getExtraVars()) {
            final List<InputParameterRestRep> inputParam = new ArrayList<InputParameterRestRep>();
            for (final String extraVar : from.getExtraVars()) {
                final InputParameterRestRep param = new InputParameterRestRep();
                param.setName(extraVar);
                param.setType(ParameterType.STRING.name());
                inputParam.add(param);
            }
            PrimitiveRestRep.InputGroup inputGroup = new PrimitiveRestRep.InputGroup(){{
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
    
    private static void mapScript(final CustomServiceScriptPrimitive from, final PrimitiveRestRep to) {
        final Map<String, PrimitiveRestRep.InputGroup> input = new HashMap<String, PrimitiveRestRep.InputGroup>();
        if (null != from.getInput()) {
            final List<InputParameterRestRep> inputParam = new ArrayList<InputParameterRestRep>();
            for (final String arg : from.getInput()) {
                final InputParameterRestRep param = new InputParameterRestRep();
                param.setName(arg);
                param.setType(ParameterType.STRING.name());
                inputParam.add(param);
            }
            PrimitiveRestRep.InputGroup inputGroup = new PrimitiveRestRep.InputGroup(){{
                setInputGroup(inputParam);
            }};
            input.put("input_params",inputGroup);
        }
        to.setInputGroups(input);
    }

    public static void mapPrimitiveFields(final UserPrimitive from,
            PrimitiveRestRep to) {
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
}
