/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import com.emc.storageos.model.customconfig.CustomConfigCreateParam;
import com.emc.storageos.model.customconfig.CustomConfigPreviewParam;
import com.emc.storageos.model.customconfig.CustomConfigPreviewRep;
import com.emc.storageos.model.customconfig.CustomConfigRestRep;
import com.emc.storageos.model.customconfig.CustomConfigTypeRep;
import com.emc.storageos.model.customconfig.CustomConfigUpdateParam;
import com.emc.storageos.model.customconfig.ScopeParam;
import com.emc.storageos.model.search.SearchResults;

import java.net.URI;
import java.util.List;

import static util.BourneUtil.getViprClient;

public class CustomConfigUtils {

    public static List<CustomConfigTypeRep> getConfigTypes() {
        return getViprClient().customConfigs().getCustomConfigTypes();
    }

    public static CustomConfigRestRep getCustomConfig(URI id) {
        return getViprClient().customConfigs().get(id);
    }

    public static List<CustomConfigRestRep> getCustomConfigs() {
        return getViprClient().customConfigs().getCustomConfigs();
    }

    public static List<CustomConfigRestRep> getCustomConfigs(String configType) {
        SearchResults searchResults = getViprClient().customConfigs().search(configType, null, null, null);
        return getViprClient().customConfigs().getByRefs(searchResults.getResource());

    }

    public static CustomConfigRestRep createCustomConfig(String configType, String scopeType, String scopeValue, String value) {
        ScopeParam scope = new ScopeParam();
        scope.setType(scopeType);
        scope.setValue(scopeValue);

        CustomConfigCreateParam param = new CustomConfigCreateParam();
        param.setConfigType(configType);
        param.setScope(scope);
        param.setValue(value);

        return getViprClient().customConfigs().createCustomConfig(param);
    }

    public static CustomConfigRestRep updateCustomConfig(URI id, String value) {
        CustomConfigUpdateParam param = new CustomConfigUpdateParam();
        param.setValue(value);

        return getViprClient().customConfigs().updateCustomConfig(id, param);
    }

    public static void deleteCustomConfig(URI id) {
        getViprClient().customConfigs().deactivateCustomConfig(id);
    }

    public static CustomConfigPreviewRep generatePreview(String configType, String scopeType, String scopeValue, String value) {
        CustomConfigPreviewParam param = new CustomConfigPreviewParam();
        param.setConfigType(configType);
        param.setScope(new ScopeParam(scopeType, scopeValue));
        param.setValue(value);

        return getViprClient().customConfigs().getCustomConfigPreviewValue(param);
    }
}
