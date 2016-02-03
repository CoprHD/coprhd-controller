/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.authorization;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import com.emc.vipr.client.core.BlockSnapshotSessions;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.*;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.security.SecurityDisabler;
import com.emc.storageos.security.authorization.*;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.spi.container.ResourceFilter;

/**
 * Class implements ResourceFilterFactory to add permissions filter where needed
 */
public class PermissionsFilterFactory extends AbstractPermissionsFilterFactory {
    private static final Logger _log = LoggerFactory.getLogger(PermissionsFilterFactory.class);
    private PermissionsHelper _permissionsHelper;
    private boolean disableLicenseCheck = false;

    @Autowired(required = false)
    private SecurityDisabler _disabler;

    private @Context
    UriInfo uriInfo;

    @Autowired
    private DrUtil drUtil;

    private boolean isStandby; // default to false
    
    /**
     * PermissionsFilter for apisvc
     */
    private class ApisvcPermissionFilter extends AbstractPermissionFilter {
        ApisvcPermissionFilter(Role[] roles, ACL[] acls, boolean blockProxies,
                Class resourceClazz, PermissionsHelper helper) {
            super(roles, acls, blockProxies, resourceClazz, helper);
        }

        @Override
        protected UriInfo getUriInfo() {
            return uriInfo;
        }

        /**
         * Get tenant id from the uri
         * 
         * @return
         */
        @Override
        protected URI getTenantIdFromURI(UriInfo uriInfo) {
            if (_resourceClazz.isAssignableFrom(TenantsService.class)) {
                String uriStr = uriInfo.getPathParameters().getFirst("id");
                if (uriStr != null && !uriStr.isEmpty()) {
                    return URI.create(uriStr);
                }
            } else if (_resourceClazz.isAssignableFrom(HostService.class) ||
                    _resourceClazz.isAssignableFrom(ClusterService.class) ||
                    _resourceClazz.isAssignableFrom(VcenterDataCenterService.class)) {
                String tenantResourceUriStr = uriInfo.getPathParameters().getFirst("id");
                if (tenantResourceUriStr != null && !tenantResourceUriStr.isEmpty()) {
                    URI tenantURI = _permissionsHelper.getTenantResourceTenantId(tenantResourceUriStr);
                    if (NullColumnValueGetter.isNullURI(tenantURI)) {
                        APIException.forbidden.resourceDoesNotBelongToAnyTenant(VcenterDataCenterService.class.getSimpleName(),
                                tenantResourceUriStr);
                    }
                    return _permissionsHelper.getTenantResourceTenantId(tenantResourceUriStr);
                }
            } else if (_resourceClazz.isAssignableFrom(InitiatorService.class) ||
                    _resourceClazz.isAssignableFrom(IpInterfaceService.class)) {
                String tenantResourceUriStr = uriInfo.getPathParameters().getFirst("id");
                if (tenantResourceUriStr != null && !tenantResourceUriStr.isEmpty()) {
                    return _permissionsHelper.getTenantResourceTenantId(tenantResourceUriStr);
                }
            } else if (_resourceClazz.isAssignableFrom(ProjectService.class)) {
                String projectUriStr = uriInfo.getPathParameters().getFirst("id");
                if (projectUriStr != null && !projectUriStr.isEmpty()) {
                    return _permissionsHelper.getTenantIdFromProjectId(projectUriStr,
                            isIdEmbeddedInURL(projectUriStr));
                }
            } else {
                URI projectUri = getProjectIdFromURI(uriInfo);
                if (projectUri != null) {
                    return _permissionsHelper.getTenantIdFromProjectId(projectUri.toString(),
                            isIdEmbeddedInURL(projectUri.toString()));
                }
            }
            return null;
        }

        /**
         * Retrieve project id from the resource id
         * 
         * @param uri
         * @param clazz
         * @return
         */
        private URI getProjectIdFromResourceId(String uri, Class<? extends DataObject> clazz) {
            URI id = URI.create(uri);
            DataObject obj = _permissionsHelper.getObjectById(id, clazz);
            if (obj == null) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
            ProjectResource projObj = (ProjectResource) obj;
            if (obj.checkInternalFlags(Flag.NO_PUBLIC_ACCESS) || (projObj.getProject() == null)) {
                throw APIException.badRequests.unauthorizedAccessToNonPublicResource();
            }
            return projObj.getProject().getURI();
        }

