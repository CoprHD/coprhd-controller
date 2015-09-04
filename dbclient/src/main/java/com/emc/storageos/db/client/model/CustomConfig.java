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

package com.emc.storageos.db.client.model;

/**
 * This class holds the user-input for a customizable configuration item.
 * It contains the value of the config item and the scope to which it
 * applies. For example, an instance of this class can be the port
 * allocation max volume value for VNX or the zone name convention
 * for brocade.
 *
 */
@SuppressWarnings("serial")
@Cf("CustomConfig")
public class CustomConfig extends DataObject {
    private String configType;
    private StringMap scope;
    private String value;
    private Boolean registered;
    private Boolean systemDefault;

    @AlternateId("AltIdIndex")
    @Name("configType")
    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
        setChanged("configType");
    }

    @Name("scope")
    public StringMap getScope() {
        return scope;
    }

    public void setScope(StringMap scope) {
        this.scope = scope;
        setChanged("scope");
    }

    @Name("value")
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        setChanged("value");
    }

    @Name("registered")
    public Boolean getRegistered() {
        return registered == null ? false : registered;
    }

    public void setRegistered(Boolean registered) {
        this.registered = registered;
        setChanged("registered");
    }

    @Name("systemDefault")
    public Boolean getSystemDefault() {
        return systemDefault == null ? false : systemDefault;
    }

    public void setSystemDefault(Boolean systemDefault) {
        this.systemDefault = systemDefault;
        setChanged("systemDefault");
    }

}