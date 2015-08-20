/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.vipr.model.sys.healthmonitor.NodeHealth;

public class NodesDataTable extends DataTable {

    public NodesDataTable() {
        addColumn("id").setRenderFunction("renderLink");
        addColumn("ip");
        addColumn("status").setRenderFunction("render.status");
        addColumn("type");
        addColumn("actions").setRenderFunction("renderButtonBar");
        sortAllExcept("actions");
        setServerSide(true);
    }

    public static class Nodes {
        String id;
        String ip;
        String status;
        String type;

        public Nodes(NodeHealth node, String type) {
            this.id = node.getNodeId();
            this.ip = node.getIp();
            this.status = node.getStatus();
            this.type = type;
        }
    }

}
