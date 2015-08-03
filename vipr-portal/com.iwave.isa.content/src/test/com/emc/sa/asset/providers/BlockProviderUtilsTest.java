/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
// commented out due to project dependency issues. We would prefer not to have to depend on apisvc to get DummyDBClient.

//package com.emc.sa.asset.providers;
//
//import static org.junit.Assert.assertTrue;
//import java.net.URI;
//import java.util.List;
//import java.util.Set;
//import org.junit.Before;
//import org.junit.Test;
//import com.emc.sa.engine.ExecutionEngineImplTest.MockDbClient;
//import com.emc.storageos.db.client.URIUtil;
//import com.emc.storageos.db.client.model.BlockSnapshot;
//import com.emc.storageos.db.client.model.Volume;
//import com.emc.storageos.db.common.VdcUtil;
//import com.emc.storageos.model.block.export.ExportBlockParam;
//import com.emc.storageos.model.block.export.ExportGroupRestRep;
//import com.google.common.collect.Lists;
//import com.google.common.collect.Sets;
//
//public class BlockProviderUtilsTest {
//    
//    @Before
//    public void setUp() {
//        VdcUtil.setDbClient(MockDbClient.create());
//    }
//    
//    @Test
//    public void getVolumesAndSnapshotsInExportsTest() {
//        
//        List<ExportGroupRestRep> exports = Lists.newArrayList();
//        
//        ExportGroupRestRep e1 = new ExportGroupRestRep();
//        
//        ExportBlockParam vol1 = newVolume();
//        ExportBlockParam vol2 = newSnapshot();
//        ExportBlockParam vol3 = newSnapshot();
//        ExportBlockParam vol4 = newVolume();
//        List<ExportBlockParam> e1Volumes = Lists.newArrayList(vol1, vol2, vol3, vol4);
//        e1.setVolumes(e1Volumes);
//        exports.add(e1);
//        
//        Set<URI> volumes = Sets.newHashSet();
//        Set<URI> snapshots = Sets.newHashSet();
//        
//        BlockProviderUtils.getVolumesAndSnapshotsInExports(exports, volumes, snapshots);
//        
//        assertTrue(snapshots.contains(vol2.getId()));
//        assertTrue(snapshots.contains(vol3.getId()));
//        
//        assertTrue(volumes.contains(vol1.getId()));
//        assertTrue(volumes.contains(vol4.getId()));
//        
//    }
//    private ExportBlockParam newVolume() {
//        ExportBlockParam volume = new ExportBlockParam(URIUtil.createId(Volume.class), 0);
//        return volume;
//    }
//    
//    private ExportBlockParam newSnapshot() {
//        ExportBlockParam snapshot = new ExportBlockParam(URIUtil.createId(BlockSnapshot.class), 0);
//        return snapshot;
//    }
// }
