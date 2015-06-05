/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.*;

@Cf("CatalogCategory")
public class CatalogCategory extends ModelObjectWithACLs implements Cloneable, SortedIndexDataObject, TenantDataObject {  
    
    public static final String NO_PARENT = "urn:storageos:CatalogCategory:NONE:";
    public static final String DELETED_CATEGORY = "urn:storageos:CatalogCategory:DELETED:";
    public static final String DELETED_CATEGORY_NAME = "DELETED";
    
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String IMAGE = "image";
    public static final String CATALOG_CATEGORY_ID = "catalogCategoryId";
    public static final String SORTED_INDEX = "sortedIndex";
    public static final String TENANT = TenantDataObject.TENANT_COLUMN_NAME;
    public static final String VERSION = "version";

	private String title;
	
	private String description;    

	private String image;
	
	private NamedURI catalogCategoryId;
	
	private Integer sortedIndex;
	
	private String tenant;

    private String version;
	
	public CatalogCategory() {
	}

    @Name(TITLE)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        setChanged(TITLE);
    }

    @Name(DESCRIPTION)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged(DESCRIPTION);
    }

    @Name(IMAGE)
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
        setChanged(IMAGE);
    }
    
    @NamedRelationIndex(cf = "NamedRelationIndex", type = CatalogCategory.class)
    @Name(CATALOG_CATEGORY_ID)
    public NamedURI getCatalogCategoryId() {
        return catalogCategoryId;
    }

    public void setCatalogCategoryId(NamedURI catalogCategoryId) {
        this.catalogCategoryId = catalogCategoryId;
        setChanged(CATALOG_CATEGORY_ID);
    }  
    
    @Name(SORTED_INDEX)
    public Integer getSortedIndex() {
        return sortedIndex;
    }

    public void setSortedIndex(Integer sortedIndex) {
        this.sortedIndex = sortedIndex;
        setChanged(SORTED_INDEX);
    }

    @AlternateId("TenantToCatalogCategory")
    @Name(TENANT)
    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
        setChanged(TENANT);
    }    
    
    @Override
	public String toString() {
		return getLabel();
	}

    @Name(VERSION)
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        setChanged(VERSION);
    }

    public static boolean isRoot(CatalogCategory catalogCategory) {
        if (catalogCategory != null && catalogCategory.getCatalogCategoryId() != null && catalogCategory.getCatalogCategoryId().getURI() != null) {
            return NO_PARENT.equals(catalogCategory.getCatalogCategoryId().getURI().toString());
        }
        return false;
    }
    
    @Override
    public Object[] auditParameters() {
        return new Object[] {getLabel(), 
                getCatalogCategoryId(), getTenant(), getId() };
    }    
    
}
