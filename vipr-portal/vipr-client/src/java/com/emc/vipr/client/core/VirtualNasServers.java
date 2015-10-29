package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.project.VirtualNasParam;
import com.emc.storageos.model.vnas.VirtualNASBulkRep;
import com.emc.storageos.model.vnas.VirtualNASList;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * vNAS Servers resources.
 * <p>
 * Base URL: <tt>/vdc/vnasservers/</tt>
 *
 */
public class VirtualNasServers extends AbstractCoreBulkResources<VirtualNASRestRep> implements
        TopLevelResources<VirtualNASRestRep> {

    public VirtualNasServers(ViPRCoreClient parent, RestClient client) {
        super(parent, client, VirtualNASRestRep.class, PathConstants.VIRTUAL_NAS_SERVER_URL);

    }


    /**
     * Gets a list of vNAS Servers references from the given path.
     * 
     * @param path
     *            the path to get.
     * @param args
     *            the path arguments.
     * @return the list of vNAS Servers references.
     */
    protected List<NamedRelatedResourceRep> getList(String path, Object... args) {
        VirtualNASList response = client.get(VirtualNASList.class, path, args);
        return ResourceUtils.defaultList(response.getVNASServers());
    }

    /**
     * Lists all vNAS Servers.
     * <p>
     * API Call: <tt>GET /vdc/vnasservers</tt>
     * 
     * @return the list of vNAS Server references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        return getList(baseUrl);
    }


    /**
     * Gets a list of all vNAS Servers. This is a convenience method for <tt>getByRefs(list())</tt>.
     * 
     * @return the list of all vNAS Servers.
     */
    @Override
    public List<VirtualNASRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets a list of all vNAS Servers, optionally filtering the results. This is a convenience method for
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of all vNAS Servers.
     */
    @Override
    public List<VirtualNASRestRep> getAll(
            ResourceFilter<VirtualNASRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

	@Override
	protected List<VirtualNASRestRep> getBulkResources(BulkIdParam input) {
		VirtualNASBulkRep response = client.post(VirtualNASBulkRep.class, input, getBulkUrl());
        return defaultList(response.getVnasServers());
	}
	
	/**
     * Lists the vNAS servers for the given storage system by ID.
     * <p>
     * API Call: <tt>GET /vdc/storage-systems/{storage-system-id}/vnas-servers</tt>
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @return the list of vNAS server references.
     */
    public List<NamedRelatedResourceRep> listByStorageSystem(URI storageSystemId) {
        return getList(PathConstants.VIRTUAL_NAS_SERVER_BY_STORAGE_SYSTEM_URL, storageSystemId);
    }
    
    /**
     * Gets the list of vNAS servers for the given storage system by ID. This is a convenience method for:
     * <tt>getByRefs(listByStorageSystem(storageSystemId))</tt>.
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @return the list of vNAS servers.
     */
    public List<VirtualNASRestRep> getByStorageSystem(URI storageSystemId) {
        return getByStorageSystem(storageSystemId, null);
    }
    /**
     * Gets the list of vNAS servers for the given storage system by ID, optionally filtering the results. This is a
     * convenience method for: <tt>getByRefs(listByStorageSystem(storageSystemId), filter)</tt>.
     * 
     * @param storageSystemId
     *            the ID of the storage system.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of vNAS servers.
     */
    public List<VirtualNASRestRep> getByStorageSystem(URI storageSystemId, ResourceFilter<VirtualNASRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByStorageSystem(storageSystemId);
        return getByRefs(refs, filter);
    }
    
    /**
     * Lists the vNAS servers for the given virtual array by ID.
     * <p>
     * API Call: <tt>GET /vdc/vnasservers/varray/{varray-id}</tt>
     * 
     * @param virtualArrayId
     *            the ID of the virtual array.
     * @return the list of vNAS server references.
     */
    public List<NamedRelatedResourceRep> listByVirtualArray(URI virtualArrayId) {
        return getList(PathConstants.VIRTUAL_NAS_SERVER_BY_VARRAY_URL, virtualArrayId);
    }

    /**
     * Gets the list of vNAS servers for the given virtual array by ID. This is a convenience method for:
     * <tt>getByRefs(listByVirtualArray(virtualArrayId))</tt>
     * 
     * @param virtualArrayId
     *            the ID of the virtual array.
     * @return the list of vNAS servers.
     */
    public List<VirtualNASRestRep> getByVirtualArray(URI virtualArrayId) {
        return getByVirtualArray(virtualArrayId, null);
    }

    /**
     * Gets the list of vNAS servers for the given virtual array by ID, optionally filtering the results. This is a
     * convenience method for: <tt>getByRefs(listByVirtualArray(virtualArrayId), filter)</tt>
     * 
     * @param virtualArrayId
     *            the ID of the virtual array.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of vNAS servers.
     */
    public List<VirtualNASRestRep> getByVirtualArray(URI virtualArrayId, ResourceFilter<VirtualNASRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByVirtualArray(virtualArrayId);
        return getByRefs(refs, filter);
    }
    
    
    /**
     * Gets the base URL for assigning vNas servers to a Project: <tt>/projects/{project_id}/assign-vnas-servers</tt>
     * 
     * @return the assign vnas servers URL.
     */
    protected String getVnasAssignUrl() {
        return "/projects/{project_id}/assign-vnas-servers";
    }
    
    /**
     * Gets the base URL for assigning vNas servers to a Project: <tt>/projects/{project_id}/unassign-vnas-servers</tt>
     * 
     * @return the assign vnas servers URL.
     */
    protected String getVnasUnAssignUrl() {
        return "/projects/{project_id}/unassign-vnas-servers";
    }

    
    /**
     * This method assign vNas Servers to a project.
     * PUT /projects/{project_id}/assign-vnas-servers
     * 
     * @param projectId
     * @param vNasParam
     */
    public void assignVnasServers(URI projectId, VirtualNasParam vNasParam) {

        client.put(String.class, vNasParam, getVnasAssignUrl(), projectId);

    }
    
    /**
     * This method unassign vNas Servers from a project.
     * PUT /projects/{project_id}/unassign-vnas-servers
     * 
     * @param projectId
     * @param vNasParam
     */
    public void unassignVnasServers(URI projectId, VirtualNasParam vNasParam) {
        client.put(String.class, vNasParam, getVnasUnAssignUrl(), projectId);
    }

}
