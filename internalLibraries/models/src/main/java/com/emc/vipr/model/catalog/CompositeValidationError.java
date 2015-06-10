/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import com.emc.vipr.model.catalog.ValidationError;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class CompositeValidationError extends ValidationError {

    private List<ValidationError> errors;
    
    public CompositeValidationError() {
        super();
    }
    
    public CompositeValidationError(String error, String field) {
        super(error, field);
        
    }
    public void addValidationError(ValidationError error) {
        getErrors().add(error);
    }
    
    @XmlElementWrapper(name = "errors")
    @XmlElement(name="error")
    public List<ValidationError> getErrors() {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
    }

    @Override
    public void setError(String error) {};
    
    @Override
    public void setField(String field) {};
    
}
