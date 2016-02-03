/*
 * Copyright (c) 2015 EMC Corporation
 * Copyright 2016 Intel Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.auth.*;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

/**
 * This class maps between Authn Provider object model and rest representations
 */
public class AuthMapper {
    public static final AuthnProviderRestRep map(AuthnProvider from) {
        if (from == null) {
            return null;
        }
        AuthnProviderRestRep to = new AuthnProviderRestRep();
        mapDataObjectFields(from, to);
        to.setMode(from.getMode());
        to.setDomains(from.getDomains());
        to.setSearchFilter(from.getSearchFilter());
        to.setSearchScope(from.getSearchScope());
        to.setManagerDN(from.getManagerDN());
        to.setManagerPassword(from.getManagerPassword());
        to.setSearchBase(from.getSearchBase());
        to.setGroupAttribute(from.getGroupAttribute());
        to.setServerUrls(from.getServerUrls());
        to.setGroupWhitelistValues(from.getGroupWhitelistValues());
        to.setDisable(from.getDisable());
        to.setAutoRegisterOpenStackProjects(from.getAutoRegisterOpenStackProjects());
        to.setDescription(from.getDescription());
        to.setMaxPageSize(from.getMaxPageSize());
        to.setGroupObjectClasses(from.getGroupObjectClassNames());
        to.setGroupMemberAttributes(from.getGroupMemberAttributeTypeNames());
        return to;
    }

    public static final AuthnProvider map(AuthnCreateParam from) {
        AuthnProvider authn = new AuthnProvider();
        if (from.getManagerDn() != null) {
            authn.setManagerDN(from.getManagerDn());
        }
        if (from.getManagerPassword() != null) {
            authn.setManagerPassword(from.getManagerPassword());
        }
        if (from.getDisable() != null) {
            authn.setDisable(from.getDisable());
        }
        if (from.getAutoRegisterOpenStackProjects() != null) {
            authn.setAutoRegisterOpenStackProjects(from.getAutoRegisterOpenStackProjects());
        }
        StringSet urlStringSet = null;
        if (from.getServerUrls() != null && !from.getServerUrls().isEmpty()) {
        	urlStringSet = new StringSet();
        	urlStringSet.addAll(from.getServerUrls());
            authn.setServerUrls(urlStringSet);
        }
        if (from.getMode() != null) {
            authn.setMode(from.getMode());
        }
        if (from.getLabel() != null) {
            authn.setLabel(from.getLabel());
        }
        if (from.getDescription() != null) {
            authn.setDescription(from.getDescription());
        }
        if (from.getGroupAttribute() != null) {
            authn.setGroupAttribute(from.getGroupAttribute());
        }
        StringSet ss = null;
        if (from.getGroupWhitelistValues() != null && !from.getGroupWhitelistValues().isEmpty()) {
            ss = new StringSet();
            ss.addAll(from.getGroupWhitelistValues());
            authn.setGroupWhitelistValues(ss);
        }
        if (from.getDomains() != null && !from.getDomains().isEmpty()) {
            StringSet trimmedDomains = new StringSet();
            for (String domain : from.getDomains()) {
                // Strip whitespace and convert domain to lowercase
                trimmedDomains.add(domain.trim().toLowerCase());
            }
            authn.setDomains(trimmedDomains);
        }
        
        if (from.getSearchBase() != null) {
            authn.setSearchBase(from.getSearchBase());
        }
        if (from.getSearchFilter() != null) {
            authn.setSearchFilter(from.getSearchFilter());
        }
        if (from.getSearchScope() != null) {
            authn.setSearchScope(from.getSearchScope());
        }

        if (from.getMaxPageSize() != null) {
            authn.setMaxPageSize(from.getMaxPageSize());
        }

        if (from.getGroupObjectClasses() != null) {
            ss = new StringSet();
            ss.addAll(from.getGroupObjectClasses());
            authn.setGroupObjectClassNames(ss);
        }

        if (from.getGroupMemberAttributes() != null) {
            ss = new StringSet();
            ss.addAll(from.getGroupMemberAttributes());
            authn.setGroupMemberAttributeTypeNames(ss);
        }

        return authn;
    }

