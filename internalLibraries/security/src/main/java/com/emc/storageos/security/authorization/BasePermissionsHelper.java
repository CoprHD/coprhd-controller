/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authorization;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.*;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.exceptions.FatalDatabaseException;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.tenant.UserMappingAttributeParam;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.model.usergroup.UserAttributeParam;
import com.emc.storageos.security.SecurityDisabler;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import javax.xml.bind.annotation.XmlElement;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * Class provides helper methods for accessing roles and acls from db
 */
public class BasePermissionsHelper {
    private static final Logger _log = LoggerFactory.getLogger(BasePermissionsHelper.class);
    private static final String ROOT = "root";
    private DbClient _dbClient = null;
    private boolean _usingCache = true;
    private Map<CoordinatorClient.LicenseType, Boolean> licensedCache = new HashMap<CoordinatorClient.LicenseType, Boolean>();

    @Autowired(required = false)
    private SecurityDisabler _disabler;

    @Autowired
    private CoordinatorClient _coordinatorClient;

    /**
     * Determines if the specific license type is enabled.
     * 
     * @param type
     *            the type of the license.
     * @return true if the license is found.
     */
    public boolean isLicensed(CoordinatorClient.LicenseType type) {
        boolean licensed = Boolean.TRUE.equals(licensedCache.get(type));
        if (!licensed) {
            licensed = _coordinatorClient.isStorageProductLicensed(type);
            licensedCache.put(type, licensed);
        }
        return licensed;
    }

