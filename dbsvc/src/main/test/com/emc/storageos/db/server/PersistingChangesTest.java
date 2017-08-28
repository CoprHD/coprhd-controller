/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.util.*;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.IndexCleaner;
import com.emc.storageos.db.client.impl.IndexCleanupList;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnFamilyQuery;
import com.netflix.astyanax.util.TimeUUIDUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.TestDBClientUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.*;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.SumPrimitiveFieldAggregator;
import com.emc.storageos.db.server.DbClientTest;

public class PersistingChangesTest extends DbsvcTestBase {
    private static final Logger _log = LoggerFactory.getLogger(PersistingChangesTest.class);
    private String _stringValue = new String("isilon");
    private URI _uriValue = URIUtil.createId(DataObject.class);
    private NamedURI _namedURIValue = new NamedURI(_uriValue, _stringValue);
    private Number _numValue = 0xffff;
    private Date _dateValue = new Date();
    private StringMap _mapValue = new StringMap();
    private StringSet _setValue = new StringSet();
    private OpStatusMap _opValue = new OpStatusMap();
    private FSExportMap _fsExportValue = new FSExportMap();
    private SMBShareMap _smbShareMapValue = new SMBShareMap();
    private StringSetMap _setMapValue = new StringSetMap();
    private ScopedLabel _scopedLabelValue = new ScopedLabel("foo", "bar");
    private ScopedLabelSet _scopedLabelSet = new ScopedLabelSet();
    private Calendar _calValue = Calendar.getInstance();

    private DbClient dbClient;

    @Before
    public void setupTest() {
        this.dbClient = super.getDbClient(new DbClientTest.DbClientImplUnitTester());
    }

    @After
    public void teardown() {
        if (this.dbClient instanceof DbClientTest.DbClientImplUnitTester) {
            ((DbClientTest.DbClientImplUnitTester) this.dbClient).removeAll();
        }
    }

    private void initMapSet() {
        // StoragePool.reservedCapacityMap expects map values can be parsed by Long.parse()
        _mapValue.put("key1", "1");
        _mapValue.put("key2", "2");
        _setValue.add("key1");
        _setValue.add("key2");
        _opValue.put("key1", new Operation(Operation.Status.ready.name()));
        _opValue.put("key2", new Operation(Operation.Status.pending.name()));
        _fsExportValue.put("key1", new FileExport(Arrays.asList("a"), "b", "c", "d", "e", "z"));
        _fsExportValue.put("key2", new FileExport(Arrays.asList("f"), "j", "k", "l", "m", "n"));
        _smbShareMapValue.put("key1", new SMBFileShare("a", "b", "c", "d", 1));
        _smbShareMapValue.put("key2", new SMBFileShare("e", "f", "g", "h", 1));
        _setMapValue.put("key1", "value1");
        _setMapValue.put("key1", "value2");
        _setMapValue.put("key2", "value3");
        // ScopedLabel.label must be at least 2 chars long or constraint queries will fail
        _scopedLabelSet.add(new ScopedLabel("abcde", "fghij"));
        _scopedLabelSet.add(new ScopedLabel("klmno", "pqrst"));
    }

    private <T extends DataObject> void setAll(Class<T> clazz, DataObject obj) throws Exception {
        BeanInfo bInfo;
        bInfo = Introspector.getBeanInfo(clazz);
        PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();
        Object objValue = null;
        for (int i = 0; i < pds.length; i++) {
            PropertyDescriptor pd = pds[i];
            if (pd.getWriteMethod() == null) {
                continue;
            }
            if (pd.getName().equals("class")) {
                continue;
            }
            Class type = pd.getPropertyType();
            if (type == String.class) {
                objValue = _stringValue;
            } else if (type == URI.class) {
                objValue = _uriValue;
            } else if (type == Byte.class) {
                objValue = _numValue.byteValue();
            } else if (type == Boolean.class) {
                objValue = true;
            } else if (type == Short.class) {
                objValue = _numValue.shortValue();
            } else if (type == Integer.class) {
                objValue = _numValue.intValue();
            } else if (type == Long.class) {
                objValue = _numValue.longValue();
            } else if (type == Float.class) {
                objValue = _numValue.floatValue();
            } else if (type == Double.class) {
                objValue = _numValue.doubleValue();
            } else if (type == Date.class) {
                objValue = _dateValue;
            } else if (type == StringMap.class) {
                objValue = _mapValue;
            } else if (type == StringSet.class) {
                objValue = _setValue;
            } else if (type == OpStatusMap.class) {
                objValue = _opValue;
            } else if (type == FSExportMap.class) {
                objValue = _fsExportValue;
            } else if (type == NamedURI.class) {
                objValue = _namedURIValue;
            } else if (type == SMBShareMap.class) {
                objValue = _smbShareMapValue;
            } else if (type == StringSetMap.class) {
                objValue = _setMapValue;
            } else if (type == ScopedLabel.class) {
                objValue = _scopedLabelValue;
            } else if (type == ScopedLabelSet.class) {
                objValue = _scopedLabelSet;
            } else if (type == Calendar.class) {
                objValue = _calValue;
            } else {
                throw new IllegalArgumentException("type: " + type + "clazz: " + clazz);
            }
            pd.getWriteMethod().invoke(obj, objValue);
        }
    }

