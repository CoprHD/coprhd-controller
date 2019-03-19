/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "dbschemas")
public class DbSchemasRestRep {

    private DataObjectSchemaRestRep data_object_schema;

    public DataObjectSchemaRestRep getData_object_schema() {
        return data_object_schema;
    }

    public void setData_object_schema(DataObjectSchemaRestRep data_object_schema) {
        this.data_object_schema = data_object_schema;
    }

}
