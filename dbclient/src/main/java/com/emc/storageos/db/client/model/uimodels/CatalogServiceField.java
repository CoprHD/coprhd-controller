/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.*;

@Cf("CatalogServiceField")
public class CatalogServiceField extends ModelObject implements SortedIndexDataObject {

    public static final String VALUE = "value";
    public static final String OVERRIDE = "override";
    public static final String CATALOG_SERVICE_ID = "catalogServiceId";
    public static final String SORTED_INDEX = "sortedIndex";
    
    private String value;
    
    private Boolean override = true;
    
    private NamedURI catalogServiceId;
    
    private Integer sortedIndex;

    @Name(VALUE)
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        setChanged(VALUE);
    }

    @Name(OVERRIDE)
    public Boolean getOverride() {
        return override;
    }

    public void setOverride(Boolean override) {
        this.override = override;
        setChanged(OVERRIDE);
    }

    @NamedRelationIndex(cf = "NamedRelationIndex", type = CatalogService.class)
    @Name(CATALOG_SERVICE_ID)
    public NamedURI getCatalogServiceId() {
        return catalogServiceId;
    }

    public void setCatalogServiceId(NamedURI catalogServiceId) {
        this.catalogServiceId = catalogServiceId;
        setChanged(CATALOG_SERVICE_ID);
    }
    
    @Name(SORTED_INDEX)
    public Integer getSortedIndex() {
        return sortedIndex;
    }

    public void setSortedIndex(Integer sortedIndex) {
        this.sortedIndex = sortedIndex;
        setChanged(SORTED_INDEX);
    }    
    
    @Override
    public Object[] auditParameters() {
        return new Object[] {getLabel(), getId() };
    }    
    
}
