/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.index.client;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.model.DataObject;

public interface IndexClient {

    /**
     * import data into solr
     * 
     * @param clazz object type
     * @param collectionName name of collection
     * @param objects object list
     * @param fieldNames field names to index
     * @return total numbers of records imported
     */
    public <T extends DataObject> int importData(Class<T> clazz, String collectionName, Iterator<T> objects, List<String> fieldNames);

    /**
     * query form solr
     * 
     * @param collectionName name of collection
     * @param queryString string to query
     * @param pageSize number of records list on a page
     * @param pageNumber current page number
     * @return uris
     */
    public List<URI> query(String collectionName, String queryString, int pageSize, int pageNumber);

    /**
     * Start solr cluster with zookeeper
     */
    public void start();

}
