/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.List;

import org.apache.log4j.Logger;

import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.cluster.ClusterUpdateParam;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;

/**
 * Requires set-up of virtual arrays, virtual pools, and two clusters with two hosts each.
 */
public class ClusterAutoExportTest {

    private static final String HOST_NAME = "hostname";
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "password";
    private static final String CLUSTER_1_NAME = "cluster1";
    private static final String CLUSTER_2_NAME = "cluster2";

    private static final int RETRIES = 10;
    private static final int SLEEP = 10000;
    private static final int LONG_SLEEP = 60000;

    public static URI cluster1Id = null;
    public static URI cluster2Id = null;
    public static URI projectId = null;

    private static URI export1;
    private static URI export2;

    private static URI cluster1;
    private static URI cluster2;

    private static Logger log = Logger.getLogger(ClusterAutoExportTest.class);

    private final ViPRCoreClient client;

    public static void main(String[] args) throws Exception {
        ViPRCoreClient client =
                new ViPRCoreClient(HOST_NAME, true).withLogin(USER_NAME, PASSWORD);
        ClusterAutoExportTest tester = new ClusterAutoExportTest(client);
        tester.run();
    }

    public ClusterAutoExportTest(ViPRCoreClient client) {
        this.client = client;
    }

    public VirtualArrayRestRep chooseVirtualArray(List<VirtualArrayRestRep> virtualArrays) {
        if (virtualArrays.isEmpty()) {
            throw new IllegalArgumentException("No virtualArrays");
        }
        return virtualArrays.get(0);
    }

    public BlockVirtualPoolRestRep chooseVirtualPool(List<BlockVirtualPoolRestRep> virtualPools) {
        if (virtualPools.isEmpty()) {
            throw new IllegalArgumentException("No virtualPools");
        }
        return virtualPools.get(0);
    }

    public ProjectRestRep chooseProject(List<ProjectRestRep> projects) {
        if (projects.isEmpty()) {
            throw new IllegalArgumentException("No projects");
        }
        return projects.get(0);
    }

    public void run() throws Exception {

        cluster1 = getCluster(CLUSTER_1_NAME).getId();
        cluster2 = getCluster(CLUSTER_2_NAME).getId();

        setClusterAutoExport(cluster1, true);
        setClusterAutoExport(cluster1, true);

        export1 = createBlockVolumeForCluster(CLUSTER_1_NAME);
        export2 = createBlockVolumeForCluster(CLUSTER_2_NAME);

        test1();

        test2();

        test3();

        test4();
    }

    public void test1() throws Exception {
        // Move host from one cluster to another. Exports should be automatic.
        HostRestRep host = getHosts(cluster1).get(0);
        moveHost(host, cluster2);
        verifyHostNotInClusterExport(host, export1);
        verifyHostInClusterExport(host, export2);
    }

    public void test2() throws Exception {
        // Set auto-export on one cluster. Move host. Exports should be automatic.
        setClusterAutoExport(cluster2, false);
        HostRestRep host = getHosts(cluster2).get(0);
        moveHost(host, cluster1);
        verifyHostNotInClusterExport(host, export2);
        verifyHostInClusterExport(host, export1);
    }

    public void test3() throws Exception {
        // Set auto-export to false on both clusters. Exports will not be triggered.
        setClusterAutoExport(cluster1, false);
        HostRestRep host = getHosts(cluster2).get(0);

        moveHost(host, cluster1);

        // wait long enough for export updates to process
        Thread.sleep(SLEEP);

        verifyHostNotInClusterExport(host, export1);
        verifyHostInClusterExport(host, export2);
    }

    public void test4() throws Exception {
        // Set auto-export to true on one cluster to trigger synchronization
        List<HostRestRep> cluster1Hosts = getHosts(cluster1);
        List<HostRestRep> cluster2Hosts = getHosts(cluster2);

        setClusterAutoExport(cluster1, true);

        Thread.sleep(LONG_SLEEP);

        for (HostRestRep host : cluster1Hosts) {
            verifyHostInClusterExport(host, export1);
            verifyHostNotInClusterExport(host, export2);
        }

        for (HostRestRep host : cluster2Hosts) {
            verifyHostInClusterExport(host, export2);
            verifyHostNotInClusterExport(host, export1);
        }
    }

