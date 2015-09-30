/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "catalog_category_update")
@XmlType(name = "CatalogCategoryUpdateParam")
public class CatalogCategoryUpdateParam extends CatalogCategoryCommonParam {

}
