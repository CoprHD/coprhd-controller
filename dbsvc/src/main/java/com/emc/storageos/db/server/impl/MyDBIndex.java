package com.emc.storageos.db.server.impl;

import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.db.ArrayBackedSortedColumns;
import org.apache.cassandra.db.Cell;
import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.Directories;
import org.apache.cassandra.db.composites.CBuilder;
import org.apache.cassandra.db.composites.CellName;
import org.apache.cassandra.db.composites.CellNameType;
import org.apache.cassandra.db.composites.Composite;
import org.apache.cassandra.db.index.PerRowSecondaryIndex;
import org.apache.cassandra.db.index.SecondaryIndexSearcher;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.CompositeType;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.serializers.LongSerializer;
import org.apache.cassandra.serializers.UTF8Serializer;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.concurrent.OpOrder;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Created by brian on 6/13/16.
 */
public class MyDBIndex extends PerRowSecondaryIndex {
    private static final Logger _log = LoggerFactory.getLogger(MyDBIndex.class);

    public static final String INDEXES_DIR_NAME = "mylucene";
    public static final String FULL_KEY="full_key";
    public static final String PARTITION_KEY="partition_key";
    public static final String PARTITION_RAW_KEY="partition_key";
    public static final String CLUSTERING_KEY="clustering_key";

    // Setup CQL query handler
    static {
        try {
            Field field = ClientState.class.getDeclaredField("cqlQueryHandler");
            field.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            field.set(null, new MyIndexQueryHandler());
        } catch (Exception e) {
            logger.error("Unable to set Lucene CQL query handler", e);
        }
    }

    @Override
    public String getIndexName() {
        return "MyVirtualDataCenterIndex";
    }

    public ColumnDefinition getColumnDefinition() {
        return columnDefinition;
    }

    private ColumnDefinition columnDefinition;
    private String indexName;
    private String name;
    private boolean isExcluded;
    private IndexWriter writer;
    private CFMetaData cfMetaData;

    public CFMetaData getCfMetaData() {
        return cfMetaData;
    }

    @Override
    public void init() {
        logger.info("lbyc Initializing My Lucene index");
        columnDefinition = columnDefs.iterator().next();
        String ksName = columnDefinition.ksName;
        String cfName = columnDefinition.cfName;
        cfMetaData = Schema.instance.getCFMetaData(ksName, cfName);

        Directories directories = new Directories(cfMetaData);
        String basePath = directories.getDirectoryForNewSSTables().getAbsolutePath();
        Path indexPath = Paths.get(basePath + File.separator + INDEXES_DIR_NAME);
        String indexName = String.format("%s.%s.%s", ksName, cfName, columnDefinition.getIndexName());
        _log.info("lbyk0 path={} index name={}", indexPath, indexName);
        try {
            FSDirectory fsDirectory = FSDirectory.open(indexPath);
            IndexWriterConfig config = new IndexWriterConfig(new WhitespaceAnalyzer());
            writer = new IndexWriter(fsDirectory, config);
            /*
            // Load column family info
            columnDefinition = columnDefs.iterator().next();
            indexName = columnDefinition.getIndexName();
            indexConfig = newIndexConfig();
            name = indexConfig.getName();
            service = RowService.build(baseCfs, indexConfig);
            logger.info("Initialized index {}", name);
            isExcluded = false;
            */
        } catch (Exception e) {
            logger.error("lby Error initializing Lucene index " + name, e);
        }
    }

        /*
    private IndexConfig newIndexConfig() {
        ColumnDefinition cfDef = columnDefs.iterator().next();
        String ksName = cfDef.ksName;
        String cfName = cfDef.cfName;
        CFMetaData metadata = Schema.instance.getCFMetaData(ksName, cfName);
        return new IndexConfig(metadata, cfDef);
    }
        */

