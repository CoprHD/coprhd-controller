/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.CustomConfig;

/**
 * This class represents a customizable configuration definition. Customizable
 * configurations are created by the application and they define the scope and
 * constraints for each configuration, in addition to a default value. Users can
 * create instances of CustomConfig to override the default value for different
 * scope. Instances of this class are defined in
 * controller-custom-config-info.xml.
 * 
 */
public class CustomConfigType implements Serializable {
    private String name;
    private Map<DataSourceVariable, Boolean> dataSourceVariables;
    private String type;
    private List<CustomConfigConstraint> constraints;
    private Map<String, String> scope;
    private String configType;
    private Map<String, String> defaultValues;

    /**
     * Returns the configuration type
     * 
     * @see ConfigType
     * @return Returns the type of the value this configuration.
     * 
     */
    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    /**
     * A map of scope-type-to-scope-valid-values. The value in the map can be a
     * comma-separated list of possible values.
     * 
     * @return
     */
    public Map<String, String> getScope() {
        return scope;
    }

    public void setScope(Map<String, String> scope) {
        this.scope = scope;
    }

    /**
     * Returns the data type (ex. Integer, String, etc.) of the value this
     * configuration.
     * 
     * @return Returns the data type of the value this configuration.
     * 
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The name of the configuration which is a unique identifier for each
     * customizable configuration. This name is used by {@link CustomConfig} to
     * reference the template which they customize.
     * 
     * @return the unique name of the configuration
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * A map of the different data source properties that apply to this
     * configuration. This property applies only to configurations of type {@link ConfigType#CustomName}. The boolean value indicates in
     * this source
     * property is recommended.
     * 
     * @return A map of the different data source properties that apply to this
     *         configuration. The boolean value indicates in this source
     *         property is recommended.
     */
    public Map<DataSourceVariable, Boolean> getDataSourceVariables() {
        return dataSourceVariables;
    }

    public void setDataSourceVariables(
            Map<DataSourceVariable, Boolean> dataSourceVariables) {
        this.dataSourceVariables = dataSourceVariables;
    }

    /**
     * Returns the system-specified default values of this configuration as a
     * map of scope-to-default-value. A different default value can be specified
     * for different scopes. A general 'default' type default should always be
     * provided.
     * <p>
     * This default value is used when a user-defined customized value is not found for a given scope.
     * 
     * @return Returns the system-specified default value of this configuration.
     */
    public Map<String, String> getDefaultValues() {
        return defaultValues;
    }

    public void setDefaultValues(Map<String, String> defaultValues) {
        this.defaultValues = defaultValues;
    }

    /**
     * Returns a list of constraints or rules that apply to this configuration.
     * 
     * @return Returns a list of constraints or rules that apply to this
     *         configuration.
     */
    public List<CustomConfigConstraint> getConstraints() {
        return (constraints != null) ? constraints : Collections
                .<CustomConfigConstraint> emptyList();
    }

    public void setConstraints(List<CustomConfigConstraint> constraints) {
        this.constraints = constraints;
    }

    public enum ConfigType {
        CustomName, SimpleConfig
    }

}
