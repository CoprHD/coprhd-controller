/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

/**
 * This class defines a NameMask customizable configuration variable.
 * This variable value is computed and not directly available
 * as a property on a domain object
 * 
 * @see controller-custom-config-info.xml
 * 
 */
public class ComputedDataSourceVariable extends DataSourceVariable {

    private static final long serialVersionUID = 4682100792433870106L;

    private String computedPropertyName;

    /**
     * The name of the computed property that
     * this data source property represents
     * 
     * @return
     */
    public String getComputedPropertyName() {
        return computedPropertyName;
    }

    public void setComputedPropertyName(String computedPropertyName) {
        this.computedPropertyName = computedPropertyName;
    }

}
