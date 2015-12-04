package com.emc.storageos.db.client.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;

public class QueryObjectFieldResultList <T extends DataObject>  extends QueryResultList<T> {
    private Collection<String> fieldNames = null;
    
    public QueryObjectFieldResultList(DbClientImpl dbClient, Class<T> clazz, Collection<String> fieldNames, Collection<URI> ids, boolean activeOnly) {
        super(dbClient, clazz, ids, activeOnly);
        this.fieldNames = fieldNames;
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        this.checkRange(fromIndex);
        this.checkRange(toIndex);
        List<URI> subList = ids.subList(fromIndex, toIndex);
        return new QueryObjectFieldResultList<T>(dbClient, clazz, fieldNames, subList, activeOnly);
    }

    @Override
    protected Iterator<T> queryIterativeObjects() {
        return this.dbClient.queryIterativeObjectFields(this.clazz, this.fieldNames, this.ids);
    }

    @Override
    protected T queryObject(URI id) {
        Collection<T> c = this.dbClient.queryObjectFields(clazz, fieldNames, Arrays.asList(id));
        ArrayList<T> l = new ArrayList<T>(c);
        return l.isEmpty()? null : l.get(0);
    }

}
