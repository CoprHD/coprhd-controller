/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;

import org.apache.commons.lang.ObjectUtils;

import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.CatalogCategoryCommonParam;
import com.emc.vipr.model.catalog.CatalogCategoryCreateParam;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.google.common.base.Function;

public class CatalogCategoryMapper implements Function<CatalogCategory,CatalogCategoryRestRep> {
    
    public static final CatalogCategoryMapper instance = new CatalogCategoryMapper();
    
    public static CatalogCategoryMapper getInstance() {
        return instance;
    }
    
    private CatalogCategoryMapper() {
    }
    
    public CatalogCategoryRestRep apply(CatalogCategory resource) {
        return map(resource);
    }

    public static CatalogCategoryRestRep map(CatalogCategory from) {
        if (from == null) {
            return null;
        }
        CatalogCategoryRestRep to = new CatalogCategoryRestRep();
        mapDataObjectFields(from, to);
        
        if (from.getTenant() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, uri(from.getTenant())));
        }     
        
        if (from.getCatalogCategoryId() != null && isParent(from.getCatalogCategoryId()) == false) {
            to.setCatalogCategory(toRelatedResource(ResourceTypeEnum.CATALOG_CATEGORY, from.getCatalogCategoryId().getURI()));
        }

        to.setTitle(from.getTitle());
        to.setDescription(from.getDescription());
        to.setImage(from.getImage());
        to.setSortedIndex(from.getSortedIndex());
        to.setVersion(from.getVersion());

        return to;
    }
    
    private static boolean isParent(NamedURI namedUri) {
        return CatalogCategory.NO_PARENT.equalsIgnoreCase(namedUri.getURI().toString());
    }
    
    public static CatalogCategory createNewCatalogCategory(CatalogCategory parentCatalogCategory, CatalogCategoryCreateParam param) {
        CatalogCategory newCatalogCategory = new CatalogCategory();
        newCatalogCategory.setId(URIUtil.createId(CatalogCategory.class));
        newCatalogCategory.setTenant(param.getTenantId());
        
        updateCatalogCategoryObject(parentCatalogCategory, newCatalogCategory, param);
        
        return newCatalogCategory;
    }
    
    public static void updateCatalogCategoryObject(CatalogCategory parentCatalogCategory, CatalogCategory catalogCategory, CatalogCategoryCommonParam param) {
        if (param.getName() != null) {
            catalogCategory.setLabel(param.getName());
        }
        if (param.getTitle() != null) {
            catalogCategory.setTitle(param.getTitle());
        }
        if (param.getDescription() != null) {
            catalogCategory.setDescription(param.getDescription());
        }
        if (param.getImage() != null) {
            catalogCategory.setImage(param.getImage());
        }
        if (param.getCatalogCategoryId() != null) {
            URI oldParentId = getEffectiveParentId(catalogCategory);
            URI newParentId = NullColumnValueGetter.normalize(param.getCatalogCategoryId());

            if (NullColumnValueGetter.isNullURI(param.getCatalogCategoryId())) {
                catalogCategory.setCatalogCategoryId(new NamedURI(NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullStr()));
            }
            else {
                catalogCategory.setCatalogCategoryId(new NamedURI(parentCatalogCategory.getId(), parentCatalogCategory.getLabel()));
            }
            
            // Reset the order index if the service is moved to a different category
            if (ObjectUtils.notEqual(oldParentId, newParentId)) {
                catalogCategory.setSortedIndex(null);
            }
        }
    }
    
    private static URI getEffectiveParentId(CatalogCategory category) {
        NamedURI id = category.getCatalogCategoryId();
        return NullColumnValueGetter.isNullNamedURI(id) ? null : id.getURI();
    }
}
