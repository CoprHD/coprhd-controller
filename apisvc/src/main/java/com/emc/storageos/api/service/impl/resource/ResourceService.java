/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.plugins.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.AsynchJobExecutorService;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPermissionsConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractDiscoveredTenantResource;
import com.emc.storageos.db.client.model.AbstractTenantResource;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredComputeSystemWithAcls;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.common.DbDependencyPurger;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.tenant.TenantOrgList;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.InterNodeHMACAuthFilter;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoDependencyChecker;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.api.NotFoundException;

/**
 * Base resource with common dependencies and convenience methods
 */
public abstract class ResourceService {
    public final static URI INTERNAL_DATASVC_USER = URI.create("datasvc");
    public final static String VDC_ID_QUERY_PARAM = "vdc-id";

    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";
    protected static final String DATAOBJECT_NAME_FIELD = "label";

    @SuppressWarnings("unused")
    private final static Logger _log = LoggerFactory.getLogger(ResourceService.class);

    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    @Autowired
    protected GeoDependencyChecker geoDependencyChecker;

    @Autowired
    protected DbDependencyPurger _dbPurger;

    @Autowired
    protected AsynchJobExecutorService _asynchJobService;

    @Autowired
    protected AuditLogManager _auditMgr;

    @Context
    protected SecurityContext sc;

    @Context
    protected UriInfo uriInfo;

    @Context
    protected HttpServletRequest _request;

    protected CoordinatorClient _coordinator;
    protected DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    protected String getServiceType() {
        throw new UnsupportedOperationException("Not implemented in current service");
    }

    /**
     * Check if a resource can be inactivated safely
     * 
     * @param object Reference to object to verify.
     * 
     * @return detail type of the dependency if exist, null otherwise
     * 
     * @throws DatabaseException for db errors
     */
    protected <T extends DataObject> String checkForDelete(T object) {
        return checkForDelete(object, null);
    }

    /**
     * Check if a resource can be inactivated safely
     * 
     * @param object Reference to object to verify.
     * @param excludeTypes optional list of classes that can be excluded as dependency
     * 
     * @return detail type of the dependency if exist, null otherwise
     * 
     * @throws DatabaseException for db errors
     */
    protected <T extends DataObject> String checkForDelete(T object, List<Class<? extends DataObject>> excludeTypes) {
        Class<? extends DataObject> clazz = object.getClass();
        URI id = object.getId();

        String depMsg = geoDependencyChecker.checkDependencies(id, clazz, true, excludeTypes);
        if (depMsg != null) {
            return depMsg;
        }

        return object.canBeDeleted();
    }

