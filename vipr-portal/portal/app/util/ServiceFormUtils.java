/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldGroupRestRep;
import com.emc.vipr.model.catalog.ServiceFieldModalRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceFieldTableRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import controllers.api.AssetOptionsApi;

public class ServiceFormUtils {
    /**
     * Creates a mapping of field name to {@link AssetFieldDescriptor} for all asset fields in the provided service.
     * 
     * @param service
     *            the service descriptor.
     * @return the map of field name->AssetFieldDescriptor.
     */
    public static Map<String, AssetFieldDescriptor> createAssetFieldDescriptors(ServiceDescriptorRestRep service) {
        Map<String, ServiceFieldRestRep> providedFields = Maps.newHashMap();
        Map<String, AssetFieldDescriptor> assetFields = createAssetFieldsFromItems(service.getItems(), providedFields);
        calculateReverseDependencies(assetFields);
        return assetFields;
    }

    /**
     * Reverses the dependencies of the asset fields, calculating {@link AssetFieldDescriptor#fieldsThatDependOnUs}.
     * 
     * @param assetFields
     *            the map of all asset fields.
     */
    private static void calculateReverseDependencies(Map<String, AssetFieldDescriptor> assetFields) {
        for (Map.Entry<String, AssetFieldDescriptor> entry : assetFields.entrySet()) {
            String name = entry.getKey();
            for (String dependencyName : entry.getValue().fieldsWeDependOn) {
                AssetFieldDescriptor dependency = assetFields.get(dependencyName);
                if (dependency != null) {
                    dependency.fieldsThatDependOnUs.add(name);
                }
            }
        }
    }

    /**
     * Creates the asset fields for the container and all its children.
     * 
     * @param container
     *            the service item container.
     * @param providedFields
     *            the asset fields provided by the parent, mapped by asset type.
     * @return the map of field name->AssetFieldDescriptor.
     */
    private static Map<String, AssetFieldDescriptor> createAssetFieldsFromItems(Collection<? extends ServiceItemRestRep> items,
            Map<String, ServiceFieldRestRep> providedFields) {
        List<ServiceFieldRestRep> fields = ServiceFieldRestRep.getAssetFields(items);
        Map<String, AssetFieldDescriptor> assetFields = createAssetFields(fields, providedFields);

        // Add the container fields to the provided fields for the children
        providedFields = addFieldsByType(fields, providedFields);
        for (ServiceItemRestRep child : items) {
            if (child instanceof ServiceFieldGroupRestRep) {
                assetFields.putAll(createAssetFieldsFromItems(((ServiceFieldGroupRestRep) child).getItems(), providedFields));
            }
            else if (child instanceof ServiceFieldTableRestRep) {
                assetFields.putAll(createAssetFieldsFromItems(((ServiceFieldTableRestRep) child).getItems(), providedFields));
            }
            else if (child instanceof ServiceFieldModalRestRep) {
                assetFields.putAll(createAssetFieldsFromItems(((ServiceFieldModalRestRep) child).getItems(), providedFields));
            }
        }

        return assetFields;
    }

    /**
     * Creates asset field descriptors for the list of asset fields, with access to the additional fields provided by
     * the parent.
     * 
     * @param fields
     *            the asset fields for which to create descriptors.
     * @param providedFields
     *            the asset fields provided by the parent, mapped by asset type.
     * @return the map of field name->AssetFieldDescriptor.
     */
    private static Map<String, AssetFieldDescriptor> createAssetFields(List<ServiceFieldRestRep> fields,
            Map<String, ServiceFieldRestRep> providedFields) {
        Map<String, AssetFieldDescriptor> assetFields = Maps.newHashMap();

        Set<String> availableTypes = Sets.newHashSet(ServiceFieldRestRep.getAssetTypes(providedFields.values()));
        availableTypes.addAll(ServiceFieldRestRep.getAssetTypes(fields));

        for (ServiceFieldRestRep field : fields) {
            String assetType = field.getAssetType();
            List<String> dependencies = Lists.newArrayList();
            for (String requiredType : AssetOptionsApi.calculateAssetDependencies(assetType, availableTypes)) {
                ServiceFieldRestRep dep = findFieldByAssetType(requiredType, fields, providedFields);
                dependencies.add(dep.getName());
            }
            AssetFieldDescriptor assetField = createAssetField(field, dependencies);
            assetFields.put(field.getName(), assetField);
        }

        return assetFields;
    }

    /**
     * Creates an asset field descriptor for the service field with the provided dependencies
     * 
     * @param field
     *            the service field.
     * @param dependencies
     *            the names of the fields that are dependencies.
     * @return the asset field descriptor.
     */
    private static AssetFieldDescriptor createAssetField(ServiceFieldRestRep field, List<String> dependencies) {
        AssetFieldDescriptor assetField = new AssetFieldDescriptor();
        assetField.assetType = field.getAssetType();
        assetField.select = field.getSelect();
        assetField.fieldsWeDependOn = dependencies;
        // Will be calculated after all asset field descriptors are created
        assetField.fieldsThatDependOnUs = Lists.newArrayList();
        return assetField;
    }

    /**
     * Adds service fields to the provided fields by type, returning a new map. Any provided fields will be overwritten
     * if the service fields contain a matching asset type.
     * 
     * @param fields
     *            the service fields to add.
     * @param providedFields
     *            the current provided fields, mapped by asset type.
     * @return the combined map containing the original provided fields and the service fields mapped by type.
     */
    private static Map<String, ServiceFieldRestRep> addFieldsByType(List<ServiceFieldRestRep> fields,
            Map<String, ServiceFieldRestRep> providedFields) {
        Map<String, ServiceFieldRestRep> map = Maps.newHashMap(providedFields);
        for (ServiceFieldRestRep field : fields) {
            if (field.isAsset()) {
                map.put(field.getAssetType(), field);
            }
        }
        return map;
    }

    /**
     * Finds a field by asset type, first searching the fields list and falling back to a lookup in the provided fields.
     * 
     * @param assetType
     *            the asset type.
     * @param fields
     *            the fields to search.
     * @param providedFields
     *            the fields provided by the parent, mapped by asset type.
     * @return the matching service field.
     * 
     * @throws IllegalArgumentException
     *             if there is no matching field with the desired type.
     */
    private static ServiceFieldRestRep findFieldByAssetType(String assetType, Collection<ServiceFieldRestRep> fields,
            Map<String, ServiceFieldRestRep> providedFields) {
        ServiceFieldRestRep field = findFieldByAssetType(assetType, fields);
        if (field != null) {
            return field;
        }
        field = providedFields.get(assetType);
        if (field != null) {
            return field;
        }
        throw new IllegalArgumentException("No Field of type " + assetType);
    }

    /**
     * Finds a field by asset type in a collection of fields. The first matching field is returned.
     * 
     * @param assetType
     *            the asset type.
     * @param fields
     *            the fields to search.
     * @return the matching service field.
     */
    private static ServiceFieldRestRep findFieldByAssetType(String assetType, Collection<ServiceFieldRestRep> fields) {
        for (ServiceFieldRestRep field : fields) {
            if (StringUtils.equals(field.getAssetType(), assetType)) {
                return field;
            }
        }
        return null;
    }

    public static class AssetFieldDescriptor {
        /** A list of form field names that require us to proceed */
        public List<String> fieldsThatDependOnUs;
        /** A list of form field names that this field requires */
        public List<String> fieldsWeDependOn;
        public String assetType;
        public String select;
    }
}
