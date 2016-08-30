/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.index.client.impl;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CollectionParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.index.client.IndexClient;

public class IndexClientImpl implements IndexClient {

    private static final Logger log = LoggerFactory.getLogger(IndexClientImpl.class);

    private CloudSolrClient solrClient;

    @Override
    public <T extends DataObject> int importData(Class<T> clazz, String collectionName, Iterator<T> objects, List<String> fieldNames) {

        int countAll = 0;
        createCollection(collectionName);
        addSchema(collectionName, fieldNames);
        solrClient.setDefaultCollection(collectionName);
        try {
            countAll = importRecords(clazz, objects);
            log.info(countAll + " records added");
            solrClient.commit();
        } catch (Exception e) {
            log.error("Data import failed");
        }
        return countAll;
    }

    @Override
    public List<URI> query(String collectionName, String queryString, int pageSize, int pageNumber) {

        solrClient.setDefaultCollection(collectionName);
        List<URI> uris = new LinkedList<URI>();

        SolrQuery query = new SolrQuery();
        query.setQuery(queryString);
        query.setFields("id");
        query.setStart((pageNumber - 1) * pageSize);
        query.setRows(pageSize);
        query.addSort("id", SolrQuery.ORDER.asc);
        QueryResponse response;
        try {
            response = solrClient.query(query);
            SolrDocumentList results = response.getResults();
            for (SolrDocument solrDocument : results) {
                String id = (String) solrDocument.getFieldValue("id");
                URI uri = new URI(id);
                uris.add(uri);
            }
        } catch (SolrServerException | IOException e) {
            log.error("Query failed");
        } catch (URISyntaxException e) {
            log.error("Generate URI failed");
        }
        return uris;
    }

    @Override
    public void start() {

        String zkHostString = "10.247.101.118:2181,10.247.101.149:2181,10.247.101.177:2181";
        solrClient = new CloudSolrClient.Builder().withZkHost(zkHostString).build();
        solrClient.connect();
        log.info("solrClient start");
    }

    public void createCollection(String collectionName) {

        boolean hasCollection = solrClient.getZkStateReader().getClusterState().hasCollection(collectionName);

        if (hasCollection) {
            log.info("collection has been created");
            return;
        }
        CollectionAdminResponse res = new CollectionAdminResponse();
        log.info("Create collection:{}", collectionName);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("action", CollectionParams.CollectionAction.CREATE.toString());
        params.set("numShards", 2);
        params.set("replicationFactor", 1);
        params.set("maxShardsPerNode", 1);
        params.set("collection.configName", "myconf");
        params.set("name", collectionName);

        SolrRequest request = new QueryRequest(params);
        request.setPath("/admin/collections");

        try {
            res.setResponse(solrClient.request(request));
        } catch (SolrServerException | IOException e) {
            log.error("collection creating failed");
        }
        log.info("collection is created");

    }

    public void addSchema(String collectionName, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            Map<String, Object> fieldAttributes = new LinkedHashMap<>();
            fieldAttributes.put("name", fieldName);
            fieldAttributes.put("type", "string");
            fieldAttributes.put("stored", false);
            fieldAttributes.put("indexed", true);
            SchemaRequest.AddField addFieldRequest = new SchemaRequest.AddField(fieldAttributes);
            try {
                addFieldRequest.process(solrClient, collectionName);
            } catch (SolrServerException | IOException e) {
                log.error("Add schema failed");
            }
        }
        log.info("Schema is added");
    }

    private <T extends DataObject> int importRecords(Class<T> clazz, Iterator<T> objects)
            throws Exception {

        boolean isPrint = true;
        int countAll = 0;
        while (objects.hasNext()) {
            T object = objects.next();
            isPrint = importBeanProperties(clazz, object);
            if (isPrint) {
                countAll++;
            }
        }
        return countAll;
    }

    private <T extends DataObject> boolean importBeanProperties(Class<T> clazz, T object)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, SolrServerException, IOException {

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", object.getId().toString());
        boolean isPrint = true;
        BeanInfo bInfo;
        try {
            bInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException ex) {
            log.error("Unexpected exception getting bean info", ex);
            throw new RuntimeException("Unexpected exception getting bean info", ex);
        }

        PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();
        Object objValue;
        Class type;
        for (PropertyDescriptor pd : pds) {
            if (pd.getName().equals("class") || pd.getName().equals("id")) {
                continue;
            }
            Name nameAnnotation = pd.getReadMethod().getAnnotation(Name.class);
            String objKey;
            if (nameAnnotation != null) {
                objKey = nameAnnotation.value();

                objValue = pd.getReadMethod().invoke(object);
                if (objValue == null) {
                    continue;
                }

                if (isEmptyStr(objValue)) {
                    continue;
                }
                doc.addField(objKey, objValue.toString());
            }

        }

        solrClient.add(doc);
        return isPrint;
    }

    private boolean isEmptyStr(Object objValue) {
        if (!(objValue instanceof String)) {
            return false;
        }
        return StringUtils.isEmpty((String) objValue);
    }
}
