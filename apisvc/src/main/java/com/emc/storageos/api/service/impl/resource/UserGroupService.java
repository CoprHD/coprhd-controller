/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.functions.MapUserGroup;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.usergroup.*;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.*;

import static com.emc.storageos.api.mapper.UserGroupMapper.map;

/**
 * API for creating and manipulating user groups.
 * So, that these groups can be used as groups in role-assignments,
 * acl-assignments and also in tenant userMappings.
 */

@Path("/vdc/admin/user-groups")
@DefaultPermissions(readRoles = { Role.SECURITY_ADMIN },
        writeRoles = { Role.SECURITY_ADMIN })
public class UserGroupService extends TaggedResource {
    private static String EXPECTED_GEO_VERSION = "2.3";
    private static String FEATURE_NAME = "Attributes Based Role and ACL Assignments";

    private static final Logger _log = LoggerFactory.getLogger(UserGroupService.class);

    private static final String EVENT_SERVICE_TYPE = "usergroupconfig";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected DataObject queryResource(URI id) {
        return getUserGroupById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<UserGroup> getResourceClass() {
        return UserGroup.class;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.USER_GROUP;
    }

    /**
     * @param id the URN of a ViPR UserGroup to get details from
     * @param checkInactive If true, make sure that provider is not inactive
     * @return UserGroup object for the given id
     */
    private UserGroup getUserGroupById(URI id, boolean checkInactive) {
        if (id == null) {
            _log.debug("User Group ID is NULL");
            return null;
        }
        _log.debug("User Group ID is {}", id.toString());
        UserGroup userGroup = _permissionsHelper.getObjectById(id, UserGroup.class);
        ArgValidator.checkEntity(userGroup, id, isIdEmbeddedInURL(id), checkInactive);

        return userGroup;
    }

    /**
     * Creates user group.
     * The submitted user group element values will be validated.
     * <p>
     * The minimal set of parameters include: name, domain, attributes (key and values pair).
     * <p>
     * 
     * @param param Representation of UserGroup with all necessary elements
     * 
     * @brief Creates an User Group
     * @return Newly created User Group details as UserGroupRestRep
     * @see UserGroupCreateParam
     * @see UserGroupRestRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public UserGroupRestRep createUserGroup(UserGroupCreateParam param) {
        checkCompatibleVersion();
        validateUserGroupCreateParam(param);

        // Check for active UserGroup with same name
        checkDuplicateLabel(UserGroup.class, param.getLabel());

        UserGroup userGroup = map(param);
        URI id = URIUtil.createId(UserGroup.class);
        userGroup.setId(id);
        _log.debug("Saving the UserGroup: {}: {}", userGroup.getId(), userGroup.toString());

        // Check if there is any existing user group with same set of properties.
        checkForOverlappingUserGroup(userGroup);

        _dbClient.createObject(userGroup);

        auditOp(OperationTypeEnum.CREATE_USERGROUP, true, null,
                userGroup.toString(), userGroup.getId().toString());

        return map(getUserGroupById(id, false));
    }

    /**
     * Updates user group.
     * The submitted user group element values will be validated.
     * <p>
     * The minimal set of parameters include: name, domain, attributes (key and values pair).
     * <p>
     * 
     * @param param Representation of UserGroup with all necessary elements
     * 
     * @brief Updates an User Group
     * @return The updated User Group details as UserGroupRestRep
     * @see UserGroupUpdateParam
     * @see UserGroupRestRep
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public UserGroupRestRep updateUserGroup(@PathParam("id") URI id, UserGroupUpdateParam param) {
        checkCompatibleVersion();
        UserGroup userGroup = getUserGroupById(id, false);
        ArgValidator.checkEntityNotNull(userGroup, id, isIdEmbeddedInURL(id));

        validateUserGroupUpdateParam(param);

        // Update the db object with new information.
        overlayUserGroup(userGroup, param);

        // Check if there is any existing user group with same set of properties.
        checkForOverlappingUserGroup(userGroup);

        _dbClient.persistObject(userGroup);

        auditOp(OperationTypeEnum.UPDATE_USERGROUP, true, null,
                userGroup.toString(), userGroup.getId().toString());

        return map(getUserGroupById(id, false));
    }

    /**
     * Gets the user group list (of URNs).
     * 
     * @brief All the active user group's URN will be returned.
     * @return All the user groups details as UserGroupList
     * @see UserGroupList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public UserGroupList getUserGroupIds() {
        checkCompatibleVersion();
        checkIfUserHasPermissions();

        NamedElementQueryResultList userGroups = new NamedElementQueryResultList();
        List<URI> uris = _dbClient.queryByType(UserGroup.class, true);
        List<UserGroup> configs = _dbClient.queryObject(UserGroup.class, uris);

        List<NamedElementQueryResultList.NamedElement> elements =
                new ArrayList<NamedElementQueryResultList.NamedElement>(configs.size());
        for (UserGroup p : configs) {
            elements.add(NamedElementQueryResultList.NamedElement.createElement(p.getId(), p.getLabel()));
        }
        userGroups.setResult(elements.iterator());

        UserGroupList list = new UserGroupList();
        list.getUserGroups().addAll(DbObjectMapper.map(ResourceTypeEnum.USER_GROUP, userGroups));

        return list;
    }

    /**
     * Gets the details of one user group.
     * 
     * @param id of the user group to be returned.
     * @brief The details of the active user group is returned.
     * @return The user groups details as UserGroupRestRep
     * @see UserGroupRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public UserGroupRestRep getUserGroup(@PathParam("id") URI id) {
        checkCompatibleVersion();
        checkIfUserHasPermissions();

        UserGroup userGroup = getUserGroupById(id, false);
        ArgValidator.checkEntityNotNull(userGroup, id, isIdEmbeddedInURL(id));

        return map(userGroup);
    }

    /**
     * Deletes the active user group.
     * 
     * @param id of the user group to be deleted.
     * @brief The user group that matches the id will be deactivated.
     * @return Ok if deletion is successful otherwise valid exception.
     */
    @DELETE
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public Response deleteUserGroup(@PathParam("id") URI id) {
        checkCompatibleVersion();
        UserGroup userGroup = getUserGroupById(id, false);
        ArgValidator.checkEntityNotNull(userGroup, id, isIdEmbeddedInURL(id));

        // check that there are no active resources that uses this user group.
        checkForActiveUsageOfUserGroup(userGroup.getDomain(), userGroup.getLabel());

        _dbClient.removeObject(userGroup);

        auditOp(OperationTypeEnum.DELETE_USERGROUP, true, null,
                userGroup.getId().toString());

        return Response.ok().build();
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of user group resources
     * @return list of representations.
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public UserGroupBulkRestRep getBulkResources(BulkIdParam param) {
        return (UserGroupBulkRestRep) super.getBulkResources(param);
    }

    /**
     * Validates if all the user attributes param contains the valid set of
     * keys and values.
     * 
     * @param attributeParams
     */
    private void validateUserAttributeParam(Set<UserAttributeParam> attributeParams) {
        for (UserAttributeParam userAttributeParam : attributeParams) {
            if (userAttributeParam != null) {
                ArgValidator.checkFieldNotNull(userAttributeParam.getKey(), "key");
                ArgValidator.checkFieldNotEmpty(userAttributeParam.getValues(), "values");
            }
        }
    }

    /**
     * Validates if UserGroupCreateParam of the create api
     * contains all the valid payload that is expected.
     * 
     * @param param of user group create api payload to be validated.
     */
    private void validateUserGroupCreateParam(UserGroupCreateParam param) {
        validateUserGroupBaseParam(param);

        ArgValidator.checkFieldNotEmpty(param.getAttributes(), "attributes");

        // Make sure all the UserAttributeParam contains valid attribute key and values.
        validateUserAttributeParam(param.getAttributes());
    }

    /**
     * Validates if UserGroupUpdateParam of the update api
     * contains all the valid payload that is expected.
     * 
     * @param param of user group update api to be validated.
     */
    private void validateUserGroupUpdateParam(UserGroupUpdateParam param) {
        validateUserGroupBaseParam(param);

        // Make sure all the UserAttributeParam contains valid attribute key and values.
        validateUserAttributeParam(param.getAddAttributes());
    }

    /**
     * Validates if UserGroupBaseParam of the create/update api
     * contains all the valid payload that is expected.
     * 
     * @param param of user group create/update api to be validated.
     */
    private void validateUserGroupBaseParam(UserGroupBaseParam param) {
        if (param == null) {
            throw APIException.badRequests.resourceEmptyConfiguration("user group");
        }

        _log.debug("Validate user group param: {}", param);

        ArgValidator.checkFieldNotNull(param.getDomain(), "domain");
        ArgValidator.checkFieldNotNull(param.getLabel(), "label");

        if (param.getLabel().contains("@")) {
            throw APIException.badRequests.invalidParameter("label", param.getLabel());
        }

        if (!authNProviderExistsForDomain(param.getDomain())) {
            throw APIException.badRequests.invalidParameter("domain", param.getDomain());
        }
    }

    /**
     * Overlay the UserGroup (a db object) with the information
     * for the update api payload.
     * 
     * @param to user group db object to be updated.
     * @param from user group update api payload to be
     *            updated in the user group db object.
     */
    private void overlayUserGroup(UserGroup to,
            UserGroupUpdateParam from) {
        if (from == null || to == null) {
            throw APIException.badRequests.resourceEmptyConfiguration("user group");
        }

        if (!to.getLabel().equalsIgnoreCase(from.getLabel())) {
            throw APIException.badRequests.cannotRenameUserGroup(to.getLabel());
        }

        if (!to.getDomain().equalsIgnoreCase(from.getDomain())) {
            checkForActiveUsageOfUserGroup(to.getDomain(), to.getLabel());
        }

        to.setDomain(from.getDomain());
        to.setLabel(from.getLabel());

        Map<String, UserAttributeParam> userAttributeParamMap = getUserAttributesToMap(to.getAttributes());
        Map<String, UserAttributeParam> FromUserAttributeParamMap = getUserAttributesToMap(from.getAddAttributes());

        if (!CollectionUtils.isEmpty(FromUserAttributeParamMap)) {
            for (Map.Entry<String, UserAttributeParam> addAttribute : FromUserAttributeParamMap.entrySet()) {
                addToMapIfNotExist(userAttributeParamMap, addAttribute);
            }
        }

        if (!CollectionUtils.isEmpty(from.getRemoveAttributes())) {
            for (String removeAttribute : from.getRemoveAttributes()) {
                userAttributeParamMap.remove(removeAttribute);
            }
        }

        if (CollectionUtils.isEmpty(userAttributeParamMap)) {
            ArgValidator.checkFieldNotEmpty(userAttributeParamMap,
                    "Attempt to remove the last attribute is not allowed.  At least one attribute must be in the user group.");
        }

        StringSet attributesToAdd = new StringSet();
        for (UserAttributeParam userAttributeParam : userAttributeParamMap.values()) {
            attributesToAdd.add(userAttributeParam.toString());
        }

        to.getAttributes().replace(attributesToAdd);
    }

    /***
     * Add the user attribute param to map if it is not already exist in the map.
     * 
     * @param userAttributeParamMap
     * @param addAttribute
     */
    private void addToMapIfNotExist(Map<String, UserAttributeParam> userAttributeParamMap,
            Map.Entry<String, UserAttributeParam> addAttribute) {
        if (CollectionUtils.isEmpty(userAttributeParamMap)) {
            _log.info("Invalid map to add the entries");
            return;
        }

        UserAttributeParam userAttributeParam = userAttributeParamMap.get(addAttribute.getKey());
        if (userAttributeParam == null) {
            userAttributeParamMap.put(addAttribute.getKey(), addAttribute.getValue());
        } else {
            userAttributeParam.setValues(addAttribute.getValue().getValues());
        }
    }

    /**
     * Creates a map with key as attribute key and user attribute
     * param as value. So, that each key (attribute) will have only one set
     * of values.
     * 
     * @param attributes to be converted to map.
     * @return returns the map<String, UserAttributeParam> with key
     *         as attribute key.
     */
    private Map<String, UserAttributeParam> getUserAttributesToMap(StringSet attributes) {
        Map<String, UserAttributeParam> userAttributeParamMap = new TreeMap<String, UserAttributeParam>(String.CASE_INSENSITIVE_ORDER);

        if (CollectionUtils.isEmpty(attributes)) {
            _log.warn("Invalid attributes set");
            return userAttributeParamMap;
        }

        for (String userAttributeParamString : attributes) {
            if (StringUtils.isBlank(userAttributeParamString)) {
                _log.debug("Invalid user attributes param string {} in user group", userAttributeParamString);
                continue;
            }

            UserAttributeParam userAttributeParam = UserAttributeParam.fromString(userAttributeParamString);
            if (userAttributeParam == null) {
                _log.info("Failed to convert user attributes param string {} to object.", userAttributeParamString);
                continue;
            }

            UserAttributeParam userAttributeParamFromMap = userAttributeParamMap.get(userAttributeParam.getKey());
            if (userAttributeParamFromMap == null) {
                userAttributeParamMap.put(userAttributeParam.getKey(), userAttributeParam);
            } else {
                userAttributeParamFromMap.getValues().addAll(userAttributeParam.getValues());
            }
        }

        return userAttributeParamMap;
    }

    /**
     * Creates a map with key as attribute key and user attribute
     * param as value. So, that each key (attribute) will have only one set
     * of values.
     * 
     * @param userAttributeParams to be converted to map.
     * @return returns the map<String, UserAttributeParam> with key
     *         as attribute key.
     */
    private Map<String, UserAttributeParam> getUserAttributesToMap(Set<UserAttributeParam> userAttributeParams) {
        StringSet userAttributesStringSet = new StringSet();
        if (!CollectionUtils.isEmpty(userAttributeParams)) {
            for (UserAttributeParam param : userAttributeParams) {
                if (param != null) {
                    userAttributesStringSet.add(param.toString());
                }
            }
        }

        Map<String, UserAttributeParam> userAttributeParamMap = getUserAttributesToMap(userAttributesStringSet);

        return userAttributeParamMap;
    }

    /**
     * Check if the user group object presented with the label
     * and domain is actively used by any other resources (Tenants userMapping,
     * VDC role-assignments, tenants role-assignments, projects acls, catalog acls).
     * 
     * @param domain for which this user group is configured.
     * @param label name that represents the user group in the db.
     *            This name is what used in all above said resources to represent it.
     */
    void checkForActiveUsageOfUserGroup(String domain, String label) {
        // Check if VDC rol-assignment references the user group.
        Set<URI> resourcesUsingUserGroup = new HashSet<URI>();
        Set<URI> vdcURI = _permissionsHelper.checkForActiveVDCRoleAssignmentsUsingUserGroup(label);
        if (!CollectionUtils.isEmpty(vdcURI)) {
            resourcesUsingUserGroup.addAll(vdcURI);
        }

        // Check if tenants role-assignments references the user group.
        Set<URI> tenantsURI = _permissionsHelper.checkForActiveTenantRoleAssignmentsUsingUserGroup(label);
        if (!CollectionUtils.isEmpty(tenantsURI)) {
            resourcesUsingUserGroup.addAll(tenantsURI);
        }

        // Check if tenants user mapping for the domain references the user group.
        Set<URI> userMappingsURI = _permissionsHelper.checkForActiveUserMappingUsingGroup(domain, label);
        if (!CollectionUtils.isEmpty(userMappingsURI)) {
            resourcesUsingUserGroup.addAll(userMappingsURI);
        }

        // Check if project acls references the user group.
        Set<URI> projectsURI = _permissionsHelper.checkForActiveProjectAclsUsingUserGroup(label);
        if (!CollectionUtils.isEmpty(projectsURI)) {
            resourcesUsingUserGroup.addAll(projectsURI);
        }

        // Check if catalog categories acls references the user group.
        Set<URI> catalogCategoriesURI = _permissionsHelper.checkForActiveCatalogCategoryAclsUsingUserGroup(label);
        if (!CollectionUtils.isEmpty(catalogCategoriesURI)) {
            resourcesUsingUserGroup.addAll(catalogCategoriesURI);
        }

        // Check if catalog services acls references the user group.
        Set<URI> catalogServicesURI = _permissionsHelper.checkForActiveCatalogServiceAclsUsingUserGroup(label);
        if (!CollectionUtils.isEmpty(catalogServicesURI)) {
            resourcesUsingUserGroup.addAll(catalogServicesURI);
        }

        if (!CollectionUtils.isEmpty(resourcesUsingUserGroup)) {
            throw APIException.badRequests.cannotDeleteOrEditUserGroup(resourcesUsingUserGroup.size(), resourcesUsingUserGroup);
        }
    }

    /**
     * Check if a provider exists for the given domain
     * 
     * @param domain
     * @return
     */
    private boolean authNProviderExistsForDomain(String domain) {
        URIQueryResultList providers = new URIQueryResultList();
        try {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getAuthnProviderDomainConstraint(domain), providers);
        } catch (DatabaseException ex) {
            _log.error(
                    "Could not query for authn providers to check for existing domain {}",
                    domain, ex.getStackTrace());
            throw ex;
        }

        // check if there is an AuthnProvider contains the given domain and not in disabled state
        boolean bExist = false;
        Iterator<URI> it = providers.iterator();
        while (it.hasNext()) {
            URI providerURI = it.next();
            AuthnProvider provider = _dbClient.queryObject(AuthnProvider.class, providerURI);
            if (provider != null && provider.getDisable() == false) {
                bExist = true;
                break;
            }
        }

        return bExist;
    }

    /**
     * Check if the user contains any the these roles.
     * Roles required : Tenant.Admin, Security.Admin, Project.OWN.
     * 
     */
    private void checkIfUserHasPermissions() {
        StorageOSUser user = getUserFromContext();
        if ((!_permissionsHelper.userHasGivenRoleInAnyTenant(user, Role.SECURITY_ADMIN, Role.TENANT_ADMIN)) &&
                (!_permissionsHelper.userHasGivenProjectACL(user, ACL.OWN))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
    }

    /**
     * Check if all the VDCs in the federation are in the same expected
     * or minimum supported version for this api.
     * 
     */
    private void checkCompatibleVersion() {
        if (!_dbClient.checkGeoCompatible(EXPECTED_GEO_VERSION)) {
            throw APIException.badRequests.incompatibleGeoVersions(EXPECTED_GEO_VERSION, FEATURE_NAME);
        }
    }

    /**
     * Returns the minimum expected version for this API to the
     * consumers of the apisvc (portal).
     * 
     * @return minimum expected geo version for this api.
     */
    public static String getExpectedGeoVDCVersion() {
        return EXPECTED_GEO_VERSION;
    }

    private void checkForOverlappingUserGroup(UserGroup userGroup) {
        if (userGroup == null) {
            _log.error("Invalid user group to compare");
            return;
        }

        List<UserGroup> userGroupList =
                _permissionsHelper.getAllUserGroupForDomain(userGroup.getDomain());

        if (CollectionUtils.isEmpty(userGroupList)) {
            _log.debug("No user group found for the domain {}", userGroup.getDomain());
            return;
        }

        Set<String> overlappingGroups = new HashSet<String>();
        for (UserGroup existingUserGroup : userGroupList) {
            if (existingUserGroup == null) {
                _log.info("Invalid user group found in db");
                continue;
            }

            if ((!userGroup.getLabel().equalsIgnoreCase(existingUserGroup.getLabel())) &&
                    userGroup.overlap(existingUserGroup)) {
                overlappingGroups.add(existingUserGroup.getLabel());
            }

            if ((!userGroup.getLabel().equalsIgnoreCase(existingUserGroup.getLabel())) &&
                    existingUserGroup.overlap(userGroup)) {
                overlappingGroups.add(existingUserGroup.getLabel());
            }
        }

        if (!CollectionUtils.isEmpty(overlappingGroups)) {
            throw APIException.badRequests.overlappingAttributesNotAllowed(userGroup.getLabel(),
                    overlappingGroups);
        }
    }

    @Override
    public UserGroupBulkRestRep queryBulkResourceReps(List<URI> ids) {

        Iterator<UserGroup> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);

        return new UserGroupBulkRestRep(BulkList.wrapping(_dbIterator,
                MapUserGroup.getInstance()));
    }

    @Override
    protected UserGroupBulkRestRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<UserGroup> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);

        BulkList.ResourceFilter filter =
                new BulkList.UserGroupFilter(getUserFromContext(), _permissionsHelper);

        return new UserGroupBulkRestRep(BulkList.wrapping(_dbIterator,
                MapUserGroup.getInstance(), filter));
    }
}
