/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.NetworkVarrayIndexMigration;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * @author cgarber
 * 
 */
public class NetworkVarrayIndexMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger logger = LoggerFactory.getLogger(NetworkVarrayIndexMigrationTest.class);

    private VirtualArray virtualArray1;
    private VirtualArray virtualArray2;
    private Network network;

    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
            {
                // Add your implementation of migration callback below.
                add(new NetworkVarrayIndexMigration());
            }
        });

        DbSimpleMigrationTestBase.initialSetup(new AlterSchema() {
            @Override
            protected void process() {
                replaceIndexCf(Network.class, "assignedVirtualArrays", "RelationIndex");
                replaceIndexCf(Network.class, "connectedVirtualArrays", "RelationIndex");
            }
        });
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getSourceVersion()
     */
    @Override
    protected String getSourceVersion() {
        return "1.1";
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getTargetVersion()
     */
    @Override
    protected String getTargetVersion() {
        return "2.0";
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#prepareData()
     */
    @Override
    protected void prepareData() throws Exception {
        DbClient dbClient = getDbClient();

        virtualArray1 = new VirtualArray();
        virtualArray1.setId(URIUtil.createId(VirtualArray.class));
        virtualArray1.setLabel("varray1");
        dbClient.createObject(virtualArray1);

        network = new Network();
        network.setId(URIUtil.createId(Network.class));
        network.setLabel("networkObj");

        StringSet varrayStrSet1 = new StringSet();
        varrayStrSet1.add(virtualArray1.getId().toString());
        network.setAssignedVirtualArrays(varrayStrSet1);

        virtualArray2 = new VirtualArray();
        virtualArray2.setId(URIUtil.createId(VirtualArray.class));
        virtualArray2.setLabel("varray2");
        dbClient.createObject(virtualArray2);

        StringSet varrayStrSet2 = new StringSet();
        varrayStrSet2.add(virtualArray1.getId().toString());
        varrayStrSet2.add(virtualArray2.getId().toString());

        network.setConnectedVirtualArrays(varrayStrSet2);

        dbClient.createObject(network);

        // prove that we've reproduced the problem
        List<Network> assignedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, virtualArray2.getId(), Network.class, "assignedVirtualArrays");

        // should be false once the index is fixed
        Assert.assertTrue(assignedNetworks.iterator().hasNext());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#verifyResults()
     */
    @Override
    protected void verifyResults() throws Exception {
        DbClient dbClient = getDbClient();

        List<Network> assignedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, virtualArray1.getId(), Network.class, "assignedVirtualArrays");

        Assert.assertTrue(assignedNetworks.iterator().hasNext());
        Assert.assertEquals(assignedNetworks.size(), 1);

        List<Network> connectedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, virtualArray2.getId(), Network.class, "connectedVirtualArrays");

        Assert.assertTrue(connectedNetworks.iterator().hasNext());
        Assert.assertEquals(connectedNetworks.size(), 1);

        connectedNetworks.clear();
        connectedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, virtualArray1.getId(), Network.class, "connectedVirtualArrays");

        Assert.assertTrue(connectedNetworks.iterator().hasNext());
        Assert.assertEquals(connectedNetworks.size(), 1);

        assignedNetworks.clear();
        assignedNetworks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, virtualArray2.getId(), Network.class, "assignedVirtualArrays");

        Assert.assertFalse(assignedNetworks.iterator().hasNext());
    }

}
