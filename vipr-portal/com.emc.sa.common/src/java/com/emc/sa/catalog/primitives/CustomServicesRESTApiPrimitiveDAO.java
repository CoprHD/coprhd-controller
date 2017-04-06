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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriTemplate;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.catalog.primitives.CustomServicesDBHelper.UpdatePrimitive;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBRESTApiPrimitive;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.restapi.CustomServicesRESTApiPrimitive;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class CustomServicesRESTApiPrimitiveDAO implements CustomServicesPrimitiveDAO<CustomServicesRESTApiPrimitive> {

    @Autowired
    private CustomServicesPrimitiveManager primitiveManager;
    @Autowired
    private ModelClient client;
    @Autowired 
    private DbClient dbClient;
    
    private static final Set<String> ATTRIBUTES = ImmutableSet.<String>builder()
            .add(CustomServicesConstants.BODY)
            .add(CustomServicesConstants.PROTOCOL)
            .add(CustomServicesConstants.METHOD)
            .add(CustomServicesConstants.AUTH_TYPE)
            .add(CustomServicesConstants.PATH)
            .build();
    
    private static final Set<String> REQUEST_INPUT_TYPES = ImmutableSet.<String>builder()
            .add(CustomServicesConstants.QUERY_PARAMS)
            .add(CustomServicesConstants.HEADERS)
            .add(CustomServicesConstants.CREDENTIALS)
            .build();
    
    private static final Set<String> RESPONSE_INPUT_TYPES = ImmutableSet.<String>builder()
            .addAll(REQUEST_INPUT_TYPES)
            .add(CustomServicesConstants.INPUT_PARAMS)
            .build();
    
    private static final ImmutableMap<String, List<InputParameter>> CONNECTION_OPTIONS = ImmutableMap.<String, List<InputParameter>>builder()
            .put(CustomServicesConstants.CONNECTION_DETAILS, ImmutableList.<InputParameter>builder()
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.TARGET, true, null))
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.PORT, true, null))
                    .build())
            .build();
    
    private static Function<CustomServicesDBRESTApiPrimitive, CustomServicesRESTApiPrimitive> MAPPER = 
            new Function<CustomServicesDBRESTApiPrimitive, CustomServicesRESTApiPrimitive>() {

        @Override
        public CustomServicesRESTApiPrimitive apply(CustomServicesDBRESTApiPrimitive primitive) {
            final Map<String, List<InputParameter>> input = ImmutableMap.<String, List<InputParameter>>builder()
                    .putAll(CustomServicesDBHelper.mapInput(RESPONSE_INPUT_TYPES, primitive.getInput()))
                    .putAll(CONNECTION_OPTIONS)
                    .build();
                            
            
            final List<OutputParameter> output = CustomServicesDBHelper.mapOutput(primitive.getOutput());
            final Map<String, String> attributes = CustomServicesDBHelper.mapAttributes(ATTRIBUTES, primitive.getAttributes()); 
            return new CustomServicesRESTApiPrimitive(primitive, input, attributes, output);
        }
        
    };
            
    @Override
    public String getType() {
        return CustomServicesConstants.REST_API_PRIMITIVE_TYPE;
    }

    @Override
    public CustomServicesRESTApiPrimitive get(URI id) {
        return CustomServicesDBHelper.get(MAPPER, 
                CustomServicesDBRESTApiPrimitive.class, 
                primitiveManager, 
                id);
    }

    @Override
    public CustomServicesRESTApiPrimitive create(final CustomServicesPrimitiveCreateParam param) {
        return CustomServicesDBHelper.create(MAPPER, 
                CustomServicesDBRESTApiPrimitive.class,
                CustomServicesDBNoResource.class, 
                primitiveManager,
                new Function<CustomServicesPrimitiveCreateParam, StringSetMap>() {
                    @Override
                    public StringSetMap apply(CustomServicesPrimitiveCreateParam param) {
                        return createInput(param);
                    }
                },
                CustomServicesDBHelper.createAttributesFunction(ATTRIBUTES),
                param);
    }

    @Override
    public CustomServicesRESTApiPrimitive update(final URI id, final CustomServicesPrimitiveUpdateParam param) {
        return CustomServicesDBHelper.update(MAPPER, 
                CustomServicesDBRESTApiPrimitive.class, 
                CustomServicesDBNoResource.class, 
                primitiveManager, 
                client, 
                param, 
                new Function<UpdatePrimitive<CustomServicesDBRESTApiPrimitive>, StringSetMap>() {

                    @Override
                    public StringSetMap apply(final UpdatePrimitive<CustomServicesDBRESTApiPrimitive> update) {
                        return updateInput(update);
                    }
                },
                CustomServicesDBHelper.updateAttributesFunction(ATTRIBUTES),
                id);
    }

    @Override
    public void deactivate(final URI id) {
        CustomServicesDBHelper.deactivate(CustomServicesDBRESTApiPrimitive.class, primitiveManager, client, id);
    }

    @Override
    public List<URI> list() {
        return CustomServicesDBHelper.list(CustomServicesDBRESTApiPrimitive.class, client);
    }

    @Override
    public String getPrimitiveModel() {
        return CustomServicesDBRESTApiPrimitive.class.getSimpleName();
    }

    @Override
    public Iterator<CustomServicesPrimitiveRestRep> bulk(final Collection<URI> ids) {
        return CustomServicesDBHelper.bulk(ids, CustomServicesRESTApiPrimitive.class, 
                CustomServicesDBRESTApiPrimitive.class, dbClient, MAPPER);
    }
    
    @Override
    public boolean hasResource() {
        return false;
    }
    
    private StringSetMap createInput(final CustomServicesPrimitiveCreateParam param) {
        final StringSetMap input = CustomServicesDBHelper.createInput(REQUEST_INPUT_TYPES, param.getInput());
        final Set<String> pathParams = getPathParams(param.getAttributes().get(CustomServicesConstants.PATH));
        final Set<String> bodyParams = getBodyParams(param.getAttributes().get(CustomServicesConstants.BODY));
        
        final StringSet inputParams = new StringSet();
        inputParams.addAll(pathParams);
        inputParams.addAll(bodyParams);
        
        input.put(CustomServicesConstants.INPUT_PARAMS.toString(), inputParams);
        
        return input;
        
    }
    
    private static StringSetMap updateInput(final UpdatePrimitive<CustomServicesDBRESTApiPrimitive> update) {
        final CustomServicesPrimitiveUpdateParam param = update.param();
        final CustomServicesDBRESTApiPrimitive primitive = update.primitive();
        final StringSetMap input = CustomServicesDBHelper.updateInput(REQUEST_INPUT_TYPES, param.getInput(), primitive);

        
        if( null != param.getAttributes() &&
           ( null != param.getAttributes().get(CustomServicesConstants.BODY) || 
             null != param.getAttributes().get(CustomServicesConstants.PATH))) {
            final StringSet updateParams = updateInputParams(
                    param.getAttributes(),
                    primitive);
            input.put(CustomServicesConstants.INPUT_PARAMS.toString(), updateParams);
        }
        
        primitive.setInput(input);
        return input;
    }
    
    private static StringSet updateInputParams( final Map<String, String> attributes, final CustomServicesDBRESTApiPrimitive primitive) {
        final StringSet inputParams = new StringSet();
        final String path = (null == attributes.get(CustomServicesConstants.PATH)) ? primitive.getAttributes().get(CustomServicesConstants.PATH) : attributes.get(CustomServicesConstants.PATH);
        final String body = (null == attributes.get(CustomServicesConstants.BODY)) ? primitive.getAttributes().get(CustomServicesConstants.BODY) : attributes.get(CustomServicesConstants.BODY);
        inputParams.addAll(getPathParams(path));
        inputParams.addAll(getBodyParams(body));
        
        final StringSet updateParams = primitive.getInput().get(CustomServicesConstants.INPUT_PARAMS) == null ? new StringSet() : primitive.getInput().get(CustomServicesConstants.INPUT_PARAMS);
        
        // Remove any existing params that are not in the new 
        // input set
        final StringSet remove = new StringSet();
        for( final String existingParam : updateParams) {
            if( !inputParams.contains(existingParam)) {
                remove.add(existingParam);
            }
        }
        ;
        updateParams.removeAll(remove);
        
        
        // Add all of the input params
        updateParams.addAll(inputParams);
        
        return updateParams;
    }

    private static Set<String> getBodyParams(final String body) {
        final Matcher m = Pattern.compile("\\$(\\w+)").matcher(body);
        final Set<String> params = new StringSet();
        while (m.find()) {
            params.add(m.group(1));     
        }
        return params;
    }

    private static Set<String> getPathParams(final String templatePath) {
        final Set<String> params = new StringSet();
        final UriTemplate template = new UriTemplate(templatePath);
        params.addAll(template.getVariableNames());
        return params;
        
    }
}
