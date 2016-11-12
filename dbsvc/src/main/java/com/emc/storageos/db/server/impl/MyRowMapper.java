package com.emc.storageos.db.server.impl;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.Cell;
import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.Row;
import org.apache.cassandra.db.composites.CBuilder;
import org.apache.cassandra.db.composites.CellName;
import org.apache.cassandra.db.composites.CellNameType;
import org.apache.cassandra.db.composites.Composite;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.CompositeType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by brian on 7/5/16.
 */
public class MyRowMapper {
    private CompositeType type;
    private CFMetaData metaData;
    private AbstractType partitionKeyType;
    private CellNameType cellNameType;

    public MyRowMapper(CFMetaData metaData) {
        this.metaData = metaData;

        cellNameType = metaData.comparator;
    }

    public MyRowKey rowKey(ByteBuffer bb) {
        ByteBuffer[] bbs = MyByteBufferUtils.split(bb, type);
        DecoratedKey partitionKey = DatabaseDescriptor.getPartitioner().decorateKey(bbs[0]);
        CellNameType cellNameType = metaData.comparator;
        partitionKeyType = metaData.getKeyValidator();
        CellName clusteringKey = cellNameType.cellFromByteBuffer(bbs[1]);
        type = CompositeType.getInstance(partitionKeyType, cellNameType.asAbstractType());
        return new MyRowKey(partitionKey, clusteringKey);
    }

    public MyRowKey rowKey(Row row) {
        DecoratedKey partitionKey = row.key;
        CellName clusteringKey = clusteringKey(row.cf);
        return new MyRowKey(partitionKey, clusteringKey);
    }

    public MyRowKeys rowKeys(ByteBuffer bb) {
        MyRowKeys rowKeys = new MyRowKeys();
        bb.rewind();
        while (bb.hasRemaining()) {
            int size = bb.getInt();
            byte[] bytes = new byte[size];
            bb.get(bytes);
            MyRowKey rowKey = rowKey(ByteBuffer.wrap(bytes));
            rowKeys.add(rowKey);
        }
        bb.rewind();
        return rowKeys;
    }

    public ByteBuffer byteBuffer(MyRowKeys rowKeys) {

        List<byte[]> allBytes = new ArrayList<>(rowKeys.size());
        int size = 0;
        for (MyRowKey rowKey : rowKeys) {
            byte[] bytes = MyByteBufferUtils.asArray(byteBuffer(rowKey));
            allBytes.add(bytes);
            size += bytes.length + 4;
        }

        ByteBuffer bb = ByteBuffer.allocate(size);
        for (byte[] bytes : allBytes) {
            bb.putInt(bytes.length);
            bb.put(bytes);
        }
        bb.rewind();
        return bb;
    }

    public CellName clusteringKey(ColumnFamily columnFamily) {
        Iterator<Cell> iterator = columnFamily.iterator();
        return iterator.hasNext() ? clusteringKey(iterator.next().name()) : null;
    }

    /**
     * Returns the clustering key contained in the specified {@link ByteBuffer}.
     *
     * @param bb A {@link ByteBuffer}.
     * @return The clustering key contained in the specified {@link ByteBuffer}.
     */
    public CellName clusteringKey(ByteBuffer bb) {
        return cellNameType.cellFromByteBuffer(bb);
    }

    private CellName clusteringKey(CellName cellName) {
        return cellNameType.cellFromByteBuffer(cellName.toByteBuffer());
    }

    public ByteBuffer byteBuffer(MyRowKey rowKey) {
        DecoratedKey partitionKey = rowKey.getPartitionKey();
        CellName clusteringKey = rowKey.getClusteringKey();
        return type.builder().add(partitionKey.getKey()).add(clusteringKey.toByteBuffer()).build();
    }
}
