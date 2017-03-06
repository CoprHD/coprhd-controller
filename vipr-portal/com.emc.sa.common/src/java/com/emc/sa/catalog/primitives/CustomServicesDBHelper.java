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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.CustomServicesDBPrimitiveType;
import com.emc.storageos.primitives.CustomServicesDBResourceType;
import com.emc.storageos.primitives.CustomServicesPrimitive.InputType;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter.StringOutputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;


public final class CustomServicesDBHelper {
    
    @SuppressWarnings("rawtypes")
    private static final Class<Map> INPUT_ARG = Map.class;
    @SuppressWarnings("rawtypes")
    private static final Class<Map> ATTRIBUTES_ARG = Map.class;
    @SuppressWarnings("rawtypes")
    private static final Class<List> OUTPUT_ARG = List.class;
    @SuppressWarnings("rawtypes")
    private static final Class<Map> RESOURCE_ATTRIBUTES_ARG = Map.class;
    
    public static <T extends CustomServicesDBPrimitiveType> T get(final Class<T> type,
            final Class<? extends CustomServicesDBPrimitive> clazz,
            final CustomServicesPrimitiveManager primitiveManager,
            final URI id) {
        final CustomServicesDBPrimitive primitive = primitiveManager.findById(clazz, id);
        return primitive == null ? null : makePrimitiveType(type, primitive);
    }
    
    public static <T extends CustomServicesDBPrimitiveType> T create(final Class<T> type,
            final Class<? extends CustomServicesDBPrimitive> dbModel,
            final Class<? extends CustomServicesDBResource> resourceType,
            final CustomServicesPrimitiveManager primitiveManager,
            final CustomServicesPrimitiveCreateParam param ) {
        final CustomServicesDBPrimitive primitive;
        try {
            primitive = dbModel.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create custom services primitive: " + dbModel.getSimpleName());
        }
        primitive.setId(URIUtil.createId(primitive.getClass()));
        primitive.setLabel(param.getName());
        primitive.setFriendlyName(param.getFriendlyName());
        primitive.setDescription(param.getDescription());
        final CustomServicesDBResource resource = primitiveManager.findResource(resourceType, param.getResource());
        primitive.setResource(new NamedURI(resource.getId(), resource.getLabel()));
        final StringMap attributes = new StringMap();
        //TODO need validation e.g. playbook needs to be in resource
        if( null != param.getAttributes() ) {
            for( final Entry<String, String> attribute :  param.getAttributes().entrySet()) {
                if(primitive.attributeKeys().contains(attribute.getKey())) {
                    attributes.put(attribute.getKey(), attribute.getValue());
                } else {
                    throw BadRequestException.badRequests.invalidParameter("attributes", attribute.getKey());
                }
            }
        }
        primitive.setAttributes(attributes);
        
        final StringSetMap inputMap = new StringSetMap();
        final StringSet inputs = new StringSet();
        if( null != param.getInput() ) {
            for(String input : param.getInput()) {
                inputs.add(input);
            } 
        }
        inputMap.put(InputType.INPUT_PARAMS.toString(), inputs);
        primitive.setInput(inputMap);
        
        final StringSet output = new StringSet();
        if(param.getOutput() != null ) {
            output.addAll(param.getOutput());
        }
        primitive.setOutput(output);
        
        primitiveManager.save(primitive);
        return makePrimitiveType(type, primitive);
    }
    
    public static <T extends CustomServicesDBPrimitiveType> T update(final Class<T> type,
            final Class<? extends CustomServicesDBPrimitive> clazz,
            final Class<? extends CustomServicesDBResource> resourceType,
            final CustomServicesPrimitiveManager primitiveManager,
            final ModelClient client,
            final CustomServicesPrimitiveUpdateParam param,
            final URI id) {
        final CustomServicesDBPrimitive primitive = primitiveManager.findById(clazz, id);
        
        if( null == primitive ) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        
        checkNotInUse(client, id, primitive);
        
        if (null != param.getName()) {
            primitive.setLabel(param.getName());
        }
        if (null != param.getFriendlyName()) {
            primitive.setFriendlyName(param.getFriendlyName());
        }
        if (null != param.getDescription()) {
            primitive.setDescription(param.getDescription());
        }
        // TODO missing attributes and input updates 
        
        if (null != param.getOutput()) {
            final StringSet output = primitive.getOutput() == null ? new StringSet()
                    : primitive.getOutput();
            if (null != param.getOutput().getAdd()) {
                for (final String addOutput : param.getOutput().getAdd()) {
                    output.add(addOutput);
                }
            }
            if (null != param.getOutput().getRemove()) {
                for (final String rmOutput : param.getOutput().getRemove()) {
                    output.remove(rmOutput);
                }
            }
            primitive.setOutput(output);
        }
        return makePrimitiveType(type, primitive);
        
    }
    
