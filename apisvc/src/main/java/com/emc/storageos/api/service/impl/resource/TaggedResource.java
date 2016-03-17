/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.FilterIterator;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TagAssignment;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.model.search.Tags;
import com.emc.storageos.security.authentication.RequestProcessingUtils;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ExcludeLicenseCheck;
import com.emc.storageos.security.authorization.InheritCheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Base class for all resources with
 * 1. support for /<base path>/{id}/...
 * 2. support for tagging
 */
public abstract class TaggedResource extends ResourceService {
    private static Logger _log = LoggerFactory.getLogger(TaggedResource.class);

    private static final int DEFAULT_MAX_BULK_SIZE = 4000;
    private int _maxBulkSize = DEFAULT_MAX_BULK_SIZE;

    /**
     * Derived class can set the max bulk size based on its resource rep type.
     */
    public void setMaxBulkSize(int maxBulkSize) {
        _maxBulkSize = maxBulkSize;
    }

    public int getMaxBulkSize() {
        return _maxBulkSize;
    }

    /**
     * @brief Assign tags to resource
     *        Assign tags
     * 
     * @prereq none
     * 
     * @param id the URN of a ViPR resource
     * @param assignment tag assignments
     * @return No data returned in response body
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/tags")
    @InheritCheckPermission(writeAccess = true)
    public Tags assignTags(@PathParam("id") URI id, TagAssignment assignment) {
        DataObject object = queryResource(id);
        ArgValidator.checkEntityNotNull(object, id, isIdEmbeddedInURL(id));
        ScopedLabelSet tagSet = object.getTag();
        if (tagSet == null) {
            tagSet = new ScopedLabelSet();
            object.setTag(tagSet);
        }
        if (assignment.getAdd() != null && !assignment.getAdd().isEmpty()) {
            Iterator<String> it = assignment.getAdd().iterator();
            while (it.hasNext()) {
                String tagName = it.next();
                if (tagName == null || tagName.isEmpty() || tagName.length() < 2) {
                    throw APIException.badRequests.parameterTooShortOrEmpty("Tag", 2);
                }
                ScopedLabel tagLabel = new ScopedLabel(getTenantOwnerIdString(id), tagName);
                tagSet.add(tagLabel);
            }
        }
        if (assignment.getRemove() != null && !assignment.getRemove().isEmpty()) {
            Iterator<String> it = assignment.getRemove().iterator();
            while (it.hasNext()) {
                String tagName = it.next();
                if (tagName == null || tagName.isEmpty()) {
                    continue;
                }
                ScopedLabel tagLabel = new ScopedLabel(getTenantOwnerIdString(id), tagName);
                if (tagSet.contains(tagLabel)) {
                    tagSet.remove(tagLabel);
                }
            }
        }
        _dbClient.updateAndReindexObject(object);
        return getTagsResponse(object);
    }

    /**
     * @brief List tags assigned to resource
     *        Returns assigned tags
     * 
     * @prereq none
     * 
     * @param id the URN of a ViPR Resource
     * @return Tags information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/tags")
    @InheritCheckPermission
    public Tags getTags(@PathParam("id") URI id) {
        DataObject object = queryResource(id);
        ArgValidator.checkEntityNotNull(object, id, isIdEmbeddedInURL(id));
        return getTagsResponse(object);
    }

    private Tags getTagsResponse(DataObject object) {
        Tags tags = new Tags();
        if (object.getTag() != null) {
            for (ScopedLabel label : object.getTag()) {
                tags.getTag().add(label.getLabel());
            }
        }
        return tags;
    }

    private String getTenantOwnerIdString(URI id) {
        URI tenantOwner = getTenantOwner(id);
        if (tenantOwner == null) {
            return null;
        } else {
            return tenantOwner.toString();
        }
    }

    /**
     * Actual Services provide resource query implementation
     */
    protected abstract DataObject queryResource(URI id);

    /**
     * 
     * return the actual class object of this resource
     * 
     * The base class throws unsupported exception.
     * Derived resource class which supports bulk retrieving should override this method.
     */
    protected <T extends DataObject> Class<T> getResourceClass() {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * Tenant this object belongs to. For non tenant specific objects (like storage system), it returns null
     * 
     * @return
     */
    protected abstract URI getTenantOwner(URI id);

    // The following 8 methods are used by the Search API.
    // Every derived class needs to override them appropriately

    protected abstract ResourceTypeEnum getResourceType();

    /**
     * To detect zone level resources -- default is true
     */
    protected boolean isZoneLevelResource() {
        return true;
    }

    /**
     * Non-zone level resource, but visible to system admin -- default false
     * 
     * @return
     */
    protected boolean isSysAdminReadableResource() {
        return false;
    }

    /**
     * Get search results by name in zone (default) or in a specific project.
     * 
     * @return SearchedResRepList
     */
    protected SearchedResRepList getNamedSearchResults(String name, URI projectId) {
        SearchedResRepList resRepList = null;
        if (projectId == null) {
            resRepList = new SearchedResRepList(getResourceType());
            _dbClient.queryByConstraint(
                    PrefixConstraint.Factory.getLabelPrefixConstraint(getResourceClass(), name),
                    resRepList);
        } else {
            throw APIException.badRequests.parameterNotSupportedFor(
                    "project-level search",
                    MessageFormat.format("{0} search", getResourceClass().getName()));
        }
        return resRepList;
    }

    /**
     * Get search results by tag in zone (default) or in a specific tenant level
     * 
     * @return SearchedResRepList
     */
    protected SearchedResRepList getTagSearchResults(String tag, URI tenant) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        _dbClient.queryByConstraint(
                PrefixConstraint.Factory.getTagsPrefixConstraint(getResourceClass(), tag, tenant),
                resRepList);
        return resRepList;
    }

