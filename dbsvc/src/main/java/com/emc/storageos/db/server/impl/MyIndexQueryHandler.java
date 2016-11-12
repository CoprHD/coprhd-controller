package com.emc.storageos.db.server.impl;

import com.datastax.driver.core.exceptions.UnavailableException;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.cql3.BatchQueryOptions;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.Operation;
import org.apache.cassandra.cql3.QueryHandler;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.ResultSet;
import org.apache.cassandra.cql3.Term;
import org.apache.cassandra.cql3.UpdateParameters;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.cql3.statements.ModificationStatement;
import org.apache.cassandra.cql3.statements.ParsedStatement;
import org.apache.cassandra.cql3.statements.Restriction;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.cql3.statements.Selection;
import org.apache.cassandra.cql3.statements.UpdateStatement;
import org.apache.cassandra.db.ArrayBackedSortedColumns;
import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.IndexExpression;
import org.apache.cassandra.db.Row;
import org.apache.cassandra.db.RowPosition;
import org.apache.cassandra.db.composites.CellName;
import org.apache.cassandra.db.composites.Composite;
import org.apache.cassandra.db.filter.IDiskAtomFilter;
import org.apache.cassandra.dht.AbstractBounds;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.serializers.UTF8Serializer;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.MyStorageProxy;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.service.pager.PagingState;
import org.apache.cassandra.thrift.CqlResult;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.utils.MD5Digest;
import org.apache.cassandra.utils.Pair;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.request.schema.AnalyzerDefinition;
import org.apache.solr.client.solrj.request.schema.FieldTypeDefinition;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.params.CollectionParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by brian on 6/14/16.
 */
public class MyIndexQueryHandler implements QueryHandler {
    private static final Logger log = LoggerFactory.getLogger(MyIndexQueryHandler.class);

    CloudSolrClient solrClient;

    private static final QueryProcessor cqlProcessor = QueryProcessor.instance;

    public MyIndexQueryHandler() {
        solrClient = new CloudSolrClient.Builder().withZkHost("localhost:2181").build();
    }

     @Override
    public ResultMessage.Prepared prepare(String query, QueryState state) throws RequestValidationException {
         log.info("lbyd prepare {}", query);

         return cqlProcessor.prepare(query, state);
    }

    @Override
    public ParsedStatement.Prepared getPrepared(MD5Digest id) {
        log.info("lbyd getPrepared");
        return cqlProcessor.getPrepared(id);
    }

    @Override
    public ParsedStatement.Prepared getPreparedForThrift(Integer id) {
        log.info("lbyd getPreparedForThrift");
        return cqlProcessor.getPreparedForThrift(id);
    }

    @Override
    public ResultMessage processPrepared(CQLStatement statement, QueryState state, QueryOptions options)
    throws RequestExecutionException, RequestValidationException {
        log.info("lbyd processPrepared statement={}", statement);
        return cqlProcessor.processPrepared(statement, state, options);
    }

    private static ByteBuffer getValue(Operation operation, UpdateParameters parameters)
            throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
        Field field = Operation.class.getDeclaredField("t");
        field.setAccessible(true);
        Term term = (Term)field.get(operation);

