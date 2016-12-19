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
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.emc.storageos.api.service.impl.response.ResourceTypeMapping;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.AnsiblePackage;
import com.emc.storageos.db.client.model.uimodels.UserPrimitive;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.orchestration.InputParameterRestRep;
import com.emc.storageos.model.orchestration.OutputParameterRestRep;
import com.emc.storageos.model.orchestration.PrimitiveRestRep;
import com.emc.storageos.primitives.Parameter.ParameterType;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

/**
 *
 */
public class PrimitiveMapper {
    public final static PrimitiveMapper instance = new PrimitiveMapper();

    private PrimitiveMapper() {
    };

    public PrimitiveMapper getInstance() {
        return instance;
    }

    public static PrimitiveRestRep map(final UserPrimitive from) {
        final PrimitiveRestRep to = new PrimitiveRestRep();

        mapDataObjectFields(from, to);
        mapPrimitiveFields(from, to);
        if(from.isAnsiblePackage()) {
            mapAnsiblePackage(from.asAnsiblePackage(), to);
        }
        try {
            to.setResource(makeResourceLink(from));
        } catch (final URISyntaxException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Making prmitive resource link failed", e);
        }
        return to;
    }

    /**
     * 
     */
    private static RestLinkRep makeResourceLink(UserPrimitive resource) throws URISyntaxException {
        final ResourceTypeEnum type = ResourceTypeMapping.getResourceType(resource);
        if(type == null) {
            return null;
        }
        
        switch(type) {
        case ANSIBLE_PACKAGE:
           return makeResourceLink(type.getService(), "ansible", resource.getId());
        default:
            return null;
        }
    }


    private static RestLinkRep makeResourceLink(final String service, final String type, final URI id) throws URISyntaxException {
        StringBuilder builder = new StringBuilder(service).append("/resource/")
                .append(type).append("/").append(id);
        return new RestLinkRep("resource", new URI(builder.toString()));
    }

    /**
     * 
     */
    private static void mapAnsiblePackage(final AnsiblePackage from,
            final PrimitiveRestRep to) {
        List<InputParameterRestRep> input = new ArrayList<InputParameterRestRep>();
        if (null != from.getExtraVars()) {
            for (final String extraVar : from.getExtraVars()) {
                InputParameterRestRep param = new InputParameterRestRep();
                param.setName("@extraVars." + extraVar);
                param.setType(ParameterType.STRING.name());
                input.add(param);
            }
        }
        to.setInput(input);
   
        List<PrimitiveRestRep.Attribute> attributes = new ArrayList<PrimitiveRestRep.Attribute>();
        if (null != from.getEntryPoints()) {
            PrimitiveRestRep.Attribute attribute = new PrimitiveRestRep.Attribute();
            attribute.setName("entryPoints");
            attribute.setValues(Arrays.asList(from.getEntryPoints().toArray(
                    new String[from.getEntryPoints().size()])));
            attributes.add(attribute);
        }
        to.setAttributes(attributes);
    }

    public static void mapPrimitiveFields(final UserPrimitive from,
            PrimitiveRestRep to) {
        to.setName(from.getName());
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
