/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.xmlgen.beans;

public class XmlElementSequenceAttribute {

    private String name;
    private String model;
    private String type;
    private String properties;
    private String values;
    private String endStatus;
    private Boolean childExists;

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the model
     */
    public String getModel() {
        return model;
    }

    /**
     * @param model the model to set
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the properties
     */
    public String getProperties() {
        return properties;
    }

    /**
     * @param properties the properties to set
     */
    public void setProperties(String properties) {
        this.properties = properties;
    }

    /**
     * @return the values
     */
    public String getValues() {
        return values;
    }

    /**
     * @param values the values to set
     */
    public void setValues(String values) {
        this.values = values;
    }

    /**
     * @return the endStatus
     */
    public String getEndStatus() {
        return endStatus;
    }

    /**
     * @param endStatus the endStatus to set
     */
    public void setEndStatus(String endStatus) {
        this.endStatus = endStatus;
    }

    /**
     * @return the childExists
     */
    public Boolean getChildExists() {
        return childExists;
    }

    /**
     * @param childExists the childExists to set
     */
    public void setChildExists(Boolean childExists) {
        this.childExists = childExists;
    }
}