        /**
         * Retrieve project id from the snapshot resource id
         * 
         * @param uri
         * @param clazz
         * @return
         */
        private URI getProjectIdFromResourceSnapshotId(String uri, Class<? extends DataObject> clazz) {
            URI id = URI.create(uri);
            ProjectResourceSnapshot obj = (ProjectResourceSnapshot) _permissionsHelper
                    .getObjectById(id, clazz);
            if (obj == null) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
            return getProjectIdFromResourceId(obj.getParent().getURI().toString(),
                    obj.parentClass());
        }

        /**
         * Retrieve project id from the block snapshot id
         * 
         * @param uri
         * @param clazz
         * @return
         */
        private URI getProjectIdFromResourceBlockSnapshotId(String uri, Class<? extends DataObject> clazz) {
            URI id = URI.create(uri);
            ProjectResourceSnapshot obj = (ProjectResourceSnapshot) _permissionsHelper
                    .getObjectById(id, clazz);
            if (obj == null) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            }
            return obj.getProject().getURI();
        }

        /**
         * Retrieve project id from the snapshot resource id
         * 
         * @param uri
         * @return
         */
        private URI getProjectIdFromComputeResources(String uri) {
            URI id = URI.create(uri);
            if (URIUtil.isType(id, Host.class)) {
                Host host = _permissionsHelper.getObjectById(id, Host.class);
                return host.getProject();
            } else if (URIUtil.isType(id, Cluster.class)) {
                Cluster cluster = _permissionsHelper.getObjectById(id, Cluster.class);
                return cluster.getProject();
            } else if (URIUtil.isType(id, Initiator.class)) {
                Initiator ini = _permissionsHelper.getObjectById(id, Initiator.class);
                if (ini.getHost() != null) {
                    Host host = _permissionsHelper.getObjectById(ini.getHost(), Host.class);
                    return host.getProject();
                } else {
                    return null;
                }
            } else if (URIUtil.isType(id, IpInterface.class)) {
                IpInterface hostIf = _permissionsHelper.getObjectById(id, IpInterface.class);
                if (hostIf.getHost() != null) {
                    Host host = _permissionsHelper.getObjectById(hostIf.getHost(), Host.class);
                    return host.getProject();
                } else {
                    return null;
                }
            }
            return null;
        }

        /**
         * Get project id from the uri
         * 
         * @return
         */
        @Override
        protected URI getProjectIdFromURI(UriInfo uriInfo) {
            if (_resourceClazz.isAssignableFrom(ProjectService.class)) {
                String projectUriStr = uriInfo.getPathParameters().getFirst("id");
                if (projectUriStr != null && !projectUriStr.isEmpty()) {
                    return URI.create(projectUriStr);
                }
            } else {
                String projectUriStr = uriInfo.getQueryParameters().getFirst("project");
                String uriStr = uriInfo.getPathParameters().getFirst("id");
                if (projectUriStr != null && !projectUriStr.isEmpty()) {
                    return URI.create(projectUriStr);
                } else if (uriStr != null && !uriStr.isEmpty()) {
                    if (_resourceClazz.isAssignableFrom(FileService.class)) {
                        return getProjectIdFromResourceId(uriStr, FileShare.class);
                    } else if (_resourceClazz.isAssignableFrom(ExportGroupService.class)) {
                        return getProjectIdFromResourceId(uriStr, ExportGroup.class);
                    } else if (_resourceClazz.isAssignableFrom(BlockService.class)) {
                        return getProjectIdFromResourceId(uriStr, BlockService.getBlockServiceResourceClass(uriStr));
                    } else if (_resourceClazz.isAssignableFrom(BlockConsistencyGroupService.class)) {
                        return getProjectIdFromResourceId(uriStr, BlockConsistencyGroup.class);
                    } else if (_resourceClazz.isAssignableFrom(BlockSnapshotService.class)) {
                        return getProjectIdFromResourceBlockSnapshotId(uriStr, BlockSnapshot.class);
                    } else if (_resourceClazz.isAssignableFrom(FileSnapshotService.class)) {
                        return getProjectIdFromResourceSnapshotId(uriStr, Snapshot.class);
                    } else if (_resourceClazz.isAssignableFrom(HostService.class)) {
                        return getProjectIdFromComputeResources(uriStr);
                    } else if (_resourceClazz.isAssignableFrom(ClusterService.class)) {
                        return getProjectIdFromComputeResources(uriStr);
                    } else if (_resourceClazz.isAssignableFrom(IpInterfaceService.class)) {
                        return getProjectIdFromComputeResources(uriStr);
                    } else if (_resourceClazz.isAssignableFrom(InitiatorService.class)) {
                        return getProjectIdFromComputeResources(uriStr);
                    } else if (_resourceClazz.isAssignableFrom(BucketService.class)) {
                        return getProjectIdFromResourceId(uriStr, Bucket.class);
                    } else if (_resourceClazz.isAssignableFrom(BlockSnapshotSessionService.class)) {
                        return getProjectIdFromResourceBlockSnapshotId(uriStr, BlockSnapshotSession.class);
                    }
                } else {
                    _log.warn("project id not available for this resource type");
                }
            }
            return null;
        }

