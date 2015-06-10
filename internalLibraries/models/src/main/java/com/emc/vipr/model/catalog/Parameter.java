/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Parameter {
    
    /**
     * Label or key for this parameter to an order
     */
    private String label;                       
    
    /**
     * Actual value for this parameter (often times an ID)
     */
    private String value;                 
    
    private String friendlyLabel;
    
    /**
     * User friendly text value representing the choice (often the label)
     */
    private String friendlyValue;               
    
    private boolean userInput = true;
    
    private boolean encrypted = false;

    public Parameter() {
    }

    public Parameter(String label, String value, String friendlyValue) {
        this.label = label;
        this.value = value;
        this.friendlyValue = friendlyValue;
    }

    @Override
    public String toString() {
        return String.format("%s %s", label, value);
    }

    @XmlElement(name = "friendly_value", required = false)
    public String getFriendlyValue() {
        return friendlyValue;
    }

    public void setFriendlyValue(String friendlyValue) {
        this.friendlyValue = friendlyValue;
    }

    @XmlElement(name = "label")
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @XmlElement(name = "value", required = false)
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @XmlElement(name = "friendly_label", required = false)
    public String getFriendlyLabel() {
        return friendlyLabel;
    }

    public void setFriendlyLabel(String friendlyLabel) {
        this.friendlyLabel = friendlyLabel;
    }

    @XmlElement(name = "user_input")
    public boolean isUserInput() {
        return userInput;
    }

    public void setUserInput(boolean userInput) {
        this.userInput = userInput;
    }
    
    @XmlElement(name = "encrypted")
    public boolean isEncrypted() {
        return encrypted;
    }
    
    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }
    
}
