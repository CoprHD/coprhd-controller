package com.emc.storageos.db.server.impl;

import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.Operator;
import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.IndexExpression;
import org.apache.cassandra.db.Row;
import org.apache.cassandra.db.composites.CBuilder;
import org.apache.cassandra.db.composites.CellName;
import org.apache.cassandra.db.composites.CellNameType;
import org.apache.cassandra.db.composites.Composite;
import org.apache.cassandra.db.filter.ColumnSlice;
import org.apache.cassandra.db.filter.ExtendedFilter;
import org.apache.cassandra.db.filter.QueryFilter;
import org.apache.cassandra.db.filter.SliceQueryFilter;
import org.apache.cassandra.db.index.SecondaryIndexManager;
import org.apache.cassandra.db.index.SecondaryIndexSearcher;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by brian on 6/27/16.
 */
public class MyIndexSearcher extends SecondaryIndexSearcher {
    private static final Logger log = LoggerFactory.getLogger(MyIndexSearcher.class);

    public static final ByteBuffer AFTER = UTF8Type.instance.fromString("search_after_doc");

    private MyDBIndex index;
    private ColumnIdentifier indexedColumnName;
    private Directory searchDir;
    private MyRowMapper mapper;

    public MyIndexSearcher(SecondaryIndexManager indexManager, MyDBIndex index,
                           Directory searchDir, Set<ByteBuffer> columns) {
        super(indexManager, columns);
        this.index = index;
        indexedColumnName = index.getColumnDefinition().name;
        this.searchDir = searchDir;
        mapper = new MyRowMapper(index.getCfMetaData());
    }

    @Override
    public List<Row> search(ExtendedFilter extendedFilter) {
        log.info("lbyo search stack=", new Throwable());
        List<IndexExpression> clause = extendedFilter.getClause();
        int startPos = after(clause);
        IndexExpression indexedExpression = indexedExpression(clause);
        log.info("lbyoo indexedExpression={}", indexedExpression);
        String queryStr = UTF8Type.instance.compose(indexedExpression.value);
        log.info("lbyoo queryStr={}", queryStr);
        String[] tokens = queryStr.split(":");
        log.info("lbyoo tokens={} {}", tokens[0], tokens[1]);

        List<Row> result = new ArrayList();
        try {
            IndexReader reader = DirectoryReader.open(searchDir);
            IndexSearcher searcher = new IndexSearcher(reader);
            Term term =  new Term(tokens[0], tokens[1]);
            Query query = new WildcardQuery(term);

            TopDocs matchedDocs = searcher.search(query, 10);
            log.info("lbyooo matched {}", matchedDocs.totalHits);
            ScoreDoc[] scoreDocs = matchedDocs.scoreDocs;
            for (ScoreDoc scoreDoc : scoreDocs) {
                IndexReader r = searcher.getIndexReader();
                log.info("reader={}", r.toString());
                Document doc = searcher.doc(scoreDoc.doc);
                String v = doc.get(tokens[0]);
                String key = doc.get("partition_key");
                log.info("lbyoo doc={} value={} key={}", scoreDoc.doc, v, key);
                log.info("lbyoo11 doc={}", doc.toString());

                BytesRef ref = doc.getBinaryValue(MyDBIndex.PARTITION_RAW_KEY);
                log.info("lbyoo ref={}", ref);
                ByteBuffer partitionKey = ByteBuffer.wrap(ref.bytes, ref.offset, ref.offset + ref.length);
                IPartitioner partitioner = DatabaseDescriptor.getPartitioner();
                DecoratedKey pkey = partitioner.decorateKey(partitionKey);

                ref = doc.getBinaryValue(MyDBIndex.CLUSTERING_KEY);
                ByteBuffer clusteringKey = ByteBuffer.wrap(ref.bytes, ref.offset, ref.offset+ref.length);
                CellNameType type = index.getCfMetaData().comparator;
                CellName clusteringCellName = type.cellFromByteBuffer(clusteringKey);
                log.info("lbyhg cluster size={}", clusteringCellName.size());
                Composite start = start(clusteringCellName);
                Composite end = start(clusteringCellName).end();
                ColumnSlice[] columnSlices = new ColumnSlice[1];
                columnSlices[0] = new ColumnSlice(start, end);

                SliceQueryFilter dataFilter = new SliceQueryFilter(columnSlices, false, Integer.MAX_VALUE, index.getCfMetaData().clusteringColumns().size());
                QueryFilter queryFilter = new QueryFilter(pkey, baseCfs.name, dataFilter, extendedFilter.timestamp);

                ColumnFamily queryColumnFamily = baseCfs.getColumnFamily(queryFilter);
                log.info("lbygh cf={}", queryColumnFamily);

                Row row = new Row(partitionKey, queryColumnFamily);
                log.info("lbyoo8 add key={}", row.key);
                result.add(row);
            }
        }catch (Exception e ) {
            log.error("lbyeee: e=",e);
        }

        log.info("lbyoo2 result size={}", result.size());
        return result;
        /*
        try {
            RowKey after = after(extendedFilter.getClause());
            long timestamp = extendedFilter.timestamp;
            int limit = extendedFilter.currentLimit();
            DataRange dataRange = extendedFilter.dataRange;
            List<IndexExpression> clause = extendedFilter.getClause();
            List<IndexExpression> filteredExpressions = filteredExpressions(clause);
            Search search = search(clause);
            return rowService.search(search, filteredExpressions, dataRange, limit, timestamp, after);
        } catch (Exception e) {
            throw new IndexException(e, "Error while searching: %s", extendedFilter);
        }
        */

    }

