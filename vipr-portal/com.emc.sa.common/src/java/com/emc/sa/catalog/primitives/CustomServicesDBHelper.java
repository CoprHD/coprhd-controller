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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam.InputCreateList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep.Attribute;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep.InputGroup;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.model.customservices.InputParameterRestRep;
import com.emc.storageos.model.customservices.InputUpdateParam;
import com.emc.storageos.model.customservices.InputUpdateParam.InputUpdateList;
import com.emc.storageos.model.customservices.OutputParameterRestRep;
import com.emc.storageos.model.customservices.OutputUpdateParam;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.db.CustomServicesDBPrimitiveType;
import com.emc.storageos.primitives.db.CustomServicesDBResourceType;
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
import com.google.common.collect.Sets;

/**
 * Helper class to access primitives and resources that are stored in the
 * database
 *
 */
public final class CustomServicesDBHelper {

    public static final List<NamedElement> EMPTY_ELEMENT_LIST = Collections.emptyList();
    @SuppressWarnings("rawtypes")
    private static final Class<Map> RESOURCE_ATTRIBUTES_ARG = Map.class;

    private CustomServicesDBHelper() {
    }

    /**
     * Given a DB column family and an ID get the primitive from the database and return it
     * as a primitive type java object
     * 
     * @param mapper The primitive mapper
     * @param clazz the column family java class
     * @param primitiveManager The database access component
     * @param id The id of the primitive
     * @return The primitive type java object
     */
    public static <DBModel extends CustomServicesDBPrimitive, T extends CustomServicesDBPrimitiveType> T get(
            final Function<DBModel, T> mapper,
            final Class<DBModel> clazz,
            final CustomServicesPrimitiveManager primitiveManager,
            final URI id) {
        final DBModel primitive = primitiveManager.findById(clazz, id);
        return primitive == null ? null : mapper.apply(primitive);
    }

    /**
     * Given a creation param, DB column family, and primitive type save a new primitive to the DB
     * and return the primitive type java object
     * 
     * @param mapper The primitive mapper
     * @param dbModel The database column family java class
     * @param resourceType The resource
     * @param primitiveManager The database access component
     * @param param The primitive creation param
     * @return The primitive type java object
     */
    public static <DBModel extends CustomServicesDBPrimitive, T extends CustomServicesDBPrimitiveType> T create(
            final Function<DBModel, T> mapper,
            final Class<DBModel> dbModel,
            final Class<? extends CustomServicesDBResource> resourceType,
            final CustomServicesPrimitiveManager primitiveManager,
            final Function<CustomServicesPrimitiveCreateParam, StringSetMap> createInputFunc,
            final Function<CustomServicesPrimitiveCreateParam, StringMap> createAttributesFunc,
            final CustomServicesPrimitiveCreateParam param) {
        final DBModel primitive;
        try {
            primitive = dbModel.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create custom services primitive: " + dbModel.getSimpleName());
        }
        primitive.setId(URIUtil.createId(primitive.getClass()));

        if (StringUtils.isNotBlank(param.getName())) {
            checkDuplicateLabel(param.getName().trim(), primitiveManager, dbModel);
            primitive.setLabel(param.getName().trim());
        } else {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("name");
        }
        primitive.setFriendlyName(param.getFriendlyName());
        primitive.setDescription(param.getDescription());

        if (!resourceType.isAssignableFrom(CustomServicesDBNoResource.class)) {
            if (param.getResource() == null) {
                throw APIException.badRequests.requiredParameterMissingOrEmpty("resource");
            }
            final CustomServicesDBResource resource = primitiveManager.findResource(resourceType, param.getResource());
            if (null == resource) {
                throw APIException.notFound.unableToFindEntityInURL(param.getResource());
            }
            if (resource.getInactive()) {
                throw APIException.notFound.entityInURLIsInactive(param.getResource());
            }
            primitive.setResource(new NamedURI(resource.getId(), resource.getLabel()));
        } else if (null != param.getResource()) {
            throw APIException.badRequests.invalidParameter("resource", param.getResource().toString());
        }
        primitive.setAttributes(createAttributesFunc.apply(param));
        primitive.setInput(createInputFunc.apply(param));
        primitive.setOutput(createOutput(param.getOutput()));

        primitiveManager.save(primitive);
        return mapper.apply(primitive);
    }