    public static void deactivate(final Class<? extends CustomServicesDBPrimitive> clazz,
            final CustomServicesPrimitiveManager primitiveManager,
            final ModelClient client,
            final URI id) {
        final CustomServicesDBPrimitive primitive = primitiveManager.findById(clazz, id);
        if( null == primitive ) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        
        checkNotInUse(client, id, primitive);
        
        client.delete(primitive);
    }
    
    public static List<URI> list(final Class<? extends CustomServicesDBPrimitive> clazz, final ModelClient client) {
        return client.findByType(clazz);
    }

    public static <T extends CustomServicesPrimitiveResourceType> T getResource(
            final Class<T> type,
            final Class<? extends CustomServicesDBResource> clazz,
            final CustomServicesPrimitiveManager primitiveManager, 
            final URI id) {
        final CustomServicesDBResource resource = primitiveManager.findResource(clazz, id);
        return null == resource ? null : makeResourceType(type, resource);
        
    }
    
    private static Map<InputType, List<InputParameter>> mapInput(final CustomServicesDBPrimitive primitive) {
        final Builder<InputType, List<InputParameter>> inputMap = ImmutableMap.<InputType, List<InputParameter>>builder();
        if( null != primitive.getInput() ) {
            for(final Entry<String, AbstractChangeTrackingSet<String>> inputGroup : primitive.getInput().entrySet()) {
                inputMap.put(inputGroup(inputGroup.getKey(), primitive.inputTypes()), mapInputParameters(inputGroup.getValue()));
            }
        }
        return inputMap.build();
    }
    
    private static InputType inputGroup(final String key, final Set<String> inputTypes) {
        final InputType type = InputType.fromString(key);
        if( key == null || !inputTypes.contains(key)) {
            throw new IllegalStateException("Unknown input type: " + key);
        }
        return type;
    }
    
    private static final List<InputParameter> mapInputParameters(final AbstractChangeTrackingSet<String> inputSet) {
        final ImmutableList.Builder<InputParameter> parameters = ImmutableList.<InputParameter>builder();
        if( null != inputSet ) {
            for( final String parameter : inputSet ) {
                parameters.add(makeInputParameter(parameter));
            }
        }
        return parameters.build();
    }

    private static List<OutputParameter> mapOutput(final CustomServicesDBPrimitive primitive) {
        final ImmutableList.Builder<OutputParameter> output = ImmutableList.<OutputParameter>builder();
        if( null != primitive.getOutput()) {
            for(final String outputName : primitive.getOutput()) {
                output.add(makeOutputParameter(outputName));
            }
        }
        return output.build();
    }

    private static Map<String, String> mapAttributes(final CustomServicesDBPrimitive primitive) {
        ImmutableMap.Builder<String, String> attributeMap = ImmutableMap.<String,String>builder();
        if( null != primitive.getAttributes()) {
            for(final Entry<String, String> attribute : primitive.getAttributes().entrySet()) {
                if(!primitive.attributeKeys().contains(attribute.getKey())) {
                    throw new IllegalStateException("Unknow attribute key: " + attribute.getKey());
                }
                attributeMap.put(attribute.getKey(), attribute.getValue());
            }
        }
        return attributeMap.build();
    }

    private static InputParameter makeInputParameter(String parameter) {
        return new BasicInputParameter.StringParameter(parameter, false, null);
    }
    
    private static OutputParameter makeOutputParameter(String name) {
        return new StringOutputParameter(name);
    }
    

    private static <T extends CustomServicesDBPrimitive> void checkNotInUse(
            final ModelClient client, final URI id, final T primitive) {
        final List<NamedElement> workflows = client.customServicesWorkflows().getByPrimitive(id);
        if( null != workflows && !workflows.isEmpty()) {
            throw APIException.badRequests.resourceHasActiveReferencesWithType(primitive.getClass().getSimpleName(), id, CustomServicesWorkflow.class.getSimpleName());
        }
    }
    
