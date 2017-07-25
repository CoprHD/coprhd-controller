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
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptResource;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.script.CustomServicesScriptPrimitive;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.google.common.base.Function;

/**
 * Data access object for script primitives
 *
 */
public class CustomServicesScriptPrimitiveDAO implements CustomServicesPrimitiveDAO<CustomServicesScriptPrimitive> {

    @Autowired
    private CustomServicesPrimitiveManager primitiveManager;
    @Autowired
    private ModelClient client;
    @Autowired
    private DbClient dbClient;

    private static final Set<String> INPUT_TYPES = Collections.singleton(CustomServicesConstants.INPUT_PARAMS);
    private static final Set<String> ATTRIBUTES = Collections.emptySet();
    
    private static final Function<CustomServicesDBScriptPrimitive, CustomServicesScriptPrimitive> MAPPER = 
            new Function<CustomServicesDBScriptPrimitive, CustomServicesScriptPrimitive>() {
        @Override
        public CustomServicesScriptPrimitive apply(final CustomServicesDBScriptPrimitive primitive) {
            final Map<String, List<InputParameter>> input = CustomServicesDBHelper.mapInput(INPUT_TYPES, primitive.getInput());
            final List<OutputParameter> output = CustomServicesDBHelper.mapOutput(primitive.getOutput());
            final Map<String, String> attributes = CustomServicesDBHelper.mapAttributes(ATTRIBUTES, primitive.getAttributes()); 
            return new CustomServicesScriptPrimitive(primitive, input, attributes, output);
        }

    };
    
    @Override
    public String getType() {
        return CustomServicesScriptPrimitive.TYPE;
    }

    @Override
    public CustomServicesScriptPrimitive get(final URI id) {
        return CustomServicesDBHelper.get(MAPPER, CustomServicesDBScriptPrimitive.class, primitiveManager, id);
    }

    @Override
    public CustomServicesScriptPrimitive create(
            CustomServicesPrimitiveCreateParam param) {
        return CustomServicesDBHelper.create(MAPPER, 
                CustomServicesDBScriptPrimitive.class,
                CustomServicesDBScriptResource.class,
                primitiveManager, 
                CustomServicesDBHelper.createInputFunction(INPUT_TYPES),
                CustomServicesDBHelper.createAttributesFunction(ATTRIBUTES),
                param);
    }

    @Override
    public CustomServicesScriptPrimitive update(URI id,
            CustomServicesPrimitiveUpdateParam param) {
        return CustomServicesDBHelper.update(MAPPER, 
                CustomServicesDBScriptPrimitive.class,
                CustomServicesDBScriptResource.class, 
                primitiveManager, 
                client, 
                param, 
                CustomServicesDBHelper.updateInputFunction(INPUT_TYPES),
                CustomServicesDBHelper.updateAttributesFunction(ATTRIBUTES),
                id, null);
    }

    @Override
    public void deactivate(URI id) {
        CustomServicesDBHelper.deactivate(CustomServicesDBScriptPrimitive.class, primitiveManager, client, id, CustomServicesDBScriptResource.class, null);
    }

    @Override
    public String getPrimitiveModel() {
        return CustomServicesDBScriptPrimitive.class.getSimpleName();
    }

    @Override
    public List<URI> list() {
        return CustomServicesDBHelper.list(CustomServicesDBScriptPrimitive.class, client);
    }

    @Override
    public Iterator<CustomServicesPrimitiveRestRep> bulk(Collection<URI> ids) {
        return CustomServicesDBHelper.bulk(ids, CustomServicesScriptPrimitive.class, 
                CustomServicesDBScriptPrimitive.class, dbClient, MAPPER);
    }

    @Override
    public boolean importPrimitive(final CustomServicesPrimitiveRestRep operation) {
        return CustomServicesDBHelper.importDBPrimitive(CustomServicesDBScriptPrimitive.class, operation, client);
    }
    
    @Override
    public CustomServicesScriptPrimitive export(URI id) {
        return CustomServicesDBHelper.exportDBPrimitive(CustomServicesDBScriptPrimitive.class, id, primitiveManager, MAPPER);
    }
    
    @Override
    public boolean hasResource() {
        return true;
    }
}
