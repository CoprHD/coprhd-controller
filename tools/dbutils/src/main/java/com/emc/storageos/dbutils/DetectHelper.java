/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.dbutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.AggregateDbIndex;
import com.emc.storageos.db.client.impl.AltIdDbIndex;
import com.emc.storageos.db.client.impl.DbIndex;
import com.emc.storageos.db.client.impl.DecommissionedDbIndex;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.NamedRelationDbIndex;
import com.emc.storageos.db.client.impl.PermissionsDbIndex;
import com.emc.storageos.db.client.impl.PrefixDbIndex;
import com.emc.storageos.db.client.impl.RelationDbIndex;
import com.emc.storageos.db.client.impl.ScopedLabelDbIndex;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.ColumnFamily;

public class DetectHelper {
    private static final Logger log = LoggerFactory.getLogger(DetectHelper.class);

    public static ObjectEntry extractObjectEntryFromIndex(String indexKey,
            IndexColumnName name, Class<? extends DbIndex> type) {
        // The className of a data object CF in a index record
        String className;
        // The id of the data object record in a index record
        String objectId;
        if (type.equals(AltIdDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(RelationDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(NamedRelationDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(DecommissionedDbIndex.class)) {
            objectId = name.getTwo();
            className = indexKey;
        } else if (type.equals(PermissionsDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(PrefixDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(ScopedLabelDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(AggregateDbIndex.class)) {
            objectId = name.getTwo();
            int firstColon = indexKey.indexOf(':');
            className = firstColon == -1 ? indexKey : indexKey.substring(0, firstColon);
        } else {
            String msg = String.format("Unsupported index type %s.", type);
            log.warn(msg);
            System.out.println(msg);
            return null;
        }
        return new ObjectEntry(className, objectId);
    }

    public static class ObjectEntry {
        private String className;
        private String objectId;

        public ObjectEntry(String className, String objectId) {
            this.className = className;
            this.objectId = objectId;
        }

        public String getClassName() {
            return className;
        }

        public String getObjectId() {
            return objectId;
        }

        @Override
        public String toString() {
            StringBuffer buffer = new StringBuffer();
            buffer.append("ObjectEntry ClassName: ").append(className).append(" ObjectId: ").append(objectId);
            return buffer.toString();
        }
    }

    public static class IndexEntry {
        private String indexKey;
        private IndexColumnName columnName;

        public IndexEntry(String indexKey, IndexColumnName columnName) {
            this.indexKey = indexKey;
            this.columnName = columnName;
        }

        public String getIndexKey() {
            return indexKey;
        }

        public IndexColumnName getColumnName() {
            return columnName;
        }

    }

    /*
     * This class records the Index Data's ColumnFamily and
     * the related DbIndex type and it belongs to which Keyspace.
     */
    public static class IndexAndCf {
        public ColumnFamily<String, IndexColumnName> cf;
        public Class<? extends DbIndex> indexType;
        public Keyspace keyspace;

        public IndexAndCf(Class<? extends DbIndex> indexType,
                ColumnFamily<String, IndexColumnName> cf) {
            this.indexType = indexType;
            this.cf = cf;
        }

        public IndexAndCf(Class<? extends DbIndex> indexType,
                ColumnFamily<String, IndexColumnName> cf, Keyspace keyspace) {
            this.indexType = indexType;
            this.cf = cf;
            this.keyspace = keyspace;
        }

        @Override
        public String toString() {
            return generateKey(indexType, cf, keyspace);
        }

        public static String generateKey(Class<? extends DbIndex> indexType,
                ColumnFamily<String, IndexColumnName> cf, Keyspace keyspace) {
            StringBuffer buffer = new StringBuffer();
            buffer.append(keyspace.getKeyspaceName()).append("/")
                    .append(indexType.getSimpleName()).append("/")
                    .append(cf.getName());
            return buffer.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof IndexAndCf)) {
                return false;
            }
            if (this == obj) {
                return true;
            }
            IndexAndCf that = (IndexAndCf) obj;
            if (cf != null ? !cf.equals(that.cf) : that.cf != null) {
                return false;
            }
            if (indexType != null ? !indexType.equals(that.indexType)
                    : that.indexType != null) {
                return false;
            }
            if (keyspace != null ? !keyspace.equals(that.keyspace)
                    : that.keyspace != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = cf != null ? cf.hashCode() : 0;
            result = 31 * result + (indexType != null ? indexType.hashCode() : 0);
            result = 31 * result + (keyspace != null ? keyspace.hashCode() : 0);
            return result;
        }
    }

}
