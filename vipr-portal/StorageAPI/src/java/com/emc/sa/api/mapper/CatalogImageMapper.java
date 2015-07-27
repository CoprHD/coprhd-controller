/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;

import com.emc.storageos.db.client.model.uimodels.CatalogImage;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.CatalogImageCommonParam;
import com.emc.vipr.model.catalog.CatalogImageCreateParam;
import com.emc.vipr.model.catalog.CatalogImageRestRep;
import com.google.common.base.Function;

public class CatalogImageMapper implements Function<CatalogImage,CatalogImageRestRep>{
    
    public static final CatalogImageMapper instance = new CatalogImageMapper();
    
    public static CatalogImageMapper getInstance() {
        return instance;
    }
    
    private CatalogImageMapper() {
    }
    
    public CatalogImageRestRep apply(CatalogImage resource) {
        return map(resource);
    }

    public static CatalogImageRestRep map(CatalogImage from) {
        if (from == null) {
            return null;
        }
        CatalogImageRestRep to = new CatalogImageRestRep();
        mapDataObjectFields(from, to);
        
        if (from.getTenant() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, uri(from.getTenant())));
        }  
        
        to.setContentType(from.getContentType());
        to.setData(from.getData());

        return to;
    }        
    
    public static CatalogImage createNewObject(URI tenantId, CatalogImageCreateParam param) {
        CatalogImage newObject = new CatalogImage();
        newObject.setId(URIUtil.createId(CatalogImage.class));
        newObject.setTenant(tenantId.toString());
        
        updateObject(newObject, param);
        
        return newObject;
    }        
    
    public static void updateObject(CatalogImage object, CatalogImageCommonParam param) {
        if (param.getName() != null) {
            object.setLabel(param.getName());
        }
        if (param.getContentType() != null) {
            object.setContentType(param.getContentType());
        }
        if (param.getData() != null) {
            object.setData(param.getData());
        }
    }        
    
}
