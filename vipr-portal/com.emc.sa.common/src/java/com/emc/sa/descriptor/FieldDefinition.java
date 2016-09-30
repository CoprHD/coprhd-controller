/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Java representation of the service field definition JSON.
 */
public class FieldDefinition extends ItemDefinition {
    private static final long serialVersionUID = -7613470021410469644L;

    /** Whether the field is required. */
    public boolean required = true;
    
    /** Whether the field will add a "None" value if required. */
    public boolean omitNone = false;

    /** Whether the field can be 'locked' down (pre-defined) by an admin in the catalog. */
    public boolean lockable = false;

    /** The initial field value. */
    public String initialValue;

    /** The type of selection for choice or asset types. */
    public String select = ServiceField.SELECT_ONE;

    /** Select options for standard 'select' types. */
    public Map<String, String> options = new LinkedHashMap<>();

    /** Validation definition for this field. */
    public ValidationDefinition validation = new ValidationDefinition();

    public FieldDefinition() {}

    public FieldDefinition(ServiceField serviceField) {
        this.descriptionKey = serviceField.getDescription();
        this.labelKey = serviceField.getLabel();
        this.name = serviceField.getName();
        this.type = serviceField.getType();
        this.omitNone = serviceField.isOmitNone();
        this.lockable = serviceField.isLockable();
        this.select = serviceField.getSelect();
        this.required = serviceField.isRequired();
        this.initialValue = serviceField.getInitialValue();
        this.options = serviceField.getOptions();
        this.validation.min = serviceField.getValidation().getMin();
        this.validation.max = serviceField.getValidation().getMax();
        this.validation.regEx = serviceField.getValidation().getRegEx();
        this.validation.errorKey = serviceField.getValidation().getError();
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        toString(builder);
        builder.append("required", required);
        builder.append("omitNone", omitNone);
        builder.append("lockable", lockable);
        builder.append("initialValue", initialValue);
        builder.append("select", select);
        builder.append("options", options);
        builder.append("validation", validation);
        return builder.toString();
    }
}
