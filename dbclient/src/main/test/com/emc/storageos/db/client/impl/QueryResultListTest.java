package com.emc.storageos.db.client.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbClientMock;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Volume;

public class QueryResultListTest {
    private static Logger logger = LoggerFactory.getLogger(QueryResultListTest.class);
    private DbClient dbClient;
    private List<URI> ids;
    
    @Before
    public void setup() {
        this.dbClient = new DbClientMock();
        this.ids = new ArrayList<URI>();
    }
    
    @Test
    public void shouldSizeNoChangeIfNoModification() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertEquals(this.ids.size(), volumes.size());
    }
    
    @Test
    public void shouldSizeChangeIfRemove() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        Volume volume = new Volume();
        volume.setId(this.ids.get(0));
        this.dbClient.removeObject(volume);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertNotEquals(this.ids.size(), volumes.size());
    }
    
    @Test
    public void shouldNotEmptyIfHasData() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertFalse(volumes.isEmpty());
    }
    
    @Test
    public void shouldEmptyIfNoData() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        this.removeObject(Volume.class, this.ids);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertTrue(volumes.isEmpty());
    }
    
    @Test
    public void shouldContainIfHasElement() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertTrue(volumes.contains(this.constructDataObject(Volume.class, this.ids.get(0))));
    }
    
    @Test
    public void shouldNotContainIfNoElement() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertFalse(volumes.contains(this.constructDataObject(Volume.class, null)));
    }
    
    @Test
    public void shouldToArrayWorks() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Object[] array =  volumes.toArray();
        Assert.assertNotNull(array);
        Assert.assertEquals(this.ids.size(), array.length);
    }
    
    @Test
    public void shouldAddNotTouchDb() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Volume object = (Volume) constructDataObject(Volume.class);
        volumes.add(object);
        this.ids.add(object.getId());
        Assert.assertTrue(this.ids.size()!=volumes.size());
    }
    
    

    @Test
    public void shouldGetIfNoModification() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertNotNull(volumes.get(0));
        this.cleanAll();
    }
    
    @Test
    public void shouldEnhanceForLoopWork() {
        int expectCount = randInt(500, 1000);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        int count = 0;
        for (Volume v : volumes) {
            count++;
        }
        Assert.assertEquals(expectCount, count);
        this.cleanAll();
    }
    
    private void createDataObject(Class<? extends DataObject> clazz, int count) {
        try {
                Set<DataObject> objects = new HashSet<DataObject>();
                for (int i=0; i<count; i++) {
                    DataObject object = clazz.newInstance();
                    URI id = URIUtil.createId(clazz);
                    this.ids.add(id);
                    object.setId(id);
                    objects.add(object);
                }
                this.dbClient.createObject(objects);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
    }
    
    private DataObject constructDataObject(Class<? extends DataObject> clazz, URI id) {
        
        try {
            DataObject object;
            object = clazz.newInstance();
            object.setId(id);
            return object;
        } catch (Exception e) {
            logger.error("initiaze DataObject fail", e);
            throw new RuntimeException(e);
        }
    }
    
    private DataObject constructDataObject(Class<? extends DataObject> clazz) {
        
        try {
            DataObject object;
            object = clazz.newInstance();
            object.setId(URIUtil.createId(clazz));
            return object;
        } catch (Exception e) {
            logger.error("initiaze DataObject fail", e);
            throw new RuntimeException(e);
        }
    }
    
    private void removeObject(Class<? extends DataObject> clazz, Collection<URI> ids) {
        try {
                DataObject[] objects = new DataObject[ids.size()];
                int index = 0;
                for (URI id : ids) {
                    DataObject object = clazz.newInstance();
                    object.setId(id);
                    objects[index++] = object;
                }
                this.dbClient.removeObject(objects);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
    }
    

    private void cleanAll() {
        this.ids.clear();
        ((DbClientMock) dbClient).removeAll();
    }
    
    public static int randInt(int min, int max) {
        Random rand = new Random();
        int randomNum = rand.nextInt((max - min) + 1) + min;
        return randomNum;
    }
}
