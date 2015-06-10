/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.healthmonitor;


import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.healthmonitor.NodeHardwareInfoRestRep;
import com.emc.vipr.model.sys.healthmonitor.NodeHardwareInfo.NodeHardwareInfoType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class NodeResourceAllocationChecker {
    private static final Logger _log = LoggerFactory.getLogger(NodeResourceAllocationChecker .class);
    private static final String STATUS_OK = "OK";
    private static final String STATUS_IMBALANCE = "IMBALANCE";

    private static final float epsillonRatio = 0.1f;

    private CoordinatorClientExt _coordinator;

    public void setCoordinator(CoordinatorClientExt coordinator) {
        _coordinator = coordinator;
    }

    public NodeResourceAllocationChecker() {
    }

    public String getNodeResourceAllocationCheckResult() {
        try {
            final List<String> svcIds = _coordinator.getAllNodes();
            final String mySvcId = _coordinator.getMySvcId();
            NodeHardwareInfoRestRep myBaseRep = getNodeHardwareInfo(mySvcId);

            for (String svcId : svcIds) {
                if (!svcId.equals(mySvcId)) {
                    NodeHardwareInfoRestRep compareRep = getNodeHardwareInfo(svcId);
                    if (!check2NodesReourceAllocation(myBaseRep, compareRep)) {
                        return STATUS_IMBALANCE;
                    }
                }
            }

            return STATUS_OK;
        } catch (SysClientException ex ) {
            //Ignore exception
            return STATUS_IMBALANCE;
        }
    }

    private NodeHardwareInfoRestRep getNodeHardwareInfo(String svcId) throws SysClientException{
        NodeHardwareInfoRestRep nodeHardwareInfoRestRep = null;
        try {
            _log.info("Get node: {} resp.", svcId);
            nodeHardwareInfoRestRep =  SysClientFactory.getSysClient(_coordinator.getNodeEndpointForSvcId(svcId))
                    .get(SysClientFactory.URI_GET_INTERNAL_NODE_HARDWARE,NodeHardwareInfoRestRep.class,null);
        } catch (SysClientException ex) {
            _log.error("Error get node hardware info with node: {} Cause: {}", svcId, ex.getMessage());
            throw ex;
        }
        return nodeHardwareInfoRestRep;
    }

    /**
     * check 2 node resource is balance or not, if imbalance, return false.
     *
     * @param myBaseRep
     * @param compareRep
     */
    private boolean check2NodesReourceAllocation(NodeHardwareInfoRestRep myBaseRep, NodeHardwareInfoRestRep compareRep) {
        for (Map.Entry<NodeHardwareInfoType, Float> baseEntry : myBaseRep.getHardwareInfos().entrySet()) {
            float diff = Math.abs(baseEntry.getValue() - compareRep.getHardwareInfos().get(baseEntry.getKey()));
            if (diff > (baseEntry.getValue() * epsillonRatio)) {
                _log.info("Resource allocation imbalance, base {} compare {}", myBaseRep.toString(), compareRep.toString());
                return false;
            }
        }

        return true;
    }
}
