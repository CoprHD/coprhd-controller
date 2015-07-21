/*
 * Copyright (c) 2011-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.services.util.EnvConfig;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * ApiTest_AuthnProviderUtils an utility class to create the
 * default authnprovider config that can be used by all the
 * other tests.
 */
public class ApiTest_AuthnProviderUtils {
    private final String AUTHN_PROVIDER_BASE_URL = "/vdc/admin/authnproviders";
    private final String AUTHN_PROVIDER_EDIT_URL = AUTHN_PROVIDER_BASE_URL + "/%s";

    private final static String DEFAULT_TEST_LDAP_AUTHN_PROVIDER_LABEL = "LDAPAuthnProvider";
    private final static String DEFAULT_TEST_LDAP_AUTHN_PROVIDER_DESCRIPTION = "Authn Provider implemented by LDAP";
    private final static String DEFAULT_TEST_LDAP_AUTHN_PROVIDER_MODE = "ldap";

    private final static String DEFAULT_TEST_LDAP_SERVER_URL = "ldap://" + EnvConfig.get("sanity", "ldap2.ip");
    private final static String DEFAULT_TEST_LDAP_SERVER_DOMIN = "maxcrc.com";
    private final static String DEFAULT_TEST_LDAP_SERVER_MANAGER_DN = "cn=Manager,dc=maxcrc,dc=com";
    private final static String DEFAULT_TEST_LDAP_SERVER_MANAGER_DN_PWD = "secret";
    private final static String DEFAULT_TEST_LDAP_SERVER_SEARCH_BASE = "ou=vipr,dc=maxcrc,dc=com";
    private final static String DEFAULT_TEST_LDAP_SERVER_SEARCH_SCOPE = "SUBTREE";
    private final static String DEFAULT_TEST_LDAP_SERVER_SEARCH_FILTER = "uid=%U";
    private final static String DEFAULT_TEST_LDAP_SERVER_GROUP_ATTRIBUTE = "CN";
    private final static String DEFAULT_TEST_SECOND_DOMAIN = "sanity.local";
    private final static String DEFAULT_TEST_ONE_LETTER_DOMAIN = "d";

    private final static String[] DEFAULT_TEST_LDAP_SERVER_GROUP_OBJECT_CLASSES = {"groupofnames", "groupofuniquenames", "posixgroup", "organizationalrole"};
    private final static String[] DEFAULT_TEST_LDAP_SERVER_GROUP_MEMBER_ATTRIBUTES = {"member", "uniquemember", "memberuid", "roleoccupant"};

    //ldapViPRUserGroup - groupOfNames object class.
    //ldapViPRUserGroupNew - groupOfNames object class.
    //ldapViPRUserGroupOrgRole - organizationalRole object class.
    //ldapViPRUniqueNameGroup - groupOfUniqueNames object class.
    //ldapViPRPosixGroup - posixGroup object class.
    //ldapViPRUserGroupNewOuter - groupOfNames object class.
    //Marketing - groupOfUniqueNames object class.
    //MarketingNew - groupOfUniqueNames object class.
    //MarketingOuter - groupOfUniqueNames object class.
    private final static String[] DEFAULT_TEST_LDAP_GROUPS = {"ldapViPRUserGroup", "ldapViPRUserGroupNew", "ldapViPRUserGroupOrgRole",
            "ldapViPRUniqueNameGroup", "ldapViPRPosixGroup", "ldapViPRUserGroupNewOuter", "Marketing", "MarketingNew", "MarketingOuter"};

