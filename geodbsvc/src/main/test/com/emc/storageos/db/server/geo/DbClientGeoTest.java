/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.geo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.ColumnValue;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.exceptions.DatabaseException;

//Suppress Sonar violation of Lazy initialization of static fields should be synchronized
//Junit test will be called in single thread by default, it's safe to ignore this violation
@SuppressWarnings("squid:S2444")
public class DbClientGeoTest extends DbsvcGeoTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(DbClientGeoTest.class);

    private static List<FileShare> fsList;
    private static List<Project> pjList;
    private static TenantOrg rootTenant;
    private static List<DataObject> dbObjList;

    @Before
    public void testSetup() {
        DbClient dbClient = getDbClient();
        boolean rootTenantExists = isRootTenantExist(dbClient);
        int retryCount = 0;
        while (!rootTenantExists && retryCount < 30) {
            // sleep and try again
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // should be there by now
            rootTenantExists = isRootTenantExist(dbClient);
            retryCount++;
        }
        Assert.assertTrue(rootTenantExists);
        rootTenant = getRootTenant();
        Assert.assertFalse(rootTenant == null);

        fsList = new ArrayList<FileShare>();
        pjList = new ArrayList<Project>();
        dbObjList = addDataObjects(rootTenant, fsList, pjList);
    }

    @After
    public void teardown() {
        DbClient dbClient = getDbClient();
        for (FileShare fs : fsList) {
            dbClient.removeObject(fs);
        }
        for (Project pj : pjList) {
            dbClient.removeObject(pj);
        }
    }

    /**
     * Root tenant is moved from localdb to geodb, so we need to unit test here.
     * 
     * @throws Exception
     */
    @Test
    public void testRootTenantExists() throws Exception {
        _logger.info("Starting testRootTenantExists test");
        URIQueryResultList tenants = new URIQueryResultList();
        getDbClient().queryByConstraint(
                ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                tenants);
        Assert.assertTrue(tenants.iterator().hasNext());
    }

    @Test
    public void testLocalKeyspace() throws Exception {
        _logger.info("Starting testLocalKeyspace");
        DbClient dbClient = getDbClient();
        String altId = UUID.randomUUID().toString();

        // persist
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));
        fs.setLabel("test");
        fs.setNativeGuid(altId);
        dbClient.createObject(fs);

        // verify
        FileShare fsQuery = dbClient.queryObject(FileShare.class, fs.getId());

        Assert.assertNotNull(fsQuery);
        Assert.assertTrue(fsQuery.getId().equals(fs.getId()));
        Assert.assertTrue(isItWhereItShouldBe(fsQuery));
        Assert.assertTrue(getDbClient().queryByType(FileShare.class, true).iterator().hasNext());

        dbClient.removeObject(fs);
        Assert.assertNull(dbClient.queryObject(FileShare.class, fs.getId()));

    }

    @Test
    public void testGlobalOnlyKeyspace() {
        _logger.info("Starting testGlobalOnlyKeyspace");
        DbClient dbClient = getDbClient();

        Project pj = new Project();
        pj.setId(URIUtil.createId(Project.class));

        Assert.assertTrue(pj.getId().toString().contains("global"));

        String label = String.format("Test Label");
        pj.setLabel(label);
        pj.setTenantOrg(new NamedURI(rootTenant.getId(), label));
        dbClient.createObject(pj);

        Project queriedPj = dbClient.queryObject(Project.class, pj.getId());

        Assert.assertNotNull(queriedPj);
        Assert.assertTrue(queriedPj.getId().equals(pj.getId()));
        Assert.assertTrue(isItWhereItShouldBe(queriedPj));

        Assert.assertTrue(getDbClient().queryByType(Project.class, true).iterator().hasNext());

        dbClient.removeObject(pj);
        Assert.assertNull(dbClient.queryObject(Project.class, pj.getId()));

    }

    @Test
    public void testCreateRemoveObject() {
        _logger.info("Starting testCreateRemoveObject");

        for (DataObject obj : dbObjList) {
            Assert.assertTrue(isItWhereItShouldBe(obj));
        }

        List<DataObject> dbObjList = addDataObjects(rootTenant, null, null);

        DbClient dbClient = getDbClient();

        for (DataObject obj : dbObjList) {
            Assert.assertNotNull(dbClient.queryObject(obj.getClass(), obj.getId()));
        }

        for (FileShare fs : fsList) {
            Assert.assertFalse(dbClient.queryObject(FileShare.class, fs.getId()).isGlobal());
        }

        for (Project pj : pjList) {
            Assert.assertTrue(dbClient.queryObject(Project.class, pj.getId()).isGlobal());
        }

        dbClient.removeObject(dbObjList.toArray(new DataObject[dbObjList.size()]));
        for (DataObject obj : dbObjList) {
            Assert.assertNull(dbClient.queryObject(obj.getClass(), obj.getId()));
        }

    }

    @Test
    public void testQueryByType() {
        _logger.info("Starting testQueryByType");

        DbClient dbClient = getDbClient();

        internalTestQueryByType(FileShare.class, dbClient, fsList.size(), false);
        internalTestQueryByType(Project.class, dbClient, pjList.size(), false);

    }

    private <T extends DataObject> void internalTestQueryByType(Class<T> clazz, DbClient dbClient, int expectedCount, boolean geo) {
        List<URI> queryiedObjList = geo ? dbClient.queryByType(clazz, true) : dbClient.queryByType(clazz, true);
        Assert.assertEquals(expectedCount, count(queryiedObjList));
    }

    @Test
    public void testQueryObject() {
        _logger.info("Starting testQueryObject");

        DbClient dbClient = getDbClient();

        internalTestQueryObject(FileShare.class, dbClient, fsList);
        internalTestQueryObject(Project.class, dbClient, pjList);

    }

    private <T extends DataObject> void internalTestQueryObject(Class<T> clazz, DbClient dbClient, Collection<T> objList) {
        List<URI> idList = new ArrayList<URI>();
        for (T fs : objList) {
            idList.add(fs.getId());
        }
        // add a fake id to the list
        idList.add(URIUtil.createId(clazz));
        List<T> queryiedObjList = dbClient.queryObject(clazz, idList);
        Assert.assertTrue(count(queryiedObjList) == objList.size());

    }

    @Test
    public void testQueryObjectField() {
        _logger.info("Starting testQueryObjectField");

        DbClient dbClient = getDbClient();

        internalTestQueryObjectField(FileShare.class, dbClient, fsList);
        internalTestQueryObjectField(Project.class, dbClient, pjList);

    }

    private <T extends DataObject> void internalTestQueryObjectField(Class<T> clazz, DbClient dbClient, Collection<T> objList) {
        String fieldName = "label";
        List<URI> idList = new ArrayList<URI>();
        for (T fs : objList) {
            idList.add(fs.getId());
        }
        // add a fake id to the list
        idList.add(URIUtil.createId(clazz));
        List<T> queryiedObjList = dbClient.queryObjectField(clazz, fieldName, idList);
        Assert.assertTrue(count(queryiedObjList) == objList.size());

        for (T obj : queryiedObjList) {
            Assert.assertFalse(obj.getLabel() == null);
        }

    }

    private <T> int count(List<T> queryiedObjList) {
        int count = 0;
        Iterator<T> itr = queryiedObjList.iterator();
        while (itr.hasNext()) {
            itr.next();
            count++;
        }
        return count;
    }

    @Test
    public void testQueryObjectFields() {
        _logger.info("Starting testQueryObjectField");

        DbClient dbClient = getDbClient();

        internalTestQueryObjectFields(FileShare.class, dbClient, fsList);
        internalTestQueryObjectFields(Project.class, dbClient, pjList);

    }

    private <T extends DataObject> void internalTestQueryObjectFields(Class<T> clazz, DbClient dbClient, Collection<T> objList) {
        Set<String> fieldNames = new HashSet<String>();
        fieldNames.add("label");
        fieldNames.add("creationTime");
        List<URI> idList = new ArrayList<URI>();
        for (T fs : objList) {
            idList.add(fs.getId());
        }
        // add a fake id to the list
        idList.add(URIUtil.createId(clazz));
        List<T> queryiedObjList = new ArrayList<T>();
        queryiedObjList.addAll(dbClient.queryObjectFields(clazz, fieldNames, idList));
        Assert.assertTrue(count(queryiedObjList) == objList.size());

        for (T obj : queryiedObjList) {
            Assert.assertFalse(obj.getLabel() == null);
            Assert.assertFalse(obj.getCreationTime() == null);
        }

    }

    @Test
    public void testAggregateObjectField() {
        internalTestAggregateObjectField(FileShare.class, fsList);
        internalTestAggregateObjectField(Project.class, pjList);
    }

    private <T extends DataObject> void internalTestAggregateObjectField(Class<T> clazz, List<T> objs) {
        DbClient dbClient = getDbClient();
        List<URI> ids = new ArrayList<URI>();
        for (DataObject obj : objs) {
            ids.add(obj.getId());
        }

        TestAggregator tstAggr = new TestAggregator(clazz, "label");
        dbClient.aggregateObjectField(clazz, ids.iterator(), tstAggr);

        List<String> results = tstAggr.getResults();
        Assert.assertTrue(results.size() == objs.size());
        for (DataObject obj : objs) {
            Assert.assertTrue(results.contains(obj.getLabel()));
        }

    }

    public static class TestAggregator implements DbAggregatorItf {

        private List<String> _list;
        private String _field;
        private Class<? extends DataObject> _clazz;

        public TestAggregator(Class<? extends DataObject> clazz, String field) {
            _field = field;
            _clazz = clazz;
            _list = new ArrayList<String>();
        }

        @Override
        public void aggregate(List<CompositeColumnName> columns) {
            if (columns == null || columns.size() == 0) {
                return;
            }
            DataObjectType doType = TypeMap.getDoType(_clazz);
            if (doType == null) {
                throw new IllegalArgumentException();
            }
            ColumnField columnField = doType.getColumnField(_field);
            if (columnField == null) {
                throw new IllegalArgumentException();
            }
            CompositeColumnName column = columns.iterator().next();
            if (column.getOne().equals(_field)) {
                String value = ColumnValue.getPrimitiveColumnValue(column.getValue(),
                        columnField.getPropertyDescriptor()).toString();
                _list.add(value);
            }
        }

        @Override
        public String[] getAggregatedFields() {
            return new String[] { _field };
        }

        public List<String> getResults() {
            return _list;
        }

    }

    @Test
    public void testLocalRefToGeo() {
        _logger.info("Starting testLocalRefToGeo");

        // FileShare is local
        FileShare fs1 = new FileShare();
        fs1.setId(URIUtil.createId(FileShare.class));
        fs1.setLabel("testfs1");
        fs1.setNativeGuid(UUID.randomUUID().toString());

        // project is geo
        Project pj1 = new Project();
        pj1.setId(URIUtil.createId(Project.class));
        pj1.setLabel("testpj1");
        pj1.setTenantOrg(new NamedURI(rootTenant.getId(), "testpj1"));

        fs1.setProject(new NamedURI(pj1.getId(), "project"));

        DbClient dbClient = getDbClient();
        dbClient.createObject(pj1);
        dbClient.createObject(fs1);

        Project queriedPj = dbClient.queryObject(Project.class, pj1.getId());
        Assert.assertNotNull(queriedPj);
        Assert.assertTrue(queriedPj.getId().equals(pj1.getId()));
        Assert.assertTrue(isItWhereItShouldBe(queriedPj));
        Assert.assertTrue(getDbClient().queryByType(Project.class, true).iterator().hasNext());

        FileShare queriedFs = dbClient.queryObject(FileShare.class, fs1.getId());
        Assert.assertNotNull(queriedFs);
        Assert.assertTrue(queriedFs.getId().equals(fs1.getId()));
        Assert.assertTrue(isItWhereItShouldBe(queriedFs));
        Assert.assertTrue(getDbClient().queryByType(FileShare.class, true).iterator().hasNext());

        // get fileshare by project tests global index into a local cf
        ContainmentConstraint fcc = ContainmentConstraint.Factory.getProjectFileshareConstraint(pj1.getId());
        URIQueryResultList flist = new URIQueryResultList();
        dbClient.queryByConstraint(fcc, flist);
        Assert.assertTrue(flist.iterator().hasNext());

        // get project by tenant tests global index into another global cf
        ContainmentConstraint pcc = ContainmentConstraint.Factory.getTenantOrgProjectConstraint(rootTenant.getId());
        URIQueryResultList plist = new URIQueryResultList();
        dbClient.queryByConstraint(pcc, plist);
        Assert.assertTrue(plist.iterator().hasNext());

        dbClient.removeObject(fs1);
        URIQueryResultList flist2 = new URIQueryResultList();
        dbClient.queryByConstraint(fcc, flist2);
        Assert.assertFalse(flist2.iterator().hasNext());

        dbClient.removeObject(pj1);
        URIQueryResultList plist2 = new URIQueryResultList();
        dbClient.queryByConstraint(pcc, plist2);
        Iterator<URI> pjItr = plist2.iterator();
        while (pjItr.hasNext()) {
            if (pjItr.next().equals(pj1.getId())) {
                Assert.fail("project found after delete");
            }
        }

        Assert.assertNull(dbClient.queryObject(FileShare.class, fs1.getId()));
        Assert.assertNull(dbClient.queryObject(Project.class, pj1.getId()));
    }

    @Test
    public void testEncryption() {
        DbClient dbClient = getDbClient();

        // create a geo-replicated object with an encrypted field
        AuthnProvider authProvider = new AuthnProvider();
        authProvider.setId(URIUtil.createId(AuthnProvider.class));
        authProvider.setManagerPassword("password");
        dbClient.createObject(authProvider);

        // create a local object with an encrypted field
        Vcenter vc = new Vcenter();
        vc.setId(URIUtil.createId(Vcenter.class));
        vc.setPassword("password");
        dbClient.createObject(vc);

        AuthnProvider q0 = dbClient.queryObject(AuthnProvider.class, authProvider.getId());
        Assert.assertNotNull(q0);
        Assert.assertEquals(q0.getManagerPassword(), "password");

        Vcenter k0 = dbClient.queryObject(Vcenter.class, vc.getId());
        Assert.assertNotNull(k0);
        Assert.assertEquals(k0.getPassword(), "password");

        // null out geo encryption provider; make sure local object is still valid
        TypeMap.setEncryptionProviders(_encryptionProvider, null);

        Vcenter k1 = dbClient.queryObject(Vcenter.class, vc.getId());
        Assert.assertNotNull(k1);
        Assert.assertEquals(k1.getPassword(), "password");

        // geo-replicated object should be encrypted
        AuthnProvider q1 = dbClient.queryObject(AuthnProvider.class, authProvider.getId());
        Assert.assertNotNull(q1);
        Assert.assertFalse(q1.getManagerPassword().equals("password"));

        // restore geo encryption provider and null out local
        TypeMap.setEncryptionProviders(null, _geoEncryptionProvider);

        AuthnProvider q2 = dbClient.queryObject(AuthnProvider.class, authProvider.getId());
        Assert.assertNotNull(q2);
        Assert.assertEquals(q2.getManagerPassword(), "password");

        Vcenter k2 = dbClient.queryObject(Vcenter.class, vc.getId());
        Assert.assertNotNull(k2);
        Assert.assertFalse(k2.getPassword().equals("password"));

        // now just to make sure the encryption keys are different, lets swap them
        // and make sure the queries are not successful
        TypeMap.setEncryptionProviders(_geoEncryptionProvider, _encryptionProvider);

        try {
            AuthnProvider q3 = dbClient.queryObject(AuthnProvider.class, authProvider.getId());
            Assert.fail("geo repliated object query after swapping encryption providers succeeded; failure expected");
        } catch (Exception e) {
            // this is expected; test passes
        }

        try {
            Vcenter k3 = dbClient.queryObject(Vcenter.class, vc.getId());
            Assert.fail("local object query after swapping encryption providers succeeded; failure expected");
        } catch (Exception e) {
            // this is expected; test passes
        }

    }

    private boolean isRootTenantExist(DbClient dbClient) {

        URIQueryResultList tenants = new URIQueryResultList();
        try {
            dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                    tenants);
            if (tenants.iterator().hasNext()) {
                return true;
            } else {
                _logger.info("root tenant query returned no results");
                return false;
            }
        } catch (DatabaseException ex) {
            _logger.error("failed querying for root tenant", ex);
        }
        throw new IllegalStateException("root tenant query failed");
    }

    private TenantOrg getRootTenant() {
        URIQueryResultList tenants = new URIQueryResultList();
        try {
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                    tenants);
            if (tenants.iterator().hasNext()) {
                URI root = tenants.iterator().next();
                TenantOrg rootTenant = _dbClient.queryObject(TenantOrg.class, root);
                // safety check to prevent further operations when root tenant is messed up
                // It is possible have multiple index entries for the same root tenant at a certain period (CQ610571)
                while (tenants.iterator().hasNext()) {
                    URI mulRoot = tenants.iterator().next();
                    if (!mulRoot.equals(root)) {
                        _logger.error("multiple entries found for root tenant. Stop.");
                        return null;
                    }
                }
                return rootTenant;
            } else {
                _logger.error("root tenant query returned no results");
                return null;
            }
        } catch (DatabaseException ex) {
            _logger.error("DatabaseException :", ex);
            return null;
        }
    }

    private List<DataObject> addDataObjects(TenantOrg t, List<FileShare> fsList, List<Project> pjList) {

        // FileShare is local
        FileShare fs1 = new FileShare();
        fs1.setId(URIUtil.createId(FileShare.class));
        fs1.setLabel("testfs1");
        fs1.setNativeGuid(UUID.randomUUID().toString());

        FileShare fs2 = new FileShare();
        fs2.setId(URIUtil.createId(FileShare.class));
        fs2.setLabel("testfs2");
        fs2.setNativeGuid(UUID.randomUUID().toString());

        if (fsList != null) {
            fsList.add(fs1);
            fsList.add(fs2);
        }

        // project is geo
        Project pj1 = new Project();
        pj1.setId(URIUtil.createId(Project.class));
        pj1.setLabel("testpj1");
        pj1.setTenantOrg(new NamedURI(t.getId(), "testpj1"));

        Project pj2 = new Project();
        pj2.setId(URIUtil.createId(Project.class));
        pj2.setLabel("testpj2");
        pj2.setTenantOrg(new NamedURI(t.getId(), "testpj2"));

        if (pjList != null) {
            pjList.add(pj1);
            pjList.add(pj2);
        }

        List<DataObject> dbObjList = new ArrayList<DataObject>();
        dbObjList.add(fs1);
        dbObjList.add(fs2);
        dbObjList.add(pj1);
        dbObjList.add(pj2);

        DbClient dbClient = getDbClient();
        dbClient.createObject(dbObjList);

        return dbObjList;
    }
}