    public void setClusterAutoExport(URI cluster, Boolean value) {
        ClusterUpdateParam input = new ClusterUpdateParam();
        input.setAutoExportEnabled(value);
        client.clusters().update(cluster, input);
    }

    public boolean isHostInExport(HostRestRep host, URI exportId) {
        ExportGroupRestRep export = client.blockExports().get(exportId);
        for (HostRestRep exportHost : export.getHosts()) {
            if (exportHost.getId().equals(host.getId())) {
                return true;
            }
        }
        return false;
    }

    private void verifyHostInClusterExport(HostRestRep host, URI exportId) throws Exception {
        for (int i = 0; i < RETRIES; i++) {
            try {
                Thread.sleep(SLEEP);
                if (!isHostInExport(host, exportId)) {
                    System.out.println("Host " + host.getName() + " not in export yet");
                } else {
                    System.out.println("Host " + host.getName() + " in export. Success.");
                    return;
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }

        }
        throw new Exception("Host " + host.getName() + " was not in export.");
    }

    private void verifyHostNotInClusterExport(HostRestRep host, URI exportId) throws Exception {
        for (int i = 0; i < RETRIES; i++) {
            try {
                Thread.sleep(SLEEP);
                if (isHostInExport(host, exportId)) {
                    System.out.println("Host " + host.getName() + " still in export");
                } else {
                    System.out.println("Host " + host.getName() + " not in export. Success.");
                    return;
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }

        }
        throw new Exception("Host " + host.getName() + " was still in export.");
    }

    private void moveHost(HostRestRep host, URI id) {
        HostUpdateParam param = new HostUpdateParam();
        param.setCluster(id);
        client.hosts().update(host.getId(), param);
    }

    public URI createBlockVolumeForCluster(String clusterName) {
        ClusterRestRep cluster = getCluster(clusterName);

        List<VirtualArrayRestRep> virtualArrays = client.varrays().findByConnectedCluster(cluster.getId());
        // User choice
        VirtualArrayRestRep selectedVirtualArray = chooseVirtualArray(virtualArrays);

        List<BlockVirtualPoolRestRep> virtualPools = client.blockVpools().getByVirtualArray(selectedVirtualArray.getId());
        // User choice
        BlockVirtualPoolRestRep selectedVirtualPool = chooseVirtualPool(virtualPools);

        List<ProjectRestRep> projects = client.projects().getByUserTenant();

        // User choice
        ProjectRestRep selectedProject = chooseProject(projects);

        URI volumeId = createVolume(selectedVirtualArray, selectedVirtualPool, selectedProject);

        return createExport(volumeId, cluster, selectedVirtualArray, selectedProject);
    }

    public List<HostRestRep> getHosts(URI clusterId) {
        return client.hosts().getByCluster(clusterId);
    }

    public ClusterRestRep getCluster(String clusterName) {
        return client.clusters().searchByName(clusterName).get(0);
    }

    public URI createVolume(VirtualArrayRestRep virtualArray, BlockVirtualPoolRestRep virtualPool, ProjectRestRep project) {
        VolumeCreate input = new VolumeCreate();
        input.setName("ClusterAutoExportTest" + System.currentTimeMillis());
        input.setVarray(virtualArray.getId());
        input.setVpool(virtualPool.getId());
        input.setSize("2GB");
        input.setCount(1);
        input.setProject(project.getId());

        Task<VolumeRestRep> task = client.blockVolumes().create(input).firstTask();
        VolumeRestRep volume = task.get();
        System.out.println("Created Volume: " + volume.getId());
        return volume.getId();
    }

    public URI createExport(URI volumeId, ClusterRestRep cluster, VirtualArrayRestRep virtualArray,
            ProjectRestRep project) {
        ExportCreateParam input = new ExportCreateParam();
        input.setName(cluster.getName());
        input.setType("Cluster");
        input.addCluster(cluster.getId());
        input.setVarray(virtualArray.getId());
        input.addVolume(volumeId);
        input.setProject(project.getId());

        ExportGroupRestRep export = client.blockExports().create(input).get();
        System.out.println("Created Export Group: " + export.getId());
        return export.getId();
    }
}