        @Override
        protected Set<String> getUsageAclsFromURI(String tenantId, UriInfo uriInfo) {
            Set<String> acls = null;
            if (tenantId != null) {
                String uriStr = uriInfo.getPathParameters().getFirst("id");
                if (uriStr != null && !uriStr.isEmpty()) {
                    URI uri = URI.create(uriStr);
                    if (VirtualPoolService.class.isAssignableFrom(_resourceClazz)) {
                        VirtualPool obj = _permissionsHelper.getObjectById(uri, VirtualPool.class);
                        // if no acls, consider open for all
                        if (obj.getAcls() == null || obj.getAcls().size() == 0) {
                            acls = new HashSet<String>();
                            acls.add(ACL.USE.toString());
                        } else {
                            acls = obj.getAclSet(new PermissionsKey(PermissionsKey.Type.TENANT,
                                    tenantId, obj.getType()).toString());
                        }
                    } else if (ComputeVirtualPoolService.class.isAssignableFrom(_resourceClazz)) {
                        ComputeVirtualPool obj = _permissionsHelper.getObjectById(uri, ComputeVirtualPool.class);
                        // if no acls, consider open for all
                        if (obj.getAcls() == null || obj.getAcls().size() == 0) {
                            acls = new HashSet<String>();
                            acls.add(ACL.USE.toString());
                        } else {
                            acls = obj.getAclSet(new PermissionsKey(PermissionsKey.Type.TENANT,
                                    tenantId.toString(), obj.getSystemType()).toString());
                        }

                    } else if (_resourceClazz.isAssignableFrom(VirtualArrayService.class)) {
                        VirtualArray obj = _permissionsHelper
                                .getObjectById(uri, VirtualArray.class);
                        // if no acls, consider open for all
                        if (obj.getAcls() == null || obj.getAcls().size() == 0) {
                            acls = new HashSet<String>();
                            acls.add(ACL.USE.toString());
                        } else {
                            acls = obj.getAclSet(new PermissionsKey(PermissionsKey.Type.TENANT,
                                    tenantId).toString());
                        }
                    } else if (_resourceClazz.isAssignableFrom(VcenterService.class)) {
                        Vcenter obj = _permissionsHelper.getObjectById(uri, Vcenter.class);
                        if (obj.getAcls() == null || obj.getAcls().size() == 0) {
                            acls = new HashSet<String>();
                            acls.add(ACL.USE.toString());
                        } else {
                            acls = obj.getAclSet(new PermissionsKey(PermissionsKey.Type.TENANT,
                                    tenantId).toString());
                        }
                    } else if (_resourceClazz.isAssignableFrom(HostService.class) ||
                            _resourceClazz.isAssignableFrom(VcenterDataCenterService.class) ||
                            _resourceClazz.isAssignableFrom(InitiatorService.class) ||
                            _resourceClazz.isAssignableFrom(IpInterfaceService.class) ||
                            _resourceClazz.isAssignableFrom(ClusterService.class)) {
                        // do nothing, if there is no project association, there are no ACLs
                    } else {
                        throw new RuntimeException("undefined permission check on resource: "
                                + _resourceClazz);
                    }
                }
            }
            return acls;
        }

