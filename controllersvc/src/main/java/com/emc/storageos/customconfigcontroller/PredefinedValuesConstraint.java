package com.emc.storageos.customconfigcontroller;

import java.util.Set;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;

public class PredefinedValuesConstraint extends CustomConfigConstraint{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private Set<String> predefinedValues;
    private String defaultValue;
    
    @Override
    public String applyConstraint(String value, String systemType) {
        if (predefinedValues.contains(value)) {
            return value;
        } else {
            return defaultValue;
        }
        
    }
    @Override
    public void validate(String value, String systemType) {
        if(!predefinedValues.contains(value)) {
            throw CustomConfigControllerException.exceptions.predefinedValueConstraintViolated(value, getValidValues());
        }
        
    }
    public Set<String> getPredefinedValues() {
        return predefinedValues;
    }
    public void setPredefinedValues(Set<String> predefinedValues) {
        this.predefinedValues = predefinedValues;
    }
    public String getDefaultValue() {
        return defaultValue;
    }
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    private String getValidValues() {
        StringBuilder builder = new StringBuilder();
        for (String value : predefinedValues) {
            builder.append(value);
            builder.append(" ");
        }
        return builder.toString();
    }
    
}
