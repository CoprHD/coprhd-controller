package com.emc.storageos.db.client.impl;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import com.emc.storageos.db.client.model.DataObject;

public class QueryObjectResultList <T extends DataObject> extends QueryResultList<T>{

    public QueryObjectResultList(DbClientImpl dbClient, Class<T> clazz, Collection<URI> ids, boolean activeOnly) {
        super(dbClient, clazz, ids, activeOnly);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        this.checkRange(fromIndex);
        this.checkRange(toIndex);
        List<URI> subIdList = ids.subList(fromIndex, toIndex);
        return new QueryObjectResultList<T>(this.dbClient, this.clazz, subIdList, this.activeOnly);
    }

    @Override
    protected Iterator<T> queryIterativeObjects() {
        return this.dbClient.queryIterativeObjects(this.clazz, this.ids, this.activeOnly);
    }

    @Override
    protected T queryObject(URI id) {
        return this.dbClient.queryObject(this.clazz, id);
    }
}
