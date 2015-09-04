/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.varray.NetworkCreate;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.varray.NetworkUpdate;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.exceptions.ViPRException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import controllers.Common;
import controllers.VirtualArrays;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;
import models.RegistrationStatus;
import models.TransportProtocols;
import models.datatable.NetworkEndpointDataTable;
import models.datatable.NetworkEndpointDataTable.EndpointInfo;
import models.datatable.NetworksDataTable;
import models.datatable.NetworksDataTable.NetworkInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Util;
import play.mvc.With;
import util.HostUtils;
import util.MessagesUtils;
import util.NetworkUtils;
import util.StoragePortUtils;
import util.StorageSystemUtils;
import util.VirtualArrayUtils;
import util.datatable.DataTablesSupport;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.backToReferrer;
import static controllers.Common.flashException;
import static util.BourneUtil.getViprClient;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class Networks extends ViprResourceController {
    protected static final String SAVED_SUCCESS = "Networks.save.success";
    protected static final String SAVED_ERROR = "Networks.save.error";
    protected static final String DELETED_SUCCESS = "Networks.delete.success";
    protected static final String DELETED_ERROR = "Networks.delete.error";
    protected static final String UNKNOWN = "Networks.unknown";

    private static final String VIRTUAL_ARRAY_PARAM = "virtualArrayId";

    /**
     * Handles an error while saving a network form.
     * 
     * @param network
     *            the network form.
     */
    private static void error(NetworkForm network) {
        params.flash();
        Validation.keep();
        edit(network.id, params.get(VIRTUAL_ARRAY_PARAM));
    }

    @FlashException("list")
    public static void createIpNetwork(String name, String virtualArrayId) {
        NetworkCreate param = new NetworkCreate();
        param.setLabel(name);
        param.setTransportType(TransportProtocols.IP);
        if (StringUtils.isNotBlank(virtualArrayId)) {
            param.setVarrays(uris(virtualArrayId));
        }

        NetworkRestRep network = NetworkUtils.create(param);
        edit(stringId(network), virtualArrayId);
    }

    /**
     * Displays a page for editing the given network.
     * 
     * @param id
     *            the network ID.
     * @param virtualArrayId
     *            the virtual array from which the edit request came.
     */
    public static void edit(String id, String virtualArrayId) {
        NetworkRestRep network = getNetwork(id);
        NetworkForm form = new NetworkForm();
        form.load(network);
        edit(form);
    }

    /**
     * Gets a network by ID, or flashes an error and goes back to the referring page or the list page if no referrer.
     * 
     * @param id
     *            the network ID.
     * @return the network.
     */
    @Util
    public static NetworkRestRep getNetwork(String id) {
        NetworkRestRep network = NetworkUtils.getNetwork(id);
        if (network == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            backToReferrer();
            list();
        }
        return network;
    }

    /**
     * Show the network editor.
     * 
     * @param network
     *            the network to edit.
     */
    private static void edit(NetworkForm network) {
        renderArgs.put("virtualArrayOptions", dataObjectOptions(VirtualArrayUtils.getVirtualArrays()));
        renderArgs.put(VIRTUAL_ARRAY_PARAM, params.get(VIRTUAL_ARRAY_PARAM));
        renderArgs.put("dataTable", NetworkEndpointDataTable.createDataTable(network.type));
        render("@edit", network);
    }

    /**
     * Creates and renders JSON datatable source for endpoints for the given network.
     * 
     * @param networkId
     *            the network ID.
     */
    public static void endpointsJson(String networkId) {
        NetworkRestRep network = NetworkUtils.getNetwork(networkId);
        if (network == null) {
            error(MessagesUtils.get(UNKNOWN, networkId));
        }

        List<EndpointInfo> items = Lists.newArrayList();

        // All known endpoints, remove endpoints from storage systems and hosts to get manual endpoints
        Set<String> endpoints = Sets.newTreeSet(new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return s1.compareToIgnoreCase(s2);
            }
        });
        endpoints.addAll(network.getEndpoints());

        // Add ports from storage systems
        CachedResources<StorageSystemRestRep> storageSystems = StorageSystemUtils.createCache();
        for (StoragePortRestRep storagePort : StoragePortUtils.getStoragePortsByNetwork(network.getId())) {
            items.add(new EndpointInfo(storagePort, storageSystems));
            for (String endpoint : NetworkUtils.getEndPoints(storagePort)) {
                endpoints.remove(endpoint);
            }
        }

        // Add ports from hosts
        CachedResources<HostRestRep> hosts = HostUtils.createCache();
        for (InitiatorRestRep initiator : NetworkUtils.getInitiators(network.getId())) {
            if (initiator.getHost() != null) {
                items.add(new EndpointInfo(initiator, hosts));
                endpoints.remove(NetworkUtils.getEndPoint(initiator));
            }
        }
        for (IpInterfaceRestRep ipInterface : NetworkUtils.getIpInterfaces(network.getId())) {
            if (ipInterface.getHost() != null) {
                items.add(new EndpointInfo(ipInterface, hosts));
                endpoints.remove(NetworkUtils.getEndPoint(ipInterface));
            }
        }

        // Add any remaining endpoints as 'manual'
        for (String endpoint : endpoints) {
            items.add(new EndpointInfo(endpoint));
        }

        setEndpointAttrs(network, items);
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    private static void setEndpointAttrs(NetworkRestRep network, List<EndpointInfo> endpoints) {
        Set<String> discovered = NetworkUtils.getDiscoveredEndpoints(network);
        Map<String, String> aliases = NetworkUtils.getAliases(network);
        for (EndpointInfo endpoint : endpoints) {
            endpoint.discovered = discovered.contains(endpoint.identifier);
            endpoint.alias = aliases.get(endpoint.identifier);
        }
    }

    /**
     * Saves a network and redirects back to the referring page.
     * 
     * @param network
     *            the network to save.
     */
    public static void save(NetworkForm network) {
        network.validate("network");
        if (Validation.hasErrors()) {
            error(network);
        }
        try {
            network.save();
            flash.success(MessagesUtils.get(SAVED_SUCCESS, network.name));
            String virtualArrayId = params.get(VIRTUAL_ARRAY_PARAM);
            if (StringUtils.isNotBlank(virtualArrayId)) {
                VirtualArrays.networks(virtualArrayId);
            }
            backToReferrer();
            list();
        } catch (ViPRException e) {
            flashException(e);
            error(network);
        }
    }

    /**
     * Lists all networks.
     */
    public static void list() {
        NetworksDataTable dataTable = new NetworksDataTable();
        render(dataTable);
    }

    /**
     * Retrieves all networks and renders them as JSON for a datatable.
     */
    public static void listJson() {
        // Creates a mapping of ID => virtual array name
        Map<URI, String> virtualArrays = ResourceUtils.mapNames(getViprClient().varrays().list());

        List<NetworkInfo> items = Lists.newArrayList();
        for (NetworkRestRep network : NetworkUtils.getNetworks()) {
            NetworkInfo info = new NetworkInfo(network);
            Set<String> varrayNames = getNames(virtualArrays, uris(network.getAssignedVirtualArrays()));
            info.virtualArrayNames = StringUtils.join(varrayNames, ",");
            items.add(info);
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    /**
     * Gets the set of names for the given IDs by looking up the values in the
     * map.
     * 
     * @param nameMap
     *            the map of ID->name
     * @param ids
     *            the collection of IDs.
     * @return the set of names.
     */
    private static Set<String> getNames(Map<URI, String> nameMap, Collection<URI> ids) {
        Set<String> names = Sets.newTreeSet();
        for (URI id : ids) {
            String name = nameMap.get(id);
            if (StringUtils.isNotBlank(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Deletes/deregisters the specified networks.
     * 
     * @param ids
     *            the IDs of the virtual arrays to delete/deregister.
     */
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    /**
     * Deletes/deregisters the specified networks and redirects back to the list page.
     * 
     * @param ids
     *            the list of IDs.
     */
    private static void delete(List<URI> ids) {
        performSuccessFail(NetworkUtils.getNetworks(ids), new DeactivateOperation(), DELETED_SUCCESS, DELETED_ERROR);
        list();
    }

    public static void register(@As(",") String[] ids) {
        registerNetworks(uris(ids));
        list();
    }

    @Util
    public static void registerNetworks(List<URI> ids) {
        if ((ids != null) && (ids.size() > 0)) {
            for (NetworkRestRep network : NetworkUtils.getNetworks(ids)) {
                if (RegistrationStatus.isUnregistered(network.getRegistrationStatus())) {
                    NetworkUtils.register(network.getId());
                }
            }
        }
    }

    public static void deregister(@As(",") String[] ids) {
        deregisterNetworks(uris(ids));
        list();
    }

    @Util
    public static void deregisterNetworks(List<URI> ids) {
        if ((ids != null) && (ids.size() > 0)) {
            for (NetworkRestRep network : NetworkUtils.getNetworks(ids)) {
                if (RegistrationStatus.isRegistered(network.getRegistrationStatus())) {
                    NetworkUtils.deregister(network.getId());
                }
            }
        }
    }

    public static void removePorts(String id, @As(",") String[] ids) {
        try {
            if (ids != null && ids.length > 0) {
                String[] decodeIds = decodeIds(ids);
                if (decodeIds.length > 0) {
                    List<String> hostEndpoints = NetworkUtils.getHostEndpoints(Arrays.asList(decodeIds));
                    if (hostEndpoints.size() > 0) {
                        NetworkUtils.removeEndpoints(id, hostEndpoints);
                    }
                    for (String storagePortId : NetworkUtils.getStoragePortEndpoints(Arrays.asList(decodeIds))) {
                        StoragePortUtils.unassign(storagePortId);
                    }
                }
            }

        } catch (Exception e) {
            flashException(e);
        }
        edit(id, params.get(VIRTUAL_ARRAY_PARAM));
    }

    private static String[] decodeIds(String[] ids) throws UnsupportedEncodingException {
        String[] decodedIds = new String[0];
        if (ids != null && ids.length > 0) {
            decodedIds = new String[ids.length];
            for (int i = 0; i < ids.length; i++) {
                decodedIds[i] = URLDecoder.decode(ids[i], "UTF-8");
            }
        }
        return decodedIds;
    }

    @FlashException(referrer = { "edit" })
    public static void addPorts(String id, String ports) {
        if (StringUtils.isNotBlank(ports)) {
            String[] values = ports.replaceAll("\r\n", "\n").split("\n");
            Set<String> endpoints = Sets.newHashSet();
            Set<String> invalidEndpoints = Sets.newHashSet();
            for (String value : values) {
                if (StringUtils.isNotBlank(value)) {
                    if (StringUtils.contains(value, ',')) {
                        invalidEndpoints.add(value);
                    } else {
                        endpoints.add(value);
                    }
                }
            }
            if (endpoints.size() > 0) {
                NetworkUtils.addEndpoints(id, endpoints);
            }
            if (invalidEndpoints.size() > 0) {
                flash.error(MessagesUtils.get("network.ports.add.error.invalid", invalidEndpoints));
            }
        }
        edit(id, params.get(VIRTUAL_ARRAY_PARAM));
    }

    @FlashException(referrer = { "edit" })
    public static void addHostPorts(String id, @As(",") String[] ids) {
        if (ids != null) {
            List<String> endpoints = NetworkUtils.getHostEndpoints(Arrays.asList(ids));
            if (!endpoints.isEmpty()) {
                NetworkUtils.addEndpoints(id, endpoints);
            }
        }
        edit(id, params.get(VIRTUAL_ARRAY_PARAM));
    }

    @FlashException(referrer = { "edit" })
    public static void addArrayPorts(String id, @As(",") String[] ids) {
        if (ids != null) {
            StoragePortUpdate update = new StoragePortUpdate();
            update.setNetwork(uri(id));
            for (String storagePortId : NetworkUtils.getStoragePortEndpoints(Arrays.asList(ids))) {
                StoragePortUtils.update(uri(storagePortId), update);
            }
        }
        edit(id, params.get(VIRTUAL_ARRAY_PARAM));
    }

    public static void availableHostEndpointsJson(String id) {
        NetworkRestRep network = NetworkUtils.getNetwork(id);
        CachedResources<HostRestRep> hosts = HostUtils.createCache();

        List<EndpointInfo> items = Lists.newArrayList();
        if (TransportProtocols.isIp(network.getTransportType())) {
            // Host IP interfaces not in the network
            for (IpInterfaceRestRep ipInterface : NetworkUtils.getEligibleIpInterfaces(network.getId())) {
                items.add(new EndpointInfo(ipInterface, hosts));
            }
        }
        // Host initiators not in the network
        Set<String> protocols = NetworkUtils.getSupportedProtocols(network);
        for (InitiatorRestRep initiator : NetworkUtils.getEligibleInitiators(network.getId(), protocols)) {
            items.add(new EndpointInfo(initiator, hosts));
        }
        setEndpointAttrs(network, items);
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    public static void availableArrayEndpointsJson(String id) {
        // Get the ports NOT in the specified network matching the network type
        NetworkRestRep network = NetworkUtils.getNetwork(id);
        StoragePortUtils.TransportTypeFilter transportTypeFilter = new StoragePortUtils.TransportTypeFilter(
                network.getTransportType());
        ResourceFilter<StoragePortRestRep> notInNetworkFilter = new StoragePortUtils.NetworkFilter(uri(id)).not();
        listArrayEndpointsJson(network, StoragePortUtils.getStoragePorts(transportTypeFilter.and(notInNetworkFilter)));
    }

    private static void listArrayEndpointsJson(NetworkRestRep network, List<StoragePortRestRep> ports) {
        CachedResources<StorageSystemRestRep> storageSystems = StorageSystemUtils.createCache();
        List<EndpointInfo> items = Lists.newArrayList();
        for (StoragePortRestRep port : ports) {
            items.add(new EndpointInfo(port, storageSystems));
        }
        setEndpointAttrs(network, items);
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    protected static class DeactivateOperation implements ResourceValueOperation<Void, NetworkRestRep> {
        @Override
        public Void performOperation(NetworkRestRep network) throws Exception {
            boolean registered = RegistrationStatus.isRegistered(network.getRegistrationStatus());
            if (registered) {
                NetworkUtils.deregister(network.getId());
            }
            // Only delete networks that are not discovered
            try {
                if (Boolean.FALSE.equals(network.getDiscovered())) {
                    NetworkUtils.deactivate(network.getId());
                }
            } catch (Exception e) {
                // If deactivate failed, re-register the network
                if (registered) {
                    NetworkUtils.register(network.getId());
                }
                throw e;
            }

            return null;
        }
    }

    /**
     * Network create/edit form.
     */
    public static class NetworkForm {
        public String id;
        public Set<String> virtualArrays;

        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;
        @Required
        public String type;
        public String fabricId;
        public boolean discovered;

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void load(NetworkRestRep network) {
            this.id = stringId(network);
            this.virtualArrays = network.getAssignedVirtualArrays();
            this.name = network.getName();
            this.type = network.getTransportType();
            this.fabricId = network.getFabricId();
            this.discovered = Boolean.TRUE.equals(network.getDiscovered());
        }

        public NetworkRestRep create() {
            NetworkCreate create = new NetworkCreate();
            create.setLabel(name);
            create.setTransportType(type);
            create.setVarrays(uris(virtualArrays));
            return NetworkUtils.create(create);
        }

        public NetworkRestRep update() {
            NetworkRestRep oldNetwork = NetworkUtils.getNetwork(id);
            NetworkUpdate update = new NetworkUpdate();
            update.setName(name);
            update.setVarrayChanges(getVirtualArrayChanges(oldNetwork));
            return NetworkUtils.update(uri(id), update);
        }

        private VirtualArrayAssignmentChanges getVirtualArrayChanges(NetworkRestRep oldNetwork) {
            Set<String> added = getAddedVirtualArrays(oldNetwork);
            Set<String> removed = getRemovedVirtualArrays(oldNetwork);

            VirtualArrayAssignmentChanges changes = new VirtualArrayAssignmentChanges();
            if (added.size() > 0) {
                changes.setAdd(new VirtualArrayAssignments(added));
            }
            if (removed.size() > 0) {
                changes.setRemove(new VirtualArrayAssignments(removed));
            }

            return changes;
        }

        @SuppressWarnings("unchecked")
        private Set<String> getAddedVirtualArrays(NetworkRestRep oldNetwork) {
            Set<String> oldVirtualArrays = defaultSet(oldNetwork.getAssignedVirtualArrays());
            Set<String> newVirtualArrays = defaultSet(virtualArrays);
            return Sets.newHashSet(CollectionUtils.subtract(newVirtualArrays, oldVirtualArrays));
        }

        @SuppressWarnings("unchecked")
        private Set<String> getRemovedVirtualArrays(NetworkRestRep oldNetwork) {
            Set<String> oldVirtualArrays = defaultSet(oldNetwork.getAssignedVirtualArrays());
            Set<String> newVirtualArrays = defaultSet(virtualArrays);
            return Sets.newHashSet(CollectionUtils.subtract(oldVirtualArrays, newVirtualArrays));
        }

        private static <T> Set<T> defaultSet(Set<T> set) {
            if (set == null) {
                set = Collections.emptySet();
            }
            return set;
        }

        public NetworkRestRep save() {
            if (isNew()) {
                return create();
            }
            else {
                return update();
            }
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
        }
    }
}