    /**
     * Given an ID, update param, DB column family, and primitive type save a new primitive to the DB
     * and return the primitive type java object
     * 
     * @param mapper The primitive mapper
     * @param dbModel The DB column family class
     * @param resourceType The resource
     * @param primitiveManager The database access component
     * @param client The model client
     * @param param The primitive update param
     * @param id The id of the primitive to update
     * 
     * @return The primitive type java object
     */
    public static <DBModel extends CustomServicesDBPrimitive, T extends CustomServicesDBPrimitiveType> T update(
            final Function<DBModel, T> mapper,
            final Class<DBModel> dbModel,
            final Class<? extends CustomServicesDBResource> resourceType,
            final CustomServicesPrimitiveManager primitiveManager,
            final ModelClient client,
            final CustomServicesPrimitiveUpdateParam param,
            final Function<UpdatePrimitive<DBModel>, StringSetMap> updateInputFunc,
            final Function<UpdatePrimitive<DBModel>, StringMap> updateAttributesFunc,
            final URI id, final Class<? extends CustomServicesDBResource> referencedByresourceType) {
        final DBModel primitive = primitiveManager.findById(dbModel, id);

        if (null == primitive) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        } else if (primitive.getInactive()) {
            throw APIException.notFound.entityInURLIsInactive(id);
        }

        checkNotInUse(client, id, primitive);

        if (StringUtils.isNotBlank(param.getName())) {
            final String label = param.getName().trim();
            if (!label.equalsIgnoreCase(primitive.getLabel())) {
                checkDuplicateLabel(label, primitiveManager, dbModel);
                primitive.setLabel(label);
            }
        }

        if (null != param.getFriendlyName()) {
            primitive.setFriendlyName(param.getFriendlyName());
        }
        if (null != param.getDescription()) {
            primitive.setDescription(param.getDescription());
        }

        final NamedURI oldResourceId = primitive.getResource();
        final CustomServicesDBResource resource;
        if (param.getResource() == null) {
            resource = null;
        } else if (!resourceType.isAssignableFrom(CustomServicesDBNoResource.class)) {
            resource = primitiveManager.findResource(resourceType, param.getResource());
            if (null == resource) {
                throw APIException.notFound.unableToFindEntityInURL(param.getResource());
            }
            if (resource.getInactive()) {
                throw APIException.notFound.entityInURLIsInactive(param.getResource());
            }
            primitive.setResource(new NamedURI(resource.getId(), resource.getLabel()));
        } else {
            throw APIException.badRequests.invalidParameter("resource", param.getResource().toString());
        }

        final UpdatePrimitive<DBModel> updatePrimitive = new UpdatePrimitive<DBModel>(param, primitive);

        updateAttributesFunc.apply(updatePrimitive);

        updateInputFunc.apply(updatePrimitive);
        updateOutput(param.getOutput(), primitive);

        client.save(primitive);

        // check and delete the old resource if there are no primitives referencing it
        // check that the resource is not referenced by other primitives
        if (null != resource && null != oldResourceId
                && null == checkResourceNotReferenced(dbModel, CustomServicesDBPrimitive.RESOURCE, client,
                        oldResourceId.getURI(), resource)) {

            // Find all the associated inventory files if exist for the old resource and delete the associated inventory files if exist
            deleteReferencedInventoryResources(oldResourceId.getURI(), primitiveManager, client, referencedByresourceType);

            // delete old resource (after updating the inventory, if any exists)
            client.delete(primitiveManager.findResource(resourceType, oldResourceId.getURI()));
        }

