/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.vipr.model.catalog.CatalogImageRestRep;

import controllers.catalog.CatalogImages;

public class CatalogImageDataTable extends DataTable {
    public CatalogImageDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("contentType");
        addColumn("size").setRenderFunction("renderSize");
        addColumn("preview").setRenderFunction("renderPreview");
        sortAllExcept("preview");
        setDefaultSort("createdDate", "desc");
        setRowCallback("createRowLink");
    }

    public static class ImageInfo {
        public String id;
        public String name;
        public String contentType;
        public long size;
        public String preview;
        public String rowLink;
        
        public ImageInfo(CatalogImageRestRep image) {
            id = image.getId().toString();
            name = image.getName();
            contentType = image.getContentType();
            size = image.getData().length;
            rowLink = createLink(CatalogImages.class, "edit", "id", id);
        }
    }
}
