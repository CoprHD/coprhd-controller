/**
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

package com.emc.storageos.customconfigcontroller;

import java.io.Serializable;

import com.emc.storageos.db.client.model.DataObject;

/**
 * This class defines a NameMask customizable configuration variable. 
 * A name mask variables corresponds to a property on a domain
 * object (data source which is an instance of {@link DataObject}) 
 * that can be included in a resource name. 
 * 
 * @see controller-custom-config-info.xml
 *  
 */
public class DataSourceVariable implements Serializable{
    private String displayName;
    private String propertyName;
    private Class<? extends DataObject> sourceClass;
    private String sample;
    
    /**
     * The string that displays in the name mask string.
     * @return The string that displays in the name mask string.
     */
    public String getDisplayName() {
        return displayName;
    }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    /**
     * The name of the property in the domain object that 
     * this data source property represents
     * @return
     */
    public String getPropertyName() {
        return propertyName;
    }
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }
    
    /**
     * The name of the domain object (data source) class that this variable
     * refers to.
     * 
     * @return The name of the domain object class that this variable
     * refers to.
     */
    public Class<? extends DataObject> getSourceClass() {
        return sourceClass;
    }
    public void setSourceClass(Class<? extends DataObject> sourceClass) {
        this.sourceClass = sourceClass;
    }
    
    /**
     * A sample value of the property that this data source property refers to.
     * @return
     */
    public String getSample() {
        return sample;
    }
    public void setSample(String sample) {
        this.sample = sample;
    }

}
