/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;

public class ServiceDescriptorBuilder {
    public static final String CATEGORY_SUFFIX = ".category";
    public static final String TITLE_SUFFIX = ".title";
    public static final String LABEL_SUFFIX = ".label";
    public static final String DESCRIPTION_SUFFIX = ".description";
    public static final String ERROR_SUFFIX = ".validation.error";

    private List<ResourceBundle> bundles;

    public ServiceDescriptorBuilder(String... bundleNames) {
        this(ServiceDescriptorBuilder.class.getClassLoader(), Locale.getDefault(), bundleNames);
    }

    public ServiceDescriptorBuilder(ClassLoader loader, Locale locale, String... bundleNames) {
        bundles = new ArrayList<>();
        for (int i = 0; i < bundleNames.length; i++) {
            bundles.add(ResourceBundle.getBundle(bundleNames[i], locale, loader));
        }
    }

    public ServiceDescriptor build(ServiceDefinition definition) {
        String baseKey = StringUtils.defaultIfBlank(definition.baseKey, definition.serviceId);

        ServiceDescriptor service = new ServiceDescriptor();
        service.setServiceId(definition.serviceId);
        service.setCategory(getMessage(definition.categoryKey, baseKey + CATEGORY_SUFFIX));
        service.setTitle(getMessage(definition.titleKey, baseKey + TITLE_SUFFIX));
        service.setDescription(getMessage(definition.descriptionKey, baseKey + DESCRIPTION_SUFFIX));
        service.addRoles(definition.roles);

        // Ensure that a missing resource keys don't cause the service to be hidden in the catalog
        if (StringUtils.isBlank(service.getTitle())) {
            service.setTitle(StringUtils.defaultString(definition.titleKey, definition.serviceId));
        }
        for (ItemDefinition itemDefinition : definition.items.values()) {
            ServiceItem item = build(baseKey, itemDefinition);
            service.addItem(item);
        }
        return service;
    }

    public ServiceItem build(String baseKey, ItemDefinition definition) {
        if (StringUtils.equals(definition.type, ServiceItem.TYPE_GROUP)) {
            return build(baseKey, (GroupDefinition) definition);
        }
        else if (StringUtils.equals(definition.type, ServiceItem.TYPE_TABLE)) {
            return build(baseKey, (TableDefinition) definition);
        }
        else {
            return build(baseKey, (FieldDefinition) definition);
        }
    }

    public ServiceField build(String baseKey, FieldDefinition definition) {
        String baseFieldKey = getBaseKey(baseKey, definition);

        ServiceField field = new ServiceField();
        apply(baseFieldKey, definition, field);
        field.setRequired(definition.required);
        field.setOmitNone(definition.omitNone);
        field.setLockable(definition.lockable);
        field.setInitialValue(definition.initialValue);
        field.setSelect(definition.select);
        field.addOptions(definition.options);
        field.getValidation().setMin(definition.validation.min);
        field.getValidation().setMax(definition.validation.max);
        field.getValidation().setRegEx(definition.validation.regEx);
        field.getValidation().setError(getMessage(definition.validation.errorKey, baseFieldKey + ERROR_SUFFIX));
        return field;
    }

    public ServiceFieldGroup build(String baseKey, GroupDefinition definition) {
        String baseGroupKey = getBaseKey(baseKey, definition);
        ServiceFieldGroup group = new ServiceFieldGroup();
        apply(baseGroupKey, definition, group);
        group.setCollapsible(definition.collapsible);
        group.setCollapsed(definition.collapsed);
        for (ItemDefinition itemDefinition : definition.items.values()) {
            ServiceItem item = build(baseGroupKey, itemDefinition);
            group.addItem(item);
        }
        return group;
    }

    public ServiceFieldTable build(String baseKey, TableDefinition definition) {
        String baseTableKey = getBaseKey(baseKey, definition);
        ServiceFieldTable table = new ServiceFieldTable();
        apply(baseTableKey, definition, table);
        for (FieldDefinition fieldDefinition : definition.fields.values()) {
            ServiceField field = build(baseTableKey, fieldDefinition);
            table.addItem(field);
        }
        return table;
    }

    private void apply(String baseKey, ItemDefinition source, ServiceItem target) {
        target.setName(source.name);
        target.setType(source.type);
        target.setLabel(getMessage(source.labelKey, baseKey + LABEL_SUFFIX));
        target.setDescription(getMessage(source.descriptionKey, baseKey + DESCRIPTION_SUFFIX));

        // Use the field name if no label is found
        if (StringUtils.isBlank(target.getLabel())) {
            target.setLabel(source.name);
        }
    }

    private String getBaseKey(String baseKey, ItemDefinition definition) {
        return String.format("%s.%s", baseKey, definition.name);
    }

    private String getMessage(String key, String defaultKey) {
        String message = getMessage(key);
        if (message != null) {
            return message;
        }
        else {
            return getMessage(defaultKey);
        }
    }

    /**
     * Finds a message within the resource bundles based on the key. This will try to locate the most specific match in
     * all bundles before shortening the key and looking for a more generic version. The key is dot-separated, each pass
     * will test the key value against all resource bundles. If no match is found, the leading value is removed and it
     * is retried until the key is empty or a match is found.
     * 
     * @param key
     *            the key to find.
     * @return the message, or null if no match is found.
     */
    private String getMessage(String key) {
        while (StringUtils.isNotBlank(key)) {
            for (ResourceBundle bundle : bundles) {
                String message = getMessage(bundle, key);
                if (message != null) {
                    return message;
                }
            }
            key = StringUtils.substringAfter(key, ".");
        }
        return null;
    }

    /**
     * Gets a message from a resource bundle, returning null if no key is found.
     * 
     * @param bundle
     *            the resource bundle.
     * @param key
     *            the message key.
     * @return the message.
     */
    private static String getMessage(ResourceBundle bundle, String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }

}
