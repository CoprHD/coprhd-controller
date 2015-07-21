/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.gc;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.db.server.geo.DbsvcGeoTestBase;

/**
 * garbage collector tests
 */
public class GlobalGarbageCollectorTests extends DbsvcGeoTestBase {
    private static final Logger _log = LoggerFactory
            .getLogger(GlobalGarbageCollectorTests.class);

    private GarbageCollectionExecutor _localGCExecutor;
    private GarbageCollectionExecutor _globalGCExecutor;
    private DbClient _dbClient = getDbClient();
    private DataObjectScanner _scanner;
        
    public GlobalGarbageCollectorTests(){
       
    }

    private void initGCExecutor() throws IOException, URISyntaxException {
        _scanner = new DataObjectScanner();
        _scanner.setPackages("com.emc.storageos.db.client.model");
        _scanner.init();

        _localGCExecutor = new GarbageCollectionExecutor();
        _localGCExecutor.setDataObjectScanner(_scanner);

        _globalGCExecutor = new GarbageCollectionExecutor();
        _globalGCExecutor.setDataObjectScanner(_scanner);

        GarbageCollectionExecutorLoop localGCExecutorLoop = new LocalGCExecutorLoop();
        localGCExecutorLoop.setDbClient(_dbClient);
        localGCExecutorLoop.setCoordinator(geoRunner.getCoordinator());
        localGCExecutorLoop.setGcDelayMins(0);

        GarbageCollectionExecutorLoop globalGCExecutorLoop = new GlobalGCExecutorLoop();
        globalGCExecutorLoop.setDbClient(_dbClient);
        globalGCExecutorLoop.setCoordinator(geoRunner.getCoordinator());
        globalGCExecutorLoop.setGcDelayMins(0);

        _localGCExecutor.setGcExecutor(localGCExecutorLoop);
        _globalGCExecutor.setGcExecutor(globalGCExecutorLoop);

    }

    @Test
    public void testDependencyChecker() throws Exception {
        int num_projects = 10;
        int num_fs = 110;

        initGCExecutor();
        DependencyChecker checker = new DependencyChecker(_dbClient, _scanner);
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URIUtil.createId(VirtualPool.class));
        vpool.setLabel("GOLD");
        vpool.setType("file");
        vpool.setProtocols(new StringSet());
        _dbClient.createObject(vpool);

        TenantOrg tenant = new TenantOrg();
        tenant.setId(URIUtil.createId(TenantOrg.class));
        tenant.setLabel("tenant dependency tests");
        List<URI> activeProjects = new ArrayList<URI>();
        for (int i = 0; i < num_projects; i++) {
            Project p = new Project();
            p.setId(URIUtil.createId(Project.class));
            p.setLabel("dependency test project");
            p.setTenantOrg(new NamedURI(tenant.getId(), "dependency test project"));
            _dbClient.createObject(p);
            activeProjects.add(p.getId());
        }

