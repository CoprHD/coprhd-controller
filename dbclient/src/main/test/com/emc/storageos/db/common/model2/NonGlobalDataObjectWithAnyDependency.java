package com.emc.storageos.db.common.model2;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedRelationIndex;

/**
 * This class tests non-global object, it covers these scenarios:
 * 
 * 1. Object has non-global references
 * 2. Object has global references
 * 
 * output: both cases tests should pass.
 */
@Cf("schema_ut2")
@DbKeyspace(Keyspaces.LOCAL) 
public class NonGlobalDataObjectWithAnyDependency extends DataObject {
    private static final long serialVersionUID = 1L;
    private String fieldUT;
    private String fieldUT2;

    @NamedRelationIndex(cf = "NamedRelationIndex", type = GlobalDependency.class)
    @Name("dependency")
    public String getFieldUT() {
        return fieldUT;
    }

    public void setFieldUT(String fieldUT) {
        this.fieldUT = fieldUT;
    }

    @NamedRelationIndex(cf = "NamedRelationIndex", type = NonGlobalDependency.class)
    @Name("dependency2")
    public String getFieldUT2() {
        return fieldUT2;
    }

    public void setFieldUT2(String fieldUT2) {
        this.fieldUT2 = fieldUT2;
    }

    @Cf("schema_ut2")
    @DbKeyspace(Keyspaces.GLOBAL)
    class GlobalDependency extends DataObject {
        private static final long serialVersionUID = 1L;
    }

    @Cf("schema_ut2")
    @DbKeyspace(Keyspaces.LOCAL)
    class NonGlobalDependency extends DataObject {
        private static final long serialVersionUID = 1L;
    }
}
