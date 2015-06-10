/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.model;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Describes a Java Class that is used as either a request or response
 */
public class ApiClass {
    public String name;
    public String description;
    public List<ApiField> fields = Lists.newArrayList();
    public List<ApiField> attributes = Lists.newArrayList();

    public void addField(ApiField field) {
        fields.add(field);
    }

    public void addAttribute(ApiField attribute) {
        attributes.add(attribute);
    }
}
