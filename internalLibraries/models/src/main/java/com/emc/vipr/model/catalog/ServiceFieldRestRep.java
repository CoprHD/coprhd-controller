/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "service_field")
public class ServiceFieldRestRep extends ServiceItemRestRep {

    public static final String ASSET_TYPE_PREFIX = "assetType.";

    private boolean required;
    private boolean omitNone;
    private String initialValue;
    private boolean modalField;
    private String select;
    private boolean lockable;
    private Integer min;
    private Integer max;
    private String regEx;
    private String failureMessage;
    private List<Option> options;
    private boolean hideIfEmpty;

    @XmlElement(name = "required")
    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
    
    @XmlElement(name = "omitNone")
    public boolean isOmitNone() {
        return omitNone;
    }

    public void setOmitNone(boolean omitNone) {
        this.omitNone = omitNone;
    }

    @XmlElement(name = "initial_value")
    public String getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(String initialValue) {
        this.initialValue = initialValue;
    }

    @XmlElement(name = "modalField")
    public boolean isModalField() {
        return modalField;
    }

    public void setModalField(boolean modalField) {
        this.modalField = modalField;
    }

    @XmlElement(name = "select")
    public String getSelect() {
        return select;
    }

    public void setSelect(String select) {
        this.select = select;
    }

    @XmlElement(name = "lockable")
    public boolean isLockable() {
        return lockable;
    }

    public void setLockable(boolean lockable) {
        this.lockable = lockable;
    }

    @XmlElement(name = "min")
    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

    @XmlElement(name = "max")
    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    @XmlElement(name = "regex")
    public String getRegEx() {
        return regEx;
    }

    public void setRegEx(String regEx) {
        this.regEx = regEx;
    }

    @XmlElement(name = "failure_message")
    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    @XmlElement(name = "options")
    public List<Option> getOptions() {
        if (options == null) {
            options = new ArrayList<>();
        }
        return options;
    }

    public void setOptions(List<Option> options) {
        this.options = options;
    }

    public boolean isAsset() {
        return getType() != null && getType().startsWith(ASSET_TYPE_PREFIX);
    }

    public String getAssetType() {
        if (isAsset()) {
            return getType().substring(ASSET_TYPE_PREFIX.length(), getType().length());
        }
        return null;
    }

    public static Set<String> getAssetTypes(Collection<? extends ServiceItemRestRep> items) {
        Set<String> assetTypes = new HashSet<>();
        for (ServiceFieldRestRep assetField : getAssetFields(items)) {
            assetTypes.add(assetField.getAssetType());
        }
        return assetTypes;
    }

    public static List<ServiceFieldRestRep> getAssetFields(Collection<? extends ServiceItemRestRep> items) {
        List<ServiceFieldRestRep> assetFields = new ArrayList<>();
        for (ServiceItemRestRep item : items) {
            if (item.isField() && ((ServiceFieldRestRep) item).isAsset()) {
                assetFields.add((ServiceFieldRestRep) item);
            }
        }

        return assetFields;
    }
    
    @XmlElement(name = "hideIfEmpty")
    public boolean getHideIfEmpty() {
        return hideIfEmpty;
    }

    public void setHideIfEmpty(boolean hideIfEmpty) {
        this.hideIfEmpty = hideIfEmpty;
    }

}
