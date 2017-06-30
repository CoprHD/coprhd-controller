/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.functions.MapCustomConfig;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.customconfigcontroller.CustomConfigConstraint;
import com.emc.storageos.customconfigcontroller.CustomConfigType;
import com.emc.storageos.customconfigcontroller.DataSourceVariable;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CustomConfig;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.customconfig.CustomConfigBulkRep;
import com.emc.storageos.model.customconfig.CustomConfigCreateParam;
import com.emc.storageos.model.customconfig.CustomConfigList;
import com.emc.storageos.model.customconfig.CustomConfigPreviewParam;
import com.emc.storageos.model.customconfig.CustomConfigPreviewRep;
import com.emc.storageos.model.customconfig.CustomConfigRestRep;
import com.emc.storageos.model.customconfig.CustomConfigRuleList;
import com.emc.storageos.model.customconfig.CustomConfigTypeList;
import com.emc.storageos.model.customconfig.CustomConfigTypeRep;
import com.emc.storageos.model.customconfig.CustomConfigUpdateParam;
import com.emc.storageos.model.customconfig.CustomConfigVariableList;
import com.emc.storageos.model.customconfig.PreviewVariableParam;
import com.emc.storageos.model.customconfig.RelatedConfigTypeRep;
import com.emc.storageos.model.customconfig.ScopeParam;
import com.emc.storageos.model.customconfig.ScopeParamList;
import com.emc.storageos.model.customconfig.SimpleValueRep;
import com.emc.storageos.model.customconfig.ConfigTypeScopeParam;
import com.emc.storageos.model.customconfig.VariableParam;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.security.authentication.RequestProcessingUtils;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * APIs to view, create, modify and remove configs
 */
