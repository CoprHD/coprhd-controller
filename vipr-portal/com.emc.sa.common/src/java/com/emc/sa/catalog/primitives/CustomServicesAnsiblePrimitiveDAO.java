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
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsibleInventoryResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsiblePrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsibleResource;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.ansible.CustomServicesAnsiblePrimitive;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;

/**
 * Data access object for Ansible primitives
 *
 */
public class CustomServicesAnsiblePrimitiveDAO implements
        CustomServicesPrimitiveDAO<CustomServicesAnsiblePrimitive> {

    @Autowired
    private CustomServicesPrimitiveManager primitiveManager;
    @Autowired
    private ModelClient client;
    @Autowired
    private DbClient dbClient;

    private static final Set<String> INPUT_TYPES = Collections.singleton(CustomServicesConstants.INPUT_PARAMS);
    private static final Set<String> ATTRIBUTES = Collections.singleton(CustomServicesConstants.ANSIBLE_PLAYBOOK);
            
    private static final Function<CustomServicesDBAnsiblePrimitive, CustomServicesAnsiblePrimitive> MAPPER = 
            new Function<CustomServicesDBAnsiblePrimitive, CustomServicesAnsiblePrimitive>() {
        @Override
        public CustomServicesAnsiblePrimitive apply(final CustomServicesDBAnsiblePrimitive primitive) {
            final Map<String, List<InputParameter>> input = ImmutableMap.<String, List<InputParameter>>builder()
                    .putAll(CustomServicesDBHelper.mapInput(INPUT_TYPES, primitive.getInput()))
                    .putAll(CustomServicesConstants.ANSIBLE_OPTIONS_INPUT_GROUP)
                    .build();
            final List<OutputParameter> output = CustomServicesDBHelper.mapOutput(primitive.getOutput());
            final Map<String, String> attributes = CustomServicesDBHelper.mapAttributes(ATTRIBUTES, primitive.getAttributes()); 
            return new CustomServicesAnsiblePrimitive(primitive, input, attributes, output);
        }

    };
    
    private static final Function<CustomServicesDBAnsiblePrimitive, CustomServicesAnsiblePrimitive> EXPORT_MAPPER = 
            new Function<CustomServicesDBAnsiblePrimitive, CustomServicesAnsiblePrimitive>() {
        @Override
        public CustomServicesAnsiblePrimitive apply(final CustomServicesDBAnsiblePrimitive primitive) {
            final Map<String, List<InputParameter>> input = CustomServicesDBHelper.mapInput(INPUT_TYPES, primitive.getInput());
            final List<OutputParameter> output = CustomServicesDBHelper.mapOutput(primitive.getOutput());
            final Map<String, String> attributes = CustomServicesDBHelper.mapAttributes(ATTRIBUTES, primitive.getAttributes()); 
            return new CustomServicesAnsiblePrimitive(primitive, input, attributes, output);
        }

    };
    
    @Override
    public String getType() {
        return CustomServicesAnsiblePrimitive.TYPE;
    }

    @Override
    public CustomServicesAnsiblePrimitive get(final URI id) {
        return CustomServicesDBHelper.get(MAPPER, CustomServicesDBAnsiblePrimitive.class, primitiveManager,
                id);
    }

    @Override
    public CustomServicesAnsiblePrimitive create(
            CustomServicesPrimitiveCreateParam param) {
        return CustomServicesDBHelper.create(
                MAPPER, 
                CustomServicesDBAnsiblePrimitive.class,
                CustomServicesDBAnsibleResource.class, 
                primitiveManager,
                CustomServicesDBHelper.createInputFunction(INPUT_TYPES),
                CustomServicesDBHelper.createAttributesFunction(ATTRIBUTES),
                param);
    }

    @Override
    public CustomServicesAnsiblePrimitive update(URI id,
            CustomServicesPrimitiveUpdateParam param) {
        return CustomServicesDBHelper.update(MAPPER, 
                CustomServicesDBAnsiblePrimitive.class,
                CustomServicesDBAnsibleResource.class, 
                primitiveManager, 
                client, 
                param, 
                CustomServicesDBHelper.updateInputFunction(INPUT_TYPES),
                CustomServicesDBHelper.updateAttributesFunction(ATTRIBUTES),
                id, CustomServicesDBAnsibleInventoryResource.class);
    }

    @Override
    public void deactivate(URI id) {
        CustomServicesDBHelper.deactivate(CustomServicesDBAnsiblePrimitive.class, primitiveManager, client, id, CustomServicesDBAnsibleResource.class,CustomServicesDBAnsibleInventoryResource.class);
    }

    @Override
    public String getPrimitiveModel() {
        return CustomServicesDBAnsiblePrimitive.class.getSimpleName();
    }

    @Override
    public List<URI> list() {
        return CustomServicesDBHelper.list(CustomServicesDBAnsiblePrimitive.class, client);
    }

    @Override
    public Iterator<CustomServicesPrimitiveRestRep> bulk(final Collection<URI> ids) {
        return CustomServicesDBHelper.bulk(ids, CustomServicesAnsiblePrimitive.class, 
                CustomServicesDBAnsiblePrimitive.class, dbClient, MAPPER);
    }

    @Override
    public boolean hasResource() {
        return true;
    }

    @Override
    public boolean importPrimitive(final CustomServicesPrimitiveRestRep operation) {
        return CustomServicesDBHelper.importDBPrimitive(CustomServicesDBAnsiblePrimitive.class, operation, client);
    }


    @Override
    public CustomServicesAnsiblePrimitive export(URI id) {
        return CustomServicesDBHelper.exportDBPrimitive(CustomServicesDBAnsiblePrimitive.class, id, primitiveManager, EXPORT_MAPPER);
    }
}
