package com.emc.storageos.index.client.impl;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
    public <T extends DataObject> void DataImport(Class<T> clazz, String CollectionName, Iterator<T> objects, List<String> fieldNames) {

        int countAll = 0;
        StartSolr();
        createCollection(CollectionName);
        addSchema(CollectionName, fieldNames);
        solrClient.setDefaultCollection(CollectionName);
        try {
            countAll = importRecords(clazz, objects);
            System.out.println(countAll + " records added");
            solrClient.commit();
        } catch (Exception e) {
            System.out.println("Data import failed");
        }

    }

    @Override
    public List<String> Query(String CollectionName, String q, int row, int pageNow) {

        StartSolr();
        solrClient.setDefaultCollection(CollectionName);
        List<String> res = new LinkedList<String>();

        SolrQuery query = new SolrQuery();
        query.setQuery(q);
        query.setFields("id");
        query.setStart((pageNow - 1) * row);
        query.setRows(row);
        query.addSort("id", SolrQuery.ORDER.asc);
        QueryResponse response;
        try {
            response = solrClient.query(query);
            SolrDocumentList results = response.getResults();
            System.out.println("Total number is " + results.getNumFound());
            System.out.println(row + " records on page " + pageNow + " are listed");
            for (SolrDocument solrDocument : results) {
                String id = (String) solrDocument.getFieldValue("id");
                res.add(id);
            }
        } catch (SolrServerException | IOException e) {
            System.out.println("Query failed");
        }
        return res;
    }

    @Override
    public void StartSolr() {

        String zkHostString = "10.247.101.118:2181,10.247.101.149:2181,10.247.101.177:2181";
        solrClient = new CloudSolrClient.Builder().withZkHost(zkHostString).build();
        solrClient.connect();
        System.out.println("solrClient start");
    }

    @Override
    public void createCollection(String collectionName) {

        boolean hasCollection = solrClient.getZkStateReader().getClusterState().hasCollection(collectionName);

        if (!hasCollection) {
            CollectionAdminResponse res = new CollectionAdminResponse();
            log.info("Create collection:{}", collectionName);
            ModifiableSolrParams params = new ModifiableSolrParams();
            params.set("action", CollectionParams.CollectionAction.CREATE.toString());
            params.set("numShards", 2);
            params.set("replicationFactor", 1);
            params.set("maxShardsPerNode", 1);
            params.set("collection.configName", "myconf");
            // params.set("createNodeSet")
            params.set("name", collectionName);

            SolrRequest request = new QueryRequest(params);
            request.setPath("/admin/collections");

            try {
                res.setResponse(solrClient.request(request));
            } catch (SolrServerException | IOException e) {
                System.out.println("collection is not created");
            }
            log.info("collection is created");
            System.out.println("collection is created");
        } else {
            System.out.println("Use collection:" + collectionName);
        }
    }

    @Override
    public void addSchema(String collectionName, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            log.info("add field {}", fieldName);
            Map<String, Object> fieldAttributes = new LinkedHashMap<>();
            fieldAttributes.put("name", fieldName);
            fieldAttributes.put("type", "string");
            fieldAttributes.put("stored", false);
            fieldAttributes.put("indexed", true);
            SchemaRequest.AddField addFieldRequest = new SchemaRequest.AddField(fieldAttributes);
            try {
                addFieldRequest.process(solrClient, collectionName);
            } catch (SolrServerException | IOException e) {
                System.out.println("Add schema failed");
            }
        }
        System.out.println("Schema is added");
    }

    public <T extends DataObject> int importRecords(Class<T> clazz, Iterator<T> objects)
            throws Exception {

        System.out.println("Data importing...");
        boolean isPrint = true;
        int countAll = 0;
        while (objects.hasNext()) {
            T object = objects.next();
            isPrint = importBeanProperties(clazz, object);
            if (isPrint) {
                countAll++;
                System.out.println(countAll);
            }
        }
        return countAll;
    }

    public <T extends DataObject> boolean importBeanProperties(Class<T> clazz, T object)
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
            // skip class property
            if (pd.getName().equals("class") || pd.getName().equals("id")) {
                continue;
            }
            Name nameAnnotation = pd.getReadMethod().getAnnotation(Name.class);
            String objKey;
            if (nameAnnotation == null) {
                objKey = pd.getName();
            } else {
                objKey = nameAnnotation.value();
            }

            objValue = pd.getReadMethod().invoke(object);
            if (objValue == null) {
                continue;
            }

            if (isEmptyStr(objValue)) {
                continue;
            }
            /*
             * type = pd.getPropertyType();
             * if (type == URI.class) {
             * doc.addField(objKey, "URI: " + objValue);
             * } else if (type == StringMap.class) {
             * doc.addField(objKey, "StringMap " + objValue);
             * } else if (type == StringSet.class) {
             * doc.addField(objKey, "StringSet " + objValue);
             * } else if (type == OpStatusMap.class) {
             * doc.addField(objKey, "OpStatusMap " + objValue);
             * } else {
             * doc.addField(objKey, objValue);
             * }
             */
            doc.addField(objKey, objValue.toString());

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
