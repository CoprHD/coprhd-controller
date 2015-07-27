/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.customconfig.CustomConfigBulkRep;
import com.emc.storageos.model.customconfig.CustomConfigCreateParam;
import com.emc.storageos.model.customconfig.CustomConfigList;
import com.emc.storageos.model.customconfig.CustomConfigPreviewParam;
import com.emc.storageos.model.customconfig.CustomConfigPreviewRep;
import com.emc.storageos.model.customconfig.CustomConfigRestRep;
import com.emc.storageos.model.customconfig.CustomConfigTypeList;
import com.emc.storageos.model.customconfig.CustomConfigTypeRep;
import com.emc.storageos.model.customconfig.CustomConfigUpdateParam;
import com.emc.storageos.model.customconfig.RelatedConfigTypeRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

public class CustomConfigs extends AbstractBulkResources<CustomConfigRestRep> {

    public CustomConfigs(RestClient client) {
        super(client, CustomConfigRestRep.class, PathConstants.CUSTOM_CONFIG_URL);
    }

    public List<CustomConfigRestRep> getCustomConfigs() {
        CustomConfigList response = client.get(CustomConfigList.class, baseUrl);
        return defaultList(getByRefs(response.getCustomConfigs()));
    }

    public SearchResults search(String configType, String scope, String name, Boolean systemDefault) {
        UriBuilder builder = client.uriBuilder(getSearchUrl());
        addQueryParam(builder, "config_type", configType);
        addQueryParam(builder, "scope", scope);
        addQueryParam(builder, "name", name);
        addQueryParam(builder, "system_default", systemDefault);

        return client.getURI(SearchResults.class, builder.build());
    }

    private void addQueryParam(UriBuilder builder, String key, Object value) {
        if (value != null) {
            builder.queryParam(key, value);
        }
    }

    public CustomConfigRestRep getCustomConfig(URI id) {
        return client.get(CustomConfigRestRep.class, getIdUrl(), id);
    }

    @Override
    protected List<CustomConfigRestRep> getBulkResources(BulkIdParam input) {
        CustomConfigBulkRep response = client.post(CustomConfigBulkRep.class, input, getBulkUrl());
        return defaultList(response.getCustomConfigs());
    }

    public List<CustomConfigTypeRep> getCustomConfigTypes() {
        CustomConfigTypeList list = client.get(CustomConfigTypeList.class, PathConstants.CUSTOM_CONFIG_TYPE_URL);

        List<CustomConfigTypeRep> configTypes = new ArrayList<>();
        for (RelatedConfigTypeRep configTypeRep : list.getConfigTypes()) {
            CustomConfigTypeRep template = getCustomConfigType(configTypeRep.getConfigName());
            configTypes.add(template);
        }
        return configTypes;
    }

    public CustomConfigTypeRep getCustomConfigType(String configType) {
        return client.get(CustomConfigTypeRep.class, PathConstants.CUSTOM_CONFIG_TYPE_URL + "/{config_type}", configType);
    }

    public CustomConfigPreviewRep getCustomConfigPreviewValue(CustomConfigPreviewParam param) {
        return client.post(CustomConfigPreviewRep.class, param, PathConstants.CUSTOM_CONFIG_PREVIEW_URL);
    }

    public CustomConfigRestRep createCustomConfig(CustomConfigCreateParam param) {
        return client.post(CustomConfigRestRep.class, param, baseUrl);
    }

    public CustomConfigRestRep updateCustomConfig(URI id, CustomConfigUpdateParam param) {
        return client.put(CustomConfigRestRep.class, param, getIdUrl(), id);
    }

    public CustomConfigRestRep deregisterCustomConfigs(URI id) {
        return client.post(CustomConfigRestRep.class, getDeregisterUrl(), id);
    }

    public CustomConfigRestRep register(URI id) {
        return client.post(CustomConfigRestRep.class, getRegisterUrl(), id);
    }

    public void deactivateCustomConfig(URI id) {
        doDeactivate(id);
    }
//
//    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
//        CustomConfigList response = client.get(CustomConfigList.class, path, args);
//        return defaultList(response.getCustomConfigs());
//    }
}
