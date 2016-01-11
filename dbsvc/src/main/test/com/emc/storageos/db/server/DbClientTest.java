/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.constraint.AggregationQueryResultList;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPermissionsConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.IndexCleaner;
import com.emc.storageos.db.client.impl.IndexCleanupList;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TimeSeriesType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.BucketGranularity;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Event;
import com.emc.storageos.db.client.model.EventTimeSeries;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.UserSecretKey;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.SumPrimitiveFieldAggregator;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.Rows;

/**
 * DB client tests
 */
public class DbClientTest extends DbsvcTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(DbClientTest.class);

    private int pageSize = 3;

    private DbClient _dbClient;

    @Before
    public void setupTest() {
        DbClientImplUnitTester dbClient = new DbClientImplUnitTester();
        dbClient.setCoordinatorClient(_coordinator);
        dbClient.setDbVersionInfo(sourceVersion);
        dbClient.setBypassMigrationLock(true);
        _encryptionProvider.setCoordinator(_coordinator);
        dbClient.setEncryptionProvider(_encryptionProvider);

        DbClientContext localCtx = new DbClientContext();
        localCtx.setClusterName("Test");
        localCtx.setKeyspaceName("Test");
        dbClient.setLocalContext(localCtx);

        VdcUtil.setDbClient(dbClient);

        dbClient.setBypassMigrationLock(false);
        dbClient.start();

        _dbClient = dbClient;
    }

    @After
    public void teardown() {
        if (_dbClient instanceof DbClientImplUnitTester) {
            ((DbClientImplUnitTester) _dbClient).removeAll();
        }
    }

    private static class DummyQueryResult implements TimeSeriesQueryResult<Event> {
        private final CountDownLatch _latch;
        private final Integer _matchedMinute;
        private final Integer _matchedSeconds;

        DummyQueryResult(CountDownLatch latch) {
            this(latch, null);
        }

        DummyQueryResult(CountDownLatch latch, Integer matchedMinute) {
            this(latch, matchedMinute, null);
        }

        DummyQueryResult(CountDownLatch latch, Integer matchedMinute, Integer matchedSeconds) {
            _latch = latch;
            _matchedMinute = matchedMinute;
            _matchedSeconds = matchedSeconds;
        }

        @Override
        public void data(Event data, long insertionTimeMs) {
            DateTime d = new DateTime(insertionTimeMs, DateTimeZone.UTC);
            if (_matchedMinute != null) {
                Assert.assertEquals(d.getMinuteOfHour(), _matchedMinute.intValue());
            }
            if (_matchedSeconds != null) {
                Assert.assertEquals(d.getSecondOfMinute(), _matchedSeconds.intValue());
            }
            if (_latch != null) {
                _latch.countDown();
            }
        }

        @Override
        public void done() {
            _logger.info("Done callback");
        }

        @Override
        public void error(Throwable e) {
            _logger.error("Error callback", e);
            Assert.assertNull(e);
        }
    }

    private List<VirtualArray> createVirtualArrays(int count, String labelPrefix) {
        List<VirtualArray> varrays = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            VirtualArray varray = new VirtualArray();
            varray.setId(URIUtil.createId(VirtualArray.class));
            varray.setLabel(labelPrefix + i);
            _dbClient.createObject(varray);
            varrays.add(varray);
        }

        return varrays;
    }

    private List<Project> createProjects(int count, TenantOrg tenant) {
        List<Project> projects = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Project pj = new Project();
            pj.setId(URIUtil.createId(Project.class));
            String label = String.format("%1$d :/#$#@$\\: Test Label", i);
            pj.setLabel(label);
            pj.setTenantOrg(new NamedURI(tenant.getId(), label));
            _dbClient.createObject(pj);

            projects.add(pj);
        }

        return projects;
    }

    private List<VirtualPool> createVirtualPools(int count, String label, VirtualPool.Type type) {
        List<VirtualPool> vpools = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            VirtualPool vpool = new VirtualPool();
            vpool.setId(URIUtil.createId(VirtualPool.class));
            vpool.setLabel(label);
            vpool.setType(type.name());
            vpool.setProtocols(new StringSet());
            _dbClient.createObject(vpool);
            vpools.add(vpool);
        }

        return vpools;
    }

    private List<Volume> createVolumes(int count, String label, Project project) {
        return createVolumes(count, label, project, null);
    }

    private List<Volume> createVolumes(int count, String label, StoragePool pool) {
        return createVolumes(count, label, null, pool);
    }

    private List<Volume> createVolumes(int count, String label, Project project, StoragePool pool) {
        List<Volume> volumns = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Volume vol = new Volume();
            vol.setId(URIUtil.createId(Volume.class));
            vol.setLabel(label);
            if (project != null) {
                vol.setProject(new NamedURI(project.getId(), vol.getLabel()));
            }
            if (pool != null) {
                vol.setPool(pool.getId());
            }
            _dbClient.createObject(vol);

            volumns.add(vol);
        }

        return volumns;
    }

    private List<StoragePool> createStoragePools(int count, String label) {
        List<StoragePool> pools = new ArrayList<StoragePool>(count);

        for (int i = 0; i < count; i++) {
            StoragePool pool = new StoragePool();
            pool.setId(URIUtil.createId(StoragePool.class));
            pool.setLabel(label);
            _dbClient.createObject(pool);

            pools.add(pool);
        }

        return pools;
    }

    private List<TenantOrg> createTenants(int count, String label, String indexKey) {
        List<TenantOrg> tenants = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            TenantOrg t = new TenantOrg();
            t.setId(URIUtil.createId(TenantOrg.class));
            t.setLabel(label);
            t.setRoleAssignments(new StringSetMap());
            t.addRole(indexKey, "role1");
            t.addRole(indexKey, "role2");
            _dbClient.createObject(t);
            tenants.add(t);
        }

        return tenants;
    }

    @Test
    public void testDecommisionedConstraintWithPageinate() throws Exception {
        int objCount = 10;
        List<VirtualArray> varrays = createVirtualArrays(10, "foo");
        List<URI> ids = _dbClient.queryByType(VirtualArray.class, true);
        int count = 0;
        for (@SuppressWarnings("unused")
        URI ignore : ids) {
            count++;
        }

        Assert.assertEquals(objCount, count);

        testQueryByType(VirtualArray.class, true, objCount);
        testQueryByType(VirtualArray.class, false, objCount);

        // delete two virtual arrays
        _dbClient.markForDeletion(varrays.get(0));
        _dbClient.markForDeletion(varrays.get(1));

        testQueryByType(VirtualArray.class, true, objCount - 2);
        testQueryByType(VirtualArray.class, false, objCount);

    }

    private <T extends DataObject> void testQueryByType(Class<T> clazz, boolean activeOnly, int objCount) {
        URI nextId = null;
        int count = 0;
        List<URI> ids;

        while (true) {
            ids = _dbClient.queryByType(clazz, activeOnly, nextId, pageSize);

            if (ids.isEmpty())
            {
                break; // reach the end
            }

            count += ids.size();
            nextId = ids.get(ids.size() - 1);
        }

        Assert.assertEquals(objCount, count);
    }

    @Test
    public void testPrefixPaginateConstraint() throws Exception {
        int objCount = 10;
        List<VirtualArray> varrays = createVirtualArrays(10, "foo");

        Constraint labelConstraint = PrefixConstraint.Factory.getLabelPrefixConstraint(VirtualArray.class, "foo");
        queryInPaginate(labelConstraint, URIQueryResultList.class, objCount, pageSize);
    }

    @Test
    public void testAlternateIdPaginateConstraint() throws Exception {
        int objCount = 10;
        List<VirtualPool> vpools = createVirtualPools(10, "GOLD", VirtualPool.Type.file);

        Constraint constraint = AlternateIdConstraint.Factory.getVpoolTypeVpoolConstraint(VirtualPool.Type.file);
        queryInPaginate(constraint, URIQueryResultList.class, objCount, pageSize);
    }

    @Test
    public void testContainmentPaginateConstraint() throws Exception {
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URIUtil.createId(TenantOrg.class));
        _dbClient.createObject(tenant);

        int objCount = 10;
        List<Project> projects = createProjects(objCount, tenant);

        Constraint constraint = ContainmentConstraint.Factory.getTenantOrgProjectConstraint(tenant.getId());
        queryInPaginate(constraint, URIQueryResultList.class, objCount, pageSize);
    }

    @Test
    public void testContainmentLabelPaginateConstraint() throws Exception {
        Project project = new Project();
        project.setId(URIUtil.createId(Project.class));
        _dbClient.createObject(project);

        int objCount = 10;
        List<Volume> volumns = createVolumes(objCount, "bar", project);

        Constraint constraint = ContainmentPrefixConstraint.Factory.getFullMatchConstraint(Volume.class, "project", project.getId(), "bar");
        queryInPaginate(constraint, URIQueryResultList.class, objCount, pageSize);
    }

    @Test
    public void testContainmentPermissionsPaginateConstraint() throws Exception {
        int objCount = 10;
        String indexKey = "indexKey1";
        List<TenantOrg> tenants = createTenants(objCount, "test tenant", indexKey);
        Constraint constraint = ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(indexKey);

        queryInPaginate(constraint, URIQueryResultList.class, objCount, 3, 5);
    }

    @Test
    public void testLabelPaginateConstraint() throws Exception {
        int objCount = 10;
        List<Project> projects = new ArrayList<>(objCount);

        for (int index = 0; index < objCount; index++) {
            Project pj = new Project();
            pj.setId(URIUtil.createId(Project.class));
            pj.setLabel("foo");
            _dbClient.createObject(pj);
            projects.add(pj);
        }

        Constraint constraint = PrefixConstraint.Factory.getFullMatchConstraint(Project.class, "label", "foo");

        queryInPaginate(constraint, URIQueryResultList.class, objCount, 3);

        _logger.info("Cleanup project objects");
    }

    @Test
    public void testContainmentPrefixConstraintWithPageinate() throws Exception {
        TenantOrg tenant = new TenantOrg();
        tenant.setId(URIUtil.createId(TenantOrg.class));
        _dbClient.createObject(tenant);

        String prefix = "99";
        List<Project> projects = createProjects(1000, tenant);

        Constraint constraint = ContainmentPrefixConstraint.Factory.getProjectUnderTenantConstraint(tenant.getId(), prefix);
        queryInPaginate(constraint, URIQueryResultList.class, 11, 3);

        _dbClient.removeObject(projects.toArray(new Project[projects.size()]));
    }

    private <T extends QueryResultList<URI>> void queryInPaginate(Constraint constraint, Class<T> clazz, int objCount, int pageCount)
            throws IllegalAccessException, InstantiationException {
        queryInPaginate(constraint, clazz, objCount, pageCount, objCount / pageCount + 1);
    }

    private <T extends QueryResultList<URI>> void queryInPaginate(Constraint constraint, Class<T> clazz, int objCount, int pageCount,
            int times)
            throws IllegalAccessException, InstantiationException {
        int queryTimes = 0;
        int count = 0;
        URI nextId = null;

        while (true) {
            T result = (T) clazz.newInstance();
            _dbClient.queryByConstraint(constraint, result, nextId, pageCount);

            Iterator<URI> it = result.iterator();

            if (!it.hasNext()) {
                break;
            }

            queryTimes++;
            while (it.hasNext()) {
                nextId = it.next();
                count++;
            }
        }

        Assert.assertEquals(objCount, count);
        Assert.assertEquals(times, queryTimes);
    }

    @Test
    public void testStringSetMap() throws Exception {
        _logger.info("Starting StringSetMap test");

        DbClient dbClient = _dbClient;

        TenantOrg tenant = new TenantOrg();
        tenant.setId(URIUtil.createId(TenantOrg.class));

        StringSetMap sMap = new StringSetMap();
        tenant.setUserMappings(sMap);

        StringSet set = new StringSet();

        set.add("test1");
        sMap.put("key1", set);

        sMap.get("key1").add("test2");
        sMap.get("key1").remove("test1");

        dbClient.persistObject(tenant);

        // record pending
        tenant = dbClient.queryObject(TenantOrg.class, tenant.getId());
        sMap = tenant.getUserMappings();

        set = sMap.get("key1");

        Assert.assertEquals(set.size(), 1);
        Assert.assertEquals(set.contains("test2"), true);
    }

    @Test
    public void testStringSetMapRemove() throws Exception {

        // shows behavior of AbstractChangeTrackingMapSet.clear()

        _logger.info("Starting StringSetMap3 test");

        DbClient dbClient = _dbClient;

        ProtectionSystem ps = new ProtectionSystem();
        ps.setId(URIUtil.createId(ProtectionSystem.class));

        StringSet set1 = new StringSet();
        set1.add("test1");
        set1.add("test2");
        set1.add("test3");
        set1.add("test4");

        StringSet set2 = new StringSet();
        set2.add("test5");
        set2.add("test6");
        set2.add("test7");
        set2.add("test8");

        String key1 = "key1";
        String key2 = "key2";

        StringSetMap sMap = new StringSetMap();
        sMap.put(key1, set1);
        sMap.put(key2, set2);

        ps.setSiteInitiators(sMap);

        dbClient.createObject(ps);

        ProtectionSystem ps1 = dbClient.queryObject(ProtectionSystem.class, ps.getId());

        StringSet set3 = new StringSet();
        set3.add("test9");
        set3.add("test10");
        set3.add("test11");
        set3.add("test12");

        StringSet set4 = new StringSet();
        set4.add("test13");
        set4.add("test14");
        set4.add("test15");
        set4.add("test16");

        String key4 = "key4";
        String key3 = "key3";

        StringSetMap sMap2 = new StringSetMap();
        sMap2.put(key3, set3);
        sMap2.put(key4, set4);

        ps1.getSiteInitiators().remove(key1);
        ps1.getSiteInitiators().remove(key2);
        ps1.getSiteInitiators().putAll(sMap2);

        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key1));
        Assert.assertTrue(ps1.getSiteInitiators().get(key1).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key2));
        Assert.assertTrue(ps1.getSiteInitiators().get(key2).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key3));
        Assert.assertFalse(ps1.getSiteInitiators().get(key3).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key4));
        Assert.assertFalse(ps1.getSiteInitiators().get(key4).isEmpty());

        dbClient.persistObject(ps1);

        ProtectionSystem ps2 = dbClient.queryObject(ProtectionSystem.class, ps.getId());

        Assert.assertFalse(ps2.getSiteInitiators().containsKey(key1));
        Assert.assertFalse(ps2.getSiteInitiators().containsKey(key2));
        Assert.assertTrue(ps2.getSiteInitiators().containsKey(key3));
        Assert.assertFalse(ps2.getSiteInitiators().get(key3).isEmpty());
        Assert.assertTrue(ps2.getSiteInitiators().containsKey(key4));
        Assert.assertFalse(ps2.getSiteInitiators().get(key4).isEmpty());

    }

    @Test
    public void testStringSetMapClear() throws Exception {

        // shows behavior of AbstractChangeTrackingMapSet.clear()

        _logger.info("Starting StringSetMap3 test");

        DbClient dbClient = _dbClient;

        ProtectionSystem ps = new ProtectionSystem();
        ps.setId(URIUtil.createId(ProtectionSystem.class));

        StringSet set1 = new StringSet();
        set1.add("test1");
        set1.add("test2");
        set1.add("test3");
        set1.add("test4");

        StringSet set2 = new StringSet();
        set2.add("test5");
        set2.add("test6");
        set2.add("test7");
        set2.add("test8");

        String key1 = "key1";
        String key2 = "key2";

        StringSetMap sMap = new StringSetMap();
        sMap.put(key1, set1);
        sMap.put(key2, set2);

        ps.setSiteInitiators(sMap);

        dbClient.createObject(ps);

        ProtectionSystem ps1 = dbClient.queryObject(ProtectionSystem.class, ps.getId());

        StringSet set3 = new StringSet();
        set3.add("test9");
        set3.add("test10");
        set3.add("test11");
        set3.add("test12");

        StringSet set4 = new StringSet();
        set4.add("test13");
        set4.add("test14");
        set4.add("test15");
        set4.add("test16");

        String key4 = "key4";
        String key3 = "key3";

        StringSetMap sMap2 = new StringSetMap();
        sMap2.put(key3, set3);
        sMap2.put(key4, set4);

        ps1.getSiteInitiators().clear();
        ps1.getSiteInitiators().putAll(sMap2);

        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key1));
        Assert.assertTrue(ps1.getSiteInitiators().get(key1).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key2));
        Assert.assertTrue(ps1.getSiteInitiators().get(key2).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key3));
        Assert.assertFalse(ps1.getSiteInitiators().get(key3).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key4));
        Assert.assertFalse(ps1.getSiteInitiators().get(key4).isEmpty());

        dbClient.persistObject(ps1);

        ProtectionSystem ps2 = dbClient.queryObject(ProtectionSystem.class, ps.getId());

        Assert.assertFalse(ps2.getSiteInitiators().containsKey(key1));
        Assert.assertFalse(ps2.getSiteInitiators().containsKey(key2));
        Assert.assertTrue(ps2.getSiteInitiators().containsKey(key3));
        Assert.assertFalse(ps2.getSiteInitiators().get(key3).isEmpty());
        Assert.assertTrue(ps2.getSiteInitiators().containsKey(key4));
        Assert.assertFalse(ps2.getSiteInitiators().get(key4).isEmpty());

    }

    @Test
    public void testStringSetMapReplace() throws Exception {

        // shows behavior of AbstractChangeTrackingMapSet.clear()

        _logger.info("Starting StringSetMapReplace test");

        DbClient dbClient = _dbClient;

        ProtectionSystem ps = new ProtectionSystem();
        ps.setId(URIUtil.createId(ProtectionSystem.class));

        StringSet set1 = new StringSet();
        set1.add("test1");
        set1.add("test2");
        set1.add("test3");
        set1.add("test4");

        StringSet set2 = new StringSet();
        set2.add("test5");
        set2.add("test6");
        set2.add("test7");
        set2.add("test8");

        String key1 = "key1";
        String key2 = "key2";

        StringSetMap sMap = new StringSetMap();
        sMap.put(key1, set1);
        sMap.put(key2, set2);

        ps.setSiteInitiators(sMap);

        dbClient.createObject(ps);

        ProtectionSystem ps1 = dbClient.queryObject(ProtectionSystem.class, ps.getId());

        StringSet set3 = new StringSet();
        set3.add("test9");
        set3.add("test10");
        set3.add("test11");
        set3.add("test12");

        StringSet set4 = new StringSet();
        set4.add("test13");
        set4.add("test14");
        set4.add("test15");
        set4.add("test16");

        String key4 = "key4";
        String key3 = "key3";

        StringSetMap sMap2 = new StringSetMap();
        sMap2.put(key3, set3);
        sMap2.put(key4, set4);

        ps1.getSiteInitiators().replace(sMap2);

        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key1));
        Assert.assertTrue(ps1.getSiteInitiators().get(key1).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key2));
        Assert.assertTrue(ps1.getSiteInitiators().get(key2).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key3));
        Assert.assertFalse(ps1.getSiteInitiators().get(key3).isEmpty());
        Assert.assertTrue(ps1.getSiteInitiators().containsKey(key4));
        Assert.assertFalse(ps1.getSiteInitiators().get(key4).isEmpty());

        dbClient.persistObject(ps1);

        ProtectionSystem ps2 = dbClient.queryObject(ProtectionSystem.class, ps.getId());
        Assert.assertFalse(ps2.getSiteInitiators().containsKey(key1));
        Assert.assertFalse(ps2.getSiteInitiators().containsKey(key2));
        Assert.assertTrue(ps2.getSiteInitiators().containsKey(key3));
        Assert.assertFalse(ps2.getSiteInitiators().get(key3).isEmpty());
        Assert.assertTrue(ps2.getSiteInitiators().containsKey(key4));
        Assert.assertFalse(ps2.getSiteInitiators().get(key4).isEmpty());

    }

    @Test
    public void testOutOfOrderStatusUpdate() throws Exception {
        _logger.info("Starting out of order update test");

        DbClient dbClient = _dbClient;

        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("foobar");

        // record ready
        fs.setOpStatus(new OpStatusMap());
        fs.getOpStatus().put("key", new Operation("ready", "xyz"));
        dbClient.persistObject(fs);

        // record pending
        fs = dbClient.queryObject(FileShare.class, fs.getId());
        fs.getOpStatus().put("key", new Operation("pending", "xyz"));
        dbClient.persistObject(fs);

        // verify status is ready
        fs = dbClient.queryObject(FileShare.class, fs.getId());
        Assert.assertEquals(fs.getOpStatus().get("key").getStatus(), "ready");
    }

    @Test
    public void testEncryption() throws Exception {
        _logger.info("Starting encryption test");
        final int count = 100;

        Map<URI, UserSecretKey> expected = new HashMap<>();
        DbClient dbClient = _dbClient;
        TypeMap.setEncryptionProviders(_encryptionProvider, _encryptionProvider);
        for (int index = 0; index < count; index++) {
            UserSecretKey key = new UserSecretKey();
            key.setId(URIUtil.createId(UserSecretKey.class));
            key.setFirstKey(UUID.randomUUID().toString());
            key.setSecondKey("");
            expected.put(key.getId(), key);
            dbClient.persistObject(key);
        }
        Iterator<URI> it = expected.keySet().iterator();
        while (it.hasNext()) {
            URI id = it.next();
            UserSecretKey original = expected.get(id);
            UserSecretKey queried = dbClient.queryObject(UserSecretKey.class, id);
            Assert.assertEquals(original.getFirstKey(), queried.getFirstKey());
            Assert.assertEquals(original.getSecondKey(), queried.getSecondKey());
        }
        // set encryption provider to null, so, we can read and write out in plain text
        TypeMap.setEncryptionProviders(null, null);
        UserSecretKey queried = null;
        it = expected.keySet().iterator();
        while (it.hasNext()) {
            URI id = it.next();
            UserSecretKey original = expected.get(id);
            queried = dbClient.queryObject(UserSecretKey.class, id);
            Assert.assertFalse(original.getFirstKey().equals(queried.getFirstKey()));
            Assert.assertFalse(original.getSecondKey().equals(queried.getSecondKey()));
        }
        queried.setSecondKey("");
        dbClient.persistObject(queried);
        TypeMap.setEncryptionProviders(_encryptionProvider, _encryptionProvider);
        // set the encryption provider, try to read plain data via a provider
        // the provider will reject it == this is a state we should never be in
        boolean good = false;
        try {
            queried = dbClient.queryObject(UserSecretKey.class, queried.getId());
        } catch (IllegalStateException ex) {
            good = true;
        }
        Assert.assertTrue(good);

        // set encryption back so that the objects can be deleted
        queried.setSecondKey("");
        dbClient.persistObject(queried);

        _logger.info("Ended encryption test");
    }

    @Test
    public void testConfigOverride() {
        final int newTtl = 1234;

        TypeMap.DataObjectFieldConfiguration doConfig = new TypeMap.DataObjectFieldConfiguration();
        doConfig.setDoClass(FileShare.class);
        doConfig.setFieldName("status");
        doConfig.setTtl(newTtl);
        TypeMap.loadDataObjectConfiguration(Arrays.asList(doConfig));

        DataObjectType fsType = TypeMap.getDoType(FileShare.class);
        Assert.assertEquals(fsType.getColumnField("status").getTtl().intValue(), newTtl);

        TypeMap.TimeSeriesConfiguration tsConfig = new TypeMap.TimeSeriesConfiguration();
        tsConfig.setTsClass(EventTimeSeries.class);
        tsConfig.setTtl(newTtl);
        TypeMap.loadTimeSeriesConfiguration(Arrays.asList(tsConfig));

        TimeSeriesType tsType = TypeMap.getTimeSeriesType(EventTimeSeries.class);
        Assert.assertEquals(tsType.getTtl().intValue(), newTtl);
    }

    @Test
    public void testNamedURI() throws Exception {
        _logger.info("Starting named uri test");
        int objCount = 1000;
        String prefix = "99";
        URI target = URIUtil.createId(TenantOrg.class);

        Set<URI> expectedResult = new HashSet<URI>();
        DbClient dbClient = _dbClient;
        for (int index = 0; index < objCount; index++) {
            Project pj = new Project();
            pj.setId(URIUtil.createId(Project.class));
            String label = String.format("%1$d :/#$#@$\\: Test Label", index);
            pj.setLabel(label);
            pj.setTenantOrg(new NamedURI(target, label));
            if (label.startsWith(prefix)) {
                expectedResult.add(pj.getId());
            }
            dbClient.persistObject(pj);
        }

        List<URI> result = dbClient.queryByConstraint(ContainmentPrefixConstraint.Factory.
                getProjectUnderTenantConstraint(target, prefix));

        Assert.assertEquals(result.size(), expectedResult.size());
        for (int i = 0; i < result.size(); i++) {
            Assert.assertTrue(expectedResult.contains(result.get(i)));
        }

        String newPrefix = "xxx";
        Iterator<URI> it = expectedResult.iterator();
        while (it.hasNext()) {
            Project pj = dbClient.queryObject(Project.class, it.next());
            pj.setLabel(newPrefix + pj.getLabel());
            pj.setTenantOrg(new NamedURI(pj.getTenantOrg().getURI(), pj.getLabel()));
            dbClient.persistObject(pj);
            pj = dbClient.queryObject(Project.class, pj.getId());
        }

        result = dbClient.queryByConstraint(ContainmentPrefixConstraint.Factory.
                getProjectUnderTenantConstraint(target, newPrefix));
        Assert.assertEquals(result.size(), expectedResult.size());
        for (int i = 0; i < result.size(); i++) {
            Assert.assertTrue(expectedResult.contains(result.get(i)));
        }

        result = dbClient.queryByConstraint(ContainmentPrefixConstraint.Factory.
                getProjectUnderTenantConstraint(target, prefix));
        Assert.assertEquals(result.size(), 0);

        _logger.info("Done with named uri test");
    }

    @Test
    public void testTagIndex() throws Exception {
        _logger.info("Starting testTagIndex");
        final int objCount = 1000;
        final String prefix = "55";

        Set<URI> expectedResult = new HashSet<URI>();
        URI tenant = URIUtil.createId(TenantOrg.class);

        DbClient dbClient = _dbClient;
        for (int index = 0; index < objCount; index++) {
            FileShare fs = new FileShare();
            fs.setId(URIUtil.createId(FileShare.class));
            fs.setLabel("foobar");
            String tag = String.format("%1$d - test label", index);
            if (tag.startsWith(prefix)) {
                expectedResult.add(fs.getId());
            }
            ScopedLabelSet tagSet = new ScopedLabelSet();
            tagSet.add(new ScopedLabel(tenant.toString(), tag));
            fs.setTag(tagSet);
            dbClient.persistObject(fs);
        }
        List<URI> hits = dbClient.queryByConstraint(
                PrefixConstraint.Factory.getTagsPrefixConstraint(FileShare.class, prefix, null));

        Assert.assertEquals(hits.size(), expectedResult.size());
        for (int i = 0; i < hits.size(); i++) {
            Assert.assertTrue(expectedResult.contains(hits.get(i)));
        }

        hits = dbClient.queryByConstraint(PrefixConstraint.Factory.getTagsPrefixConstraint(FileShare.class,
                prefix, tenant));
        Assert.assertEquals(hits.size(), expectedResult.size());
        for (int i = 0; i < hits.size(); i++) {
            Assert.assertTrue(expectedResult.contains(hits.get(i)));
        }

        hits = dbClient.queryByConstraint(PrefixConstraint.Factory.getTagsPrefixConstraint(FileShare.class,
                "foobar", tenant));
        Assert.assertEquals(hits.size(), 0);
    }

    @Test
    public void testLabelIndex() throws Exception {
        _logger.info("Starting testLabelIndex");
        final int objCount = 10000;
        final String prefix = "99";

        Set<URI> expectedResult = new HashSet<URI>();
        URI project = URIUtil.createId(Project.class);

        DbClient dbClient = _dbClient;
        for (int index = 0; index < objCount; index++) {
            FileShare fs = new FileShare();
            fs.setId(URIUtil.createId(FileShare.class));
            String label = String.format("%1$d - test label", index);
            if (label.startsWith(prefix)) {
                expectedResult.add(fs.getId());
            }
            fs.setLabel(label);
            fs.setProject(new NamedURI(project, fs.getLabel()));
            dbClient.persistObject(fs);
        }
        List<URI> hits = dbClient.queryByConstraint(
                PrefixConstraint.Factory.getLabelPrefixConstraint(FileShare.class, prefix));

        Assert.assertEquals(hits.size(), expectedResult.size());
        for (int i = 0; i < hits.size(); i++) {
            Assert.assertTrue(expectedResult.contains(hits.get(i)));
        }

        String newPrefix = "xxx";
        Iterator<URI> it = expectedResult.iterator();
        while (it.hasNext()) {
            FileShare fs = dbClient.queryObject(FileShare.class, it.next());
            fs.setLabel(newPrefix + fs.getLabel());
            dbClient.persistObject(fs);
            fs = dbClient.queryObject(FileShare.class, fs.getId());
        }

        hits = dbClient.queryByConstraint(
                PrefixConstraint.Factory.getLabelPrefixConstraint(FileShare.class, newPrefix));
        Assert.assertEquals(hits.size(), expectedResult.size());
        for (int i = 0; i < hits.size(); i++) {
            Assert.assertTrue(expectedResult.contains(hits.get(i)));
        }

        hits = dbClient.queryByConstraint(
                PrefixConstraint.Factory.getLabelPrefixConstraint(FileShare.class, prefix));
        Assert.assertEquals(hits.size(), 0);
    }

    @Test
    public void testRelationIndex() throws Exception {
        _logger.info("Starting testAlternateId");
        final int objCount = 1000;

        DbClient dbClient = _dbClient;
        URI project = URIUtil.createId(Project.class);

        // persist
        for (int index = 0; index < objCount; index++) {
            FileShare fs = new FileShare();
            fs.setId(URIUtil.createId(FileShare.class));
            fs.setLabel("test");
            fs.setProject(new NamedURI(project, fs.getLabel()));
            dbClient.persistObject(fs);
        }

        for (int index = 0; index < objCount; index++) {
            Volume v = new Volume();
            v.setId(URIUtil.createId(Volume.class));
            v.setLabel("foobar");
            v.setProject(new NamedURI(project, v.getLabel()));
            dbClient.persistObject(v);
        }

        List<URI> fs = dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectFileshareConstraint(project));
        Assert.assertEquals(fs.size(), objCount);
    }

    /**
     * Tests object lookup by alias
     * 
     * @throws IOException
     */
    @Test
    public void testAlternateId() throws IOException {
        _logger.info("Starting testAlternateId");
        DbClient dbClient = _dbClient;
        String altId = UUID.randomUUID().toString();

        // persist
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setNativeGuid(altId);
        dbClient.persistObject(fs);

        // verify
        FileShare stdQueryResult = dbClient.queryObject(FileShare.class, fs.getId());
        Assert.assertTrue(stdQueryResult.getId().equals(fs.getId()));
        Assert.assertTrue(stdQueryResult.getLabel().equals(fs.getLabel()));
        Assert.assertTrue(stdQueryResult.getNativeGuid().equals(fs.getNativeGuid()));

        // query by altid
        List<URI> altIdResult = dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getFileShareNativeIdConstraint(fs.getNativeGuid()));
        Assert.assertTrue(altIdResult.size() == 1 && altIdResult.get(0).equals(fs.getId()));

        // Test same altId on multiple types
        Volume v = new Volume();
        v.setId(URIUtil.createId(Volume.class));
        v.setLabel("test");
        v.setNativeGuid(fs.getNativeGuid());
        dbClient.persistObject(v);
        // query by altid
        altIdResult = dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getFileShareNativeIdConstraint(altId));
        Assert.assertTrue(altIdResult.size() == 1 && altIdResult.get(0).equals(fs.getId()));
        // query by altid
        altIdResult = dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(altId));
        Assert.assertTrue(altIdResult.size() == 1 && altIdResult.get(0).equals(v.getId()));

        // test alternate id -> multiple records
        altId = "1.2.3.4";
        StorageSystem d1 = new StorageSystem();
        d1.setId(URIUtil.createId(StorageSystem.class));
        d1.setSmisProviderIP(altId);
        StorageSystem d2 = new StorageSystem();
        d2.setId(URIUtil.createId(StorageSystem.class));
        d2.setSmisProviderIP(altId);
        dbClient.persistObject(d1, d2);
        Set<URI> records = new HashSet<URI>();
        records.add(d1.getId());
        records.add(d2.getId());

        // query by altId
        altIdResult = dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStorageDeviceSmisIpConstraint(altId));
        Assert.assertTrue(altIdResult.size() == 2 &&
                records.contains(altIdResult.get(0)) &&
                records.contains(altIdResult.get(1)));

        // empty alternate id
        StorageSystem d3 = new StorageSystem();
        d3.setId(URIUtil.createId(StorageSystem.class));
        d3.setSmisProviderIP("");
        dbClient.persistObject(d3);

        // list all storage devices (verifies that alternate ID's are stored in separate CF)
        List<URI> device = dbClient.queryByType(StorageSystem.class, true);
        int count = 0;
        for (@SuppressWarnings("unused")
        URI ignore : device) {
            count++;
        }
        Assert.assertEquals(3, count);
    }

    @Test
    public void testCfWithMutipleRelationIndex() {
        _logger.info("Starting testCompareIndexes");
        DbClient dbClient = _dbClient;

        URI varrayId1 = URIUtil.createId(VirtualArray.class);
        VirtualArray varray1 = new VirtualArray();
        varray1.setId(varrayId1);
        varray1.setLabel("varray1");
        dbClient.createObject(varray1);

        Network nw1 = new Network();
        nw1.setId(URIUtil.createId(Network.class));
        nw1.setLabel("networkObj");

        StringSet varrayStrSet1 = new StringSet();
        varrayStrSet1.add(varrayId1.toString());
        nw1.setAssignedVirtualArrays(varrayStrSet1);
        dbClient.createObject(nw1);

        List<Network> assignedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, varrayId1, Network.class, "assignedVirtualArrays");

        Assert.assertTrue(assignedNetworks.iterator().hasNext());
        Assert.assertEquals(assignedNetworks.size(), 1);

        List<Network> connectedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, varrayId1, Network.class, "connectedVirtualArrays");

        Assert.assertFalse(connectedNetworks.iterator().hasNext());

        URI varrayId2 = URIUtil.createId(VirtualArray.class);
        VirtualArray varray2 = new VirtualArray();
        varray2.setId(varrayId2);
        varray2.setLabel("varray2");
        dbClient.createObject(varray2);

        StringSet varrayStrSet2 = new StringSet();
        varrayStrSet2.add(varrayId1.toString());
        varrayStrSet2.add(varrayId2.toString());

        nw1.setConnectedVirtualArrays(varrayStrSet2);

        dbClient.persistObject(nw1);

        assignedNetworks.clear();
        assignedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, varrayId1, Network.class, "assignedVirtualArrays");

        Assert.assertTrue(assignedNetworks.iterator().hasNext());
        Assert.assertEquals(assignedNetworks.size(), 1);

        connectedNetworks.clear();
        connectedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, varrayId2, Network.class, "connectedVirtualArrays");

        Assert.assertTrue(connectedNetworks.iterator().hasNext());
        Assert.assertEquals(connectedNetworks.size(), 1);

        connectedNetworks.clear();
        connectedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, varrayId1, Network.class, "connectedVirtualArrays");

        Assert.assertTrue(connectedNetworks.iterator().hasNext());
        Assert.assertEquals(connectedNetworks.size(), 1);

        assignedNetworks.clear();
        assignedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, varrayId2, Network.class, "assignedVirtualArrays");

        Assert.assertFalse(assignedNetworks.iterator().hasNext());

    }

    /**
     * Tests object lookup by alias
     * 
     * @throws IOException
     */
    @Test
    public void testAlternateIdFromStringSet() throws IOException {
        _logger.info("Starting testAlternateIdFromStringSet");
        DbClient dbClient = _dbClient;
        String altId = UUID.randomUUID().toString();

        // persist
        AuthnProvider provider = new AuthnProvider();
        provider.setId(URIUtil.createId(AuthnProvider.class));
        provider.setLabel("test-provider");
        provider.setDescription("test provider");
        StringSet domains = new StringSet();
        domains.add("test1.com");
        domains.add("test2.com");
        provider.setDomains(domains);
        dbClient.persistObject(provider);

        // verify
        AuthnProvider stdQueryResult = dbClient.queryObject(AuthnProvider.class, provider.getId());
        Assert.assertTrue(stdQueryResult.getId().equals(provider.getId()));
        Assert.assertTrue(stdQueryResult.getLabel().equals(provider.getLabel()));

        Assert.assertEquals(stdQueryResult.getDomains().size(), provider.getDomains().size());
        for (String domain : domains) {
            Assert.assertTrue(stdQueryResult.getDomains().contains(domain));
        }

        // query by altid
        for (String domain : domains) {
            URIQueryResultList altIdResult = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getAuthnProviderDomainConstraint(domain), altIdResult);
            Assert.assertTrue(altIdResult.iterator().hasNext());
        }

        stdQueryResult.getDomains().remove("test2.com");
        domains.remove("test2.com");
        domains.add("test3.com");
        stdQueryResult.getDomains().add("test3.com");
        dbClient.persistObject(stdQueryResult);

        stdQueryResult = dbClient.queryObject(AuthnProvider.class, provider.getId());
        Assert.assertTrue(stdQueryResult.getId().equals(provider.getId()));
        Assert.assertTrue(stdQueryResult.getLabel().equals(provider.getLabel()));

        // query by altid
        for (String domain : domains) {
            URIQueryResultList altIdResult = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getAuthnProviderDomainConstraint(domain), altIdResult);
            Assert.assertTrue(altIdResult.iterator().hasNext());
        }
        URIQueryResultList altIdResult = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getAuthnProviderDomainConstraint("test-subtenant"), altIdResult);
        Assert.assertFalse(altIdResult.iterator().hasNext());
    }

    @Test
    public void testTimeSeriesMd() throws Exception {
        TimeSeriesMetadata md = TypeMap.getTimeSeriesType(EventTimeSeries.class);
        Assert.assertEquals(md.getName(), "EventTimeSeries");
        BucketGranularity granularity = EventTimeSeries.class.getAnnotation(BucketGranularity.class);
        Assert.assertEquals(md.getSupportedQueryGranularity().size(), 3);
        Assert.assertArrayEquals(md.getSupportedQueryGranularity().toArray(),
                new TimeSeriesMetadata.TimeBucket[] { TimeSeriesMetadata.TimeBucket.SECOND,
                        TimeSeriesMetadata.TimeBucket.MINUTE, TimeSeriesMetadata.TimeBucket.HOUR });
    }

    @Test
    public void testTimeSeries() throws Exception {
        _logger.info("Starting testTimeSeries");
        final int perThreadCount = 100000;
        final int batchCount = 100;
        final int numThreads = 5;

        final DbClient dbClient = _dbClient;

        // write
        final Event e = new Event();
        e.setEventType("randomstuff");
        e.setVirtualPool(URI.create("urn:storageos:VirtualPool:random"));
        e.setEventId("abc");
        e.setProjectId(URI.create("urn:storageos:Project:abcrandom"));
        e.setResourceId(URI.create("urn:storageos:FileShare:random"));
        e.setSeverity("REALLY BAD");
        e.setUserId(URI.create("urn:storageos:User:foobar"));
        e.setTimeInMillis(-1);
        DateTime dateTime = new DateTime(DateTimeZone.UTC);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        long duration = System.currentTimeMillis();
        for (int index = 0; index < numThreads; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    for (int index = 0; index < perThreadCount / batchCount; index++) {
                        Event[] batch = new Event[batchCount];
                        for (int j = 0; j < batchCount; j++) {
                            batch[j] = e;
                        }
                        dbClient.insertTimeSeries(EventTimeSeries.class, batch);
                    }
                    return null;
                }
            });
        }
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        duration = System.currentTimeMillis() - duration;
        _logger.info("Insertion throughput with batch size {} is {} records per second", batchCount,
                (perThreadCount * numThreads) / (duration / 1000f));

        // read at default granularity
        executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads * perThreadCount);
        DummyQueryResult result = new DummyQueryResult(latch);
        _logger.info("Starting query with default granularity");
        duration = System.currentTimeMillis();
        dbClient.queryTimeSeries(EventTimeSeries.class, dateTime, result, executor);
        Assert.assertTrue(latch.await(60, TimeUnit.SECONDS));
        duration = System.currentTimeMillis() - duration;
        _logger.info("Read throughput(HOUR) is {} records per second",
                (perThreadCount * numThreads) / (duration / 1000f));

        // read at minute granularity
        int total = numThreads * perThreadCount;
        latch = new CountDownLatch(total);
        result = new DummyQueryResult(latch, dateTime.getMinuteOfHour());
        _logger.info("Starting query with MINUTE bucket");
        duration = System.currentTimeMillis();
        dbClient.queryTimeSeries(EventTimeSeries.class, dateTime, TimeSeriesMetadata.TimeBucket.MINUTE, result, executor);
        Assert.assertTrue(latch.getCount() >= 0);
        _logger.info("Records at time {}: {}", dateTime.toString(), total - latch.getCount());
        duration = System.currentTimeMillis() - duration;
        _logger.info("Read throughput(MINUTE) is {} records per second",
                total / (duration / 1000f));

        // read at second granularity
        latch = new CountDownLatch(total);
        result = new DummyQueryResult(latch, dateTime.getMinuteOfHour(), dateTime.getSecondOfMinute());
        _logger.info("Starting query with SECOND bucket");
        duration = System.currentTimeMillis();
        dbClient.queryTimeSeries(EventTimeSeries.class, dateTime, TimeSeriesMetadata.TimeBucket.SECOND, result, executor);
        Assert.assertTrue(latch.getCount() >= 0);
        _logger.info("Records at time {}: {}", dateTime.toString(), total - latch.getCount());
        duration = System.currentTimeMillis() - duration;
        _logger.info("Read throughput(SECOND) is {} records per second",
                total / (duration / 1000f));

        _logger.info("Finished testTimeSeries");
    }

    @Test
    public void testTimeSeriesWithTimestamp() throws Exception {
        // org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.INFO);
        _logger.info("Starting testTimeSeriesWithTimestamp");
        final int perThreadCount = 10;
        final int numThreads = 5;

        final DbClient dbClient = _dbClient;

        // write
        final Event e = new Event();
        e.setEventType("randomstuff");
        e.setVirtualPool(URI.create("urn:storageos:VirtualPool:random"));
        e.setEventId("abc");
        e.setProjectId(URI.create("urn:storageos:Project:abcrandom"));
        e.setResourceId(URI.create("urn:storageos:FileShare:random"));
        e.setSeverity("REALLY BAD");
        e.setUserId(URI.create("urn:storageos:User:foobar"));
        e.setTimeInMillis(-1);
        // Given time for data points of time series
        final DateTime dateTime = new DateTime(2000, 1, 1, 0, 0, DateTimeZone.UTC);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        long duration = System.currentTimeMillis();
        for (int threadIndex = 0; threadIndex < numThreads; threadIndex++) {
            final int threadId = threadIndex;
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    for (int index = 0; index < perThreadCount; index++) {
                        long millis = dateTime.getMillis() + (long) ((perThreadCount) * threadId + index) * 1000;
                        DateTime time = new DateTime(millis, DateTimeZone.UTC);
                        dbClient.insertTimeSeries(EventTimeSeries.class, time, e);
                    }
                    return null;
                }
            });
        }
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        duration = System.currentTimeMillis() - duration;
        _logger.info("Insertion throughput with batch size {} is {} records per second", 1,
                (perThreadCount * numThreads) / (duration / 1000f));

        // read at default granularity
        executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads * perThreadCount);
        DummyQueryResult result = new DummyQueryResult(latch);
        _logger.info("Starting query with default granularity");
        duration = System.currentTimeMillis();
        dbClient.queryTimeSeries(EventTimeSeries.class, dateTime, result, executor);
        Assert.assertTrue(latch.await(60, TimeUnit.SECONDS));
        duration = System.currentTimeMillis() - duration;
        _logger.info("Read throughput(HOUR) is {} records per second",
                (perThreadCount * numThreads) / (duration / 1000f));

        _logger.info("Finished testTimeSeriesWithTimestamp");
    }

    @Test
    public void testQueryField() throws Exception {
        DbClient dbClient = _dbClient;
        OpStatusMap status = new OpStatusMap();
        String taskname = "task1";
        Operation op = new Operation(Operation.Status.pending.toString(), "test");
        op.setDescription("descr");
        status.createTaskStatus(taskname, op);
        String label = "test system";

        StorageSystem system1 = new StorageSystem();
        system1.setId(URIUtil.createId(StorageSystem.class));
        system1.setLabel(label);
        system1.setInactive(true);
        system1.setOpStatus(status);

        StorageSystem system2 = new StorageSystem();
        system2.setId(URIUtil.createId(StorageSystem.class));
        system2.setLabel(label);
        system2.setInactive(true);
        system2.setOpStatus(status);

        dbClient.persistObject(system1, system2);

        List<URI> uris = new ArrayList<URI>();
        uris.add(system1.getId());
        uris.add(system2.getId());
        List<StorageSystem> systems = dbClient.queryObjectField(StorageSystem.class, "label", uris);
        Assert.assertEquals(2, systems.size());
        Assert.assertTrue(systems.get(0).getLabel().equals(label));
        Assert.assertTrue(systems.get(1).getLabel().equals(label));

        Iterator<StorageSystem> it = dbClient.queryIterativeObjectField(StorageSystem.class, "label", uris);
        int count = 0;
        while (it.hasNext()) {
            count++;
            StorageSystem obj = it.next();
            Assert.assertTrue(obj.getLabel().equals(label));
        }
        Assert.assertEquals(2, count);

        systems = dbClient.queryObjectField(StorageSystem.class, "inactive", uris);
        Assert.assertEquals(2, systems.size());
        Assert.assertTrue(systems.get(0).getInactive());
        Assert.assertTrue(systems.get(1).getInactive());

        systems = dbClient.queryObjectField(StorageSystem.class, "status", uris);
        Assert.assertEquals(2, systems.size());
        Assert.assertNotNull(systems.get(0).getOpStatus().get(taskname));
        Assert.assertNotNull(systems.get(1).getOpStatus().get(taskname));
    }

    @Test
    public void testBulkReadResources() throws Exception {
        DbClient dbClient = _dbClient;

        // test the bulk read with 100 (default buvket size defiend in db)

        List<URI> fsIds = createDummyFileSharesinDB(dbClient, 102400L, null, 100);
        List<URI> checkFsIds = new ArrayList<URI>(fsIds);
        Iterator<FileShare> fShares = dbClient.queryIterativeObjects(
                FileShare.class, fsIds);

        int count = 0;
        while (fShares.hasNext()) {
            count++;
            FileShare fShare = fShares.next();
            checkFsIds.remove(fShare.getId());
            Assert.assertEquals(fShare.getCapacity().longValue(), 102400L);
            // System.out.println("Id: " + fShare.getId() + " vpool: " +
            // fShare.getCos()
            // + " Capacity: " + fShare.getCapacity());
        }
        Assert.assertEquals(count, 100);
        Assert.assertEquals(checkFsIds.size(), 0);

        // test the bulk read with less than 100 (default bucket size defined in
        // db is 100)
        fsIds = createDummyFileSharesinDB(dbClient, 1024L, null, 10);
        checkFsIds = new ArrayList<URI>(fsIds);
        fShares = dbClient.queryIterativeObjects(FileShare.class, fsIds);

        count = 0;
        while (fShares.hasNext()) {
            count++;
            FileShare fShare = fShares.next();
            checkFsIds.remove(fShare.getId());
            Assert.assertEquals(fShare.getCapacity().longValue(), 1024L);
            // System.out.println("Id: " + fShare.getId() + " vpool: " +
            // fShare.getCos()
            // + " Capacity: " + fShare.getCapacity());
        }
        Assert.assertEquals(count, 10);
        Assert.assertEquals(checkFsIds.size(), 0);

        // test the bulk read with more than 100 (default bucket size defined in
        // db is 100)
        fsIds = createDummyFileSharesinDB(dbClient, 512L, null, 1000);
        checkFsIds = new ArrayList<URI>(fsIds);
        fShares = dbClient.queryIterativeObjects(FileShare.class, fsIds);

        count = 0;
        while (fShares.hasNext()) {
            count++;
            FileShare fShare = fShares.next();
            checkFsIds.remove(fShare.getId());
            Assert.assertEquals(fShare.getCapacity().longValue(), 512L);
            // System.out.println("Id: " + fShare.getId() + " vpool: " +
            // fShare.getCos()
            // + " Capacity: " + fShare.getCapacity());
        }
        Assert.assertEquals(count, 1000);
        Assert.assertEquals(checkFsIds.size(), 0);

        // check for concurrent modification on the list.
        // concurrent modification not allowed
        fsIds = createDummyFileSharesinDB(dbClient, 1L, null, 500);
        try {
            fShares = dbClient.queryIterativeObjects(FileShare.class, fsIds);
            while (fShares.hasNext()) {
                fShares.next();
                fsIds.add(URI.create("DUMMY_STRING"));
            }
        } catch (ConcurrentModificationException e) {
            Assert.assertTrue(e != null);
        }

        // Add invalid URI in the request List
        fsIds = createDummyFileSharesinDB(dbClient, 1000L, null, 10);
        fsIds.add(URI.create("INVALID_ID1"));
        fsIds.add(URI.create("INVALID_ID2"));
        checkFsIds = new ArrayList<URI>(fsIds);

        fShares = dbClient.queryIterativeObjects(FileShare.class, fsIds);
        count = 0;
        while (fShares.hasNext()) {
            count++;
            FileShare fShare = fShares.next();
            checkFsIds.remove(fShare.getId());
            Assert.assertEquals(fShare.getCapacity().longValue(), 1000L);
            // System.out.println("Id: " + fShare.getId() + " vpool: " +
            // fShare.getCos()
            // + " Capacity: " + fShare.getCapacity());
        }
        Assert.assertEquals(count, 10);
        Assert.assertEquals(checkFsIds.size(), 2);

        // test for a bucket of invalid ids in the middle

        fsIds = createDummyFileSharesinDB(dbClient, 200L, null, 100);

        // remove 100 that falls in one middle bucket
        for (int i = 0; i < 200; i++) {
            fsIds.add(URI.create("INVALID_ID2" + System.nanoTime()));
        }

        List<URI> fsIds2 = createDummyFileSharesinDB(dbClient, 200L, null, 150);

        List<URI> originalList = new ArrayList<URI>();
        originalList.addAll(fsIds);
        originalList.addAll(fsIds2);
        checkFsIds = new ArrayList<URI>(originalList);
        fShares = dbClient.queryIterativeObjects(FileShare.class, originalList);
        count = 0;
        while (fShares.hasNext()) {
            count++;
            FileShare fShare = fShares.next();
            checkFsIds.remove(fShare.getId());
            Assert.assertEquals(fShare.getCapacity().longValue(), 200L);
        }
        Assert.assertEquals(count, 250);
        Assert.assertEquals(checkFsIds.size(), 200);

        _logger.info("Finished reading the entire list of FileShares");

        // test the bulk read with 100 (default buvket size defiend in db)
        fsIds = createDummyFileSharesinDB(dbClient, 128L, "GOLDEN", 100);
        checkFsIds = new ArrayList<URI>(fsIds);
        fShares = dbClient.queryIterativeObjectField(FileShare.class, "label", fsIds);

        int goldCnt = 0;
        while (fShares.hasNext()) {
            goldCnt++;
            FileShare fShare = fShares.next();
            checkFsIds.remove(fShare.getId());
            Assert.assertEquals(fShare.getLabel(), "GOLDEN");
            // System.out.println("Id: " + fShare.getId() + " vpool: " +
            // fShare.getCos()
            // + " Capacity: " + fShare.getCapacity());
        }
        Assert.assertEquals(goldCnt, 100);
        Assert.assertEquals(checkFsIds.size(), 0);

        // test the bulk read with less than 100 (default buvket size defiend in db)
        fsIds2 = createDummyFileSharesinDB(dbClient, 1280L, "SILVER", 10);
        checkFsIds = new ArrayList<URI>(fsIds2);

        fShares = dbClient.queryIterativeObjectField(FileShare.class, "label", fsIds2);

        int silverCnt = 0;
        while (fShares.hasNext()) {
            silverCnt++;
            FileShare fShare = fShares.next();
            checkFsIds.remove(fShare.getId());
            // System.out.println("Id: " + fShare.getId() + " vpool: " +
            // fShare.getCos()
            // + " Capacity: " + fShare.getCapacity());
        }
        Assert.assertEquals(silverCnt, 10);
        Assert.assertEquals(checkFsIds.size(), 0);

        // test the bulk read with less than 100 (default buvket size defiend in db)
        List<URI> sumList = new ArrayList<URI>();
        sumList.addAll(fsIds);
        sumList.addAll(fsIds2);
        checkFsIds = new ArrayList<URI>(sumList);

        fShares = dbClient.queryIterativeObjectField(FileShare.class, "label", sumList);

        int sumCnt = 0;
        while (fShares.hasNext()) {
            sumCnt++;
            FileShare fShare = fShares.next();
            if (fShare.getLabel().equals("GOLDEN"))
            {
                checkFsIds.remove(fShare.getId());
                // System.out.println("Id: " + fShare.getId() + " vpool: " +
                // fShare.getCos()
                // + " Capacity: " + fShare.getCapacity());
            }
        }
        Assert.assertEquals(sumCnt, 110);
        Assert.assertEquals(checkFsIds.size(), 10);

        // test the bulk read with more than 100 (default buvket size defiend in db)
        fsIds = createDummyFileSharesinDB(dbClient, 12800L, "COPPER", 256);
        checkFsIds = new ArrayList<URI>(fsIds);

        fShares = dbClient.queryIterativeObjectField(FileShare.class, "label", fsIds);

        int copperCnt = 0;
        while (fShares.hasNext()) {
            FileShare fShare = fShares.next();
            checkFsIds.remove(fShare.getId());
            copperCnt++;
            Assert.assertEquals(fShare.getLabel(), "COPPER");
            // System.out.println("Id: " + fShare.getId() + " vpool: " +
            // fShare.getCos()
            // + " Capacity: " + fShare.getCapacity());
        }
        Assert.assertEquals(copperCnt, 256);
        Assert.assertEquals(checkFsIds.size(), 0);

    }

    private List<URI> createDummyFileSharesinDB(DbClient dbClient, long size,
            String label, int count) throws Exception {
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URIUtil.createId(VirtualPool.class));
        vpool.setLabel("GOLD");
        vpool.setType("file");
        vpool.setProtocols(new StringSet());
        dbClient.persistObject(vpool);

        List<URI> fsIds = new ArrayList<URI>();
        for (int i = 0; i < count; i++) {
            FileShare fs = new FileShare();
            fs.setId(URIUtil.createId(FileShare.class));
            if (label == null) {
                fs.setLabel("fileshare" + System.nanoTime());
            } else {
                fs.setLabel(label);
            }
            fs.setCapacity(size);
            fs.setVirtualPool(vpool.getId());
            fs.setOpStatus(new OpStatusMap());
            Operation op = new Operation();
            op.setStatus(Operation.Status.pending.name());
            fs.getOpStatus().put("filesharereq", op);
            dbClient.persistObject(fs);
            fsIds.add(fs.getId());
        }
        return fsIds;
    }

    @Test
    public void testCreationTime() throws Exception {
        _logger.info("Starting testCreationTime");
        DbClient dbClient = _dbClient;
        StorageSystem system = new StorageSystem();
        system.setId(URIUtil.createId(StorageSystem.class));
        dbClient.createObject(system);

        Thread.sleep(100);

        StorageSystem hit = dbClient.queryObject(StorageSystem.class, system.getId());
        Calendar now = Calendar.getInstance();
        Calendar then = hit.getCreationTime();
        Assert.assertNotNull(then);
        Assert.assertTrue(String.format("then(%s) is not before now(%s), should be %s", then.toString(), now.toString(), system
                .getCreationTime().toString()), then.before(now));

        system.setLabel("Update");
        dbClient.persistObject(system);

        hit = dbClient.queryObject(StorageSystem.class, system.getId());
        Calendar thenAgain = hit.getCreationTime();
        Assert.assertNotNull(thenAgain);
        Assert.assertEquals(thenAgain, then);

        _logger.info("Finished testing CreationTime");
    }

    @Test
    public void testQueryByType() {
        DbClient dbClient = _dbClient;
        List<URI> expected = new ArrayList<URI>();
        for (int i = 0; i < 10; i++) {
            AuthnProvider provider = new AuthnProvider();
            provider.setId(URIUtil.createId(AuthnProvider.class));
            provider.setLabel("provider" + i);
            dbClient.createObject(provider);
            expected.add(provider.getId());
        }

        List<AuthnProvider> providerList = new ArrayList<AuthnProvider>();
        for (int i = 10; i < 120; i++) {
            AuthnProvider provider = new AuthnProvider();
            provider.setId(URIUtil.createId(AuthnProvider.class));
            provider.setLabel("provider" + i);
            providerList.add(provider);
            expected.add(provider.getId());
            providerList.add(provider);
        }
        dbClient.createObject(providerList);

        List<URI> match = new ArrayList<URI>(expected);
        List<URI> uris = dbClient.queryByType(AuthnProvider.class, true);
        Iterator<URI> it = uris.iterator();
        Assert.assertTrue(it.hasNext());
        int apCnt = 0;
        while (it.hasNext()) {
            apCnt++;
            URI uri = it.next();
            Assert.assertTrue(match.contains(uri));
            match.remove(uri);
        }
        Assert.assertEquals(apCnt, 120);
        Assert.assertEquals(match.size(), 0);

        // query with active flag
        List<URI> activeOnly = new ArrayList<URI>(expected);
        for (int i = 0; i < 5; i++) {
            AuthnProvider provider = new AuthnProvider();
            provider.setId(expected.get(i));
            provider.setInactive(true);
            dbClient.updateAndReindexObject(provider);
            activeOnly.remove(expected.get(i));
        }
        // all query
        match = new ArrayList<URI>(expected);
        uris = dbClient.queryByType(AuthnProvider.class, false);
        it = uris.iterator();
        Assert.assertTrue(it.hasNext());
        while (it.hasNext()) {
            URI uri = it.next();
            Assert.assertTrue(String.format("URI %s is not contained in match (%d)", uri.toString(), match.size()), match.contains(uri));
            match.remove(uri);
        }
        Assert.assertEquals(0, match.size());
        // active only query - with mixed active/inactive
        uris = dbClient.queryByType(AuthnProvider.class, true);
        it = uris.iterator();
        Assert.assertTrue(it.hasNext());
        while (it.hasNext()) {
            URI uri = it.next();
            Assert.assertTrue(activeOnly.contains(uri));
            activeOnly.remove(uri);
        }
        Assert.assertEquals(0, activeOnly.size());
    }

    @Test
    public void addRemoveSameKeyStringMap() {

        DbClient dbClient = _dbClient;

        String oldValue1 = "one";
        String oldValue2 = "two";
        String oldValue3 = "three";

        String newValue2 = "two two";

        // The map should be:
        // ["1", ["one"]]
        // ["2", ["two"]]
        // ["3", ["three"]]
        StringMap map = new StringMap();
        map.put("1", oldValue1);
        map.put("2", oldValue2);
        map.put("3", oldValue3);

        VirtualPool vpool = new VirtualPool();
        vpool.setId(URIUtil.createId(VirtualPool.class));
        vpool.setLabel("GOLD");
        vpool.setType("file");
        vpool.setProtocols(new StringSet());
        vpool.setHaVarrayVpoolMap(map);

        dbClient.persistObject(vpool);

        VirtualPool queriedVPool = dbClient.queryObject(VirtualPool.class, vpool.getId());

        Assert.assertEquals(3, queriedVPool.getHaVarrayVpoolMap().size());
        Assert.assertEquals(oldValue1, queriedVPool.getHaVarrayVpoolMap().get("1"));
        Assert.assertEquals(oldValue2, queriedVPool.getHaVarrayVpoolMap().get("2"));
        Assert.assertEquals(oldValue3, queriedVPool.getHaVarrayVpoolMap().get("3"));

        queriedVPool.getHaVarrayVpoolMap().remove("2");
        queriedVPool.getHaVarrayVpoolMap().put("2", newValue2);

        Assert.assertEquals(newValue2, queriedVPool.getHaVarrayVpoolMap().get("2"));

        dbClient.persistObject(queriedVPool);

        VirtualPool queriedVPool2 = dbClient.queryObject(VirtualPool.class, vpool.getId());

        Assert.assertEquals(3, queriedVPool2.getHaVarrayVpoolMap().size());
        Assert.assertEquals(oldValue1, queriedVPool2.getHaVarrayVpoolMap().get("1"));
        Assert.assertEquals(newValue2, queriedVPool2.getHaVarrayVpoolMap().get("2"));
        Assert.assertEquals(oldValue3, queriedVPool2.getHaVarrayVpoolMap().get("3"));
    }

    @Test
    public void testDeactivate() {

        List<TenantOrg> tenants = createTenants(1, "tenant", "tenantIndex");
        List<Project> projects = createProjects(1, tenants.iterator().next());

        Project project = projects.iterator().next();

        String msg = checkForDelete(project);

        _logger.info(msg);

        Assert.assertNull(msg);

        _dbClient.markForDeletion(project);

        List<URI> projectIds = new ArrayList<URI>();
        projectIds.add(project.getId());

        List<Project> queriedProjects = _dbClient.queryObject(Project.class, projectIds, true);

        Assert.assertFalse(queriedProjects.iterator().hasNext());

    }

    @Test
    public void testAggregate() {

        VirtualPool curPool = null;
        Map<URI, Volume> curVols = null;
        URI curVolId = null;
        Volume volume = null;
        Map<URI, Map<URI, Volume>> volumes = new HashMap<>();
        Map<URI, List<URI>> volumeIds = new HashMap<>();
        Map<URI, Volume> allVolumes = new HashMap<>();
        List<VirtualPool> pools = new ArrayList<>();
        for (int jj = 0; jj < 2; jj++) {
            URI poolId = URIUtil.createId(VirtualPool.class);
            VirtualPool pool = new VirtualPool();
            pool.setId(poolId);
            _dbClient.createObject(pool);
            pools.add(pool);
            Map<URI, Volume> volMap = new HashMap<>();
            List<URI> volIds = new ArrayList<>();
            for (int ii = 0; ii < 4; ii++) {
                volume = new Volume();
                volume.setId(URIUtil.createId(Volume.class));
                String label = String.format("%1$d.%2$d : Test Label", jj, ii);
                volume.setLabel(label);
                int mult = (int) Math.round(Math.pow(10, jj));
                volume.setCapacity(2000L * (ii + 1) * mult);
                volume.setProvisionedCapacity(3000L * (ii + 1) * mult);
                volume.setAllocatedCapacity(1000L * (ii + 1) * mult);
                volume.setVirtualPool(poolId);
                _dbClient.createObject(volume);
                volMap.put(volume.getId(), volume);
                volIds.add(volume.getId());
                allVolumes.put(volume.getId(), volume);
            }
            volumes.put(poolId, volMap);
            volumeIds.put(poolId, volIds);
        }
        checkAggregatedValues("provisionedCapacity", Volume.class, allVolumes);
        curPool = pools.get(0);
        checkAggregatedValues("virtualPool", curPool.getId().toString(), "provisionedCapacity", Volume.class, volumes.get(curPool.getId()));
        curPool = pools.get(1);
        checkAggregatedValues("virtualPool", curPool.getId().toString(), "provisionedCapacity", Volume.class, volumes.get(curPool.getId()));

        //
        // change capacity of one of the volume and check that results do match...
        //
        curPool = pools.get(0);
        curVols = volumes.get(curPool.getId());
        volume = new Volume();
        volume.setId(volumeIds.get(curPool.getId()).get(2));
        volume.setCapacity(3456L);
        volume.setProvisionedCapacity(3456L);
        volume.setAllocatedCapacity(3456L);
        volume.setLabel("Test Label: capacity modified");
        // Do not need to set pool. Pool should be set by the framework during persist.
        // volume.setVirtualPool( curPool.getId());
        _dbClient.persistObject(volume);
        allVolumes.put(volume.getId(), volume);
        curVols.put(volume.getId(), volume);
        curPool = pools.get(1);
        curVols = volumes.get(curPool.getId());
        volume = new Volume();
        volume.setId(volumeIds.get(curPool.getId()).get(2));
        volume.setCapacity(67890L);
        volume.setProvisionedCapacity(67890L);
        volume.setAllocatedCapacity(67890L);
        volume.setLabel("Test Label: capacity modified");
        // verify that by setting pool, nothing get broken
        volume.setVirtualPool(curPool.getId());
        _dbClient.persistObject(volume);
        allVolumes.put(volume.getId(), volume);
        curVols.put(volume.getId(), volume);
        checkAggregatedValues("provisionedCapacity", Volume.class, allVolumes);
        curPool = pools.get(0);
        checkAggregatedValues("virtualPool", curPool.getId().toString(), "provisionedCapacity", Volume.class, volumes.get(curPool.getId()));
        curPool = pools.get(1);
        checkAggregatedValues("virtualPool", curPool.getId().toString(), "provisionedCapacity", Volume.class, volumes.get(curPool.getId()));

        //
        // modify pool for one of the volume and check results
        //
        curPool = pools.get(0);
        curVolId = volumeIds.get(curPool.getId()).remove(0);
        curVols = volumes.get(curPool.getId());
        curVols.remove(curVolId);
        curPool = pools.get(1);
        // create volume replacement
        volume = new Volume();
        volume.setId(curVolId);
        volume.setVirtualPool(curPool.getId());
        // Do not need to set pool. Pool should be set by the framework during persist.
        // volume.setCapacity(...);
        // volume.setProvisionedCapacity(...);
        // volume.setAllocatedCapacity(...);
        volume.setLabel("Test Label: pool modified");
        _dbClient.persistObject(volume);
        volumeIds.get(curPool.getId()).add(volume.getId());
        volumes.get(curPool.getId()).put(volume.getId(), volume);
        allVolumes.put(volume.getId(), volume);
        checkAggregatedValues("provisionedCapacity", Volume.class, allVolumes);
        curPool = pools.get(0);
        checkAggregatedValues("virtualPool", curPool.getId().toString(), "provisionedCapacity", Volume.class, volumes.get(curPool.getId()));
        curPool = pools.get(1);
        checkAggregatedValues("virtualPool", curPool.getId().toString(), "provisionedCapacity", Volume.class, volumes.get(curPool.getId()));

        //
        // delete one of the entries and validate that results match
        //
        curPool = pools.get(1);
        curVols = volumes.get(curPool.getId());
        curVolId = volumeIds.get(curPool.getId()).remove(1);
        volume = curVols.remove(curVolId);
        allVolumes.remove(curVolId);
        _dbClient.removeObject(volume);
        checkAggregatedValues("provisionedCapacity", Volume.class, allVolumes);
        checkAggregatedValues("virtualPool", curPool.getId().toString(), "provisionedCapacity", Volume.class, curVols);

        _logger.info("finish aggregator");
    }

    @Test
    public void testAggregationEfficiency() {
        int NN = 100000;

        for (int ii = 0; ii < NN; ii++) {
            Volume volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            String label = String.format("%1$d : Test Label", ii);
            volume.setLabel(label);
            volume.setCapacity(2000L);
            volume.setProvisionedCapacity(3000L);
            volume.setAllocatedCapacity(1000L);
            _dbClient.createObject(volume);
            if ((ii + 1) % 50000 == 0) {
                _logger.info("Created " + (ii + 1) + " volumes in the database");
            }
        }
        _logger.info("Waiting for 20 sec  for the DB to do its job. ");
        try {
            Thread.currentThread().sleep(20000);
        } catch (Exception ex) {
            _logger.warn("Thread is interrupted", ex);
        }
        _logger.info("DB should be set by now. Starting the efficiency test ");
        long start = System.currentTimeMillis();
        CustomQueryUtility.AggregatedValue aggregatedValue =
                CustomQueryUtility.aggregatedPrimitiveField(_dbClient, Volume.class, "allocatedCapacity");
        long endNew = System.currentTimeMillis();
        long newTime = endNew - start;
        _logger.info("Aggregation by Agg index: " + aggregatedValue.getValue() +
                "; time : " + newTime);
        long startOld = System.currentTimeMillis();
        Iterator<URI> volIter = _dbClient.queryByType(Volume.class, false).iterator();
        SumPrimitiveFieldAggregator agg = CustomQueryUtility.aggregateActiveObject(
                _dbClient, Volume.class, new String[] { "allocatedCapacity" }, volIter);
        long endOld = System.currentTimeMillis();
        long oldTime = endOld - startOld;
        _logger.info("Aggregation by CF : " + agg.getAggregate("allocatedCapacity") +
                "; time : " + oldTime);

        _logger.info("Aggregation byCF : " + oldTime + "msec; by AggregatedIdx : " + newTime +
                "msec.");

        Assert.assertTrue((long) aggregatedValue.getValue() == (long) agg.getAggregate("allocatedCapacity"));
    }

    private void checkAggregatedValues(String groupBy, String groupByValue,
            String field,
            Class<? extends DataObject> clazz,
            Map<URI, ? extends DataObject> validatedObj) {
        _logger.info("Checking aggregated index for class : " + clazz.getSimpleName() + "; field : " + field +
                ";  groupField = " + groupBy + "; groupValue : " + groupByValue);
        AggregationQueryResultList queryResults = new AggregationQueryResultList();
        _dbClient.queryByConstraint(AggregatedConstraint.Factory.getAggregationConstraint(clazz, groupBy, groupByValue, field),
                queryResults);
        Iterator<AggregationQueryResultList.AggregatedEntry> it = queryResults.iterator();
        checkAggregatedQuery(it, clazz, field, validatedObj);
    }

    private void checkAggregatedValues(String field,
            Class<? extends DataObject> clazz,
            Map<URI, ? extends DataObject> validatedObj) {
        _logger.info("Checking aggregated index for class : " + clazz.getSimpleName() + "; field : " + field);
        AggregationQueryResultList queryResults = new AggregationQueryResultList();
        _dbClient.queryByConstraint(AggregatedConstraint.Factory.getAggregationConstraint(clazz, field),
                queryResults);
        Iterator<AggregationQueryResultList.AggregatedEntry> it = queryResults.iterator();
        checkAggregatedQuery(it, clazz, field, validatedObj);
    }

    private void checkAggregatedQuery(Iterator<AggregationQueryResultList.AggregatedEntry> it,
            Class<? extends DataObject> clazz,
            String field,
            Map<URI, ? extends DataObject> validatedObj) {
        int count = 0;
        while (it.hasNext()) {
            AggregationQueryResultList.AggregatedEntry entry = it.next();
            _logger.info("         " + entry.getId() + ";  capacity = " + entry.getValue().toString());
            DataObject obj = validatedObj.get(entry.getId());
            DataObjectType doType = TypeMap.getDoType(clazz);
            Object value = ColumnField.getFieldValue(doType.getColumnField(field), obj);
            Assert.assertEquals(value, entry.getValue());
            count++;
        }
        Assert.assertEquals(count, validatedObj.size());

    }

    private <T extends DataObject> String checkForDelete(T object) {
        Class<? extends DataObject> clazz = object.getClass();
        URI id = object.getId();

        String depMsg = _geoDependencyChecker.checkDependencies(id, clazz, true);
        if (depMsg != null) {
            return depMsg;
        }

        return object.canBeDeleted();
    }

    private int size(QueryResultList<URI> list) {
        int count = 0;
        Iterator<URI> itr = list.iterator();
        while (itr.hasNext()) {
            itr.next();
            count++;
        }
        return count;
    }

    @Test
    public void testRemoveIndex() {
        StoragePool pool = createStoragePools(1, "storagepool").iterator().next();
        List<Volume> vols = createVolumes(3, "volume", null, pool);
        Constraint constraint = ContainmentConstraint.Factory.getVirtualPoolVolumeConstraint(pool.getId());
        URIQueryResultList results = new URIQueryResultList();
        _dbClient.queryByConstraint(constraint, results);

        Assert.assertEquals(size(results), 3);

        // delete the index records
        if (InternalDbClient.class.isAssignableFrom(_dbClient.getClass())) {
            ((InternalDbClient) _dbClient).removeFieldIndex(Volume.class, "pool", "RelationIndex");
            URIQueryResultList results2 = new URIQueryResultList();
            _dbClient.queryByConstraint(constraint, results2);
            Assert.assertFalse(results2.iterator().hasNext());
        } else {
            Assert.fail("testRemoveIndex requires InternalDbClient");
        }

    }

    public static class StepLock {
        public enum Step
        {
            Pre, Read, InsertNewColumns, FetchNewestColumns, CleanupOldColumns, Quit
        }

        public int clientId;
        public Step step;
        public Step feedback;

        public boolean waitForStep(Step stepToWait) {
            synchronized (this) {
                while (this.step != stepToWait) {
                    if (this.step == Step.Quit) {
                        this.feedback = this.step;
                        this.notify();
                        return false;
                    }
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        _logger.warn("Thread is interrupted", e);
                    }
                }

                return true;
            }
        }

        public boolean ackStep(Step targetStep) {
            synchronized (this) {
                if (this.step != targetStep || this.feedback == targetStep) {
                    return false;
                }

                this.feedback = targetStep;
                this.notify();
            }

            return true;
        }

        public void drain(Step targetStep) {
            synchronized (this) {

                for (;;) {
                    if (this.step != this.feedback && this.step.ordinal() <= targetStep.ordinal()) {
                        this.feedback = this.step;
                        this.notify();
                    }

                    if (this.step == targetStep) {
                        return;
                    }

                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        _logger.warn("Thread is interrupted", e);
                    }
                }
            }
        }

        public void moveToState(Step targetStep) {
            synchronized (this) {

                this.step = targetStep;

                // If any thread is currently in .wait(), wake them up (threads not in .wait() are all outside of the lock)
                this.notify();

                while (this.feedback != targetStep) {
                    try {
                        this.wait(); // Release lock and wait for notification
                    } catch (InterruptedException e) {
                        _logger.warn("Thread is interrupted", e);
                    }
                }
            }
        }
    }

    private static class NoBgIndexCleaner extends IndexCleaner {
        @Override
        public void cleanIndexAsync(final RowMutator mutator,
                final DataObjectType doType,
                final SoftReference<IndexCleanupList> listToCleanRef) {
            // Do nothing
        }
    }

    public static class DbClientImplUnitTester extends InternalDbClient {

        // Id for this client that in a race-condition test
        public int clientId;

        public ThreadLocal<StepLock> threadStepLock;

        private Map<Class<? extends DataObject>, Set<URI>> objMap = new HashMap<Class<? extends DataObject>, Set<URI>>();

        @Override
        public String getGeoVersion() {
            return getSchemaVersion();
        }

        @Override
        public <T extends DataObject> void createObject(Collection<T> dataobjects)
                throws DatabaseException {
            addToMap(dataobjects);
            super.createObject(dataobjects);
        }

        @Override
        public <T extends DataObject> void persistObject(Collection<T> dataobjects)
                throws DatabaseException {
            addToMap(dataobjects);
            super.persistObject(dataobjects);
        }

        @Override
        public <T extends DataObject> void persistObject(T object) throws DatabaseException {
            addToMap(object);
            super.persistObject(object);
        }

        @Override
        public <T extends DataObject> void persistObject(T... objects) throws DatabaseException {
            addToMap(objects);
            super.persistObject(objects);
        }

        public void removeAll() {
            for (Entry<Class<? extends DataObject>, Set<URI>> entry : objMap.entrySet()) {
                List<URI> inUris = new ArrayList<URI>(entry.getValue());
                Iterator<? extends DataObject> objs = super.queryIterativeObjects(entry.getKey(), inUris);
                while (objs.hasNext()) {
                    super.removeObject(objs.next());
                }
            }
        }

        private <T extends DataObject> void addToMap(T... objects) {
            addToMap(Arrays.asList(objects));
        }

        private <T extends DataObject> void addToMap(Collection<T> dataobjects) {
            for (T object : dataobjects) {
                if (objMap.get(object.getClass()) == null) {
                    objMap.put(object.getClass(), new HashSet<URI>());
                }
                objMap.get(object.getClass()).add(object.getId());
            }
        }

        public void disableAsyncIndexCleanup() {
            this._indexCleaner = new NoBgIndexCleaner();
        }

        @Override
        protected <T extends DataObject> List<URI> insertNewColumns(Keyspace ks, Collection<T> dataobjects)
                throws DatabaseException {
            StepLock stepLock = this.threadStepLock == null ? null : this.threadStepLock.get();
            if (stepLock != null) {
                stepLock.waitForStep(StepLock.Step.InsertNewColumns);
            }

            try {
                return super.insertNewColumns(ks, dataobjects);
            } finally {
                if (stepLock != null) {
                    stepLock.ackStep(StepLock.Step.InsertNewColumns);
                }
            }
        }

        @Override
        protected <T extends DataObject> Rows<String, CompositeColumnName> fetchNewest(Class<? extends T> clazz, Keyspace ks,
                List<URI> objectsToCleanup)
                throws DatabaseException {
            StepLock stepLock = this.threadStepLock == null ? null : this.threadStepLock.get();
            if (stepLock != null) {
                stepLock.waitForStep(StepLock.Step.FetchNewestColumns);
            }

            try {
                return super.fetchNewest(clazz, ks, objectsToCleanup);
            } finally {
                if (stepLock != null) {
                    stepLock.ackStep(StepLock.Step.FetchNewestColumns);
                }
            }
        }

        // Override default implementation to
        @Override
        protected <T extends DataObject> void cleanupOldColumns(Class<? extends T> clazz, Keyspace ks,
                Rows<String, CompositeColumnName> rows)
                throws DatabaseException {
            StepLock stepLock = this.threadStepLock == null ? null : this.threadStepLock.get();
            if (stepLock != null) {
                stepLock.waitForStep(StepLock.Step.CleanupOldColumns);
            }

            try {
                super.cleanupOldColumns(clazz, ks, rows);
            } finally {
                if (stepLock != null) {
                    stepLock.ackStep(StepLock.Step.CleanupOldColumns);
                }
            }
        }
    }

}