    @Override
    public void index(ByteBuffer key, ColumnFamily columnFamily) {
        BytesRef bref = bytesRef(key);
        _log.info("lbyh00 key={} {}", bref, cfMetaData.comparator);
        _log.info("lbyccc stack=", new Throwable());

        String partitionKey = UTF8Serializer.instance.deserialize(key);
        _log.info("lbycc index is called key={} clustering size={}", partitionKey, cfMetaData.clusteringColumns().size());

        for (Cell cell : columnFamily) {
            CellName cellName = cell.name();
            _log.info("lbyh0 cellName={}", cellName);
            ByteBuffer[] buffers = new ByteBuffer[cellName.clusteringSize()];
            for (int i = 0; i < cellName.clusteringSize(); i++) {
                ByteBuffer bb = cellName.get(i);
                buffers[i] = bb;
                bref = bytesRef(bb);
                logger.info("lbyh1 bb={}", bref);
            }
            ByteBuffer b = cellName.toByteBuffer();
        }
        _log.info("lbyh0 done");

        long timestamp = System.currentTimeMillis();


        IPartitioner partitioner = DatabaseDescriptor.getPartitioner();
        DecoratedKey partitionkey = partitioner.decorateKey(key);


        // documents(doc, partitionkey, columnFamily, timestamp);
        CFMetaData metaData = columnFamily.metadata();
        CellNameType type = metaData.comparator;
        for (Cell cell : columnFamily) {
            String v = null;
            ByteBuffer cellValue = cell.value();
            String fieldName = type.subtype(0).getString(cell.name().get(0));
            CellName cellName = cell.name();
            try {
                PropertyDescriptor[] descriptors = Introspector.getBeanInfo(VirtualDataCenter.class).getPropertyDescriptors();
                for (PropertyDescriptor descriptor : descriptors) {
                    if (descriptor.getName().equals(fieldName)) {
                        v = getValue(descriptor, cellValue);
                        _log.info("lbymm2 name={} v={}", fieldName, v);
                        if (v != null) {
                            Document doc = new Document();
                            StringField field = new StringField(PARTITION_KEY, partitionKey, org.apache.lucene.document.Field.Store.YES);
                            doc.add(field);

                            BytesRef bytesRef = bytesRef(key);
                            _log.info("lbygh ref={}", bytesRef);
                            StringField keyField = new StringField(PARTITION_RAW_KEY, bytesRef, org.apache.lucene.document.Field.Store.YES);
                            doc.add(keyField);

                            bytesRef = bytesRef(cellName.toByteBuffer());
                            StringField clusteringKeyField = new StringField(CLUSTERING_KEY, bytesRef, org.apache.lucene.document.Field.Store.YES);
                            doc.add(clusteringKeyField);

                            Term term = new Term(partitionKey, fieldName);
                            IndexableField f = new StringField(fieldName, v, org.apache.lucene.document.Field.Store.YES);
                            if (fieldName.equals("label")) {
                                _log.info("lbypp add {} {} to doc", fieldName, v);
                            }
                            doc.add(f);
                            writer.updateDocument(term, doc);
                        }
                        break;
                    }
                }
                writer.commit();
            }catch (Exception e ) {
                _log.error("lbye e=",e);
            }
            _log.info("lbymm2 done");
            /*
            if (cellValue != null) {
                v = UTF8Serializer.instance.deserialize(cellValue);
            }
            */


            /*
            //ColumnDefinition columnDefinition = cfMetaData.getColumnDefinition(cellName);
            ColumnDefinition columnDefinition = metaData.getColumnDefinition(cellName);
            _log.info("lbymm1 column definition={}", columnDefinition);
            if (columnDefinition == null) {
                continue;
            }

            AbstractType<?> valueType = columnDefinition.type;
            */

            /*
            Column col = Column.fromDecomposed(name, cellValue, valueType, false);
            String name = col.getFullName();
            Object value = col.getComposedValue();
            Term term = term(partitionKey, clusteringKey);
            try {
                writer.updateDocument(term, mdocument);
            }catch (IOException e) {
                _log.error("lbyxxx: e=",e);
            }
            */
        }

            /*
            if (!isExcluded) {
                logger.debug("Indexing row in Lucene index {}", name);
                try {
                    long timestamp = System.currentTimeMillis();
                    service.index(key, columnFamily, timestamp);
                } catch (Exception e) {
                    logger.error("Error indexing row in Lucene index " + name, e);
                }
            } else {
                logger.debug("Ignoring excluded indexing in Lucene index {}", name);
            }
            */
    }

