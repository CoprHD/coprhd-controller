/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.mock;

import java.net.URI;
import com.emc.sa.model.dao.BourneDbClient;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.EncryptionProvider;

import static com.emc.sa.model.mock.LocalCassandraService.*;

public class LocalDbClient extends BourneDbClient {

    public static final CoordinatorClient DEFAULT_COORDINATOR_CLIENT = new StubCoordinatorClientImpl(
            URI.create("thrift://localhost:9160"));
    public static final EncryptionProvider DEFAULT_ENCRYPTION_PROVIDER = new NoEncryptionProvider();

    public LocalDbClient() {
        DbClientImpl dbClient = new DbClientImpl();
        dbClient.setCoordinatorClient(DEFAULT_COORDINATOR_CLIENT);
        dbClient.setClusterName(DEFAULT_CLUSTER_NAME);
        dbClient.setKeyspaceName(DEFAULT_KEYSPACE_NAME);
        dbClient.setEncryptionProvider(DEFAULT_ENCRYPTION_PROVIDER);
        DbVersionInfo versionInfo = new DbVersionInfo();
        versionInfo.setSchemaVersion("1.1");
        dbClient.setDbVersionInfo(versionInfo);
        setDbClient(dbClient);
    }

    public static LocalDbClient create() {
        LocalDbClient client = new LocalDbClient();
        client.init();
        return client;
    }
}
