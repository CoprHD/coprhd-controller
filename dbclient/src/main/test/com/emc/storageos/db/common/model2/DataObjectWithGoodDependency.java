/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common.model2;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedRelationIndex;
/**
 * This tests Global DataObject with global reference scenario.
 * Output: Test should pass.
 *
 */
@Cf("schema_ut2")
@DbKeyspace(Keyspaces.GLOBAL) 
class DataObjectWithGoodDependency extends DataObject{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String fieldUT;

    @NamedRelationIndex(cf = "NamedRelationIndex", type = MyValidDependency.class)
    @Name("dependency")
    public String getFieldUT() {
        return fieldUT;
    }

    public void setFieldUT(String fieldUT) {
        this.fieldUT = fieldUT;
    }

    @Cf("schema_ut2")
    @DbKeyspace(Keyspaces.GLOBAL)
    class MyValidDependency extends DataObject {
        private static final long serialVersionUID = 1L;
    }

}
