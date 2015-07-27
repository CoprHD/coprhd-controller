/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static com.emc.vipr.client.core.util.ResourceUtils.stringId;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;

import util.datatable.DataTable;

import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

/*
 * TODO: Rename and clean up fields.
 */
public class NetworksDataTable extends DataTable {

    public NetworksDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        addColumn("type").setRenderFunction("render.protocols");
        addColumn("discovered").setRenderFunction("renderBoolean");
        addColumn("virtualArrayNames");
        addColumn("storageSystemsNames").hidden();
        addColumn("fabricManagersNames").hidden();
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class NetworkInfo {
        public String id;
        public String name;
        public String type;
        public Boolean discovered;
        public String virtualArrayNames;
        public String storageSystemsNames;
        public String fabricManagersNames;
        public String registrationStatus;

        public NetworkInfo() {
        }

        public NetworkInfo(NetworkRestRep network) {
            this(network, null, null, null);
        }

        public NetworkInfo(NetworkRestRep network, Collection<VirtualArrayRestRep> virtualArrays,
                Collection<NetworkSystemRestRep> networkSystems, Collection<StorageSystemRestRep> storageSystems) {
            this.id = stringId(network);
            this.name = network.getName();
            this.type = network.getTransportType();
            this.discovered = Boolean.TRUE.equals(network.getDiscovered());
            this.registrationStatus = network.getRegistrationStatus();
            if (virtualArrays != null) {
                virtualArrayNames = StringUtils.join(ResourceUtils.names(virtualArrays), ", ");
            }
            if (storageSystems != null) {
                storageSystemsNames = StringUtils.join(ResourceUtils.names(storageSystems), ", ");
            }
            if (networkSystems != null) {
                this.fabricManagersNames = StringUtils.join(ResourceUtils.names(networkSystems), ", ");
            }
        }
    }
}
