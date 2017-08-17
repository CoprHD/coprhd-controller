/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationMode;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;

/**
 * Configuration for simulated remote replication.
 */
public class RemoteReplicationConfiguration {

    // native ids of replication sets
    private enum ReplicationSets {replicationSet1, replicationSet2};

    // native ids of replication groups
    private enum ReplicationGroups {replicationGroup1_set1, replicationGroup2_set1, replicationGroup1_set2, replicationGroup2_set2};

    // native ids of source and target arrays
    private enum SourceStorageSystems {source_replicationGroup1_set1, source_replicationGroup2_set1, source_replicationGroup1_set2, source_replicationGroup2_set2};
    private enum TargetStorageSystems {target_replicationGroup1_set1, target_replicationGroup2_set1, target_replicationGroup1_set2, target_replicationGroup2_set2};

    private static RemoteReplicationSet replicationSet1 = new RemoteReplicationSet();
    private static RemoteReplicationSet replicationSet2 = new RemoteReplicationSet();

    // replication groups in replication set1
    private static RemoteReplicationGroup replicationGroup1_set1 = new RemoteReplicationGroup();
    private static RemoteReplicationGroup replicationGroup2_set1 = new RemoteReplicationGroup();

    // replication groups in replication set2
    private static RemoteReplicationGroup replicationGroup1_set2 = new RemoteReplicationGroup();
    private static RemoteReplicationGroup replicationGroup2_set2 = new RemoteReplicationGroup();

