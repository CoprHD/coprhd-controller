/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterCreateParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.cluster.ClusterUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

import java.net.URI;
import java.util.List;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

/**
 */
public class ClusterUtils {

    /**
     * @return All clusters that are not VCenter clusters
     */
    public static List<ClusterRestRep> getClustersExcludingVCenter(String tenantId) {
        List<ClusterRestRep> clusterResponses = getClusters(tenantId);

        List<ClusterRestRep> clusters = Lists.newArrayList();
        for (ClusterRestRep clusterResponse : clusterResponses) {
            if (clusterResponse.getVcenterDataCenter() == null) {
                clusters.add(clusterResponse);
            }
        }
        return clusters;
    }

    /** Adds a NO CLUSTER option to the list of clusters */
    public static List<ClusterRestRep> getClusterOptions(String tenantId) {
        List<ClusterRestRep> clusters = Lists.newArrayList();

        ClusterRestRep clusterRestRep = new ClusterRestRep();
        clusterRestRep.setName(MessagesUtils.get("ClusterUtils.defaultName"));
        clusterRestRep.setId(uri("null"));
        clusters.add(clusterRestRep);
        clusters.addAll(getClustersExcludingVCenter(tenantId));

        return clusters;
    }

    public static List<HostRestRep> getHosts(URI clusterId) {
        return getViprClient().hosts().getByCluster(clusterId);
    }

    public static List<ClusterRestRep> getClusters(String tenantId) {
        return getViprClient().clusters().getByTenant(uri(tenantId));
    }

    public static URI createCluster(String tenantId, ClusterCreateParam hostCreateParam) {
        return getViprClient().clusters().create(uri(tenantId), hostCreateParam).getId();
    }

    public static ClusterRestRep getCluster(URI id) {
        try {
            return (id != null) ? getViprClient().clusters().get(id) : null;
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static URI updateHost(URI uri, ClusterUpdateParam hostUpdateParam) {
        ClusterRestRep cluster = getViprClient().clusters().update(uri, hostUpdateParam);
        return cluster.getId();
    }

    public static Task<ClusterRestRep> deactivate(URI clusterId) {
        return getViprClient().clusters().deactivate(clusterId);
    }
    
    public static Task<ClusterRestRep> deactivate(URI hostId, boolean detachStorage) {
        return getViprClient().clusters().deactivate(hostId, detachStorage);
    }

    public static Task<ClusterRestRep> detachStorage(URI clusterId) {
        return getViprClient().clusters().detachStorage(clusterId);
    }
}
