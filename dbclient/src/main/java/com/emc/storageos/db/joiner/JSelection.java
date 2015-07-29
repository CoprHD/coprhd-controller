/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.joiner;

import java.util.Set;

/**
 * JSelection represents a match operator.
 * Right now, only inclusive matches are supported, i.e. when the
 * field contains one of the values.
 * If the field is a collection, when any value in the collection
 * matches any value in values a match is declared.
 * 
 * @author watson
 * 
 */
class JSelection {
    String field;                       // field name selection applied to
    Set<Object> values;                 // values to be selected

    String getField() {
        return field;
    }

    void setField(String field) {
        this.field = field;
    }

    Set<Object> getValues() {
        return values;
    }

    void setValues(Set<Object> values) {
        this.values = values;
    }
}
