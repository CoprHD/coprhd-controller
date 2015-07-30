/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.smis;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;

/*
 * Test class to test RefreshRequiredUpdateFunction.call() with simulated objects
 * It uses configuration files from dbclient, dbutils and coordinatorsvc
 */
public class RefreshRequiredUpdateFunctionTest {
    private static final Logger _logger = LoggerFactory
            .getLogger(RefreshRequiredUpdateFunctionTest.class);
    // number of storage systems in DB
    private static final int _storageSystemCount = 5;
    // number of block objects on each storage system, an even number.
    // volume and block snapshot each takes half
    // total number of block objects is _blockObjectCountPerSystem *
    // _storageSystemCount
    private static final int _blockObjectCountPerSystem = 10000;
    private DbClient _dbClient = null;

    // URIs of dummy block objects. Volume and BlockSnapshot are distributed
    // evenly
    private List<URI> _blockObjectURIs = new ArrayList<URI>();

    // list of URIs used to create RefreshRequiredUpdateFunction instance
    private List<URI> _originalBlockObjectList = new ArrayList<URI>();
    // Storage System URI used to create RefreshRequiredUpdateFunction instance
    private URI _storageSystemURI = null;

    @Before
    public void setup() {
        // get DB client
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(
                "dbutils-conf.xml");
        _dbClient = (DbClient) ctx.getBean("dbclient");
        _dbClient.start();
    }

    @After
    public void cleanup() {
        if (_dbClient != null) {
            _dbClient.stop();
        }
    }

    @Test
    /*
     * Test RefreshRequiredUpdateFunction.call method
     */
    public void testRefresh() {
        // populate DB with dummy Volume/BlockSnapshot
        createBlockObjects();
        _originalBlockObjectList.add(_blockObjectURIs.get(0));
        _originalBlockObjectList.add(_blockObjectURIs.get(1));

        RefreshRequiredUpdateFunction refreshFunction = new RefreshRequiredUpdateFunction(
                _storageSystemURI, _originalBlockObjectList, _dbClient);
        long start = System.currentTimeMillis();
        refreshFunction.call();
        _logger.info("Refresh finished within (s) "
                + ((double) System.currentTimeMillis() - start) / 1000);

        // verify block objects and clean up DB
        verifyAndDeleteBlockObjects();
        _logger.info("testRefresh finished");
    }

    /*
     * Create Volume/BlockSnapshot for all Storage Systems
     */
    private void createBlockObjects() {
        for (int i = 0; i < _storageSystemCount; i++) {
            URI storageSystemURI = URIUtil.createId(StorageSystem.class);
            if (i == 0) {
                // use the first storage system to create
                // RefreshRequiredUpdateFunction
                _storageSystemURI = storageSystemURI;
            }

            createBlockObjects(_blockObjectCountPerSystem, storageSystemURI);
        }
    }

    /*
     * Create Volume/BlockSnapshot for one Storage Systems
     * 
     * @param numOfObject number of block objects to be created
     * 
     * @param storageSystemURI URI of Storage Controller
     */
    private void createBlockObjects(int numOfObject, URI storageSystemURI) {
        List<BlockObject> blockObjects = new ArrayList<BlockObject>(numOfObject);
        Random random = new Random();
        boolean isVolume = true;
        for (int i = 0; i < numOfObject; i++) {
            BlockObject blockObject = createBlockObject(isVolume ? Volume.class
                    : BlockSnapshot.class);
            _blockObjectURIs.add(blockObject.getId());
            blockObject.setStorageController(storageSystemURI);

            // set the refreshRequired flag randomly
            blockObject.setRefreshRequired(random.nextBoolean());
            blockObjects.add(blockObject);

            // create Volume and BlockSnapshot alternatively
            isVolume = !isVolume;
        }

        _dbClient.createObject(blockObjects);
    }

    /*
     * Create one block object
     * 
     * @param clazz type of block object to be created
     * 
     * @return block object of given type, not persisted yet
     */
    private BlockObject createBlockObject(Class<? extends BlockObject> clazz) {
        BlockObject blockObject = null;

        try {
            blockObject = clazz.newInstance();
            URI blockObjectURI = URIUtil.createId(clazz);
            blockObject.setId(blockObjectURI);
            // set the object active
            blockObject.setInactive(false);
            blockObject.setLabel("Dummy Object");
            blockObject.setNativeGuid("SYMMETRIX+000123456789+VOLUME+00000");
        } catch (InstantiationException e) {
            _logger.error("InstantiationException: " + e.getMessage());
        } catch (IllegalAccessException e) {
            _logger.error("IllegalAccessException: " + e.getMessage());
        }

        Assert.assertNotNull(blockObject);
        return blockObject;
    }

    /*
     * Verify all objects, and delete them from DB
     */
    private void verifyAndDeleteBlockObjects() {
        List<URI> volumeURIs = new ArrayList<URI>();
        List<URI> blockSnapshotURIs = new ArrayList<URI>();
        for (URI uri : _blockObjectURIs) {
            if (URIUtil.isType(uri, Volume.class)) {
                volumeURIs.add(uri);
            } else {
                blockSnapshotURIs.add(uri);
            }
        }

        // verify and delete Volume and BlockSnapshot
        verifyAndDeleteBlockObjects(Volume.class, volumeURIs);
        verifyAndDeleteBlockObjects(BlockSnapshot.class, blockSnapshotURIs);
    }

    /*
     * Verify a list of block objects of given type
     */
    private <T extends BlockObject> void verifyAndDeleteBlockObjects(
            Class<T> clazz, List<URI> blockObjectURIs) {
        // get all objects in the list
        Iterator<T> iBlockObjects = _dbClient.queryIterativeObjects(clazz,
                blockObjectURIs);
        List<T> blockObjects = new ArrayList<T>();
        while (iBlockObjects.hasNext()) {
            T blockObject = iBlockObjects.next();
            // check if it should be refreshed
            if (this._originalBlockObjectList.contains(blockObject.getId())
                    || _storageSystemURI.equals(blockObject
                            .getStorageController())) {
                // verify it has been refreshed
                Assert.assertFalse(blockObject.getRefreshRequired());
            }

            // add to list to be deleted
            blockObjects.add(blockObject);
        }

        // delete all objects in the list
        _dbClient.removeObject(blockObjects
                .toArray(new BlockObject[blockObjects.size()]));
        blockObjectURIs.clear();
    }
}