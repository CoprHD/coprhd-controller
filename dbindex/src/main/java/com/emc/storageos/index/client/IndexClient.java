package com.emc.storageos.index.client;

import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.model.DataObject;

public interface IndexClient {

    public <T extends DataObject> void DataImport(Class<T> clazz, String CollectionName, Iterator<T> objects, List<String> fieldNames);

    public List Query(String CollectionName, String q, int row, int pageNow);

    public void StartSolr();

    public void createCollection(String collectionName);

    public void addSchema(String collectionName, List<String> fieldNames);

}
