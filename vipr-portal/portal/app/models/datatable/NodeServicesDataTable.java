/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.vipr.model.sys.healthmonitor.ProcModels.ProcessStatus;
import com.emc.vipr.model.sys.healthmonitor.ServiceHealth;
import com.emc.vipr.model.sys.healthmonitor.ServiceStats;

public class NodeServicesDataTable extends DataTable {
    
    public NodeServicesDataTable() {
        addColumn("nodeId").hidden();
        addColumn("name");
        addColumn("status").setRenderFunction("render.status");
        addColumn("threads");
        addColumn("uptime").setRenderFunction("renderServiceUptime");
        addColumn("memory").setRenderFunction("render.bytes");
        addColumn("files");
        addColumn("pid");
        addColumn("actions").setRenderFunction("renderServiceActions");
        sortAllExcept("actions", "pid", "files");
    }
    
    public static class Services {
        String nodeId;
        String name;
        String status;
        Long threads;
        Long uptime;
        Long memory;
        int files;
        Integer pid;
        
        public Services(String nodeId, ServiceHealth serviceHealth, ServiceStats serviceStats) {
            this.nodeId = nodeId;
            this.name = serviceHealth.getServiceName();
            this.status = serviceHealth.getStatus();
            this.files = serviceStats.getFileDescriptors();
            ProcessStatus processStatus = serviceStats.getProcessStatus();
            if (processStatus != null) {
                this.threads = processStatus.getNumberOfThreads();
                this.uptime = processStatus.getUpTime();
                this.memory = processStatus.getVirtualMemSizeInBytes();
                this.pid = processStatus.getPid();
            }
        }
    }
}
