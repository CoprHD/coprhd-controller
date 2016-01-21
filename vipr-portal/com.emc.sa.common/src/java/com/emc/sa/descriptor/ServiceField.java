/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.google.common.collect.Maps;

/**
 * Represents a field on a form
 */
public class ServiceField extends ServiceItem {
    private static final long serialVersionUID = -1755035953840982580L;
    public static final String SELECT_ONE = "one";
    public static final String SELECT_MANY = "many";

    public static final String ASSET_TYPE_PREFIX = "assetType.";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_CHOICE = "choice";
    public static final String TYPE_STORAGE_SIZE = "storageSize";
    public static final String TYPE_EXPAND_SIZE = "expandSize";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_PASSWORD = "password"; // NOSONAR ("False positive, field does not store a password")

    /** Indicates whether the field is required. */
    private boolean required = true;

    /** Indicates that the field can be 'locked' down (pre-defined) by an admin in the catalog. */
    private boolean lockable;

    /** The initial value of the field. */
    private String initialValue;

    /** For choice or asset fields, whether one or many values can be selected. */
    private String select = SELECT_ONE;

    /** Validation descriptor for the field. */
    private Validation validation = new Validation();

    /** For choice fields, defines fixed options. */
    private Map<String, String> options = Maps.newLinkedHashMap();

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isLockable() {
        return lockable;
    }

    public void setLockable(boolean lockable) {
        this.lockable = lockable;
    }

    public String getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(String initialValue) {
        this.initialValue = initialValue;
    }

    public String getSelect() {
        return select;
    }

    public void setSelect(String select) {
        this.select = select;
    }

    public Validation getValidation() {
        return validation;
    }

    public void setValidation(Validation validation) {
        this.validation = validation;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public void addOptions(Map<String, String> options) {
        this.options.putAll(options);
    }

    @Override
    public void setType(String type) {
        if (StringUtils.equals(type, TYPE_GROUP) || StringUtils.equals(type, TYPE_TABLE)) {
            throw new IllegalArgumentException("Invalid field type: " + type);
        }
        super.setType(type);
    }

    public boolean isAsset() {
        return StringUtils.startsWith(getType(), ASSET_TYPE_PREFIX);
    }

    public String getAssetType() {
        return StringUtils.substringAfter(getType(), ASSET_TYPE_PREFIX);
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(ToStringStyle.SHORT_PREFIX_STYLE);
        toString(builder);
        builder.append("required", required);
        builder.append("lockable", lockable);
        builder.append("initialValue", initialValue);
        builder.append("select", select);
        builder.append("options", options);
        builder.append("validation", validation);
        return builder.toString();
    }

    public static List<ServiceField> getFieldList(ServiceItemContainer container) {
        List<ServiceField> fields = new ArrayList<>();
        for (ServiceItem item : container.getItems().values()) {
            if (item.isField()) {
                fields.add((ServiceField) item);
            }
        }
        return fields;
    }

    public static List<ServiceField> getAllFieldList(ServiceItemContainer container) {
        List<ServiceField> fields = new ArrayList<>();
        for (ServiceItem item : container.getItems().values()) {
            if (item.isField()) {
                fields.add((ServiceField) item);
            }
            else if (item instanceof ServiceItemContainer) {
                fields.addAll(getAllFieldList((ServiceItemContainer) item));
            }
        }
        return fields;
    }

    public static ServiceField findField(ServiceItemContainer container, String name) {
        ServiceItem value = container.getItems().get(name);
        if (value != null && value.isField()) {
            return (ServiceField) value;
        }

        // Search for a nested field
        for (ServiceItem item : container.getItems().values()) {
            if (item instanceof ServiceItemContainer) {
                ServiceField field = findField((ServiceItemContainer) item, name);
                if (field != null) {
                    return field;
                }
            }
        }
        // No field found
        return null;
    }

    public static ServiceField removeField(ServiceItemContainer container, String name) {
        ServiceItem value = container.getItems().get(name);
        if (value != null && value.isField()) {
            container.getItems().remove(name);
            return (ServiceField) value;
        }

        // Search for a nested field
        for (ServiceItem item : container.getItems().values()) {
            if (item instanceof ServiceItemContainer) {
                ServiceField field = removeField((ServiceItemContainer) item, name);
                if (field != null) {
                    return field;
                }
            }
        }
        // No field found
        return null;
    }

    public static List<ServiceField> getAssetFields(ServiceItemContainer container) {
        return getAssetFields(container.getItems().values());
    }

    public static Set<String> getAssetTypes(ServiceItemContainer container) {
        return getAssetTypes(container.getItems().values());
    }

    public static List<ServiceField> getAssetFields(Collection<? extends ServiceItem> items) {
        List<ServiceField> assetFields = new ArrayList<>();
        for (ServiceItem item : items) {
            if (item.isField() && ((ServiceField) item).isAsset()) {
                assetFields.add((ServiceField) item);
            }
        }

        return assetFields;
    }

    public static Set<String> getAssetTypes(Collection<? extends ServiceItem> items) {
        Set<String> assetTypes = new HashSet<>();
        for (ServiceField assetField : getAssetFields(items)) {
            assetTypes.add(assetField.getAssetType());
        }
        return assetTypes;
    }

    public static class Validation implements Serializable {
        private static final long serialVersionUID = 7910763484942532839L;
        private Integer min;
        private Integer max;
        private String regEx;
        private String error;

        public Integer getMin() {
            return min;
        }

        public void setMin(Integer min) {
            this.min = min;
        }

        public Integer getMax() {
            return max;
        }

        public void setMax(Integer max) {
            this.max = max;
        }

        public String getRegEx() {
            return regEx;
        }

        public void setRegEx(String regEx) {
            this.regEx = regEx;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        @Override
        public String toString() {
            ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
            builder.append("min", min);
            builder.append("max", max);
            builder.append("regEx", regEx);
            builder.append("error", error);
            return builder.toString();
        }
    }
}
