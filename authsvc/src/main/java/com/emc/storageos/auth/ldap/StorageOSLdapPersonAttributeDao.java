/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.ldap;

import com.emc.storageos.auth.AuthenticationManager.ValidationFailureReason;
import com.emc.storageos.auth.impl.LdapFailureHandler;
import com.emc.storageos.auth.StorageOSPersonAttributeDao;
import com.emc.storageos.auth.impl.LdapOrADServer;
import com.emc.storageos.auth.impl.LdapServerList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.AuthnProvider.ProvidersType;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.usergroup.UserAttributeParam;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMappingAttribute;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.UnauthorizedException;
import com.google.common.collect.Lists;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.SizeLimitExceededException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.*;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.naming.InvalidNameException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * 
 * Attribute repository for LDAP users
 */
public class StorageOSLdapPersonAttributeDao implements StorageOSPersonAttributeDao {

    private static final Logger _log = LoggerFactory.getLogger(StorageOSPersonAttributeDao.class);
    public static final String AD_DISTINGUISHED_NAME = "distinguishedName";
    public static final String LDAP_DISTINGUISHED_NAME = "entryDN";
    public static final String COMMON_NAME = "cn";
    public static final String OBJECT_SID = "objectSid";
    public static final String TOKEN_GROUPS = "tokenGroups";
    public static final String OBJECT_CLASS = "objectClass";
    /**
     * The LdapTemplate to use to execute queries on the DirContext
     */
    private String _baseDN;
    private SearchControls _searchControls = new SearchControls();
    private GroupWhiteList _groupWhiteList = GroupWhiteList.SID;
    private DbClient _dbClient;
    private String _filter;
    private Set<String> _groupObjectClasses = new HashSet<String>();
    private Set<String> _groupMemberAttributes = new HashSet<String>();

    // LDAP server can have a maximum number of query results it will return at once.
    // On AD the default is 1000 although this is user configurable
    private int _maxPageSize = 1000;

    private ProvidersType _type;
    private LdapFailureHandler _failureHandler = new LdapFailureHandler();
    private LdapServerList _ldapServers;

    public StorageOSLdapPersonAttributeDao() {
        super();
    }

    /**
     * @return The base distinguished name to use for queries.
     */
    public String getBaseDN() {
        return this._baseDN;
    }

    /**
     * @param baseDN
     *            The base distinguished name to use for queries.
     */
    public void setBaseDN(final String baseDN) {
        if (baseDN == null) {
            this._baseDN = "";
        } else {
            this._baseDN = baseDN;
        }
    }

    public SearchControls getSearchControls() {
        return this._searchControls;
    }

    public void setSearchControls(final SearchControls searchControls) {
        Assert.notNull(searchControls, "searchControls can not be null");
        this._searchControls = searchControls;
    }

    public GroupWhiteList getGroupWhiteList() {
        return _groupWhiteList;
    }

