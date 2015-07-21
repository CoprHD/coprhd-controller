/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Validation;
import util.MessagesUtils;
import util.PasswordUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DefaultPropertyPage implements PropertyPage {
    private String name;
    private String renderTemplate = "defaultPage.html";
    private List<Property> properties;

    public DefaultPropertyPage(String name) {
        this.name = name;
        this.properties = Lists.newArrayList();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRenderTemplate() {
        return renderTemplate;
    }

    public void setRenderTemplate(String renderTemplate) {
        this.renderTemplate = renderTemplate;
    }

    @Override
    public String getLabel() {
        String labelKey = "configProperties." + name;
        String label = MessagesUtils.get(labelKey);
        if (StringUtils.equals(label, labelKey)) {
            label = name;
        }
        return label;
    }

    @Override
    public List<Property> getProperties() {
        return properties;
    }

    public Property getProperty(String name) {
        for (Property property : properties) {
            if (StringUtils.equals(name, property.getName())) {
                return property;
            }
        }
        return null;
    }

    public Property addProperty(Property property) {
        if (property != null) {
            properties.add(property);
        }
        return property;
    }

    @Override
    public boolean hasErrors() {
        return hasErrors(properties);
    }

    protected boolean hasErrors(List<Property> props) {
        for (Property property : props) {
            String fieldName = property.getName();
            if (Validation.hasError(fieldName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void validate(Map<String, String> values) {
        validate(properties, values);
    }

    protected void validate(List<Property> props, Map<String, String> values) {
        for (Property property : props) {
            String value = values.get(property.getName());
            property.validate(value);
        }
    }

    @Override
    public Map<String, String> getUpdatedValues(Map<String, String> values) {
        return getUpdatedValues(properties, values);
    }

    protected Map<String, String> getUpdatedValues(List<Property> props, Map<String, String> values) {
        Map<String, String> updated = Maps.newHashMap();
        for (Property property : props) {
            String name = property.getName();
            if (!values.containsKey(name)) {
                continue;
            }

            String value = values.get(name);
            if (property.isValueHidden()) {
                // For properties where the value is hidden, update it if it's not blank
                if (StringUtils.isNotBlank(value)) {
                    updated.put(name, value);
                }
            }
            else {
                // Otherwise update it if the value has changed
                String originalValue = property.getValue();
                // If the value has changed and not from null -> empty string
                if (!StringUtils.equals(value, originalValue) &&
                        !(StringUtils.isBlank(value) && StringUtils.isBlank(originalValue))) {
                    // Password or encrypted fields may or may not be encrypted
                    if (property.isPasswordField() || property.isEncryptedField()) {
                        value = PasswordUtil.decryptedValue(value);
                    }
                    updated.put(name, value);
                }
            }
        }
        return updated;
    }

    @Override
    public boolean isRebootRequired(Collection<String> keys) {
        boolean rebootRequired = false;
        for (String key: keys) {
            Property property = getProperty(key);
            if (property != null) {
                rebootRequired |= property.isRebootRequired();
            }
        }
        return rebootRequired;
    }
}
