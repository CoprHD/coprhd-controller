package com.emc.storageos.db.server.impl;

import com.google.common.base.Objects;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.composites.CellName;

/**
 * Created by brian on 7/4/16.
 */
public class MyRowKey {
    /** The partition key. */
    private final DecoratedKey partitionKey;

    /** The clustering key. */
    private final CellName clusteringKey;

    /**
     * Builds a new row key.
     *
     * @param partitionKey  The partition key.
     * @param clusteringKey The clustering key.
     */
    public MyRowKey(DecoratedKey partitionKey, CellName clusteringKey) {
        this.partitionKey = partitionKey;
        this.clusteringKey = clusteringKey;
    }

    /**
     * Returns the partition key.
     *
     * @return The partition key.
     */
    public DecoratedKey getPartitionKey() {
        return partitionKey;
    }

    /**
     * Returns the clustering key.
     *
     * @return The clustering key.
     */
    public CellName getClusteringKey() {
        return clusteringKey;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("partitionKey", partitionKey)
                .add("clusteringKey", clusteringKey)
                .toString();
    }
}