    public void setGroupWhiteList(final GroupWhiteList groupWhiteList) {
        this._groupWhiteList = groupWhiteList;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setFilter(String filter) {
        _filter = filter;
    }

    public void setMaxPageSize(int maxPageSize) {
        _maxPageSize = maxPageSize;
    }

    public void setProviderType(ProvidersType type) {
        _type = type;
    }

    public Set<String> getGroupObjectClasses() {
        return _groupObjectClasses;
    }

    public void setGroupObjectClasses(Set<String> groupObjectClasses) {
        this._groupObjectClasses = groupObjectClasses;
    }

    public Set<String> getGroupMemberAttributes() {
        return _groupMemberAttributes;
    }

    public void setGroupMemberAttributes(Set<String> groupMemberAttributes) {
        this._groupMemberAttributes = groupMemberAttributes;
    }

    /**
     * Convert the binary objectSID into readable string format
     * 
     * The byte array structure is (according to MSDN):
     * <ol>
     * <li>First element - one byte - revision of the SID structure (currently must be set to 0x01; the field name is Revision)
     * <li>Second element - one byte - is the number of sub authorities in that SID (this field name is SubAuthorityCount )
     * <li>The third element - 6 bytes - is identifying the authority under which the SID was created (field name is IdentifierAuthority).
     * <li>The fourth is a variable length array of unsigned 32 bits integers identifies a principal relative to the IdentifierAuthority -
     * the length of the array is determine by SubAuthorityCount (this field name is SubAuthority). Notice that the each sub authority is an
     * integer, It is important to remember that Microsoft Windows is built around little endian and this is why the code below reads each
     * sub authority 4 bytes from the high to the low byte.
     * </ol>
     * 
     * @param sid
     * @return
     */
    private String getSidAsString(byte[] sid) {
        // Add the 'S' prefix
        StringBuilder strSID = new StringBuilder("S-");

        // sid[0] : in the array is the version (must be 1 but might
        // change in the future)
        strSID.append(sid[0]).append('-');

        // sid[2..7] : the Authority
        StringBuilder sb = new StringBuilder();
        for (int t = 2; t <= 7; t++) {
            String hexString = Integer.toHexString(sid[t] & 0xFF);
            sb.append(hexString);
        }
        strSID.append(Long.parseLong(sb.toString(), 16));

        // sid[1] : the sub authorities count
        int count = sid[1];

        // sid[8..end] : the sub authorities (these are Integers - notice
        // the endian)
        for (int i = 0; i < count; i++) {
            int currSubAuthOffset = i * 4;
            sb.setLength(0);
            sb.append(String.format("%02X%02X%02X%02X",
                    (sid[11 + currSubAuthOffset] & 0xFF),
                    (sid[10 + currSubAuthOffset] & 0xFF),
                    (sid[9 + currSubAuthOffset] & 0xFF),
                    (sid[8 + currSubAuthOffset] & 0xFF)));

            strSID.append('-').append(Long.parseLong(sb.toString(), 16));
        }

        // That's it - we have the SID
        return strSID.toString();
    }

    /**
     * Generate LDAP Group query filter
     * 
     * @param dataAttribute
     * @param queryValues
     * @return
     */
    private String getGroupSidQueryFilter(
            String dataAttribute, List<String> queryValues) {
        AndFilter queryBuilder = new AndFilter();

        final Filter objClassfilter = new EqualsFilter("objectClass", "group");
        queryBuilder.and(objClassfilter);

        OrFilter sidQueryBuilder = new OrFilter();

        for (final String queryValue : queryValues) {

            final Filter filter;
            if (!queryValue.contains("*")) {
                filter = new EqualsFilter(dataAttribute, queryValue);
            } else {
                filter = new LikeFilter(dataAttribute, queryValue);
            }

            sidQueryBuilder.or(filter);
        }
        queryBuilder.and(sidQueryBuilder);

        return queryBuilder.encode();
    }

    /*
     * @see com.emc.storageos.auth.StorageOSPersonAttributeDao#isGroupValid(java.lang.String)
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean isGroupValid(final String groupId,
            ValidationFailureReason[] failureReason) {
        String group = groupId.substring(0, groupId.lastIndexOf("@"));
        String domain = groupId.substring(groupId.lastIndexOf("@") + 1);
        // only one result is needed
        final long countLimit = 1L;
        // Check if the group is on the whitelist
        if (isGroupOnWhiteList(group)) {
            // If the group is on the whitelist search AD/LDAP to see if it is valid
            final AndFilter queryBuilder = new AndFilter();

            final Filter groupObjClassfilter = createGroupObjectClassFilter();
            if (groupObjClassfilter == null) {
                _log.error(
                        "Group {} could not be searched in LDAP due to missing or empty LDAP Group properties of Authentication Provider. "
                                + "In order to work on groups in LDAP, kindly add the LDAP Group properties in Authentication Provider configuration.",
                        groupId);
                failureReason[0] = ValidationFailureReason.LDAP_CANNOT_SEARCH_GROUP_IN_LDAP_MODE;
                return false;
            }

            final Filter groupAttributeFilter = new EqualsFilter(_groupWhiteList.getType(), group);
            queryBuilder.and(groupObjClassfilter);
            queryBuilder.and(groupAttributeFilter);

            String[] returnAttributes = { _groupWhiteList.getType(), COMMON_NAME, getDistinguishedNameAttribute() };

            List<List<GroupAttribute>> queryGroupResults = null;
            try {
                queryGroupResults = searchAuthProvider(queryBuilder, returnAttributes, countLimit,
                        new GroupsMapper(_groupWhiteList.getType(), getDistinguishedNameAttribute()), failureReason);
            } catch (SizeLimitExceededException e) {
                _log.error(
                        "Multiple entries for group {} are found in AD/LDAP. Please use other group attributes such as objectSid or objectGUID to uniquely identify the group.",
                        groupId);
                failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
                return false;
            }
            if (!CollectionUtils.isEmpty(queryGroupResults)) {
                // validate if group DN match input group domain,
                // this could be different in AD forest scenario.
                GroupAttribute groupAttribute = queryGroupResults.get(0).get(0);
                if (groupAttribute == null) {
                    _log.error("Group {} matching with domain {} not present in AD/LDAP/UserGroup", groupId, domain);
                    return false;
                }

                if (!groupAttribute.domainMatch(domain)) {
                    _log.error("Group {} has dn as {}, which doesn't match its domain",
                            groupId, groupAttribute.getGroupDistinguishedName());
                    return false;
                }

                _log.debug("Group {} is valid", groupId);
                return true;
            } else {
                if (null != queryGroupResults) {
                    // null means Exception has been thrown and error logged already, empty means no group found in LDAP/AD
                    _log.error("Group {} matching with domain {} is not present in AD/LDAP/UserGroup", groupId, domain);
                    failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
                }
                return false;
            }
        }
        _log.error("Group {} is not on the whitelist", groupId);
        failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
        return false;
    }

    /*
     * @see com.emc.storageos.auth.StorageOSPersonAttributeDao#validateUser(java.lang.String, java.lang.String)
     */
    @Override
    public void validateUser(final String userId, final String tenantId, final String altTenantId) {

        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(userId, "");
        StorageOSUserDAO user = getStorageOSUser(creds);

        // the user must not be null and it must have tenant id
        boolean belongsToTenant = user.getTenantId().equals(tenantId);
        boolean belongsToAltTenant =
                (altTenantId != null) && user.getTenantId().equals(altTenantId);
        if (!(belongsToTenant || belongsToAltTenant)) {
            throw APIException.badRequests.principalSearchFailed(userId);
        }
    }

    /*
     * @see com.emc.storageos.auth.StorageOSPersonAttributeDao#getPerson(java.lang.String)
     */
    @Override
    public StorageOSUserDAO getStorageOSUser(final Credentials credentials,
            ValidationFailureReason[] failureReason) {
        final String username = ((UsernamePasswordCredentials) credentials).getUserName();
        UserAndTenants userAndTenants = getStorageOSUserAndTenants(username,
                failureReason);
        if (null != userAndTenants) {
            StorageOSUserDAO user = userAndTenants._user;
            Map<URI, UserMapping> tenants = userAndTenants._tenants;
            if (null == tenants || tenants.isEmpty()) {
                _log.error("User {} did not match any tenant", username);
            } else if (tenants.keySet().size() > 1) {
                _log.error("User {} mapped to tenants {}", username, tenants.keySet().toArray());
            } else {
                user.setTenantId(tenants.keySet().iterator().next().toString());
            }
            return user;
        }

        return null;
    }

    /*
     * another implementation of getStorageOSUser which throws Exception with error message instead of using failure reason.
     */
    @Override
    public StorageOSUserDAO getStorageOSUser(final Credentials credentials) {
        final String username = ((UsernamePasswordCredentials) credentials).getUserName();

        ValidationFailureReason[] failureReason = new ValidationFailureReason[1];

        UserAndTenants userAndTenants = getStorageOSUserAndTenants(username, failureReason);

        if (userAndTenants == null) {
            switch (failureReason[0]) {
                case LDAP_CONNECTION_FAILED:
                    throw SecurityException.fatals
                            .communicationToLDAPResourceFailed();
                case LDAP_MANAGER_AUTH_FAILED:
                    throw SecurityException.fatals.ldapManagerAuthenticationFailed();
                case USER_OR_GROUP_NOT_FOUND_FOR_TENANT:
                default:
                    throw APIException.badRequests.principalSearchFailed(username);
            }
        }

        StorageOSUserDAO user = userAndTenants._user;
        Map<URI, UserMapping> tenants = userAndTenants._tenants;

        if (null == tenants || tenants.isEmpty()) {
            _log.error("User {} did not match any tenant", username);
            throw APIException.forbidden.userDoesNotMapToAnyTenancy(user.getUserName());
        }

        if (tenants.keySet().size() > 1) {
            _log.error("User {} mapped to tenants {}", username, tenants.keySet().toArray());
            throw APIException.forbidden.userBelongsToMultiTenancy(user.getUserName(), tenantName(tenants.keySet()));
        }

        user.setTenantId(tenants.keySet().iterator().next().toString());
        return user;
    }

    private List<String> tenantName(Set<URI> uris) {
        List<String> tenantNames = new ArrayList<>();
        for (URI tId : uris) {
            TenantOrg t = _dbClient.queryObject(TenantOrg.class, tId);
            tenantNames.add(t.getLabel());
        }

        return tenantNames;
    }

    @Override
    public Map<URI, UserMapping> getUserTenants(String username) {
        ValidationFailureReason[] failureReason = new ValidationFailureReason[1];
        UserAndTenants userAndTenants = getStorageOSUserAndTenants(username,
                failureReason);
        if (null != userAndTenants) {
            return userAndTenants._tenants;
        }
        return null;
    }

    @Override
    public Map<URI, UserMapping> peekUserTenants(String username, URI tenantURI, List<UserMapping> userMapping) {
        ValidationFailureReason[] failureReason = new ValidationFailureReason[1];
        UserAndTenants userAndTenants = getStorageOSUserAndTenants(username,
                failureReason, tenantURI, userMapping);
        if (null != userAndTenants) {
            return userAndTenants._tenants;
        }
        return null;
    }

    /**
     * @see com.emc.storageos.auth.StorageOSPersonAttributeDao#setFailureHandler(LdapFailureHandler)
     * @param failureHandler
     */
    @Override
    public void setFailureHandler(LdapFailureHandler failureHandler) {
        _failureHandler = failureHandler;
    }

    /**
     * Search for the user in LDAP and create a StorageOSUserDAO and also
     * Map the user to tenant(s)
     * 
     * @param username name of the user
     * @return an object containing the StorageOSUserDao and a list of tenants
     */
    private UserAndTenants getStorageOSUserAndTenants(String username,
            ValidationFailureReason[] failureReason) {
        return getStorageOSUserAndTenants(username, failureReason, null, null);
    }

    private UserAndTenants getStorageOSUserAndTenants(String username,
            ValidationFailureReason[] failureReason,
            URI tenantURI, List<UserMapping> usermapping) {
        BasePermissionsHelper permissionsHelper = new BasePermissionsHelper(_dbClient, false);

        final String[] userDomain = username.split("@");
        if (userDomain.length < 2) {
            _log.error("Illegal username {} missing domain", username);
            failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
            return null;
        }
        final String domain = userDomain[1];
        final String ldapQuery = LdapFilterUtil.getPersonFilterWithValues(_filter, username);
        if (ldapQuery == null) {
            _log.error("Null query filter from string {} for username", _filter, username);
            failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
            return null;
        }

        StringSet authnProviderDomains = getAuthnProviderDomains(domain);

        List<String> attrs = new ArrayList<String>();
        Map<URI, List<UserMapping>> tenantToMappingMap = permissionsHelper.getAllUserMappingsForDomain(authnProviderDomains);
        if (_searchControls.getReturningAttributes() != null) {
            Collections.addAll(attrs, _searchControls.getReturningAttributes());
        }

        if (tenantURI != null) {
            tenantToMappingMap.put(tenantURI, usermapping);
        }

        printTenantToMappingMap(tenantToMappingMap);

        // Add attributes that need to be released for tenant mapping
        for (List<UserMapping> mappings : tenantToMappingMap.values()) {
            if (mappings == null) {
                continue;
            }

            for (UserMapping mapping : mappings) {
                if (mapping.getAttributes() != null && !mapping.getAttributes().isEmpty()) {
                    for (UserMappingAttribute mappingAttribute : mapping.getAttributes()) {
                        attrs.add(mappingAttribute.getKey());
                    }
                }
            }
        }

        // Now get the returning attributes from the userGroup table.
        getReturningAttributesFromUserGroups(permissionsHelper, domain, attrs);

        // Create search controls with the additional attributes to return
        SearchControls dnSearchControls = new SearchControls(
                _searchControls.getSearchScope(), _searchControls.getCountLimit(),
                _searchControls.getTimeLimit(), attrs.toArray(new String[attrs.size()]), _searchControls.getReturningObjFlag(),
                _searchControls.getDerefLinkFlag());

        Map<String, List<String>> userMappingAttributes = new HashMap<String, List<String>>();
        StorageOSUserMapper userMapper = new StorageOSUserMapper(username, getDistinguishedNameAttribute(), userMappingAttributes);
        // Execute the query
        @SuppressWarnings("unchecked")
        final List<StorageOSUserDAO> storageOSUsers = safeLdapSearch(_baseDN, ldapQuery,
                dnSearchControls, userMapper, failureReason);
        if (null == storageOSUsers) {
            _log.error("Query for user {} failed", username);
            return null;
        }

        StorageOSUserDAO storageOSUser = null;
        try {
            storageOSUser = DataAccessUtils.requiredUniqueResult(storageOSUsers);
            if (null == storageOSUser) {
                _log.error("Query for user {} yielded no results", username);
                failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
                return null;
            }
        } catch (IncorrectResultSizeDataAccessException ex) {
            _log.error("Query for user {} yielded incorrect number of results.", username, ex);
            failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
            return null;
        }

        // If the type is AD then fetch the users tokenGroups
        if (_type == AuthnProvider.ProvidersType.ad) {
            List<String> groups = queryTokenGroups(ldapQuery, storageOSUser);
            StringBuilder groupsString = new StringBuilder("[ ");
            for (String group : groups) {
                groupsString.append(group + " ");
                storageOSUser.addGroup(group);
            }
            groupsString.append("]");
            _log.debug("User {} adding groups {}", username, groupsString);
        } else {
            if (!updateGroupsAndRootGroupsInLDAPByMemberAttribute(storageOSUser, failureReason)) {
                // null means Exception has been thrown and error logged already, empty means no group found in LDAP/AD
                _log.info("User {} is not in any AD/LDAP groups.", storageOSUser.getDistinguishedName());
            }
        }

        // Add the user's group based on the attributes.
        addUserGroupsToUserGroupList(permissionsHelper, domain, storageOSUser);

        return new UserAndTenants(storageOSUser, mapUserToTenant(authnProviderDomains, storageOSUser, userMappingAttributes, tenantToMappingMap,
                failureReason));
    }

    /**
     * Match the user to one and only one tenant if found user there attributes/groups
     * 
     * @param domains
     * @param storageOSUser
     * @param attributeKeyValuesMap
     * @param tenantToMappingMap
     */
    private Map<URI, UserMapping> mapUserToTenant(StringSet domains, StorageOSUserDAO storageOSUser,
            Map<String, List<String>> attributeKeyValuesMap, Map<URI,
            List<UserMapping>> tenantToMappingMap, ValidationFailureReason[] failureReason) {
        Map<URI, UserMapping> tenants = new HashMap<URI, UserMapping>();
        if (CollectionUtils.isEmpty(domains)) {
            return tenants;
        }

        List<UserMappingAttribute> userMappingAttributes = new ArrayList<UserMappingAttribute>();

        for (Entry<String, List<String>> attributeKeyValues : attributeKeyValuesMap.entrySet()) {
            UserMappingAttribute userMappingAttribute = new UserMappingAttribute();
            userMappingAttribute.setKey(attributeKeyValues.getKey());
            userMappingAttribute.setValues(attributeKeyValues.getValue());
            userMappingAttributes.add(userMappingAttribute);
        }

        List<String> userMappingGroups = new ArrayList<String>();
        if (null != storageOSUser.getGroups()) {
            for (String group : storageOSUser.getGroups()) {
                userMappingGroups.add((group.split("@")[0]).toUpperCase());
                _log.debug("Adding user's group {} to usermapping group ", (group.split("@")[0]).toUpperCase());
            }
        }

        for (Entry<URI, List<UserMapping>> tenantToMappingMapEntry : tenantToMappingMap.entrySet()) {
            if (tenantToMappingMapEntry == null || tenantToMappingMapEntry.getValue() == null) {
                continue;
            }

            for (String domain : domains) {
                for (UserMapping userMapping : tenantToMappingMapEntry.getValue()) {
                    if (userMapping.isMatch(domain, userMappingAttributes, userMappingGroups)) {
                        tenants.put(tenantToMappingMapEntry.getKey(), userMapping);
                    }
                }
            }
        }

        // if no tenant was found then set it to the root tenant
        // unless the root tenant is restricted by a mapping
        if (tenants.isEmpty()) {

            BasePermissionsHelper permissionsHelper = new BasePermissionsHelper(_dbClient, false);
            TenantOrg rootTenant = permissionsHelper.getRootTenant();

            // check if UserMappingMap parameter contains provider tenant or not.
            // if yes, means Provider Tenant's user-mapping under modification.
            if (tenantToMappingMap.containsKey(rootTenant.getId())) {
                List<UserMapping> rootUserMapping = tenantToMappingMap.get(rootTenant.getId());

                // check if the change is to remove all user-mapping from provider tenant.
                // if yes, set user map to provider tenant.
                if (CollectionUtils.isEmpty(rootUserMapping)) {
                    _log.debug("User {} did not match a tenant.  Assigning to root tenant since root does not have any attribute mappings",
                            storageOSUser.getUserName());
                    tenants.put(rootTenant.getId(), null);
                }

                // provider tenant is not in UserMapping parameter, means no change to its user-mapping in this request,
                // need to check if its original user-mapping is empty or not.
            } else if (rootTenant.getUserMappings() == null || rootTenant.getUserMappings().isEmpty()) {
                _log.debug("User {} did not match a tenant.  Assigning to root tenant since root does not have any attribute mappings",
                        storageOSUser.getUserName());
                tenants.put(rootTenant.getId(), null);
            }
        }
        return tenants;
    }

    /**
     * Do the Ldap search and catch any connection errors.
     * Return null for caught exceptions, empty list for empty search result.
     * 
     * @param base the search base
     * @param ldapQuery the search query
     * @param searchControls the LDAP search controls
     * @param mapper the AttributeMapper
     * @return List of objects
     */
    private List safeLdapSearch(final String base, final String ldapQuery,
            final SearchControls searchControls, final AttributesMapper mapper) {
        ValidationFailureReason[] failureReason = new ValidationFailureReason[1];
        return safeLdapSearch(base, ldapQuery, searchControls, mapper, failureReason);
    }

    /**
     * Do the Ldap search and catch any connection errors.
     * Return null for caught exceptions, empty list for empty search result.
     * 
     * @param base the search base
     * @param ldapQuery the search query
     * @param searchControls the LDAP search controls
     * @param mapper the AttributeMapper
     * @param failureReason an output parameter which described the reason for the failure
     * @return List of objects
     */
    @SuppressWarnings("rawtypes")
    private List safeLdapSearch(final String base, final String ldapQuery,
            final SearchControls searchControls, final AttributesMapper mapper,
            ValidationFailureReason[] failureReason) {
        try {
            _log.debug("Ldap query to get user's attributes is {}", ldapQuery);
            return doLdapSearch(base, ldapQuery, searchControls, mapper);
        } catch (AuthenticationException e) {
            _log.error("Caught authentication exception connecting to ldap server", e);
            failureReason[0] = ValidationFailureReason.LDAP_MANAGER_AUTH_FAILED;
            return null;
        }
    }

    private List doLdapSearch(String base, String ldapQuery, SearchControls searchControls, AttributesMapper mapper) {
        List<LdapOrADServer> connectedServers = _ldapServers.getConnectedServers();
        for (LdapOrADServer server : connectedServers) {
            try {
                return doLdapSearchOnSingleServer(base, ldapQuery, searchControls, mapper, server);
            } catch (CommunicationException e) {
                _failureHandler.handle(_ldapServers, server);
                _log.info("Failed to connect to all AD/Ldap servers.", e);
            }
        }

        // Going here means attempts on all servers failed
        throw UnauthorizedException.unauthorized.ldapCommunicationException();
    }

    private List doLdapSearchOnSingleServer(String base, String ldapQuery, SearchControls searchControls, AttributesMapper mapper, LdapOrADServer server) {
        LdapTemplate ldapTemplate = buildLdapTeamplate(server);
        return ldapTemplate.search(base, ldapQuery, searchControls, mapper);
    }

    private LdapTemplate buildLdapTeamplate(LdapOrADServer server) {
        LdapTemplate ldapTemplate = new LdapTemplate(server.getContextSource());
        ldapTemplate.setIgnorePartialResultException(true); // To avoid the exceptions due to referrals returned
        return ldapTemplate;
    }

    /**
     * Get the user's token groups from AD. The groups returned will be on the
     * whitelist that is configured for this authN provider. Also the group name
     * in the list will be the configured user attribute.
     * 
     * @param ldapQuery Configured LDAP query to search for the user
     * @param storageOSUser The storageOSUser to get tokenGroups for
     * @return the names of the white listed groups for the user.
     */
    public List<String> queryTokenGroups(final String ldapQuery, final StorageOSUserDAO storageOSUser) {
        List<String> groups = new ArrayList<String>();
        SearchControls dnSearchControls;
        String dn = storageOSUser.getDistinguishedName();

        dnSearchControls = new SearchControls(
                SearchControls.OBJECT_SCOPE,
                1,
                _searchControls.getTimeLimit(),
                new String[] { TOKEN_GROUPS },
                _searchControls.getReturningObjFlag(), _searchControls.getDerefLinkFlag());

        @SuppressWarnings("unchecked")
        List<List<String>> tokenGroupSids =
                safeLdapSearch(dn, ldapQuery, dnSearchControls, new TokenGroupsMapper());
        if (null == tokenGroupSids) {
            _log.debug("No groups found for user: ", storageOSUser.getUserName());
            return groups;
        }

        List<String> unFilteredGroups = resolveGroups(tokenGroupSids.get(0));
        for (String groupName : unFilteredGroups) {
            String groupNameWithoutDomain = groupName.substring(0, groupName.lastIndexOf("@"));
            if (isGroupOnWhiteList(groupNameWithoutDomain)) {
                groups.add(groupName);
            }
        }
        return groups;
    }

    /**
     * Given a list of group SIDs do an LDAP search to get the configured group attribute
     * 
     * @param groupSids List of group object SIDs
     * @return a list of groups resolved using the group sids
     */
    public List<String> resolveGroups(final List<String> groupSids) {

        List<String> resolvedGroups = new ArrayList<String>();
        List<List<String>> partitionedGroupSids = Lists.partition(groupSids, _maxPageSize);
        _log.debug("User is in {} number of token groups", groupSids.size());
        if (partitionedGroupSids.size() > 1) {
            _log.info("Partitioning group query into {} lists since max results is {}", partitionedGroupSids.size(), _maxPageSize);
        }
        for (List<String> groupSidPartition : partitionedGroupSids) {
            SearchControls groupSearchControls = new SearchControls(
                    SearchControls.SUBTREE_SCOPE,
                    _maxPageSize, _searchControls.getTimeLimit(),
                    null, _searchControls.getReturningObjFlag(),
                    _searchControls.getDerefLinkFlag());

            final String groupSidLdapQuery = getGroupSidQueryFilter(OBJECT_SID, groupSidPartition);
            if (groupSidLdapQuery == null) {
                _log.error("Group sid query filter was null when trying to resolve groups");
                return resolvedGroups;
            }
            @SuppressWarnings("unchecked")
            final List<List<GroupAttribute>> resolvedGroupAttributeList = safeLdapSearch(_baseDN, groupSidLdapQuery,
                    groupSearchControls, new GroupsMapper(_groupWhiteList.getType()));
            if (null == resolvedGroupAttributeList) {
                _log.error("Query to resolve groups returned no results");
                return resolvedGroups;
            }
            for (List<GroupAttribute> resolvedGroupAttribute : resolvedGroupAttributeList) {
                if (!resolvedGroupAttribute.isEmpty()) {
                    resolvedGroups.add(resolvedGroupAttribute.get(0).getGroupNameWithDomain());
                }
            }
        }
        return resolvedGroups;
    }

    /**
     * Check if a group is on the whitelist for this authN provider
     * 
     * @param groupId ID of the group to check
     * @return true if the group is on the white list false otherwise
     */
    private boolean isGroupOnWhiteList(String groupId) {
        Pattern[] patterns = _groupWhiteList.getCompiledPatterns();
        if (patterns != null && patterns.length > 0) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(groupId).matches()) {
                    return true;
                }
            }
        } else {
            return true;
        }
        return false;
    }

    /**
     * Add the user group to the storageos user's
     * group list if the storageos user's attributes and values matches
     * with the user group configs.
     * 
     * @param permissionsHelper to find and match the db objects.
     * @param domain to find all the userMappings for the domain.
     * @param storageOSUser to be updated with the list of user
     *            group to the storageos user's group list.
     */
    private void addUserGroupsToUserGroupList(BasePermissionsHelper permissionsHelper, String domain, StorageOSUserDAO storageOSUser) {
        if (StringUtils.isBlank(domain)) {
            _log.error("Invalid domain {} to search user group", domain);
            return;
        }

        List<UserGroup> userGroupList = permissionsHelper.getAllUserGroupForDomain(domain);
        if (CollectionUtils.isEmpty(userGroupList)) {
            _log.debug("Cannot find user mappings for the domain {}", domain);
            return;
        }

        for (UserGroup userGroup : userGroupList) {
            if (userGroup != null) {
                if (permissionsHelper.matchUserAttributesToUserGroup(storageOSUser, userGroup)) {
                    _log.debug("Adding user group {} to the user", userGroup.getLabel());
                    storageOSUser.addGroup(userGroup.getLabel());
                }
            } else {
                _log.info("Invalid user group returned while searching db with domain {}", domain);
            }
        }
    }

    /**
     * Get the list of returning attributes AD or LDAP servers
     * based on the configured user group.
     * 
     * @param permissionsHelper to find and match the db objects.
     * @param domain to find all the configured user group for the domain.
     * @param attrs out param, to be updated with the list of attributes
     *            to be returned from the AD or LDAP servers.
     */
    private void getReturningAttributesFromUserGroups(BasePermissionsHelper permissionsHelper, String domain, List<String> attrs) {
        if (StringUtils.isBlank(domain)) {
            _log.info("Invalid domain {} to search user group", domain);
            return;
        }

        List<UserGroup> userGroupList = permissionsHelper.getAllUserGroupForDomain(domain);
        if (CollectionUtils.isEmpty(userGroupList)) {
            _log.debug("User group not found for the domain {}", domain);
            return;
        }

        for (UserGroup userGroup : userGroupList) {
            if (userGroup == null || CollectionUtils.isEmpty(userGroup.getAttributes())) {
                continue;
            }

            for (String userAttributesString : userGroup.getAttributes()) {
                if (StringUtils.isBlank(userAttributesString)) {
                    _log.info("Invalid user attributes param string {}", userAttributesString);
                    continue;
                }

                UserAttributeParam userAttributeParam = UserAttributeParam.fromString(userAttributesString);
                if (userAttributeParam == null) {
                    _log.info("Conversion from user attributes param string {} to attributes param object failed.", userAttributesString);
                    continue;
                }

                _log.debug("Adding attribute {} to the returning attributes list", userAttributeParam.getKey());
                attrs.add(userAttributeParam.getKey());
            }
        }
    }

    /**
     * Checks whether searching groups in LDAP can be done or not. This is done based on
     * the authentication provider's configuration. If the Authentication provider mode is
     * LDAP, it should have LDAP Group properties like group object classes and group
     * member attributes in order to search for the groups in LDAP.
     * 
     * @return - return true if the authentication provider's configuration contains valid
     *         group object classes and group member attributes otherwise false.
     * 
     */
    private boolean shouldSearchGroupInLDAP() {

        boolean continueToGroupSearch = false;
        if (!CollectionUtils.isEmpty(this._groupObjectClasses) &&
                !CollectionUtils.isEmpty(this._groupMemberAttributes)) {
            continueToGroupSearch = true;
        }

        return continueToGroupSearch;
    }

    /**
     * Creates the objectClass filter for the AD or LDAP search. For AD, the only possible
     * objectClass filter is "group" for LDAP, it can be any of "groupOfNames", "groupOfUniqueNames",
     * "posixGroup", "organizationalRole". These are inputs from API payload. The default is "groupOfNames".
     * 
     * @return - returns the objectClass filter based on the type of the authn provider.
     *         null for the ldap authn provider if authn provider configuration does not
     *         contain any ldap group search properties like object classes and group
     *         member attributes.
     * 
     */
    private Filter createGroupObjectClassFilter() {

        OrFilter groupObjectClassFilter = null;
        if (_type == ProvidersType.ad) {
            groupObjectClassFilter = new OrFilter();
            final Filter localObjectClassFilter = new EqualsFilter("objectClass", "group");
            groupObjectClassFilter.or(localObjectClassFilter);
        } else {
            if (shouldSearchGroupInLDAP()) {
                groupObjectClassFilter = new OrFilter();
                for (String objectClass : this._groupObjectClasses) {
                    final Filter localObjectClassFilter = new EqualsFilter("objectClass", objectClass);
                    groupObjectClassFilter.or(localObjectClassFilter);
                }
            }
        }

        return groupObjectClassFilter;
    }

    /**
     * Returns the distinguished name's attribute type name based on the
     * authentication provider type.
     * 
     * @return - returns the DN attributeType name based on the authn
     *         provider type.
     * 
     */
    private String getDistinguishedNameAttribute() {
        String distinguishedNameAttr;
        if (_type == ProvidersType.ad) {
            distinguishedNameAttr = AD_DISTINGUISHED_NAME;
        } else {
            distinguishedNameAttr = LDAP_DISTINGUISHED_NAME;
        }
        return distinguishedNameAttr;
    }

    /**
     * Finds the group based on one of (either groupOfNames, groupOfUniqueNames, posixGroup, organizationalRole)
     * objectClass and one of these attribute (either member, uniqueMember, memberUid, roleOccupant) attributes
     * from LDAP.
     * This function finds multiple level user's group membership in the given search base.
     * 
     * @param storageOSUser - A domain user whose group member is being found.
     * @param failureReason - A string to be updated with failure reason if there is any failure.
     * 
     * @return - true if the search is successful (or if the authentication provider is not in ldap mode).
     *         otherwise false.
     * 
     */
    private boolean updateGroupsAndRootGroupsInLDAPByMemberAttribute(StorageOSUserDAO storageOSUser,
            ValidationFailureReason[] failureReason) {
        boolean foundUserGroups = false;
        if (_type != ProvidersType.ldap) {
            return foundUserGroups;
        }

        String memberEntryDN = storageOSUser.getDistinguishedName();
        Set<String> allGroupsOfUser = new HashSet<String>();
        findGroupsInLDAPByMemberAttribute(memberEntryDN, allGroupsOfUser, failureReason);

        if (CollectionUtils.isEmpty(allGroupsOfUser)) {
            return foundUserGroups;
        }

        for (String groupWithDomain : allGroupsOfUser) {
            if (StringUtils.isNotBlank(groupWithDomain)) {
                storageOSUser.addGroup(groupWithDomain);
                foundUserGroups = true;
                _log.debug("Group {} added to user {}", groupWithDomain, storageOSUser.getDistinguishedName());
            }
        }
        return foundUserGroups;
    }

    /**
     * Finds the group based on one of (ex., groupOfNames, groupOfUniqueNames, posixGroup, organizationalRole)
     * objectClass and one of these attribute (ex., member, uniqueMember, memberUid, roleOccupant) attributes
     * from LDAP.
     * 
     * @param memberEntryDN - A value to be searched in the values of any of the given group's attribute.
     * @param allGroupsOfUserWithDomain - An out param. Set all the groups with domain suffix to which the user is member off.
     * @param failureReason - A string to be updated with failure reason if there is any failure.
     * 
     */
    private void findGroupsInLDAPByMemberAttribute(String memberEntryDN, Set<String> allGroupsOfUserWithDomain,
            ValidationFailureReason[] failureReason) {
        if (_type != ProvidersType.ldap) {
            _log.info("Non ldap authn provider.");
            return;
        }

        if (StringUtils.isBlank(memberEntryDN)) {
            _log.error("Invalid DN {} to search in ldap.", memberEntryDN);
        }

        final Filter groupObjectClassFilter = createGroupObjectClassFilter();
        if (groupObjectClassFilter == null) {
            // Empty LDAP group search properties. Just return true.
            _log.info("Empty ldap group object classes or attributes.");
            return;
        }

        final long countLimit = 0L;
        final AndFilter queryBuilder = new AndFilter();
        final OrFilter groupMemberAttributeFilter = new OrFilter();

        for (String groupMemberAttribute : this._groupMemberAttributes) {
            // Create or filter based on all the group member attributes given
            // the user in either portal or API.
            final Filter localGroupMemberAttributeFilter = new EqualsFilter(groupMemberAttribute, memberEntryDN);
            groupMemberAttributeFilter.or(localGroupMemberAttributeFilter);
        }

        // Query filter is based on the given objectClasses, member attributes.
        queryBuilder.and(groupObjectClassFilter);
        queryBuilder.and(groupMemberAttributeFilter);

        // Expecting the attributes from the search results that can be used to construct
        // the GroupAttribute object.
        Set<String> returnAttributesSet = new HashSet<String>(this._groupMemberAttributes);
        returnAttributesSet.add(COMMON_NAME);
        returnAttributesSet.add(OBJECT_CLASS);
        returnAttributesSet.add(getDistinguishedNameAttribute());

        String[] returnAttributes = returnAttributesSet.toArray(new String[returnAttributesSet.size()]);

        List<List<GroupAttribute>> queryGroupResults = null;
        try {
            queryGroupResults = searchAuthProvider(queryBuilder, returnAttributes, countLimit,
                    new GroupsMapper(_groupWhiteList.getType(), getDistinguishedNameAttribute()), failureReason);
        } catch (SizeLimitExceededException e) {
            _log.error("Multiple entries for group are found in LDAP. Please use other group attributes such as cn or entryDN to uniquely identify the group.");
            failureReason[0] = ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT;
        }

        if (CollectionUtils.isEmpty(queryGroupResults)) {
            _log.debug("{} is not a member of any group ", memberEntryDN);
            return;
        }

        if (allGroupsOfUserWithDomain == null) {
            allGroupsOfUserWithDomain = new HashSet<String>();
        }

        for (List<GroupAttribute> groupAttrResults : queryGroupResults) {
            if (CollectionUtils.isEmpty(groupAttrResults)) {
                continue;
            }

            for (GroupAttribute groupAttr : groupAttrResults) {
                String groupDN = groupAttr.getGroupDistinguishedName();
                allGroupsOfUserWithDomain.add(groupAttr.getGroupNameWithDomain());

                _log.debug("Finding the higher level group of {}", groupDN);
                findGroupsInLDAPByMemberAttribute(groupAttr.getGroupDistinguishedName(), allGroupsOfUserWithDomain, failureReason);
            }
        }
    }

    /**
     * Searches the Authentication Provider (either LDAP or AD) based on the given
     * search controls and required return attributes and return count limit.
     * 
     * @param queryBuilder - A query to be used to search in authn provider.
     * @param returnAttributes - An array of attributes to be returned from the search.
     * @param countLimit - number of expected objects to be returns that matches the
     *            search query.
     * @param mapper - An attribute mapper used to extract the required values from
     *            the search.
     * @param failureReason - A string to be updated with failure reason if there is any failure.
     * 
     * @return - Returns the list of GroupAttribute that matches the search criteria.
     * 
     */
    @SuppressWarnings("unchecked")
    private List<List<GroupAttribute>> searchAuthProvider(
            Filter queryBuilder, String[] returnAttributes,
            final long countLimit,
            AttributesMapper mapper, ValidationFailureReason[] failureReason) throws SizeLimitExceededException {

        SearchControls groupSearchControls = new SearchControls(
                SearchControls.SUBTREE_SCOPE,
                countLimit, _searchControls.getTimeLimit(),
                returnAttributes, _searchControls.getReturningObjFlag(),
                _searchControls.getDerefLinkFlag());

        List<List<GroupAttribute>> queryGroupResults = null;
        queryGroupResults = safeLdapSearch(_baseDN, queryBuilder.encode(), groupSearchControls, mapper, failureReason);

        return queryGroupResults;
    }

    public void setLdapServers(LdapServerList ldapServers) {
        _ldapServers = ldapServers;
    }

    /**
     * Class to implement map tokenGroups attribute to a list of SID strings
     */
    private class TokenGroupsMapper implements AttributesMapper {
        @Override
        public Object mapFromAttributes(Attributes attributes) throws NamingException {
            List<String> tokenGroupSids = new ArrayList<String>();
            Attribute tokenGroupsAttr = attributes.get(TOKEN_GROUPS);
            if (null != tokenGroupsAttr) {
                NamingEnumeration<?> tokenGroups = tokenGroupsAttr.getAll();
                while (tokenGroups.hasMoreElements()) {
                    byte[] bytes = (byte[]) tokenGroups.nextElement();
                    if (null != bytes) {
                        tokenGroupSids.add(getSidAsString(bytes));
                    }
                }
            }
            return tokenGroupSids;
        }
    }

    /**
     * Class to map a group Attributes to a string list of groups
     */
    private class GroupsMapper implements AttributesMapper {
        private final String _groupAttribute;
        private final String _groupDNAttributeTypeName;

        public GroupsMapper(String groupAttribute) {
            this(groupAttribute, AD_DISTINGUISHED_NAME);
        }

        public GroupsMapper(String groupAttribute, String distinguishedNameId) {
            super();
            _groupAttribute = groupAttribute;
            _groupDNAttributeTypeName = distinguishedNameId;
        }

        @Override
        public Object mapFromAttributes(Attributes attributes) throws NamingException {
            List<GroupAttribute> groups = new ArrayList<GroupAttribute>();

            // get distinguishedName
            Attribute dnAttr = attributes.get(_groupDNAttributeTypeName);
            String dn = null;
            if (null != dnAttr) {
                dn = (String) dnAttr.get();
            }

            Attribute cnAttribute = attributes.get("cn");
            String cn = null;
            if (cnAttribute != null) {
                cn = (String) cnAttribute.get();
            }

            Attribute groupAttr = attributes.get(_groupAttribute);
            if (null != groupAttr) {
                NamingEnumeration<?> groupAttrValues = groupAttr.getAll();
                while (groupAttrValues.hasMoreElements()) {
                    Object group = groupAttrValues.nextElement();
                    if (null != group) {
                        String groupString;
                        if (group instanceof byte[]) {
                            groupString = getSidAsString((byte[]) group);
                        } else {
                            groupString = group.toString();
                        }
                        GroupAttribute groupObject = new GroupAttribute();
                        groupObject.setGroupAttributeValue(groupString);
                        groupObject.setGroupDistinguishedName(dn);
                        groupObject.setGroupCommonName(cn);
                        groups.add(groupObject);
                    }
                }
            }
            return groups;
        }
    }

    /**
     * group attribute returns from ldap search, it contains
     * 1. the attribute value specified by the search,
     * 2. and distinguishName of the group object, which will be used for domain validation.
     */
    private class GroupAttribute {
        private String groupAttributeValue;
        private String groupDistinguishedName;
        private String groupCommonName;
        private String groupDomain;

        public void setGroupAttributeValue(String value) {
            groupAttributeValue = value;
        }

        public void setGroupDistinguishedName(String dn) {
            groupDistinguishedName = dn;
        }

        public String getGroupAttributeValue() {
            return groupAttributeValue;
        }

        public String getGroupDistinguishedName() {
            return groupDistinguishedName;
        }

        public String getGroupCommonName() {
            return groupCommonName;
        }

        public void setGroupCommonName(String groupCommonName) {
            this.groupCommonName = groupCommonName;
        }

        public String getGroupDomain() {
            return groupDomain;
        }

        public void setGroupDomain(String groupDomain) {
            this.groupDomain = groupDomain;
        }

        /**
         * validate if the group object match the specified domain
         * 
         * @param domain format as "vipr.com"
         * @return
         */
        public boolean domainMatch(String domain) {
            if (domain == null || groupDistinguishedName == null) {
                return false;
            }

            if (StringUtils.isBlank(this.groupDomain)) {
                this.groupDomain = getDomainFromDN();
            }

            if (domain.equalsIgnoreCase(this.groupDomain)) {
                return true;
            }

            return false;
        }

        /**
         * construct domain from group's distinguish name,
         * 
         * @return domain extract DC parts from dn, formatted as "vipr.com"
         */
        public String getDomainFromDN() {
            if (groupDistinguishedName == null) {
                groupDistinguishedName = "";
            }

            String domainName = "";
            try {
                LdapName dn = new LdapName(groupDistinguishedName);
                List<String> dcs = new LinkedList<String>();
                for (Rdn rdn : dn.getRdns()) {
                    if (rdn.getType().equalsIgnoreCase("DC")) {
                        dcs.add(0, rdn.getValue().toString());
                    }
                }
                domainName = StringUtils.join(dcs, '.');
            } catch (InvalidNameException e) {
                _log.error("{} is not a standard dn", groupDistinguishedName);
            }

            return domainName;
        }

        /**
         * construct group name with domain name
         */
        public String getGroupNameWithDomain() {
            if (StringUtils.isBlank(this.groupDomain)) {
                this.groupDomain = getDomainFromDN();
            }
            return groupAttributeValue + "@" + this.groupDomain;
        }
    }

    /**
     * Private class to be a container for a StorageOSUserDAO
     * And their tenants
     */
    private class UserAndTenants {
        public UserAndTenants(StorageOSUserDAO user,
                Map<URI, UserMapping> tenants) {
            _user = user;
            _tenants = tenants;
        }

        private final StorageOSUserDAO _user;
        private final Map<URI, UserMapping> _tenants;
    }

    private void printTenantToMappingMap(Map<URI, List<UserMapping>> maps) {
        Iterator<URI> keys = maps.keySet().iterator();

        _log.debug("user mapping: ");
        while (keys.hasNext()) {
            URI key = keys.next();
            if (maps.get(key) != null) {
                _log.debug(key + " = " + maps.get(key).toString());
            }
        }
    }

    /**
     * Gets all the domains supported by the authn providers that supports
     * the particular domain.
     *
     * @param domain to find the supported authn provider.
     * @return returns all the supported domains of each authn provider
     * supports the domain.
     */
    private StringSet getAuthnProviderDomains(String domain) {
        StringSet authnProviderDomains = new StringSet();
        URIQueryResultList providers = new URIQueryResultList();
        try {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getAuthnProviderDomainConstraint(domain.toLowerCase()), providers);
        } catch (DatabaseException ex) {
            _log.error(
                    "Could not query for authn providers to check for existing domain {}",
                    domain, ex.getStackTrace());
            throw ex;
        }

        //Add all the domains of the AuthnProvider if it is not in disabled state.
        //We expect only one authn provider here because, we cannot have multiple
        //authn provider supporting same domain.
        Iterator<URI> it = providers.iterator();
        if (it.hasNext()) {
            URI providerURI = it.next();
            AuthnProvider provider = _dbClient.queryObject(AuthnProvider.class, providerURI);
            if (provider != null && provider.getDisable() == false) {
                authnProviderDomains.addAll(provider.getDomains());
            }
        }

        return authnProviderDomains;
    }
}