    private Composite start(CellName cellName) {
        CBuilder builder = index.getCfMetaData().comparator.builder();
        for (int i = 0; i < cellName.clusteringSize(); i++) {
            ByteBuffer component = cellName.get(i);
            builder.add(component);
        }
        return builder.build();
    }

    private IndexExpression indexedExpression(List<IndexExpression> clause) {
        for (IndexExpression indexExpression : clause) {
            ByteBuffer columnName = indexExpression.column;
            if (indexedColumnName.bytes.equals(columnName)) {
                return indexExpression;
            }
        }
        return null;
    }

    @Override
    public boolean canHandleIndexClause(List<IndexExpression> clause) {
        log.info("lbyo canHandleIndexClause indexedColumnName={} clause={}", indexedColumnName, clause);
        for (IndexExpression expression : clause) {
            ByteBuffer columnName = expression.column;
            boolean sameName = indexedColumnName.bytes.equals(columnName);
            if (expression.operator.equals(Operator.EQ) && sameName) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IndexExpression highestSelectivityPredicate(List<IndexExpression> clause, boolean trace) {
        log.info("lbyo highestSelectiveityPredicate index={}", index);
        ColumnDefinition cfd = index.getColumnDefinition();
        log.info("lbyo cfd={}", cfd);
        ColumnIdentifier columnIdentifier =  index.getColumnDefinition().name;

        for (IndexExpression expression : clause) {
            ByteBuffer columnName = expression.column;
            log.info("lbyo columnName={}", columnIdentifier);
            boolean sameName = columnName.equals(columnName);
            if (expression.operator.equals(Operator.EQ) && sameName) {
                return expression;
            }
        }

        return null;
    }

    @Override
    public List<Row> postReconciliationProcessing(List<IndexExpression> clause, List<Row> rows) {

        log.info("lbyoo3 postReconcliationProcessing rows.size={} stack=", rows.size(), new Throwable());
        for (Row row :rows) {
            log.info("lbyoo8 key={}", row.key);
        }
        return rows;
    }

    public MyRowMapper mapper() {
        return mapper;
    }

    private int after(List<IndexExpression> expressions) {
        for (IndexExpression indexExpression : expressions) {
            ByteBuffer columnName = indexExpression.column;
            if (AFTER.equals(columnName)) {
                return indexExpression.value.getInt();
            }
        }
        return 0;
    }
}