        /**
         * Get tenant ids from the uri
         *
         * @return
         */
        @Override
        protected Set<URI> getTenantIdsFromURI(UriInfo uriInfo) {
            if (_resourceClazz.isAssignableFrom(VcenterService.class)) {
                String uriStr = uriInfo.getPathParameters().getFirst("id");
                if (uriStr != null && !uriStr.isEmpty()) {
                    return _permissionsHelper.getTenantResourceTenantIds(uriStr);
                }
            }
            return null;
        }
    }

    /**
     * License filter for apisvc
     */
    private class ApisvcLicenseFilter extends AbstractLicenseFilter {
        @Override
        public ContainerRequest filter(ContainerRequest request) {
            if (!_permissionsHelper.hasAnyLicense()) {
                throw APIException.forbidden.noLicenseFound();
            }
            return request;
        }
    }

    /**
     * Setter for permissions helper object
     * 
     * @param permissionsHelper
     */
    public void setPermissionsHelper(PermissionsHelper permissionsHelper) {
        _permissionsHelper = permissionsHelper;
    }

    /**
     * Setter for disabling license check
     * 
     * @param disableLicenseCheck
     */
    public void setDisableLicenseCheck(boolean disableLicenseCheck) {
        this.disableLicenseCheck = disableLicenseCheck;
    }

    public void setIsStandby(boolean isStandby) {
        this.isStandby = isStandby;
    }

    @Override
    protected boolean isSecurityDisabled() {
        return (_disabler != null);
    }

    @Override
    protected boolean isLicenseCheckDisabled() {
        return disableLicenseCheck;
    }

    @Override
    protected ResourceFilter getPreFilter() {
        return new StandbyApisvcFilter();
    }

    @Override
    protected ResourceFilter getPostFilter() {
        return null;
    }

    @Override
    protected AbstractPermissionFilter getPermissionsFilter(Role[] roles, ACL[] acls,
            boolean blockProxies, Class resourceClazz) {
        return new ApisvcPermissionFilter(roles, acls, blockProxies, resourceClazz, _permissionsHelper);
    }

    @Override
    protected AbstractLicenseFilter getLicenseFilter() {
        return new ApisvcLicenseFilter();
    }
    
    /**
     * Request filter for apisvc on standby node. We disable all post request except bulk API and DR API
     */
    private class StandbyApisvcFilter implements ResourceFilter, ContainerRequestFilter {
        @Override
        public ContainerRequest filter(ContainerRequest request) {
            // allow all request on active site
            // use a injected variable rather than querying with DrUtil every time
            // because if a ZK quorum is lost on the active site all the ZK accesses will fail
            // note that readonly mode is not enabled on the active site.
            if (!isStandby) {
                return request;
            }
            String path = request.getPath();
            // allow all requests for DR
            if (path.startsWith("site")) {
                return request;
            }
            String method = request.getMethod();
            // allow keystore related operation
            if (path.contains("keystore")) {
                return request;
            }
            // allow all GET request or bulk request
            if (method.equalsIgnoreCase("GET") || path.endsWith("/bulk")) {
                return request;
            }
            // disallowed operation
            String siteId = drUtil.getActiveSiteId();
            Site activeSite = drUtil.getSiteFromLocalVdc(siteId);
            throw APIException.forbidden.disallowOperationOnDrStandby(activeSite.getVip());
        }
        
        @Override
        public ContainerRequestFilter getRequestFilter() {
            return this;
        }

        @Override
        public ContainerResponseFilter getResponseFilter() {
            return null;
        }
    }
}
