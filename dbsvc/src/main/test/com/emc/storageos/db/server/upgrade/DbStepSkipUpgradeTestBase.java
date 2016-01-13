/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade;

import java.net.URI;
import java.util.*;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.util.BaseTestCustomMigrationCallback;

/**
 * base class for upgrade tests using test models
 */
public abstract class DbStepSkipUpgradeTestBase extends DbsvcTestBase {
    private static final Logger log = LoggerFactory.getLogger(DbStepSkipUpgradeTestBase.class);
    List<URI> r3List = new ArrayList<URI>();
    List<URI> r2List = new ArrayList<URI>();
    List<URI> r1List = new ArrayList<URI>();

    private Map<String, List<URI>> expectedAltIndexLists = new HashMap<String, List<URI>>();
    private Map<URI, List<URI>> expectedRelIndexLists = new HashMap<URI, List<URI>>();
    private Map<URI, List<NamedURI>> expectedNamedRelIndexLists = new HashMap<URI, List<NamedURI>>();

    protected static final String initalVersion = "1.2";
    protected static final String firstUpgradeVersion = "1.3";
    protected static final String secondUpgradeVersion = "1.4";
    protected static final List<BaseTestCustomMigrationCallback> firstUpgradeCallbacks = new ArrayList<BaseTestCustomMigrationCallback>();
    protected static final List<BaseTestCustomMigrationCallback> secondUpgradeCallbacks = new ArrayList<BaseTestCustomMigrationCallback>();

    @Before
    public void setupTest() {
        firstUpgradeCallbacks.add(new com.emc.storageos.db.server.upgrade.util.callbacks.Resource3RefPopulator());
        firstUpgradeCallbacks.add(new com.emc.storageos.db.server.upgrade.util.callbacks.Resource3FlagsInitializer());
        firstUpgradeCallbacks.add(new com.emc.storageos.db.server.upgrade.util.callbacks.Resource3Resource4RefInitializer());
        firstUpgradeCallbacks.add(new com.emc.storageos.db.server.upgrade.util.callbacks.Resource4KeyInitializer());

        // callbacks2.add(new com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3RefPopulator());
        // callbacks2.add(new com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3FlagsInitializer());
        secondUpgradeCallbacks.add(new com.emc.storageos.db.server.upgrade.util.callbacks2.Resource5Initializer());
        secondUpgradeCallbacks.add(new com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3NewFlagsInitializer());
        // callbacks2.add(new com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3Resource4RefInitializer());
        // callbacks2.add(new com.emc.storageos.db.server.upgrade.util.callbacks2.Resource4KeyInitializer());
    }

    private void addToExpectedRelIndexLists(URI key, URI value) {
        if (!expectedRelIndexLists.containsKey(key)) {
            expectedRelIndexLists.put(key, new ArrayList<URI>());
        }
        expectedRelIndexLists.get(key).add(value);
    }

    private void addToExpectedNamedRelIndexLists(URI key, NamedURI value) {
        if (!expectedNamedRelIndexLists.containsKey(key)) {
            expectedNamedRelIndexLists.put(key, new ArrayList<NamedURI>());
        }
        expectedNamedRelIndexLists.get(key).add(value);
    }

    private void addToExpectedAltIndexLists(String key, URI value) {
        if (!expectedAltIndexLists.containsKey(key)) {
            expectedAltIndexLists.put(key, new ArrayList<URI>());
        }
        expectedAltIndexLists.get(key).add(value);
    }

    protected void prepareData1() throws Exception {
        prepareData(1, 1);
        customMigrationCallbacks.put(firstUpgradeVersion, new ArrayList<BaseCustomMigrationCallback>(firstUpgradeCallbacks));
    }

    protected void prepareData2() throws Exception {
        customMigrationCallbacks.put(secondUpgradeVersion, new ArrayList<BaseCustomMigrationCallback>(secondUpgradeCallbacks));
    }