        Assert.assertNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));
        Assert.assertNotNull(checker.checkDependencies(tenant.getId(), TenantOrg.class, true));

        Map<URI, List<URI>> activeFileSystems = new HashMap<URI, List<URI>>();
        for (int i = 0; i < num_fs; i++) {
            String label = "fileshare dependency tests " + i;
            FileShare fs = new FileShare();
            fs.setId(URIUtil.createId(FileShare.class));
            fs.setLabel(label);
            fs.setCapacity(102400L);
            fs.setVirtualPool(vpool.getId());
            SMBShareMap shareMap = new SMBShareMap();
            shareMap.put("test", new SMBFileShare("blah", "blah", "blah", "blah", 1));
            fs.setSMBFileShares(shareMap);

            URI proj = activeProjects.get(new Random().nextInt(activeProjects.size()));
            fs.setProject(new NamedURI(proj, label));
            _dbClient.createObject(fs);
            if (!activeFileSystems.containsKey(proj)) {
                activeFileSystems.put(proj, new ArrayList<URI>());
            }
            activeFileSystems.get(fs.getProject().getURI()).add(fs.getId());
        }

        Assert.assertNotNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));
        for (int i = 0; i < num_projects; i++) {
            if (activeFileSystems.containsKey(activeProjects.get(i))) {
                Assert.assertNotNull(checker.checkDependencies(activeProjects.get(i),Project.class, true));
            } else {
                Assert.assertNull(checker.checkDependencies(activeProjects.get(i),Project.class, true));
            }
        }

        List<URI> inactiveRefProjects = new ArrayList<URI>();
        List<URI> lastRefProjects = new ArrayList<URI>();
        for (Map.Entry<URI, List<URI>> entry : activeFileSystems.entrySet()) {
            if (entry.getValue().size() % 3 == 0) {
                // inactive all refs
                for (URI uri : entry.getValue()) {
                    FileShare fs = new FileShare();
                    fs.setId(uri);
                    fs.setInactive(true);
                    _dbClient.updateAndReindexObject(fs);
                }
                inactiveRefProjects.add(entry.getKey());
            } else if (entry.getValue().size() % 3 == 2) {
                // inactive all but last ref
                for (int i = 0; i < entry.getValue().size() - 1; i++) {
                    FileShare fs = new FileShare();
                    fs.setId(entry.getValue().get(i));
                    fs.setInactive(true);
                    _dbClient.updateAndReindexObject(fs);
                }
                lastRefProjects.add(entry.getKey());
            } else {
                continue;
            }
        }

        Assert.assertNotNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));
        Assert.assertNotNull(checker.checkDependencies(tenant.getId(), TenantOrg.class, true));
        for (int i = 0; i < num_projects; i++) {
            URI p = activeProjects.get(i);
            if (inactiveRefProjects.contains(p)) {
                Assert.assertNull(checker.checkDependencies(p, Project.class, true));
                Assert.assertNotNull(checker.checkDependencies(p, Project.class, false));
            } else {
                Assert.assertNotNull(checker.checkDependencies(p, Project.class, true));
                if (lastRefProjects.contains(p)) {
                    URI lastFs = activeFileSystems.get(p).get(
                            activeFileSystems.get(p).size() - 1);
                    FileShare fs = new FileShare();
                    fs.setId(lastFs);
                    fs.setInactive(true);
                    _dbClient.updateAndReindexObject(fs);
                } else {
                    for (URI uri : activeFileSystems.get(p)) {
                        FileShare fs = new FileShare();
                        fs.setId(uri);
                        fs.setInactive(true);
                        _dbClient.updateAndReindexObject(fs);
                    }
                }
            }
        }

        Assert.assertNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));
        Assert.assertNotNull(checker.checkDependencies(tenant.getId(), TenantOrg.class, true));
        for (int i = 0; i < num_projects; i++) {
            Assert.assertNull(checker.checkDependencies(activeProjects.get(i),Project.class, true));
            Assert.assertNotNull(checker.checkDependencies(activeProjects.get(i),Project.class, false));
            Project p = new Project();
            p.setId(activeProjects.get(i));
            p.setInactive(true);
            _dbClient.updateAndReindexObject(p);
        }

        Assert.assertNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, true));
        Assert.assertNull(checker.checkDependencies(tenant.getId(), TenantOrg.class, true));
        Assert.assertNotNull(checker.checkDependencies(tenant.getId(), TenantOrg.class, false));

        _localGCExecutor.runNow();
        _globalGCExecutor.runNow();

        Assert.assertNull(checker.checkDependencies(vpool.getId(), VirtualPool.class, false));
        Assert.assertNull(checker.checkDependencies(tenant.getId(), TenantOrg.class, true));
        Assert.assertNull(checker.checkDependencies(tenant.getId(), TenantOrg.class, false));

        tenant.setInactive(true);
        _dbClient.updateAndReindexObject(tenant);
        vpool.setInactive(true);
        _dbClient.updateAndReindexObject(vpool);

        _localGCExecutor.runNow();
        _globalGCExecutor.runNow();

        Assert.assertNull(_dbClient.queryObject(TenantOrg.class, tenant.getId()));
        Assert.assertNull(_dbClient.queryObject(VirtualPool.class, vpool.getId()));
    }

}
