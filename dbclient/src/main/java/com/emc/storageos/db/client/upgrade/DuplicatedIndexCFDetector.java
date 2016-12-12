/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.NamedRelationIndex;
import com.emc.storageos.db.client.model.PermissionsIndex;
import com.emc.storageos.db.client.model.PrefixIndex;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.ScopedLabelIndex;
import com.emc.storageos.db.common.schema.AnnotationType;
import com.emc.storageos.db.common.schema.AnnotationValue;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.common.schema.FieldInfo;

public class DuplicatedIndexCFDetector {
    
    public List<DuplciatedIndexDataObject> findDuplicatedIndexCFNames(DbSchemas schemas) {
        List<DuplciatedIndexDataObject> duplciatedIndexDataObjects = new ArrayList<DuplciatedIndexDataObject>();
        for (DbSchema schema : schemas.getSchemas()) {
            DuplciatedIndexDataObject duplciatedIndexDataObject = new DuplciatedIndexDataObject();
            duplciatedIndexDataObject.setClassName(schema.getType());
            duplciatedIndexDataObject.setDataCFName(schema.getName());
            
            Map<IndexCFKey, List<FieldInfo>> indexFieldsMap = new HashMap<IndexCFKey, List<FieldInfo>>();
            for (FieldInfo field : schema.getFields()) {
                for (AnnotationType annotation : field.getAnnotations().getAnnotations()) {
                    if (isDbIndexAnnotation(annotation)) {
                        String indexCF = getDbIndexCFName(annotation);
                        IndexCFKey indexCFKey = new IndexCFKey(annotation.getType(), indexCF, field.getType());
                        
                        if (!indexFieldsMap.containsKey(indexCFKey)) {
                            indexFieldsMap.put(indexCFKey, new ArrayList<FieldInfo>());
                        }
                        indexFieldsMap.get(indexCFKey).add(field);
                    }
                }
            }
            
            for (Entry<IndexCFKey, List<FieldInfo>> entry : indexFieldsMap.entrySet()) {
                if (entry.getValue().size() > 1) {
                    duplciatedIndexDataObject.getIndexFieldsMap().put(entry.getKey(), entry.getValue());
                }
            }
            
            if (duplciatedIndexDataObject.getIndexFieldsMap().size() > 0) {
                duplciatedIndexDataObjects.add(duplciatedIndexDataObject);
            }
        }
        return duplciatedIndexDataObjects;
    }
    
    private String getDbIndexCFName(AnnotationType annotation) {
        if (annotation.getType().equals(AlternateId.class.getName()) ||
                annotation.getType().equals(PermissionsIndex.class.getName())) {
            return annotation.getValueList().get(0).getValue();
            
        } else if (annotation.getType().equals(RelationIndex.class.getName()) ||
                    annotation.getType().equals(NamedRelationIndex.class.getName()) ||
                    annotation.getType().equals(PrefixIndex.class.getName()) ||
                    annotation.getType().equals(ScopedLabelIndex.class.getName())) {
            for (AnnotationValue value : annotation.getValueList()) {
                if (value.getName().toLowerCase().equals("cf")) {
                    return value.getValue();
                }
            }
        }
        
        throw new IllegalStateException();
    }
    
    private boolean isDbIndexAnnotation(AnnotationType annotation) {
        if (annotation.getType().equals(AlternateId.class.getName()) ||
                annotation.getType().equals(PermissionsIndex.class.getName()) ||
                annotation.getType().equals(RelationIndex.class.getName()) ||
                annotation.getType().equals(NamedRelationIndex.class.getName()) ||
                annotation.getType().equals(PrefixIndex.class.getName()) ||
                annotation.getType().equals(ScopedLabelIndex.class.getName())) {
            return true;
        }
        
        return false;
    }
    
    public static class DuplciatedIndexDataObject {
        private String className;
        private String dataCFName;
        private Map<IndexCFKey, List<FieldInfo>> indexFieldsMap = new HashMap<IndexCFKey, List<FieldInfo>>();
        private Map<IndexCFKey, List<PropertyDescriptor>> indexPropertysMap = new HashMap<IndexCFKey, List<PropertyDescriptor>>();

        public Map<IndexCFKey, List<PropertyDescriptor>> getIndexPropertysMap() {
            return indexPropertysMap;
        }

        public void setIndexPropertysMap(Map<IndexCFKey, List<PropertyDescriptor>> indexPropertysMap) {
            this.indexPropertysMap = indexPropertysMap;
        }

        public String getDataCFName() {
            return dataCFName;
        }

        public void setDataCFName(String dataCFName) {
            this.dataCFName = dataCFName;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public Map<IndexCFKey, List<FieldInfo>> getIndexFieldsMap() {
            return indexFieldsMap;
        }

        public void setIndexFieldsMap(Map<IndexCFKey, List<FieldInfo>> indexFieldsMap) {
            this.indexFieldsMap = indexFieldsMap;
        }
    }
    
    public static class IndexCFKey {
        private String index;
        private String cf;
        private String fieldTypeClass;
        
        public IndexCFKey(String index, String cf, String fieldTypeClass) {
            super();
            this.index = index;
            this.cf = cf;
            this.fieldTypeClass = fieldTypeClass;
        }

        public String getIndex() {
            return index;
        }

        public String getCf() {
            return cf;
        }

        public String getFieldTypeClass() {
            return fieldTypeClass;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((cf == null) ? 0 : cf.hashCode());
            result = prime * result + ((fieldTypeClass == null) ? 0 : fieldTypeClass.hashCode());
            result = prime * result + ((index == null) ? 0 : index.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            IndexCFKey other = (IndexCFKey) obj;
            if (cf == null) {
                if (other.cf != null)
                    return false;
            } else if (!cf.equals(other.cf))
                return false;
            if (fieldTypeClass == null) {
                if (other.fieldTypeClass != null)
                    return false;
            } else if (!fieldTypeClass.equals(other.fieldTypeClass))
                return false;
            if (index == null) {
                if (other.index != null)
                    return false;
            } else if (!index.equals(other.index))
                return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("IndexCFKey [index=");
            builder.append(index);
            builder.append(", cf=");
            builder.append(cf);
            builder.append(", fieldTypeClass=");
            builder.append(fieldTypeClass);
            builder.append("]");
            return builder.toString();
        }
    }
}
