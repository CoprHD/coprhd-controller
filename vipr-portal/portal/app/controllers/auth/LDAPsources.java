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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.AuthnProvider;

import models.SearchScopes;
import models.datatable.LDAPsourcesDataTable;
import models.datatable.LDAPsourcesDataTable.LDAPsourcesInfo;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.AuthSourceType;
import util.AuthnProviderUtils;
import util.EnumOption;
import util.MessagesUtils;
import util.VCenterUtils;

import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.model.auth.AuthnUpdateParam.DomainChanges;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN") })
public class LDAPsources extends ViprResourceController {

    private static Logger log = LoggerFactory.getLogger(LDAPsources.class);

    protected static final String SAVED = "LDAPsources.saved";
    protected static final String DELETED = "LDAPsources.deleted";
    protected static final String FAILED = "LDAPsources.failed";
    protected static final String UNKNOWN = "LDAPsources.unknown";
    protected static final String MODEL_NAME = "LDAPsources";
    public static final String EXPECTED_GEO_VERSION_FOR_LDAP_GROUP_SUPPORT = AuthnProvider.getExpectedGeoVDCVersionForLDAPGroupSupport();
    private static final String[] DEFAULT_GROUP_OBJECT_CLASSES = { "groupOfNames", "groupOfUniqueNames", "posixGroup", "organizationalRole" };
    private static final String[] DEFAULT_GROUP_MEMBER_ATTRIBUTES = { "member", "uniqueMember", "memberUid", "roleOccupant" };

    //
    // Add reference data so that they can be reference in html template
    //

    private static void addReferenceData() {

        renderArgs.put("authSourceTypeList", Arrays.asList(EnumOption.options(AuthSourceType.values())));

        renderArgs.put("adType", AuthSourceType.ad);

        renderArgs.put("ldapType", AuthSourceType.ldap);
        renderArgs.put("keyStoneType", AuthSourceType.keystone);

        renderArgs.put("searchScopeTypeList", SearchScopes.options(SearchScopes.ONELEVEL, SearchScopes.SUBTREE));

        renderArgs.put("showLdapGroup", VCenterUtils.checkCompatibleVDCVersion(EXPECTED_GEO_VERSION_FOR_LDAP_GROUP_SUPPORT));
    }

    /**
     * if it was not redirect from another page, clean flash
     * 
     * @param redirect
     */
    public static void list() {
        renderArgs.put("dataTable", new LDAPsourcesDataTable());
        render();
    }

    public static void listJson() {
        performListJson(AuthnProviderUtils.getAuthnProviders(), new JsonItemOperation());
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

    public static void create() {
        LDAPsourcesForm ldapSources = new LDAPsourcesForm();
        // put all "initial create only" defaults here rather than field initializers
        edit(ldapSources);
    }

    private static void edit(LDAPsourcesForm ldapSources) {
        addReferenceData();
        render("@edit", ldapSources);
    }

    @FlashException("list")
    public static void edit(String id) {
        AuthnProviderRestRep authnProvider = AuthnProviderUtils.getAuthnProvider(id);
        if (authnProvider == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

        edit(new LDAPsourcesForm(authnProvider));
    }

    @FlashException(keep = true)
    public static void save(LDAPsourcesForm ldapSources) {
        ldapSources.validate("ldapSources");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        ldapSources.save();
        flash.success(MessagesUtils.get(SAVED, ldapSources.name));
        list();
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeleteOperation(), DELETED, FAILED);
        list();
    }

    public static class LDAPsourcesForm {

        public String id;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        public String mode;

        public String description;

        public Boolean disable;
        
        public Boolean autoRegCoprHDNImportOSProjects;

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
            /* RAG
            if(this.domains!=null && !this.domains.isEmpty())
            {
            	renderArgs.put("readOnlyDomains", true);
            }else {
            	renderArgs.put("readOnlyDomains", false);
            }*/
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void readFrom(AuthnProviderRestRep ldapSources) {
            this.id = stringId(ldapSources);
            this.name = ldapSources.getName();
            this.mode = ldapSources.getMode();
            this.description = ldapSources.getDescription();
            this.disable = ldapSources.getDisable();
            this.autoRegCoprHDNImportOSProjects = ldapSources.getAutoRegCoprHDNImportOSProjects();
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
