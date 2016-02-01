/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.compute.ComputeSystemBulkRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.varray.AttributeList;
import com.emc.storageos.model.varray.VArrayAttributeList;
import com.emc.storageos.model.varray.VirtualArrayBulkRep;
import com.emc.storageos.model.varray.VirtualArrayConnectivityList;
import com.emc.storageos.model.varray.VirtualArrayConnectivityRestRep;
import com.emc.storageos.model.varray.VirtualArrayCreateParam;
import com.emc.storageos.model.varray.VirtualArrayList;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.varray.VirtualArrayUpdateParam;
import com.emc.storageos.model.vpool.VDCCapacities;
import com.emc.storageos.model.vpool.VirtualArrayVirtualPoolCapacity;
import com.emc.storageos.model.vpool.VirtualPoolAvailableAttributesResourceRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.impl.SearchConstants;
import com.emc.vipr.client.core.search.VirtualArraySearchBuilder;
import com.emc.vipr.client.core.util.RelatedResourceComparator;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * Virtual Arrays resources.
 * <p>
 * Base URL: <tt>/vdc/varrays</tt>
 */
public class VirtualArrays extends AbstractCoreBulkResources<VirtualArrayRestRep> implements
        TopLevelResources<VirtualArrayRestRep>, ACLResources {
    public VirtualArrays(ViPRCoreClient parent, RestClient client) {
        super(parent, client, VirtualArrayRestRep.class, PathConstants.VARRAY_URL);
    }

    @Override
    public VirtualArrays withInactive(boolean inactive) {
        return (VirtualArrays) super.withInactive(inactive);
    }

    @Override
    public VirtualArrays withInternal(boolean internal) {
        return (VirtualArrays) super.withInternal(internal);
    }

    @Override
    protected List<VirtualArrayRestRep> getBulkResources(BulkIdParam input) {
        VirtualArrayBulkRep response = client.post(VirtualArrayBulkRep.class, input, getBulkUrl());
        return defaultList(response.getVirtualArrays());
    }

    /**
     * Lists all virtual arrays.
     * <p>
     * API Call: <tt>GET /vdc/varrays</tt>
     * 
     * @return the list of virtual array references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        VirtualArrayList response = client.get(VirtualArrayList.class, baseUrl);
        return ResourceUtils.defaultList(response.getVirtualArrays());
    }

    /**
     * Lists all virtual arrays in a virtual data center
     * <p>
     * API Call: <tt>GET /vdc/varrays</tt>
     * 
     * @return the list of virtual array references in a virtual data centers
     */
    public List<NamedRelatedResourceRep> listByVDC(String shortVdcId) {
        UriBuilder builder = client.uriBuilder(baseUrl);
        builder.queryParam(SearchConstants.VDC_ID_PARAM, shortVdcId);
        VirtualArrayList response = client.getURI(VirtualArrayList.class, builder.build());
        return ResourceUtils.defaultList(response.getVirtualArrays());
    }

    /**
     * Gets the list of all virtual arrays. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @return the list of virtual arrays.
     */
    @Override
    public List<VirtualArrayRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all virtual arrays, optionally filtering the results. This is a convenience method for:
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of virtual arrays.
     */
    @Override
    public List<VirtualArrayRestRep> getAll(ResourceFilter<VirtualArrayRestRep> filter) {
        return getByRefs(list(), filter);
    }

    /**
     * Creates a virtual array.
     * <p>
     * API Call: <tt>POST /vdc/varrays</tt>
     * 
     * @param input
     *            the create configuration.
     * @return the newly create virtual array.
     */
    public VirtualArrayRestRep create(VirtualArrayCreateParam input) {
        return client.post(VirtualArrayRestRep.class, input, baseUrl);
    }

    /**
     * Updated the given virtual array by ID.
     * <p>
     * API Call: <tt>PUT /vdc/varrays/{id}</tt>
     * 
     * @param id
     *            the ID of the virtual array to update.
     * @param input
     *            the update configuration.
     * @return the updated virtual array.
     */
    public VirtualArrayRestRep update(URI id, VirtualArrayUpdateParam input) {
        return client.put(VirtualArrayRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given virtual array by ID.
     * <p>
     * API Call: <tt>POST /vdc/varrays/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the virtual array to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    public void deactivate(VirtualArrayRestRep virtualArray) {
        deactivate(ResourceUtils.id(virtualArray));
    }

    /**
     * Gets the available attributes for the given virtual array.
     * <p>
     * API Call: <tt>GET /vdc/varrays/{id}/available-attributes</tt>
     * 
     * @param id
     *            the ID of the virtual array.
     * @return the list of available attributes.
     */
    public List<VirtualPoolAvailableAttributesResourceRep> getAvailableAttributes(URI id) {
        AttributeList response = client.get(AttributeList.class, getIdUrl() + "/available-attributes", id);
        return defaultList(response.getAttributes());
    }

    /**
     * Gets the available attributes for the given virtual arrays.
     * <p>
     * API Call: <tt>GET /vdc/varrays/available-attributes</tt>
     * 
     * @param virtualArrayIds
     *            the IDs of the virtual arrays.
     * @return the list of available attributes.
     */
    public Map<URI, List<VirtualPoolAvailableAttributesResourceRep>> getAvailableAttributes(Collection<URI> virtualArrayIds) {
        BulkIdParam input = new BulkIdParam((List<URI>) virtualArrayIds);
        VArrayAttributeList response = client.post(VArrayAttributeList.class, input, PathConstants.AVAILABLE_ATTRIBUTES_FOR_ALL_VARRAY);

        Map<URI, List<VirtualPoolAvailableAttributesResourceRep>> availableAttributes = new HashMap<URI, List<VirtualPoolAvailableAttributesResourceRep>>();
        for (AttributeList attributes : response.getAttributes()) {
            availableAttributes.put(attributes.getVArrayId(), attributes.getAttributes());
        }

        return availableAttributes;
    }

    /**
     * Gets the connectivity information for the given virtual array by ID.
     * <p>
     * API Call: <tt>GET /vdc/varrays/{id}/connectivity</tt>
     * 
     * @param id
     *            the ID of the virtual array.
     * @return the list of connectivity information.
     */
    public List<VirtualArrayConnectivityRestRep> getConnectivity(URI id) {
        VirtualArrayConnectivityList response = client.get(VirtualArrayConnectivityList.class, getIdUrl()
                + "/connectivity", id);
        return defaultList(response.getConnections());
    }

    @Override
    public List<ACLEntry> getACLs(URI id) {
        return doGetACLs(id);
    }

    @Override
    public List<ACLEntry> updateACLs(URI id, ACLAssignmentChanges aclChanges) {
        return doUpdateACLs(id, aclChanges);
    }

    /**
     * Creates a search builder specific for building virtual array searches.
     * 
     * @return the virtual array seach builder.
     */
    @Override
    public VirtualArraySearchBuilder search() {
        return new VirtualArraySearchBuilder(this);
    }

    /**
     * Finds the list of virtual arrays connected to the given initiator port. This is a convenience method for:
     * <tt>search().byInitiatorPort(initiatorPort).run()</tt>
     * 
     * @param initiatorPort
     *            the initiator port.
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByInitiatorPort(String initiatorPort) {
        return search().byInitiatorPort(initiatorPort).run();
    }

    /**
     * Finds the list of virtual arrays connected to the given initiator port, optionally filtering the results. This is
     * a convenience method for <tt>search().byInitiatorPort(initiatorPort).filter(filter).run()</tt>
     * 
     * @param initiatorPort
     *            the initiator port.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByInitiatorPort(String initiatorPort,
            ResourceFilter<VirtualArrayRestRep> filter) {
        return search().byInitiatorPort(initiatorPort).filter(filter).run();
    }

    /**
     * Finds the list of virtual arrays connected to any of the given initiators.
     * 
     * @param initiators
     *            the initiators.
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByInitiators(Collection<InitiatorRestRep> initiators) {
        return findByInitiators(initiators, null);
    }

    /**
     * Finds the list of virtual arrays connected to any of the given initiators, optionally filtering the results.
     * 
     * @param initiators
     *            the initiators.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByInitiators(Collection<InitiatorRestRep> initiators,
            ResourceFilter<VirtualArrayRestRep> filter) {
        // Collect unique virtual array refs before fetching
        Set<RelatedResourceRep> virtualArrayRefs = new TreeSet<RelatedResourceRep>(new RelatedResourceComparator());
        for (InitiatorRestRep initiator : initiators) {
            String port = initiator.getInitiatorPort();
            virtualArrayRefs.addAll(performSearchBy(SearchConstants.INITIATOR_PORT_PARAM, port));
        }
        return getByRefs(virtualArrayRefs, filter);
    }

    /**
     * Finds the list of virtual arrays connected to the given host.
     * 
     * @param host
     *            the host.
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByConnectedHost(HostRestRep host) {
        return findByConnectedHost(ResourceUtils.id(host), null);
    }

    /**
     * Finds the list of virtual arrays connected to the given host, optionally filtering the results.
     * 
     * @param host
     *            the host.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByConnectedHost(HostRestRep host, ResourceFilter<VirtualArrayRestRep> filter) {
        return findByConnectedHost(ResourceUtils.id(host), filter);
    }

    /**
     * Finds the list of virtual arrays connected to the given host by ID. This is a convenience method for:
     * <tt>search().byHost(hostId).run()</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByConnectedHost(URI hostId) {
        return search().byHost(hostId).run();
    }

    /**
     * Finds the list of virtual arrays connected to the given host by ID, optionally filtering the results. This is
     * a convenience method for <tt>search().byHost(hostId).filter(filter).run()</tt>
     * 
     * @param hostId
     *            the ID of the host.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByConnectedHost(URI hostId, ResourceFilter<VirtualArrayRestRep> filter) {
        return search().byHost(hostId).filter(filter).run();
    }

    /**
     * Finds the list of virtual arrays connected to the given cluster by ID. This is a convenience method for:
     * <tt>search().byCluster(clusterId).run()</tt>
     * 
     * @param clusterId
     *            the ID of the cluster.
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByConnectedCluster(URI clusterId) {
        return search().byCluster(clusterId).run();
    }

    /**
     * Finds the list of virtual arrays connected to the given cluster by ID, optionally filtering the results. This is
     * a convenience method for <tt>search().byCluster(clusterId).filter(filter).run()</tt>
     * 
     * @param clusterId
     *            the ID of the cluster.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of connected virtual arrays.
     */
    public List<VirtualArrayRestRep> findByConnectedCluster(URI clusterId, ResourceFilter<VirtualArrayRestRep> filter) {
        return search().byCluster(clusterId).filter(filter).run();
    }

    /**
     * Gets the list of virtual pool capacities on all virtual arrays.
     * <p>
     * API Call: <tt>GET /vdc/capacities</tt>
     * 
     * @return the list of virtual pool capacities.
     */
    public List<VirtualArrayVirtualPoolCapacity> getCapacities() {
        VDCCapacities response = client.get(VDCCapacities.class, PathConstants.VDC_URL + "/capacities");
        return defaultList(response.getArrayCapacities());
    }

    /**
     * Gets the list of Compute Systems that are visible in this vArray
     * 
     * @param id the ID of the virtual array
     * @return List of Compute Systems that belong to the vArray
     */
    public List<ComputeSystemRestRep> getComputeSystems(URI id) {
        ComputeSystemBulkRep response = client.get(ComputeSystemBulkRep.class, getIdUrl()
                + "/compute-systems", id);
        return defaultList(response.getComputeSystems());
    }
}
