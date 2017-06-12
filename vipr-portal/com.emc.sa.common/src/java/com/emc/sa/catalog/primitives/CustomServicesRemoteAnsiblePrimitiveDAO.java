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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBRemoteAnsiblePrimitive;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.remoteansible.CustomServicesRemoteAnsiblePrimitive;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class CustomServicesRemoteAnsiblePrimitiveDAO implements CustomServicesPrimitiveDAO<CustomServicesRemoteAnsiblePrimitive> {

    @Autowired
    private CustomServicesPrimitiveManager primitiveManager;
    @Autowired
    private ModelClient client;
    @Autowired
    private DbClient dbClient;

    private static final Set<String> INPUT_TYPES = Collections.singleton(CustomServicesConstants.INPUT_PARAMS);
    private static final Set<String> ATTRIBUTES = ImmutableSet.<String>builder()
            .add(CustomServicesConstants.ANSIBLE_PLAYBOOK)
            .add(CustomServicesConstants.ANSIBLE_BIN)
            .build();
            
    private static final ImmutableMap<String, List<InputParameter>> CONNECTION_DETAILS_INPUT_GROUP = 
            ImmutableMap.<String, List<InputParameter>>of(
            CustomServicesConstants.CONNECTION_DETAILS, ImmutableList.<InputParameter>builder()
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.REMOTE_NODE, true, null))
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.REMOTE_USER, true, null))
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.REMOTE_PASSWORD, true, null))
                    .build());
    
    private static final Function<CustomServicesDBRemoteAnsiblePrimitive, CustomServicesRemoteAnsiblePrimitive> MAPPER = 
            new Function<CustomServicesDBRemoteAnsiblePrimitive, CustomServicesRemoteAnsiblePrimitive>() {
        @Override
        public CustomServicesRemoteAnsiblePrimitive apply(final CustomServicesDBRemoteAnsiblePrimitive primitive) {
            final Map<String, List<InputParameter>> input = ImmutableMap.<String, List<InputParameter>>builder()
                    .putAll(CustomServicesDBHelper.mapInput(INPUT_TYPES, primitive.getInput()))
                    .putAll(CustomServicesConstants.ANSIBLE_OPTIONS_INPUT_GROUP)
                    .putAll(CONNECTION_DETAILS_INPUT_GROUP)
                    .build();
            final List<OutputParameter> output = CustomServicesDBHelper.mapOutput(primitive.getOutput());
            final Map<String, String> attributes = CustomServicesDBHelper.mapAttributes(ATTRIBUTES, primitive.getAttributes()); 
            return new CustomServicesRemoteAnsiblePrimitive(primitive, input, attributes, output);
        }

    };
    
    private static final Function<CustomServicesDBRemoteAnsiblePrimitive, CustomServicesRemoteAnsiblePrimitive> EXPORT_MAPPER = 
            new Function<CustomServicesDBRemoteAnsiblePrimitive, CustomServicesRemoteAnsiblePrimitive>() {
        @Override
        public CustomServicesRemoteAnsiblePrimitive apply(final CustomServicesDBRemoteAnsiblePrimitive primitive) {
            final Map<String, List<InputParameter>> input = CustomServicesDBHelper.mapInput(INPUT_TYPES, primitive.getInput());
            final List<OutputParameter> output = CustomServicesDBHelper.mapOutput(primitive.getOutput());
            final Map<String, String> attributes = CustomServicesDBHelper.mapAttributes(ATTRIBUTES, primitive.getAttributes()); 
            return new CustomServicesRemoteAnsiblePrimitive(primitive, input, attributes, output);
        }

    };
    
    @Override
    public String getType() {
        return CustomServicesRemoteAnsiblePrimitive.TYPE;
    }

    @Override
    public CustomServicesRemoteAnsiblePrimitive get(URI id) {
        return CustomServicesDBHelper.get(MAPPER, CustomServicesDBRemoteAnsiblePrimitive.class, primitiveManager, id);
    }

    @Override
    public CustomServicesRemoteAnsiblePrimitive create(CustomServicesPrimitiveCreateParam param) {
        return CustomServicesDBHelper.create(MAPPER, 
                CustomServicesDBRemoteAnsiblePrimitive.class, 
                CustomServicesDBNoResource.class, 
                primitiveManager, 
                CustomServicesDBHelper.createInputFunction(INPUT_TYPES), 
                CustomServicesDBHelper.createAttributesFunction(ATTRIBUTES), 
                param);
    }

    @Override
    public CustomServicesRemoteAnsiblePrimitive update(URI id, CustomServicesPrimitiveUpdateParam param) {
        return CustomServicesDBHelper.update(MAPPER, 
                CustomServicesDBRemoteAnsiblePrimitive.class, 
                CustomServicesDBNoResource.class, 
                primitiveManager, 
                client,
                param,
                CustomServicesDBHelper.updateInputFunction(INPUT_TYPES), 
                CustomServicesDBHelper.updateAttributesFunction(ATTRIBUTES),
                id, null);
    }

    @Override
    public void deactivate(URI id) {
        CustomServicesDBHelper.deactivate(CustomServicesDBRemoteAnsiblePrimitive.class, primitiveManager, client, id, CustomServicesDBNoResource.class, null);
    }

    @Override
    public List<URI> list() {
        return CustomServicesDBHelper.list(CustomServicesDBRemoteAnsiblePrimitive.class, client);
    }

    @Override
    public String getPrimitiveModel() {
        return CustomServicesDBRemoteAnsiblePrimitive.class.getSimpleName();
    }

    @Override
    public Iterator<CustomServicesPrimitiveRestRep> bulk(Collection<URI> ids) {
        return CustomServicesDBHelper.bulk(ids, 
                CustomServicesRemoteAnsiblePrimitive.class, 
                CustomServicesDBRemoteAnsiblePrimitive.class, 
                dbClient, 
                MAPPER);
    }

    @Override
    public boolean importPrimitive(final CustomServicesPrimitiveRestRep operation) {
        return CustomServicesDBHelper.importDBPrimitive(CustomServicesDBRemoteAnsiblePrimitive.class, operation, client);
    }
    
    @Override
    public CustomServicesRemoteAnsiblePrimitive export(URI id) {
        return CustomServicesDBHelper.exportDBPrimitive(CustomServicesDBRemoteAnsiblePrimitive.class, id, primitiveManager, EXPORT_MAPPER);
    }
    
    @Override
    public boolean hasResource() {
        return false;
    }

}