    /**
     * Get search results by project alone.
     * By default this fails
     * 
     * @return SearchedResRepList
     */
    protected SearchedResRepList getProjectSearchResults(URI projectId) {
        throw APIException.badRequests.parameterNotSupportedFor("project",
                MessageFormat.format("{0} search", getResourceClass().getName()));
    }

    /**
     * Get object specific search results by parameters other than name and tag.
     * Default is not implemented error
     * 
     * @return SearchResults
     */
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters,
            boolean authorized) {
        throw APIException.badRequests.unknownParameter("search", parameters.toString());
    }

    /**
     * Get object specific permissions filter, if applicable
     * Default is null
     * 
     * @return ResRepFilter<? extends RelatedResourceRep>
     */
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(
            StorageOSUser user, PermissionsHelper permissionsHelper)
    {
        if (!isZoneLevelResource()) {
            throw new UnsupportedOperationException("non-zone level resource needs to implement its specific permission filter");
        }

        throw APIException.forbidden
                .insufficientPermissionWhileSearchingZoneLevelResource(getUserFromContext()
                        .toString());
    }

    /**
     * @brief search API
     *        Search resources by name, tag, project or additional parameters (for example, wwn or initiator_port etc.)
     * 
     * @prereq none
     * @return search results
     */

    /*
     * Parameters:
     * Common Parameters:
     * name: Name has to be a minimum of 2 characters. Could only be combined with project parameter
     * tag: Tag has to be a minimum of 2 characters. Could only be combined with tenant parameter
     * project: The full project URI needs to be provided.
     * 
     * NOTE: Name and tag are not case sensitive.
     * For Zone level resources, search by project is not allowed.
     * Special Parameters:
     * wwn: only used by block service
     * initiator_port: only used by virtual array service.
     */
    @GET
    @Path("/search")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SearchResults search() {
        // 1. Figure out user privilege
        boolean bAuthorized = false;
        StorageOSUser user = getUserFromContext();
        if (_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_MONITOR)) {
            bAuthorized = true;
        } else if ((isZoneLevelResource() || isSysAdminReadableResource()) &&
                (_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN))) {
            bAuthorized = true;
        }

        // 2. Parameter check and early detection for unsupported search
        String name = null, tag = null;
        URI projectId = null, tenant = null;
        Map<String, List<String>> parameters = uriInfo.getQueryParameters();

        // remove non-search related common parameters
        parameters.remove(RequestProcessingUtils.REQUESTING_COOKIES);

        if (parameters.containsKey("name")) {
            name = parameters.get("name").get(0);
            checkSearchParameterLength("name", name, getResourceClass());
            checkParameterCombination(parameters, getResourceClass(), "name", "project");
        }

        if (parameters.containsKey("tag")) {
            tag = parameters.get("tag").get(0);
            checkSearchParameterLength("tag", tag, getResourceClass());
            checkParameterCombination(parameters, getResourceClass(), "tag", "tenant");

            // set tenant scope for non-zone resources
            if (!isZoneLevelResource()) {
                if (parameters.containsKey("tenant")) {
                    tenant = URI.create(parameters.get("tenant").get(0));
                }
            }
        }

        if (parameters.containsKey("project")) {
            checkParameterCombination(parameters, getResourceClass(), "project", "name");

            projectId = URI.create(parameters.get("project").get(0));
            if (isZoneLevelResource()) {
                throw APIException.badRequests.invalidParameterSearchProjectNotSupported(getResourceClass().getName());
            }

            // check if user is authorized for the project
            if (!bAuthorized) {
                if (!isAuthorized(projectId)) {
                    throw APIException.forbidden
                            .insufficientPermissionsForUser(getUserFromContext()
                                    .toString());
                }
                bAuthorized = true;
            }
        }

        // 3. Search from db and response permission-eligible results
        SearchResults result = new SearchResults();
        if (name != null || tag != null) {
            SearchedResRepList resRepList = null;
            if (name != null) {
                // search named resources
                resRepList = getNamedSearchResults(name, projectId);
            } else {
                // search tagged resources
                resRepList = getTagSearchResults(tag, tenant);
            }

            if (!bAuthorized) {
                SearchedResRepList filteredResRepList = new SearchedResRepList();

                Iterator<SearchResultResourceRep> _queryResultIterator = resRepList.iterator();

                ResRepFilter<SearchResultResourceRep> resrepFilter = null;
                resrepFilter = (ResRepFilter<SearchResultResourceRep>) getPermissionFilter(getUserFromContext(), _permissionsHelper);

                filteredResRepList.setResult(
                        new FilterIterator<SearchResultResourceRep>(_queryResultIterator, resrepFilter));

                result.setResource(filteredResRepList);
            } else {
                result.setResource(resRepList);
            }

            return result;
        }

        if (projectId != null) {
            // start resource search within project
            // note: for project resources search, the permission check
            // has been addressed in parameter checke period.

            result.setResource(getProjectSearchResults(projectId));
            return result;
        } // end resource search within project

        // some other resource specific search
        return getOtherSearchResults(parameters, bAuthorized);
    }

    // End of the search API section

    private static void checkParameterCombination(
            Map<String, List<String>> parameters, Class<DataObject> resourceClass,
            final String first, final String second) {
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (!entry.getKey().equals(first) && !entry.getKey().equals(second)) {
                throw APIException.badRequests.parameterForSearchCouldOnlyBeCombinedWithOtherParameter(resourceClass.getName(), first,
                        second);
            }
        }
    }

    private static void checkSearchParameterLength(final String field, String value, Class<DataObject> resourceClass) {
        final int minimum = 2;
        if (value.length() < minimum) {
            throw APIException.badRequests.invalidParameterSearchStringTooShort(field, value, resourceClass.getName(), minimum);
        }
    }

    /**
     * @brief List all instances of resource type
     *        Retrieve all ids of this type of resources.
     * 
     * @prereq none
     * 
     * @return list of ids.
     */
    @GET
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public BulkIdParam getBulkIds() {
        StorageOSUser user = getUserFromContext();
        if (_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_MONITOR) ||
                ((isZoneLevelResource() || isSysAdminReadableResource()) &&
                _permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN))) {
            return queryBulkIds();
        }

        throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
    }

    /**
     * Retrieve resource ids.
     * 
     * @return list of IDs of the resources of this type.
     */
    protected BulkIdParam queryBulkIds() {

        BulkIdParam ret = new BulkIdParam();
        ret.setIds(_dbClient.queryByType(getResourceClass(), true));
        return ret;
    }

    /**
     * @brief List data of specified resources
     *        Retrieve resource representations based on input ids.
     * 
     * @prereq none
     * 
     * @param param POST data containing the id list.
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ExcludeLicenseCheck
    public BulkRestRep getBulkResources(BulkIdParam param) {
        return getBulkResources(param.getIds());
    }

    protected BulkRestRep getBulkResources(List<URI> ids) {
        StorageOSUser user = getUserFromContext();
        BulkRestRep ret = null;

        if (ids.size() > _maxBulkSize) {
            throw APIException.badRequests.exceedingLimit("bulk size", _maxBulkSize);
        }

        // full list for:
        // -system monitor
        // -sysadmin (if zone level resource or resource is system admin readable)
        if (_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_MONITOR) ||
                ((isZoneLevelResource() || isSysAdminReadableResource())
                && _permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN))) {
            _log.info("Bulk of {} for sysmonitor/sysadmin",
                    getResourceClass().getSimpleName());
            ret = queryBulkResourceReps(ids);
        } else {
            _log.info("Bulk of {} for user",
                    getResourceClass().getSimpleName());
            ret = queryFilteredBulkResourceReps(ids);
        }
        return ret;
    }

    /**
     * Query resource objects from db based on given ids which are accessible
     * by teh user in the securiy context and wrap them into resourceRestRep
     * objects.
     * 
     * The base class throws unsupported exception.
     * Derived resource class which supports bulk retrieving in user context
     * should override this method.
     * 
     * @param ids the URN of a ViPR representations to be filtered upon
     * @return list of filtered representations.
     */
    protected BulkRestRep queryFilteredBulkResourceReps(
            List<URI> ids) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * Query resource objects from db based on given ids and wrap them
     * into resourceRestRep objects.
     * 
     * The base class throws unsupported exception.
     * Derived resource class which supports bulk retrieving should override this method.
     * 
     * @param ids the URN list of a ViPR bulk resource
     * @return list of representations.
     */
    public BulkRestRep queryBulkResourceReps(List<URI> ids) {
        throw APIException.methodNotAllowed.notSupported();
    }

    protected void verifySystemAdmin() {
        if (!isSystemOrRestrictedSystemAdmin()) {
            throw APIException.forbidden
                    .insufficientPermissionsForUser(getUserFromContext().getName());
        }
    }

    protected boolean isSystemAdmin() {
        return _permissionsHelper.userHasGivenRole(
                getUserFromContext(), null, Role.SYSTEM_ADMIN);
    }

    protected boolean isRestrictedSystemAdmin() {
        return _permissionsHelper.userHasGivenRole(
                getUserFromContext(), null, Role.RESTRICTED_SYSTEM_ADMIN);
    }

    protected boolean isSystemOrRestrictedSystemAdmin() {
        return isSystemAdmin() || isRestrictedSystemAdmin();
    }

    protected boolean isSecurityAdmin() {
        return _permissionsHelper.userHasGivenRole(getUserFromContext(), null, Role.SECURITY_ADMIN);
    }
}