    //ldapViPRUser1 - is a member of ldapViPRUserGroup and Marketing.
    //ldapViPRUser2, ldapViPRUser4, ldapViPRUserGroup - is a member of  ldapViPRUserGroupNew.
    //ldapViPRUserGroupNew - is a member of ldapViPRUserGroupNewOuter.
    //ldapViPRUserGroupNewOuter - is a member of ldapViPRUniqueNameGroup.
    //Marketing - is a member of MarketingNew.
    //MarketingNew - is a member of MarketingOuter.
    //ldapViPRUserGroupNewOuter, MarketingOuter - is a member of ldapViPRUserGroupOrgRole
    //ldapViPRUser5 - has attributes departmentNumber = [ENG, DEV] and localityName = [Boston].
    //ldapViPRUser6 - has attributes departmentNumber = [ENG, QE] and localityName = [New York].
    //ldapViPRUser7 - has attributes departmentNumber = [ENG, QE, MANAGE] and localityName = [Boston].
    private final static String[] DEFAULT_TEST_LDAP_USERS_UID = {"ldapViPRUser1", "ldapViPRUser2", "ldapViPRUser3", "ldapViPRUser4",
            "ldapViPRUser5", "ldapViPRUser6", "ldapViPRUser7"};

    private final static String DEFAULT_TEST_LDAP_SERVER_NON_MANAGER_BIND_DN = "uid=ldapViPRUser1,ou=Users,ou=ViPR,dc=maxcrc,dc=com";

    private final static String DEFAULT_TEST_TENANT_USERS_PASS_WORD = "secret";

    private final String[] TEST_DEFAULT_ATTRIBUTE_KEYS = {"departmentNumber", "l"}; //l means localityName
    private final String[] TEST_DEFAULT_ATTRIBUTE_DEPARTMENT_VALUES = {"ENG", "QE", "DEV", "MANAGE"};
    private final String[] TEST_DEFAULT_ATTRIBUTE_LOCALITY_VALUES = {"Boston", "New York", "West Coast"};

    public Set<String> getDefaultGroupObjectClasses(){
        return new HashSet<String>(Arrays.asList(DEFAULT_TEST_LDAP_SERVER_GROUP_OBJECT_CLASSES));
    }

    public Set<String> getDefaultGroupMemberAttributes(){
        return new HashSet<String>(Arrays.asList(DEFAULT_TEST_LDAP_SERVER_GROUP_MEMBER_ATTRIBUTES));
    }

    public String getGroupObjectClass(int index){
        return DEFAULT_TEST_LDAP_SERVER_GROUP_OBJECT_CLASSES[index];
    }

    public String getGroupMemberAttribute(int index){
        return DEFAULT_TEST_LDAP_SERVER_GROUP_MEMBER_ATTRIBUTES[index];
    }

    public String getDefaultGroupAttribute(){
        return DEFAULT_TEST_LDAP_SERVER_GROUP_ATTRIBUTE;
    }

    public AuthnCreateParam getDefaultAuthnCreateParam (String description) {
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel(DEFAULT_TEST_LDAP_AUTHN_PROVIDER_LABEL);
        if (StringUtils.isNotBlank(description)) {
            param.setDescription(description);
        } else {
            param.setDescription(DEFAULT_TEST_LDAP_AUTHN_PROVIDER_DESCRIPTION);
        }
        param.setDisable(false);
        param.getDomains().add(DEFAULT_TEST_LDAP_SERVER_DOMIN);
        param.setManagerDn(DEFAULT_TEST_LDAP_SERVER_MANAGER_DN);
        param.setManagerPassword(DEFAULT_TEST_LDAP_SERVER_MANAGER_DN_PWD);
        param.setSearchBase(DEFAULT_TEST_LDAP_SERVER_SEARCH_BASE);
        param.setSearchFilter(DEFAULT_TEST_LDAP_SERVER_SEARCH_FILTER);
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add(DEFAULT_TEST_LDAP_SERVER_URL);
        param.setMode(DEFAULT_TEST_LDAP_AUTHN_PROVIDER_MODE);
        param.setGroupAttribute(DEFAULT_TEST_LDAP_SERVER_GROUP_ATTRIBUTE);
        param.setSearchScope(DEFAULT_TEST_LDAP_SERVER_SEARCH_SCOPE);
        param.setGroupObjectClasses(getDefaultGroupObjectClasses());
        param.setGroupMemberAttributes(getDefaultGroupMemberAttributes());

        return param;
    }

