/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VirtualPoolExpandableMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Unit test for VirtualPoolExpandableMigration procedure
 */
public class VirtualPoolExpandableMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(VirtualPoolExpandableMigrationTest.class);
    @BeforeClass
    public static void setup() throws IOException {

        /**
         * Define a custom migration callback map.
         * The key should be the source version from getSourceVersion().
         * The value should be a list of migration callbacks under test.
         */
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new VirtualPoolExpandableMigration());
        }});

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "1.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.0";
    }

    @Override
    protected void prepareData() throws Exception {

        // clean up before the test
        log.info("Start data preparation for VirtualPool instances. ");
        deleteVirtualPools();

        List<VirtualPool> vpList = new ArrayList<VirtualPool>();
        VirtualPool vpExpandable = new VirtualPool();
        vpExpandable.setId(URIUtil.createId(VirtualPool.class));
        VirtualPool vpNonExpandable = new VirtualPool();
        vpNonExpandable.setId(URIUtil.createId(VirtualPool.class));
        VirtualPool vpNonExpandableWithMirrors = new VirtualPool();
        vpNonExpandableWithMirrors.setId(URIUtil.createId(VirtualPool.class));

        vpList.add(vpExpandable);
        vpList.add(vpNonExpandable);
        vpList.add(vpNonExpandableWithMirrors);

        vpExpandable.setNonDisruptiveExpansion(true);
        vpNonExpandable.setNonDisruptiveExpansion(false);
        vpNonExpandableWithMirrors.setNonDisruptiveExpansion(false);
        vpNonExpandableWithMirrors.setMaxNativeContinuousCopies(3);
        _dbClient.createObject(vpList);

        log.info("End data preparation for VirtualPool instances. ");
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> vpURIs = _dbClient.queryByType(VirtualPool.class, false);
        Iterator<VirtualPool> vpIter = _dbClient.queryIterativeObjects(VirtualPool.class, vpURIs);
        while (vpIter.hasNext()) {
            VirtualPool vp = vpIter.next();
            log.info(String.format("Verifying VirtualPool %s, nonDisruptiveExpansion: %s, local mirrors: %s, expandable: %s, fastExpansion; %s",
                    vp.getId().toString(), vp.getNonDisruptiveExpansion(), vp.getMaxNativeContinuousCopies(), vp.getExpandable(), vp.getFastExpansion()));
            if (vp.getNonDisruptiveExpansion()) {
                Assert.assertTrue("For vpool with nonDisruptiveExpansion true, expandable and fastExpansion properties should be true.",
                        vp.getExpandable() && vp.getFastExpansion());
            } else if (VirtualPool.vPoolSpecifiesMirrors(vp, _dbClient)){
                Assert.assertTrue("For vpool which specifies local mirrors, expandable and fastExpansion properties should be both false.",
                        !(vp.getExpandable() || vp.getFastExpansion()));
            } else {
                Assert.assertTrue("For vpool with nonDisruptiveExpansion false and no local mirrors, expandable should be false and fastExpansion" +
                                " should be false.", !(vp.getExpandable() || vp.getFastExpansion()));
            }
        }
        // clean up after the test
        deleteVirtualPools();
    }

    private void deleteVirtualPools() {
        List<URI> vpURIs = _dbClient.queryByType(VirtualPool.class, false);
        Iterator<VirtualPool> vpIter = _dbClient.queryIterativeObjects(VirtualPool.class, vpURIs);

        while (vpIter.hasNext()) {
            _dbClient.removeObject(vpIter.next());
        }
    }
}

