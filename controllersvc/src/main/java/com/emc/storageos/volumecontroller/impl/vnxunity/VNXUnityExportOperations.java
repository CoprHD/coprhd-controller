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
            Set<String> vnxeHostIds = new HashSet<String>();
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
                    }

                    // query VNX Unity initiator
                    VNXeHostInitiator vnxeInitiator = apiClient.getInitiatorByWWN(initiatorId);
                    if (vnxeInitiator != null) {
                        VNXeBase parentHost = vnxeInitiator.getParentHost();
                        if (parentHost != null) {
                            vnxeHostIds.add(parentHost.getId());
                        }
                    }
                }
            }

            if (vnxeHostIds.isEmpty()) {
                log.info("No Host found on array for initiators {}", Joiner.on(',').join(initiatorNames));
            } else {
                log.info("Found matching hosts {} on array", vnxeHostIds);
                for (String vnxeHostId : vnxeHostIds) {
                    // Get vnxeHost from vnxeHostId
                    VNXeHost vnxeHost = apiClient.getHostById(vnxeHostId);
                    List<VNXeBase> hostLunIds = vnxeHost.getHostLUNs();
                    if (hostLunIds != null && !hostLunIds.isEmpty()) {
                        for (VNXeBase hostLunId : hostLunIds) {
                            HostLun hostLun = apiClient.getHostLun(hostLunId.getId());
                            log.info("Looking at Host Lun {}; Lun: {}, HLU: {}", hostLunId.getId(), hostLun.getLun(), hostLun.getHlu());
                            usedHLUs.add(hostLun.getHlu());
                        }
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

}
