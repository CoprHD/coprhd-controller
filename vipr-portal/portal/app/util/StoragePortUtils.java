/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.NULL_URI;
import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.ids;
import static com.emc.vipr.client.core.util.ResourceUtils.asString;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.ports.StoragePortRequestParam;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.vipr.client.core.filters.DefaultResourceFilter;
import com.emc.vipr.client.core.filters.IdFilter;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.exceptions.ViPRHttpException;

public class StoragePortUtils {

    public static StoragePortRestRep getStoragePort(String id) {
        return getStoragePort(uri(id));
    }

    public static StoragePortRestRep getStoragePort(URI id) {
        try {
            return getViprClient().storagePorts().get(id);
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<StoragePortRestRep> getStoragePorts(Collection<URI> ids) {
        return getViprClient().storagePorts().getByIds(ids);
    }

    public static List<StoragePortRestRep> getStoragePorts() {
        return getViprClient().storagePorts().getAll();
    }

    public static List<StoragePortRestRep> getStoragePorts(ResourceFilter<StoragePortRestRep> filter) {
        return getViprClient().storagePorts().getAll(filter);
    }

    public static List<StoragePortRestRep> getStoragePorts(String storageSystemId) {
        return getStoragePortsByStorageSystem(uri(storageSystemId));
    }

    public static List<StoragePortRestRep> getStoragePortsByStorageSystem(URI storageSystemId) {
        return getViprClient().storagePorts().getByStorageSystem(storageSystemId);
    }

    public static List<StoragePortRestRep> getStoragePortsByNetwork(URI networkId) {
        return getViprClient().storagePorts().getByNetwork(networkId);
    }

    public static List<StoragePortRestRep> getStoragePortsByVirtualArray(URI virtualArrayId) {
        return getViprClient().storagePorts().getByVirtualArray(virtualArrayId);
    }

    public static List<StoragePortRestRep> getStoragePortsAssignedToVirtualArray(String virtualArrayId) {
        List<NamedRelatedResourceRep> refs = getViprClient().storagePorts().listByVirtualArray(uri(virtualArrayId));
        return getViprClient().storagePorts().getByRefs(refs, new AssignedVirtualArrayFilter(virtualArrayId));
    }

    public static List<StoragePortRestRep> getStoragePortsAssignableToVirtualArray(URI virtualArrayId) {
        List<StoragePortRestRep> assignedPorts = getStoragePortsAssignedToVirtualArray(asString(virtualArrayId));
        List<URI> ids = ids(assignedPorts);
        return getViprClient().storagePorts().getAll(new IdFilter<StoragePortRestRep>(ids).notId());
    }

    public static StoragePortRestRep register(URI portId, URI arrayId) {
        return getViprClient().storagePorts().register(portId, arrayId);
    }

    public static void deregister(URI portId) {
        getViprClient().storagePorts().deregister(portId);
    }

    public static StoragePortRestRep unassign(String portId) {
        return getViprClient().storagePorts().update(uri(portId), new StoragePortUpdate(NULL_URI));
    }

    public static StoragePortRestRep create(URI id, StoragePortRequestParam param) {
        return getViprClient().storagePorts().create(id, param);
    }

    public static StoragePortRestRep update(String portId, NetworkRestRep network) {
        return update(uri(portId), new StoragePortUpdate(id(network)));
    }

    public static StoragePortRestRep update(URI portId, StoragePortUpdate update) {
        return getViprClient().storagePorts().update(portId, update);
    }

    public static class NetworkFilter extends DefaultResourceFilter<StoragePortRestRep> {
        private URI networkId;

        public NetworkFilter(URI networkId) {
            this.networkId = networkId;
        }

        @Override
        public boolean accept(StoragePortRestRep item) {
            return ObjectUtils.equals(networkId, id(item.getNetwork()));
        }
    }

    public static class TransportTypeFilter extends DefaultResourceFilter<StoragePortRestRep> {
        private String transportType;

        public TransportTypeFilter(String transportType) {
            this.transportType = transportType;
        }

        @Override
        public boolean accept(StoragePortRestRep item) {
            return StringUtils.equals(transportType, item.getTransportType());
        }
    }

    private static class AssignedVirtualArrayFilter extends DefaultResourceFilter<StoragePortRestRep> {
        private final String virtualArrayId;

        public AssignedVirtualArrayFilter(String virtualArrayId) {
            this.virtualArrayId = virtualArrayId;
        }

        @Override
        public boolean accept(StoragePortRestRep item) {
            return item.getAssignedVirtualArrays() != null && item.getAssignedVirtualArrays().contains(virtualArrayId);
        }
    }
}
