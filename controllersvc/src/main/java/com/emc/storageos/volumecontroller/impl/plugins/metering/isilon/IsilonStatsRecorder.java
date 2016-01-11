/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.isilon;

import java.net.URI;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota.Thresholds;
import com.emc.storageos.isilon.restapi.IsilonSnapshot;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;

/**
 * Class for creating Stat objects from IsilonStats
 */
public class IsilonStatsRecorder {
    private Logger _log = LoggerFactory.getLogger(IsilonStatsRecorder.class);

    private ZeroRecordGenerator zeroRecordGenerator;

    private CassandraInsertion statsColumnInjector;

    class HostStat {
        public long reads = 0;
        public long writes = 0;
        ArrayList<Float> outBw = new ArrayList<Float>();
        ArrayList<Float> inBw = new ArrayList<Float>();

        private float getAvg(ArrayList<Float> inArray) {
            float sum = 0;
            for (float f : inArray) {
                sum += f;
            }
            return sum / inArray.size();
        }

        public float getOutBWAvg() {
            return getAvg(outBw);
        }

        public float getInBWAvg() {
            return getAvg(inBw);
        }
    }

    /**
     * Constructor.
     * 
     * @param zeroRecordGenerator
     * @param statsColumnInjector
     */
    public IsilonStatsRecorder(ZeroRecordGenerator zeroRecordGenerator,
            CassandraInsertion statsColumnInjector) {
        this.zeroRecordGenerator = zeroRecordGenerator;
        this.statsColumnInjector = statsColumnInjector;
    }

    /**
     * Adds a Stat for usage from the IsilonQuota.
     * 
     * @param quota
     * @param keyMap
     * @param fsNativeGuid native Guid of the file share
     * @param isilonApi
     * @return the stat
     */
    public Stat addUsageStat(IsilonSmartQuota quota, Map<String, Object> keyMap, String fsNativeGuid, IsilonApi isilonApi) {

        Stat stat = zeroRecordGenerator.injectattr(keyMap, fsNativeGuid, null);
        if (stat != null) {
            try {
                DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
                stat.setTimeInMillis((Long) keyMap.get(Constants._TimeCollected));
                stat.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));

                statsColumnInjector.injectColumns(stat, dbClient);

                long provisionedCapacity = 0L;
                Thresholds threshold = quota.getThresholds();
                if (threshold != null && threshold.getHard() != null) {
                	provisionedCapacity = threshold.getHard();
                }
                stat.setProvisionedCapacity(provisionedCapacity);
                long usedCapacity = quota.getUsagePhysical();
                stat.setAllocatedCapacity(usedCapacity);

                URIQueryResultList snapURIList = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.
                        Factory.getFileshareSnapshotConstraint(stat.getResourceId()), snapURIList);

                // Set snapshot count.
                // Set snapshot size. Get current data for snapshot size (snapshot size changes dynamically).
                int snapCount = 0;
                long fsSnapshotSize = 0;
                IsilonSnapshot isiSnap;
                for (URI snapURI : snapURIList) {
                    Snapshot snap = dbClient.queryObject(Snapshot.class, snapURI);
                    // Filter out deleted Snapshot
                    if (snap != null && (!snap.getInactive())) {
                        String nativeId = snap.getNativeId();
                        try {
                            isiSnap = isilonApi.getSnapshot(nativeId);
                        } catch (IsilonException iex) {
                            _log.error(String.format("Stat: %s: can not get snapshot size for snapshot: %s", fsNativeGuid, nativeId), iex);
                            continue;
                        }
                        snapCount++;
                        fsSnapshotSize += Long.valueOf(isiSnap.getSize());
                    }
                }
                stat.setSnapshotCount(snapCount);
                _log.debug(String.format("Stat: %s: snapshot count: %s", fsNativeGuid, snapCount));