        Method method = Term.class.getDeclaredMethod("bindAndGet", QueryOptions.class);
        return (ByteBuffer)method.invoke(term, parameters.options);
    }

    @Override
    public ResultMessage processBatch(BatchStatement statement, QueryState state, BatchQueryOptions options)
    throws RequestExecutionException, RequestValidationException {
        log.info("lbyw processBatch class path={}", System.getProperty("java.class.path"));

        ResultMessage resultMessage =  cqlProcessor.processBatch(statement, state, options);

        try {
            /*
            CollectionAdminRequest.List list = new CollectionAdminRequest.List();
            CollectionAdminResponse res = new CollectionAdminResponse();
            NamedList<Object> response = solrClient.request(list);
            res.setResponse(response);
            List<String> collections = (List<String>) res.getResponse().get("collections");
            log.info("lbyw7 collections={}", collections);
            */
            List<String> indexedFieldNames = getIndexedFields(VirtualDataCenter.class);
            log.info("lbyw6: indexedFields={}", indexedFieldNames);

            while (true) {
                try {
                    solrClient.connect();
                    break;
                }catch (Exception e) {
                    log.info("lbytt0 waiting for Solr to come up");
                    Thread.currentThread().sleep(5000);
                }
            }

            ZkStateReader zkReader = solrClient.getZkStateReader();
            String collectionName="VirtualDataCenter1";
            boolean hasCollection = zkReader.getClusterState().hasCollection(collectionName);
            log.info("lbyw7 has {} collection={}", collectionName, hasCollection);
            if (!hasCollection) {
                createCollection(collectionName, indexedFieldNames);
            }
            solrClient.setDefaultCollection(collectionName);

            List<ModificationStatement> statements = statement.getStatements();
            boolean dump = false;
            int count = 0;

            SolrInputDocument doc = new SolrInputDocument() ;
            for (int i = 0; i < statements.size(); i++) {
                ModificationStatement s = statements.get(i);
                if (!updateIndex(s)) {
                    continue;
                }

                QueryOptions queryOptions = options.forStatement(i);
                List<ByteBuffer> keys = s.buildPartitionKeyNames(queryOptions);
                Composite clusteringPrefix = s.createClusteringPrefix(queryOptions);
                UpdateParameters params = s.makeUpdateParameters(keys, clusteringPrefix, queryOptions, false, options.getTimestamp(state));
                List<Operation> operations = s.getOperations();
                String value = UTF8Serializer.instance.deserialize(keys.get(0));
                log.info("lby10 keys.size={} key={}", keys.size(), value);

                SolrInputField idField = doc.getField("id");

                if (idField == null) {
                    log.info("lby11 add id field");
                    doc.addField("id", value);
                }

                for (Operation op : operations) {
                    ColumnFamily cf = ArrayBackedSortedColumns.factory.create(s.cfm);
                    CellName cname = cf.getComparator().create(clusteringPrefix, op.column);
                    String fieldname = UTF8Serializer.instance.deserialize(cname.get(0));
                    log.info("lbyw5 name={}", fieldname);
                    if (indexedFieldNames.contains(fieldname)) {
                        value=UTF8Serializer.instance.deserialize(getValue(op, params));
                        log.info("lbyw5 value={}", value);
                        doc.addField(fieldname, value);
                    }
                }
                count++;
                dump = true;
            }

            if (dump == true) {
                log.info("lbyuu6 count={} stack=", count, new Throwable());
                putDoc(doc);
                readDoc();
            }
        }catch (Exception e) {
            log.error("lbyw5 e=", e);
        }

        //return cqlProcessor.processBatch(statement, state, options);
        return resultMessage;
    }

    private void putDoc(SolrInputDocument doc) throws SolrServerException, IOException {
        UpdateRequest req = new UpdateRequest();
        req.setAction(UpdateRequest.ACTION.COMMIT, true, false);
        req.setParam("overwrite", "true");
        req.add(doc);
        UpdateResponse rsp = req.process(solrClient);
    }

    private void readDoc() throws SolrServerException, IOException {
        log.info("lbyw8 readDoc");
        SolrQuery query = new SolrQuery();
        query.setQuery("*:*");
        QueryResponse rsp = solrClient.query(query);
        SolrDocumentList docs = rsp.getResults();
        Iterator<SolrDocument> iter = docs.iterator();
        log.info("lbyw8 has next={}", iter.hasNext());
        while (iter.hasNext()) {
            SolrDocument doc = iter.next();
            String shortId = (String)doc.getFieldValue("shortId");
            String label = (String)doc.getFieldValue("label");
            log.info("lbyw8 shortId={} label={}", shortId, label);
        }
    }

    private void createCollection(String collectionName, List<String> fieldNames)
            throws SolrServerException, IOException {
        log.info("lbyww To create collection:{}", collectionName);

        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("action", CollectionParams.CollectionAction.CREATE.toString());
        params.set("numShards", 1);
        params.set("replicationFactor", 1);
        params.set("maxShardsPerNode", 1);
        params.set("collection.configName", "myconf");
        //params.set("createNodeSet")
        params.set("name", collectionName);

        SolrRequest request = new QueryRequest(params);
        request.setPath("/admin/collections");

        CollectionAdminResponse res = new CollectionAdminResponse();
        res.setResponse(solrClient.request(request));
        log.info("lbyw7 collection is created");

        for (String fieldName : fieldNames) {
            addField(collectionName, fieldName);
        }
    }

    private void addField(String collectionName, String fieldName) throws SolrServerException, IOException {
        log.info("lbyw10 add field {}", fieldName);
        /*
        FieldTypeDefinition fieldTypeDefinition = new FieldTypeDefinition();
        Map<String, Object> fieldTypeAttributes = new LinkedHashMap<>();
        fieldTypeAttributes.put("name", fieldName);
        fieldTypeAttributes.put("class", "solr.TextField");
        fieldTypeDefinition.setAttributes(fieldTypeAttributes);

        AnalyzerDefinition analyzerDefinition = new AnalyzerDefinition();
        Map<String, Object> charFilterAttributes = new LinkedHashMap<>();
        charFilterAttributes.put("class", "solr.PatternReplaceCharFilterFactory");
        charFilterAttributes.put("replacement", "$1$1");
        charFilterAttributes.put("pattern", "([a-zA-Z])\\\\1+");
        analyzerDefinition.setCharFilters(Collections.singletonList(charFilterAttributes));
        Map<String, Object> tokenizerAttributes = new LinkedHashMap<>();
        tokenizerAttributes.put("class", "solr.WhitespaceTokenizerFactory");
        analyzerDefinition.setTokenizer(tokenizerAttributes);
        Map<String, Object> filterAttributes = new LinkedHashMap<>();
        filterAttributes.put("class", "solr.WordDelimiterFilterFactory");
        filterAttributes.put("preserveOriginal", "0");
        analyzerDefinition.setFilters(Collections.singletonList(filterAttributes));
        fieldTypeDefinition.setAnalyzer(analyzerDefinition);

        SchemaRequest.AddFieldType addFieldTypeRequest =
                new SchemaRequest.AddFieldType(fieldTypeDefinition);
        SchemaResponse.UpdateResponse addFieldTypeResponse = addFieldTypeRequest.process(solrClient, collectionName);
        log.info("lbyw10 rsp={}",addFieldTypeResponse);
        */
        Map<String, Object> fieldAttributes = new LinkedHashMap<>();
        fieldAttributes.put("name", fieldName);
        fieldAttributes.put("type", "string");
        fieldAttributes.put("stored", true);
        SchemaRequest.AddField addFieldRequest = new SchemaRequest.AddField(fieldAttributes);
        addFieldRequest.process(solrClient, collectionName);
    }

    private boolean updateIndex(ModificationStatement statement) {
        String cfName = statement.columnFamily();

        // we are only interested in updating "VirtualDataCenter" CF for now
        if (!(statement instanceof UpdateStatement) || !cfName.equals("VirtualDataCenter")) {
            return false;
        }

        return true;
    }

    private <T extends DataObject> List<String> getIndexedFields(Class<T> clazz) {
        List<String> indexedFieldNames = new ArrayList();

        if (!clazz.equals(VirtualDataCenter.class)) {
            return indexedFieldNames;
        }

        DataObjectType doType = TypeMap.getDoType(clazz);
        Collection<ColumnField> fields = doType.getColumnFields();
        for (ColumnField field : fields) {
            PropertyDescriptor descriptor = field.getPropertyDescriptor();
            Class<?> type = descriptor.getPropertyType();
            if ( String.class == type) {
                indexedFieldNames.add(field.getName());
            }
        }

        return indexedFieldNames;
    }


    private static Restriction[] getColumnRestrictions(SelectStatement statement) {
        try {
            Field field = SelectStatement.class.getDeclaredField("columnRestrictions");
            field.setAccessible(true);
            Restriction[] restrictions = (Restriction[]) field.get(statement);

            return restrictions;
        }catch (Exception e) {
            log.error("lbyff0 e=",e);
        }

        return null;
    }

    @Override
    public ResultMessage process(String query, QueryState state, QueryOptions options)
    throws RequestExecutionException, RequestValidationException {
        log.info("lbyttt query={}", query);

        ParsedStatement.Prepared p = QueryProcessor.getStatement(query, state.getClientState());
        options.prepare(p.boundNames);
        CQLStatement prepared = p.statement;
        if (prepared.getBoundTerms() != options.getValues().size()) {
            throw new InvalidRequestException("Invalid amount of bind variables");
        }

        if (!state.getClientState().isInternal) {
            QueryProcessor.metrics.regularStatementsExecuted.inc();
        }

        log.info("lbyttt prepared={}", prepared);
        boolean dump = false;
        if (prepared instanceof SelectStatement) {
            SelectStatement select = (SelectStatement) prepared;
            Selection selection = select.getSelection();
            ResultSet.Metadata metadata = selection.getResultMetadata();
            if (select.columnFamily().equals("VirtualDataCenter")) {
                dump = true;
                log.info("lbyz4: metadata={}", metadata);
                List<ColumnDefinition> columns = selection.getColumns();
                if (columns.size() == 1) {
                    log.info("lbydd10 column={}", columns.get(0).name.toString());
                    Restriction[] restrictions = getColumnRestrictions(select);
                    log.info("lbydd5 restrictions={} length={}", restrictions, restrictions.length);
                    if (restrictions != null) {
                        for (Restriction restriction : restrictions) {
                            if (restriction != null) {
                                log.info("lbydd9 isEQ={} isMultiColumn={}", restriction.isEQ(), restriction.isMultiColumn());
                                List<ByteBuffer> value = restriction.values(options);
                                log.info("lbydd9 value.size={} v={}", value.size(), value.get(0));
                                ByteBuffer buffer = value.get(0);
                                log.info("lbydd9 v={}", UTF8Serializer.instance.deserialize(buffer));
                            }
                        }
                    }
                }
            }

            Map<String,String> toSearch = queryFromSolr(select, options);
            if (!toSearch.isEmpty()) {
                /*
                Restriction[] restrictions = getColumnRestrictions(select);
                log.info("lbydd11 restrictions={} length={}", restrictions, restrictions.length);
                for (Restriction restriction : restrictions) {
                    if (restriction != null) {
                        log.info("lbydd11 isEQ={} isMultiColumn={}", restriction.isEQ(), restriction.isMultiColumn());
                        List<ByteBuffer> value = restriction.values(options);
                        log.info("lbydd11 value.size={} v={}", value.size(), value.get(0));
                        ByteBuffer buffer = value.get(0);
                        log.info("lbydd11 v={}", UTF8Serializer.instance.deserialize(buffer));
                    }
                }
                */

                StringBuilder queryStr = new StringBuilder();
                boolean isFirst = true;
                for (Map.Entry<String, String> entry : toSearch.entrySet()) {
                    if (!isFirst) {
                        queryStr.append(" OR ");
                    }else {
                        isFirst = false;
                    }

                    queryStr.append(entry.getKey())
                            .append(":")
                            .append(entry.getValue());
                }

                String qStr = queryStr.toString();
                log.info("lbyddd0 queryStr={}", qStr);
                SolrQuery q = new SolrQuery(qStr);

                List<ByteBuffer> row = new ArrayList();
                try {
                    QueryResponse resp = solrClient.query(q);
                    SolrDocumentList result = resp.getResults();
                    Iterator<SolrDocument> it = result.iterator();
                    log.info("lbyddd000");
                    while (it.hasNext()) {
                        SolrDocument doc = it.next();

                        Collection<String> names = doc.getFieldNames();
                        for (String name : names) {
                            String value = (String)doc.getFieldValue(name);
                            log.info("lbyddd00 name={} value={}", name, value);
                            if (name.equals("id")) {
                                ByteBuffer buf = UTF8Serializer.instance.serialize(value);
                                row.add(buf);
                                break;
                            }
                        }
                    }
                    log.info("lbyddd001");
                }catch (SolrServerException | IOException e) {
                    throw new RuntimeException(e);
                }

                /*
                ByteBuffer buf = UTF8Serializer.instance.serialize("urn:storageos:VirtualDataCenter:bbd40f3f-4449-4b4f-963c-0bc44d6ad99c:vdc1");
                row.add(buf);
                */
                List<List<ByteBuffer>> rows = new ArrayList();
                rows.add(row);
                ResultSet resultSet = new ResultSet(metadata, rows);
                ResultMessage.Rows msg = new ResultMessage.Rows(resultSet);
                log.info("lbyz11 return my resultmessage");
                return msg;
            }

        /*
            log.info("lbyuu selection={} selected columns={}", select, select.getSelection().getColumns());
            List<IndexExpression> expressions = select.getValidatedIndexExpressions(options);
            ColumnFamilyStore cfs = Keyspace.open(select.keyspace()).getColumnFamilyStore(select.columnFamily());
            SecondaryIndexManager secondaryIndexManager = cfs.indexManager;
            SecondaryIndexSearcher searcher = secondaryIndexManager.getHighestSelectivityIndexSearcher(expressions);
            if (searcher instanceof MyIndexSearcher) {
                try {
                    ResultMessage msg = process((MyIndexSearcher) searcher, expressions, select, state, options);
                    return msg;
                } catch (RequestExecutionException | RequestValidationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        */
        }

        ResultMessage result = cqlProcessor.processStatement(prepared, state, options);
        if (dump) {
            log.info("lbyz0: result={}", result);
        }
        return result;
    }

    // key=fieldname
    // value=search value
    private Map<String,String> queryFromSolr(SelectStatement select, QueryOptions options) throws InvalidRequestException {
        Map<String, String> toSearch = new HashMap();

        if (!select.columnFamily().equals("VirtualDataCenter")) {
            return toSearch;
        }

        Selection selection = select.getSelection();
        List<ColumnDefinition> columns = selection.getColumns();
        if (columns.size() != 1) {
            return toSearch;
        }

        String selectedColumnName = columns.get(0).name.toString();
        log.info("lbydd10 column={}", selectedColumnName);

        if (!selectedColumnName.equals("key")) {
            return toSearch;
        }

        Restriction[] restrictions = getColumnRestrictions(select);
        if (restrictions == null) {
            return toSearch;
        }

        for (Restriction restriction : restrictions) {
            if (restriction != null) {
                log.info("lbyddd1 isEQ={} isMultiColumn={}", restriction.isEQ(), restriction.isMultiColumn());
                List<ByteBuffer> value = restriction.values(options);
                log.info("lbyddd11 value.size={} v={}", value.size(), value.get(0));
                ByteBuffer buffer = value.get(0);
                String v = UTF8Serializer.instance.deserialize(buffer);
                log.info("lbyddd11 v={}", v);
                String[] subStrs = v.split(",");
                log.info("lbyddd2 subStrs.length={}", subStrs.length);
                for (String str : subStrs) {
                    log.info("lbyddd3 str={}", str);
                    String[] subStrs2 = str.split(":");
                    if (subStrs2.length == 2) {
                        toSearch.put(subStrs2[0], subStrs2[1]);
                    }
                }
            }
        }

        return toSearch;
    }

    private IDiskAtomFilter makeFilter(SelectStatement statement, QueryOptions options, int limit)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Method method = SelectStatement.class.getDeclaredMethod("makeFilter", QueryOptions.class, int.class);
        method.setAccessible(true);
        return (IDiskAtomFilter) method.invoke(statement, options, limit);
    }

    private ResultMessage process(MyIndexSearcher searcher,
                                  List<IndexExpression> expressions,
                                  SelectStatement statement,
                                  QueryState state,
                                  QueryOptions options) throws Exception {

        log.info("lbyg1 statement={} options={}", statement, options);

        ClientState clientState = state.getClientState();
        statement.checkAccess(clientState);
        statement.validate(clientState);

        int limit = statement.getLimit(options);
        int page = options.getPageSize();
        int startPos = 0;
        //boolean isCount = isCount(statement);
        boolean isCount = false;

        String ks = statement.keyspace();
        String cf = statement.columnFamily();
        long now = System.currentTimeMillis();

        ConsistencyLevel cl = options.getConsistency();
        log.info("lbymm1 cl={}", cl);
        if (cl == null) {
            throw new InvalidRequestException("Invalid empty consistency level");
        }
        cl.validateForRead(ks);

        IDiskAtomFilter filter = makeFilter(statement, options, limit);
        AbstractBounds<RowPosition> range = statement.getKeyBounds(options);

        MyRowMapper mapper = searcher.mapper();
        PagingState pagingState = options.getPagingState();
        MyRowKeys rowKeys = null;

        log.info("lbygg2 ks={} cf={} cl={}", ks, cf, cl);
        log.info("pagingState={}", pagingState);

        if (pagingState != null) {
            limit = pagingState.remaining;
            ByteBuffer bb = pagingState.partitionKey;
            /*
            if (!MyByteBufferUtils.isEmpty(bb)) {
                rowKeys = mapper.rowKeys(bb);
            }
            */
            int pageNumber = bb.getInt();
            startPos = (pageNumber-1)*page;
        }

        //int rowsPerCommand = page > 0 ? page : limit;
        int rowsPerCommand = page > 0 ? page: limit;
        List<Row> rows = new ArrayList<>();
        int remaining;
        int collectedRows;

        do {
            Pair<List<Row>, MyRowKeys> results = MyStorageProxy.getRangeSlice(searcher,
                    ks,
                    cf,
                    now,
                    filter,
                    range,
                    expressions,
                    rowsPerCommand,
                    startPos,
                    cl,
                    rowKeys,
                    isCount);
            collectedRows = results.left.size();
            rows.addAll(results.left);
            rowKeys = results.right;
            remaining = limit - rows.size();

        } while (isCount && remaining > 0 && collectedRows == rowsPerCommand);

        Iterator<Row> rs = rows.iterator();
        while (rs.hasNext()) {
            Row r = rs.next();
            log.info("lbygg2 r.cf={}", r.cf);
        }

        ResultMessage.Rows msg = statement.processResults(rows, options, limit, now);
        if (!isCount && remaining > 0 && rows.size() == rowsPerCommand) {
            //ByteBuffer bb = mapper.byteBuffer(rowKeys);
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.putInt(startPos+1); // move to next page
            pagingState = new PagingState(bb, null, remaining);
            msg.result.metadata.setHasMorePages(pagingState);
        }
        return msg;
    }
}
