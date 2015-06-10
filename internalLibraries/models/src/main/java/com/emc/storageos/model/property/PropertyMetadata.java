/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.property;

import static com.emc.storageos.model.property.PropertyConstants.*;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement
public class PropertyMetadata {

    private String label;
    private String description;
    private String type;
    private String tag;
    private Integer minLen = 0;             // For type STRING only; the lowest valid value is 0
    private Integer maxLen = 65534;             // For type STRING only; the highest valid value is 65534 (?)
    private String[] allowedValues = new String[0];     // For STRING and INT types only
    private Boolean userConfigurable = false;   // This applies to OVF only
    private Boolean userMutable = false;        // This applies to wizard and syssvc API
    private Boolean advanced = false;           // Advanced wizard only
    private Boolean hidden = false;             // Do not show in  wizard and syssvc API without force
    private Boolean reconfigRequired = false;
    private Boolean rebootRequired = false;
    private String[] notifiers = new String[0];
    private String value;
    private Boolean controlNodeOnly = false;    // Control node only property flag

    public PropertyMetadata() {}

    public void setLabel(String label) {
        this.label = label;
    }

    @XmlElement(name = "label")
    @JsonProperty("label")
    public String getLabel() {
        return label;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "description")
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlElement(name = "type")
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    @XmlElement(name = "tag")
    @JsonProperty("tag")
    public String getTag() {
        return tag;
    }

    public void setMinLen(Integer minLen) {
        this.minLen = minLen;
    }

    @XmlElement(name = "minLen")
    @JsonProperty("minLen")
    public Integer getMinLen()  {
        return minLen;
    }

    public void setMaxLen(Integer maxLen) {
        this.maxLen = maxLen;
    }

    @XmlElement(name = "maxLen")
    @JsonProperty("maxLen")
    public Integer getMaxLen() {
        return maxLen;
    }

    public void setAllowedValues(String[] allowedValues) {
        this.allowedValues = allowedValues;
    }

    @XmlElement(name = "allowedValues")
    @JsonProperty("allowedValues")
    public String[] getAllowedValues() {
        return allowedValues;
    }

    public void setUserConfigurable(Boolean userConfigurable) {
        this.userConfigurable = userConfigurable;
    }

    @XmlElement(name = "userConfigurable")
    @JsonProperty("userConfigurable")
    public Boolean getUserConfigurable() {
        return userConfigurable;
    }

    public void setUserMutable(Boolean userMutable) {
        this.userMutable = userMutable;
    }

    @XmlElement(name = "userMutable")
    @JsonProperty("userMutable")
    public Boolean getUserMutable() {
        return userMutable == null? false : userMutable;
    }

    public void setAdvanced(Boolean advanced) {
        this.advanced = advanced;
    }

    @XmlElement(name = "advanced")
    @JsonProperty("advanced")
    public Boolean getAdvanced() {
        return advanced;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    @XmlElement(name = "hidden")
    @JsonProperty("hidden")
    public Boolean getHidden() {
        return hidden;
    }

    public void setReconfigRequired(Boolean reconfigRequired) {
        this.reconfigRequired = reconfigRequired;
    }

    @XmlElement(name = "reconfigRequired")
    @JsonProperty("reconfigRequired")
    public Boolean getReconfigRequired() {
        return reconfigRequired;
    }

    public void setRebootRequired(Boolean rebootRequired) {
        this.rebootRequired = rebootRequired;
    }

    @XmlElement(name = "rebootRequired")
    @JsonProperty("rebootRequired")
    public Boolean getRebootRequired() {
        return rebootRequired;
    }

    public void setNotifiers(String[] notifiers) {
        this.notifiers = notifiers;
    }

    @XmlElement(name = "notifiers")
    @JsonProperty("notifiers")
    public String[] getNotifiers() {
        return notifiers;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @XmlElement(name = "value")
    @JsonProperty("value")
    public String getValue() {
        return value;
    }

    public void setControlNodeOnly(Boolean controlNodeOnly) {
        this.controlNodeOnly = controlNodeOnly;
    }

    @XmlElement(name = "controlNodeOnly")
    @JsonProperty("controlNodeOnly")
    public Boolean getControlNodeOnly() {
        return controlNodeOnly;
    }

    /**
     * Get default value
     *
     *
     * @return default value from metadata. Null when property is non-usermutable.
     */
    public String getDefaultValue() {
        if (userMutable == null || userMutable == false) {
            return null;
        }
        return getDefaultValueMetaData();
    }
    
    /**
     * Get default value meta data
     *
     * For all types : if _value not null, return it.
     *
     * For Ip Address : ip address can only be set during deployment. It can from user input or default value.
     *                    if it has default value, then return it. Otherwise, it must be set by user.
     *
     * For String : if no default value defined in metadata
     *                  case 1: minLen is null or 0, return ""
     *                  case 2: minLen > 0, its value should have been set during deployment
     * For URL & Email & License & Hostname & Iplist  : same as String
     * For UNIT64 & UINT32 & PERCENT : if no default, set to 0
     *
     * @return      default value string. Null when no default values needed.
     */
    public String getDefaultValueMetaData() {
        // return default value if exist
        if (value != null) {
            return value;
        }

        if (IPADDR.equals(type)) {
            // do not set ip address since they are configured during deployment
            return null;
        }

        if (STRING.equals(type) || URL.equals(type) || EMAIL.equals(type) || LICENSE.equals(type)
                || HOSTNAME.equals(type)|| IPLIST.equals(type)) {
            // if minLen is not set or 0. Default value is empty string
            // if minLen is set.
            //   It must have been configured during deployment.
            //   no default value needed
            return (minLen == null || minLen == 0)? "" : null;
        }

        if (UINT64.equals(type) || UINT32.equals(type) || PERCENT.equals(type)) {
            // default value set to 0
            return "0";
        }

        return null;
    }
}

