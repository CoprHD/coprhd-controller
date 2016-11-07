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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.model.orchestration.InputParameterRestRep;
import com.emc.storageos.model.orchestration.OutputParameterRestRep;
import com.emc.storageos.model.orchestration.PrimitiveList;
import com.emc.storageos.model.orchestration.PrimitiveRestRep;
import com.emc.storageos.model.orchestration.internal.BasicInputParameter;
import com.emc.storageos.model.orchestration.internal.BasicOutputParameter;
import com.emc.storageos.model.orchestration.internal.InputParameter;
import com.emc.storageos.model.orchestration.internal.OutputParameter;
import com.emc.storageos.model.orchestration.internal.Primitive;
import com.emc.storageos.model.orchestration.internal.PrimitiveHelper;
import com.emc.storageos.model.orchestration.internal.TableOutputParameter;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

@Path("/primitives")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
public class PrimitiveService {
    
    private final PrimitiveList PRIMITIVE_LIST;
    private final Map<String, PrimitiveRestRep> PRIMITIVE_MAP;
    
    public PrimitiveService() {
        Builder<String, PrimitiveRestRep> builder = ImmutableMap.<String, PrimitiveRestRep>builder();
        for(final Primitive primitive : PrimitiveHelper.list() ) {
            PrimitiveRestRep primitiveRestRep = new PrimitiveRestRep();
            primitiveRestRep.setName(primitive.getName());
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
        return PRIMITIVE_MAP.get(name);
    }

    private static List<InputParameterRestRep> mapInput(List<InputParameter> inputParameters) {
        List<InputParameterRestRep> inputRestRep = new ArrayList<InputParameterRestRep>();
        int index = 0;
        for(final InputParameter parameter : inputParameters) {
            
            if(parameter.isBasicInputParameter()) {
                InputParameterRestRep inputParamRestRep = new InputParameterRestRep();
                inputParamRestRep.setName(parameter.getName());
                inputParamRestRep.setType(parameter.getType());
                BasicInputParameter<?> inputParam = parameter.asBasicInputParameter();
                inputParamRestRep.setRequired(inputParam.getRequired());
                if( null != inputParam.getDefaultValue() ) {
                    inputParamRestRep.setDefaultValue(Collections.singletonList(inputParam.getDefaultValue().toString()));
                }
                inputRestRep.add(index++, inputParamRestRep);
            }
            
        }
        return inputRestRep;
    }
    
    private static List<OutputParameterRestRep> mapOutput(List<OutputParameter> output) {
        List<OutputParameterRestRep> outputRestRep = new ArrayList<OutputParameterRestRep>();
        int index = 0;
        for(final OutputParameter parameter : output) {
            if(parameter.isBasicOutputParameter()) {
                BasicOutputParameter outputParam = parameter.asBasicOutputParameter();
                OutputParameterRestRep parameterRestRep = new OutputParameterRestRep();
                parameterRestRep.setName(outputParam.getName());
                parameterRestRep.setType(outputParam.getType());
                outputRestRep.add(index++, parameterRestRep);
            } else {
                TableOutputParameter outputParam = parameter.asTableOutputParameter();
                for( final BasicOutputParameter column : outputParam.getColumns()) {
                    OutputParameterRestRep parameterRestRep = new OutputParameterRestRep();
                    parameterRestRep.setName(column.getName());
                    parameterRestRep.setType(column.getType());
                    parameterRestRep.setTable(outputParam.getName());
                    outputRestRep.add(index++, parameterRestRep);
                }
            }
        }
        return outputRestRep;
    }

}
