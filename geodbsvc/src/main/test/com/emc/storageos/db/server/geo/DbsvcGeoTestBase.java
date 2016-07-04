/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.geo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.impl.IndexCleanupList;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.services.util.LoggingUtils;

//Suppress Sonar violation of Lazy initialization of static fields should be synchronized
//Junit test will be called in single thread by default, it's safe to ignore this violation
@SuppressWarnings("squid:S2444")
public class DbsvcGeoTestBase {

    static {
        LoggingUtils.configureIfNecessary("geodbtest-log4j.properties");
    }
    private static final Logger log = LoggerFactory.getLogger(DbsvcGeoTestBase.class);

    protected static CoordinatorClient _coordinator;
    protected static EncryptionProviderImpl _encryptionProvider = new EncryptionProviderImpl();
    protected static EncryptionProviderImpl _geoEncryptionProvider = new EncryptionProviderImpl();
    protected static DbVersionInfo _dbVersionInfo;
    protected static TestGeoDbClientImpl _dbClient;

    protected static DbSvcRunner geoRunner;
    protected static DbSvcRunner localRunner;

    @BeforeClass
    public static void setup() {
        _dbVersionInfo = new DbVersionInfo();
        _dbVersionInfo.setSchemaVersion(DbSvcRunner.SVC_VERSION);

        geoRunner = new DbSvcRunner(DbSvcRunner.GEODBSVC_CONFIG, Constants.GEODBSVC_NAME);
        geoRunner.startCoordinator();
        geoRunner.start();

        localRunner = new DbSvcRunner(DbSvcRunner.DBSVC_CONFIG, Constants.DBSVC_NAME);
        localRunner.start();

        // geoRunner.waitUntilStarted(100);
        try {
            configDbClient();
        } catch (Exception e) {
            log.error("Error configuring dbclient", e);
            return;
        }
    }

    @AfterClass
    public static void stop() {
        localRunner.stop();
        geoRunner.stop();
    }

    protected static TestGeoDbClientImpl getDbClient() {
        try {
            TestGeoDbClientImpl dbClient = getDbClientBase();

            DbClientContext geoCtx = new DbClientContext();
            geoCtx.setClusterName("GeoStorageOS");
            geoCtx.setKeyspaceName("GeoStorageOS");
            dbClient.setGeoContext(geoCtx);

            DbClientContext localCtx = new DbClientContext();
            localCtx.setClusterName("StorageOS");
            localCtx.setKeyspaceName("StorageOS");
            dbClient.setLocalContext(localCtx);

            // dbClient.setGeoContext(new DbClientContext());

            dbClient.start();
            VdcUtil.setDbClient(dbClient);
            return dbClient;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Create DbClient to embedded DB
     * 
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    protected static void configDbClient() throws URISyntaxException, IOException {
        if (_dbClient == null) {
            _dbClient = getDbClient();
            log.info("DB client started OK");
        }
    }

    protected static TestGeoDbClientImpl getDbClientBase() throws URISyntaxException, IOException {
        _coordinator = geoRunner.getCoordinator();
        TestGeoDbClientImpl dbClient = new TestGeoDbClientImpl();
        dbClient.setCoordinatorClient(_coordinator);
        dbClient.setDbVersionInfo(_dbVersionInfo);
        dbClient.setBypassMigrationLock(true);
        _encryptionProvider.setCoordinator(_coordinator);
        dbClient.setEncryptionProvider(_encryptionProvider);
        _geoEncryptionProvider.setCoordinator(_coordinator);
        _geoEncryptionProvider.setEncryptId("geoid");
        dbClient.setGeoEncryptionProvider(_geoEncryptionProvider);

        return dbClient;
    }

    protected static class TestGeoDbClientImpl extends DbClientImpl {

        public boolean isItWhereItShouldBe(DataObject dbObj) {
            if ((dbObj.getClass().isAnnotationPresent(DbKeyspace.class) &&
                    Arrays.asList(dbObj.getClass().getAnnotation(DbKeyspace.class).value()).contains(Keyspaces.GLOBAL) && Arrays.asList(
                    dbObj.getClass().getAnnotation(DbKeyspace.class).value()).contains(Keyspaces.LOCAL))) {
                // it's hybrid, check the id
                if (dbObj.isGlobal()) {
                    return (queryObject(geoContext, dbObj.getClass(), dbObj.getId()) != null);
                } else {
                    return (queryObject(localContext, dbObj.getClass(), dbObj.getId()) != null);
                }
            } else if (dbObj.getClass().isAnnotationPresent(DbKeyspace.class) &&
                    Arrays.asList(dbObj.getClass().getAnnotation(DbKeyspace.class).value()).contains(Keyspaces.GLOBAL)) {
                return (queryObject(geoContext, dbObj.getClass(), dbObj.getId()) != null);
            } else {
                return (queryObject(localContext, dbObj.getClass(), dbObj.getId()) != null);
            }
        }

        private <T extends DataObject> T queryObject(DbClientContext dbContext, Class<T> clazz, URI id)
                throws DatabaseException {
            DataObjectType doType = TypeMap.getDoType(clazz);
            if (doType == null) {
                throw new IllegalArgumentException();
            }
            Map<String, List<CompositeColumnName>> row = queryRowWithAllColumns(dbContext, clazz, id, doType.getCF().getName());
            if (row == null) {
                return null;
            }
            IndexCleanupList cleanList = new IndexCleanupList();
            T dataObject = doType.deserialize(clazz, id.toString(), row.get(id.toString()), cleanList, null);
            return dataObject;
        }

        private <T extends DataObject> Map<String, List<CompositeColumnName>> queryRowWithAllColumns(DbClientContext dbContext, Class<T> clazz, URI id,
                String cf) throws DatabaseException {
            List<URI> collection = new ArrayList<URI>(1);
            collection.add(id);
            Map<String, List<CompositeColumnName>> result = queryRowsWithAllColumns(dbContext, collection, cf);
            if (result == null || result.get(id.toString()) == null) {
                return null;
            }
            return result;
        }

    }

    protected boolean isItWhereItShouldBe(DataObject dbObj) {
        return _dbClient.isItWhereItShouldBe(dbObj);
    }

}
