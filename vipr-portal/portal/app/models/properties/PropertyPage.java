/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.properties;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PropertyPage {
    /**
     * Gets the name of the property page.
     * 
     * @return the name of the property page.
     */
    public String getName();

    /**
     * Gets the label of the property page.
     * 
     * @return the label of the page.
     */
    public String getLabel();

    /**
     * Gets the name of the template to render this page.
     * 
     * @return the render template name.
     */
    public String getRenderTemplate();

    /**
     * Gets the properties to display on the property page.
     * 
     * @return the properties.
     */
    public List<Property> getProperties();

    /**
     * Validates the properties on the page.
     * 
     * @param values
     *        the values to validate.
     */
    public void validate(Map<String, String> values);

    /**
     * Determines if there are any validation errors for the properties on this page.
     * 
     * @return true if there are errors on the page.
     */
    public boolean hasErrors();

    /**
     * Gets the values that have been updated.
     * 
     * @param values
     *        the values.
     * @return the map of updated values.
     */
    public Map<String, String> getUpdatedValues(Map<String, String> values);

    /**
     * Determines if any of the properties named require a reboot.
     *
     * @param keys Keys to check if they require a reboot
     * @return true if reboot is required, false otherwise.
     */
    public boolean isRebootRequired(Collection<String> keys);
}