    protected void prepareData(int iterNum1, int iterNum2) throws Exception {
        DbClient dbClient = getDbClient();
        for (int i = 0; i < iterNum1; i++) {
            com.emc.storageos.db.server.upgrade.util.models.old.Resource3 r3 = new com.emc.storageos.db.server.upgrade.util.models.old.Resource3();
            r3.setId(URIUtil.createId(com.emc.storageos.db.server.upgrade.util.models.old.Resource3.class));
            dbClient.createObject(r3);
            com.emc.storageos.db.server.upgrade.util.models.old.Resource3 r3second = new com.emc.storageos.db.server.upgrade.util.models.old.Resource3();
            r3second.setId(URIUtil.createId(com.emc.storageos.db.server.upgrade.util.models.old.Resource3.class));
            dbClient.createObject(r3second);
            r3List.add(r3.getId());
            r3List.add(r3second.getId());

            // add Resource6 objects
            com.emc.storageos.db.server.upgrade.util.models.old.Resource6 r6 = new com.emc.storageos.db.server.upgrade.util.models.old.Resource6();
            r6.setId(URIUtil.createId(com.emc.storageos.db.server.upgrade.util.models.old.Resource6.class));
            dbClient.createObject(r6);
            com.emc.storageos.db.server.upgrade.util.models.old.Resource6 r6second = new com.emc.storageos.db.server.upgrade.util.models.old.Resource6();
            r6second.setId(URIUtil.createId(com.emc.storageos.db.server.upgrade.util.models.old.Resource6.class));
            dbClient.createObject(r6second);

            for (int j = 0; j < iterNum2; j++) {
                com.emc.storageos.db.server.upgrade.util.models.old.Resource1 r1 = new com.emc.storageos.db.server.upgrade.util.models.old.Resource1();
                r1.setId(URIUtil.createId(com.emc.storageos.db.server.upgrade.util.models.old.Resource1.class));
                r1.setLabel("resource1_" + i + "_" + j);
                r1.setRes3Map(new StringMap());
                r1.getRes3Map().put(r3.getId().toString(), "test1");
                r1.getRes3Map().put(r3second.getId().toString(), "test2");
                dbClient.createObject(r1);
                r1List.add(r1.getId());
                // expected relational index entries
                // r3.getId -> r1.getId
                // r3second.getId -> r1.getId
                addToExpectedRelIndexLists(r3.getId(), r1.getId());
                addToExpectedRelIndexLists(r3second.getId(), r1.getId());

                com.emc.storageos.db.server.upgrade.util.models.old.Resource2 r2 = new com.emc.storageos.db.server.upgrade.util.models.old.Resource2();
                r2.setId(URIUtil.createId(com.emc.storageos.db.server.upgrade.util.models.old.Resource2.class));
                r2.setLabel("resource2_" + i + "_" + j);
                r2.setRes1(new NamedURI(r1.getId(), r2.getLabel()));
                // expected named relational index entries
                // r1.getId -> r2.getId, r2.getLabel
                addToExpectedNamedRelIndexLists(r1.getId(), new NamedURI(r2.getId(), r2.getLabel()));

                r2.setAssociated(new StringSet());
                r2.getAssociated().add(r2.getLabel());
                r2.getAssociated().add("resource2_" + i);
                // expected alt id index entries
                // r2.label -> r2.getId
                // "resource2_i" -> r2.getId
                addToExpectedAltIndexLists(r2.getLabel(), r2.getId());
                addToExpectedAltIndexLists("resource2_" + i, r2.getId());

                dbClient.createObject(r2);
                r2List.add(r2.getId());
            }
        }

    }

    protected void verifyAll() throws Exception {
        firstUpgradeVerifyResults();
        secondUpgradeVerifyResults();
    }

    protected void firstUpgradeVerifyResults() throws Exception {
        for (BaseTestCustomMigrationCallback cb : firstUpgradeCallbacks) {
            cb.setDbClient(getDbClient());
            cb.verify();
        }
        log.info("verifyResults1: Done.");
    }

    protected void secondUpgradeVerifyResults() throws Exception {
        for (BaseTestCustomMigrationCallback cb : secondUpgradeCallbacks) {
            cb.verify();
        }
        log.info("verifyResults1: Done.");
    }

    protected void setupDB(String sourceVersion, String targetVersion, String targetModels) throws Exception {
        startDb(sourceVersion, targetVersion, targetModels);
    }

    protected void setupDB(String targetVersion, String targetModels, List<BaseCustomMigrationCallback> callbacks) throws Exception {
        customMigrationCallbacks.put(_coordinator.getCurrentDbSchemaVersion(), callbacks);
        startDb(targetVersion, targetVersion, targetModels);
    }
}