    /**
     * Check if a resource with the same name exists
     * 
     * @param name
     */
    protected <T extends DataObject> List<T> listFileSystemsWithLabelName(String name, Class<T> type,
            URI parentToScope, String parentFieldName) {
        List<T> objectList = new ArrayList<T>();
        if (parentToScope != null && parentFieldName != null) {
            objectList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, type,
                    ContainmentPrefixConstraint.Factory.getFullMatchConstraint(type, parentFieldName,
                            parentToScope, name));
        } else {
            objectList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, type,
                    PrefixConstraint.Factory.getFullMatchConstraint(type, DATAOBJECT_NAME_FIELD, name));
        }

        return objectList;
    }

    /**
     * Check if a resource with the same name exists
     * 
     * @param name
     */
    public static <T extends DataObject> void checkForDuplicateName(String name, Class<T> type,
            URI parentToScope, String parentFieldName, DbClient dbClient) {
        List<T> objectList = new ArrayList<T>();
        if (parentToScope != null && parentFieldName != null) {
            objectList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, type,
                    ContainmentPrefixConstraint.Factory.getFullMatchConstraint(type, parentFieldName,
                            parentToScope, name));
        } else {
            objectList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, type,
                    PrefixConstraint.Factory.getFullMatchConstraint(type, DATAOBJECT_NAME_FIELD, name));
        }
        if (!objectList.isEmpty()) {
            throw APIException.badRequests.duplicateLabel(name);
        }
    }

    /**
     * Check if a resource with the same name exists at zone level
     * 
     * @param name
     */
    protected void checkForDuplicateName(String name, Class<? extends DataObject> type) {
        checkForDuplicateName(name, type, null, null, _dbClient);
    }

    /**
     * Check if a tenant with the same namespace exists
     * @param namespace     namespace of the tenant
     */
    public void checkForDuplicateNamespace(String namespace) {
        TenantOrgList list = new TenantOrgList();
        //Verify with root tenant if current is not root
        TenantOrg rootTenant = _permissionsHelper.getRootTenant();
        if (rootTenant.getNamespace() != null && rootTenant.getNamespace().equalsIgnoreCase(namespace)) {
            throw APIException.badRequests.duplicateNamespace(namespace);
        }

        NamedElementQueryResultList subtenants = new NamedElementQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getTenantOrgSubTenantConstraint(rootTenant.getId()), subtenants);
        for (NamedElementQueryResultList.NamedElement el : subtenants) {
            TenantOrg currTenant = _dbClient.queryObject(TenantOrg.class, el.getId());
            if (currTenant.getNamespace() != null && currTenant.getNamespace().equalsIgnoreCase(namespace)) {
                    throw APIException.badRequests.duplicateNamespace(namespace);
            }
        }
    }

    /**
     * Looks up controller dependency for given hardware type.
     * If cannot locate controller for defined hardware type, lookup controller for
     * EXTERNALDEVICE.
     *
     * @param clazz controller interface
     * @param hw hardware name
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        T controller;
        try {
            controller = _coordinator.locateService(
                   clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
        } catch (RetryableCoordinatorException rex) {
            controller = _coordinator.locateService(
                    clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, Constants.EXTERNALDEVICE, clazz.getSimpleName());
        }
        return controller;
    }

    /**
     * Get StorageOSUser from the security context
     * 
     * @return
     */
    protected StorageOSUser getUserFromContext() {
        if (!hasValidUserInContext()) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        return (StorageOSUser) sc.getUserPrincipal();
    }

    /**
     * Determine if the security context has a valid StorageOSUser object
     * 
     * @return true if the StorageOSUser is present
     */
    protected boolean hasValidUserInContext() {
        if ((sc != null) && (sc.getUserPrincipal() instanceof StorageOSUser)) {
            return true;
        } else {
            return false;
        }
    }

    // Helper function to check if the user has authorization to access the project
    // This is used by all search functions
    protected boolean isAuthorized(URI projectUri) {
        final StorageOSUser user = getUserFromContext();
        if (_permissionsHelper == null) {
            return false;
        }
        Project project = _permissionsHelper.getObjectById(projectUri, Project.class);
        if (project == null) {
            return false;
        }
        if ((_permissionsHelper.userHasGivenRole(user, project.getTenantOrg().getURI(),
                Role.SYSTEM_MONITOR, Role.TENANT_ADMIN) || _permissionsHelper.userHasGivenACL(user,
                projectUri, ACL.ANY))) {
            return true;
        } else {
            return false;
        }
    }

    protected RestLinkRep getCurrentSelfLink() {
        try {
            return new RestLinkRep("self", new URI("/" + uriInfo.getPath()));
        } catch (Exception ex) {
            return new RestLinkRep("self", null);
        }
    }

    /**
     * Returns the instance of DataObject for the given id and class. It
     * throws {@link com.emc.storageos.svcs.errorhandling.resources.BadRequestException} when id is not a valid
     * URI or when the object has been marked for deletion and checkInactive
     * is true. Throws {@link NotFoundException} when the object has been delete.
     * 
     * @param id the URN of a ViPR object to be fetched.
     * @param checkInactive a flag to indicate if the object inactive flag should be
     *            checked. In general, this check should be performed when the object
     *            is going to be modified or deleted.
     * @return the instance of dataobject for the given id.
     */
    protected <T extends DataObject> T queryObject(Class<T> clzz, URI id, boolean checkInactive) {
        ArgValidator.checkUri(id);
        T dataObject = _dbClient.queryObject(clzz, id);
        ArgValidator.checkEntity(dataObject, id, isIdEmbeddedInURL(id), checkInactive);
        return dataObject;
    }

    /**
     * Check all path parameters for the specified id
     * 
     * @param id the URN of a ViPR Entity to check
     * @return true if the id is in the path
     */
    protected boolean isIdEmbeddedInURL(final URI id) {
        return BlockServiceUtils.isIdEmbeddedInURL(id, uriInfo);
    }

    /**
     * This function is to retrieve the children of a given class.
     * 
     * @param id the URN of parent
     * @param clzz the child class
     * @param nameField the name of the field of the child class that will be displayed as
     *            name in {@link NamedRelatedResourceRep}. Note this field should be a required
     *            field because, objects for which this field is null will not be returned by
     *            this function.
     * @param linkField the name of the field in the child class that stored the parent id
     * @return a list of children of tenant for the given class
     */
    protected <T extends DataObject> List<NamedElementQueryResultList.NamedElement> listChildren(URI id, Class<T> clzz,
            String nameField, String linkField) {
        @SuppressWarnings("deprecation")
        List<URI> uris = _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getContainedObjectsConstraint(id, clzz, linkField));
        if (uris != null && !uris.isEmpty()) {
            List<T> dataObjects = _dbClient.queryObjectField(clzz, nameField, uris);
            List<NamedElementQueryResultList.NamedElement> elements = new ArrayList<NamedElementQueryResultList.NamedElement>(
                    dataObjects.size());
            for (T dataObject : dataObjects) {
                Object name = DataObjectUtils.getPropertyValue(clzz, dataObject, nameField);
                elements.add(NamedElementQueryResultList.NamedElement.createElement(
                        dataObject.getId(), name == null ? "" : name.toString()));
            }
            return elements;
        } else {
            return new ArrayList<NamedElementQueryResultList.NamedElement>();
        }
    }

    /**
     * A utility function that checks of an alternate ID uniqueness constraints
     * will be violated when an object is created or modified.
     * 
     * @param type The class of object being validated
     * @param fieldName the name of the alternate ID field
     * @param value the value being checked
     * @param entityName the name of the entity to be used in the error message
     */
    protected <T extends DataObject> void checkDuplicateAltId(Class<T> type,
            String fieldName, String value, String entityName) {
        checkDuplicateAltId(type, fieldName, value, entityName, null);
    }

    /**
     * A utility function that checks of an alternate ID uniqueness constraints
     * will be violated when an object is created or modified.
     * 
     * @param type The class of object being validated
     * @param fieldName the name of the alternate ID field
     * @param value the value being checked
     * @param entityName the name of the entity to be used in the error message
     * @param errorFriendlyFieldName - user friendly value of fieldName to put in error message
     *            (Example 'Initiator Port' rather than 'iniport').
     */
    protected <T extends DataObject> void checkDuplicateAltId(Class<T> type,
            String fieldName, String value, String entityName, String errorFriendlyFieldName) {
        List<T> objectList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, type,
                AlternateIdConstraint.Factory.getConstraint(type, fieldName, value));
        if (!objectList.isEmpty()) {
            if (errorFriendlyFieldName == null) {
                throw APIException.badRequests.duplicateEntityWithField(entityName, fieldName);
            } else {
                throw APIException.badRequests.duplicateEntityWithField(entityName, errorFriendlyFieldName);
            }
        }
    }

    /**
     * A utility function that checks for label uniqueness constraint for a single
     * class type.
     * 
     * @param type The class of object being validated
     * @param value the value of label being checked
     */
    protected <T extends DataObject> void checkDuplicateLabel(Class<T> type,
            String value) {
        List<T> objectList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, type,
                PrefixConstraint.Factory.getFullMatchConstraint(type, DATAOBJECT_NAME_FIELD, value));
        if (!objectList.isEmpty()) {
            throw APIException.badRequests.duplicateLabel(value);
        }
    }

    /**
     * For some object such as vcenter clusters and vcenter data centers, the name
     * must be unique within the children of the same parent.
     * 
     * @param id the URN of the parent object
     * @param clzz the class of the child object
     * @param nameField the field in the child object that needs to be unique
     * @param linkField the field in the child class that references the parent
     * @param value the value being checked
     * @param dbClient an instance of {@link DbClient}
     */
    protected void checkDuplicateChildName(URI id, Class<? extends DataObject> clzz,
            String nameField, String linkField, String value, DbClient dbClient) {

        URIQueryResultList uris = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getContainedObjectsConstraint(id, clzz, linkField), uris);
        Iterator<?> objs = dbClient.queryIterativeObjects(clzz, uris);
        while (objs.hasNext()) {
            DataObject obj = (DataObject) objs.next();
            if (value.equals(obj.getLabel()) && !obj.getInactive()) {
                throw APIException.badRequests.duplicateChildForParent(id);
            }
        }
    }

    /**
     * For two URIs which can be null as defined in {@link NullColumnValueGetter#getNullURI()},
     * compare the two URI and return true if they are both null or both not null but equal.
     * 
     * @param uri1 the first URI
     * @param uri2 the second URI
     * @return true if both URIs are null as defined in {@link NullColumnValueGetter#getNullURI()} or both are equal.
     */
    protected boolean areEqual(URI uri1, URI uri2) {
        if (NullColumnValueGetter.isNullURI(uri1)) {
            return NullColumnValueGetter.isNullURI(uri2);
        } else {
            return uri1.equals(uri2);
        }
    }

    /**
     * Record audit log for services
     * 
     * @param opType audit event type (e.g. CREATE_VPOOL|TENANT etc.)
     * @param operationalStatus Status of operation (true|false)
     * @param operationStage Stage of operation.
     *            For sync operation, it should be null;
     *            For async operation, it should be "BEGIN" or "END";
     * @param descparams Description paramters
     */
    protected void auditOp(OperationTypeEnum opType,
            boolean operationalStatus,
            String operationStage,
            Object... descparams) {

        URI tenantId;
        URI username;

        if (!hasValidUserInContext() && InterNodeHMACAuthFilter.isInternalRequest(_request)) {
            // use default values for internal datasvc requests that lack a user context
            tenantId = _permissionsHelper.getRootTenant().getId();
            username = INTERNAL_DATASVC_USER;
        } else {
            tenantId = URI.create(getUserFromContext().getTenantId());
            username = URI.create(getUserFromContext().getName());
        }
        _auditMgr.recordAuditLog(tenantId,
                username,
                getServiceType(),
                opType,
                System.currentTimeMillis(),
                operationalStatus ? AuditLogManager.AUDITLOG_SUCCESS : AuditLogManager.AUDITLOG_FAILURE,
                operationStage,
                descparams);
    }

    /**
     * Checks if the user is authorized to view resources in a tenant organization. The
     * user can see resources if:
     * <ul>
     * <li>The user is in the tenant organization</li>
     * <li>The user has SYSTEM_ADMIN or SYSTEM_MONITOR role</li>
     * <li>the user has TENANT_ADMIN role to this tenant organization even if the user is in another tenant org</li>
     * </ul>
     * 
     * @param tenantId the tenant organization URI
     * @param user the user
     */
    protected void verifyAuthorizedInTenantOrg(URI tenantId, StorageOSUser user) {
        if (!(tenantId.toString().equals(user.getTenantId()) || isSystemAdminOrMonitorUser() || _permissionsHelper.userHasGivenRole(user,
                tenantId, Role.TENANT_ADMIN))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
    }

    /**
     * A utility function to check if the context user has system admin or monitor role.
     * 
     * @return True if if the context user has system admin or monitor role.
     */
    protected boolean isSystemAdminOrMonitorUser() {
        return _permissionsHelper.userHasGivenRole(
                getUserFromContext(), null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR);
    }

    /**
     * A utility function to check if the context user has system admin or monitor role and
     * throws an exception when the check fails.
     */
    protected void verifySystemAdminOrMonitorUser() {
        if (!isSystemAdminOrMonitorUser()) {
            throw APIException.forbidden
                    .insufficientPermissionsForUser(getUserFromContext().getName());
        }
    }

    /**
     * This function is to retrieve the name and id list of a class of objects.
     * 
     * @param clazz the class objects to retrieved.
     * @param nameField the name of the field of the class that will be displayed as
     *            name in {@link NamedRelatedResourceRep}. Note this field should be a required
     *            field because, objects for which this field is null will not be returned by
     *            this function.
     * @return a name and id list of a given class
     */
    protected <T extends DataObject> List<NamedElementQueryResultList.NamedElement> listDataObjects(Class<T> clazz,
            String nameField) {
        List<T> dataObjects = getDataObjects(clazz);
        if (!CollectionUtils.isEmpty(dataObjects)) {
            return getNamedElementsList(clazz, nameField, dataObjects);
        } else {
            return new ArrayList<NamedElementQueryResultList.NamedElement>();
        }
    }

    /**
     * This function is to get the name and id list of a class from the list of given objects.
     * 
     * @param clazz the class objects to retrieved.
     * @param nameField the name of the field of the class that will be displayed as
     *            name in {@link NamedRelatedResourceRep}. Note this field should be a required
     *            field because, objects for which this field is null will not be returned by
     *            this function.
     * @param dataObjects all the data objects of the class to which the name and id pair list
     *            is created.
     * @return a name and id list of a given class.
     */
    protected <T extends DataObject> List<NamedElementQueryResultList.NamedElement> getNamedElementsList(Class<T> clazz,
            String nameField,
            List<T> dataObjects) {
        List<NamedElementQueryResultList.NamedElement> elements = new ArrayList<NamedElementQueryResultList.NamedElement>(
                dataObjects.size());
        for (T dataObject : dataObjects) {
            Object name = DataObjectUtils.getPropertyValue(clazz, dataObject, nameField);
            elements.add(NamedElementQueryResultList.NamedElement.createElement(
                    dataObject.getId(), name == null ? "" : name.toString()));
        }
        return elements;
    }

    /**
     * Filters the data objects with no configured acls from the given list of objects.
     * From the filtered list, creates the name and id pair elements.
     * 
     * @param clazz class objects to be filtered.
     * @param nameField name field of the objects.
     * @param dataObjects list of data objects to be filtered with no acls.
     * @return a name and id list of a given class with no acls.
     */
    protected <T extends DiscoveredComputeSystemWithAcls> List<NamedElementQueryResultList.NamedElement> getNamedElementsWithNoAcls(
            Class<T> clazz,
            String nameField,
            List<T> dataObjects) {
        List<NamedElementQueryResultList.NamedElement> elements = new ArrayList<NamedElementQueryResultList.NamedElement>(
                dataObjects.size());
        for (T dataObject : dataObjects) {
            if (CollectionUtils.isEmpty(dataObject.getAcls())) {
                Object name = DataObjectUtils.getPropertyValue(clazz, dataObject, nameField);
                elements.add(NamedElementQueryResultList.NamedElement.createElement(
                        dataObject.getId(), name == null ? "" : name.toString()));
            }
        }
        return elements;
    }

    /**
     * Get the data object of a type.
     * 
     * @param clazz of objects to be retrieved.
     * @return list of all the objects of type clazz.
     */
    protected <T extends DataObject> List<T> getDataObjects(Class<T> clazz) {
        @SuppressWarnings("deprecation")
        List<URI> uris = _dbClient.queryByType(clazz, true);
        List<T> dataObjects = null;
        if (uris != null) {
            dataObjects = _dbClient.queryObject(clazz, uris);
        }
        return dataObjects;
    }

    /**
     * Retrieves the list of NamedElements of the data objects with acls
     * and the list is filtered based on the tenant information.
     * 
     * @param tenantId the URN of parent
     * @param clzz the child class
     * @param nameField the name of the field of the child class that will be displayed as
     *            name in {@link NamedRelatedResourceRep}. Note this field should be a required
     *            field because, objects for which this field is null will not be returned by
     *            this function.
     * @return a list of NamedElements filtered by tenant.
     */
    protected <T extends DiscoveredComputeSystemWithAcls> List<NamedElementQueryResultList.NamedElement> listChildrenWithAcls(URI tenantId,
            Class<T> clzz,
            String nameField) {
        List<T> dataObjects = getDiscoveredComputeObjects(tenantId, clzz);
        List<NamedElementQueryResultList.NamedElement> elements = new ArrayList<NamedElementQueryResultList.NamedElement>(
                dataObjects.size());
        for (T dataObject : dataObjects) {
            Object name = DataObjectUtils.getPropertyValue(Vcenter.class, dataObject, nameField);
            elements.add(NamedElementQueryResultList.NamedElement.createElement(
                    dataObject.getId(), name == null ? "" : name.toString()));
        }
        return elements;
    }

    /**
     * Retrieves the list of objects with acls based on the tenant information.
     * 
     * @param tenantId to used to filter the objects.
     * @param clzz class of objects.
     * @return the filtered list of objects with acls.
     */
    protected <T extends DiscoveredComputeSystemWithAcls> List<T> getDiscoveredComputeObjects(URI tenantId, Class<T> clzz) {
        PermissionsKey permissionKey = new PermissionsKey(PermissionsKey.Type.TENANT, tenantId.toString());

        URIQueryResultList resultURIs = new URIQueryResultList();
        Constraint aclConstraint = ContainmentPermissionsConstraint.Factory.getDiscoveredObjsWithPermissionsConstraint(
                permissionKey.toString(), Vcenter.class);
        _dbClient.queryByConstraint(aclConstraint, resultURIs);
        List<URI> uris = new ArrayList<URI>();
        for (URI result : resultURIs) {
            uris.add(result);
        }

        List<T> dataObjects = new ArrayList<T>();
        if (uris != null && !uris.isEmpty()) {
            dataObjects = _dbClient.queryObjectField(clzz, DATAOBJECT_NAME_FIELD, uris);
        }
        return dataObjects;
    }

    /**
     * Filters the named element list of abstract tenant resources
     * like vCenterDataCenter and Cluster by its tenant.
     * 
     * @param tenantId will be filtered based on this tenantId.
     * @param elements List of named elements of all the active abstract tenant
     *            resources to be filtered based on the tenant.
     * @return returns the filtered element list based on the tenant.
     */
    protected <T extends AbstractTenantResource> List<NamedElementQueryResultList.NamedElement>
            filterTenantResourcesByTenant(URI tenantId, Class<T> clazz,
                    List<NamedElementQueryResultList.NamedElement> elements) {
        if (CollectionUtils.isEmpty(elements)) {
            return elements;
        }

        URI localTenantId = tenantId;
        // The the requested tenant is null or "No-Filter", return all the vCenters.
        if (NullColumnValueGetter.isNullURI(localTenantId) ||
                AbstractTenantResource.NO_TENANT_SELECTOR.equalsIgnoreCase(localTenantId.toString())) {
            return elements;
        }

        // If the filter tenantId is "Not-Assigned" modify the search tenantId to null.
        if (AbstractDiscoveredTenantResource.TENANT_SELECTOR_FOR_UNASSIGNED.equalsIgnoreCase(localTenantId.toString())) {
            localTenantId = NullColumnValueGetter.getNullURI();
        }

        Iterator<NamedElementQueryResultList.NamedElement> elementIterator = elements.iterator();
        while (elementIterator.hasNext()) {
            NamedElementQueryResultList.NamedElement element = elementIterator.next();
            if (element == null) {
                continue;
            }

            T dataCenter = _dbClient.queryObject(clazz, element.getId());
            if (dataCenter == null) {
                continue;
            }

            // The vCenters tenant
            if (areEqual(localTenantId, dataCenter.getTenant())) {
                continue;
            }
            elementIterator.remove();
        }

        return elements;
    }

    /**
     * Filters the named element list of abstract discovered tenant resources
     * like vCenter and Host by its tenant.
     * 
     * @param tenantId will be filtered based on this tenantId.
     * @param elements List of named elements of all the active abstract discovered,
     *            tenant resources to be filtered based on the tenant.
     * @return returns the filtered element list based on the tenant.
     */
    protected <T extends AbstractDiscoveredTenantResource> List<NamedElementQueryResultList.NamedElement>
            filterDiscoveredTenantResourcesByTenant(URI tenantId, Class<T> clazz,
                    List<NamedElementQueryResultList.NamedElement> elements) {
        if (CollectionUtils.isEmpty(elements)) {
            return elements;
        }

        URI localTenantId = tenantId;
        // The the requested tenant is null or ALL, return all the vCenters.
        if (NullColumnValueGetter.isNullURI(localTenantId) ||
                AbstractDiscoveredTenantResource.NO_TENANT_SELECTOR.equalsIgnoreCase(localTenantId.toString())) {
            return elements;
        }

        // If the filter tenantId is "NONE" modify the search tenantId to null.
        if (AbstractDiscoveredTenantResource.TENANT_SELECTOR_FOR_UNASSIGNED.equalsIgnoreCase(localTenantId.toString())) {
            localTenantId = NullColumnValueGetter.getNullURI();
        }

        Iterator<NamedElementQueryResultList.NamedElement> elementIterator = elements.iterator();
        while (elementIterator.hasNext()) {
            NamedElementQueryResultList.NamedElement element = elementIterator.next();
            if (element == null) {
                continue;
            }

            T dataCenter = _dbClient.queryObject(clazz, element.getId());
            if (dataCenter == null) {
                continue;
            }

            // The vCenters tenant
            if (areEqual(localTenantId, dataCenter.getTenant())) {
                continue;
            }
            elementIterator.remove();
        }

        return elements;
    }

    /**
     * Filters the named element list of abstract discovered tenant resources
     * like vCenter and Host by its tenant.
     * 
     * @param tenantId will be filtered based on this tenantId.
     * @param clazz class of the resource to be filtered.
     * @param nameField label field of the resource.
     * @param linkField name of the parent link field.
     * @return returns the filtered element list based on the tenant.
     */
    protected <T extends DataObject> List<NamedElementQueryResultList.NamedElement>
            filterTenantResourcesByTenant(URI tenantId, Class<T> clazz, String nameField, String linkField) {

        List<NamedElementQueryResultList.NamedElement> elements;

        URI localTenantId = tenantId;
        // The the requested tenant is null or ALL, return all the vCenters.
        if (NullColumnValueGetter.isNullURI(localTenantId) ||
                AbstractDiscoveredTenantResource.NO_TENANT_SELECTOR.equalsIgnoreCase(localTenantId.toString())) {
            elements = listDataObjects(clazz, nameField);
            return elements;
        }

        // If the filter tenantId is "NONE" modify the search tenantId to null.
        if (AbstractDiscoveredTenantResource.TENANT_SELECTOR_FOR_UNASSIGNED.equalsIgnoreCase(localTenantId.toString())) {
            localTenantId = NullColumnValueGetter.getNullURI();
        }

        elements = listChildren(localTenantId, clazz, nameField, linkField);
        return elements;
    }
}
