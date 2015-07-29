/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.util;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Validation;

import com.emc.storageos.model.DataObjectRestRep;

/**
 * Abstract implementation of a 'form'.
 */
public abstract class AbstractRestRepForm<T extends DataObjectRestRep> {

    /** the id of this form object */
    public String id;

    /** Read data from the given model object into this form object. */
    public void readFrom(T model) {
        this.id = model.getId().toString();
        doReadFrom(model);
    }

    /** Implementing classes should do their 'read from the model into the form' work here. */
    protected abstract void doReadFrom(T model);

    /** run the validation processing on this form object */
    public void validate(String formName) {
        Validation.valid(formName, this);
        doValidation(formName);
    }

    /** Implementing classes should do their 'validate the data in the form' work here. */
    protected void doValidation(String formName) {

    }

    /** Save data from this form to the database. */
    public T save() {
        T model;
        if (isNew()) {
            model = doCreate();
        }
        else {
            model = doUpdate();
        }
        return model;
    }

    protected abstract T doCreate();

    protected abstract T doUpdate();

    /**
     * returns true if this is a new instance of the form
     * ie: it hasn't been saved yet.
     */
    public boolean isNew() {
        return StringUtils.isBlank(id);
    }
}