    private <T extends DataObject> void getAllAndVerify(Class<T> clazz, DataObject obj) throws Exception {
        BeanInfo bInfo;
        bInfo = Introspector.getBeanInfo(clazz);
        PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();

        for (int i = 0; i < pds.length; i++) {
            PropertyDescriptor pd = pds[i];
            Object objValue = pd.getReadMethod().invoke(obj);
            if (pd.getName().equals("class")) {
                continue;
            }
            // StoragePool.getFreeCapacity() := StoragePool.setFreeCapacity() - sum(StoragePool.setReservedCapacityMap())
            // Volume.getWWM() := upper(Volume.setWWM())
            else if (clazz.equals(StoragePool.class) && pd.getName().equals("freeCapacity")
                    || clazz.equals(Volume.class) && pd.getName().equals("WWN")) {
                continue;
            }

            String errMsg = String.format("Class: %s field: %s value didn't match.", clazz.getName(), pd.getName());
            Class type = pd.getPropertyType();
            if (type == String.class) {
                Assert.assertEquals(errMsg, _stringValue, objValue);
            } else if (type == URI.class) {
                Assert.assertEquals(errMsg, _uriValue, objValue);
            } else if (type == Byte.class) {
                Assert.assertEquals(errMsg, _uriValue, objValue);
            } else if (type == Boolean.class) {
                Assert.assertTrue(errMsg, (Boolean) objValue);
            } else if (type == Short.class) {
                Assert.assertEquals(errMsg, _numValue.shortValue(), objValue);
            } else if (type == Integer.class) {
                Assert.assertEquals(errMsg, _numValue.intValue(), objValue);
            } else if (type == Long.class) {
                Assert.assertEquals(errMsg, _numValue.longValue(), objValue);
            } else if (type == Float.class) {
                Assert.assertEquals(errMsg, _numValue.floatValue(), objValue);
            } else if (type == Double.class) {
                Assert.assertEquals(errMsg, _numValue.doubleValue(), objValue);
            } else if (type == Date.class) {
                Assert.assertEquals(errMsg, _dateValue, objValue);
            } else if (type == StringMap.class) {
                Assert.assertEquals(errMsg, _mapValue, objValue);
            } else if (type == StringSet.class) {
                Assert.assertEquals(errMsg, _setValue, objValue);
            } else if (type == OpStatusMap.class) {
                Assert.assertEquals(errMsg, _opValue, objValue);
            } else if (type == FSExportMap.class) {
                Assert.assertEquals(errMsg, _fsExportValue, objValue);
            } else if (type == NamedURI.class) {
                Assert.assertEquals(errMsg, _namedURIValue, objValue);
            } else if (type == SMBFileShare.class) {
                Assert.assertEquals(errMsg, _smbShareMapValue, objValue);
            } else if (type == StringSetMap.class) {
                Assert.assertEquals(errMsg, _setMapValue, objValue);
            } else if (type == ScopedLabel.class) {
                Assert.assertEquals(errMsg, _scopedLabelValue, objValue);
            } else if (type == ScopedLabelSet.class) {
                Assert.assertEquals(errMsg, _scopedLabelSet, objValue);
            } else if (type == Calendar.class) {
                Assert.assertEquals(errMsg, _calValue, objValue);
            }
        }
    }

    /**
     * Set all fields on the object, persist it, query it back and verify field values
     */
    private <T extends DataObject> void testSet(DbClient dbClient, Class<T> clazz) throws Exception {
        T obj = clazz.newInstance();
        setAll(clazz, obj);
        dbClient.persistObject(obj);
        DataObject qObj = dbClient.queryObject(clazz, obj.getId());
        getAllAndVerify(clazz, qObj);
    }

    /**
     * Tests all setters are working as expected
     * 
     * @throws Exception
     */
    @Test
    public void testSets() throws Exception {
        initMapSet();

        testSet(this.dbClient, VirtualPool.class);
        testSet(this.dbClient, VirtualArray.class);
        testSet(this.dbClient, Network.class);
        testSet(this.dbClient, FileShare.class);
        testSet(this.dbClient, Project.class);
        testSet(this.dbClient, Snapshot.class);
        testSet(this.dbClient, StorageSystem.class);
        testSet(this.dbClient, StoragePool.class);
        testSet(this.dbClient, TenantOrg.class);
        testSet(this.dbClient, Volume.class);
    }

    /**
     * Tests the changes tracking and the logic of persisting only diffs is working as expected
     */
    @Test
    public void testPersistingChanges() throws Exception {
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URIUtil.createId(VirtualPool.class));
        vpool.setLabel("GOLD");
        vpool.setType("file");
        vpool.setProtocols(new StringSet());
        this.dbClient.persistObject(vpool);

