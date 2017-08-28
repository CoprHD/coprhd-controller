/*
 * Copyright 2015 EMC Corporation
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package controllers.auth;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.*;

import com.emc.storageos.api.service.impl.resource.AuthnConfigurationService;
import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.db.client.model.AuthnProvider;

import com.emc.storageos.model.keystone.OpenStackTenantListParam;
import com.emc.storageos.model.keystone.OpenStackTenantParam;
import com.emc.storageos.security.authentication.AuthUtil;
import models.SearchScopes;
import models.TenantsSynchronizationOptions;
import models.datatable.LDAPsourcesDataTable;
import models.datatable.LDAPsourcesDataTable.LDAPsourcesInfo;

import models.datatable.OpenStackTenantsDataTable;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.*;

import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.model.auth.AuthnUpdateParam.DomainChanges;
import com.emc.storageos.model.auth.AuthnUpdateParam.TenantsSynchronizationOptionsChanges;
import com.emc.storageos.model.auth.AuthnUpdateParam.GroupWhitelistValueChanges;
import com.emc.storageos.model.auth.AuthnUpdateParam.ServerUrlChanges;
import com.emc.storageos.model.auth.AuthnUpdateParam.GroupObjectClassChanges;
import com.emc.storageos.model.auth.AuthnUpdateParam.GroupMemberAttributeChanges;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;

import util.datatable.DataTablesSupport;


@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN") })
public class LDAPsources extends ViprResourceController {

    private static String authProviderName = "";
    private static Boolean authProviderAutoReg = false;
    protected static final String SAVED = "LDAPsources.saved";
    protected static final String DELETED = "LDAPsources.deleted";
    protected static final String FAILED = "LDAPsources.failed";
    protected static final String UNKNOWN = "LDAPsources.unknown";
    protected static final String MODEL_NAME = "LDAPsources";
    public static final String EXPECTED_GEO_VERSION_FOR_LDAP_GROUP_SUPPORT = AuthnProvider.getExpectedGeoVDCVersionForLDAPGroupSupport();
    private static final String[] DEFAULT_GROUP_OBJECT_CLASSES = { "groupOfNames", "groupOfUniqueNames", "posixGroup", "organizationalRole" };
    private static final String[] DEFAULT_GROUP_MEMBER_ATTRIBUTES = { "member", "uniqueMember", "memberUid", "roleOccupant" };
    private static final String KEYSTONE_SERVER_URL = CinderConstants.HTTP_URL + "[IP Address]" + CinderConstants.COLON
            + CinderConstants.OS_ADMIN_PORT
            + CinderConstants.REST_API_VERSION_2;
    // Interval delay between each execution in seconds.
    private static final String DEFAULT_INTERVAL_DELAY = "900";
    // Minimum interval in seconds.
    public static final int MIN_INTERVAL_DELAY = 10;

    //
    // Add reference data so that they can be reference in html template
    //

    private static void addReferenceData() {

        renderArgs.put("authSourceTypeList", Arrays.asList(EnumOption.options(AuthSourceType.values())));

        renderArgs.put("adType", AuthSourceType.ad);

        renderArgs.put("ldapType", AuthSourceType.ldap);
        renderArgs.put("keyStoneType", AuthSourceType.keystone);
        renderArgs.put("keystoneServerURL", KEYSTONE_SERVER_URL);
        renderArgs.put("defaultInterval", DEFAULT_INTERVAL_DELAY);
        renderArgs.put("searchScopeTypeList", SearchScopes.options(SearchScopes.ONELEVEL, SearchScopes.SUBTREE));
        renderArgs.put("tenantsOptions", TenantsSynchronizationOptions.options(TenantsSynchronizationOptions.ADDITION, TenantsSynchronizationOptions.DELETION));
        renderArgs.put("showLdapGroup", VCenterUtils.checkCompatibleVDCVersion(EXPECTED_GEO_VERSION_FOR_LDAP_GROUP_SUPPORT));
        renderArgs.put("tenants", new LDAPSourcesTenantsDataTable());
    }

    /**
     * if it was not redirect from another page, clean flash
     */
    public static void list() {
        renderArgs.put("dataTable", new LDAPsourcesDataTable());
        render();
    }

    public static void listJson() {
        performListJson(AuthnProviderUtils.getAuthnProviders(), new JsonItemOperation());
    }

    /**
     * Shows the dialog for OpenStack tenants editing form.
     *
     */

    public static void tenantsList() {
        renderArgs.put("tenants", new OpenStackTenantsDataTable());
        render();
    }
    /**
     * Gets the list of tenants.
     *
     */
    public static void tenantsListJson() {
        List<OpenStackTenantsDataTable.OpenStackTenant> tenants = Lists.newArrayList();
        for (OpenStackTenantParam tenant : OpenStackTenantsUtils.getOpenStackTenants()) {
            tenants.add(new OpenStackTenantsDataTable.OpenStackTenant(tenant));
        }
        renderJSON(DataTablesSupport.createJSON(tenants, params));
    }

    private static List<String> parseMultiLineString(String multiValueList) {
        List<String> cleanedValues = new ArrayList();
        List<String> list = Arrays.asList(multiValueList.split("\n"));
        for (String str : list) {
            str = StringUtils.trim(str);
            // do not add any blank lines from multiline field
            if (StringUtils.isNotBlank(str)) {
                cleanedValues.add(str);
            }
        }
        return cleanedValues;
    }

    /**
     * If the argument is null or empty, or contains 1 empty string, return an empty list If the argument contains one
     * comma delimited String, split it up Otherwise, return the original list
     */
    private static List<String> parseMultiLineInput(String string) {
        List<String> result = null;
        if ((string == null) || (string.isEmpty())) {
            result = Collections.EMPTY_LIST;
        }
        else if ((StringUtils.isNotEmpty(string)) && StringUtils.contains(string, '\n')) {
            result = parseMultiLineString(string);
        }
        else {
            // result = Collections.EMPTY_LIST;
            List<String> single = new ArrayList();
            single.add(0, string);
            result = single;
        }
        return result;
    }

    public static void create(String authsource) {
        if (authsource == null) {
            authsource = AuthSourceType.ad.name();
        }

        if ( authsource.equals( AuthSourceType.oidc.name() ) ) {
            editOidcForm(new OIDCAuthnProviderForm());
        } else {
            // put all "initial create only" defaults here rather than field initializers
            editLdapForm(new LDAPsourcesForm(authsource));
        }
    }

    @FlashException("list")
    public static void edit(String id) {
        AuthnProviderRestRep authnProvider = AuthnProviderUtils.getAuthnProvider(id);
        if (authnProvider == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

        authProviderAutoReg = authnProvider.getAutoRegCoprHDNImportOSProjects();

        edit(authnProvider);
    }

    private static void edit(AuthnProviderRestRep authnProvider) {
        String mode = authnProvider.getMode();

        if (mode.equals(AuthUtil.AuthMode.oidc.name())) {
            editOidcForm(new OIDCAuthnProviderForm(authnProvider));
        } else {
            editLdapForm(new LDAPsourcesForm(authnProvider));
        }
    }

    private static void editOidcForm(OIDCAuthnProviderForm authnProvider) {
        renderArgs.put("authSourceTypeList", Arrays.asList(EnumOption.options(AuthSourceType.values())));
        render("@editOidc", authnProvider);
    }

    private static void editLdapForm(LDAPsourcesForm ldapSources) {
        addReferenceData();
        render("@edit", ldapSources);
    }

    public static void addTenants(String ldadSourceId, @As(",") String[] ids) {
        List<OpenStackTenantParam> tenants = OpenStackTenantsUtils.getOpenStackTenants();
        if (ids != null) {
            List<String> idList = Arrays.asList(ids);
            for (OpenStackTenantParam tenant : tenants) {
                if (!idList.contains(tenant.getOsId())) {
                    tenant.setExcluded(true);
                }
            }
        }
        OpenStackTenantListParam params = new OpenStackTenantListParam();
        params.setOpenstackTenants(tenants);

        OpenStackTenantsUtils.addOpenStackTenants(params);

        flash.success(MessagesUtils.get(SAVED, authProviderName));
        list();
    }

    @FlashException(keep = true)
    public static void saveOidc(OIDCAuthnProviderForm authnProvider) {
        save(authnProvider);
    }

    @FlashException(keep = true)
    public static void saveLdap(LDAPsourcesForm ldapSources) {
        save(ldapSources);
    }

    private static void save(AuthnProviderForm authnProvider) {
        authnProvider.validate("ldapSources");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        AuthnProviderRestRep authnProviderRep = authnProvider.save();
        authProviderName = authnProvider.name;

        flash.success(MessagesUtils.get(SAVED, authnProvider.name));

        if (authnProvider.mode.equals(AuthSourceType.keystone.name())) {
            if ( ((LDAPsourcesForm) authnProvider).autoRegCoprHDNImportOSProjects && !authProviderAutoReg) {
                renderArgs.put("showDialog", "true");
                editLdapForm(new LDAPsourcesForm(authnProviderRep));
            }
        }

        list();
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeleteOperation(), DELETED, FAILED);
        list();
    }

    /**
     * Base class for different types of provider forms
     */
    public static abstract class AuthnProviderForm {
        public String id;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;
        @Required
        public String mode;
        public String description;
        public Boolean disable;

        abstract AuthnProviderRestRep save();

        abstract void validate(String fieldName);

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    }


    @SuppressWarnings("ClassVariableVisibilityCheck")
    public static class LDAPsourcesForm extends AuthnProviderForm {

        public Boolean autoRegCoprHDNImportOSProjects;

        public List<String> tenantsSynchronizationOptions;

        public String synchronizationInterval;

        @Required
        public List<String> domains;

        public List<String> groupObjectClasses;

        public List<String> groupMemberAttributes;

        public String groupAttribute;

        public List<String> groupWhiteListValues;

        @Required
        public String managerDn;

        public String managerPassword;


        public String searchBase;


        public String searchFilter;


        public String searchScope;

        @Required
        public List<String> serverUrls;

        public LDAPsourcesForm() {
            groupObjectClasses = Arrays.asList(DEFAULT_GROUP_OBJECT_CLASSES);
            groupMemberAttributes = Arrays.asList(DEFAULT_GROUP_MEMBER_ATTRIBUTES);

            renderArgs.put("groupObjectClassesString", StringUtils.join(this.groupObjectClasses, "\n"));
            renderArgs.put("groupMemberAttributesString", StringUtils.join(this.groupMemberAttributes, "\n"));
        }

        public LDAPsourcesForm(AuthnProviderRestRep authnProviderResponse) {
            readFrom(authnProviderResponse);
            // render the list items to textareas one item per line
            renderArgs.put("domainString", StringUtils.join(this.domains, "\n"));
            renderArgs.put("groupWhiteListString", StringUtils.join(this.groupWhiteListValues, "\n"));
            renderArgs.put("serverUrlsString", StringUtils.join(this.serverUrls, "\n"));
            renderArgs.put("groupObjectClassesString", StringUtils.join(this.groupObjectClasses, "\n"));
            renderArgs.put("groupMemberAttributesString", StringUtils.join(this.groupMemberAttributes, "\n"));
            renderArgs.put("readOnlyGroupAttribute", !isGroupAttributeBlankOrNull(this.groupAttribute));
            renderArgs.put("readOnlyCheckboxForAutomaticRegistration", this.autoRegCoprHDNImportOSProjects);
            renderArgs.put("readOnlySynchronizationInterval", this.synchronizationInterval);
        }

        public LDAPsourcesForm(String authSource) {
            this();
            this.mode = authSource;
        }

        public void readFrom(AuthnProviderRestRep ldapSources) {
            this.id = stringId(ldapSources);
            this.name = ldapSources.getName();
            this.mode = ldapSources.getMode();
            this.description = ldapSources.getDescription();
            this.disable = ldapSources.getDisable();
            this.autoRegCoprHDNImportOSProjects = ldapSources.getAutoRegCoprHDNImportOSProjects();
            this.tenantsSynchronizationOptions = Lists.newArrayList(ldapSources.getTenantsSynchronizationOptions());
            this.synchronizationInterval = getInterval(ldapSources.getTenantsSynchronizationOptions());
            this.domains = Lists.newArrayList(ldapSources.getDomains());
            this.groupAttribute = isGroupAttributeBlankOrNull(ldapSources.getGroupAttribute()) ? "" : ldapSources.getGroupAttribute();
            this.groupWhiteListValues = Lists.newArrayList(ldapSources.getGroupWhitelistValues());
            this.managerDn = ldapSources.getManagerDN();
            this.managerPassword = ""; // the platform will never return the real password //NOSONAR
                                       // ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")
            this.searchBase = ldapSources.getSearchBase();
            this.searchFilter = ldapSources.getSearchFilter();
            this.serverUrls = Lists.newArrayList(ldapSources.getServerUrls());
            this.searchScope = ldapSources.getSearchScope();
            this.groupObjectClasses = Lists.newArrayList(ldapSources.getGroupObjectClasses());
            this.groupMemberAttributes = Lists.newArrayList(ldapSources.getGroupMemberAttributes());
        }

        @Override
        public AuthnProviderRestRep save() {
            if (isNew()) {
                return create();
            } else {
                return update();
            }
        }

        private AuthnProviderRestRep update() {
            AuthnUpdateParam param = new AuthnUpdateParam();
            AuthnProviderRestRep provider = AuthnProviderUtils.getAuthnProvider(this.id);

            param.setLabel(this.name);
            param.setMode(this.mode);
            param.setDescription(StringUtils.trimToNull(this.description));
            param.setDisable(this.disable);
            param.setAutoRegCoprHDNImportOSProjects(this.autoRegCoprHDNImportOSProjects);
            if (this.autoRegCoprHDNImportOSProjects) {
                param.setTenantsSynchronizationOptionsChanges(getTenantsSynchronizationOptionsChanges(provider));
            }
            param.setManagerDn(this.managerDn);
            param.setManagerPassword(StringUtils.trimToNull(this.managerPassword));

            param.setSearchBase(this.searchBase);
            param.setSearchFilter(this.searchFilter);
            param.setSearchScope(this.searchScope);

            param.setDomainChanges(getDomainChanges(provider));
            param.setGroupWhitelistValueChanges(getGroupWhitelistValueChanges(provider));
            param.setServerUrlChanges(getServerUrlChanges(provider));

            param.setGroupObjectClassChanges(getGroupObjectClassChanges(provider));
            param.setGroupMemberAttributeChanges(getGroupMemberAttributeChanges(provider));

            if (isGroupAttributeBlankOrNull(provider.getGroupAttribute())) {
                param.setGroupAttribute(this.groupAttribute);
                return AuthnProviderUtils.forceUpdate(this.id, param);
            } else {
                return AuthnProviderUtils.update(this.id, param);
            }
        }

        private DomainChanges getDomainChanges(AuthnProviderRestRep provider) {
            Set<String> newValues = Sets.newHashSet(parseMultiLineInput(this.domains.get(0)));
            Set<String> oldValues = provider.getDomains();

            DomainChanges changes = new DomainChanges();
            changes.getAdd().addAll(newValues);
            changes.getAdd().removeAll(oldValues);
            changes.getRemove().addAll(oldValues);
            changes.getRemove().removeAll(newValues);

            return changes;
        }

        public static String getInterval(Set<String> tenantsSynchronizationOptions) {
            String interval = "";
            for (String option : tenantsSynchronizationOptions) {
                // There is only ADDITION, DELETION and interval in this StringSet.
                if (!AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString().equals(option) &&
                    !AuthnProvider.TenantsSynchronizationOptions.DELETION.toString().equals(option)) {
                    interval = option;
                }
            }
            return interval;
        }

        private TenantsSynchronizationOptionsChanges getTenantsSynchronizationOptionsChanges(AuthnProviderRestRep provider) {
            Set<String> newValues;
            if (this.tenantsSynchronizationOptions != null) {
                newValues = Sets.newHashSet(this.tenantsSynchronizationOptions);
                newValues.add(this.synchronizationInterval);
            } else {
                newValues = Sets.newHashSet(this.synchronizationInterval);
            }

            Set<String> oldValues = provider.getTenantsSynchronizationOptions();

            TenantsSynchronizationOptionsChanges changes = new TenantsSynchronizationOptionsChanges();
            changes.getAdd().addAll(newValues);
            changes.getAdd().removeAll(oldValues);
            changes.getRemove().addAll(oldValues);
            changes.getRemove().removeAll(newValues);

            return changes;
        }

        private GroupWhitelistValueChanges getGroupWhitelistValueChanges(AuthnProviderRestRep provider) {
            Set<String> oldValues = provider.getGroupWhitelistValues();
            Set<String> newValues = Sets.newHashSet(parseMultiLineInput(this.groupWhiteListValues.get(0)));

            GroupWhitelistValueChanges changes = new GroupWhitelistValueChanges();
            changes.getAdd().addAll(newValues);
            changes.getAdd().removeAll(oldValues);
            changes.getRemove().addAll(oldValues);
            changes.getRemove().removeAll(newValues);

            return changes;
        }

        private ServerUrlChanges getServerUrlChanges(AuthnProviderRestRep provider) {
            Set<String> oldValues = provider.getServerUrls();
            Set<String> newValues = Sets.newHashSet(parseMultiLineInput(this.serverUrls.get(0)));

            ServerUrlChanges changes = new ServerUrlChanges();
            changes.getAdd().addAll(newValues);
            changes.getAdd().removeAll(oldValues);
            changes.getRemove().addAll(oldValues);
            changes.getRemove().removeAll(newValues);

            return changes;
        }

        private GroupObjectClassChanges getGroupObjectClassChanges(AuthnProviderRestRep provider) {
            Set<String> oldValues = provider.getGroupObjectClasses();
            Set<String> newValues = Sets.newHashSet(parseMultiLineInput(this.groupObjectClasses.get(0)));

            GroupObjectClassChanges changes = new GroupObjectClassChanges();
            changes.getAdd().addAll(newValues);
            changes.getAdd().removeAll(oldValues);
            changes.getRemove().addAll(oldValues);
            changes.getRemove().removeAll(newValues);

            return changes;
        }

        private GroupMemberAttributeChanges getGroupMemberAttributeChanges(AuthnProviderRestRep provider) {
            Set<String> oldValues = provider.getGroupMemberAttributes();
            Set<String> newValues = Sets.newHashSet(parseMultiLineInput(this.groupMemberAttributes.get(0)));

            GroupMemberAttributeChanges changes = new GroupMemberAttributeChanges();
            changes.getAdd().addAll(newValues);
            changes.getAdd().removeAll(oldValues);
            changes.getRemove().addAll(oldValues);
            changes.getRemove().removeAll(newValues);

            return changes;
        }

        private AuthnProviderRestRep create() {
            AuthnCreateParam param = new AuthnCreateParam();
            param.setLabel(this.name);
            param.setMode(this.mode);
            param.setDescription(StringUtils.trimToNull(this.description));
            param.setDisable(this.disable);
            param.setAutoRegCoprHDNImportOSProjects(this.autoRegCoprHDNImportOSProjects);
            if (this.autoRegCoprHDNImportOSProjects) {
                if (tenantsSynchronizationOptions != null) {
                    param.setTenantsSynchronizationOptions((Sets.newHashSet(this.tenantsSynchronizationOptions)));
                } else {
                    param.setTenantsSynchronizationOptions(Sets.<String> newHashSet());
                }
                param.getTenantsSynchronizationOptions().add(this.synchronizationInterval);
            }
            param.setGroupAttribute(this.groupAttribute);
            param.setManagerDn(this.managerDn);
            param.setManagerPassword(this.managerPassword);
            param.setSearchBase(this.searchBase);
            param.setSearchFilter(this.searchFilter);
            param.setSearchScope(this.searchScope);
            param.getDomains().addAll(parseMultiLineInput(this.domains.get(0)));
            param.getServerUrls().addAll(parseMultiLineInput(this.serverUrls.get(0)));
            param.getGroupWhitelistValues().addAll(parseMultiLineInput(this.groupWhiteListValues.get(0)));
            param.getGroupObjectClasses().addAll(parseMultiLineInput(this.groupObjectClasses.get(0)));
            param.getGroupMemberAttributes().addAll(parseMultiLineInput(this.groupMemberAttributes.get(0)));

            return AuthnProviderUtils.create(param);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

        	if (StringUtils.equals(AuthSourceType.ad.name(), mode)) {
                Validation.required(fieldName + ".groupAttribute", groupAttribute);
            }
            Validation.required(fieldName + ".domains", parseMultiLineInput(this.domains.get(0)));
            Validation.required(fieldName + ".serverUrls", parseMultiLineInput(this.serverUrls.get(0)));

            if (isNew()) {
                Validation.required(fieldName + ".managerPassword", this.managerPassword);
            }

            if (this.autoRegCoprHDNImportOSProjects ) {
                Validation.required(fieldName + ".synchronizationInterval", this.synchronizationInterval);
                if (!StringUtils.isNumeric(this.synchronizationInterval) ||
                        (StringUtils.isNumeric(this.synchronizationInterval) && (Integer.parseInt(this.synchronizationInterval)) < MIN_INTERVAL_DELAY)){
                    Validation.addError(fieldName + ".synchronizationInterval",
                            MessagesUtils.get("ldapSources.synchronizationInterval.integerRequired"));
                }
            }

        	if (!StringUtils.equals(AuthSourceType.keystone.name(), mode)) {

            if (StringUtils.lastIndexOf(this.searchFilter, "=") < 0) {
                Validation.addError(fieldName + ".searchFilter",
                        MessagesUtils.get("ldapSources.searchFilter.equalsRequired"));
            }
            else {
                String afterEquals = StringUtils.substringAfterLast(this.searchFilter, "=");
                if (StringUtils.contains(afterEquals, "%u") == false
                        && StringUtils.contains(afterEquals, "%U") == false) {
                    Validation.addError(fieldName + ".searchFilter",
                            MessagesUtils.get("ldapSources.searchFilter.variableRequired"));
                }
            }

            validateLDAPGroupProperties(fieldName);
        	}
        }

        private void validateLDAPGroupProperties(String fieldName) {
            boolean groupObjectClassesEmpty = checkIfEmptyList(this.groupObjectClasses);
            boolean groupMemberAttributesEmpty = checkIfEmptyList(this.groupMemberAttributes);

            // Return error if only one of either objectClasses or memberAttributes
            // is entered and other one is empty.
            if (groupObjectClassesEmpty ^ groupMemberAttributesEmpty) {
                if (groupObjectClassesEmpty) {
                    Validation.addError(fieldName + ".groupObjectClasses",
                            MessagesUtils.get(fieldName + ".groupObjectClasses.variableRequired"));
                } else {
                    Validation.addError(fieldName + ".groupMemberAttributes",
                            MessagesUtils.get(fieldName + ".groupMemberAttributes.variableRequired"));
                }
            }
        }

        private boolean checkIfEmptyList(List<String> list) {
            boolean isListEmpty = false;
            if (CollectionUtils.isEmpty(list) ||
                    (list.size() == 1 && StringUtils.isBlank(list.get(0)))) {
                isListEmpty = true;
            }
            return isListEmpty;
        }

        boolean isGroupAttributeBlankOrNull(String groupAttribute) {
            boolean isBlankOrNull = false;
            if (StringUtils.isBlank(groupAttribute) ||
                    groupAttribute.equalsIgnoreCase("null")) {
                isBlankOrNull = true;
            }
            return isBlankOrNull;
        }

    }

    public static class OIDCAuthnProviderForm extends AuthnProviderForm {
        public String oidcBaseUrl;
        public String oidcAuthUrl;
        public String oidcTokenUrl;
        
        @Required
        public List<String> domains;

        public OIDCAuthnProviderForm(AuthnProviderRestRep authnProvider) {
            this.id = stringId(authnProvider);
            this.name = authnProvider.getName();
            this.mode = authnProvider.getMode();
            this.description = authnProvider.getDescription();
            this.disable = authnProvider.getDisable();

            this.oidcBaseUrl = authnProvider.getOidcBaseUrl();
            this.oidcAuthUrl = authnProvider.getOidcAuthorizeUrl();
            this.oidcTokenUrl = authnProvider.getOidcTokenUrl();
            this.domains = Lists.newArrayList(authnProvider.getDomains());
            
            renderArgs.put("domainString", StringUtils.join(this.domains, "\n"));

        }

        public OIDCAuthnProviderForm() {
            this.mode = AuthSourceType.oidc.name();
        }

        @Override
        AuthnProviderRestRep save() {
            if (isNew()) {
                return create();
            } else {
                return update();
            }
        }

        private AuthnProviderRestRep create() {
            AuthnCreateParam param = new AuthnCreateParam();

            param.setLabel(this.name);
            param.setMode(this.mode);
            param.setDescription(StringUtils.trimToNull(this.description));
            param.setDisable(this.disable);
            param.setOidcBaseUrl(this.oidcBaseUrl);
            param.getDomains().addAll(parseMultiLineInput(this.domains.get(0)));

            return AuthnProviderUtils.create(param);
        }

        private AuthnProviderRestRep update() {
            AuthnUpdateParam param = new AuthnUpdateParam();
            AuthnProviderRestRep provider = AuthnProviderUtils.getAuthnProvider(this.id);

            param.setLabel(this.name);
            param.setMode(this.mode);
            param.setDescription(StringUtils.trimToNull(this.description));
            param.setDisable(this.disable);
            param.setOidcBaseUrl(this.oidcBaseUrl);
            param.setDomainChanges(getDomainChanges(provider));

            return AuthnProviderUtils.update(this.id, param);
        }
        
        private DomainChanges getDomainChanges(AuthnProviderRestRep provider) {
            Set<String> newValues = Sets.newHashSet(parseMultiLineInput(this.domains.get(0)));
            Set<String> oldValues = provider.getDomains();

            DomainChanges changes = new DomainChanges();
            changes.getAdd().addAll(newValues);
            changes.getAdd().removeAll(oldValues);
            changes.getRemove().addAll(oldValues);
            changes.getRemove().removeAll(newValues);

            return changes;
        }


        @Override
        void validate(String fieldName) {
            Validation.valid(fieldName, this);
            Validation.required(fieldName + ".domains", parseMultiLineInput(this.domains.get(0)));


        }
    }

    public static class LDAPSourcesTenantsDataTable extends OpenStackTenantsDataTable {
        public LDAPSourcesTenantsDataTable() {
            alterColumn("name").setRenderFunction(null);
        }
    }

    protected static class JsonItemOperation implements ResourceValueOperation<LDAPsourcesInfo, AuthnProviderRestRep> {
        @Override
        public LDAPsourcesInfo performOperation(AuthnProviderRestRep provider) throws Exception {
            return new LDAPsourcesInfo(provider);
        }
    }

    protected static class DeleteOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            AuthnProviderUtils.delete(id);
            return null;
        }
    }
}