    public static byte[] asArray(ByteBuffer byteBuffer) {
        ByteBuffer bb = ByteBufferUtil.clone(byteBuffer);
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        return bytes;
    }

    public static BytesRef bytesRef(ByteBuffer bb) {
        byte[] bytes = asArray(bb);
        return new BytesRef(bytes);
    }

    private String getValue(PropertyDescriptor descriptor, ByteBuffer cellValue) {
        Class type = descriptor.getPropertyType();
        String value = null;

        if (type == String.class || type == URI.class || type == NamedURI.class || type == ScopedLabel.class || type.isEnum()) {
            return UTF8Serializer.instance.deserialize(cellValue);
        }

        if (type == Calendar.class) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(LongSerializer.instance.deserialize(cellValue));
            return cal.toString();
        }

        _log.info("lbye ignore field {} of type {}", descriptor.getName(), descriptor.getPropertyType());
        // throw new UnsupportedOperationException();
        return null;
    }

    public void documents(Document document, DecoratedKey partitionKey, ColumnFamily columnFamily, long timestamp) {
        logger.info("lbyccc documents()");

        Map<CellName, ColumnFamily> incomingRows = splitRows(columnFamily);
        logger.info("lbyccc1 size={}", incomingRows.size());
        List<CellName> incompleteRows = new ArrayList<>(incomingRows.size());

        logger.info("lbyccc2");
        // Separate complete and incomplete rows
        for (Map.Entry<CellName, ColumnFamily> entry : incomingRows.entrySet()) {
            logger.info("lbyccc3");
            CellName clusteringKey = entry.getKey();
            ColumnFamily rowColumnFamily = entry.getValue();

            for (Cell cell : rowColumnFamily) {
                /*
                if (!name.equals("value")) {
                    continue;
                }
                ColumnDefinition columnDefinition = cfMetaData.getColumnDefinition(cellName);
                if (columnDefinition == null) {
                    continue;
                }

                AbstractType<?> valueType = columnDefinition.type;

                ByteBuffer cellValue = cell.value();
                Column col = Column.fromDecomposed(name, cellValue, valueType, false);
                String name = col.getFullName();
                Object value = col.getComposedValue();
                Term term = term(partitionKey, clusteringKey);
                try {
                    writer.updateDocument(term, mdocument);
                }catch (IOException e) {
                    _log.error("lbyxxx: e=",e);
                }
                */
            }

            logger.info("lbyfff {} {}", partitionKey, clusteringKey);
        }
    }

    public Term term(DecoratedKey partitionKey, CellName clusteringKey) {
        AbstractType<?> type = cfMetaData.getKeyValidator();
        CompositeType type2 = CompositeType.getInstance(type, cfMetaData.comparator.asAbstractType());
        ByteBuffer bb = type2.builder().add(partitionKey.getKey()).add(clusteringKey.toByteBuffer()).build();

        ByteBuffer bb2 = ByteBufferUtil.clone(bb);
        byte[] bytes = new byte[bb2.remaining()];
        bb.get(bytes);
        BytesRef ref = new BytesRef(bytes);
        return new Term(FULL_KEY, ref);
    }

    /**
     * Splits the specified {@link ColumnFamily} into CQL logic rows grouping the data by clustering key.
     *
     * @param columnFamily A {@link ColumnFamily}.
     * @return A map associating clustering keys with its {@link ColumnFamily}.
     */
    public Map<CellName, ColumnFamily> splitRows(ColumnFamily columnFamily) {
        Map<CellName, ColumnFamily> columnFamilies = new LinkedHashMap<>();
        CFMetaData meta = columnFamily.metadata();
        for (Cell cell : columnFamily) {
            CellName cellName = cell.name();
            CellName clusteringKey = clusteringKey(cellName);
            ColumnFamily rowColumnFamily = columnFamilies.get(clusteringKey);
            if (rowColumnFamily == null) {
                rowColumnFamily = ArrayBackedSortedColumns.factory.create(meta);
                columnFamilies.put(clusteringKey, rowColumnFamily);
            }
            rowColumnFamily.addColumn(cell);

        }
        return columnFamilies;
    }

    private CellName clusteringKey(CellName cellName) {
        CellNameType cellNameType = cfMetaData.comparator;
        _log.info("lbyddd cellNameType={}", cellNameType);
        CBuilder builder = cellNameType.builder();
        for (int i = 0; i < cfMetaData.clusteringColumns().size(); i++) {
            ByteBuffer component = cellName.get(i);
            builder.add(component);
        }
        Composite prefix = builder.build();
        return cellNameType.rowMarker(prefix);
    }

    @Override
    public void delete(DecoratedKey key, OpOrder.Group opGroup) {
        _log.info("lbyc delete is called");
            /*
            if (!isExcluded) {
                logger.debug("Removing row from Lucene index {}", name);
                try {
                    service.delete(key);
                    service = null;
                } catch (Exception e) {
                    logger.error("Error deleting row in Lucene index " + name, e);
                }
            } else {
                logger.debug("Ignoring excluded deletion in Lucene index {}", name);
            }
            */
    }

    /** {@inheritDoc} */
    @Override
    public boolean indexes(CellName cellName) {
        _log.info("lbyc0 indexes is called {}", cellName);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void validateOptions() throws ConfigurationException {
        _log.info("lbyc validateOptions is called");
            /*
            logger.debug("Validating Lucene index options");
            try {
                newIndexConfig();
                logger.debug("Lucene index options are valid");
            } catch (IndexException e) {
                throw new ConfigurationException(e.getMessage(), e);
            }
            */
    }

    /** {@inheritDoc} */
    @Override
    public long estimateResultRows() {
        _log.info("lbyc estimateResultRows is called");
        return 1;
    }

    /** {@inheritDoc} */
    @Override
    public ColumnFamilyStore getIndexCfs() {
        _log.info("lbyc getIndexCfs is called");
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void removeIndex(ByteBuffer columnName) {
        _log.info("lbyc removeIndex is called");
            /*
            logger.info("Removing Lucene index {}", name);
            try {
                removeIndex();
                logger.info("Removed Lucene index {}", name);
            } catch (Exception e) {
                logger.error("Error removing Lucene index " + name, e);
            }
            */
    }

    /** {@inheritDoc} */
    @Override
    public void invalidate() {
        _log.info("lbyc invalidate is called");
            /*
            logger.info("Invalidating Lucene index {}", name);
            try {
                removeIndex();
                logger.info("Invalidated Lucene index {}", name);
            } catch (Exception e) {
                logger.error("Error invalidating Lucene index " + name, e);
            }
            */
    }

    /** {@inheritDoc} */
    @Override
    public void truncateBlocking(long truncatedAt) {
        _log.info("lbyc truncateBlocking is called");
            /*
            logger.info("Truncating Lucene index {}", name);
            try {
                service.truncate();
                logger.info("Truncated Lucene index {}", name);
            } catch (Exception e) {
                logger.error("Error truncating Lucene index " + name, e);
            }
            */
    }

    /** {@inheritDoc} */
    @Override
    public void reload() {
        _log.info("lbyc reload is called");
    }

    /** {@inheritDoc} */
    @Override
    public void forceBlockingFlush() {
        _log.info("lbyc forceBlockingFlush is called");
            /*
            logger.info("Flushing Lucene index {}", name);
            try {
                service.commit();
                logger.info("Flushed Lucene index {}", name);
            } catch (Exception e) {
                logger.error("Error flushing Lucene index " + name, e);
            }
            */
    }

    /** {@inheritDoc} */
    @Override
    protected SecondaryIndexSearcher createSecondaryIndexSearcher(Set<ByteBuffer> columns) {
        _log.info("lbyc createSecondaryIndexSearcher is called");
        return new MyIndexSearcher(baseCfs.indexManager, this, writer.getDirectory(), columns);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        _log.info("lbyc toString is called");
        return "MyIndex";
        // return name;
    }
}

