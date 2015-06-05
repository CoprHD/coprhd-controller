/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.common;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.TimeSeries;
import com.emc.storageos.db.client.model.TimeSeriesSerializer;
import com.emc.storageos.db.common.schema.DataObjectSchema;
import com.emc.storageos.db.common.schema.DataPointSchema;
import com.emc.storageos.db.common.schema.DbSchema;
import com.emc.storageos.db.common.schema.DbSchemas;
import com.emc.storageos.db.common.schema.TimeSeriesSchema;

public class DbSchemaScanner extends PackageScanner {
    private static final Logger log = LoggerFactory.getLogger(DbSchemaScanner.class);
    
    private DbSchemas schemas = new DbSchemas();
    private List<DbSchema> geoSchemas = new ArrayList<>();
    public DbSchemaScanner(String[] pkgs) {
        super(pkgs);
    }

    public void scan() {
        scan(Cf.class, XmlRootElement.class);
    }

    @Override
    protected void processClass(Class clazz) {
        DbSchema schema = null;
        if (DataObject.class.isAssignableFrom(clazz)) {
            if (_scannerInterceptor != null && _scannerInterceptor.isClassIgnored(clazz.getSimpleName())) {
                log.warn("{} is ignored in schema due to interceptor", clazz.getSimpleName());                
                return;
            }
            schema = new DataObjectSchema(clazz, _scannerInterceptor);
            if (clazz.isAnnotationPresent(DbKeyspace.class)) {
                DbKeyspace anno = (DbKeyspace) clazz.getAnnotation(DbKeyspace.class);
                if (DbKeyspace.Keyspaces.GLOBAL.equals(anno.value())) {
                    geoSchemas.add(schema);
                }
            }

        }else if (TimeSeries.class.isAssignableFrom(clazz)) {
            schema = new TimeSeriesSchema(clazz);
        }else if (TimeSeriesSerializer.DataPoint.class.isAssignableFrom(clazz)) {
            schema = new DataPointSchema(clazz);
        }else {
            return;
        }
        schemas.addSchema(schema);
    }

    public DbSchemas getSchemas() {
        return schemas;
    }

    public List<DbSchema> getGeoSchemas() {
        return geoSchemas;
    }
}
