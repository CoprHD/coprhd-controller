/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import org.apache.commons.lang.StringUtils;

import util.datatable.DataTable;

import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;

public class VirtualDataCentersDataTable extends DataTable {

    public VirtualDataCentersDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("apiEndpoint");
        addColumn("connectionStatus").setRenderFunction("render.vdcStatus");
        addColumn("lastSeen").setRenderFunction("render.vdclastReached");
        addColumn("local").setRenderFunction("render.boolean");
        addColumn("canDisconnect").hidden();
        addColumn("canReconnect").hidden();
        addColumn("canDelete").hidden();
        sortAll();
        setDefaultSortField("name");
    }

    public static class VirtualDataCenter {
        public String id;
        public String shortId;
        public String name;
        public String description;
        public String apiEndpoint;
        public String connectionStatus;
        public boolean local;
        public long lastSeen;
        public boolean canReconnect;
        public boolean canDisconnect;
        public boolean canDelete;
        public boolean lastReachAlarm;

        public VirtualDataCenter() {
        }

        public VirtualDataCenter(VirtualDataCenterRestRep vdc) {
            id = vdc.getId().toString();
            shortId = vdc.getShortId();
            name = StringUtils.defaultString(vdc.getName(), "localhost");
            apiEndpoint = vdc.getApiEndpoint();
            description = vdc.getDescription();
            connectionStatus = vdc.getStatus();
            local = vdc.isLocal();
            if (vdc.getLastSeenTimeInMillis() != null) {
                lastSeen = vdc.getLastSeenTimeInMillis();
            }

            canReconnect = vdc.canReconnect();
            canDisconnect = vdc.canDisconnect();
            canDelete = vdc.canDelete();
            lastReachAlarm = vdc.shouldAlarm();
        }
    }
}
