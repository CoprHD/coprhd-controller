/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.CustomConfigConstraint;
import com.emc.storageos.customconfigcontroller.CustomConfigResolver;
import com.emc.storageos.customconfigcontroller.CustomConfigType;
import com.emc.storageos.customconfigcontroller.CustomConfigTypeProvider;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.DataSourceVariable;
import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CustomConfig;
import com.emc.storageos.db.client.model.StringMap;

public class CustomConfigHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomConfigHandler.class);
    private DbClient dbClient;
    private Map<String, CustomConfigResolver> configResolvers;
    private CustomConfigTypeProvider configTypeProvider;
    private DataSourceFactory datasourceFactory;

    public DataSourceFactory getDatasourceFactory() {
        return datasourceFactory;
    }

    public void setDatasourceFactory(DataSourceFactory datasourceFactory) {
        this.datasourceFactory = datasourceFactory;
    }

    public void setDbClient(DbClient dbc) {
        dbClient = dbc;
    }

    public Map<String, CustomConfigResolver> getConfigResolvers() {
        return configResolvers;
    }

    public void setConfigResolvers(Map<String, CustomConfigResolver> configResolvers) {
        this.configResolvers = configResolvers;
    }

    public CustomConfigTypeProvider getConfigTypeProvider() {
        return configTypeProvider;
    }

    public void setConfigTypeProvider(CustomConfigTypeProvider configTypeProvider) {
        this.configTypeProvider = configTypeProvider;
    }

    /**
     * Get all the defined configuration typess.
     * 
     * @return a list of all configuration typess defined in the system.
     */
    public List<CustomConfigType> getCustomConfigTypes() {
        return configTypeProvider.getCustomConfigTypes();
    }

    /**
     * Get the custom configuration type for a given configuration name.
     * 
     * @param name the configuration type name
     * @return the custom configuration type for the configuration name
     * @throws CustomConfigControllerException is a type could not be found
     *             for the configuration name
     */
    public CustomConfigType getCustomConfigType(String name) {
        return configTypeProvider.getCustomConfigType(name);
    }

    /**
     * Gets the resolved value for a given configuration name,
     * and scope.
     * 
     * @param configName the configuration type name
     * @param scope the scope to be used to find the configuration value.
     *            This is determined by first trying to find a configuration with
     *            exact scope, if one cannot be found then the global scope, and
     *            this cannot be found then the system default defined {@link CustomConfigType}
     * @return the resolved value for a given configuration name,
     *         and scope. This function is guaranteed to return a value for
     *         all valid inputs.
     */
    public String getCustomConfigValue(String configName, StringMap scope)
            throws CustomConfigControllerException {
        CustomConfig customConfig = null;
        CustomConfig globalConfig = null;
        CustomConfig systemDefaultConfig = null;
        String value = null;

        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getCustomConfigByConfigType(configName),
                results);
        while (results.iterator().hasNext()) {
            CustomConfig tmpConfig = dbClient.queryObject(CustomConfig.class, results.iterator().next());
            if (tmpConfig == null || tmpConfig.getInactive() == true || !tmpConfig.getRegistered()) {
                continue;
            }
            StringMap tmpscope = tmpConfig.getScope();
            if (tmpscope != null && scope != null && tmpscope.equals(scope)) {
                if (tmpConfig.getSystemDefault()) {
                    systemDefaultConfig = tmpConfig;
                } else {
                    customConfig = tmpConfig;
                    break;
                }
            } else if (tmpscope != null && tmpscope.containsKey(CustomConfigConstants.GLOBAL_KEY)) {
                if (tmpConfig.getSystemDefault()) {
                    if (systemDefaultConfig == null) {
                        systemDefaultConfig = tmpConfig;
                    }
                } else {
                    globalConfig = tmpConfig;
                }
            }
        }
        if (customConfig != null) {
            logger.info("Found the custom config {} for {}", configName, scope);
            value = customConfig.getValue();
        } else if (globalConfig != null) {
            logger.info("Could not find custom config {} for {}. The global custom config will be used.", configName, scope);
            value = globalConfig.getValue();
        } else if (systemDefaultConfig != null) {
            logger.info("Could not find custom config {} for {}. The system default config will be used.", configName, scope);
            value = systemDefaultConfig.getValue();
        } else {
            String key = scope.keySet().iterator().next();
            throw CustomConfigControllerException.exceptions.customConfigScopeWithNoDefault(configName, key, scope.get(key));
        }

        return value;
    }

    /**
     * Gets the resolved custom name for a given CustomName configuration type
     * name, a scope and the list of values specified in the data source.
     * 
     * @param name the configuration type name
     * @param scope the scope to be used to find the configuration value. See {@link #getCustomConfigValue(String, StringMap)} for details.
     * @param dataSource the object containing the variable values
     *            to be used replaced in the CustomName mask.
     * @return resolved custom name for a given CustomName configuration
     */
    public String getComputedCustomConfigValue(String name, StringMap scope,
            DataSource dataSource) throws CustomConfigControllerException {
        CustomConfigType item = getCustomConfigType(name);
        String value = getCustomConfigValue(name, scope);
        CustomConfigResolver resolver = configResolvers.get(item.getConfigType());
        String result = resolver.resolve(item, scope, value, dataSource);

        // Apply the constraints
        List<CustomConfigConstraint> constraints = item.getConstraints();
        String systemType = CustomConfigConstants.DEFAULT_KEY;
        if (scope != null) {
            systemType = scope.get(CustomConfigConstants.SYSTEM_TYPE_SCOPE);
            // if the system type scope is not available, check for host type scope.
            // host type scope is only available for Hitachi Host Mode Option
            if (systemType == null) {
                systemType = scope.get(CustomConfigConstants.HOST_TYPE_SCOPE);
            }
        }

        for (CustomConfigConstraint constraint : constraints) {
            result = constraint.applyConstraint(result, systemType);
        }

        return result;
    }

    /**
     * Resolves custom name for a given CustomName configuration type
     * name, a scope and the list of values specified in the data source
     *
     * @param name the configuration type name
     * @param scope the scope to be used to find the configuration value.
     * @param dataSource the object containing the variable values
     *            to be used replaced in the CustomName mask.
     * @return resolved custom name for a given CustomName configuration
     */
    public String resolve(String name, String scope,
            DataSource dataSource) throws CustomConfigControllerException {
        StringMap scopeMap = new StringMap();
        CustomConfigType item = getCustomConfigType(name);
        if (item != null) {
            for (String key : item.getScope().keySet()) {
                List<String> scopeVals = java.util.Arrays.asList(item.getScope().get(key).split(","));
                if (scopeVals.contains(scope)) {
                    scopeMap.put(key, scope);
                }
            }
        }
        String value = getCustomConfigValue(name, scopeMap);
        CustomConfigResolver resolver = configResolvers.get(item.getConfigType());
        String result = resolver.resolve(item, scopeMap, value, dataSource);
        return result;
    }

    /**
     * Performs the necessary checks to ensure the user-specified value for a
     * configuration is valid.
     * <ol>
     * <li>if isCheckDuplicate is set to true, ensure that a user-created configuration does not already exist for this configuration type
     * and scope</li>
     * <li>check that a configuration type exists for the name</li>
     * <li>check that the scope is supported based on the definition of the scope in the type of this configuration</li>
     * <li>check that the value is of the right type and that it is compliant with the constraints defined for this configuration type</li>
     * </ol>
     * 
     * @param name the configuration type name
     * @param scope the scope of the configuration item
     * @param value the value of the configuration item in a string form
     * @param isCheckDuplicate true when uniqueness check is requested
     * @throws CustomConfigControllerException if any of the checks fail.
     */
    public void validate(String name, StringMap scope, String value, boolean isCheckDuplicate) {
        // check this is a supported config
        CustomConfigType item = getCustomConfigType(name);
        if (item == null) {
            throw CustomConfigControllerException.exceptions.configTypeNotFound(name);
        }

        // if this is a create, check for duplicates and also validate the scope
        if (isCheckDuplicate) {
            CustomConfig config = getUserDefinedCustomConfig(constructConfigName(name, scope));
            if (config != null && config.getScope().equals(scope)) {
                throw CustomConfigControllerException.exceptions.customConfigAlreadyExists(config.getLabel());
            }
            // check this is a supported scope
            for (String key : scope.keySet()) {
                if (!item.getScope().containsKey(key)) {
                    throw CustomConfigControllerException.exceptions.scopeTypeNotSupported(key, name);
                }
                List<String> scopeVals = java.util.Arrays.asList(item.getScope().get(key).split(","));
                if (!scopeVals.contains(scope.get(key))) {
                    throw CustomConfigControllerException.exceptions.scopeValueNotSupported(scope.get(key), key, name);
                }
            }
        }

        // this performs additional validations
        value = getCustomConfigPreviewValue(name, value, scope, null);

        // check the value against each constraint
        for (CustomConfigConstraint constraint : item.getConstraints()) {
            constraint.validate(value, scope.values().iterator().next());
        }
    }

    /**
     * Get the custom configuration item for the given label.
     * 
     * @param label which is the unique and fully qualified name of the configuration
     *            that includes the configuration name and its full scope.
     * @return the custom configuration item if a configuration item is found. Return
     *         null otherwise.
     */
    public CustomConfig getUserDefinedCustomConfig(String label) {
        CustomConfig value = null;
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(PrefixConstraint.Factory.getLabelPrefixConstraint(CustomConfig.class, label),
                results);

        while (results.iterator().hasNext()) {
            CustomConfig tmpConfig = dbClient.queryObject(CustomConfig.class, results.iterator().next());
            if (!tmpConfig.getSystemDefault()) {
                value = tmpConfig;
                break;
            }
        }

        return value;
    }

    /**
     * Uses the samples provided in the configuration item to generate a resolved
     * name for a given mask, configuration and scope. This can be used to preview
     * and validate a name mask prior to setting it.
     * 
     * @param name the configuration type name
     * @param value the name mask to be resolved.
     * @param scope the scope to be used to for deciding which constraints to apply.
     * @param variables a map of variable-name-to-variable-value. This map can contain
     *            a value for all or only some of the name mask variables, or it can be
     *            empty. Any missing variable will be replaced with the variable default.
     * @return the resolved name.
     * @throws CustomConfigControllerException for invalid input.
     */
    public String getCustomConfigPreviewValue(String name, String value, StringMap scope,
            Map<String, String> variables) {

        CustomConfigType item = getCustomConfigType(name);
        Map<DataSourceVariable, Boolean> dsVariables = item.getDataSourceVariables();
        DataSource sampleDatasource = null;
        if (dsVariables != null && !dsVariables.isEmpty()) {
            sampleDatasource = datasourceFactory.getSampleDataSource(name);
        }

        CustomConfigResolver resolver = configResolvers.get(item.getConfigType());
        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                sampleDatasource.addProperty(entry.getKey(), entry.getValue());
            }
        }

        // validate the computed value
        resolver.validate(item, scope, value);

        // Resolve the config value
        String computedName = resolver.resolve(item, scope, value, sampleDatasource);

        // validate against the constraints
        List<CustomConfigConstraint> constraints = item.getConstraints();
        String systemType = CustomConfigConstants.DEFAULT_KEY;
        if (scope != null) {
            systemType = scope.get(CustomConfigConstants.SYSTEM_TYPE_SCOPE);
            // if the system type scope is not available, check for host type scope.
            // host type scope is only available for Hitachi Host Mode Option
            if (systemType == null) {
                systemType = scope.get(CustomConfigConstants.HOST_TYPE_SCOPE);
            }
        }

        for (CustomConfigConstraint constraint : constraints) {
            constraint.validate(computedName, systemType);
        }

        return computedName;
    }

    /**
     * This is a short cut for the controller code to avoid having to create
     * a scope. In the future a scope factory can be added.
     */
    public String getComputedCustomConfigValue(String name, String scope,
            DataSource sources) throws CustomConfigControllerException {
        StringMap map = new StringMap();
        CustomConfigType item = getCustomConfigType(name);
        if (item != null) {
            for (String key : item.getScope().keySet()) {
                List<String> scopeVals = java.util.Arrays.asList(item.getScope().get(key).split(","));
                if (scopeVals.contains(scope)) {
                    map.put(key, scope);
                }
            }
        }
        return getComputedCustomConfigValue(name, map, sources);
    }

    /**
     * Short cut to getting the value as a boolean.
     */
    public Boolean getComputedCustomConfigBooleanValue(String name, String scope,
            DataSource sources) throws CustomConfigControllerException {
        return Boolean.valueOf(getComputedCustomConfigValue(name, scope, sources));
    }

    /**
     * Build the unique and fully qualified name of the configuration
     * that includes the configuration name and its full scope. Example:
     * systemtype.mds.ZoneName
     * 
     * @param configName the configuration type name
     * @param scope scope the scope of the configuration
     * @return the unique and fully qualified name of the configuration
     */
    public static String constructConfigName(String configName, StringMap scope) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : scope.entrySet()) {
            builder.append(entry.getKey());
            builder.append(".");
            builder.append(entry.getValue());
            builder.append(".");
        }
        builder.append(configName);
        return builder.toString();
    }

    /**
     * This function is called when the controller service is started. It reads
     * the custom configuration types, computes the list of system-defined
     * configurations that should be persisted in the database and updates the DB accordingly.
     * 
     */
    public void loadSystemCustomConfigs() {
        logger.debug("loadSystemCustomConfigs started");
        long start = System.currentTimeMillis();

        // get all existing system-define configs in the database
        Map<String, CustomConfig> dbSystemConfigsByLabel = getSystemConfigsFromDb();
        // compute the list of required system-define configs from the types
        Map<String, CustomConfig> templateConfigsByLabel = getTemplateConfigs();

        // Diff the two maps and update
        List<CustomConfig> created = new ArrayList<CustomConfig>();
        List<CustomConfig> updated = new ArrayList<CustomConfig>();
        List<CustomConfig> deleted = new ArrayList<CustomConfig>();
        CustomConfig curConfig = null;
        CustomConfig newConfig = null;
        for (String label : templateConfigsByLabel.keySet()) {
            if (dbSystemConfigsByLabel.containsKey(label)) {
                curConfig = dbSystemConfigsByLabel.get(label);
                newConfig = templateConfigsByLabel.get(label);
                // check if we need to update
                if (!newConfig.getValue().equals(curConfig.getValue())) {
                    curConfig.setValue(newConfig.getValue());
                    logger.info("System-defined CustomConfig {} will be updated", label);
                    updated.add(curConfig);
                }
                dbSystemConfigsByLabel.remove(label);
            } else {
                newConfig = templateConfigsByLabel.get(label);
                newConfig.setId(URIUtil.createId(CustomConfig.class));
                logger.info("System-defined CustomConfig {} will be created", label);
                created.add(newConfig);
            }
        }
        // any remaining system-defined custom configs should be deleted
        // any user-defined instances should also be deleted
        for (String label : dbSystemConfigsByLabel.keySet()) {
            logger.info("System-defined CustomConfig {} will be deleted.", label);
            deleted.add(dbSystemConfigsByLabel.get(label));
            curConfig = getUserDefinedCustomConfig(label);
            if (curConfig != null) {
                logger.info("User-defined CustomConfig {} will be delete with user-defined instance", label);
                deleted.add(curConfig);
            }
        }
        dbClient.markForDeletion(deleted);
        dbClient.createObject(created);
        dbClient.persistObject(updated);
        logger.info("loadSystemCustomConfigs results: Created: {}, Updated: {}, Deleted: {}",
                new Object[] { created.size(), updated.size(), deleted.size() });
        logger.debug("loadSystemCustomConfigs ended and took {}", (System.currentTimeMillis() - start));
    }

    /**
     * Retrieves the list of system-defined custom configurations from the
     * data base and create a map keyed by the custom configuration unique
     * and fully qualified label.
     * 
     * @return a map of db system-defined custom configurations keyed by the custom
     *         configuration unique and fully qualified label
     */
    private Map<String, CustomConfig> getSystemConfigsFromDb() {
        // get all existing configs in the database
        logger.debug("getSystemConfigsFromDb started");
        Map<String, CustomConfig> systemConfigsByName = new HashMap<String, CustomConfig>();
        Iterator<CustomConfig> curConfigsItr = dbClient.queryIterativeObjects(CustomConfig.class,
                dbClient.queryByType(CustomConfig.class, true), true);
        CustomConfig curConfig = null;
        while (curConfigsItr.hasNext()) {
            curConfig = curConfigsItr.next();
            if (curConfig != null && curConfig.getSystemDefault()) {
                systemConfigsByName.put(curConfig.getLabel(), curConfig);
            }
        }
        logger.debug("getSystemConfigsFromDb ended and found a total of {}" +
                " system-defined configs in the database", systemConfigsByName.size());
        return systemConfigsByName;
    }

    /**
     * Computes the list of system-defined custom configurations that should
     * be exist in the database from the custom configuration templates. Each
     * scope must have a system-defined default custom configuration . If none
     * is specified in the template, than the template 'default' will be used.
     * 
     * @return a map of the system-defined custom configurations that need to be
     *         in the database keyed by the custom configuration unique and fully qualified label
     */
    private Map<String, CustomConfig> getTemplateConfigs() {
        logger.debug("getTemplateConfigs started");
        CustomConfig customConfig;
        // the default value for the scope.
        String defaultValue;
        // the 'default' default value as defined in the template
        String masterDefaultValue;
        Map<String, String> defaultValuesMap;
        List<CustomConfigType> templates = configTypeProvider.getCustomConfigTypes();
        Map<String, CustomConfig> customConfigsMap = new HashMap<String, CustomConfig>();
        for (CustomConfigType template : templates) {
            defaultValuesMap = template.getDefaultValues();
            // if the template defines a 'default' default value, get it
            masterDefaultValue = defaultValuesMap.get(CustomConfigConstants.DEFAULT_KEY);
            for (String key : template.getScope().keySet()) {
                String[] vals = template.getScope().get(key).split(",");
                for (String val : vals) {
                    if (defaultValuesMap.containsKey(val)) {
                        // if a default value is specified in the template for this scope, use it
                        defaultValue = defaultValuesMap.get(val);
                    } else if (masterDefaultValue != null) {
                        // if a default value is NOT specified in the template for this scope,
                        // use the template 'default' default value
                        defaultValue = masterDefaultValue;
                    } else {
                        // A mistake made in the config file, either a scope or a 'default' default must be specified
                        throw CustomConfigControllerException.exceptions.customConfigScopeWithNoDefault(
                                template.getName(), key, val);
                    }
                    logger.debug("System-define CutomConfig item for {} " +
                            " and scope {}:{} is computed", new Object[] { template.getName(), key, val });
                    customConfig = createSystemConfig(template, key, val, defaultValue);
                    customConfigsMap.put(customConfig.getLabel(), customConfig);
                }
            }
        }
        logger.debug("getTemplateConfigs ended and found a total of {} " +
                " system-defined configs in the templates", customConfigsMap.size());
        return customConfigsMap;
    }

    private CustomConfig createSystemConfig(CustomConfigType template, String scopeType,
            String scopevalue, String value) {
        // create one
        CustomConfig curConfig = new CustomConfig();
        curConfig.setConfigType(template.getName());
        curConfig.setSystemDefault(true);
        curConfig.setValue(value);
        StringMap scope = new StringMap();
        scope.put(scopeType, scopevalue);
        curConfig.setScope(scope);
        curConfig.setLabel(CustomConfigHandler.constructConfigName(template.getName(), scope));
        curConfig.setRegistered(true);
        return curConfig;
    }

}