        TenantOrg tenant = new TenantOrg();
        tenant.setId(URIUtil.createId(TenantOrg.class));
        tenant.setLabel("test tenant");
        tenant.setRoleAssignments(new StringSetMap());

        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("fileshare");
        fs.setCapacity(102400L);
        fs.setVirtualPool(vpool.getId());
        fs.setOpStatus(new OpStatusMap());
        fs.setTenant(new NamedURI(tenant.getId(), fs.getLabel()));
        Operation op = new Operation();
        op.setStatus(Operation.Status.pending.name());
        fs.getOpStatus().put("filesharereq", op);
        dbClient.persistObject(fs);
        fs.getOpStatus().put("filesharereq", op);
        this.dbClient.persistObject(fs);

        FileShare fsQ = this.dbClient.queryObject(FileShare.class, fs.getId());
        FileShare fsE = this.dbClient.queryObject(FileShare.class, fs.getId());
        // file share creation
        Assert.assertEquals(fsQ.getId(), fs.getId());
        Assert.assertEquals(fsQ.getLabel(), "fileshare");
        Assert.assertEquals((long) fsQ.getCapacity(), 102400L);
        Assert.assertEquals(fsQ.getVirtualPool(), vpool.getId());
        Assert.assertEquals(fsQ.getOpStatus().get("filesharereq").getStatus(),
                Operation.Status.pending.name());
        fsQ.setMountPath("test/mount/path");
        fsQ.setNativeGuid("nativeguid");
        fsQ.setFsExports(new FSExportMap());
        FileExport fsExport = new FileExport(Arrays.asList("client"), "storageport", "sys", "rw", "root", "nfs");
        fsQ.getFsExports().put("fsexport1", fsExport);
        op = new Operation();
        op.setStatus(Operation.Status.ready.name());
        fsQ.getOpStatus().put("filesharereq", op);

        // request to add exports
        Assert.assertEquals(fsE.getId(), fs.getId());
        Assert.assertEquals(fsE.getLabel(), "fileshare");
        Assert.assertEquals((long) fsE.getCapacity(), 102400L);
        Assert.assertEquals(fsE.getVirtualPool(), vpool.getId());
        Assert.assertEquals(fsE.getOpStatus().get("filesharereq").getStatus(),
                Operation.Status.pending.name());
        fsE.setLabel("changed label");

        op = new Operation();
        op.setStatus(Operation.Status.pending.name());
        fsE.getOpStatus().put("adding export", op);

        // persist - in reverse order
        this.dbClient.persistObject(fsQ);
        this.dbClient.persistObject(fsE);

        // test create and update status objects
        FileShare fs_tasks = new FileShare();
        fs_tasks.setId(URIUtil.createId(FileShare.class));
        fs_tasks.setLabel("fileshare1");

        fs_tasks.setCapacity(102400L);
        fs_tasks.setVirtualPool(vpool.getId());
        fs_tasks.setOpStatus(new OpStatusMap());
        Operation op1 = new Operation();
        op1.setStatus(Operation.Status.pending.name());
        op1.setDescription("sample description");

        fs_tasks.getOpStatus().createTaskStatus("filesharereq2", op1);
        fs_tasks.setTenant(new NamedURI(tenant.getId(), fs.getLabel()));
        this.dbClient.persistObject(fs_tasks);
        FileShare fsBeforeUpdate = this.dbClient.queryObject(FileShare.class, fs_tasks.getId());
        Operation opActual = fsBeforeUpdate.getOpStatus().get("filesharereq2");
        Assert.assertNotNull(opActual.getStartTime());
        Assert.assertNull(opActual.getEndTime());

        this.dbClient.updateTaskOpStatus(FileShare.class, fs_tasks.getId(), "filesharereq2", new Operation(Operation.Status.ready.name()));
        FileShare fsAfterUpdate = this.dbClient.queryObject(FileShare.class, fs_tasks.getId());
        opActual = fsAfterUpdate.getOpStatus().get("filesharereq2");
        Assert.assertNotNull(opActual.getStartTime());
        Assert.assertNotNull(opActual.getEndTime());

        // make sure both changes exist
        FileShare fsV = this.dbClient.queryObject(FileShare.class, fs.getId());
        Assert.assertEquals(fsV.getId(), fs.getId());
        Assert.assertEquals(fsV.getLabel(), "changed label");
        Assert.assertEquals((long) fsV.getCapacity(), 102400L);
        Assert.assertEquals(fsV.getVirtualPool(), vpool.getId());
        Assert.assertEquals(fsV.getOpStatus().get("filesharereq").getStatus(),
                Operation.Status.ready.name());
        Assert.assertEquals(fsV.getOpStatus().get("adding export").getStatus(),
                Operation.Status.pending.name());
        Assert.assertEquals(fsV.getNativeGuid(), "nativeguid");
        Assert.assertEquals(fsV.getFsExports().get("fsexport1"), fsExport);
        Assert.assertEquals(fsV.getMountPath(), "test/mount/path");

