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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Encrypt;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.index.client.IndexClient;
import com.emc.storageos.index.client.IndexQueryResult;

public class IndexClientImpl implements IndexClient {

    private static final Logger log = LoggerFactory.getLogger(IndexClientImpl.class);

    private CloudSolrClient solrClient;

    public IndexClientImpl() {
        String zkHostString = "10.247.101.186:2181,10.247.101.178:2181,10.247.101.179:2181,10.247.97.92:2181,10.247.97.93:2181";
        solrClient = new CloudSolrClient.Builder().withZkHost(zkHostString).build();
        solrClient.connect();
        log.info("Index Client starts");
    }

    public <T extends DataObject> void init(Class<T> clazz) {
        String collectionName = clazz.getName();
        createCollection(collectionName);
        List<String> fieldNames = getIndexedFields(clazz);
        addSchema(collectionName, fieldNames);
        solrClient.setDefaultCollection(collectionName);
    }

    @Override
    public <T extends DataObject> boolean importRecords(Class<T> clazz, T object) throws Exception {
        boolean success = true;
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", object.getId().toString());

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

            Encrypt encryptAnnotation = pd.getReadMethod().getAnnotation(Encrypt.class);
            if (encryptAnnotation != null) {
                continue;
            }

            type = pd.getPropertyType();
            if (String.class != type) {
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
            doc.addField(objKey, objValue.toString());
        }
        solrClient.add(doc);
        return success;
    }

    public void commit() {
        try {
            solrClient.commit();
        } catch (SolrServerException e) {
            log.error("Commit failed", e);
        } catch (IOException e) {
            log.error("Commit failed", e);
        }
    }

    @Override
    public <T extends DataObject> IndexQueryResult query(Class<T> clazz, String queryString, int pageSize, int pageNumber) {
        String collectionName = clazz.getName();
        solrClient.setDefaultCollection(collectionName);
        List<URI> uris = new ArrayList<URI>(pageSize);

        SolrQuery query = new SolrQuery();
        query.setQuery(queryString);
        query.setFields("id");
        query.setStart((pageNumber - 1) * pageSize);
        query.setRows(pageSize);
        QueryResponse response;
        IndexQueryResult resultSets = null;

        try {
            response = solrClient.query(query);
            SolrDocumentList results = response.getResults();
            long totalNum = results.getNumFound();
            for (SolrDocument solrDocument : results) {
                String id = (String) solrDocument.getFieldValue("id");
                URI uri = new URI(id);
                uris.add(uri);
            }
            resultSets = new IndexQueryResult(totalNum, uris);
        } catch (SolrServerException | IOException e) {
            log.error("Query failed", e);
        } catch (URISyntaxException e) {
            log.error("Generate URI failed", e);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return resultSets;
    }

    public void createCollection(String collectionName) {
        boolean hasCollection = solrClient.getZkStateReader().getClusterState().hasCollection(collectionName);

        if (hasCollection) {
            log.info("The collection {} has been created", collectionName);
            return;
        }

        CollectionAdminResponse res = new CollectionAdminResponse();
        log.info("Create collection:{}", collectionName);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("action", CollectionParams.CollectionAction.CREATE.toString());
        params.set("numShards", 5);
        params.set("replicationFactor", 1);
        params.set("maxShardsPerNode", 9);
        params.set("collection.configName", "myconf");
        params.set("name", collectionName);

        SolrRequest request = new QueryRequest(params);
        request.setPath("/admin/collections");

        try {
            res.setResponse(solrClient.request(request));
        } catch (SolrServerException | IOException e) {
            log.error("collection creating failed", e);
        }
        log.info("The collection {} has been created", collectionName);
    }

    private <T extends DataObject> List<String> getIndexedFields(Class<T> clazz) {
        List<String> indexedFieldNames = new ArrayList<String>();

        indexedFieldNames.add("id");
        DataObjectType doType = TypeMap.getDoType(clazz);
        Collection<ColumnField> fields = doType.getColumnFields();
        for (ColumnField field : fields) {
            PropertyDescriptor descriptor = field.getPropertyDescriptor();
            Class<?> type = descriptor.getPropertyType();
            if (String.class == type) {
                indexedFieldNames.add(field.getName());
            }
        }
        return indexedFieldNames;
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
                log.error("Add schema failed", e);
            }
        }
        log.info("Schema is added");
    }

    private boolean isEmptyStr(Object objValue) {
        if (!(objValue instanceof String)) {
            return false;
        }
        return StringUtils.isEmpty((String) objValue);
    }

    @Override
    public void stop() throws IOException {
        solrClient.close();
    }

    public void deleteAllData() {
        try {
            solrClient.deleteByQuery("*:*");
            solrClient.commit();
        } catch (SolrServerException e) {
            log.info("Failed to delete data in Solr.", e);
        } catch (IOException e) {
            log.info("Failed to delete data in Solr.", e);
        }
    }
}
