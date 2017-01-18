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
package com.emc.sa.api;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.model.orchestration.InputParameterRestRep;
import com.emc.storageos.model.orchestration.OutputParameterRestRep;
import com.emc.storageos.model.orchestration.PrimitiveList;
import com.emc.storageos.model.orchestration.PrimitiveRestRep;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.primitives.PrimitiveHelper;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.emc.storageos.primitives.output.TableOutputParameter;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/primitives")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
public class PrimitiveService {
    
    private final PrimitiveList PRIMITIVE_LIST;
    private final ImmutableMap<String, PrimitiveRestRep> PRIMITIVE_MAP;
    
    public PrimitiveService() {
        Builder<String, PrimitiveRestRep> builder = ImmutableMap.<String, PrimitiveRestRep>builder();
        for(final Primitive primitive : PrimitiveHelper.list() ) {
            PrimitiveRestRep primitiveRestRep = new PrimitiveRestRep();
            primitiveRestRep.setName(primitive.getName());
            primitiveRestRep.setType(primitive.getType().toString());
            primitiveRestRep.setFriendlyName(primitive.getFriendlyName());
            primitiveRestRep.setDescription(primitive.getDescription());
            primitiveRestRep.setSuccessCriteria(primitive.getSuccessCriteria());
            primitiveRestRep.setInput(mapInput(primitive.getInput()));
            primitiveRestRep.setOutput(mapOutput(primitive.getOutput()));
            builder.put(primitiveRestRep.getName(), primitiveRestRep);
        }
        PRIMITIVE_MAP = builder.build();
        PRIMITIVE_LIST = new PrimitiveList(ImmutableList.<PrimitiveRestRep>builder().addAll((PRIMITIVE_MAP.values())).build());
    }

    /**
     * Get the list of primitives that can be used for creating orchestration workflows
     *
     * @prereq none
     *
     *
     * @brief Get list of primitives
     *
     * @return PrimitiveList
     *
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public PrimitiveList getPrimitives() {
        return PRIMITIVE_LIST;
    }
    
    /**
     * Get a primitive that can be used in orchestration workflows
     *
     * @prereq none
     *
     *
     * @brief Get a primitive by name
     *
     * @param name
     * @return PrimitiveRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public PrimitiveRestRep getPrimitive(@PathParam(value = "id") final String name) {
        final PrimitiveRestRep primitive = PRIMITIVE_MAP.get(name);
        if( null == primitive) {
            throw APIException.notFound.unableToFindEntityInURL(URIUtil.uri(name));
        }
        return primitive;
    }

    private static Map<String,InputParameterRestRep> mapInput(Map<String,InputParameter> inputParameters) {
        Map<String,InputParameterRestRep> inputRestRep = new HashMap<String,InputParameterRestRep>();
        for(final InputParameter parameter : inputParameters.values()) {
            String key = parameter.getName();
            if(parameter.isBasicInputParameter()) {
                InputParameterRestRep inputParamRestRep = new InputParameterRestRep();
                inputParamRestRep.setType(parameter.getType().name());
                BasicInputParameter<?> inputParam = parameter.asBasicInputParameter();
                inputParamRestRep.setRequired(inputParam.getRequired());
                if( null != inputParam.getDefaultValue() ) {
                    inputParamRestRep.setDefaultValue(Collections.singletonList(inputParam.getDefaultValue().toString()));
                }
                inputRestRep.put(key, inputParamRestRep);
            }
            
        }
        return inputRestRep;
    }
    
    private static Map<String,OutputParameterRestRep> mapOutput(Map<String,OutputParameter> output) {
        Map<String,OutputParameterRestRep> outputRestRep = new HashMap<String,OutputParameterRestRep>();
        for(final OutputParameter parameter : output.values()) {
            String key = parameter.getName();
            if(parameter.isBasicOutputParameter()) {
                BasicOutputParameter outputParam = parameter.asBasicOutputParameter();
                OutputParameterRestRep parameterRestRep = new OutputParameterRestRep();
                parameterRestRep.setType(outputParam.getType().name());
                outputRestRep.put(key, parameterRestRep);
            } else {
                TableOutputParameter outputParam = parameter.asTableOutputParameter();
                for( final BasicOutputParameter column : outputParam.getColumns()) {
                    OutputParameterRestRep parameterRestRep = new OutputParameterRestRep();
                    parameterRestRep.setType(column.getType().name());
                    parameterRestRep.setTable(outputParam.getName());
                    outputRestRep.put(key, parameterRestRep);
                }
            }
        }
        return outputRestRep;
    }

}