        return mapper.apply(primitive);

    }

    public static StringMap updateAttributes(final Set<String> attributeKeys,
            final Map<String, String> attributes,
            final CustomServicesDBPrimitive primitive) {
        final StringMap update = primitive.getAttributes() == null ? new StringMap() : primitive.getAttributes();
        if (attributes != null) {
            for (final Entry<String, String> entry : attributes.entrySet()) {
                if (!attributeKeys.contains(entry.getKey())) {
                    throw APIException.badRequests.invalidParameter("attributes", entry.getKey());
                }
                update.put(entry.getKey(), entry.getValue());
            }
            primitive.setAttributes(update);
        }
        return update;
    }

    /**
     * @param param
     * @param primitive
     * @return
     */
    public static StringSetMap updateInput(final Set<String> inputTypes,
            final InputUpdateParam param,
            final CustomServicesDBPrimitive primitive) {
        final StringSetMap update = primitive.getInput() == null ? new StringSetMap() : primitive.getInput();
        if (param != null) {
            addInput(inputTypes, param.getAdd(), update);
            removeInput(inputTypes, param.getRemove(), update);
            primitive.setInput(update);
        }
        return update;
    }

    private static void addInput(final Set<String> keys, final Map<String, InputUpdateList> map,
            final StringSetMap update) {
        if (null == map) {
            return;
        }

        for (final Entry<String, InputUpdateList> entry : map.entrySet()) {
            if (!keys.contains(entry.getKey())) {
                throw APIException.badRequests.invalidParameter("input", entry.getKey());
            }
            if (null != entry.getValue().getInput()) {
                final StringSet add = new StringSet();
                add.addAll(entry.getValue().getInput());
                update.put(entry.getKey(), add);
            }
        }
    }

    private static void removeInput(final Set<String> keys, final Map<String, InputUpdateList> remove,
            final StringSetMap update) {
        if (null == remove) {
            return;
        }
        for (final Entry<String, InputUpdateList> entry : remove.entrySet()) {
            if (null != entry.getValue().getInput()) {
                for (final String param : entry.getValue().getInput()) {
                    update.remove(entry.getKey(), param);
                }
            }
        }
    }

    /**
     * Given an output update parameter update the output key set for the primitive
     * 
     * @param param OutputUpdateParam that contains the keys to add or remove
     * @param primitive The primitive instance to update
     */
    private static void updateOutput(final OutputUpdateParam param, final CustomServicesDBPrimitive primitive) {
        if (null == param) {
            return;
        }
        final StringSet output = primitive.getOutput() == null ? new StringSet() : primitive.getOutput();

        addOutputKeys(param.getAdd(), output);

        removeOutputKeys(param.getRemove(), output);

        primitive.setOutput(output);
    }

    /**
     * Add a given list of output keys to a the set
     * 
     * @param add List of keys to add
     * @param output updated StringSet
     */
    private static void addOutputKeys(final List<String> add, final StringSet output) {
        if (null == add) {
            return;
        }

        for (final String addOutput : add) {
            output.add(addOutput);
        }
    }

    /**
     * Remove a given list of output keys from the given set
     * 
     * @param rm The list of keys to remove
     * @param output The updated StringSet
     */
    private static void removeOutputKeys(final List<String> rm, final StringSet output) {
        if (null == rm) {
            return;
        }

        for (final String rmOutput : rm) {
            output.remove(rmOutput);
        }
    }

    /**
     * Delete the inventory files assciated with the resource files
     *
     * @param resourceId The resourceId to search for inventory files
     * @param primitiveManager The database access component
     * @param client The model client
     * @param referencedByresourceType The DB component which is the inventory resource to delete
     */
    private static void deleteReferencedInventoryResources(final URI resourceId,
            final CustomServicesPrimitiveManager primitiveManager,
            final ModelClient client,
            final Class<? extends CustomServicesDBResource> referencedByresourceType) {
        if (null != resourceId && null != referencedByresourceType) {
            final List<NamedElement> refResource = listResources(referencedByresourceType, client,
                    CustomServicesDBResource.PARENTID, resourceId.toString());
            for (final NamedElement eachResource : refResource) {
                final CustomServicesDBResource refDBResource = primitiveManager.findResource(referencedByresourceType,
                        eachResource.getId());
                // delete the associated inventory files if exist for the DB model
                client.delete(refDBResource);
            }
        }
    }

    /**
     * Deactivate primitive with the given ID
     * 
     * @param clazz The database column family class
     * @param primitiveManager The database access component
     * @param client The model client
     * @param id The ID of the primitive to deactivate
     */
    public static void deactivate(final Class<? extends CustomServicesDBPrimitive> clazz,
            final CustomServicesPrimitiveManager primitiveManager,
            final ModelClient client,
            final URI id, final Class<? extends CustomServicesDBResource> resourceType,
            final Class<? extends CustomServicesDBResource> referencedByresourceType) {
        final CustomServicesDBPrimitive primitive = primitiveManager.findById(clazz, id);
        if (null == primitive) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }

        checkNotInUse(client, id, primitive);
        client.delete(primitive);

        if (!resourceType.isAssignableFrom(CustomServicesDBNoResource.class)) {
            final CustomServicesDBResource resource = primitiveManager.findResource(resourceType, primitive.getResource().getURI());
            if (null == resource) {
                throw APIException.notFound.unableToFindEntityInURL(primitive.getResource().getURI());
            }
            // check that the resource is not referenced by other primitives
            if (null != resource && null == checkResourceNotReferenced(clazz, CustomServicesDBPrimitive.RESOURCE, client,
                    primitive.getResource().getURI(), resource)) {

                // Find all the associated inventory files if exist for the DB model and delete the associated inventory files if exist, for
                // the resource DB model
                deleteReferencedInventoryResources(primitive.getResource().getURI(), primitiveManager, client, referencedByresourceType);

                // then delete the resource
                client.delete(resource);
            }
        }
    }

    /**
     * Get a list of IDs of the given database column family
     * 
     * @param clazz The database column family class
     * @param client The model client
     * @return A list of IDs of primitives
     */
    public static List<URI> list(final Class<? extends CustomServicesDBPrimitive> clazz, final ModelClient client) {
        return client.findByType(clazz);
    }

    /**
     * Get a list of IDs of the given database column family, filter if needed.
     *
     * @param clazz The database column family class
     * @param client The model client
     * @param columnName The filterId's columnName
     * @param filterByReferenceId The filterId
     * @return A list of IDs of primitive resource
     */
    public static <T extends CustomServicesDBResource> List<NamedElement> listResources(
            final Class<? extends CustomServicesDBResource> clazz, final ModelClient client,
            final String columnName,
            final String filterByReferenceId) {
        if (StringUtils.isBlank(filterByReferenceId)) {
            return client.customServicesPrimitiveResources().list(clazz);
        } else {
            return client.customServicesPrimitiveResources().listAllResourceByRefId(clazz,
                    columnName, filterByReferenceId);
        }
    }

    public static <T extends CustomServicesPrimitiveResourceType> T getResource(
            final Class<T> type,
            final Class<? extends CustomServicesDBResource> clazz,
            final CustomServicesPrimitiveManager primitiveManager,
            final URI id) {
        final CustomServicesDBResource resource = primitiveManager.findResource(clazz, id);
        return null == resource ? null : makeResourceType(type, resource);

    }

    /**
     * Map input of a given primitive from a StringSet map to the input parameter map
     * 
     * @param inputTypes A primitive instance inputTypes
     * @return An input parameter map
     */
    public static Map<String, List<InputParameter>> mapInput(final Set<String> inputTypes,
            final StringSetMap inputMap) {
        final Builder<String, List<InputParameter>> input = ImmutableMap.<String, List<InputParameter>> builder();
        if (null != inputMap) {
            for (final Entry<String, AbstractChangeTrackingSet<String>> inputGroup : inputMap.entrySet()) {
                input.put(validateInputGroup(inputGroup.getKey(), inputTypes), mapInputParameters(inputGroup.getValue()));
            }
        }
        return input.build();
    }

    /**
     * @param inputGroups
     * @return
     */
    private static StringSetMap mapInput(Map<String, InputGroup> inputGroups) {
        final StringSetMap input = new StringSetMap();
        if (null != inputGroups) {
            for (final Entry<String, InputGroup> inputGroup : inputGroups.entrySet()) {
                final StringSet set = new StringSet();
                for (final InputParameterRestRep param : inputGroup.getValue().getInputGroup()) {
                    set.add(param.getName());
                }
                input.put(inputGroup.getKey(), set);
            }
        }
        return input;
    }

    /**
     * Get an input type given a string and the supported set of input types
     * 
     * @param key The name of the input type as a string
     * @param inputTypes The supported input types
     * @return The InputType enum of the given string
     */
    private static String validateInputGroup(final String key, final Set<String> inputTypes) {
        if (key == null || !inputTypes.contains(key)) {
            throw new IllegalStateException("Unknown input type: " + key);
        }
        return key;
    }

    /**
     * Convert an AbstractChangeTrackingSet of strings to a List of InputParameters
     * 
     * @param inputSet The set of strings to convert
     * @return A list of input parameters
     */
    private static final List<InputParameter> mapInputParameters(final AbstractChangeTrackingSet<String> inputSet) {
        final ImmutableList.Builder<InputParameter> parameters = ImmutableList.<InputParameter> builder();
        if (null != inputSet) {
            for (final String parameter : inputSet) {
                parameters.add(makeInputParameter(parameter));
            }
        }
        return parameters.build();
    }

    /**
     * Convert a given primitive instance's out from a StringSet into a list of OutputParameter
     * 
     * @param outputSet The primitive instance outputSet
     * @return The converted List of output parameters
     */
    public static List<OutputParameter> mapOutput(final StringSet outputSet) {
        final ImmutableList.Builder<OutputParameter> output = ImmutableList.<OutputParameter> builder();
        if (null != outputSet) {
            for (final String outputName : outputSet) {
                output.add(makeOutputParameter(outputName));
            }
        }
        return output.build();
    }

    /**
     * Convert a list of output parameters to a StringSet
     * 
     * @param output
     * @return
     */
    private static StringSet mapOutput(List<OutputParameterRestRep> output) {
        final StringSet set = new StringSet();
        if (null != output) {
            for (final OutputParameterRestRep param : output) {
                set.add(param.getName());
            }
        }
        return set;
    }

    /**
     * Convert a given primitive instance's StringMap attributes to a Map
     * 
     * @param attributeKeys The primitive instance attributeKeys
     * @param attributes The primitive instance attributes
     * @return The converted attributes map
     */
    static Map<String, String> mapAttributes(final Set<String> attributeKeys, final StringMap attributes) {
        ImmutableMap.Builder<String, String> attributeMap = ImmutableMap.<String, String> builder();
        if (null != attributes) {
            for (final Entry<String, String> attribute : attributes.entrySet()) {
                if (!attributeKeys.contains(attribute.getKey())) {
                    throw new IllegalStateException("Unknow attribute key: " + attribute.getKey());
                }
                attributeMap.put(attribute.getKey(), attribute.getValue());
            }
        }
        return attributeMap.build();
    }

    /**
     * Convert a given map of attributes to a StringMap
     * 
     * @param attributes
     * @return
     */
    private static StringMap mapAttributes(final Map<String, String> attributes) {
        final StringMap map = new StringMap();
        if (null != map) {
            map.putAll(attributes);
        }
        return map;
    }

    /**
     * Make a string key into an InputParameter object
     * 
     * @param parameter The name of the input parameter
     * @return The input parameter
     */
    private static InputParameter makeInputParameter(String parameter) {
        return new BasicInputParameter.StringParameter(parameter, false, null);
    }

    /**
     * Convert a string key into an OutputParameter
     * 
     * @param name The name of the output parameter
     * @return The output parameter
     */
    private static OutputParameter makeOutputParameter(String name) {
        return new StringOutputParameter(name);
    }

    private static StringSet createOutput(List<String> list) {
        final StringSet output = new StringSet();
        if (list != null) {
            output.addAll(list);
        }
        return output;
    }

    public static StringMap createAttributes(final Set<String> attributeKeys,
            final Map<String, String> attributes) {
        final StringMap attributesMap = new StringMap();

        if (null != attributes) {
            if (!attributes.keySet().containsAll(attributeKeys)) {
                throw APIException.badRequests.invalidParameter("attributes",
                        "missing: " + Sets.difference(attributeKeys, attributes.keySet()));
            }

            for (final Entry<String, String> attribute : attributes.entrySet()) {
                if (attributeKeys.contains(attribute.getKey())) {
                    attributesMap.put(attribute.getKey(), attribute.getValue());
                } else {
                    throw BadRequestException.badRequests.invalidParameter("attributes", attribute.getKey());
                }
            }
        } else if (!attributeKeys.isEmpty()) {
            throw APIException.badRequests.invalidParameter("attributes", "missing: " + attributeKeys);
        }

        return attributesMap;
    }

    public static StringSetMap createInput(final Set<String> keys, final Map<String, InputCreateList> map) {
        final StringSetMap inputMap = new StringSetMap();
        if (null != map) {
            for (Entry<String, InputCreateList> entry : map.entrySet()) {
                if (keys.contains(entry.getKey())) {
                    inputMap.put(entry.getKey(), createInputStringSet(entry.getValue().getInput()));
                } else {
                    throw APIException.badRequests.unknownParameter("input", entry.getKey());
                }
            }
        }
        return inputMap;
    }

    private static AbstractChangeTrackingSet<String> createInputStringSet(
            List<String> from) {
        final StringSet inputStringSet = new StringSet();
        if (from != null) {
            inputStringSet.addAll(from);
        }
        return inputStringSet;
    }

    /**
     * Check if a primitive is in use in a workflow. Throw a bad request exception if it is being used.
     * 
     * @param client ModelClient
     * @param id The ID of the primitive
     * @param primitive The primitive instance
     */
    private static <T extends CustomServicesDBPrimitive> void checkNotInUse(
            final ModelClient client, final URI id, final T primitive) {
        final List<NamedElement> workflows = client.customServicesWorkflows().getByPrimitive(id);
        if (CollectionUtils.isNotEmpty(workflows)) {
            final StringBuilder wfName = new StringBuilder();
            String prefix = ". Workflows used : ";
            for (final NamedElement eachWf : workflows) {
                wfName.append(prefix);
                prefix = ", ";
                wfName.append(eachWf.getName());
            }

            throw APIException.badRequests.resourceHasActiveReferencesWithType(primitive.getClass().getSimpleName(), primitive.getLabel(),
                    CustomServicesWorkflow.class.getSimpleName() + wfName.toString());
        }
    }

    private static <T extends ModelObject> void checkDuplicateLabel(final String name, final CustomServicesPrimitiveManager client,
            final Class<T> clazz) {
        final List<T> ids = client.getByLabel(clazz, name);
        if (CollectionUtils.isNotEmpty(ids)) {
            throw APIException.badRequests.duplicateLabel(name);
        }
    }

    /**
     * Given a primitive resource type java class and the database column family instance
     * 
     * @param type The type of the resource java class
     * @param resource The resource database column family instance
     * @return The resource type class instance
     */
    private static <T extends CustomServicesPrimitiveResourceType> T makeResourceType(final Class<T> type,
            final CustomServicesDBResource resource) {
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

    /**
     * Given a resource database instance convert its StringSetMap of attributes to a Map
     * 
     * @param resource The resource database instance
     * @return The Map representation of the attributes
     */
    private static Map<String, Set<String>> mapResourceAttributes(final CustomServicesDBResource resource) {
        final ImmutableMap.Builder<String, Set<String>> attributes = ImmutableMap.<String, Set<String>> builder();
        if (resource.getAttributes() != null) {
            for (final Entry<String, AbstractChangeTrackingSet<String>> entry : resource.getAttributes().entrySet()) {
                attributes.put(entry);
            }
        }
        return attributes.build();
    }

    /**
     * Given a List of attributes create a StringSetMap
     * 
     * @param attributes list of Attribute
     * @return StringSetMap representation of the attributes
     */
    private static StringSetMap mapResourceAttributes(List<Attribute> attributes) {
        final StringSetMap map = new StringSetMap();
        if (null != attributes && !attributes.isEmpty()) {
            for (final Attribute attribute : attributes) {
                final StringSet set = new StringSet();
                set.addAll(attribute.getValues());
                map.put(attribute.getName(), set);
            }
        }
        return map;
    }

    /**
     * Given the name, attributes, parentId and bytes of a resource save the database instance
     *
     * @param type The class type of the resource
     * @param dbModel The database column family of the resource
     * @param primitiveManager The database access component
     * @param name The name of the new resource
     * @param stream The bytes of the resource
     * @param attributes The attributes of the resource
     * @param parentId The parentId of the resource
     * @return The java object instance of this resource
     */
    public static <T extends CustomServicesDBResourceType<?>> T createResource(
            final Class<T> type,
            final Class<? extends CustomServicesDBResource> dbModel,
            final CustomServicesPrimitiveManager primitiveManager,
            final String name,
            final byte[] stream,
            final StringSetMap attributes, final URI parentId) {
        final CustomServicesDBResource resource;
        try {
            resource = dbModel.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create custom services primitive: " + dbModel.getSimpleName());
        }
        resource.setId(URIUtil.createId(dbModel));
        if (StringUtils.isNotBlank(name)) {
            checkDuplicateLabel(name.trim(), primitiveManager, dbModel);
            resource.setLabel(name.trim());
        } else {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("name");
        }
        resource.setAttributes(attributes);
        resource.setParentId(parentId);
        resource.setResource(Base64.encodeBase64(stream));
        primitiveManager.save(resource);
        return makeResourceType(type, resource);
    }

    /**
     * Given a resource ID and parameters update a resource in the database
     * 
     * @param dbModel The database column family of the resource
     * @param primitiveManager The database access component
     * @param id The ID of the resource
     * @param name The new name of the resource null, if no update
     * @param stream The new bytes of the resource, null if no update
     * @param attributes The new attributes of the resource, null if no update
     * @return The updated java object instance of this resource type
     */
    public static <T extends CustomServicesDBResourceType<?>> T updateResource(
            final Class<T> type,
            final Class<? extends CustomServicesDBResource> dbModel,
            final CustomServicesPrimitiveManager primitiveManager,
            final URI id,
            final String name,
            final byte[] stream,
            final StringSetMap attributes, final URI parentId, final ModelClient client,
            final Class<? extends CustomServicesDBResource> referencedByResource,
            final String referencedByResourceColumnName,
            final Class<? extends CustomServicesDBPrimitive> referencedByPrimitive, final String referencedByPrimitiveColumnName) {
        final CustomServicesDBResource resource = primitiveManager.findResource(dbModel, id);
        if (null == resource) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        } else if (resource.getInactive()) {
            throw APIException.notFound.entityInURLIsInactive(id);
        }

        if (StringUtils.isNotBlank(name)) {
            final String label = name.trim();
            if (!label.equalsIgnoreCase(resource.getLabel())) {
                checkDuplicateLabel(label, primitiveManager, dbModel);
                resource.setLabel(label);
            }
        }

        if (null != parentId) {
            resource.setParentId(parentId);
        }

        if (null != attributes || null != stream) {
            BadRequestException resourceReferencedexception = checkResourceNotReferenced(referencedByPrimitive,
                    referencedByPrimitiveColumnName,
                    client, id,
                    resource);

            if (resourceReferencedexception != null) {
                throw resourceReferencedexception;
            }

            resourceReferencedexception = checkResourceNotReferenced(referencedByResource, referencedByResourceColumnName,
                    client, id,
                    resource);

            if (resourceReferencedexception != null) {
                throw resourceReferencedexception;
            }
            resource.setAttributes(attributes);
            resource.setResource(Base64.encodeBase64(stream));
        }

        primitiveManager.save(resource);

        return makeResourceType(type, resource);
    }

    /**
     * Deactivate a resource database instance
     * 
     * @param dbModel The database column family class
     * @param primitiveManager The database access component
     * @param client The model client
     * @param id ID of the resource to deactivate
     */
    public static void deactivateResource(
            final Class<? extends CustomServicesDBResource> dbModel,
            final CustomServicesPrimitiveManager primitiveManager,
            final ModelClient client, final URI id, final Class<? extends CustomServicesDBResource> referencedByResource,
            final String referencedByResourceColumnName,
            final Class<? extends CustomServicesDBPrimitive> referencedByPrimitive, final String referencedByPrimitiveColumnName) {
        final CustomServicesDBResource resource = primitiveManager.findResource(dbModel, id);
        if (null == resource) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        BadRequestException resourceReferencedexception = checkResourceNotReferenced(referencedByPrimitive,
                referencedByPrimitiveColumnName,
                client, id,
                resource);

        if (resourceReferencedexception != null) {
            throw resourceReferencedexception;
        }

        resourceReferencedexception = checkResourceNotReferenced(referencedByResource, referencedByResourceColumnName,
                client, id,
                resource);

        if (resourceReferencedexception != null) {
            throw resourceReferencedexception;
        }

        client.delete(resource);
    }

    private static <T extends CustomServicesDBResource> BadRequestException checkResourceNotReferenced(
            final Class<? extends ModelObject> referencedByClazz, final String referencedByColumnName,
            final ModelClient client, final URI resourceId, final T resource) {

        List<NamedElement> resourceExistList = Collections.emptyList();
        final BadRequestException resourceReferencedexception = null;
        if (null != referencedByClazz) {
            resourceExistList = client.findBy(referencedByClazz, referencedByColumnName, resourceId);
            if (null != resourceExistList && !resourceExistList.isEmpty()) {
                return APIException.badRequests.resourceHasActiveReferencesWithType(resource.getClass().getSimpleName(), resourceId,
                        StringUtils.substringAfterLast(referencedByClazz.getName(), "."));
            }
        }
        return resourceReferencedexception;
    }

    /**
     * Get a bulk iterator of the primitives with given IDs
     * 
     * @param ids The list of IDs of primitives to query
     * @param type The java object type of the primitive
     * @param dbModel The database column family class of the primitive
     * @param dbClient DBClient to query the database
     * @return An Iterator of rest response entities of the given type
     */
    public static <Type extends CustomServicesDBPrimitiveType, Model extends CustomServicesDBPrimitive>
            Iterator<CustomServicesPrimitiveRestRep> bulk(
                    final Collection<URI> ids,
                    Class<Type> type,
                    Class<Model> dbModel,
                    final DbClient dbClient,
                    final Function<Model, Type> mapper) {
        return BulkList.wrapping(dbClient.queryIterativeObjects(dbModel, ids), new Function<Model, CustomServicesPrimitiveRestRep>() {

            @Override
            public CustomServicesPrimitiveRestRep apply(final Model from) {
                return CustomServicesPrimitiveMapper.map(mapper.apply(from));
            }

        }).iterator();
    }

    public static Function<CustomServicesPrimitiveCreateParam, StringSetMap> createInputFunction(final Set<String> inputTypes) {
        return new Function<CustomServicesPrimitiveCreateParam, StringSetMap>() {
            @Override
            public StringSetMap apply(CustomServicesPrimitiveCreateParam param) {
                return CustomServicesDBHelper.createInput(inputTypes, param.getInput());
            }
        };
    }

    public static Function<CustomServicesPrimitiveCreateParam, StringMap> createAttributesFunction(final Set<String> attributeKeys) {
        return new Function<CustomServicesPrimitiveCreateParam, StringMap>() {
            @Override
            public StringMap apply(CustomServicesPrimitiveCreateParam param) {
                return CustomServicesDBHelper.createAttributes(attributeKeys, param.getAttributes());
            }
        };
    }

    public static <DBModel extends CustomServicesDBPrimitive> Function<UpdatePrimitive<DBModel>, StringSetMap>
            updateInputFunction(final Set<String> inputTypes) {
        return new Function<UpdatePrimitive<DBModel>, StringSetMap>() {

            @Override
            public StringSetMap apply(final UpdatePrimitive<DBModel> update) {
                return CustomServicesDBHelper.updateInput(inputTypes, update.param().getInput(), update.primitive());
            }

        };
    }

    public static <DBModel extends CustomServicesDBPrimitive> Function<UpdatePrimitive<DBModel>, StringMap>
            updateAttributesFunction(final Set<String> attributes) {
        return new Function<UpdatePrimitive<DBModel>, StringMap>() {

            @Override
            public StringMap apply(final UpdatePrimitive<DBModel> update) {
                return updateAttributes(attributes, update.param().getAttributes(), update.primitive());
            }

        };
    }

    /**
     * Import a resource to the database
     * 
     * @param clazz the DB model class
     * @param resource The REST representation of the resource
     * @param bytes The file bytes of the resource
     * @param client The ModelClient to access the database
     * @return true if the resource was imported, false if it already exists
     */
    public static <T extends CustomServicesDBResource> boolean importResource(Class<T> clazz,
            CustomServicesPrimitiveResourceRestRep resource, byte[] bytes, final ModelClient client) {
        final T existing = client.findById(resource.getId());
        if (null == existing) {
            client.save(makeDBResource(clazz, resource, bytes));
            return true;
        } else if (existing.getInactive()) {
            client.save(updateDBResource(resource, bytes, existing));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Make a DB resource of a given type given the resource REST model and file bytes
     * 
     * @param clazz The type of resource to create
     * @param resource The REST model for the resource
     * @param bytes the file bytes
     * 
     * @return An instance of the DB model given the REST model
     */
    public static <T extends CustomServicesDBResource> T makeDBResource(final Class<T> clazz,
            final CustomServicesPrimitiveResourceRestRep resource,
            final byte[] bytes) {
        try {
            return updateDBResource(resource, bytes, clazz.newInstance());
        } catch (final InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Invalid DB model: " + clazz, e);
        }
    }

    /**
     * Update the given DB resource with fields from the REST model
     * 
     * @param resource the REST representation of the resource
     * @param bytes the file bytes of the resource
     * @param dbResource the DB instance to modify
     * @return
     */
    private static <T extends CustomServicesDBResource> T updateDBResource(
            final CustomServicesPrimitiveResourceRestRep resource,
            final byte[] bytes,
            final T dbResource) {
        dbResource.setId(resource.getId());
        dbResource.setLabel(resource.getName());
        dbResource.setInactive(false);
        dbResource.setParentId(resource.getParentId());
        dbResource.setAttributes(mapResourceAttributes(resource.getAttributes()));
        dbResource.setResource(bytes);
        return dbResource;
    }

    /**
     * Import a primitive to the database
     * 
     * @param clazz the DB model class
     * @param operation The REST representation of the primitive
     * @param client the ModelClient instance
     * @return true if the primitive was imported, false if it already exists
     */
    public static <T extends CustomServicesDBPrimitive> boolean importDBPrimitive(final Class<T> clazz,
            final CustomServicesPrimitiveRestRep operation,
            final ModelClient client) {
        final T existing = client.findById(operation.getId());
        if (null == existing) {
            client.save(makeDBPrimitive(clazz, operation));
            return true;
        } else if (existing.getInactive()) {
            client.save(updateDBPrimitive(operation, existing));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Make a DB primitive of the given type given the REST model
     * 
     * @param clazz The type of the primitive to create
     * @param operation The REST model
     * 
     * @return An instance of the DB model given the REST model
     */
    public static <T extends CustomServicesDBPrimitive> T makeDBPrimitive(final Class<T> clazz,
            final CustomServicesPrimitiveRestRep operation) {
        try {
            return updateDBPrimitive(operation, clazz.newInstance());
        } catch (final InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Invalid DB model " + clazz, e);
        }
    }

    /**
     * Update a DB primitive given the REST representation and the db model instance
     * 
     * @param operation REST representation of the primitive
     * @param model DB model instance
     * @return updated DB model instance
     */
    public static <T extends CustomServicesDBPrimitive> T updateDBPrimitive(final CustomServicesPrimitiveRestRep operation,
            final T model) {
        model.setId(operation.getId());
        model.setLabel(operation.getName());
        model.setInactive(false);
        model.setDescription(operation.getDescription());
        model.setFriendlyName(operation.getFriendlyName());
        model.setInput(mapInput(operation.getInputGroups()));
        model.setOutput(mapOutput(operation.getOutput()));
        model.setAttributes(mapAttributes(operation.getAttributes()));
        if (null != operation.getResource()) {
            model.setResource(new NamedURI(operation.getResource().getId(),
                    operation.getResource().getName()));
        }
        model.setSuccessCriteria(operation.getSuccessCriteria());

        return model;
    }

    /**
     * Get the export
     *
     * @param clazz
     * @param id
     * @param client
     * @return the
     */
    public static <DBModel extends CustomServicesDBPrimitive, T extends CustomServicesDBPrimitiveType> T exportDBPrimitive(
            final Class<DBModel> clazz,
            final URI id,
            final CustomServicesPrimitiveManager primitiveManager,
            final Function<DBModel, T> mapper) {
        final DBModel primitive = primitiveManager.findById(clazz, id);
        return primitive == null ? null : mapper.apply(primitive);
    }

    public static class UpdatePrimitive<DBModel extends CustomServicesDBPrimitive> {
        private final CustomServicesPrimitiveUpdateParam param;
        private final DBModel primitive;

        public UpdatePrimitive(final CustomServicesPrimitiveUpdateParam param, final DBModel primitive) {
            this.param = param;
            this.primitive = primitive;
        }

        public CustomServicesPrimitiveUpdateParam param() {
            return param;
        }

        public DBModel primitive() {
            return primitive;
        }
    }
}