        // update from StringMap
        op = new Operation();
        op.setStatus(Operation.Status.error.name());
        fsV.getOpStatus().put("filesharereq", op);
        this.dbClient.persistObject(fsV);

        FileShare fs1 = this.dbClient.queryObject(FileShare.class, fs.getId());
        Assert.assertEquals(Operation.Status.error.name(),
                fs1.getOpStatus().get("filesharereq").getStatus());
        Assert.assertEquals(Operation.Status.pending.name(),
                fs1.getOpStatus().get("adding export").getStatus());

        // SetMap tests
        StringSetMap expected = new StringSetMap();
        String indexkey1 = "indexkey1";
        expected.put(indexkey1, "role1");
        tenant.addRole(indexkey1, "role1");
        expected.put(indexkey1, "role2");
        tenant.addRole(indexkey1, "role2");
        expected.put("indexkey2", "role3");
        tenant.addRole("indexkey2", "role3");
        this.dbClient.persistObject(tenant);

        TenantOrg read = this.dbClient.queryObject(TenantOrg.class, tenant.getId());
        Assert.assertEquals(expected, read.getRoleAssignments());
        read.removeRole("indexkey2", "role3");
        this.dbClient.persistObject(read);

        class PermResults extends QueryResultList<URI> {
            StringSetMap permissionsMap = new StringSetMap();

            @Override
            public URI createQueryHit(URI uri) {
                // none
                return uri;
            }

            @Override
            public URI createQueryHit(URI uri, String permission, UUID timestamp) {
                permissionsMap.put(uri.toString(), permission);
                return uri;
            }

            @Override
            public URI createQueryHit(URI uri, Object entry) {
                return createQueryHit(uri);
            }

            public StringSetMap getPermissionsMap() {
                return permissionsMap;
            }
        }
        PermResults results = new PermResults();
        this.dbClient.queryByConstraint(
                ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(
                        indexkey1), results);
        expected = new StringSetMap();
        expected.put(tenant.getId().toString(), "role1");
        expected.put(tenant.getId().toString(), "role2");
        for (Iterator<URI> iterator = results.iterator(); iterator.hasNext(); iterator.next()) {
            ;
        }
        Assert.assertEquals(expected, results.getPermissionsMap());
        // just remove
        expected = new StringSetMap();
        expected.put(indexkey1, "role1");
        expected.put(indexkey1, "role2");
        read = this.dbClient.queryObject(TenantOrg.class, tenant.getId());
        Assert.assertEquals(expected, read.getRoleAssignments());
        read.removeRole(indexkey1, "role2");
        this.dbClient.updateAndReindexObject(read);

        PermResults newResults = new PermResults();
        this.dbClient.queryByConstraint(
                ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(
                        indexkey1), newResults);
        for (Iterator<URI> iterator = newResults.iterator(); iterator.hasNext(); iterator.next()) {
            ;
        }
        expected = new StringSetMap();
        expected.put(tenant.getId().toString(), "role1");
        Assert.assertEquals(expected, newResults.getPermissionsMap());

        // query
        TenantOrg tenant2 = new TenantOrg();
        tenant2.setId(URIUtil.createId(TenantOrg.class));
        tenant2.setLabel("test tenant2");
        this.dbClient.updateAndReindexObject(tenant2);
        List<URI> uris = new ArrayList<URI>();
        uris.add(tenant.getId());
        uris.add(tenant2.getId());
        List<TenantOrg> tenantOrgs = this.dbClient.queryObjectField(TenantOrg.class, "label", uris);
        Assert.assertTrue(tenantOrgs.size() == 2);
        for (TenantOrg each : tenantOrgs) {
            Assert.assertTrue((each.getId().equals(tenant.getId()) && each.getLabel().equals("test tenant")) ||
                    (each.getId().equals(tenant2.getId()) && each.getLabel().equals("test tenant2")));
        }