    private static <T extends CustomServicesDBPrimitiveType> T makePrimitiveType(final Class<T> clazz, final CustomServicesDBPrimitive primitive) {
        Constructor<T> constructor;
        try {
            constructor = clazz.getConstructor(primitive.getClass(), INPUT_ARG, ATTRIBUTES_ARG, OUTPUT_ARG);
        } catch (final NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("Primitive type " + clazz.getSimpleName() + " is missing the constructor implementation", e);
        }
       
        try {
            return constructor.newInstance(primitive, mapInput(primitive), mapAttributes(primitive), mapOutput(primitive));
        } catch (final InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException("Primitive type " + clazz.getSimpleName() + " failed to invoke constructor", e);
        }
    }
    
    private static <T extends CustomServicesPrimitiveResourceType> T makeResourceType(final Class<T> type, final CustomServicesDBResource resource) {
        Constructor<T> constructor;
        try {
            constructor = type.getConstructor(resource.getClass(), RESOURCE_ATTRIBUTES_ARG);
        } catch (final NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("Primitive resource type " + type.getSimpleName() + " is missing the constructor implementation", e);
        }
       
        try {
            return constructor.newInstance(resource, mapResourceAttributes(resource));
        } catch (final InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException("Primitive resource type " + type.getSimpleName() + " failed to invoke constructor", e);
        }
    }

    private static Map<String, Set<String>> mapResourceAttributes(final CustomServicesDBResource resource) {
        final ImmutableMap.Builder<String, Set<String>> attributes = ImmutableMap.<String, Set<String>>builder();
        if(resource.getAttributes() != null) {
            for(final Entry<String, AbstractChangeTrackingSet<String>> entry : resource.getAttributes().entrySet()) {
                attributes.put(entry);
            }
        }
        return attributes.build();
    }

    public static <T extends CustomServicesDBResourceType<?>> T createResource(
            final Class<T> type,
            final Class<? extends CustomServicesDBResource> dbModel,
            final CustomServicesPrimitiveManager primitiveManager,
            final String name,
            final byte[] stream,
            final StringSetMap attributes) {
        final CustomServicesDBResource resource;
        try {
            resource = dbModel.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create custom services primitive: " + dbModel.getSimpleName());
        }
        resource.setId(URIUtil.createId(dbModel));
        resource.setLabel(name);
        resource.setAttributes(attributes);
        resource.setResource(Base64.encodeBase64(stream));
        primitiveManager.save(resource);
        return makeResourceType(type, resource);
    }

    public static <T extends CustomServicesDBResourceType<?>> T updateResource(
            final Class<T> type,
            final Class<? extends CustomServicesDBResource> dbModel,
            final CustomServicesPrimitiveManager primitiveManager,
            final URI id,
            final String name,
            final byte[] stream,
            final StringSetMap attributes) {
        final CustomServicesDBResource resource = primitiveManager.findResource(dbModel, id);
        if( null == resource ) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        if(null != name ) {
            resource.setLabel(name);
        }
        if(null != attributes ) {
            resource.setAttributes(attributes);
        }
        if(null != stream ) {
            resource.setResource(Base64.encodeBase64(stream));
        }
        primitiveManager.save(resource);
        return makeResourceType(type, resource);
    }

    public static void deactivateResource(
            final Class<? extends CustomServicesDBResource> dbModel,
            final CustomServicesPrimitiveManager primitiveManager,
            final ModelClient client, final URI id) {
        final CustomServicesDBResource resource = primitiveManager.findResource(dbModel, id);
        if( null == resource ) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        
        // TODO add in use check
        
        client.delete(resource);
    }

    public static <Type extends CustomServicesDBPrimitiveType, Model extends CustomServicesDBPrimitive> Iterator<CustomServicesPrimitiveRestRep> bulk(
            final Collection<URI> ids, 
            Class<Type> type,
            Class<Model> dbModel,
            final DbClient dbClient) {
        return BulkList.wrapping(dbClient.queryIterativeObjects(dbModel, ids), new Function<Model, CustomServicesPrimitiveRestRep>() {

            @Override
            public CustomServicesPrimitiveRestRep apply(final Model from) {
                return CustomServicesPrimitiveMapper.map(makePrimitiveType(type, from));
            }
            
        }).iterator();
    }
}