    public static void init() {
        /**
         * We configure two replication sets, each set with two replication groups.
         * In each set one replication group is synch and one is async.
         *
         * All groups have one source and one target systems.
         * Link granularity: group and pair.
         * All groups enforce group consistency.
         *
         * Each replication set has two source and two target systems.
         * Each replication set supports link operations for groups and pairs.
         * The same for replication groups.
         *
         * Elements in each replication set can be groups and pairs.
         *
         */

        RemoteReplicationMode replicationModeSync = new RemoteReplicationMode("synchronous", true, false);
        RemoteReplicationMode replicationModeAsync = new RemoteReplicationMode("asynchronous", true, false);
        Set<RemoteReplicationMode> supportedReplicationModes = new HashSet<>();
        supportedReplicationModes.add(replicationModeAsync);
        supportedReplicationModes.add(replicationModeSync);

        // group link granularity
        Set<RemoteReplicationSet.ElementType> replicationLinkGranularity = new HashSet<>();
        replicationLinkGranularity.add(RemoteReplicationSet.ElementType.REPLICATION_GROUP);
        replicationLinkGranularity.add(RemoteReplicationSet.ElementType.REPLICATION_PAIR);
        replicationLinkGranularity.add(RemoteReplicationSet.ElementType.REPLICATION_SET);

        Set<RemoteReplicationSet.ElementType> setSupportedElements = new HashSet<>();
        setSupportedElements.add(RemoteReplicationSet.ElementType.REPLICATION_GROUP);
        setSupportedElements.add(RemoteReplicationSet.ElementType.REPLICATION_PAIR);

        // Initialize four replication groups, two for each of the replication sets.
        /*
         * group: replicationGroup1_set1
         */
        replicationGroup1_set1.setNativeId(ReplicationGroups.replicationGroup1_set1.toString());
        replicationGroup1_set1.setDeviceLabel(ReplicationGroups.replicationGroup1_set1.toString());
        replicationGroup1_set1.setDisplayName(ReplicationGroups.replicationGroup1_set1.toString());
        //replicationGroup1_set1.setReplicationSetNativeId(ReplicationSets.replicationSet1.toString());
        replicationGroup1_set1.setIsGroupConsistencyEnforced(true);
        replicationGroup1_set1.setReplicationState("ACTIVE");
        // group replication mode
        replicationGroup1_set1.setReplicationMode(replicationModeAsync.getReplicationModeName());
        //replicationGroup1_set1.setReplicationLinkGranularity(replicationLinkGranularity);
        // source and target systems
        replicationGroup1_set1.setSourceSystemNativeId(SourceStorageSystems.source_replicationGroup1_set1.toString());
        replicationGroup1_set1.setTargetSystemNativeId(TargetStorageSystems.target_replicationGroup1_set1.toString());

        /*
         * group: replicationGroup2_set1
         */
        replicationGroup2_set1.setNativeId(ReplicationGroups.replicationGroup2_set1.toString());
        replicationGroup2_set1.setDeviceLabel(ReplicationGroups.replicationGroup2_set1.toString());
        replicationGroup2_set1.setDisplayName(ReplicationGroups.replicationGroup2_set1.toString());
        //replicationGroup2_set1.setReplicationSetNativeId(ReplicationSets.replicationSet1.toString());
        replicationGroup2_set1.setIsGroupConsistencyEnforced(true);
        replicationGroup2_set1.setReplicationState("ACTIVE");
        // group replication mode
        replicationGroup2_set1.setReplicationMode(replicationModeSync.getReplicationModeName());
        // group link granularity
        //replicationGroup2_set1.setReplicationLinkGranularity(replicationLinkGranularity);

        // source and target systems (make the systems the same as in group1)
        replicationGroup2_set1.setSourceSystemNativeId(SourceStorageSystems.source_replicationGroup1_set1.toString());
        replicationGroup2_set1.setTargetSystemNativeId(TargetStorageSystems.target_replicationGroup1_set1.toString());

        /*
         * group: replicationGroup1_set2
         */
        replicationGroup1_set2.setNativeId(ReplicationGroups.replicationGroup1_set2.toString());
        replicationGroup1_set2.setDeviceLabel(ReplicationGroups.replicationGroup1_set2.toString());
        replicationGroup1_set2.setDisplayName(ReplicationGroups.replicationGroup1_set2.toString());
        //replicationGroup1_set2.setReplicationSetNativeId(ReplicationSets.replicationSet1.toString());
        replicationGroup1_set2.setIsGroupConsistencyEnforced(true);
        replicationGroup1_set2.setReplicationState("ACTIVE");
        // group replication mode
        replicationGroup1_set2.setReplicationMode(replicationModeSync.getReplicationModeName());
        // group link granularity
        //replicationGroup1_set2.setReplicationLinkGranularity(replicationLinkGranularity);

        // source and target systems
        replicationGroup1_set2.setSourceSystemNativeId(SourceStorageSystems.source_replicationGroup1_set2.toString());
        replicationGroup1_set2.setTargetSystemNativeId(TargetStorageSystems.target_replicationGroup1_set2.toString());

        /*
         * group: replicationGroup2_set2
         */

        replicationGroup2_set2.setNativeId(ReplicationGroups.replicationGroup2_set2.toString());
        replicationGroup2_set2.setDeviceLabel(ReplicationGroups.replicationGroup2_set2.toString());
        replicationGroup2_set2.setDisplayName(ReplicationGroups.replicationGroup2_set2.toString());
        //replicationGroup2_set2.setReplicationSetNativeId(ReplicationSets.replicationSet1.toString());
        replicationGroup2_set2.setIsGroupConsistencyEnforced(true);
        replicationGroup2_set2.setReplicationState("ACTIVE");
        // group replication mode
        replicationGroup2_set2.setReplicationMode(replicationModeAsync.getReplicationModeName());
        // group link granularity
        //replicationGroup2_set2.setReplicationLinkGranularity(replicationLinkGranularity);

        // source and target systems
        replicationGroup2_set2.setSourceSystemNativeId(SourceStorageSystems.source_replicationGroup2_set2.toString());
        replicationGroup2_set2.setTargetSystemNativeId(TargetStorageSystems.target_replicationGroup2_set2.toString());

        Set<RemoteReplicationGroup> set1ReplicationGroups = new HashSet<>();
        set1ReplicationGroups.add(replicationGroup1_set1);
        set1ReplicationGroups.add(replicationGroup2_set1);

        Set<RemoteReplicationGroup> set2ReplicationGroups = new HashSet<>();
        set2ReplicationGroups.add(replicationGroup1_set2);
        set2ReplicationGroups.add(replicationGroup2_set2);

        /* ============== Done with replication groups initialization =================== */

        // initialize two replication sets
        HashSet<RemoteReplicationSet.ReplicationRole> replicationRoleSource = new HashSet<>();
        replicationRoleSource.add(RemoteReplicationSet.ReplicationRole.SOURCE);
        HashSet<RemoteReplicationSet.ReplicationRole> replicationRoleTarget = new HashSet<>();
        replicationRoleTarget.add(RemoteReplicationSet.ReplicationRole.TARGET);

        // initialize set 1.
        replicationSet1.setDeviceLabel(ReplicationSets.replicationSet1.toString());
        replicationSet1.setNativeId(ReplicationSets.replicationSet1.toString());
        //replicationSet1.setReplicationMode(replicationModeSync);
        replicationSet1.setSupportedElementTypes(setSupportedElements);
        replicationSet1.setReplicationLinkGranularity(replicationLinkGranularity);
        replicationSet1.setReplicationState("ACTIVE");
        replicationSet1.setSupportedElementTypes(setSupportedElements);
        replicationSet1.setReplicationGroups(set1ReplicationGroups);
        replicationSet1.setSupportedReplicationModes(supportedReplicationModes);

        // set system map
        Map<String, Set<RemoteReplicationSet.ReplicationRole>> systemMapSet1 = new HashMap<>();
        systemMapSet1.put(SourceStorageSystems.source_replicationGroup1_set1.toString(), replicationRoleSource);
        systemMapSet1.put(TargetStorageSystems.target_replicationGroup1_set1.toString(), replicationRoleTarget);
        replicationSet1.setSystemMap(systemMapSet1);

        // initialize set 2.
        replicationSet2.setDeviceLabel(ReplicationSets.replicationSet2.toString());
        replicationSet2.setNativeId(ReplicationSets.replicationSet2.toString());
        //replicationSet2.setReplicationMode(replicationModeSync);
        replicationSet2.setSupportedElementTypes(setSupportedElements);
        replicationSet2.setReplicationLinkGranularity(replicationLinkGranularity);
        replicationSet2.setReplicationState("ACTIVE");
        replicationSet2.setSupportedElementTypes(setSupportedElements);
        replicationSet2.setReplicationGroups(set2ReplicationGroups);
        replicationSet2.setSupportedReplicationModes(supportedReplicationModes);

        // set system map
        Map<String, Set<RemoteReplicationSet.ReplicationRole>> systemMapSet2 = new HashMap<>();
        systemMapSet2.put(SourceStorageSystems.source_replicationGroup1_set2.toString(), replicationRoleSource);
        systemMapSet2.put(SourceStorageSystems.source_replicationGroup2_set2.toString(), replicationRoleSource);
        systemMapSet2.put(TargetStorageSystems.target_replicationGroup1_set2.toString(), replicationRoleTarget);
        systemMapSet2.put(TargetStorageSystems.target_replicationGroup2_set2.toString(), replicationRoleTarget);
        replicationSet2.setSystemMap(systemMapSet2);

        /* ============== Done with replication sets initialization =================== */

    }

    public static List<RemoteReplicationSet> getRemoteReplicationSets() {
        List<RemoteReplicationSet> replicationSets = new ArrayList<>();
        replicationSets.add(replicationSet1);
        replicationSets.add(replicationSet2);

        return replicationSets;
    }

    public static List<RemoteReplicationGroup> getRemoteReplicationGroups() {
        List<RemoteReplicationGroup> replicationGroups = new ArrayList<>();
        replicationGroups.add(replicationGroup1_set1);
        replicationGroups.add(replicationGroup2_set1);
        replicationGroups.add(replicationGroup1_set2);
        replicationGroups.add(replicationGroup2_set2);

        return replicationGroups;
    }
}
