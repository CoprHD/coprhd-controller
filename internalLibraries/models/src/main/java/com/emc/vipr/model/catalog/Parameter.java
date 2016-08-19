/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

@XmlRootElement
public class Parameter implements Serializable {

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
    @JsonProperty("friendlyValue")
    public String getFriendlyValue() {
        return friendlyValue;
    }

    @JsonProperty("friendlyValue")
    public void setFriendlyValue(String friendlyValue) {
        this.friendlyValue = friendlyValue;
    }

    @XmlElement(name = "label")
    @JsonProperty("label")
    public String getLabel() {
        return label;
    }

    @JsonProperty("label")
    public void setLabel(String label) {
        this.label = label;
    }

    @JsonProperty("value")
    @XmlElement(name = "value", required = false)
    public String getValue() {
        return value;
    }

    @JsonProperty("value")
    public void setValue(String value) {
        this.value = value;
    }

    @JsonProperty("friendlyLabel")
    @XmlElement(name = "friendly_label", required = false)
    public String getFriendlyLabel() {
        return friendlyLabel;
    }

    @JsonProperty("friendlyLabel")
    public void setFriendlyLabel(String friendlyLabel) {
        this.friendlyLabel = friendlyLabel;
    }

    @JsonProperty("userInput")
    @XmlElement(name = "user_input")
    public boolean isUserInput() {
        return userInput;
    }

    @JsonProperty("userInput")
    public void setUserInput(boolean userInput) {
        this.userInput = userInput;
    }

    @XmlElement(name = "encrypted")
    @JsonProperty("encrypted")
    public boolean isEncrypted() {
        return encrypted;
    }

    @JsonProperty("encrypted")
    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

}