    /**
     * Determines if any of the provided licenses are enabled.
     * 
     * @param types
     *            the license types.
     * @return true if any of the provided licenses are found.
     */
    public boolean isAnyLicensed(CoordinatorClient.LicenseType... types) {
        for (CoordinatorClient.LicenseType type : types) {
            if (isLicensed(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if there are any licenses enabled.
     * 
     * @return true if any license is found.
     */
    public boolean hasAnyLicense() {
        return isAnyLicensed(CoordinatorClient.LicenseType.values());
    }

    private static boolean containsAllIgnoreCase(List<String> left, List<String> right) {
        for (String rightString : right) {
            if (!containsIgnoreCase(left, rightString)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the two lists contain the same string values
     * ignoring case
     * 
     * @param left
     * @param right
     * @return true if left and right lists are equal ignoring case
     *         and list position
     */
    private static boolean listEqualsIgnoreCase(List<String> left, List<String> right) {
        if (left == null) {
            if (right != null) {
                return false;
            }
        } else if (right == null) {
            return false;
        } else if (left.size() != right.size()) {
            return false;
        }
        return containsAllIgnoreCase(left, right);
    }

    private static boolean containsIgnoreCase(List<String> left, String rightString) {
        for (String leftString : left) {
            if (leftString.equalsIgnoreCase(rightString)) {
                return true;
            }
        }
        return false;
    }

    public static class UserMapping {
        private String _domain;
        private List<UserMappingAttribute> _attributes;
        private List<String> _groups;

        public String getDomain() {
            return _domain == null ? null : _domain.toLowerCase();
        }

        public void setDomain(String domain) {
            _domain = domain;
        }

        public List<UserMappingAttribute> getAttributes() {
            return _attributes;
        }

        public void setAttributes(List<UserMappingAttribute> attributes) {
            _attributes = attributes;
        }

        public List<String> getGroups() {
            return _groups;
        }

        public void setGroups(List<String> groups) {
            _groups = groups;
        }

        public UserMapping() {
            _attributes = new ArrayList<UserMappingAttribute>();
            _groups = new ArrayList<String>();
        }

        public UserMapping(UserMappingParam param) {
            _domain = param.getDomain();
            _groups = param.getGroups() == null ? new ArrayList<String>() : param.getGroups();
            if (param.getAttributes() != null) {
                _attributes = new ArrayList<UserMappingAttribute>();
                for (UserMappingAttributeParam attribute : param.getAttributes()) {
                    _attributes.add(new UserMappingAttribute(attribute));
                }
            } else {
                _attributes = new ArrayList<UserMappingAttribute>();
            }
        }

        public static List<UserMapping> fromParamList(List<UserMappingParam> params) {
            if (params == null) {
                return null;
            }
            List<UserMapping> userMappings = new ArrayList<UserMapping>();
            for (UserMappingParam param : params) {
                userMappings.add(new UserMapping(param));
            }
            return userMappings;
        }

        public static UserMappingParam toParam(UserMapping mapping) {
            UserMappingParam param = new UserMappingParam();
            param.setDomain(mapping.getDomain());
            param.setGroups(mapping.getGroups());
            if (mapping.getAttributes() != null) {
                List<UserMappingAttributeParam> attrList = new ArrayList<UserMappingAttributeParam>();
                for (UserMappingAttribute attribute : mapping.getAttributes()) {
                    attrList.add(UserMappingAttribute.toParam(attribute));
                }
                param.setAttributes(attrList);
            } else {
                param.setAttributes(new ArrayList<UserMappingAttributeParam>());
            }
            return param;
        }

        /**
         * Check if this mapping conflicts with the other mapping
         * 
         * @param other
         * @return
         */
        public boolean isMatch(UserMapping other) {
            // return true if users with this mapping would match the other mapping or
            // users with the other mapping would match this mapping
            return isMatch(other.getDomain(), other.getAttributes(), other.getGroups())
                    || other.isMatch(getDomain(), getAttributes(), getGroups());
        }

        /**
         * Check if the domain, attributes, and groups results in a match with this UserMapping object
         * 
         * @param domain
         * @param attributes
         * @param groups
         * @return true if there is a match with this mapping
         */
        public boolean isMatch(String domain, List<UserMappingAttribute> attributes, List<String> groups) {
            return _domain.equalsIgnoreCase(domain) && attributesMatch(attributes) && groupsMatch(groups);
        }

        /**
         * check if the other mapping contains all of this mapping's groups
         * 
         * @param groups the other mapping to compare to
         * @return true if this mapping's groups are a sublist of the other mapping
         */
        private boolean groupsMatch(List<String> groups) {
            // If there are no groups in this mapping then they match inherently
            // if there are groups then check if the groups passed in contain all of the
            // groups in the mapping
            return !hasGroups() || (groups != null && containsAllIgnoreCase(groups, _groups));
        }

        /**
         * Check if this mapping has groups
         * 
         * @return true if the groups list is not null and not empty
         */
        private boolean hasGroups() {
            return getGroups() != null && !getGroups().isEmpty();
        }

        /**
         * Check if this mapping's attributes conflicts with the
         * other mappings attributes
         * 
         * @param attributes The other mapping to compare this mapping to
         * @return true if the mapping's attributes conflict with the other
         */
        private boolean attributesMatch(List<UserMappingAttribute> attributes) {
            return !hasAttributes() || (attributes != null && containsAllAttributes(attributes, getAttributes()));
        }

        /**
         * Check if this user mappings attributes are equal to a list of
         * user mapping attributes
         * 
         * @param right list of attributes to compare to
         * @return true if the attribute lists are equal
         */
        private boolean equalsAttributes(List<UserMappingAttribute> right) {
            if (_attributes == null) {
                if (right != null) {
                    return false;
                }
            } else if (right == null) {
                return false;
            }
            if (_attributes.size() != right.size()) {
                return false;
            } else {
                return _attributes.containsAll(right);
            }
        }

        private boolean containsAllAttributes(
                List<UserMappingAttribute> leftAttributes,
                List<UserMappingAttribute> rightAttributes) {
            for (UserMappingAttribute rightAttribute : rightAttributes) {
                if (!containsAttribute(leftAttributes, rightAttribute)) {
                    return false;
                }
            }
            return true;
        }

        private boolean containsAttribute(
                List<UserMappingAttribute> leftAttributes,
                UserMappingAttribute rightAttribute) {
            for (UserMappingAttribute leftAttribute : leftAttributes) {
                if (leftAttribute.getKey().equalsIgnoreCase(rightAttribute.getKey())
                        && containsAllIgnoreCase(leftAttribute.getValues(), rightAttribute.getValues())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Check if the mapping has attributes
         * 
         * @return true if the list of attributes is not null and is not empty
         */
        private boolean hasAttributes() {
            return getAttributes() != null && !getAttributes().isEmpty();
        }

        @Override
        public String toString() {
            ObjectMapper mapper = new ObjectMapper();
            try {
                _domain = _domain.toLowerCase();
                return mapper.writeValueAsString(this);
            } catch (JsonGenerationException e) {
                _log.error("Failed to convert mapping to string.", e);
            } catch (JsonMappingException e) {
                _log.error("Failed to convert mapping to string.", e);
            } catch (IOException e) {
                _log.error("Failed to convert mapping to string.", e);
            }
            return null;
        }

        public static UserMapping fromString(String userMappingString) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper.readValue(userMappingString, UserMapping.class);
            } catch (JsonParseException e) {
                _log.error("Failed to convert mapping to string.", e);
            } catch (JsonMappingException e) {
                _log.error("Failed to convert mapping to string.", e);
            } catch (IOException e) {
                _log.error("Failed to convert mapping to string.", e);
            }
            return null;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((_attributes == null) ? 0 : _attributes.hashCode());
            result = prime * result
                    + ((_domain == null) ? 0 : _domain.hashCode());
            result = prime * result
                    + ((_groups == null) ? 0 : _groups.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            UserMapping other = (UserMapping) obj;

            if (_domain == null) {
                if (other._domain != null) {
                    return false;
                }
            } else if (!_domain.equalsIgnoreCase(other._domain)) {
                return false;
            }
            if (!listEqualsIgnoreCase(_groups, other._groups)) {
                return false;
            }
            return equalsAttributes(other._attributes);
        }
    }

    public static class UserMappingAttribute {
        private String _key;
        private List<String> _values;

        public UserMappingAttribute() {
            _values = new ArrayList<String>();
        }

        public UserMappingAttribute(UserMappingAttributeParam param) {
            _key = param.getKey();
            _values = param.getValues();
        }

        public static UserMappingAttributeParam toParam(UserMappingAttribute attribute) {
            UserMappingAttributeParam param = new UserMappingAttributeParam();
            param.setKey(attribute.getKey());
            param.setValues(attribute.getValues());
            return param;
        }

        @XmlElement(required = true, name = "key")
        public String getKey() {
            return _key;
        }

        public void setKey(String key) {
            _key = key;
        }

        @XmlElement(required = true, name = "value")
        public List<String> getValues() {
            return _values;
        }

        public void setValues(List<String> values) {
            _values = values;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((_key == null) ? 0 : _key.hashCode());
            result = prime * result
                    + ((_values == null) ? 0 : _values.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            UserMappingAttribute other = (UserMappingAttribute) obj;
            if (_key == null) {
                if (other._key != null) {
                    return false;
                }
            } else if (!_key.equalsIgnoreCase(other._key)) {
                return false;
            }
            return listEqualsIgnoreCase(_values, other._values);
        }

    }

    /**
     * Constructor - takes db client
     * 
     * @param dbClient
     */
    public BasePermissionsHelper(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Constructor - takes db client, using cache
     * 
     * @param dbClient
     * @param usingCache
     */
    public BasePermissionsHelper(DbClient dbClient, boolean usingCache) {
        _dbClient = dbClient;
        _usingCache = usingCache;
    }

    public void setUsingCache(boolean usingCache) {
        _usingCache = usingCache;
    }

    /**
     * Find the tenant for a user based on the attribute (key=value) string
     * 
     * @param userMapping
     * @return URI of the tenant
     */
    public URI lookupTenant(final UserMapping userMapping) {
        if (userMapping == null) {
            _log.warn("user mapping is empty");
            return null;
        }
        try {
            final List<URI> tenantIds = new ArrayList<URI>();
            QueryResultList<URI> results = new QueryResultList<URI>() {
                @Override
                public URI createQueryHit(URI uri) {
                    // none
                    return uri;
                }

                @Override
                public URI createQueryHit(URI uri, String queryUserMapping, UUID timestamp) {
                    if (userMapping.isMatch(UserMapping.fromString(queryUserMapping))) {
                        tenantIds.add(uri);
                    }
                    return uri;
                }

                @Override
                public URI createQueryHit(URI uri, Object entry) {
                    if (entry instanceof String) {
                        return createQueryHit(uri, (String) entry, null);
                    }
                    else {
                        return createQueryHit(uri);
                    }
                }
            };
            _dbClient.queryByConstraint(ContainmentPermissionsConstraint.Factory.getUserMappingsWithDomain(userMapping.getDomain()),
                    results);
            for (Iterator<URI> iterator = results.iterator(); iterator.hasNext(); iterator.next()) {
                ;
            }

            if (tenantIds.isEmpty()) {
                _log.warn("Tenant lookup returned empty for mapping: {}", userMapping.toString());
                return null;
            } else if (tenantIds.size() > 1) {
                _log.warn("Tenant lookup returned {} tenants for mapping: {}", userMapping.toString());
                return null;
            } else {
                return tenantIds.get(0);
            }
        } catch (DatabaseException ex) {
            _log.error("tenant user mapping query failed", ex);
        }
        return null;
    }

    /**
     * Returns root TenantOrg
     * 
     * @return
     */
    public TenantOrg getRootTenant() {
        if (_usingCache && QueriedObjectCache.getRootTenantOrgObject() != null) {
            return QueriedObjectCache.getRootTenantOrgObject();
        }
        URIQueryResultList tenants = new URIQueryResultList();
        try {
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                    tenants);
            if (tenants.iterator().hasNext()) {
                URI root = tenants.iterator().next();
                TenantOrg rootTenant = _dbClient.queryObject(TenantOrg.class, root);
                QueriedObjectCache.setRootTenantObject(rootTenant);
                // safety check to prevent further operations when root tenant is messed up
                // It is possible have multiple index entries for the same root tenant at a certain period (CQ610571)
                while (tenants.iterator().hasNext()) {
                    URI mulRoot = tenants.iterator().next();
                    if (!mulRoot.equals(root)) {
                        _log.error("multiple entries found for root tenant. Stop.");
                        throw SecurityException.fatals.rootTenantQueryReturnedDuplicates();
                    }
                }
                return rootTenant;
            } else {
                _log.error("root tenant query returned no results");
            }
        } catch (DatabaseException ex) {
            throw SecurityException.fatals.tenantQueryFailed(TenantOrg.NO_PARENT, ex);
        }
        throw SecurityException.fatals.tenantQueryFailed(TenantOrg.NO_PARENT);
    }

    /**
     * Get object from id
     * 
     * @param id
     * @return
     */
    public <T extends DataObject> T getObjectById(URI id, Class<T> clazz) {
        return getObjectById(id, clazz, false);
    }

    public <T extends DataObject> T getObjectById(URI id, Class<T> clazz,
            boolean bypassCache) {
        T ret = null;
        boolean usingCache = _usingCache && !bypassCache;
        if (usingCache) {
            ret = QueriedObjectCache.getObject(id, clazz);
        }
        if (ret == null) {
            ret = _dbClient.queryObject(clazz, id);
            if (ret != null && usingCache) {
                QueriedObjectCache.setObject(ret);
            }
        }
        return ret;
    }

    /**
     * Same as queryObjectById(URI, Class). Takes NamedURI instead.
     */
    public <T extends DataObject> T getObjectById(NamedURI id, Class<T> clazz) {
        return getObjectById(id.getURI(), clazz, false);
    }

    /**
     * adds user's roles on root tenant to user object
     * 
     * @param user
     */
    public void populateZoneRoles(StorageOSUser user, VirtualDataCenter vdc) {
        if (_disabler != null) {
            return;
        }
        // from upn
        Set<String> userRoles = vdc.getRoleSet(
                new PermissionsKey(PermissionsKey.Type.SID, user.getName()).toString());

        // in case db migration haven't finished yet, read vdc roles from root tenant.
        TenantOrg root = getRootTenant();
        if (CollectionUtils.isEmpty(userRoles)) {
            userRoles = root.getRoleSet(
                    new PermissionsKey(PermissionsKey.Type.SID, user.getName()).toString());
        }

        if (userRoles != null) {
            for (String role : userRoles) {
                if (isRoleZoneLevel(role)) {
                    user.addRole(role);
                }
            }
        }

        // from groups
        Set<String> groups = user.getGroups();
        if (!CollectionUtils.isEmpty(groups)) {
            for (String group : groups) {
                // add if any roles for the groups, from the vdc
                Set<String> roleSet = vdc.getRoleSet(
                        new PermissionsKey(PermissionsKey.Type.GROUP, group).toString());

                // in case db migration haven't finished yet, read vdc roles from root tenant.
                if (CollectionUtils.isEmpty(roleSet)) {
                    roleSet = root.getRoleSet(
                            new PermissionsKey(PermissionsKey.Type.GROUP, group).toString());
                }

                if (null != roleSet) {
                    for (String role : roleSet) {
                        if (isRoleZoneLevel(role)) {
                            user.addRole(role);
                        }
                    }
                }
            }
        }

        // Now based on userGroup role assignments.
        updateUserVdcRolesBasedOnUserGroup(user, vdc);
    }

    /**
     * Get tenant id from a project id retrieved from uri
     * 
     * @param projectId
     * @return
     */
    public URI getTenantIdFromProjectId(String projectId, boolean idEmbeddedInURL) {
        if (projectId == null) {
            return null;
        }

        try {
            URI id = URI.create(projectId);
            Project ret = getObjectById(id, Project.class);
            if (ret == null) {
                if (idEmbeddedInURL) {
                    throw APIException.notFound.unableToFindEntityInURL(id);
                } else {
                    throw APIException.badRequests.unableToFindEntity(id);
                }
            }

            return ret.getTenantOrg().getURI();
        } catch (DatabaseException ex) {
            throw SecurityException.fatals.failedGettingTenant(ex);
        }
    }

    /**
     * Get tenant id from a project id retrieved from uri
     * 
     * @param childId
     * @return
     */
    public URI getTenantResourceTenantId(String childId) {
        if (childId == null) {
            return null;
        }

        try {
            URI id = URI.create(childId);
            TenantResource ret = null;
            if (URIUtil.isType(id, Host.class)) {
                ret = getObjectById(id, Host.class);
            } else if (URIUtil.isType(id, VcenterDataCenter.class)) {
                ret = getObjectById(id, VcenterDataCenter.class);
            } else if (URIUtil.isType(id, Cluster.class)) {
                ret = getObjectById(id, Cluster.class);
            } else if (URIUtil.isType(id, Initiator.class)) {
                Initiator ini = getObjectById(id, Initiator.class);
                if (ini.getHost() != null) {
                    ret = getObjectById(ini.getHost(), Host.class);
                }
            } else if (URIUtil.isType(id, IpInterface.class)) {
                IpInterface hostIf = getObjectById(id, IpInterface.class);
                if (hostIf.getHost() != null) {
                    ret = getObjectById(hostIf.getHost(), Host.class);
                }
            } else {
                throw APIException.badRequests.theURIIsNotOfType(id,
                        TenantResource.class.getName());
            }

            if (ret == null) {
                throw FatalDatabaseException.fatals.unableToFindEntity(id);
            }

            return ret.getTenant();
        } catch (DatabaseException ex) {
            throw SecurityException.fatals.failedGettingTenant(ex);
        }
    }

    /**
     * get the set of tenant roles assigned to a user
     * 
     * @param user StorageOSUser representing the logged in user
     * @param tenantId URI of the tenant, if null, user's tenant is used if one exists
     * @return unmodifiable instance of Set<StorageOSUser.TenantRole>
     */
    public Set<String> getTenantRolesForUser(StorageOSUser user, URI tenantId,
            boolean idEmbeddedInURL) {
        if (tenantId == null) {
            tenantId = URI.create(user.getTenantId());
        }
        if (tenantId == null) {
            return Collections.emptySet();
        }
        Set<String> tenantRoles = new HashSet<String>();
        TenantOrg tenant = getObjectById(tenantId, TenantOrg.class);
        if (tenant == null) {
            if (idEmbeddedInURL) {
                throw APIException.notFound.unableToFindEntityInURL(tenantId);
            } else {
                throw APIException.badRequests.unableToFindTenant(tenantId);
            }
        }

        // The three scenarios that allow us to look up roles in this tenant:
        // 1 user tenant is the same tenant as the one we're after for role lookups,
        // 2 or user tenant is root tenant (parent of all)
        // 3 or user tenant is parent of the tenant we are after (technically same as 2 today since
        // there is only one level of subtenancy but in the future this may change)
        // If all are false, return no role.
        URI userTenantId = URI.create(user.getTenantId());
        TenantOrg userTenant = getObjectById(userTenantId, TenantOrg.class);
        if (!tenantId.equals(userTenantId) &&
                !TenantOrg.isRootTenant(userTenant) &&
                !tenant.getParentTenant().getURI().equals(userTenantId)) {
            return Collections.emptySet();
        }

        // for upn
        Set<String> userRoles = tenant.getRoleSet(
                new PermissionsKey(PermissionsKey.Type.SID, user.getName()).toString());
        if (userRoles != null) {
            for (String role : userRoles) {
                if (isRoleTenantLevel(role)) {
                    tenantRoles.add(role);
                }
            }
        }

        // from groups
        Set<String> groups = user.getGroups();
        if (!CollectionUtils.isEmpty(groups)) {
            for (String group : groups) {
                // add if any roles for the groups, from root tenant/zone roles
                Set<String> roleSet = tenant.getRoleSet(
                        new PermissionsKey(PermissionsKey.Type.GROUP, group).toString());
                if (null != roleSet) {
                    for (String role : roleSet) {
                        if (isRoleTenantLevel(role)) {
                            tenantRoles.add(role);
                        }
                    }
                }
            }
        }

        // Now based on userGroup role assignments.
        updateUserTenantRolesBasedOnUserGroup(user, tenant, tenantRoles);

        return Collections.unmodifiableSet(tenantRoles);
    }

    /**
     * get the set of project ACLs assigned to a user
     * 
     * @param user StorageOSUser representing the logged in user
     * @param projectId URI of the project
     * @return unmodifiable instance of Set<String>
     */
    public Set<String> getProjectACLsForUser(StorageOSUser user, URI projectId,
            boolean idEmbeddedInURL) {
        if (projectId == null) {
            return Collections.emptySet();
        }
        Set<String> projectACLs = new HashSet<String>();
        Project project = getObjectById(projectId, Project.class);
        if (project == null) {
            if (idEmbeddedInURL) {
                throw APIException.notFound.unableToFindEntityInURL(projectId);
            } else {
                throw APIException.badRequests.unableToFindEntity(projectId);
            }
        }
        // for upn
        Set<String> acls = project.getAclSet(
                new PermissionsKey(PermissionsKey.Type.SID, user.getName(),
                        user.getTenantId()).toString());
        if (acls != null) {
            for (String acl : acls) {
                if (isProjectACL(acl)) {
                    projectACLs.add(acl);
                }
            }
        }
        // from groups
        Set<String> groups = user.getGroups();
        if (!CollectionUtils.isEmpty(groups)) {
            for (String group : groups) {
                // add if any roles for the groups, from root tenant/zone roles
                acls = project.getAclSet(
                        new PermissionsKey(PermissionsKey.Type.GROUP, group,
                                user.getTenantId()).toString());
                if (null != acls) {
                    for (String acl : acls) {
                        if (isProjectACL(acl)) {
                            projectACLs.add(acl);
                        }
                    }
                }
            }
        }

        // Now based on userGroup acl assignments.
        updateUserProjectAclBasedOnUserGroup(user, project, projectACLs);

        return Collections.unmodifiableSet(projectACLs);
    }

    /**
     * Returns true if the user has any role from the given list, false otherwise
     * 
     * @param user
     * @param tenantId tenant id
     * @param roles
     * @return
     */
    public boolean userHasGivenRole(StorageOSUser user, URI tenantId, Role... roles) {
        if (_disabler != null) {
            return true;
        }
        Set<String> tenantRoles = null;
        for (Role role : roles) {
            if (user.getRoles().contains(role.toString())) {
                return true;
            } else if (isRoleTenantLevel(role.toString())) {
                if (tenantRoles == null) {
                    tenantRoles = getTenantRolesForUser(user, tenantId, false);
                }
                if (tenantRoles.contains(role.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the user has any role from the given list in their home tenant or any subtenant. false otherwise.
     * 
     * @param user StorageOSUser representing the logged in user
     * @param roles Roles to test against
     * @return whether or not the user has any of the given roles
     */
    public boolean userHasGivenRoleInAnyTenant(StorageOSUser user, Role... roles) {
        if (userHasGivenRole(user, URI.create(user.getTenantId()), roles)) {
            return true;
        }

        Map<String, Collection<String>> allSubtenantRoles = null;
        for (Role role : roles) {
            if (isRoleTenantLevel(role.toString())) {
                if (allSubtenantRoles == null) {
                    // don't initialize allSubtenantRoles unless we need to.
                    // API can't return null, so this will only run once.
                    allSubtenantRoles = getSubtenantRolesForUser(user);
                }
                for (Collection<String> subtenantRoles : allSubtenantRoles.values()) {
                    if (subtenantRoles.contains(role.toString())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the user has any acl from the given list, false otherwise
     * 
     * @param user
     * @param projectId Project uri to check acls on
     * @param acls
     * @return
     */
    public boolean userHasGivenACL(StorageOSUser user, URI projectId, ACL... acls) {
        if (_disabler != null) {
            return true;
        }
        Set<String> projectAcls = getProjectACLsForUser(user, projectId, false);
        for (ACL acl : acls) {
            if (acl.equals(ACL.ANY) && !projectAcls.isEmpty()) {
                return true;
            }
            if (projectAcls.contains(acl.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the user's tenant has a usage acl on the VirtualPool
     * 
     * @param tenantUri
     * @param virtualPool
     * @return
     */
    public boolean tenantHasUsageACL(URI tenantUri, VirtualPool virtualPool) {
        if (_disabler != null) {
            return true;
        }
        // Make CoS open to all by default, restriction kicks in once a acl assignment is done
        if (CollectionUtils.isEmpty(virtualPool.getAcls())) {
            return true;
        }
        Set<String> acls = virtualPool.getAclSet(new PermissionsKey(PermissionsKey.Type.TENANT,
                tenantUri.toString(), virtualPool.getType()).toString());
        if (acls != null && acls.contains(ACL.USE.toString())) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if any tenant in the list has a usage acl on the VirtualPool
     *
     * @param tenantUris
     * @param virtualPool
     * @return
     */
    public boolean tenantHasUsageACL(List<URI> tenantUris, VirtualPool virtualPool) {
        for (URI tenantUri : tenantUris) {
            if (tenantHasUsageACL(tenantUri, virtualPool)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the user's tenant has a usage acl on the ComputeVirtualPool
     * 
     * @param tenantUri
     * @param computeVirtualPool
     * @return
     */
    public boolean tenantHasUsageACL(URI tenantUri, ComputeVirtualPool computeVirtualPool) {
        if (_disabler != null) {
            return true;
        }
        // Make CoS open to all by default, restriction kicks in once a acl assignment is done
        if (CollectionUtils.isEmpty(computeVirtualPool.getAcls())) {
            return true;
        }

        Set<String> acls = computeVirtualPool.getAclSet(new PermissionsKey(PermissionsKey.Type.TENANT,
                tenantUri.toString(), computeVirtualPool.getSystemType()).toString());

        if (acls != null && acls.contains(ACL.USE.toString())) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if the user's tenant has a usage acl on the VirtualArray
     * 
     * @param tenantUri
     * @param virtualArray
     * @return
     */
    public boolean tenantHasUsageACL(URI tenantUri, VirtualArray virtualArray) {
        if (_disabler != null) {
            return true;
        }
        // Make Neighborhood open to all by default, restriction kicks in once a acl assignment is done
        if (CollectionUtils.isEmpty(virtualArray.getAcls())) {
            return true;
        }
        Set<String> acls = virtualArray.getAclSet(new PermissionsKey(PermissionsKey.Type.TENANT,
                tenantUri.toString()).toString());
        if (acls != null && acls.contains(ACL.USE.toString())) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if any tenant in the list has a usage acl on the VirtualArray
     *
     * @param tenantUris
     * @param virtualArray
     * @return
     */
    public boolean tenantHasUsageACL(List<URI> tenantUris, VirtualArray virtualArray) {
        for (URI tenantUri : tenantUris) {
            if (tenantHasUsageACL(tenantUri, virtualArray)) {
                return true;
            }
        }
        return false;
    }

    /**
     * return a list of tenant URI the specified user has tenant role over it.
     *
     * @param user
     * @return
     */
    public List<URI> getSubtenantsWithRoles(StorageOSUser user) {
        Map<String, Collection<String>> subTenantRoles = getSubtenantRolesForUser(user);
        List<URI> subtenants = new ArrayList<URI>();
        for (String subtenant : subTenantRoles.keySet()) {
            subtenants.add(URI.create(subtenant));
        }

        return subtenants;
    }

    /**
     * Check if the string is zone level role
     * 
     * @param role
     * @return true if the string is zone level role, false otherwise
     */
    public boolean isRoleZoneLevel(String role) {
        return (role.equalsIgnoreCase(Role.SYSTEM_ADMIN.toString()) ||
                role.equalsIgnoreCase(Role.SECURITY_ADMIN.toString()) ||
                role.equalsIgnoreCase(Role.SYSTEM_MONITOR.toString()) ||
                role.equalsIgnoreCase(Role.SYSTEM_AUDITOR.toString()) ||
                role.equalsIgnoreCase(Role.RESTRICTED_SECURITY_ADMIN.toString()) || role.equalsIgnoreCase(Role.RESTRICTED_SYSTEM_ADMIN
                .toString()));
    }

    /**
     * Check if the string is external zone level role
     * 
     * @param role
     * @return true if the string is zone level role, false otherwise
     */
    public boolean isExternalRoleZoneLevel(String role) {
        return (role.equalsIgnoreCase(Role.SYSTEM_ADMIN.toString()) ||
                role.equalsIgnoreCase(Role.SECURITY_ADMIN.toString()) ||
                role.equalsIgnoreCase(Role.SYSTEM_MONITOR.toString()) || role.equalsIgnoreCase(Role.SYSTEM_AUDITOR.toString()));
    }

    /**
     * Check if the string is a tenant level role
     * 
     * @param role
     * @return true if string is tenant level role, false otherwise
     */
    public boolean isRoleTenantLevel(String role) {
        return (role.equalsIgnoreCase(Role.TENANT_ADMIN.toString()) ||
                role.equalsIgnoreCase(Role.PROJECT_ADMIN.toString()) || role.equalsIgnoreCase(Role.TENANT_APPROVER.toString()));
    }

    /**
     * Check if the string is a valid project acl string
     * 
     * @param acl
     * @return true if a valid project acl, false otherwise
     */
    public boolean isProjectACL(String acl) {
        return (acl.equalsIgnoreCase(ACL.OWN.toString()) ||
                acl.equalsIgnoreCase(ACL.ALL.toString()) || acl.equalsIgnoreCase(ACL.BACKUP.toString()));
    }

    /**
     * Check if the string is a usage acl string
     * 
     * @param acl
     * @return true if a its usage acl, false otherwise
     */
    public static boolean isUsageACL(String acl) {
        return (acl.equalsIgnoreCase(ACL.USE.toString()));
    }

    /**
     * Get all tenants with roles for the given user
     * 
     * @param user
     * @param filterBy if not null, set of roles that the resulting columns will be filtered by
     * @param onTenant if true, queries for tenant columns, otherwise, project columns
     * @return
     */
    public Map<URI, Set<String>> getAllPermissionsForUser(StorageOSUser user,
            URI tenantId, final Set<String> filterBy, boolean onTenant) {
        try {
            final Map<URI, Set<String>> permissionsMap = new HashMap<URI, Set<String>>();
            QueryResultList<URI> results = new QueryResultList<URI>() {
                @Override
                public URI createQueryHit(URI uri) {
                    // none
                    return uri;
                }

                @Override
                public URI createQueryHit(URI uri, String permission, UUID timestamp) {
                    if (filterBy == null ||
                            filterBy.contains(permission)) {
                        if (!permissionsMap.containsKey(uri)) {
                            permissionsMap.put(uri, new HashSet<String>());
                        }
                        permissionsMap.get(uri).add(permission);
                    }
                    return uri;
                }

                @Override
                public URI createQueryHit(URI uri, Object entry) {
                    return createQueryHit(uri);
                }
            };
            // To do - fix up ContainmentPermissionsConstraint to query multiple row keys at a time.
            if (onTenant) {
                _dbClient.queryByConstraint(
                        ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(
                                new PermissionsKey(PermissionsKey.Type.SID, user.getName()).toString()), results);
                for (Iterator<URI> iterator = results.iterator(); iterator.hasNext(); iterator.next()) {
                    ;
                }
                if (user.getGroups() != null) {
                    for (String group : user.getGroups()) {
                        _dbClient.queryByConstraint(
                                ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(
                                        new PermissionsKey(PermissionsKey.Type.GROUP, group).toString()), results);
                        for (Iterator<URI> iterator = results.iterator(); iterator.hasNext(); iterator.next()) {
                            ;
                        }
                    }
                }
                // Now get the user's permissions based on the userGroup
                getUserPermissionsForTenantBasedOnUserGroup(user, filterBy, permissionsMap);
            } else {/* project */
                _dbClient.queryByConstraint(
                        ContainmentPermissionsConstraint.Factory.getObjsWithPermissionsConstraint(
                                new PermissionsKey(PermissionsKey.Type.SID, user.getName(), tenantId).toString(),
                                Project.class), results);
                for (Iterator<URI> iterator = results.iterator(); iterator.hasNext(); iterator.next()) {
                    ;
                }
                if (user.getGroups() != null) {
                    for (String group : user.getGroups()) {
                        _dbClient.queryByConstraint(
                                ContainmentPermissionsConstraint.Factory.getObjsWithPermissionsConstraint(
                                        new PermissionsKey(PermissionsKey.Type.GROUP, group, tenantId).toString(),
                                        Project.class), results);
                        for (Iterator<URI> iterator = results.iterator(); iterator.hasNext(); iterator.next()) {
                            ;
                        }
                    }
                }
                // Now get the user's permissions based on the userGroup
                getUserPermissionsForProjectBasedOnUserGroup(user, filterBy, permissionsMap);
            }
            return permissionsMap;
        } catch (DatabaseException ex) {
            _log.error("permissions index query failed", ex);
        }
        throw SecurityException.fatals.permissionsIndexQueryFailed();
    }

    /**
     * Given a domain get a map of tenant IDs to a list of user mappings with the domain
     * 
     * @param domain domain to search for
     * @return a map of tenant ID to mappings
     */
    public Map<URI, List<UserMapping>> getAllUserMappingsForDomain(String domain) {
        try {
            final Map<URI, List<UserMapping>> tenantUserMappingMap = new HashMap<URI, List<UserMapping>>();
            QueryResultList<URI> results = new QueryResultList<URI>() {
                @Override
                public URI createQueryHit(URI uri) {
                    // none
                    return uri;
                }

                @Override
                public URI createQueryHit(URI uri, String userMapping, UUID timestamp) {
                    if (!tenantUserMappingMap.containsKey(uri)) {
                        tenantUserMappingMap.put(uri, new ArrayList<UserMapping>());
                    }
                    tenantUserMappingMap.get(uri).add(UserMapping.fromString(userMapping));
                    return uri;
                }

                @Override
                public URI createQueryHit(URI uri, Object entry) {
                    return createQueryHit(uri);
                }
            };
            _dbClient.queryByConstraint(ContainmentPermissionsConstraint.Factory.getUserMappingsWithDomain(domain), results);
            for (Iterator<URI> iterator = results.iterator(); iterator.hasNext(); iterator.next()) {
                ;
            }

            return tenantUserMappingMap;
        } catch (DatabaseException ex) {
            _log.error("tenant user mapping query failed", ex);
        }
        throw SecurityException.fatals.tenantUserMappingQueryFailed();
    }

    public Map<String, Collection<String>> getSubtenantRolesForUser(StorageOSUser user) {
        Map<String, Collection<String>> subTenantRoles = new HashMap<String, Collection<String>>();
        URI userHomeTenant = URI.create(user.getTenantId());

        NamedElementQueryResultList subtenants = new NamedElementQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getTenantOrgSubTenantConstraint(userHomeTenant), subtenants);
        for (NamedElement sub : subtenants) {
            Collection<String> roles = getTenantRolesForUser(user, sub.getId(), false);
            if (!CollectionUtils.isEmpty(roles)) {
                subTenantRoles.put(sub.getId().toString(), roles);
            }
        }

        return subTenantRoles;
    }

    public void removeRootRoleAssignmentOnTenantAndProject() throws DatabaseException {
        String keyForRoot = new PermissionsKey(PermissionsKey.Type.SID, ROOT).toString();
        StringBuffer tenantRolesRemoved = new StringBuffer("Tenant roles removed: ");
        StringBuffer projectOwnerRemoved = new StringBuffer("Project owner removed: ");

        List<URI> uriQueryResultList = _dbClient.queryByType(TenantOrg.class, true);
        Iterator<TenantOrg> tenantOrgIterator = _dbClient.queryIterativeObjects(
                TenantOrg.class, uriQueryResultList);
        while (tenantOrgIterator.hasNext()) {
            boolean bNeedPersistent = false;
            TenantOrg tenantOrg = tenantOrgIterator.next();
            Set<String> rootRoles = tenantOrg.getRoleSet(keyForRoot);
            if (!CollectionUtils.isEmpty(rootRoles)) {
                for (String role : rootRoles) {
                    _log.info("removing root's " + role + " from Tenant: " + tenantOrg.getLabel());
                    tenantOrg.removeRole(keyForRoot, role);
                    bNeedPersistent = true;
                }
            }

            if (bNeedPersistent) {
                _dbClient.updateAndReindexObject(tenantOrg);
                tenantRolesRemoved.append(tenantOrg.getLabel()).append(" ");
            }
        }

        uriQueryResultList = _dbClient.queryByType(Project.class, true);
        Iterator<Project> projectIterator = _dbClient.queryIterativeObjects(
                Project.class, uriQueryResultList);
        while (projectIterator.hasNext()) {
            Project project = projectIterator.next();
            if (project.getOwner().equalsIgnoreCase(ROOT)) {
                _log.info("removing root's ownership from project: " + project.getLabel());
                project.setOwner("");
                _dbClient.updateAndReindexObject(project);
                projectOwnerRemoved.append(project.getLabel()).append(" ");
            }
        }
        _log.info(tenantRolesRemoved.toString());
        _log.info(projectOwnerRemoved.toString());
    }

    /***
     * Gets all the user group that has the domain matching with
     * the param domain.
     * 
     * @param domain to be matched.
     * @return list of active UserGroup.
     */
    public List<UserGroup> getAllUserGroupForDomain(String domain) {
        List<UserGroup> userGroupList = null;
        try {
            userGroupList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, UserGroup.class,
                    PrefixConstraint.Factory.getFullMatchConstraint(UserGroup.class, "domain", domain));
        } catch (DatabaseException ex) {
            _log.error("User group query failed", ex);
        }
        return userGroupList;
    }

    /***
     * Gets all the user group that has the label matching with
     * the param label.
     * 
     * @param label to be matched.
     * @return list of active UserGroup.
     */
    public List<UserGroup> getAllUserGroupByLabel(String label) {
        List<UserGroup> userGroupList = null;
        try {
            userGroupList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, UserGroup.class,
                    PrefixConstraint.Factory.getFullMatchConstraint(UserGroup.class, "label", label));
        } catch (DatabaseException ex) {
            _log.error("User group query failed", ex);
        }
        return userGroupList;
    }

    /***
     * Get all the configured user group from the given
     * role assignments or acls.
     * 
     * @param roleAssignments to used to find the user group based in its keyset.
     * @return a map of user group and its corresponding roles.
     */
    public Map<UserGroup, StringSet> getUserGroupsFromRoleAssignments(StringSetMap roleAssignments) {
        Map<UserGroup, StringSet> userGroupsWithRoles = null;

        if (CollectionUtils.isEmpty(roleAssignments)) {
            _log.warn("Invalid or Empty role-assignments");
            return userGroupsWithRoles;
        }

        userGroupsWithRoles = new HashMap<UserGroup, StringSet>();

        Set<String> keys = roleAssignments.keySet();
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                _log.debug("Invalid entry in the role-assignments");
                continue;
            }

            PermissionsKey permissionsKey = new PermissionsKey();
            permissionsKey.parseFromString(key);

            List<UserGroup> userGroupListList = getAllUserGroupByLabel(permissionsKey.getValue());
            if (CollectionUtils.isEmpty(userGroupListList)) {
                _log.debug("Could not find any user group with label {}", permissionsKey.getValue());
                continue;
            }

            if (userGroupListList.size() > 1) {
                _log.warn("Found more than one user group with label {} in DB. " +
                        "Using the first object in the returned list", permissionsKey.getValue());
            }

            StringSet roleSet = roleAssignments.get(key);
            _log.debug("Adding user group {} with roles", userGroupListList.get(0).getLabel(), roleSet.toString());

            userGroupsWithRoles.put(userGroupListList.get(0), roleSet);
        }

        return userGroupsWithRoles;
    }

    /***
     * Compare the user group with the list of user's attributes.
     * 
     * @param user who's attributes list to be compared with user group.
     * @param roleAssignmentUserGroup to be compared with the user's attributes list.
     * @return true if user's attributes contains all the all attributes and its values of
     *         user group otherwise false.
     */
    public boolean matchUserAttributesToUserGroup(StorageOSUserDAO user,
            UserGroup roleAssignmentUserGroup) {
        boolean isUserGroupMatchesUserAttributes = false;
        if (roleAssignmentUserGroup == null ||
                user == null) {
            _log.error("Invalid user {} or user group {}", user, roleAssignmentUserGroup);
            return isUserGroupMatchesUserAttributes;
        }

        Set<String> userGroupDoNotMatch = new HashSet<String>();
        for (String roleAssignmentUserAttributeString : roleAssignmentUserGroup.getAttributes()) {
            if (StringUtils.isBlank(roleAssignmentUserAttributeString)) {
                _log.debug("Invalid user attributes param string");
                continue;
            }

            boolean isUserGroupMatchesUserAttribute = false;

            UserAttributeParam roleAssignmentUserAttribute = UserAttributeParam.fromString(roleAssignmentUserAttributeString);
            if (roleAssignmentUserAttribute == null) {
                _log.warn("Failed to convert user attributes param string {} to object.", roleAssignmentUserAttributeString);
                continue;
            }

            for (String userAttributeString : user.getAttributes()) {
                UserAttributeParam userAttribute = UserAttributeParam.fromString(userAttributeString);
                if (userAttribute == null) {
                    _log.info("Failed to convert user attributes param string {} to object.", userAttributeString);
                    continue;
                }

                _log.debug("Comparing user attributes {} with user group attribute {}",
                        userAttributeString, roleAssignmentUserAttributeString);

                if (userAttribute.containsAllAttributeValues(roleAssignmentUserAttribute)) {
                    _log.debug("Found user attributes {} matching with user group attributes {}",
                            userAttributeString, roleAssignmentUserAttributeString);
                    isUserGroupMatchesUserAttribute = true;
                    break;
                }
            }

            if (isUserGroupMatchesUserAttribute == false) {
                _log.debug("Adding user group {} to the do not match list", roleAssignmentUserAttributeString);
                userGroupDoNotMatch.add(roleAssignmentUserAttributeString);
            }
        }

        if (CollectionUtils.isEmpty(userGroupDoNotMatch)) {
            isUserGroupMatchesUserAttributes = true;
        }
        return isUserGroupMatchesUserAttributes;
    }

    /***
     * Compares all the AD/LDAP attributes of user with configured user group
     * to find if the user's attaches matches with any of the user attributes and
     * get the role associated with that user group to add the user or
     * tenant.
     * 
     * @param user attributes of the user to be compared with the user group.
     * @param userGroupsWithRoles a map contains all the user group
     *            and the all the roles associated with that groups.
     * @return returns all the roles of the user group that matches with the
     *         user's attributes.
     */
    private StringSet findAllRolesToAdd(StorageOSUser user, Map<UserGroup, StringSet> userGroupsWithRoles) {
        StringSet rolesToAdd = null;

        if (CollectionUtils.isEmpty(userGroupsWithRoles)) {
            _log.error("Invalid user group and roles.");
            return rolesToAdd;
        }

        rolesToAdd = new StringSet();

        for (Map.Entry<UserGroup, StringSet> userGroupEntry : userGroupsWithRoles.entrySet()) {
            if (CollectionUtils.isEmpty(userGroupEntry.getValue())) {
                continue;
            }

            if (matchUserAttributesToUserGroup(user, userGroupEntry.getKey())) {
                rolesToAdd.addAll(userGroupEntry.getValue());
                ;
            }
        }
        return rolesToAdd;
    }

    /***
     * Add the vdc roles to the user based on the user group in the
     * vdc role-assignments.
     * 
     * @param user who's roles to be updated based on the user group in the
     *            vdc role-assignments.
     * @param vdc to get its role-assignments.
     */
    private void updateUserVdcRolesBasedOnUserGroup(StorageOSUser user, VirtualDataCenter vdc) {
        if (user == null || vdc == null) {
            _log.error("Invalid user {} or vdc {}", user, vdc);
            return;
        }

        StringSetMap roleAssignments = vdc.getRoleAssignments();
        Map<UserGroup, StringSet> userGroupsWithRoles = getUserGroupsFromRoleAssignments(roleAssignments);
        if (CollectionUtils.isEmpty(userGroupsWithRoles)) {
            _log.info("There are no role assignments for VDC {} with user group", vdc.getLabel());
            return;
        }

        StringSet roleSet = findAllRolesToAdd(user, userGroupsWithRoles);
        if (CollectionUtils.isEmpty(roleSet)) {
            _log.debug("There are no roles found for user group in the vdc {}", vdc.getLabel());
            return;
        }

        for (String role : roleSet) {
            if (isRoleZoneLevel(role)) {
                _log.debug("Adding the vdc role {} to the user {}", role, user.getDistinguishedName());
                user.addRole(role);
            }
        }
    }

    /***
     * Update the user's tenants roles based on the tenant's role-assignments.
     * 
     * @param user who's role to be found based the attributes and tenant's role-assignments.
     * @param tenant to get its role-assignments.
     * @param tenantRoles out param, to be updated all the user's roles for this tenant.
     */
    private void updateUserTenantRolesBasedOnUserGroup(StorageOSUser user, TenantOrg tenant, Set<String> tenantRoles) {
        if (user == null || tenant == null) {
            _log.error("Invalid user {} or tenant {}", user, tenant);
            return;
        }

        StringSetMap roleAssignments = tenant.getRoleAssignments();
        Map<UserGroup, StringSet> userGroupsWithRoles = getUserGroupsFromRoleAssignments(roleAssignments);

        if (CollectionUtils.isEmpty(userGroupsWithRoles)) {
            _log.debug("There are no role assignments for tenant {} with user group", tenant.getLabel());
            return;
        }

        StringSet roleSet = findAllRolesToAdd(user, userGroupsWithRoles);
        if (CollectionUtils.isEmpty(roleSet)) {
            _log.debug("There are no roles found for user group in the tenant {}", tenant.getLabel());
            return;
        }

        for (String role : roleSet) {
            if (isRoleTenantLevel(role)) {
                _log.debug("Adding the tenant role {} to the user {}", role, user.getDistinguishedName());
                tenantRoles.add(role);
            }
        }
    }

    /***
     * Update the user's project roles based on the project's acls.
     * 
     * @param user who's roles to be found based the attributes and project's acls.
     * @param project to get its acls.
     * @param projectAcls out param, to be updated all the user's roles for this project.
     */
    private void updateUserProjectAclBasedOnUserGroup(StorageOSUser user, Project project, Set<String> projectAcls) {
        if (user == null || project == null) {
            _log.error("Invalid user or project", user, project);
            return;
        }

        StringSetMap roleAssignments = project.getAcls();
        Map<UserGroup, StringSet> userGroupsWithRoles = getUserGroupsFromRoleAssignments(roleAssignments);

        if (CollectionUtils.isEmpty(userGroupsWithRoles)) {
            _log.debug("There are no role assignments for project {} with user group", project.getLabel());
            return;
        }

        StringSet roleSet = findAllRolesToAdd(user, userGroupsWithRoles);
        if (CollectionUtils.isEmpty(roleSet)) {
            _log.debug("There are no roles found for user group in the project {}", project.getLabel());
            return;
        }

        for (String role : roleSet) {
            if (isProjectACL(role)) {
                _log.debug("Adding the project acl {} to the user {}", role, user.getDistinguishedName());
                projectAcls.add(role);
            }
        }
    }

    /***
     * Add all the allowed permissions to the user.
     * 
     * @param filterBy if not null, set of roles that the resulting columns will be filtered by
     * @param id tenant's id
     * @param roles roles to be checked.
     * @param permissionsMap out param, to updated with list of permissions.
     */
    private void addUserPermissions(Set<String> filterBy, URI id, Set<String> roles, Map<URI, Set<String>> permissionsMap) {
        if (CollectionUtils.isEmpty(roles)) {
            _log.error("Invalid roles set");
            return;
        }

        if (permissionsMap == null) {
            permissionsMap = new HashMap<URI, Set<String>>();
        }

        for (String role : roles) {
            if (StringUtils.isBlank(role)) {
                continue;
            }

            if (filterBy == null || filterBy.contains(role)) {
                if (!permissionsMap.containsKey(id)) {
                    permissionsMap.put(id, new HashSet<String>());
                }

                _log.debug("Adding the role {} to the resource {}", role, id);
                permissionsMap.get(id).add(role);
            }
        }
    }

    /***
     * Update the user's permissions for the tenant based on the user group.
     * 
     * @param user who's permissions to be updated.
     * @param filterBy if not null, set of roles that the resulting columns will be filtered by
     * @param permissionsMap out param, to be updated with list of permissions.
     */
    private void
            getUserPermissionsForTenantBasedOnUserGroup(StorageOSUser user, Set<String> filterBy, Map<URI, Set<String>> permissionsMap) {
        if (user == null || CollectionUtils.isEmpty(user.getAttributes())) {
            _log.error("Invalid user or user attributes");
            return;
        }

        TenantOrg userTenant = (TenantOrg) _dbClient.queryObject(URI.create(user.getTenantId()));
        if (userTenant == null) {
            _log.error("Could not find user's {} tenant {}", user.getDistinguishedName(), user.getTenantId());
            return;
        }

        Set<String> tenantRoles = new HashSet<String>();
        updateUserTenantRolesBasedOnUserGroup(user, userTenant, tenantRoles);
        if (!CollectionUtils.isEmpty(tenantRoles)) {
            addUserPermissions(filterBy, userTenant.getId(), tenantRoles, permissionsMap);
        }
    }

    /***
     * Update the user's permissions for the project based on the user group.
     * 
     * @param user who's permissions to be updated.
     * @param filterBy if not null, set of roles that the resulting columns will be filtered by
     * @param permissionsMap out param, to be updated with list of permissions.
     */
    private void
            getUserPermissionsForProjectBasedOnUserGroup(StorageOSUser user, Set<String> filterBy, Map<URI, Set<String>> permissionsMap) {
        if (user == null || CollectionUtils.isEmpty(user.getAttributes())) {
            _log.error("Invalid user or user attributes");
            return;
        }

        List<URI> projectsURIList = _dbClient.queryByType(Project.class, true);
        if (projectsURIList == null || !projectsURIList.iterator().hasNext()) {
            _log.warn("There are no projects configured.");
            return;
        }

        List<Project> projects = _dbClient.queryObject(Project.class, projectsURIList);
        if (CollectionUtils.isEmpty(projects)) {
            _log.error("Could not find the project objects for the Ids {}", projectsURIList.toString());
            return;
        }

        for (Project project : projects) {
            if (project == null) {
                _log.debug("Invalid project");
                continue;
            }

            Set<String> projectACLs = new HashSet<String>();
            updateUserProjectAclBasedOnUserGroup(user, project, projectACLs);
            if (!CollectionUtils.isEmpty(projectACLs)) {
                addUserPermissions(filterBy, project.getId(), projectACLs, permissionsMap);
            }
        }
    }

    /***
     * Check if any active user mapping for the domain that uses the user group.
     * 
     * @param domain to find all the active user mappings for the domain.
     * @param label to check if the user mapping uses this user group or not.
     * @return Set of URI of the tenant that has the user mappings references to the
     *         user group.
     */
    public Set<URI> checkForActiveUserMappingUsingGroup(String domain, String label) {
        Set<URI> tenantsUsingUserGroup = null;

        Map<URI, List<UserMapping>> mappings = getAllUserMappingsForDomain(domain);
        if (CollectionUtils.isEmpty(mappings)) {
            _log.debug("Could not find any user mappings from domain {}", domain);
            return tenantsUsingUserGroup;
        }

        tenantsUsingUserGroup = new HashSet<URI>();

        for (Map.Entry<URI, List<BasePermissionsHelper.UserMapping>> entry : mappings.entrySet()) {
            if (CollectionUtils.isEmpty(entry.getValue())) {
                continue;
            }

            for (UserMapping userMapping : entry.getValue()) {
                if (userMapping == null || CollectionUtils.isEmpty(userMapping.getGroups())) {
                    continue;
                }

                for (String group : userMapping.getGroups()) {
                    if (StringUtils.isNotBlank(group) && group.equalsIgnoreCase(label)) {
                        tenantsUsingUserGroup.add(entry.getKey());
                    }
                }
            }
        }

        return tenantsUsingUserGroup;
    }

    /***
     * Checks whether the given the user group is present in the given
     * acls or role-assignment set.
     * 
     * @param label to check if the acls or role-assignments uses user group or not.
     * @param acls set or acls or role-assignments to be checked.
     * @return true if the acls or role-assignments contains the user group
     *         otherwise false.
     */
    private boolean checkUserGroupWithPermissionKeys(String label, Set<String> acls) {
        boolean resourceUsingUserGroup = false;
        if (CollectionUtils.isEmpty(acls)) {
            _log.warn("Invalid acls");
            return resourceUsingUserGroup;
        }

        for (String acl : acls) {
            if (StringUtils.isBlank(acl)) {
                _log.debug("Invalid acl entry");
                continue;
            }

            PermissionsKey permissionsKey = new PermissionsKey();
            permissionsKey.parseFromString(acl);

            if (permissionsKey.getType() == PermissionsKey.Type.GROUP &&
                    permissionsKey.getValue().equalsIgnoreCase(label)) {
                resourceUsingUserGroup = true;
            }
        }

        return resourceUsingUserGroup;
    }

    /***
     * Checks whether the given the user group is present in any of the
     * project's acls or not
     * 
     * @param label to check if any project acls uses user group or not.
     * @return set of URI's Projects that uses the user group.
     */
    public Set<URI> checkForActiveProjectAclsUsingUserGroup(String label) {
        Set<URI> projectsUsingUserGroup = null;

        // Find all the configured project IDs based on the type.
        List<URI> projectURIList = _dbClient.queryByType(Project.class, true);
        if (projectURIList == null || !projectURIList.iterator().hasNext()) {
            _log.warn("There are no projects configured.");
            return projectsUsingUserGroup;
        }

        // Find all the configured project objects based on the given list of IDs.
        List<Project> projects = _dbClient.queryObject(Project.class, projectURIList);
        if (CollectionUtils.isEmpty(projects)) {
            _log.error("Could not find the project objects for the Ids {}", projectURIList.toString());
            return projectsUsingUserGroup;
        }

        projectsUsingUserGroup = new HashSet<URI>();

        for (Project project : projects) {
            if (project == null) {
                _log.debug("Invalid project");
                continue;
            }

            if (CollectionUtils.isEmpty(project.getAcls())) {
                _log.debug("ACLs are not configured for project {}", project.getLabel());
                continue;
            }

            Set<String> aclKeys = project.getAcls().keySet();
            if (checkUserGroupWithPermissionKeys(label, aclKeys)) {
                projectsUsingUserGroup.add(project.getId());
            }
        }

        return projectsUsingUserGroup;
    }

    /***
     * Checks whether the given the user group is present in the local vdc's
     * role-assignment or not
     * 
     * @param label to check if any vdc role-assignments uses user group or not.
     * @return set of URI's VDCs that uses the user group.
     */
    public Set<URI> checkForActiveVDCRoleAssignmentsUsingUserGroup(String label) {
        Set<URI> vdcUsingUserGroup = null;

        VirtualDataCenter vdc = VdcUtil.getLocalVdc();
        if (vdc == null) {
            _log.error("Could not find local VDC");
            return vdcUsingUserGroup;
        }

        if (CollectionUtils.isEmpty(vdc.getRoleAssignments())) {
            _log.debug("Role assignments are not configured for vdc {}", vdc.getLabel());
            return vdcUsingUserGroup;
        }

        vdcUsingUserGroup = new HashSet<URI>();

        Set<String> roleAssignmentKeys = vdc.getRoleAssignments().keySet();
        if (checkUserGroupWithPermissionKeys(label, roleAssignmentKeys)) {
            vdcUsingUserGroup.add(vdc.getId());
        }

        return vdcUsingUserGroup;
    }

    /***
     * Checks whether the given the user group is present the any of the
     * tenants role-assignments or not
     * 
     * @param label to check if any tenants role-assignments uses user group or not.
     * @return set of URI's tenants that uses the user group.
     */
    public Set<URI> checkForActiveTenantRoleAssignmentsUsingUserGroup(String label) {
        Set<URI> tenantsUsingUserGroup = null;

        // Find all the configured tenant IDs based on the type.
        List<URI> tenantURIList = _dbClient.queryByType(TenantOrg.class, true);
        if (tenantURIList == null || !tenantURIList.iterator().hasNext()) {
            _log.error("There are no tenants configured.");
            return tenantsUsingUserGroup;
        }

        // Find all the configured tenant objects based on the given list of IDs.
        List<TenantOrg> tenants = _dbClient.queryObject(TenantOrg.class, tenantURIList);
        if (CollectionUtils.isEmpty(tenants)) {
            _log.error("Could not find the tenant objects for the Ids {}", tenantURIList.toString());
            return tenantsUsingUserGroup;
        }

        tenantsUsingUserGroup = new HashSet<URI>();

        for (TenantOrg tenant : tenants) {
            if (tenant == null) {
                _log.debug("Invalid tenant");
                continue;
            }

            if (CollectionUtils.isEmpty(tenant.getRoleAssignments())) {
                _log.debug("Role assignments are not configured for tenant {}", tenant.getLabel());
                continue;
            }

            Set<String> roleAssignmentKeys = tenant.getRoleAssignments().keySet();
            if (checkUserGroupWithPermissionKeys(label, roleAssignmentKeys)) {
                tenantsUsingUserGroup.add(tenant.getId());
            }
        }

        return tenantsUsingUserGroup;
    }

    /***
     * Checks whether the given the user group is present the any of the
     * catalog category acls or not
     * 
     * @param label to check if any tenants role-assignments uses user group or not.
     * @return set of URI's catalog category that uses the user group.
     */
    public Set<URI> checkForActiveCatalogCategoryAclsUsingUserGroup(String label) {
        Set<URI> catalogCategoryUsingUserGroup = null;

        // Find all the configured Catalog category IDs based on the type.
        List<URI> catalogCategoryURIList = _dbClient.queryByType(CatalogCategory.class, true);
        if (catalogCategoryURIList == null || !catalogCategoryURIList.iterator().hasNext()) {
            _log.warn("There are no catalog category configured.");
            return catalogCategoryUsingUserGroup;
        }

        // Find all the configured Catalog category objects based on the given list of IDs.
        List<CatalogCategory> catalogCategories = _dbClient.queryObject(CatalogCategory.class, catalogCategoryURIList);
        if (CollectionUtils.isEmpty(catalogCategories)) {
            _log.error("Could not find the Catalog category objects for the Ids {}", catalogCategoryURIList.toString());
            return catalogCategoryUsingUserGroup;
        }

        catalogCategoryUsingUserGroup = new HashSet<URI>();

        for (CatalogCategory catalogCategory : catalogCategories) {
            if (catalogCategory == null) {
                _log.info("Invalid catalog category");
                continue;
            }

            if (CollectionUtils.isEmpty(catalogCategory.getAcls())) {
                _log.debug("ACLs not configured for Catalog category {}", catalogCategory.getLabel());
                continue;
            }

            Set<String> aclKeys = catalogCategory.getAcls().keySet();
            if (checkUserGroupWithPermissionKeys(label, aclKeys)) {
                catalogCategoryUsingUserGroup.add(catalogCategory.getId());
            }
        }

        return catalogCategoryUsingUserGroup;
    }

    /***
     * Checks whether the given the user group is present the any of the
     * catalog service acls or not
     * 
     * @param label to check if any tenants role-assignments uses user group or not.
     * @return set of URI's catalog services that uses the user group.
     */
    public Set<URI> checkForActiveCatalogServiceAclsUsingUserGroup(String label) {
        Set<URI> catalogServiceUsingUserGroup = null;

        // Find all the configured Catalog service IDs based on the type.
        List<URI> catalogServiceURIList = _dbClient.queryByType(CatalogService.class, true);
        if (catalogServiceURIList == null || !catalogServiceURIList.iterator().hasNext()) {
            _log.warn("There are no catalog service configured.");
            return catalogServiceUsingUserGroup;
        }

        // Find all the configured Catalog service objects based on the given list of IDs.
        List<CatalogService> catalogServices = _dbClient.queryObject(CatalogService.class, catalogServiceURIList);
        if (CollectionUtils.isEmpty(catalogServices)) {
            _log.error("Could not find the Catalog service objects for the Ids {}", catalogServiceURIList.toString());
            return catalogServiceUsingUserGroup;
        }

        catalogServiceUsingUserGroup = new HashSet<URI>();

        for (CatalogService catalogService : catalogServices) {
            if (catalogService == null) {
                _log.info("Invalid catalog service");
                continue;
            }

            if (CollectionUtils.isEmpty(catalogService.getAcls())) {
                _log.debug("ACLs not configured for Catalog service {}", catalogService.getLabel());
                continue;
            }

            Set<String> aclKeys = catalogService.getAcls().keySet();
            if (checkUserGroupWithPermissionKeys(label, aclKeys)) {
                catalogServiceUsingUserGroup.add(catalogService.getId());
            }
        }

        return catalogServiceUsingUserGroup;
    }

    /**
     * Returns true if the user has any acl from the given list, false otherwise
     * 
     * @param user
     * @param acls
     * @return
     */
    public boolean userHasGivenProjectACL(StorageOSUser user, ACL... acls) {
        List<URI> projectIds = _dbClient.queryByType(Project.class, true);

        if (projectIds == null || !projectIds.iterator().hasNext()) {
            _log.warn("There are no projects configured.");
            return false;
        }

        for (URI projectId : projectIds) {
            if (userHasGivenACL(user, projectId, acls)) {
                return true;
            } else {
                continue;
            }
        }

        return false;
    }

    /**
     * given tenant id, find its name
     */
    public String getTenantNameByID(String tenantID) {
        if (StringUtils.isEmpty(tenantID)) {
            return null;
        }

        TenantOrg tenantOrg = _dbClient.queryObject(TenantOrg.class, URI.create(tenantID));
        return tenantOrg != null ? tenantOrg.getLabel() : null;
    }

    /**
     * Converts StringSetMap of acls into a list of ACLEntry as used by the API
     *
     * @param acls to be converted into the ACLEntry list.
     * @return the converted ACLEntry list.
     */
    public static List<ACLEntry> convertToACLEntries(StringSetMap acls) {
        List<ACLEntry> assignments = new ArrayList<ACLEntry>();
        if (CollectionUtils.isEmpty(acls)) {
            return assignments;
        }

        for (Map.Entry<String, AbstractChangeTrackingSet<String>> ace : acls.entrySet()) {
            PermissionsKey rowKey = new PermissionsKey();
            rowKey.parseFromString(ace.getKey());
            ACLEntry entry = new ACLEntry();
            if (rowKey.getType().equals(PermissionsKey.Type.GROUP)) {
                entry.setGroup(rowKey.getValue());
            } else if (rowKey.getType().equals(PermissionsKey.Type.SID)) {
                entry.setSubjectId(rowKey.getValue());
            } else if (rowKey.getType().equals(PermissionsKey.Type.TENANT)) {
                entry.setTenant(rowKey.getValue());
            }
            for (String priv : ace.getValue()) {
                // skip owner
                if (priv.equalsIgnoreCase(ACL.OWN.toString())) {
                    continue;
                }
                entry.getAces().add(priv);
            }
            if (!entry.getAces().isEmpty()) {
                assignments.add(entry);
            }
        }

        return assignments;
    }

    /**
     * Gets the USE URIs from the acl string set map. It converts
     * the acls string set map to the list of ACLEntry and then
     * fetches the URI list from the ACLEntry list.
     *
     * @param acls to be used to fetch the usage URIs.
     * @return a set of URIs retrived from the acls.
     */
    public static Set<URI> getUsageURIsFromAcls(StringSetMap acls) {
        Set<URI> tenantUris = new HashSet<URI>();
        if (CollectionUtils.isEmpty(acls)) {
            return tenantUris;
        }

        List<ACLEntry> aclEntries = convertToACLEntries(acls);
        tenantUris = getUsageURIsFromAclEntries(aclEntries);
        return tenantUris;
    }

    /**
     * Gets the USE URIs from the list of ACLEntry.
     *
     * @param aclEntries to be used to fetch the usage URIs.
     * @return a set of URIs retrived from the acls.
     */
    public static Set<URI> getUsageURIsFromAclEntries(List<ACLEntry> aclEntries) {
        Set<URI> tenantUris = new HashSet<URI>();
        if (CollectionUtils.isEmpty(aclEntries)) {
            return tenantUris;
        }

        Iterator<ACLEntry> aclEntryIt = aclEntries.iterator();
        while (aclEntryIt.hasNext()) {
            ACLEntry aclEntry = aclEntryIt.next();
            if (!CollectionUtils.isEmpty(aclEntry.getAces())) {
                tenantUris.add(URI.create(aclEntry.getTenant()));
            }
        }

        return tenantUris;
    }

    /**
     * Get the USE permission key for the provided tenant.
     *
     * @param tenantId to which a permission key to be created.
     * @return a string format of a permission key.
     */
    public static String getTenantUsePermissionKey(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw APIException.badRequests.invalidEntryACLEntryMissingTenant();
        }

        PermissionsKey key = new PermissionsKey(PermissionsKey.Type.TENANT, tenantId);
        return key.toString();
    }

    /**
     * Get the vCenter tenant based on its acls. Basically
     * if there is only one acl entry configured for the vCenter
     * it returns the tenant of that acl entry and if there are
     * more than one acl entry configured for the system, it returns
     * null uri.
     *
     * @param acls to be used to find out the tenant of the resource.
     * @return the URI of the resource if the resource contains only
     *          one acl entry otherwise null URI.
     */
    public static URI getTenant(StringSetMap acls) {
        Set<URI> usageUris = getUsageURIsFromAcls(acls);
        if (CollectionUtils.isEmpty(usageUris) ||
                usageUris.size() != 1) {
            return NullColumnValueGetter.getNullURI();
        }
        return usageUris.iterator().next();
    }

    /**
     * Get the USE ACLEntry for the tenant.
     *
     * @param tenantId to be used in the USE ACLEntry.
     * @return the ACLEntry.
     */
    public ACLEntry getUseAclEntry(String tenantId) {
        ACLEntry aclEntry = new ACLEntry();
        aclEntry.setTenant(tenantId);
        aclEntry.getAces().add(ACL.USE.name());

        return aclEntry;
    }

    /**
     * Get tenant id from a project id retrieved from uri
     *
     * @param childId
     * @return
     */
    public Set<URI> getTenantResourceTenantIds(String childId) {
        if (childId == null) {
            return null;
        }

        try {
            URI id = URI.create(childId);
            Vcenter ret = null;
            if (URIUtil.isType(id, Vcenter.class)) {
                ret = getObjectById(id, Vcenter.class);
            }
            return getUsageURIsFromAcls(ret.getAcls());
        } catch (DatabaseException ex) {
            throw SecurityException.fatals.failedGettingTenant(ex);
        }
    }

    /**
     * Gets the user mappings of the all the domains.
     *
     * @param domains to get all the user mappings.
     * @return returns the map of tenantID to user mappings
     * of the domains.
     */
    public Map<URI, List<UserMapping>> getAllUserMappingsForDomain(StringSet domains) {
        Map<URI, List<UserMapping>> tenantUserMappingMap = new HashMap<URI, List<UserMapping>>();
        if (CollectionUtils.isEmpty(domains)) {
            return tenantUserMappingMap;
        }

        Iterator<String> domainsIterator = domains.iterator();
        while (domainsIterator.hasNext()) {
            Map<URI, List<UserMapping>> singleTenantUserMappingMap = getAllUserMappingsForDomain(domainsIterator.next());
            if (!CollectionUtils.isEmpty(singleTenantUserMappingMap)) {
                tenantUserMappingMap.putAll(singleTenantUserMappingMap);
            }
        }

        return tenantUserMappingMap;
    }
}