    public AuthnUpdateParam getAuthnUpdateParamFromAuthnProviderRestResp (AuthnProviderRestRep createResponse) {
        AuthnUpdateParam param = new AuthnUpdateParam();
        param.setLabel(createResponse.getName());
        param.setDescription(createResponse.getDescription());
        param.setDisable(createResponse.getDisable());
        param.getDomainChanges().getAdd().addAll(createResponse.getDomains());
        param.getDomainChanges().getRemove().addAll(new HashSet<String>());
        param.setManagerDn(createResponse.getManagerDN());
        param.setManagerPassword(DEFAULT_TEST_LDAP_SERVER_MANAGER_DN_PWD);
        param.setSearchBase(createResponse.getSearchBase());
        param.setSearchFilter(createResponse.getSearchFilter());
        param.getServerUrlChanges().getAdd().addAll(createResponse.getServerUrls());
        param.getServerUrlChanges().getRemove().addAll(new HashSet<String>());
        param.setMode(createResponse.getMode());
        param.setGroupAttribute(createResponse.getGroupAttribute());
        param.setSearchScope(createResponse.getSearchScope());
        param.getGroupObjectClassChanges().getAdd().addAll(createResponse.getGroupObjectClasses());
        param.getGroupMemberAttributeChanges().getAdd().addAll(createResponse.getGroupMemberAttributes());

        return param;
    }

    public Set<String> getDefaultLDAPGroups () {
        return new HashSet<String> (Arrays.asList(DEFAULT_TEST_LDAP_GROUPS));
    }

    public String getLDAPGroup (int index) {
        return DEFAULT_TEST_LDAP_GROUPS[index];
    }

    public Set<String> getDefaultLDAPUsers () {
        return new HashSet<String> (Arrays.asList(DEFAULT_TEST_LDAP_USERS_UID));
    }

    public String getLDAPUser (int index) {
        return DEFAULT_TEST_LDAP_USERS_UID[index];
    }

    public String getLDAPUserPassword () {
        return DEFAULT_TEST_TENANT_USERS_PASS_WORD;
    }

    public String getAuthnProviderDomain() {
        return DEFAULT_TEST_LDAP_SERVER_DOMIN;
    }

    public String getSecondDomain() {
        return DEFAULT_TEST_SECOND_DOMAIN;
    }

    public String getOneLetterDomain() {
        return DEFAULT_TEST_ONE_LETTER_DOMAIN;
    }

    public String getUserWithDomain(int index){
        return DEFAULT_TEST_LDAP_USERS_UID[index] + "@" + getAuthnProviderDomain();
    }

    public String getAuthnProviderBaseURL () {
        return AUTHN_PROVIDER_BASE_URL;
    }

    public String getAuthnProviderEditURL(URI id) {
        return String.format(AUTHN_PROVIDER_EDIT_URL, id);
    }

    public String getNonManagerDN () {
        return DEFAULT_TEST_LDAP_SERVER_NON_MANAGER_BIND_DN;
    }

    public Set<String> getDefaultAttributeKeys(){
        return new HashSet<String>(Arrays.asList(TEST_DEFAULT_ATTRIBUTE_KEYS));
    }

    public Set<String> getDefaultAttributeDepartmentValues(){
        return new HashSet<String>(Arrays.asList(TEST_DEFAULT_ATTRIBUTE_DEPARTMENT_VALUES));
    }

    public Set<String> getDefaultAttributeLocalityValues(){
        return new HashSet<String>(Arrays.asList(TEST_DEFAULT_ATTRIBUTE_LOCALITY_VALUES));
    }

    public String getAttributeKey(int index){
        return TEST_DEFAULT_ATTRIBUTE_KEYS[index];
    }

    public String getAttributeDepartmentValue(int index){
        return TEST_DEFAULT_ATTRIBUTE_DEPARTMENT_VALUES[index];
    }

    public String getAttributeLocalityValue(int index){
        return TEST_DEFAULT_ATTRIBUTE_LOCALITY_VALUES[index];
    }
}
