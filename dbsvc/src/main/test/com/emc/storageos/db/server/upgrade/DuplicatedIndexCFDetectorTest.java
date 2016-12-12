/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedRelationIndex;
import com.emc.storageos.db.client.model.PermissionsIndex;
import com.emc.storageos.db.client.model.PrefixIndex;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.ScopedLabelIndex;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector.DuplciatedIndexDataObject;
import com.emc.storageos.db.client.upgrade.DuplicatedIndexCFDetector.IndexCFKey;
import com.emc.storageos.db.common.schema.DataObjectSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.common.schema.FieldInfo;

public class DuplicatedIndexCFDetectorTest {
	private DuplicatedIndexCFDetector detector;
	private DbSchemas dbSchemas;

	@Before
	public void setUp() throws Exception {
		detector = new DuplicatedIndexCFDetector();
		dbSchemas = new DbSchemas();
	}

	@Test
	public void testFindDuplicatedIndexCFNames() {
		DataObjectSchema schema = new DataObjectSchema(DummyDataObject.class);
		dbSchemas.addSchema(schema);
		dbSchemas.addSchema(new DataObjectSchema(VirtualDataCenter.class));
		
		List<DuplciatedIndexDataObject> result = detector.findDuplicatedIndexCFNames(dbSchemas);
		
		assertEquals(1, result.size());
		
		DuplciatedIndexDataObject duplciatedIndexDataObject = result.get(0);
		assertEquals("com.emc.storageos.db.server.upgrade.DuplicatedIndexCFDetectorTest.DummyDataObject",
				duplciatedIndexDataObject.getClassName());
		int indexCount = 6;
		IndexCFKey[] indexCFKeys = duplciatedIndexDataObject.getIndexFieldsMap().keySet().toArray(new IndexCFKey[0]);
		assertEquals(indexCount, indexCFKeys.length);
		
		IndexCFKey indexCFKey = indexCFKeys[--indexCount];
		List<FieldInfo> fields = duplciatedIndexDataObject.getIndexFieldsMap().get(indexCFKey);
		
		assertEquals("LabelPrefixIndex", indexCFKey.getCf());
		assertEquals("com.emc.storageos.db.client.model.PrefixIndex", indexCFKey.getIndex());
		assertEquals("java.lang.String", indexCFKey.getFieldTypeClass());
		assertEquals(2, fields.size());
		assertEquals("duplicatedLabel", fields.get(0).getName());
		assertEquals("label", fields.get(1).getName());
		
		indexCFKey = indexCFKeys[--indexCount];
        fields = duplciatedIndexDataObject.getIndexFieldsMap().get(indexCFKey);
        assertEquals("ScopedTagPrefixIndex", indexCFKey.getCf());
        assertEquals("com.emc.storageos.db.client.model.ScopedLabelIndex", indexCFKey.getIndex());
        assertEquals("com.emc.storageos.db.client.model.ScopedLabelSet", indexCFKey.getFieldTypeClass());
        assertEquals(2, fields.size());
        assertEquals("scopedLabelField", fields.get(0).getName());
        assertEquals("tags", fields.get(1).getName());
		
		indexCFKey = indexCFKeys[--indexCount];
		fields = duplciatedIndexDataObject.getIndexFieldsMap().get(indexCFKey);
		assertEquals("AlternateIdCF", indexCFKey.getCf());
		assertEquals("com.emc.storageos.db.client.model.AlternateId", indexCFKey.getIndex());
		assertEquals("java.lang.String", indexCFKey.getFieldTypeClass());
		assertEquals(2, fields.size());
		assertEquals("alternateIdField1", fields.get(0).getName());
		assertEquals("alternateIdField2", fields.get(1).getName());
		
		indexCFKey = indexCFKeys[--indexCount];
        fields = duplciatedIndexDataObject.getIndexFieldsMap().get(indexCFKey);
        assertEquals("RelationIndexCF", indexCFKey.getCf());
        assertEquals("com.emc.storageos.db.client.model.RelationIndex", indexCFKey.getIndex());
        assertEquals("int", indexCFKey.getFieldTypeClass());
        assertEquals(2, fields.size());
        assertEquals("relationIndexField1", fields.get(0).getName());
        assertEquals("relationIndexField2", fields.get(1).getName());
		
		indexCFKey = indexCFKeys[--indexCount];
        fields = duplciatedIndexDataObject.getIndexFieldsMap().get(indexCFKey);
        assertEquals("PermissionsIndexCF", indexCFKey.getCf());
        assertEquals("com.emc.storageos.db.client.model.PermissionsIndex", indexCFKey.getIndex());
        assertEquals("long", indexCFKey.getFieldTypeClass());
        assertEquals(3, fields.size());
        assertEquals("permissionsIndexField1", fields.get(0).getName());
        assertEquals("permissionsIndexField2", fields.get(1).getName());
        assertEquals("permissionsIndexField3", fields.get(2).getName());
        
        indexCFKey = indexCFKeys[--indexCount];
        fields = duplciatedIndexDataObject.getIndexFieldsMap().get(indexCFKey);
        assertEquals("NamedRelationCF", indexCFKey.getCf());
        assertEquals("com.emc.storageos.db.client.model.NamedRelationIndex", indexCFKey.getIndex());
        assertEquals("java.net.URI", indexCFKey.getFieldTypeClass());
        assertEquals(2, fields.size());
        assertEquals("namedRelation1", fields.get(0).getName());
        assertEquals("namedRelation2", fields.get(1).getName());
	}
	
