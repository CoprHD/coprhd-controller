/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.response;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProjectResource;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;
import com.google.common.base.Function;

/**
 * Iterator based list of resources
 */
public class BulkList<T> implements List<T> {

    private static final Logger _log = LoggerFactory.getLogger(BulkList.class);

    private Iterator<T> _iterator;

    public BulkList() {

    }

    public static <E extends DataObject, T> BulkList<T> wrapping(Iterator<E> dbIterator, Function<E, T> adapter, ResourceFilter<E> filter) {
        BulkList<T> list = new BulkList<T>();
        list.setIterator(new AdaptingIterator<E, T>(dbIterator, adapter, filter));
        return list;
    }

    public static <E extends DataObject, T> BulkList<T> wrapping(Iterator<E> dbIterator, Function<E, T> adapter) {
        return wrapping(dbIterator, adapter, new ResourceFilter<E>());
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
        return _iterator;
    }

    public void setIterator(Iterator<T> iterator) {
        _iterator = iterator;
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    /**
     * An iterator to create any type of element from a DataObject iterator. This is based
     * on the original RepFilteringIterator implementation but allows:
     * - Arbitrary function to define mapping.
     * - No reflect for construction. Works with any object type
     * - Same class allows filtering or not
     */
    public static class AdaptingIterator<E extends DataObject, T> implements Iterator<T> {
        private final Iterator<E> dbIterator;
        private final Function<E, T> adapter;
        private ResourceFilter<E> filter = null;

        E _next = null;

        public AdaptingIterator(Iterator<E> dbIterator, Function<E, T> adapter, ResourceFilter<E> filter) {
            this.dbIterator = dbIterator;
            this.adapter = adapter;
            this.filter = filter;
        }

        @Override
        public boolean hasNext() {
            if (null == _next) {
                while (dbIterator.hasNext()) {
                    E element = dbIterator.next();
                    if (filter == null || filter.isExposed(element)) {
                        _next = element;
                        break;
                    }
                }
            }
            return _next != null;
        }

        @Override
        public T next() {
            E next = null;
            T ret = null;

            if (_next != null) {
                next = _next;
            } else {
                if (hasNext()) {
                    next = _next;
                }
            }

            if (next != null) {
                ret = adapter.apply(next);
                _next = null;
            }
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Base ResourceFilter which only performs non-user/role specific exclusions
     */
    public static class ResourceFilter<E extends DataObject> {
        /**
         * check whether to expose the specified resource
         * 
         * The base implementation of this method only excludes
         * objects with the NO_PUBLIC_ACCESS flag
         * 
         * @param resource the resource to be checked upon.
         * @return true if the object should be exposed
         */
        public boolean isExposed(E resource) {
            return !resource.checkInternalFlags(Flag.NO_PUBLIC_ACCESS);
        }
    }

    /**
     * Abstract ResourceFilter to assist with performing user/role based filtering
     */
    public static abstract class PermissionsEnforcingResourceFilter<E extends DataObject> extends ResourceFilter<E> {
        protected PermissionsHelper _permissionsHelper;
        protected StorageOSUser _user;

        private final ResourceFilteringCache _cache = new ResourceFilteringCache();

        protected PermissionsEnforcingResourceFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            _user = user;
            _permissionsHelper = permissionsHelper;
        }

        /**
         * check whether to expose the specified resource
         * 
         * Overrides the default implmentation to require both the
         * absence of the NO_PUBLIC_ACCESS flag, and a true result
         * from isAccessible(). Subclasses must provide implementations
         * of isAccessible() which reflect the appropriate tenant, project,
         * or other appliciable permissions.
         */
        @Override
        public boolean isExposed(E resource) {
            return super.isExposed(resource) && isAccessible(resource);
        }

        /**
         * Subclasses must implement this method in such a way that it
         * returns true only if the current user should have access to
         * the resource
         * 
         * @param resource the resource to be checked upon.
         * @return true if the user has the appropriate permissions
         */
        protected abstract boolean isAccessible(E resource);

        /**
         * verify whether the user in the filter has access to tenant
         * 
         * @param tenant the tenant to be checked upon.
         * @return true if user can access the tenant.
         */
        protected boolean isTenantAccessible(URI tenant) {
            if (tenant == null) {
                return false;
            }
            if (_cache._accessibleParentResources.contains(tenant)) {
                return true;
            }
            if (_cache._nonAccessibleParentResources.contains(tenant)) {
                return false;
            }

            boolean ret =
                    _permissionsHelper.userHasGivenRole(
                            _user, tenant, Role.TENANT_ADMIN, Role.SECURITY_ADMIN);
            if (ret) {
                _cache._accessibleParentResources.add(tenant);
            } else {
                _cache._nonAccessibleParentResources.add(tenant);
            }
            return ret;
        }

        /**
         * verify whether the user in the filter has access to the project
         * 
         * @param project the project to be checked upon.
         * @return true if user can access the project.
         */
        protected boolean isProjectAccessible(URI project) {
            if (project == null) {
                return false;
            }
            if (_cache._accessibleParentResources.contains(project)) {
                return true;
            }
            if (_cache._nonAccessibleParentResources.contains(project)) {
                return false;
            }

            boolean ret = _permissionsHelper.userHasGivenACL(
                    _user, project, ACL.ANY);
            if (ret) {
                _cache._accessibleParentResources.add(project);
            } else {
                _cache._nonAccessibleParentResources.add(project);
            }

            return ret;
        }

    }

    public static abstract class TenantResourceFilter<E extends DataObject> extends PermissionsEnforcingResourceFilter<E> {
        protected TenantResourceFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        protected boolean isTenantResourceAccessible(URI tenantId) {
            boolean ret = false;
            ret = tenantId.toString().equals(_user.getTenantId());
            if (!ret) {
                ret = _permissionsHelper.userHasGivenRole(
                        _user, null, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN);
            }
            if (!ret) {
                ret = isTenantAccessible(tenantId);
            }
            return ret;
        }
    }

    public static class ProjectResourceFilter<E extends DataObject & ProjectResource>
            extends PermissionsEnforcingResourceFilter<E> {

        public ProjectResourceFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resource) {
            boolean ret = false;
            ret = isTenantAccessible(resource.getTenant().getURI());
            if (!ret) {
                NamedURI proj = resource.getProject();
                if (proj != null) {
                    ret = isProjectAccessible(proj.getURI());
                }
            }
            return ret;
        }

    }

    public static class ResourceFilteringCache {
        public HashSet<URI> _accessibleParentResources = new HashSet<URI>();
        public HashSet<URI> _nonAccessibleParentResources = new HashSet<URI>();
    }

    public static class VirtualArrayACLFilter
            extends PermissionsEnforcingResourceFilter<VirtualArray> {

        public VirtualArrayACLFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(VirtualArray resource) {
            return isNeighborhoodAccessible(resource);
        }

        /**
         * verify whether the user in the filter has access to the neighbor
         * based on resource ACL
         * 
         * @return true if user can access the resource.
         */
        private boolean isNeighborhoodAccessible(VirtualArray resource) {
            return _permissionsHelper.tenantHasUsageACL(
                    URI.create(_user.getTenantId()), resource);
        }
    }

    public static class ProjectFilter
            extends PermissionsEnforcingResourceFilter<Project> {

        public ProjectFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(Project resource) {
            boolean ret = false;
            ret = isTenantAccessible(resource.getTenantOrg().getURI());
            if (!ret) {
                return ret = _permissionsHelper.userHasGivenACL(
                        _user, resource.getId(), ACL.ANY);
            }
            return ret;
        }

    }

    public static class TenantFilter
            extends PermissionsEnforcingResourceFilter<TenantOrg> {

        public TenantFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(TenantOrg resource) {
            return _permissionsHelper.userHasGivenRole(
                    _user, resource.getId(), Role.TENANT_ADMIN, Role.SECURITY_ADMIN,
                    Role.SYSTEM_ADMIN);
        }
    }

    public static class HostFilter
            extends TenantResourceFilter<Host> {

        public HostFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(Host resource) {
            if (NullColumnValueGetter.isNullURI(resource.getTenant())) {
                return false;
            }
            return isTenantResourceAccessible(resource.getTenant());
        }
    }

    public static class HostInterfaceFilter
            extends PermissionsEnforcingResourceFilter<HostInterface> {

        public HostInterfaceFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(HostInterface resource) {
            if (_permissionsHelper.userHasGivenRole(
                    _user, null, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN)) {
                return true;
            }
            if (resource.getHost() == null) {
                return false;
            }
            boolean ret = false;
            Host host = _permissionsHelper.getObjectById(resource.getHost(), Host.class, true);
            ret = host.getTenant().toString().equals(_user.getTenantId());
            if (!ret) {
                ret = isTenantAccessible(host.getTenant());
            }
            return ret;
        }
    }

    public static class ClusterFilter
            extends TenantResourceFilter<Cluster> {

        public ClusterFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(Cluster resource) {
            if (NullColumnValueGetter.isNullURI(resource.getTenant())) {
                return false;
            }
            return isTenantResourceAccessible(resource.getTenant());
        }
    }

    public static class VcenterFilter
            extends TenantResourceFilter<Vcenter> {

        public VcenterFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(Vcenter resource) {
            if (_permissionsHelper.userHasGivenRole(_user, null,
                    Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN)) {
                return true;
            }

            Set<URI> tenantIds = _permissionsHelper.getUsageURIsFromAcls(resource.getAcls());
            if (CollectionUtils.isEmpty(tenantIds)) {
                return false;
            }

            Iterator<URI> uriIterator = tenantIds.iterator();
            while (uriIterator.hasNext()) {
                if (isTenantResourceAccessible(uriIterator.next())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected boolean isTenantResourceAccessible(URI tenantId) {
            if (tenantId.toString().equals(_user.getTenantId())) {
                return true;
            }

            return isTenantAccessible(tenantId);
        }
    }

    public static class VcenterDataCenterFilter
            extends TenantResourceFilter<VcenterDataCenter> {

        public VcenterDataCenterFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(VcenterDataCenter resource) {
            if (_permissionsHelper.userHasGivenRole(_user, null,
                    Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN)) {
                return true;
            }

            if (NullColumnValueGetter.isNullURI(resource.getTenant())) {
                return false;
            }
            return isTenantResourceAccessible(resource.getTenant());
        }
    }

    public static class VirtualPoolFilter extends PermissionsEnforcingResourceFilter<VirtualPool> {
        Type vpoolType;

        public VirtualPoolFilter(Type vpoolType) {
            super(null, null);
            this.vpoolType = vpoolType;
        }

        public VirtualPoolFilter(Type vpoolType,
                StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
            this.vpoolType = vpoolType;
        }

        @Override
        public boolean isAccessible(VirtualPool resource) {
            if (resource.getType().equals(vpoolType.name())) {
                if (_user != null) {
                    return isVpoolAccessible(resource);
                } else {
                    return true;
                }
            }
            return false;
        }

        /**
         * verify whether the user in the filter has access to the vpool
         * based on resource ACL
         * 
         * @return true if user can access the resource.
         */
        private boolean isVpoolAccessible(VirtualPool resource) {
            return _permissionsHelper.tenantHasUsageACL(
                    URI.create(_user.getTenantId()), resource);
        }

    }

    public static class ComputeVirtualPoolFilter extends PermissionsEnforcingResourceFilter<ComputeVirtualPool> {
        Type vpoolType;

        public ComputeVirtualPoolFilter() {
            super(null, null);
        }

        public ComputeVirtualPoolFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(ComputeVirtualPool resource) {
            if (_user != null) {
                return isComputeVirtualPoolAccessible(resource);
            } else {
                return true;
            }
        }

        /**
         * verify whether the user in the filter has access to the vcpool
         * based on resource ACL
         * 
         * @return true if user can access the resource.
         */
        private boolean isComputeVirtualPoolAccessible(ComputeVirtualPool resource) {
            if (_permissionsHelper.userHasGivenRole(_user, null, Role.SYSTEM_ADMIN) ||
                    _permissionsHelper.userHasGivenRole(_user, null, Role.SYSTEM_MONITOR)) {
                return true;
            }
            return _permissionsHelper.tenantHasUsageACL(
                    URI.create(_user.getTenantId()), resource);
        }

    }

    /**
     * Used to control access to migrations for bulk requests. Essentially
     * a project resource filter, but the migration itself is not a project
     * resource. The project for a migration is the project for the volume
     * being migrated.
     */
    public static class MigrationFilter extends PermissionsEnforcingResourceFilter<Migration> {

        /**
         * Default constructor
         */
        public MigrationFilter() {
            super(null, null);
        }

        /**
         * Parameter constructor.
         * 
         * @param user User requesting access to a migration.
         * @param permissionsHelper Reference to the permissions helper.
         */
        public MigrationFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isAccessible(Migration resource) {
            if (_user != null) {
                return isUserAuthorizedForMigration(resource, _user, _permissionsHelper);
            } else {
                return true;
            }
        }

        /**
         * Determines if the user is authorized for the passed migration. User
         * is assumed to be a tenant admin, system admin, or system monitor.
         * 
         * @param migration A reference to the migration.
         * @param user A reference to the user.
         * @param permissionsHelper A reference to a permissions helper.
         * 
         * @return true if the user is authorized, false otherwise.
         */
        public static boolean isUserAuthorizedForMigration(Migration migration,
                StorageOSUser user, PermissionsHelper permissionsHelper) {
            URI volumeURI = migration.getVolume();
            Volume volume = permissionsHelper.getObjectById(volumeURI, Volume.class);
            URI projectURI = volume.getProject().getURI();
            Project project = permissionsHelper.getObjectById(projectURI, Project.class);
            if ((permissionsHelper.userHasGivenRole(user,
                    project.getTenantOrg().getURI(), Role.TENANT_ADMIN))
                    || (permissionsHelper.userHasGivenACL(user, project.getId(), ACL.OWN,
                            ACL.ALL))) {
                return true;
            } else {
                return false;
            }
        }
    }

    public static class TaskFilter extends PermissionsEnforcingResourceFilter<Task> {

        public TaskFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        protected boolean isAccessible(Task resource) {
            return true;
        }
    }

    public static class UserGroupFilter
            extends PermissionsEnforcingResourceFilter<UserGroup> {

        public UserGroupFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(UserGroup resource) {
            return (_permissionsHelper.userHasGivenRoleInAnyTenant(_user, Role.SECURITY_ADMIN, Role.TENANT_ADMIN) || _permissionsHelper
                    .userHasGivenProjectACL(_user, ACL.OWN));
        }
    }

    public static class SchedulePolicyFilter
            extends PermissionsEnforcingResourceFilter<SchedulePolicy> {

        public SchedulePolicyFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(SchedulePolicy resource) {
            return _permissionsHelper.userHasGivenRole(
                    _user, resource.getId(), Role.TENANT_ADMIN, Role.SYSTEM_MONITOR);
        }
    }
}