    /**
     * Generate an AuthnProviderParams object from an input rest AuthnProviderBaseParam
     * and db model provider. It merges the two.
     * 
     * @param param the input parameter to post or put
     * @param provider the existing provider if applicable
     * @return AuthnProviderParamsToValidate
     */
    public static final AuthnProviderParamsToValidate mapToValidateCommon(AuthnProviderBaseParam param,
            AuthnProvider provider) {
        AuthnProviderParamsToValidate authnToValidate = new AuthnProviderParamsToValidate();
        if (provider == null) {
            authnToValidate.setManagerDN(param.getManagerDn());
            authnToValidate.setManagerPwd(param.getManagerPassword());
            authnToValidate.setSearchBase(param.getSearchBase());
            authnToValidate.setGroupAttr(param.getGroupAttribute());
            authnToValidate.setMode(param.getMode());
        } else {
            authnToValidate.setManagerDN((param.getManagerDn() == null) ? provider.getManagerDN() : param.getManagerDn());
            authnToValidate
                    .setManagerPwd((param.getManagerPassword() == null) ? provider.getManagerPassword() : param.getManagerPassword());
            authnToValidate.setSearchBase((param.getSearchBase() == null) ? provider.getSearchBase() : param.getSearchBase());
            authnToValidate.setGroupAttr((param.getGroupAttribute() == null) ? provider.getGroupAttribute() : param.getGroupAttribute());
            authnToValidate.setMode((param.getMode() == null) ? provider.getMode() : param.getMode());
        }
        return authnToValidate;
    }

    /**
     * Generate an AuthnProviderParamsToValidate object from an
     * input rest AuthnCreateParam.
     * 
     * @param param the input parameter to post
     * @param provider the existing provider if applicable
     * @return AuthnProviderParamsToValidate
     */
    public static final AuthnProviderParamsToValidate mapToValidateCreate(AuthnCreateParam param,
            AuthnProvider provider) {
        AuthnProviderParamsToValidate authnToValidate = mapToValidateCommon(param, provider);
        if (provider == null) {
            authnToValidate.getGroupObjectClasses().addAll(param.getGroupObjectClasses());
            authnToValidate.getGroupMemberAttributes().addAll(param.getGroupMemberAttributes());
        } else {
            authnToValidate.getGroupObjectClasses().addAll(param.getGroupObjectClasses().isEmpty() ?
                    provider.getGroupObjectClassNames() : param.getGroupObjectClasses());
            authnToValidate.getGroupMemberAttributes().addAll(param.getGroupMemberAttributes().isEmpty() ?
                    provider.getGroupMemberAttributeTypeNames() : param.getGroupMemberAttributes());
        }

        return authnToValidate;
    }

    /**
     * Generate an AuthnProviderParamsToValidate object from an
     * input rest AuthnUpdateParam.
     * 
     * @param param the input parameter to put
     * @param provider the existing provider if applicable
     * @return AuthnProviderParamsToValidate
     */
    public static final AuthnProviderParamsToValidate mapToValidateUpdate(AuthnUpdateParam param,
            AuthnProvider provider) {
        AuthnProviderParamsToValidate authnToValidate = mapToValidateCommon(param, provider);
        if (provider == null) {
            authnToValidate.getGroupObjectClasses().addAll(param.getGroupObjectClassChanges().getAdd());
            authnToValidate.getGroupMemberAttributes().addAll(param.getGroupMemberAttributeChanges().getAdd());
        } else {
            authnToValidate.getGroupObjectClasses().addAll(param.getGroupObjectClassChanges().getAdd().isEmpty() ?
                    provider.getGroupObjectClassNames() : param.getGroupObjectClassChanges().getAdd());
            authnToValidate.getGroupMemberAttributes().addAll(param.getGroupMemberAttributeChanges().getAdd().isEmpty() ?
                    provider.getGroupMemberAttributeTypeNames() : param.getGroupMemberAttributeChanges().getAdd());
        }

        return authnToValidate;
    }
}
