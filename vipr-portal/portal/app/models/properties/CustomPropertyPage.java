/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.List;
import java.util.Map;

import com.emc.storageos.services.util.SecurityUtils;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

/**
 * Base class for a custom property page. Properties that are known to the page should be added to the custom properties
 * in order to support validation and retrieving the updated values. The other properties will generally be added to the
 * end of the page when rendering.
 * 
 * @author jonnymiller
 */
public class CustomPropertyPage extends DefaultPropertyPage {
    private List<Property> customProperties;

    public CustomPropertyPage(String name) {
        super(name);
        customProperties = Lists.newArrayList();
    }

    public Property getProperty(String name) {
        Property property = super.getProperty(name);
        if (property != null) {
            return property;
        }

        for (Property custom : customProperties) {
            if (StringUtils.equals(name, custom.getName())) {
                return custom;
            }
        }
        return null;
    }

    /**
     * Gets the list of custom properties.
     * 
     * @return the list of properties.
     */
    public List<Property> getCustomProperties() {
        return customProperties;
    }

    /**
     * Removes the property from the map and adds to the custom properties list.
     * 
     * @param properties
     *            the properties map.
     * @param name
     *            the name of the custom property.
     * @return the custom property.
     */
    protected Property addCustomProperty(Map<String, Property> properties, String name) {
        Property prop = properties.remove(name);
        return addCustomProperty(prop);
    }

    /**
     * Add a property as a password field.
     * 
     * @param properties
     *            the properties map.
     * @param name
     *            the property name.
     * @return the custom property.
     */
    protected Property addCustomPasswordProperty(Map<String, Property> properties, String name) {
        Property prop = addCustomProperty(properties, name);
        if (prop != null) {
            prop.setPasswordField(true);
        }
        return prop;
    }

    protected Property addCustomBooleanProperty(Map<String, Property> properties, String name) {
        Property prop = addCustomProperty(properties, name);
        if (prop != null) {
            prop.setBooleanField(true);
        }
        return prop;
    }

    /**
     * Adds a custom property to the list of custom properties.
     * 
     * @param property
     *            the property.
     * @return the custom property.
     */
    protected Property addCustomProperty(Property property) {
        if (property != null) {
            customProperties.add(property);
        }
        return property;
    }

    @Override
    public boolean hasErrors() {
        return super.hasErrors() || hasErrors(customProperties);
    }

    @Override
    public void validate(Map<String, String> values) {
        super.validate(values);
        validate(customProperties, values);
    }

    @Override
    public Map<String, String> getUpdatedValues(Map<String, String> values) {
        Map<String, String> updated = super.getUpdatedValues(values);
        updated.putAll(getUpdatedValues(customProperties, values));
        return updated;
    }
}