	public static class DummyDataObject extends DataObject {
        private static final long serialVersionUID = -4768337187913899539L;
        private String alternateIdField1 = "";
		private String alternateIdField2 = "";
		private String alternateIdField3 = "";
		private String duplicatedLabel = "";
		private long permissionsIndexField1 = 10;
		private long permissionsIndexField2 = 20;
		private long permissionsIndexField3 = 30;
		private int relationIndexField1 = 100;
		private int relationIndexField2 = 100;
		private URI namedRelation1 = URI.create("uri1");
		private URI namedRelation2 = URI.create("uri2");
		private ScopedLabelSet scopedLabelField;
		
		@ScopedLabelIndex(cf = "ScopedTagPrefixIndex")
		public ScopedLabelSet getScopedLabelField() {
            return scopedLabelField;
        }

        public void setScopedLabelField(ScopedLabelSet scopedLabelField) {
            this.scopedLabelField = scopedLabelField;
        }

        @NamedRelationIndex(cf = "NamedRelationCF", type = Project.class)
		public URI getNamedRelation1() {
            return namedRelation1;
        }

        public void setNamedRelation1(URI namedRelation1) {
            this.namedRelation1 = namedRelation1;
        }

        @NamedRelationIndex(cf = "NamedRelationCF", type = Project.class)
        public URI getNamedRelation2() {
            return namedRelation2;
        }

        public void setNamedRelation2(URI namedRelation2) {
            this.namedRelation2 = namedRelation2;
        }

        @RelationIndex(cf = "RelationIndexCF", type = Volume.class)
        public int getRelationIndexField1() {
            return relationIndexField1;
        }

        public void setRelationIndexField1(int relationIndexField1) {
            this.relationIndexField1 = relationIndexField1;
        }

        @RelationIndex(cf = "RelationIndexCF", type = Volume.class)
        public int getRelationIndexField2() {
            return relationIndexField2;
        }

        public void setRelationIndexField2(int relationIndexField2) {
            this.relationIndexField2 = relationIndexField2;
        }

        @PermissionsIndex("PermissionsIndexCF")
	    public long getPermissionsIndexField1() {
            return permissionsIndexField1;
        }

        public void setPermissionsIndexField1(long permissionsIndexField1) {
            this.permissionsIndexField1 = permissionsIndexField1;
        }

        @PermissionsIndex("PermissionsIndexCF")
        public long getPermissionsIndexField2() {
            return permissionsIndexField2;
        }

        public void setPermissionsIndexField2(long permissionsIndexField2) {
            this.permissionsIndexField2 = permissionsIndexField2;
        }
        
        @PermissionsIndex("PermissionsIndexCF")
        public long getPermissionsIndexField3() {
            return permissionsIndexField3;
        }

        public void setPermissionsIndexField3(long permissionsIndexField3) {
            this.permissionsIndexField3 = permissionsIndexField3;
        }


        @PrefixIndex(cf = "LabelPrefixIndex")
		public String getDuplicatedLabel() {
			return duplicatedLabel;
		}

		public void setDuplicatedLabel(String duplicatedLabel) {
			this.duplicatedLabel = duplicatedLabel;
		}
		
		@AlternateId("@AltIdIndex")
	    public String getAlternateIdField3() {
			return alternateIdField3;
		}

		public void setAlternateIdField3(String alternateIdField3) {
			this.alternateIdField3 = alternateIdField3;
		}

		@AlternateId("AlternateIdCF")
		public String getAlternateIdField1() {
			return alternateIdField1;
		}

		public void setAlternateIdField1(String alternateIdField1) {
			this.alternateIdField1 = alternateIdField1;
		}

		@AlternateId("AlternateIdCF")
		public String getAlternateIdField2() {
			return alternateIdField2;
		}

		public void setAlternateIdField2(String alternateIdField2) {
			this.alternateIdField2 = alternateIdField2;
		}
	}
}
