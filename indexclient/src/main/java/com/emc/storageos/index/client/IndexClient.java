/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.index.client;

import java.util.Iterator;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.index.client.impl.IndexQueryResult;

public interface IndexClient {

    /**
     * import data into index server
     * 
     * @param clazz object type
     * @param objects object list
     * @return total numbers of records imported
     */
    public <T extends DataObject> int importData(Class<T> clazz, Iterator<T> objects);

    /**
     * query from index server
     * 
     * @param clazz object type
     * @param queryString string to query
     * @param pageSize number of records list on a page
     * @param pageNumber current page number
     * @return uris
     */
    public <T extends DataObject> IndexQueryResult query(Class<T> clazz, String queryString, int pageSize, int pageNumber);

    /**
     * Connect to the indexing server
     */
    public void starts();

}
