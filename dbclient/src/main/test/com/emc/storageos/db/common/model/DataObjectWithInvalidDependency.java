/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common.model;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedRelationIndex;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;

/**
 * This tests Global DataObject with invalid references(non-global).
 * Output: test should fail.
 */
@Cf("schema_ut2")
@DbKeyspace(Keyspaces.GLOBAL)
public class DataObjectWithInvalidDependency extends DataObject {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String fieldUT;

    @NamedRelationIndex(cf = "NamedRelationIndex", type = MyDependency.class)
    @Name("dependency")
    public String getFieldUT() {
        return fieldUT;
    }

    public void setFieldUT(String fieldUT) {
        this.fieldUT = fieldUT;
        setChanged("dependency");
    }

    @Cf("schema_ut2")
    @DbKeyspace(Keyspaces.LOCAL)
    class MyDependency extends DataObject {
        private static final long serialVersionUID = 1L;
    }

}
