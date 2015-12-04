package com.emc.storageos.db.client.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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
        this.cleanAll();
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
        this.cleanAll();
    }
    
    @Test
    public void shouldNotEmptyIfHasData() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertFalse(volumes.isEmpty());
        this.cleanAll();
    }
    
    @Test
    public void shouldEmptyIfNoData() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertNotNull(volumes);
        Assert.assertTrue(volumes.size() > 0);
        this.removeObject(Volume.class, this.ids);
        List<Volume> copy = new ArrayList<Volume> (volumes);
        volumes.removeAll(copy);
        Assert.assertTrue(volumes.isEmpty());
        this.cleanAll();
    }
    
    @Test
    public void shouldContainIfHasElement() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertTrue(volumes.contains(this.constructDataObject(Volume.class, this.ids.get(0))));
        this.cleanAll();
    }
    
    @Test
    public void shouldNotContainIfNoElement() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertFalse(volumes.contains(this.constructDataObject(Volume.class, null)));
        this.cleanAll();
    }
    
    @Test
    public void shouldToArrayWorks() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Object[] array =  volumes.toArray();
        Assert.assertNotNull(array);
        Assert.assertEquals(this.ids.size(), array.length);
        this.cleanAll();
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
        this.cleanAll();
    }
    
    @Test
    public void shouldRemoveNotTouchDb() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertTrue(this.ids.size()==volumes.size());
        Volume removedVolume = volumes.remove(0);
        Assert.assertNotNull(removedVolume);
        Assert.assertTrue(this.ids.size()!=volumes.size());
        this.cleanAll();
    }

    @Test
    public void shouldIndexOfWork() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        int index = volumes.indexOf(this.constructDataObject(Volume.class, this.ids.get(0)));
        Assert.assertTrue(index != -1);
        this.cleanAll();
    }
    
    @Test
    public void shouldContainsAllIfExist() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        List<DataObject> subObjects = new ArrayList<DataObject>();
        
        for (int i=0; i<5; i++) {
            DataObject o = this.constructDataObject(Volume.class, this.ids.get(i));
            subObjects.add(o);
        }
        Assert.assertTrue(volumes.containsAll(subObjects));
        this.cleanAll();
    }
    
    @Test
    public void shouldAddAll() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        List<Volume> subObjects = new ArrayList<Volume>();
        
        for (int i=0; i<5; i++) {
            Volume o = (Volume) this.constructDataObject(Volume.class);
            subObjects.add(o);
        }
        volumes.addAll(subObjects);
        this.dbClient.createObject(subObjects);
        Assert.assertEquals(this.ids.size()+subObjects.size(), volumes.size());
        this.cleanAll();
    }
    
    @Test
    public void shouldRemoveAll() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        List<URI> subUris = new ArrayList<URI>(this.ids.subList(0, 4));
        this.removeObject(Volume.class, subUris);
        volumes.removeAll(subUris);
        Assert.assertEquals(this.ids.size(), volumes.size());
        this.cleanAll();
    }
    
    @Test
    public void shouldRetainAll() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        List<Volume> retainedVolumes = new ArrayList<Volume>();
        List<URI> retainedIds = new ArrayList<URI>(this.ids.subList(0, 3));
        for (URI id : retainedIds) {
            Volume volume = (Volume) this.constructDataObject(Volume.class, id);
            retainedVolumes.add(volume);
        }
        List<URI> removedIds = new ArrayList<URI>(this.ids.subList(3, this.ids.size()));

        volumes.retainAll(retainedVolumes);
        this.removeObject(Volume.class, removedIds);
        Assert.assertEquals(retainedIds.size(), volumes.size());
        this.cleanAll();
    }
    
    @Test
    public void shouldClearAllElements() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertTrue(volumes.size() > 0);
        volumes.clear();
        Assert.assertTrue(volumes.isEmpty());
        this.cleanAll();
    }
    
    @Test
    public void shouldSetNotTouchDb() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Assert.assertTrue(volumes.size() > 0);
        Volume volumeToSet = (Volume) this.constructDataObject(Volume.class);
        volumes.set(0, volumeToSet);
        Assert.assertNull(volumes.get(0));
        this.cleanAll();
    }
    
    @Test
    public void shouldGetElementAfterSetAndExist() {
        int expectCount = randInt(5, 10);
        this.createDataObject(Volume.class, expectCount);
        List<Volume> volumes = this.dbClient.queryObject(Volume.class, this.ids);
        Volume volumeToSet = (Volume) this.constructDataObject(Volume.class);
        volumes.set(0, volumeToSet);
        this.dbClient.createObject(new DataObject[] {volumeToSet});
        Assert.assertEquals(volumeToSet.getId().toString(), volumes.get(0).getId().toString());
        this.cleanAll();
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
                    Iterator<URI> it = this.ids.iterator();
                    while (it.hasNext()) {
                        if (it.next().equals(id)) {
                            it.remove();
                        }
                    }
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