                stat.setSnapshotCapacity(fsSnapshotSize);
                _log.debug(String.format("Stat: %s: snapshot size: %s", fsNativeGuid, fsSnapshotSize));

                _log.debug(String.format("Stat: %s: %s: provisioned capacity(%s): used capacity(%s)",
                        stat.getResourceId(), fsNativeGuid, provisionedCapacity, usedCapacity));
            } catch (DatabaseException ex) {
                _log.error("Query to db failed for FileShare id {}, skipping recording usage stat.", stat.getResourceId(), ex);
            }
        }
        return stat;
    }

    /**
     * Adds client operation stats to Stat objects
     * Creates one stat object per client, for the timestamp
     * 
     * @param current
     */
    // TODO: Bandwidth in/out and Read/Write statistics count for file systems are unavailable. Waiting for Isilon API support.
    // public void addOperationStatsCurrent(ArrayList<IsilonStats.StatsClientProto> current, long timestamp) {
    // for (IsilonStats.StatsClientProto value: current) {
    // String client = value.getClientAddr();
    // float outBw = value.getOutBW();
    // float inBw = value.getInBW();
    // long readOps = value.getReadOps();
    // long writeOps = value.getWriteOps();
    // _log.info(String.format("%s: outBW(%s), inBW(%s), ops(%s)",
    // client, outBw, inBw, readOps+writeOps).toString());
    // _statRecorder.createFSOperationStat(timestamp, client, outBw, inBw, readOps, writeOps);
    // }
    // }

    /**
     * Adds client operation stats to Stat objects
     * Creates one stat object per client, per the whole interval
     * 
     * @param history
     */
    // TODO: Bandwidth in/out and Read/Write statistics count for file systems are unavailable. Waiting for Isilon API support.
    // public long addOperationStatsHistory(HashMap<Long, ArrayList<IsilonStats.StatsClientProto>> history, long lastTS) {
    // HashMap<String, HostStat> hostStats = new HashMap<String, HostStat>();
    // long latest = lastTS; // save the last timestamp from stats
    // Iterator<Map.Entry<Long, ArrayList<IsilonStats.StatsClientProto>>> it =
    // history.entrySet().iterator();
    // while(it.hasNext()) {
    // Map.Entry <Long, ArrayList<IsilonStats.StatsClientProto>> entry = it.next();
    // if (entry.getKey() <= lastTS) {
    // // older sample, pass
    // continue;
    // } else if (entry.getKey() > latest) {
    // // keep the highest timestamp we have seen so far
    // latest = entry.getKey();
    // }
    // ArrayList<IsilonStats.StatsClientProto> entryValues = entry.getValue();
    // for (IsilonStats.StatsClientProto value: entryValues) {
    // String client = value.getClientAddr();
    // float outBw = value.getOutBW();
    // float inBw = value.getInBW();
    // if (!hostStats.containsKey(client)) {
    // hostStats.put(client, new HostStat());
    // }
    // HostStat stat = hostStats.get(client);
    // if (outBw > 0)
    // stat.outBw.add(outBw);
    // if (inBw > 0)
    // stat.inBw.add(inBw);
    // stat.reads += value.getReadOps();
    // stat.writes += value.getWriteOps();
    // }
    // }
    // Iterator<Map.Entry <String, HostStat>> resultsIt = hostStats.entrySet().iterator();
    // while(resultsIt.hasNext()){
    // Map.Entry <String, HostStat> result = resultsIt.next();
    // _log.info(String.format("%s: OutBW(%s), InBW(%s), ops(%s)",
    // result.getKey(), result.getValue().getOutBWAvg(), result.getValue().getInBWAvg(),
    // result.getValue().reads + result.getValue().writes).toString());
    // _statRecorder.createFSOperationStat(latest, result.getKey(), result.getValue().getOutBWAvg(),
    // result.getValue().getInBWAvg(), result.getValue().reads, result.getValue().writes);
    // }
    // return latest;
    // }
}
