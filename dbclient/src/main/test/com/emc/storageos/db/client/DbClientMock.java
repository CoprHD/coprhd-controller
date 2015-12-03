package com.emc.storageos.db.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.impl.BulkDataObjQueryResultIterator;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.QueryObjectResultList;
import com.emc.storageos.db.client.model.DataObject;

public class DbClientMock extends DbClientImpl {

    private Map<Class<? extends DataObject>, Set<DataObject>> objsHolder = new HashMap<Class<? extends DataObject>, Set<DataObject>>();
    
    @Override
    public <T extends DataObject> void createObject(T object) {
            if (objsHolder.get(object.getClass()) == null) {
                objsHolder.put(object.getClass(), new HashSet<DataObject>());
            }
            objsHolder.get(object.getClass()).add(object);
    }
    
    @Override
    public <T extends DataObject> void createObject(Collection<T> dataobjects) { 
        for (T object : dataobjects) {
            this.createObject(object);
        }
    }
    
    private void removeObject(DataObject object) {
        if (this.objsHolder.get(object.getClass()) != null ) {
            Set<DataObject> objects = this.objsHolder.get(object.getClass());
            Iterator<DataObject> it = objects.iterator();
            while (it.hasNext()) {
                if (it.next().getId().equals(object.getId())) {
                    it.remove();
                }
            }
        }
    }
    
    @Override
    public void removeObject(DataObject... objects) {
        for (DataObject o : objects) {
            removeObject(o);
        }
    }
    
    public void removeAll() {
        this.objsHolder.clear();
    }
    
    @Override
    protected <T extends DataObject> List<T> internalQueryObject(Class<T> clazz, Collection<URI> ids, boolean activeOnly){
        List<T> objects = new ArrayList<T>();
        for(URI id : ids) {
            T object = queryObject(clazz, id);
            objects.add(object);
        }
        return objects;
    }
    
    @Override
    public <T extends DataObject> Iterator<T> queryIterativeObjects(final Class<T> clazz,
            Collection<URI> ids, final boolean activeOnly) {
        Set<DataObject> objs = this.objsHolder.get(clazz);
        Set<URI> objIds = new HashSet<URI>();
        for (DataObject o : objs) {
            objIds.add(o.getId());
        }
        BulkDataObjQueryResultIterator<T> bulkQueryIterator = new
                BulkDataObjQueryResultIterator<T>(objIds.iterator()) {

                    @Override
                    protected void run() {
                        currentIt = null;
                        getNextBatch();
                        while (!nextBatch.isEmpty()) {
                            List<T> currBatchResults = internalQueryObject(clazz, nextBatch, activeOnly);
                            if (!currBatchResults.isEmpty()) {
                                currentIt = currBatchResults.iterator();
                                break;
                            }

                            getNextBatch();
                        }
                    }
                };
        return bulkQueryIterator;
    }
    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, URI id) {
        if (this.objsHolder.get(clazz) == null) {
            return null;
        }
        for (DataObject object : this.objsHolder.get(clazz)) {
            if (object.getId().equals(id)) {
                return (T) object;
            }
        }
        return null;
    }
    
    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids) {
        return new QueryObjectResultList<T>(this, clazz, ids, false);
    }
}
