/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.mapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.model.ConfigVersion;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;

import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.upgrade.SyncInfoBuilder;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.ClusterInfo.ClusterState;
import com.emc.vipr.model.sys.ClusterInfo.NodeState;
import javax.ws.rs.core.Response;

public class ClusterInfoMapper {

    public static Response toClusterResponse(ClusterInfo from) {
        ClusterInfo to = toClusterInfoWithSelfLink(from);
        if (!ClusterInfo.ClusterState.STABLE.toString().equals(from.getCurrentState())) {
            // returning 202 Accepted status.
            return Response.status(202).entity(to).build();
        }
        return Response.ok(to).build();
    }

    public static ClusterInfo toClusterInfoWithSelfLink(ClusterInfo from) {
        ClusterInfo copyTo = new ClusterInfo();
        copyTo.setCurrentState(from.getCurrentState());
        copyTo.setSelfLink(new RestLinkRep());
        copyTo.getSelfLink().setLinkName("self");
        try {
            copyTo.getSelfLink().setLinkRef(new URI(ClusterInfo.CLUSTER_URI));
        } catch (URISyntaxException e) {
            throw APIException.badRequests.parameterIsNotValidURI(ClusterInfo.CLUSTER_URI);
        }
        return copyTo;
    }

    public static ClusterInfo toClusterInfo(final ClusterState controlNodesState,
            final Map<Service, RepositoryInfo> controlNodesInfo,
            final Map<Service, ConfigVersion> controlNodesConfigVersions,
            final RepositoryInfo targetRepository,
            final PropertyInfoExt targetProperty) {

        ClusterInfo toClusterInfo = new ClusterInfo();

        toClusterInfo.setCurrentState((controlNodesState != ClusterState.STABLE) ? controlNodesState.toString() :
                ClusterState.STABLE.toString());

        if (!controlNodesInfo.isEmpty()) {
            toClusterInfo.setControlNodes(new HashMap<String, NodeState>());

            for (Map.Entry<Service, RepositoryInfo> entry : controlNodesInfo.entrySet()) {
                addControlNodeInfo(toClusterInfo, entry.getKey().getNodeId(), entry.getValue(),
                        controlNodesConfigVersions != null ? controlNodesConfigVersions.get(entry.getKey()) : null);
            }
        }

        if (targetRepository != null) {
            addTargetInfo(toClusterInfo, targetRepository, targetProperty);
        }

        return toClusterInfo;
    }

    /**
     * Common method to construct node state by repository info
     * 
     * @param info repository info
     * @param configVersion config version
     * @return node state
     */
    private static NodeState getNodeStateCommon(RepositoryInfo info, ConfigVersion configVersion) {
        NodeState n = new NodeState();

        if (info.getVersions() != null) {
            n.setAvailable(new ArrayList<String>());
            for (SoftwareVersion v : info.getVersions()) {
                n.getAvailable().add(v.toString());
            }
        }
        if (info.getCurrentVersion() != null) {
            n.setCurrent(info.getCurrentVersion().toString());
        }
        if (configVersion != null) {
            n.setConfigVersion(configVersion.getConfigVersion());
        }

        return n;
    }

    private static void addControlNodeInfo(final ClusterInfo clusterInfo, final String nodeId,
            final RepositoryInfo info, final ConfigVersion configVersion) {
        clusterInfo.getControlNodes().put(nodeId, getNodeStateCommon(info, configVersion));
    }

    private static void addExtraNodeInfo(final ClusterInfo clusterInfo, final String nodeId,
            final RepositoryInfo info, final ConfigVersion configVersion) {
        clusterInfo.getExtraNodes().put(nodeId, getNodeStateCommon(info, configVersion));
    }

    private static void addTargetInfo(final ClusterInfo clusterInfo, final RepositoryInfo targetRepository,
            final PropertyInfoExt targetProperty) {
        clusterInfo.setTargetState(new NodeState());
        if (targetRepository.getVersions() != null) {
            clusterInfo.getTargetState().setAvailable(new ArrayList<String>());
            for (SoftwareVersion v : targetRepository.getVersions()) {
                clusterInfo.getTargetState().getAvailable().add(v.toString());
            }
        }
        if (targetRepository.getCurrentVersion() != null) {
            clusterInfo.getTargetState().setCurrent(targetRepository.getCurrentVersion().toString());
        }
        if (targetProperty.getProperty(PropertyInfoExt.CONFIG_VERSION) != null) {
            clusterInfo.getTargetState().setConfigVersion(targetProperty.getProperty(PropertyInfoExt.CONFIG_VERSION));
        }
    }

    /**
     * Add the new versions and the removable versions to the cluster state
     * 
     * @param targetRepoInfo Local repository info
     * @param cachedRemoteVersions available versions
     * @param force show versions for force install and force remove
     * @throws IOException
     */
    public static void setInstallableRemovable(final ClusterInfo clusterInfo, final RepositoryInfo targetRepoInfo,
            final Map<SoftwareVersion, List<SoftwareVersion>> cachedRemoteVersions,
            final boolean force) throws IOException {
        // if stable, add new versions and removable versions
        if (clusterInfo.getCurrentState().equals(ClusterState.STABLE.toString()) && cachedRemoteVersions != null) {

            final List<SoftwareVersion> newRemoteVersions = SyncInfoBuilder.getInstallableRemoteVersions(targetRepoInfo,
                    cachedRemoteVersions, force);
            if (!newRemoteVersions.isEmpty()) {
                clusterInfo.setNewVersions(new ArrayList<String>());
                for (SoftwareVersion newRemoteVersion : newRemoteVersions) {
                    clusterInfo.getNewVersions().add(newRemoteVersion.toString());
                }
            }
            List<SoftwareVersion> removableVersions = SyncInfoBuilder.findToRemove(targetRepoInfo.getVersions(),
                    targetRepoInfo.getCurrentVersion(), null, null, force);
            if (removableVersions.isEmpty()) {
                return;
            }
            List<String> removableVersionString = new ArrayList<String>();
            for (SoftwareVersion v : removableVersions) {
                removableVersionString.add(v.toString());
            }
            clusterInfo.setRemovableVersions(removableVersionString);
        }
    }

}
