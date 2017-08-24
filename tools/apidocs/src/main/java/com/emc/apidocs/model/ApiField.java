/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a field or attribute in a Class or a Method parameter
 */
public class ApiField implements Comparable<ApiField> {
    public String name;
    public boolean required = false;
    public String primitiveType = "";
    public String wrapperName = "";     // Stores the value of XMLElementWrapper
    public ApiClass type;
    public String description = "";
    public List<String> validValues = new ArrayList<String>();
    public boolean collection = false;
    public int min = 0;
    public int max = 1;
    public String jsonName; // value of JsonProperty

    // Used during diff operations
    public ChangeState changeState = ChangeState.NOT_CHANGED;

    public ApiField() {
    }

    public boolean isPrimitive() {
        return type == null || !primitiveType.equals("");
    }

    public void addValidValue(String value) {
        validValues.add(value);
    }

    public boolean isOptional() {
        return !required && min == 0 && max == 1;
    }

    /** Indicates if this Field has a type that has child elements, really only useful for XML generation */
    public boolean hasChildElements() {
        return !isPrimitive() && (type != null && !type.fields.isEmpty());
    }

    @Override
    public int compareTo(ApiField other) {
        return name.compareTo(other.name);  // To change body of implemented methods use File | Settings | File Templates.
    }
}
