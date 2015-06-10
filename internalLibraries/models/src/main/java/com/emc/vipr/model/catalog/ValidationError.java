/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ValidationError {
    
    private String field;
    private String error;

    public ValidationError() {
    }

    public ValidationError(String field, String error) {
        super();
        this.field = field;
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", field, error);
    }
}