        final int count = 1000;
        final int indexCount = 10;
        String key = "scale-test-index-key ";
        String val = "scale-test-index-value ";
        String key2 = "scale-test-index-key2 ";
        String val2 = "scale-test-index-value2 ";
        List<URI> uriList = new ArrayList<URI>(count);
        long createTime = 0;
        long updateTime = 0;
        long updateTime2 = 0;
        long updateTime3 = 0;
        for (int i = 0; i < count; i++) {
            long startTime = System.currentTimeMillis();
            TenantOrg t = new TenantOrg();
            t.setId(URIUtil.createId(TenantOrg.class));
            t.setLabel("test tenant " + i);
            t.setRoleAssignments(new StringSetMap());
            for (int j = 0; j < indexCount; j++) {
                t.addRole(key + j, val + j);
            }
            this.dbClient.persistObject(t);
            createTime += System.currentTimeMillis() - startTime;
            uriList.add(t.getId());
            // update 1 - index modifications
            startTime = System.currentTimeMillis();
            TenantOrg t2 = new TenantOrg();
            t2.setId(t.getId());
            for (int j = 0; j < indexCount; j++) {
                t2.removeRole(key + j, val + j);
                t2.addRole(key2 + j, val2 + j);
            }
            this.dbClient.updateAndReindexObject(t2);
            updateTime += System.currentTimeMillis() - startTime;
            // update 2 - label modification
            startTime = System.currentTimeMillis();
            t2 = new TenantOrg();
            t2.setId(t.getId());
            t2.setLabel(t.getLabel() + " modified");
            this.dbClient.updateAndReindexObject(t2);
            updateTime2 += System.currentTimeMillis() - startTime;
            // update 3 - non-indexed field modification
            startTime = System.currentTimeMillis();
            t2 = new TenantOrg();
            t2.setId(t.getId());
            t2.setDescription("test tenant description");
            this.dbClient.updateAndReindexObject(t2);
            updateTime3 += System.currentTimeMillis() - startTime;
        }
        _log.info("Total objects created: " + count);
        _log.info("Total create time: " + createTime + "msec");
        _log.info("Total update time (multiple index fields): " + updateTime + "msec");
        _log.info("Total update time (label only): " + updateTime2 + "msec");
        _log.info("Total update time (non indexed field): " + updateTime3 + "msec");

        class CountResults extends QueryResultList<URI> {
            public int got = 0;

            @Override
            public URI createQueryHit(URI uri) {
                // none
                return uri;
            }

            @Override
            public URI createQueryHit(URI uri, String permission, UUID timestamp) {
                got++;
                return uri;
            }

            @Override
            public URI createQueryHit(URI uri, Object entry) {
                return createQueryHit(uri);
            }

            public void verify() {
                Assert.assertEquals(count, got);
            }
        }
        for (int i = 0; i < indexCount; i++) {
            CountResults countResults = new CountResults();
            this.dbClient.queryByConstraint(
                    ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(
                            key2 + i), countResults);
            for (Iterator<URI> iterator = countResults.iterator(); iterator.hasNext(); iterator.next()) {
                ;
            }
            countResults.verify();
        }

        // test decommissioned index
        tenant = this.dbClient.queryObject(TenantOrg.class, tenant.getId());
        tenant.setInactive(true);
        tenant2 = this.dbClient.queryObject(TenantOrg.class, tenant2.getId());
        tenant2.setInactive(true);
        this.dbClient.persistObject(tenant, tenant2);
        URIQueryResultList list = new URIQueryResultList();
        this.dbClient.queryByConstraint(
                DecommissionedConstraint.Factory.getDecommissionedObjectsConstraint(
                        TenantOrg.class, 0), list);
        List<URI> gotUris = new ArrayList<URI>();
        for (Iterator<URI> iterator = list.iterator(); iterator.hasNext();) {
            gotUris.add(iterator.next());
        }
        Assert.assertTrue(gotUris.size() >= 2);
        Assert.assertTrue(gotUris.contains(tenant.getId()));
        Assert.assertTrue(gotUris.contains(tenant2.getId()));
        // test time slicing - 1
        long nowTimeUsec = TimeUUIDUtils.getMicrosTimeFromUUID(TimeUUIDUtils.getUniqueTimeUUIDinMicros());
        list = new URIQueryResultList();
        this.dbClient.queryByConstraint(
                DecommissionedConstraint.Factory.getDecommissionedObjectsConstraint(
                        TenantOrg.class, nowTimeUsec), list);
        gotUris = new ArrayList<URI>();
        for (Iterator<URI> iterator = list.iterator(); iterator.hasNext();) {
            gotUris.add(iterator.next());
        }
        Assert.assertTrue(gotUris.size() >= 2);
        Assert.assertTrue(gotUris.contains(tenant.getId()));
        Assert.assertTrue(gotUris.contains(tenant2.getId()));
        // test time slicing - 2
        list = new URIQueryResultList();
        this.dbClient.queryByConstraint(
                DecommissionedConstraint.Factory.getDecommissionedObjectsConstraint(
                        TenantOrg.class, nowTimeUsec - (60 * 1000 * 1000)), list);
        gotUris = new ArrayList<URI>();
        for (Iterator<URI> iterator = list.iterator(); iterator.hasNext();) {
            gotUris.add(iterator.next());
        }
        Assert.assertEquals(0, gotUris.size());

        // test remove object
        ((DbClientImpl)dbClient).internalRemoveObjects(tenant, tenant2);
        read = this.dbClient.queryObject(TenantOrg.class, tenant.getId());
        Assert.assertNull(read);
        read = this.dbClient.queryObject(TenantOrg.class, tenant2.getId());
        Assert.assertNull(read);

        newResults = new PermResults();
        this.dbClient.queryByConstraint(
                ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(
                        indexkey1), newResults);
        for (Iterator<URI> iterator = newResults.iterator(); iterator.hasNext(); iterator.next()) {
            ;
        }
        Assert.assertTrue(newResults.getPermissionsMap().isEmpty());

