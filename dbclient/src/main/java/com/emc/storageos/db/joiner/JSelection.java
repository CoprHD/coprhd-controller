/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
