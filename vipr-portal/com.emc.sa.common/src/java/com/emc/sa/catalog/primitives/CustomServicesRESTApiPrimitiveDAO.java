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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriTemplate;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.catalog.primitives.CustomServicesDBHelper.UpdatePrimitive;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringMap;
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
import com.emc.storageos.svcs.errorhandling.resources.APIException;
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
            .add(CustomServicesConstants.PROTOCOL)
            .add(CustomServicesConstants.METHOD)
            .add(CustomServicesConstants.AUTH_TYPE)
            .add(CustomServicesConstants.PATH)
            .add(CustomServicesConstants.BODY)
            .build();
    
    private static final Set<String> REQUEST_INPUT_TYPES = ImmutableSet.<String>builder()
            .add(CustomServicesConstants.QUERY_PARAMS)
            .add(CustomServicesConstants.HEADERS)
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
    
    private static final ImmutableMap<String, List<InputParameter>> BASIC_CREDENTIALS = ImmutableMap.<String, List<InputParameter>>builder()
            .put(CustomServicesConstants.CREDENTIALS, ImmutableList.<InputParameter>builder()
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.USER, true, null))
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.PASSWORD, true, null))
                    .build())
            .build();
    
    private static Function<CustomServicesDBRESTApiPrimitive, CustomServicesRESTApiPrimitive> MAPPER = 
            new Function<CustomServicesDBRESTApiPrimitive, CustomServicesRESTApiPrimitive>() {

        @Override
        public CustomServicesRESTApiPrimitive apply(CustomServicesDBRESTApiPrimitive primitive) {
            final ImmutableMap.Builder<String, List<InputParameter>> input = ImmutableMap.<String, List<InputParameter>>builder()
                    .putAll(CustomServicesDBHelper.mapInput(RESPONSE_INPUT_TYPES, primitive.getInput()))
                    .putAll(CONNECTION_OPTIONS);
            if( null != primitive.getAttributes().get(CustomServicesConstants.AUTH_TYPE)
                    && primitive.getAttributes().get(CustomServicesConstants.AUTH_TYPE).toUpperCase().equals(CustomServicesConstants.AuthType.BASIC.name())) {
                input.putAll(BASIC_CREDENTIALS);
            }
                            
            final List<OutputParameter> output = CustomServicesDBHelper.mapOutput(primitive.getOutput());
            final Map<String, String> attributes = CustomServicesDBHelper.mapAttributes(ATTRIBUTES, primitive.getAttributes()); 
            return new CustomServicesRESTApiPrimitive(primitive, input.build(), attributes, output);
        }
        
    };
    
    private static Function<CustomServicesDBRESTApiPrimitive, CustomServicesRESTApiPrimitive> EXPORT_MAPPER = 
            new Function<CustomServicesDBRESTApiPrimitive, CustomServicesRESTApiPrimitive>() {

        @Override
        public CustomServicesRESTApiPrimitive apply(CustomServicesDBRESTApiPrimitive primitive) {
            final Map<String, List<InputParameter>> input = CustomServicesDBHelper.mapInput(RESPONSE_INPUT_TYPES, primitive.getInput());               
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
                new Function<CustomServicesPrimitiveCreateParam, StringMap>() {
                    @Override
                    public StringMap apply(CustomServicesPrimitiveCreateParam param) {
                        return createAttributes(param);
                    }
                },
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
                new Function<UpdatePrimitive<CustomServicesDBRESTApiPrimitive>, StringMap>() {

                    @Override
                    public StringMap apply(final UpdatePrimitive<CustomServicesDBRESTApiPrimitive> update) {
                        return updateAttributes(update);
                    }
                },
                id, null);
    }

    @Override
    public void deactivate(final URI id) {
        CustomServicesDBHelper.deactivate(CustomServicesDBRESTApiPrimitive.class, primitiveManager, client, id, CustomServicesDBNoResource.class, null);
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
    public boolean importPrimitive(final CustomServicesPrimitiveRestRep operation) {
        return CustomServicesDBHelper.importDBPrimitive(CustomServicesDBRESTApiPrimitive.class, operation, client);
    }
    
    @Override
    public CustomServicesRESTApiPrimitive export(URI id) {
        return CustomServicesDBHelper.exportDBPrimitive(CustomServicesDBRESTApiPrimitive.class, id, primitiveManager, EXPORT_MAPPER);
    }
    
    @Override
    public boolean hasResource() {
        return false;
    }
    
    private StringMap createAttributes(final CustomServicesPrimitiveCreateParam param) {
        final CustomServicesConstants.RestMethods method = CustomServicesConstants.RestMethods.valueOf(param.getAttributes().get(CustomServicesConstants.METHOD).toUpperCase());
        
        ArgValidator.checkFieldForValueFromEnum(method, CustomServicesConstants.METHOD, EnumSet.allOf(CustomServicesConstants.RestMethods.class));
        
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
 
        if( !StringUtils.isEmpty(param.getAttributes().get(CustomServicesConstants.BODY))) {
            switch(method) {
                case GET:
                    throw APIException.badRequests.invalidParameterValueWithExpected(CustomServicesConstants.BODY, param.getAttributes().get(CustomServicesConstants.BODY), "");
                default:
            }
        } else if (!param.getAttributes().containsKey(CustomServicesConstants.BODY)) {
            builder.put(CustomServicesConstants.BODY, StringUtils.EMPTY);
        }
        builder.putAll(param.getAttributes());
        return CustomServicesDBHelper.createAttributes(ATTRIBUTES, builder.build());
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
    
    public static StringMap updateAttributes(final UpdatePrimitive<CustomServicesDBRESTApiPrimitive> update) {
        final Map<String, String> param = update.param().getAttributes();
        if( null == param ) {
            return update.primitive().getAttributes();
        }
        
        //If trying to change the body or the method validate that the combination makes sense
        if(param.containsKey(CustomServicesConstants.METHOD) || param.containsKey(CustomServicesConstants.BODY)) {
            final String methodStr = param.containsKey(CustomServicesConstants.METHOD) ? 
                    param.get(CustomServicesConstants.METHOD) : update.primitive().getAttributes().get(CustomServicesConstants.METHOD);
            final CustomServicesConstants.RestMethods method = 
                    CustomServicesConstants.RestMethods.valueOf(methodStr.toUpperCase());
            final String body = param.containsKey(CustomServicesConstants.BODY) ? 
                    param.get(CustomServicesConstants.BODY) : update.primitive().getAttributes().get(CustomServicesConstants.BODY);
            
            ArgValidator.checkFieldForValueFromEnum(method, CustomServicesConstants.METHOD, EnumSet.allOf(CustomServicesConstants.RestMethods.class));
                    
            switch(method) {
                case GET:
                    if(!StringUtils.isEmpty(body)) {
                        throw APIException.badRequests.invalidParameterValueWithExpected(CustomServicesConstants.BODY, body, ""); 
                    }
                    break;
                default:
            }
        }
  
        return CustomServicesDBHelper.updateAttributes(ATTRIBUTES, update.param().getAttributes(), update.primitive());
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
        final Set<String> params = new StringSet();
        if(StringUtils.isEmpty(body)) {
            return params;
        }
        final Matcher m = Pattern.compile("\\$(\\w+)").matcher(body);
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
