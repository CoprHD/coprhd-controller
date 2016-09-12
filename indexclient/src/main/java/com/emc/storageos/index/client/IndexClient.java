/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.index.client;

import java.io.IOException;

import com.emc.storageos.db.client.model.DataObject;

public interface IndexClient {

    /**
     * import data into index server
     * 
     * @param clazz object type
     * @param object object
     * @return true if import successfully
     */
    public <T extends DataObject> boolean importRecords(Class<T> clazz, T object) throws Exception;

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
     * Disconnect from the indexing server
     */
    public void stop() throws IOException;
}
