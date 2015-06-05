/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;
import com.emc.sa.model.util.SortedIndexUtils;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.google.common.collect.Lists;

public class CatalogServiceFieldFinder extends ModelFinder<CatalogServiceField> {
    
    public CatalogServiceFieldFinder(DBClientWrapper client) {
        super(CatalogServiceField.class, client);
    }

    public List<CatalogServiceField> findByCatalogService(URI catalogServiceId) {
        
        List<CatalogServiceField> results = Lists.newArrayList();
        
        List<NamedElement> catalogServiceIds = client.findBy(CatalogServiceField.class, CatalogServiceField.CATALOG_SERVICE_ID, catalogServiceId);
        if (catalogServiceIds != null) {
            results.addAll(findByIds(toURIs(catalogServiceIds)));
        }        

        SortedIndexUtils.sort(results);
        
        return results;
    }
}