@Path("/config/controller")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class CustomConfigService extends ResourceService {
    private static final Logger log = LoggerFactory.getLogger(CustomConfigService.class);
    // Constants for Events
    private static final String EVENT_SERVICE_TYPE = "Config";
    private static final String CONFIG_TYPE = "config_type";
    private static final String SYSTEM_DEFAULT = "system_default";
    private static final String SCOPE = "scope";
    private static final String VALUE = "value";
    private static final String SCOPE_DELIMETER = ",";
    private static final String NAME = "name";
    private static final String SIMPLE_VALUE_TYPE = "SimpleValue";

    @Autowired
    private CustomConfigHandler customConfigHandler;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * List custom configurations.
     * 
     * @brief List config names and ids
     * @return A reference to a CustomConfigList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public CustomConfigList getCustomConfigs() {
        CustomConfigList configList = new CustomConfigList();

        List<URI> ids = _dbClient.queryByType(CustomConfig.class, true);
        Iterator<CustomConfig> iter = _dbClient.queryIterativeObjects(CustomConfig.class, ids);
        while (iter.hasNext()) {
            configList.getCustomConfigs().add(toNamedRelatedResource(iter.next()));
        }
        return configList;
    }

    /**
     * Get config details
     * 
     * @param id the URN of a ViPRconfig.
     * 
     * @brief Show config
     * @return A reference to a CustomConfigRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public CustomConfigRestRep getCustomConfig(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, CustomConfig.class, "id");
        CustomConfig config = queryResource(id);
        return DbObjectMapper.map(config);
    }

    /**
     * Retrieve configs based on input ids.
     * 
     * 
     * @param param POST data containing the id list.
     * 
     * @brief Show data of requested configs
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public CustomConfigBulkRep getBulkResources(BulkIdParam param) {
        List<URI> ids = param.getIds();
        Iterator<CustomConfig> _dbIterator =
                _dbClient.queryIterativeObjects(CustomConfig.class, ids);
        return new CustomConfigBulkRep(BulkList.wrapping(_dbIterator, MapCustomConfig.getInstance()));
    }

    /**
     * @brief List all instances of config
     *        Retrieve all ids of config
     * 
     * @prereq none
     * @brief Retrieve bulk list of config ids
     * @return list of ids.
     */
    @GET
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BulkIdParam getBulkIds() {
        BulkIdParam ret = new BulkIdParam();
        ret.setIds(_dbClient.queryByType(CustomConfig.class, true));
        return ret;

    }

    /**
     * Deactivates the config.
     * When a config is deleted it will move to a "marked for deletion" state.
     * 
     * @prereq none
     * @param id the URN of a ViPR config
     * @brief Deactivate config
     * @return No data returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deactivateCustomConfig(@PathParam("id") URI id) {
        CustomConfig customConfig = getCustomConfigById(id, true);
        ArgValidator.checkReference(CustomConfig.class, id, checkForDelete(customConfig));
        if (customConfig.getSystemDefault()) {
            // system default could not be deleted
            throw APIException.badRequests.systemDefaultConfigCouldNotBeModifiedOrDeactivated(customConfig.getId());
        }
        customConfig.setRegistered(false);
        _dbClient.markForDeletion(customConfig);

        auditOp(OperationTypeEnum.DELETE_CONFIG, true, null, id.toString(),
                customConfig.getLabel(), customConfig.getScope());
        return Response.ok().build();
    }

    /**
     * Creates config.
     * 
     * @param createParam create parameters
     * @brief Create config
     * @return CustomConfigRestRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public CustomConfigRestRep createCustomConfig(CustomConfigCreateParam createParam) {
        String configType = createParam.getConfigType();
        String theVal = createParam.getValue();
        ArgValidator.checkFieldNotEmpty(configType, CONFIG_TYPE);
        ArgValidator.checkFieldNotEmpty(theVal, VALUE);
        ScopeParam scopeParam = createParam.getScope();
        ArgValidator.checkFieldNotNull(scopeParam, SCOPE);

        StringMap scopeMap = new StringMap();
        scopeMap.put(scopeParam.getType(), scopeParam.getValue());
        customConfigHandler.validate(configType, scopeMap, theVal, true);
        String label = CustomConfigHandler.constructConfigName(configType, scopeMap);
        CustomConfig config = new CustomConfig();
        config.setId(URIUtil.createId(CustomConfig.class));
        config.setConfigType(configType);

        config.setScope(scopeMap);
        config.setLabel(label);
        config.setValue(theVal);
        config.setRegistered(createParam.getRegistered());
        config.setSystemDefault(false);
        _dbClient.createObject(config);

        auditOp(OperationTypeEnum.CREATE_CONFIG, true, null, config.getId().toString(),
                config.getLabel(), config.getScope());
        return DbObjectMapper.map(config);
    }

    /**
     * Modify a config.
     * 
     * @param id URN of the config
     * @param param create parameters
     * @brief Modify config
     * @return NamedRelatedResourceRep
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public CustomConfigRestRep updateCustomConfig(@PathParam("id") URI id, CustomConfigUpdateParam param) {
        CustomConfig config = getCustomConfigById(id, true);
        if (config.getSystemDefault()) {
            // system default could not be modified
            throw APIException.badRequests.systemDefaultConfigCouldNotBeModifiedOrDeactivated(config.getId());
        }

        customConfigHandler.validate(config.getConfigType(), config.getScope(), param.getValue(), false);

        if (param.getValue() != null && !param.getValue().isEmpty()) {
            config.setValue(param.getValue());
        }

        _dbClient.updateAndReindexObject(config);
        auditOp(OperationTypeEnum.UPDATE_CONFIG, true, null, config.getId().toString(),
                config.getLabel(), config.getScope());
        return DbObjectMapper.map(config);
    }

    /**
     * Register a config.
     * 
     * @param id URN of the config
     * @brief Register config
     * @return NamedRelatedResourceRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/register")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public CustomConfigRestRep registerCustomConfig(@PathParam("id") URI id) {
        CustomConfig config = getCustomConfigById(id, true);
        if (config.getRegistered()) {
            return DbObjectMapper.map(config);
        }

        config.setRegistered(true);
        _dbClient.updateAndReindexObject(config);
        auditOp(OperationTypeEnum.REGISTER_CONFIG, true, null, config.getId().toString(),
                config.getLabel(), config.getScope());
        return DbObjectMapper.map(config);
    }

    /**
     * Deregister a config.
     * 
     * @param id URN of the config
     * @brief Deregister config
     * @return NamedRelatedResourceRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public CustomConfigRestRep deregisterCustomConfig(@PathParam("id") URI id) {
        CustomConfig config = getCustomConfigById(id, true);
        if (config.getSystemDefault()) {
            throw APIException.badRequests.systemDefaultConfigCouldNotBeModifiedOrDeactivated(config.getId());
        }
        config.setRegistered(false);
        _dbClient.updateAndReindexObject(config);
        auditOp(OperationTypeEnum.DEREGISTER_CONFIG, true, null, config.getId().toString(),
                config.getLabel(), config.getScope());
        return DbObjectMapper.map(config);
    }

    /**
     * Get a config preview value.
     * 
     * @param param create parameters
     * @brief Get config preview value
     * @return preview value
     */
    @POST
    @Path("/preview")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public CustomConfigPreviewRep getCustomConfigPreviewValue(CustomConfigPreviewParam param) {
        String configType = param.getConfigType();
        String theVal = param.getValue();
        ArgValidator.checkFieldNotEmpty(configType, CONFIG_TYPE);
        ArgValidator.checkFieldNotEmpty(theVal, VALUE);
        ScopeParam scopeParm = param.getScope();
        StringMap scope = null;
        if (scopeParm != null) {
            scope = new StringMap();
            scope.put(scopeParm.getType(), scopeParm.getValue());
        }
        List<PreviewVariableParam> variables = param.getPreviewVariables();
        Map<String, String> variableValues = null;
        if (variables != null && !variables.isEmpty()) {
            variableValues = new HashMap<String, String>();
            for (PreviewVariableParam variable : variables) {
                variableValues.put(variable.getVariableName(), variable.getValue());
            }
        }
        String result = customConfigHandler.getCustomConfigPreviewValue(configType, theVal,
                scope, variableValues);
        CustomConfigPreviewRep preview = new CustomConfigPreviewRep(result);
        return preview;
    }

    /**
     * List config types.
     * 
     * @brief List of config types
     * @return The list of config types.
     */
    @GET
    @Path("/types")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public CustomConfigTypeList getCustomConfigTypes() {
        List<CustomConfigType> items = customConfigHandler.getCustomConfigTypes();
        List<RelatedConfigTypeRep> types = new ArrayList<RelatedConfigTypeRep>();
        for (CustomConfigType item : items) {
            RelatedConfigTypeRep type = new RelatedConfigTypeRep();
            // build config type Link
            String service = ResourceTypeEnum.CONFIG_TYPE.getService();
            StringBuilder build = (new StringBuilder(service)).
                    append('/').append(item.getName());
            type.setConfigName(item.getName());
            try {
                type.setSelfLink(new RestLinkRep("self", new URI(build.toString())));
            } catch (URISyntaxException e) {
                // it should not happen
            }
            types.add(type);
        }
        return new CustomConfigTypeList(types);

    }

    /**
     * Show config type.
     * 
     * @brief Show config type details
     * @return The config type data.
     */
    @GET
    @Path("/types/{config_name}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public CustomConfigTypeRep getCustomConfigType(@PathParam("config_name") String configName) {

        CustomConfigType item = customConfigHandler.getCustomConfigType(configName);
        CustomConfigTypeRep result = new CustomConfigTypeRep();
        if (item == null) {
            log.info("No config type found for :", configName);
            throw APIException.badRequests.invalidConfigType(configName);
        }
        result.setConfigName(configName);
        result.setType(item.getType());
        result.setConfigType(item.getConfigType());
        Map<DataSourceVariable, Boolean> dataSources = item.getDataSourceVariables();
        if (dataSources != null && !dataSources.isEmpty()) {
            List<VariableParam> variables = new ArrayList<VariableParam>();
            for (Map.Entry<DataSourceVariable, Boolean> entry : dataSources.entrySet()) {
                DataSourceVariable datasource = entry.getKey();
                VariableParam variable = new VariableParam();
                variable.setName(datasource.getDisplayName());
                variable.setSampleValue(datasource.getSample());
                variable.setIsRecommended(entry.getValue());
                variables.add(variable);
            }
            CustomConfigVariableList variableList = new CustomConfigVariableList(variables);
            result.setVariables(variableList);
        }
        Map<String, String> scopes = item.getScope();
        if (scopes != null && !scopes.isEmpty()) {
            List<ConfigTypeScopeParam> scopeParms = new ArrayList<ConfigTypeScopeParam>();
            for (Map.Entry<String, String> entry : scopes.entrySet()) {
                String type = entry.getKey();
                String value = entry.getValue();
                List<String> values = new ArrayList<String>();
                if (value.contains(SCOPE_DELIMETER)) {
                    values = java.util.Arrays.asList(value.split(SCOPE_DELIMETER));
                } else {
                    values.add(value);
                }
                ConfigTypeScopeParam scopeparm = new ConfigTypeScopeParam(type, values);
                scopeParms.add(scopeparm);
            }
            ScopeParamList scopeList = new ScopeParamList(scopeParms);
            result.setScopes(scopeList);
        }
        // get rules
        List<CustomConfigConstraint> constraints = item.getConstraints();

        if (constraints != null && !constraints.isEmpty()) {
            List<String> rules = new ArrayList<String>();
            for (CustomConfigConstraint constraint : constraints) {
                rules.add(constraint.getName());
            }
            CustomConfigRuleList ruleList = new CustomConfigRuleList(rules);
            result.setRules(ruleList);
        }
        return result;
    }

    /**
     * Search configs
     * <p>
     * Users could search configs by name, or config_name, or scope or system_default flag. e.g. /search?name=;
     * /search?config_name=SanZoneName; /search?config_name=SanZoneName&&scope=systemType.mds
     * 
     * @brief Search configs
     * @return search result
     */
    @GET
    @Path("/search")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SearchResults search() {
        Map<String, List<String>> parameters = uriInfo.getQueryParameters();
        // remove non-search related common parameters
        parameters.remove(RequestProcessingUtils.REQUESTING_COOKIES);
        SearchedResRepList resRepList = null;
        SearchResults result = new SearchResults();
        String name = null;
        if (parameters.containsKey(NAME)) {
            name = parameters.get(NAME).get(0);
            ArgValidator.checkFieldNotEmpty(name, NAME);
            resRepList = new SearchedResRepList(getResourceType());
            _dbClient.queryByConstraint(
                    PrefixConstraint.Factory.getLabelPrefixConstraint(CustomConfig.class, name),
                    resRepList);
            String systemDefault = null;
            if (parameters.containsKey(SYSTEM_DEFAULT)) {
                systemDefault = parameters.get(SYSTEM_DEFAULT).get(0);
                List<SearchResultResourceRep> searchResultList = new ArrayList<SearchResultResourceRep>();
                Iterator<SearchResultResourceRep> it = resRepList.iterator();

                while (it.hasNext()) {
                    SearchResultResourceRep rp = it.next();
                    URI id = rp.getId();
                    CustomConfig config = queryResource(id);
                    if (systemDefault.equals(config.getSystemDefault().toString())) {
                        RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), id));
                        SearchResultResourceRep searchResult = new SearchResultResourceRep(id, selfLink, config.getLabel());
                        searchResultList.add(searchResult);
                    }
                }
                result.setResource(searchResultList);
            } else {

                result.setResource(resRepList);
            }
        } else if (parameters.containsKey(CONFIG_TYPE)) {
            String configName = parameters.get(CONFIG_TYPE).get(0);

            // Validate the user passed a value for the config type.
            ArgValidator.checkFieldNotEmpty(configName, CONFIG_TYPE);

            StringMap scopeMap = null;
            if (parameters.containsKey(SCOPE)) {
                String scope = parameters.get(SCOPE).get(0);
                scopeMap = new StringMap();
                if (scope.contains(".")) {
                    String[] scopeSplits = scope.split("\\.");
                    scopeMap.put(scopeSplits[0], scopeSplits[1]);
                } else {
                    throw APIException.badRequests.invalidScopeFomart(scope);

                }

            }

            String systemDefault = null;
            if (parameters.containsKey(SYSTEM_DEFAULT)) {
                systemDefault = parameters.get(SYSTEM_DEFAULT).get(0);
            }
            List<SearchResultResourceRep> searchResultList = new ArrayList<SearchResultResourceRep>();

            List<CustomConfig> configList = getCustomConfig(configName, scopeMap);

            for (CustomConfig config : configList) {
                if (config.getInactive()) {
                    continue;
                }
                if (systemDefault != null &&
                        !systemDefault.equals(config.getSystemDefault().toString())) {
                    continue;
                }
                RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), config.getId()));
                SearchResultResourceRep searchResult = new SearchResultResourceRep(config.getId(), selfLink, config.getLabel());
                searchResultList.add(searchResult);

            }
            result.setResource(searchResultList);

        } else if (parameters.containsKey(SYSTEM_DEFAULT)) {
            // search parameters only contains system_default
            List<SearchResultResourceRep> searchResultList = new ArrayList<SearchResultResourceRep>();
            String systemDefault = parameters.get(SYSTEM_DEFAULT).get(0);
            List<URI> ids = _dbClient.queryByType(CustomConfig.class, true);
            Iterator<CustomConfig> iter = _dbClient.queryIterativeObjects(CustomConfig.class, ids);
            while (iter.hasNext()) {
                CustomConfig config = iter.next();
                if (systemDefault.equals(config.getSystemDefault().toString())) {
                    RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), config.getId()));
                    SearchResultResourceRep searchResult = new SearchResultResourceRep(config.getId(), selfLink, config.getLabel());
                    searchResultList.add(searchResult);
                }
            }
            result.setResource(searchResultList);

        }
        return result;
    }

    protected CustomConfig queryResource(URI id) {
        CustomConfig config = getCustomConfigById(id, false);
        return config;
    }

    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.CUSTOM_CONFIG;
    }

    /**
     * Get CustomConfig object from id
     * 
     * @param id the URN of a ViPR CustomConfig
     * @return
     */
    private CustomConfig getCustomConfigById(URI id, boolean checkInactive) {
        if (id == null) {
            return null;
        }

        CustomConfig ret = _permissionsHelper.getObjectById(id, CustomConfig.class);

        ArgValidator.checkEntity(ret, id, isIdEmbeddedInURL(id), checkInactive);
        return ret;
    }

    /**
     * Get config instance matching config type and scope
     * 
     * @param configType config type e.g. SanZoneName
     * @param scope
     * @return CustomConfig instance
     */
    private List<CustomConfig> getCustomConfig(String configType, StringMap scope) {
        List<CustomConfig> configList = new ArrayList<CustomConfig>();

        URIQueryResultList results = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getCustomConfigByConfigType(configType),
                results);

        while (results.iterator().hasNext()) {
            CustomConfig tmpConfig = _dbClient.queryObject(CustomConfig.class, results.iterator().next());
            if (scope == null || scope.isEmpty()) {
                configList.add(tmpConfig);
                continue;
            } else {
                Map<String, String> tmpscope = tmpConfig.getScope();
                if (tmpscope != null && scope != null && tmpscope.equals(scope)) {
                    configList.add(tmpConfig);
                    log.debug("Found the custom config {} for {}", configType, scope);
                    break;
                }
            }
        }
        return configList;
    }
    
    /**
     * Get the custom config value set in ViPR. This is valid for simple value config type only
     * 
     * @brief Show config type details
     * @return The config type data.
     */
    @GET
    @Path("/types/{config_name}/value")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SimpleValueRep getCustomConfigTypeValue(@PathParam("config_name") String configName,
        @QueryParam("scope") String scope ) {
        ArgValidator.checkFieldNotEmpty(configName, "configName");
        CustomConfigType item = customConfigHandler.getCustomConfigType(configName);
        if (item != null && !SIMPLE_VALUE_TYPE.equals(item.getConfigType())) {
            throw APIException.badRequests.invalidConfigValueType(configName);
        }
        SimpleValueRep result = new SimpleValueRep();
        if (item != null) {
            String value = customConfigHandler.getComputedCustomConfigValue(configName, scope, null);
            result.setValue(value);
        } else {
            log.info(String.format("Invalid config type for %s", configName));
            throw APIException.badRequests.invalidConfigType(configName);
        }
        
        return result;
    }

}