        newResults = new PermResults();
        this.dbClient.queryByConstraint(
                ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(
                        indexkey1), newResults);
        for (Iterator<URI> iterator = newResults.iterator(); iterator.hasNext(); iterator.next()) {
            ;
        }
        expected = new StringSetMap();
        Assert.assertEquals(expected, newResults.getPermissionsMap());
        for (int i = 0; i < indexCount; i++) {
            CountResults countResults = new CountResults();
            this.dbClient.queryByConstraint(
                    ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(
                            key2 + i), countResults);
            for (Iterator<URI> iterator = countResults.iterator(); iterator.hasNext(); iterator.next()) {
                ;
            }
            countResults.verify();
        }
    }

    /**
     * Tests Constraint query
     */
    @Test
    public void testConstraintQuery() throws Exception {
        List<StoragePool> pools = new ArrayList<StoragePool>();
        for (int ii = 0; ii < 3; ii++) {
            StoragePool pool = new StoragePool();
            pool.setId(URIUtil.createId(VirtualPool.class));
            pool.setLabel("POOL_" + ii);
            this.dbClient.persistObject(pool);
            pools.add(pool);
        }

        List<VirtualPool> vpools = new ArrayList<VirtualPool>();
        for (int ii = 0; ii < 2; ii++) {
            VirtualPool vpool = new VirtualPool();
            vpool.setId(URIUtil.createId(VirtualPool.class));
            vpool.setLabel(ii == 0 ? "GOLD" : "SILVER");
            vpool.setType("block");
            vpool.setProtocols(new StringSet());
            vpool.setMatchedStoragePools(new StringSet());
            this.dbClient.persistObject(vpool);
            vpools.add(vpool);
        }

        vpools.get(0).getMatchedStoragePools().add(pools.get(0).getId().toString());
        vpools.get(0).getMatchedStoragePools().add(pools.get(1).getId().toString());
        this.dbClient.persistObject(vpools.get(0));
        vpools.get(1).getMatchedStoragePools().add(pools.get(1).getId().toString());
        this.dbClient.persistObject(vpools.get(1));

        URIQueryResultList cosResultList = new URIQueryResultList();
        this.dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getMatchedPoolVirtualPoolConstraint(pools.get(0).getId()), cosResultList);
        Iterator<URI> cosIter = cosResultList.iterator();
        int idx = 0;
        while (cosIter.hasNext()) {
            URI cos = cosIter.next();
            Assert.assertTrue(cos.equals(vpools.get(0).getId()));
            idx++;
        }
        Assert.assertTrue(idx == 1);

        cosResultList = new URIQueryResultList();
        this.dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getMatchedPoolVirtualPoolConstraint(pools.get(1).getId()), cosResultList);
        cosIter = cosResultList.iterator();
        idx = 0;
        while (cosIter.hasNext()) {
            URI cos = cosIter.next();
            Assert.assertTrue(cos.equals(vpools.get(0).getId()) ||
                    cos.equals(vpools.get(1).getId()));
            idx++;
        }
        Assert.assertTrue(idx == 2);

        cosResultList = new URIQueryResultList();
        this.dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getMatchedPoolVirtualPoolConstraint(pools.get(2).getId()), cosResultList);
        cosIter = cosResultList.iterator();
        Assert.assertTrue(!cosIter.hasNext());
    }

    @Test
    public void testAggregationQuery() throws Exception {
        cleanupDataObjectCF(Volume.class);
        
        Volume volume1 = new Volume();
        URI id1 = URIUtil.createId(Volume.class);
        URI pool1 = URIUtil.createId(StoragePool.class);
        volume1.setId(id1);
        volume1.setLabel("volume1");
        volume1.setPool(pool1);
        volume1.setInactive(false);
        volume1.setAllocatedCapacity(1000L);
        volume1.setProvisionedCapacity(2000L);

        Volume volume2 = new Volume();
        URI id2 = URIUtil.createId(Volume.class);
        URI pool2 = URIUtil.createId(StoragePool.class);
        volume2.setId(id2);
        volume2.setLabel("volume2");
        volume2.setPool(pool2);
        volume2.setInactive(true);
        volume2.setAllocatedCapacity(2000L);
        volume2.setProvisionedCapacity(4000L);

        Volume volume3 = new Volume();
        URI id3 = URIUtil.createId(Volume.class);
        URI pool3 = URIUtil.createId(StoragePool.class);
        volume3.setId(id3);
        volume3.setLabel("volume3");
        // volume3.setInactive(false); // by default it should be treated as active.
        volume3.setPool(pool3);
        volume3.setAllocatedCapacity(4000L);
        volume3.setProvisionedCapacity(8000L);

        Volume volume4 = new Volume();
        URI id4 = URIUtil.createId(Volume.class);
        volume4.setId(id4);
        volume4.setLabel("volume4");
        // volume4.setInactive(false); // by default it should be treated as active.
        volume4.setPool(pool3);
        volume4.setAllocatedCapacity(4500L);
        volume4.setProvisionedCapacity(9500L);

        List<URI> allIds = new ArrayList<URI>();
        allIds.add(id1);
        allIds.add(id2);
        allIds.add(id3);
        allIds.add(id4);

        Set<String> pools = new HashSet<String>();
        pools.add(pool2.toString());
        pools.add(pool3.toString());

        this.dbClient.createObject(volume1, volume2, volume3, volume4);

        // count the total number of records
        List<URI> allVol = this.dbClient.queryByType(Volume.class, false);
        Assert.assertEquals("# of Volume objects in DB is not as expected", 4, TestDBClientUtils.size(allVol));

        SumPrimitiveFieldAggregator aggregator = CustomQueryUtility.aggregateActiveObject(
                this.dbClient, Volume.class, new String[] { "allocatedCapacity" });
        Assert.assertTrue(aggregator.getRecordNum() == 3);
        Assert.assertTrue((long) aggregator.getAggregate("allocatedCapacity") == 9500L);

        aggregator = CustomQueryUtility.aggregateActiveObject(
                this.dbClient, Volume.class,
                new String[] { "allocatedCapacity" });
        Assert.assertTrue(aggregator.getRecordNum() == 3);
        Assert.assertTrue((long) aggregator.getAggregate("allocatedCapacity") == 9500L);
        aggregator = CustomQueryUtility.aggregateActiveObject(
                this.dbClient, Volume.class,
                new String[] { "provisionedCapacity" });
        Assert.assertTrue((long) aggregator.getAggregate("provisionedCapacity") == 19500L);

        Iterator<URI> iter = CustomQueryUtility.filterDataObjectsFieldValueInSet(
                this.dbClient, Volume.class,
                "pool", allIds.iterator(), pools);
        List<URI> volFromPools = new ArrayList<URI>();
        while (iter.hasNext()) {
            volFromPools.add(iter.next());
        }
        aggregator = CustomQueryUtility.aggregateActiveObject(
                this.dbClient, Volume.class,
                new String[] { "allocatedCapacity" }, volFromPools.iterator());
        Assert.assertTrue(aggregator.getRecordNum() == 2);
        Assert.assertTrue((long) aggregator.getAggregate("allocatedCapacity") == 8500L);
        aggregator = CustomQueryUtility.aggregateActiveObject(
                this.dbClient, Volume.class,
                new String[] { "provisionedCapacity" }, volFromPools.iterator());
        Assert.assertTrue((long) aggregator.getAggregate("provisionedCapacity") == 17500L);
    }
    
    @Test
    public void testQueryByFieldThenDelete() throws Exception {
        Volume volume = new Volume();
        URI id = URIUtil.createId(Volume.class);
        URI pool = URIUtil.createId(StoragePool.class);
        volume.setId(id);
        volume.setLabel("volume");
        volume.setPool(pool);
        volume.setNativeGuid("native_guid");
        volume.setNativeId("native_id");
        volume.setCompositionType("compositionType");
        volume.setInactive(false);
        volume.setAllocatedCapacity(1000L);
        volume.setProvisionedCapacity(2000L);
        
        dbClient.updateObject(volume);
        
        List<Volume> volumes = dbClient.queryObjectField(Volume.class, "compositionType", Arrays.asList(id));
        Assert.assertTrue(volumes.size() == 1);
        Assert.assertEquals("compositionType", volumes.get(0).getCompositionType());

        dbClient.markForDeletion(volumes.get(0));
        
        URIQueryResultList result1 = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeNativeGuidConstraint(volume.getNativeGuid()), result1);
        Assert.assertFalse(result1.iterator().hasNext());
        
        URIQueryResultList result2 = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
        		.getVolumeNativeIdConstraint(volume.getNativeId()), result2);
        Assert.assertFalse(result2.iterator().hasNext());
        
        Volume queryVolumeResult = (Volume) dbClient.queryObject(id);
        Assert.assertTrue(queryVolumeResult == null || queryVolumeResult.getInactive());
    }

    @Test
    public void testCleanupSoftReference() throws ConnectionException {
        Volume volume = new Volume();
        URI id = URIUtil.createId(Volume.class);
        URI pool = URIUtil.createId(StoragePool.class);
        volume.setId(id);
        volume.setLabel("origin");
        volume.setPool(pool);
        volume.setNativeGuid("native_guid");
        volume.setNativeId("native_id");
        volume.setCompositionType("compositionType");
        volume.setInactive(false);
        volume.setAllocatedCapacity(1000L);
        volume.setProvisionedCapacity(2000L);

        dbClient.updateObject(volume);

        // Search the label index directly
        String labelFieldName = "label";
        int labelCount = getIndexRecordCount(labelFieldName);
        Assert.assertEquals(2, labelCount); // By default, label index contains one record for VirtualDataCenter

        List<URI> volumes = dbClient.queryByType(Volume.class, true);
        int size = 0;
        for (URI uri : volumes) {
            Volume entry = dbClient.queryObject(Volume.class, uri);
            _log.info("{}, URI={}, Label={}", ++size, uri, entry.getLabel());
            Assert.assertEquals("origin", entry.getLabel());
        }
        Assert.assertEquals(1, size);

        _log.info("\nStart to update with new label");
        // Mock warning when listToCleanRef is null in indexCleaner
        this.dbClient = super.getDbClient(new DbClientTest.DbClientImplUnitTester() {
            @Override
            public synchronized void start() {
                super.start();
                _indexCleaner = new IndexCleaner() {
                    @Override
                    public void cleanIndex(RowMutator mutator, DataObjectType doType, SoftReference<IndexCleanupList> listToCleanRef) {
                        listToCleanRef.clear();
                        super.cleanIndex(mutator, doType, listToCleanRef);
                    }
                };
            }
        });
        volume = dbClient.queryObject(Volume.class, id);
        volume.setLabel("new");

        dbClient.updateObject(volume);

        // Search the label index directly
        labelCount = getIndexRecordCount(labelFieldName);
        Assert.assertEquals(3, labelCount); // We mocked indexCleaner, so that old label index couldn't be removed, still in DB

        volumes = dbClient.queryByType(Volume.class, true);
        size = 0;
        for (URI uri : volumes) {
            Volume entry = dbClient.queryObject(Volume.class, uri);
            _log.info("{}, URI={}, Label={}", ++size, uri, entry.getLabel());
            Assert.assertEquals("new", entry.getLabel());
        }
        Assert.assertEquals(1, size);

        // Remove the object
        _log.info("\nStart to remove volume");
        this.dbClient = super.getDbClient(new DbClientTest.DbClientImplUnitTester());
        volume = dbClient.queryObject(Volume.class, id);
        dbClient.removeObject(volume);

        // Search the label index directly
        labelCount = getIndexRecordCount(labelFieldName);
        Assert.assertEquals(1, labelCount);// All the label index related to volume should be removed.

        volumes = dbClient.queryByType(Volume.class, true);
        size = 0;
        for (URI uri : volumes) {
            Volume entry = dbClient.queryObject(Volume.class, uri);
            _log.info("{}, URI={}, Label={}", ++size, uri, entry.getLabel());
        }
        Assert.assertEquals(0, size);
    }

    private int getIndexRecordCount(String fieldName) throws ConnectionException {
        Keyspace keyspace = ((DbClientTest.DbClientImplUnitTester) dbClient).getLocalContext().getKeyspace();
        DataObjectType doType = TypeMap.getDoType(Volume.class);
        ColumnField field = doType.getColumnField(fieldName);
        ColumnFamilyQuery<String, IndexColumnName> query = keyspace.prepareQuery(field.getIndexCF());
        OperationResult<Rows<String, IndexColumnName>> result = query.getAllRows().execute();
        int count = 0;
        for (Row<String, IndexColumnName> row : result.getResult()) {
            _log.debug("{}, RowKey={}, Columns Size={}", ++count, row.getKey(), row.getColumns().size());
            for (Column<IndexColumnName> column : row.getColumns()) {
                _log.debug("\t, Column Name={}, String Value={}.", column.getName(), column.getStringValue());
            }
        }
        return count;
    }

    @Test
    public void testStringSetMapRemove() throws Exception {
        UnManagedVolume unManagedVolume = new UnManagedVolume();
        URI id = URIUtil.createId(UnManagedVolume.class);

        unManagedVolume.setId(id);

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

        unManagedVolume.putVolumeInfo("key1", set1);
        unManagedVolume.putVolumeInfo("key2", set2);

        dbClient.createObject(unManagedVolume);

        unManagedVolume = dbClient.queryObject(UnManagedVolume.class, id);

        Assert.assertTrue(unManagedVolume.getVolumeInformation().containsKey(key1));
        Assert.assertTrue(unManagedVolume.getVolumeInformation().containsKey(key2));

        unManagedVolume.getVolumeInformation().remove(key1);
        unManagedVolume.getVolumeInformation().get(key2).remove("test6");

        dbClient.updateObject(unManagedVolume);

        unManagedVolume = dbClient.queryObject(UnManagedVolume.class, id);
        Assert.assertFalse(unManagedVolume.getVolumeInformation().containsKey(key1));

        Assert.assertTrue(unManagedVolume.getVolumeInformation().get(key2).contains("test7"));
        Assert.assertFalse(unManagedVolume.getVolumeInformation().get(key2).contains("test6"));
    }
    
    @Test
    public void testRemoveObject() throws Exception {
        Task task = new Task();
        task.setId(URIUtil.createId(Task.class));
        task.setLabel("task1");
        
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel("Volume");
     
        dbClient.updateObject(task, volume);        
        dbClient.removeObject(task, volume);
        
        Assert.assertNull(dbClient.queryObject(Task.class, task.getId()));
        
        Volume result = dbClient.queryObject(Volume.class, volume.getId());
        Assert.assertEquals(volume.getId(), result.getId());
        Assert.assertEquals(volume.getLabel(), result.getLabel());
        Assert.assertTrue(result.getInactive());
    }
}
