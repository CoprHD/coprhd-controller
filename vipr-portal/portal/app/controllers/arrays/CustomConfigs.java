/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package controllers.arrays;

import com.emc.storageos.model.customconfig.ConfigTypeScopeParam;
import com.emc.storageos.model.customconfig.CustomConfigPreviewRep;
import com.emc.storageos.model.customconfig.CustomConfigRestRep;
import com.emc.storageos.model.customconfig.CustomConfigTypeRep;
import com.emc.storageos.model.customconfig.ScopeParam;
import com.emc.storageos.model.customconfig.VariableParam;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import play.mvc.With;
import util.CustomConfigUtils;
import util.MessagesUtils;
import util.datatable.DataTablesSupport;

import java.net.URI;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.google.common.collect.Lists.newArrayList;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class CustomConfigs extends ViprResourceController {

    public static void list() {
        render();
    }

    public static void configTypes() {
        List<CustomConfigTypeInfo> configTypes = newArrayList();

        for (CustomConfigTypeRep configTypeRep : CustomConfigUtils.getConfigTypes()) {
            configTypes.add(new CustomConfigTypeInfo(configTypeRep));
        }

        renderJSON(DataTablesSupport.createJSON(configTypes, params));
    }

    public static void preview(String configType, String scopeType, String scopeValue, String value) {
        try {
            CustomConfigPreviewRep preview = CustomConfigUtils.generatePreview(configType, scopeType, scopeValue, value);
            renderJSON(preview);
        } catch (ServiceErrorException ex) {
            ServiceErrorRestRep error = ex.getServiceError();
            renderJSON(error);
        }
    }

    public static void listConfigs() {
        List<CustomConfigRestRep> customConfigs = CustomConfigUtils.getCustomConfigs();
        Collections.sort(customConfigs, new CustomConfigRestRepComparator());
        performListJson(customConfigs, new RestRepToInfoOperation());
    }

    @FlashException
    public static void create(String configType, String scopeType, String scopeValue, String value) {
        try {
            CustomConfigUtils.createCustomConfig(configType, scopeType, scopeValue, value);
        } catch (ServiceErrorException ex) {
            flash.error(MessagesUtils.get("CustomConfigs.error.create",
                    value,
                    MessagesUtils.get("CustomConfigs.configType." + configType)
            ));
        }
    }

    @FlashException
    public static void update(String id, String configType, String scopeType, String scopeValue, String value) {
        CustomConfigRestRep customConfig = CustomConfigUtils.getCustomConfig(uri(id));

        ScopeParam scope = customConfig.getScope();

        try {
            if (!scope.getType().equals(scopeType) || !scope.getValue().equals(scopeValue)) {
                CustomConfigUtils.deleteCustomConfig(uri(id));
                CustomConfigUtils.createCustomConfig(configType, scopeType, scopeValue, value);
            } else {
                CustomConfigUtils.updateCustomConfig(uri(id), value);
            }
        } catch (ServiceErrorException ex) {
            flash.error(MessagesUtils.get("CustomConfigs.error.update",
                    value,
                    MessagesUtils.get("CustomConfigs.configType." + configType)
            ));
        }
    }

    @FlashException
    public static void delete(String id) {
        try {
            CustomConfigUtils.deleteCustomConfig(uri(id));
        } catch (ServiceErrorException ex) {
            flash.error(MessagesUtils.get("CustomConfigs.error.delete", id));
        }
    }

    static class RestRepToInfoOperation implements ResourceOperation<CustomConfigInfo, CustomConfigRestRep> {

        @Override
        public CustomConfigInfo performOperation(CustomConfigRestRep customConfig) {
            return new CustomConfigInfo(customConfig);
        }
    }

    static class CustomConfigTypeInfo {

        private List<ConfigTypeScopeParam> scopes;
        private String type;
        private List<VariableParam> variables;
        private List<String> rules;
        private String configType;
        private String metaType;

        public CustomConfigTypeInfo(CustomConfigTypeRep typeRep) {
            configType = typeRep.getConfigName();
            rules = (typeRep.getRules() != null) ? typeRep.getRules().getRules() : Collections.<String>emptyList();
            scopes = typeRep.getScopes().getScopes();
            type = typeRep.getType();
            variables = (typeRep.getVariables() != null) ? typeRep.getVariables().getVariables() : Collections.<VariableParam>emptyList();
            metaType = typeRep.getConfigType();
        }
    }

    public static class CustomConfigInfo {

        private URI id;
        private String configType;
        private ScopeParam scope;
        private String scopeType;
        private String scopeValue;
        private String value;
        private Boolean inactive;
//        private RestLinkRep link;
        private String name;
        private Boolean registered;
        private Boolean global;
        private Boolean internal;
        private Boolean remote;
        private Set<String> tags;
//        private URI vdc;
        private Boolean systemDefault;
        private Calendar creationTime;

        public CustomConfigInfo(CustomConfigRestRep customConfig) {
            id = customConfig.getId();
            String configName = customConfig.getName();
            configType = configName.substring(configName.lastIndexOf('.') + 1);
            scope = customConfig.getScope();
            scopeType = customConfig.getScope().getType();
            scopeValue = customConfig.getScope().getValue();
            value = customConfig.getValue();
            inactive = customConfig.getInactive();
//            link = customConfig.getLink();
            name = customConfig.getName();
            registered = customConfig.getRegistered();
            global = customConfig.getGlobal();
            internal = customConfig.getInternal();
            remote = customConfig.getRemote();
            tags = customConfig.getTags();
//            vdc = customConfig.getVdc().getLink().getLinkRef();
            systemDefault = customConfig.getSystemDefault();
            creationTime = customConfig.getCreationTime();
        }
    }

    static class CustomConfigRestRepComparator implements Comparator<CustomConfigRestRep> {

        @Override
        public int compare(CustomConfigRestRep c1, CustomConfigRestRep c2) {
            if (c1.getSystemDefault() != c2.getSystemDefault()) {
                return c1.getSystemDefault() ? -1 : 1;
            }
            return new ScopeComparator().compare(c1.getScope(), c2.getScope());
        }
    }

    static class ScopeComparator implements Comparator<ScopeParam> {

        @Override
        public int compare(ScopeParam s1, ScopeParam s2) {
            return    (s1.getType().compareTo(s2.getType()) != 0)   ? s1.getType().compareTo(s2.getType())
                    : (s1.getValue().compareTo(s2.getValue()) != 0) ? s1.getValue().compareTo(s2.getValue())
                    :                                                 0;
        }
    }
}
