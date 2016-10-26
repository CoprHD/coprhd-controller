/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.HostLun;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
import com.emc.storageos.volumecontroller.impl.vnxe.VNXeExportOperations;
import com.google.common.base.Joiner;

public class VNXUnityExportOperations extends VNXeExportOperations {
    private static final Logger log = LoggerFactory.getLogger(VNXUnityExportOperations.class);

    // Max HLU number allowed on array. TODO Find it out
    private static final int MAX_HLU = 512;

    @Override
    protected VNXeApiClient getVnxeClient(StorageSystem storage) {
        VNXeApiClient client = _clientFactory.getUnityClient(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword());

        return client;

    }

    @Override
    public Set<Integer> findHLUsForInitiators(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        Set<Integer> usedHLUs = new HashSet<Integer>();
        try {
            String vnxeHostId = null;
            VNXeApiClient apiClient = getVnxeClient(storage);
            for (String initiatorName : initiatorNames) {
                initiatorName = Initiator.toPortNetworkId(initiatorName);
                URIQueryResultList initiatorResult = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(initiatorName),
                        initiatorResult);
                if (initiatorResult.iterator().hasNext()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorResult.iterator().next());
                    String initiatorId = initiator.getInitiatorPort();
                    if (Protocol.FC.name().equals(initiator.getProtocol())) {
                        initiatorId = initiator.getInitiatorNode() + ":" + initiatorId;

                        // query VNX Unity initiator
                        VNXeHostInitiator vnxeInitiator = apiClient.getInitiatorByWWN(initiatorId);
                        if (vnxeInitiator != null) {
                            VNXeBase parentHost = vnxeInitiator.getParentHost();
                            if (parentHost != null) {
                                vnxeHostId = parentHost.getId();
                                break; // TODO verify - all initiators part of same vnxeHost?
                            }
                        }
                    }
                }
            }

            if (vnxeHostId == null) {
                log.info("No Host found on array for initiators {}", Joiner.on(',').join(initiatorNames));
            } else {
                log.info("Found matching host {} on array", vnxeHostId);
                // Get vnxeHost from vnxeHostId
                VNXeHost vnxeHost = apiClient.getHostById(vnxeHostId);
                List<VNXeBase> hostLunIds = vnxeHost.getHostLUNs();
                if (hostLunIds != null && !hostLunIds.isEmpty()) {
                    for (VNXeBase hostLunId : hostLunIds) {
                        HostLun hostLun = apiClient.getHostLun(hostLunId.getId());
                        log.info("Looking at Host Lun {}; Lun: {}, HLU: {}", hostLun.getLun(), hostLun.getHlu());
                        usedHLUs.add(hostLun.getHlu());
                    }
                }
            }

            log.info(String.format("HLUs found for Initiators { %s }: %s",
                    Joiner.on(',').join(initiatorNames), usedHLUs));
        } catch (Exception e) {
            String errMsg = "Encountered an error when attempting to query used HLUs for initiators: " + e.getMessage();
            log.error(errMsg, e);
            throw VNXeException.exceptions.hluRetrievalFailed(errMsg, e);
        }
        return usedHLUs;
    }

    @Override
    public Integer getMaximumAllowedHLU(StorageSystem storage) {
        // TODO find out how to get it
        return MAX_HLU;
    }

}
