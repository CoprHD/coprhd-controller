/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.compute.ComputeImageServerRestRep;

public class ComputeImageServersDataTable extends DataTable {

    public ComputeImageServersDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("imageName");
        addColumn("imageType");
        addColumn("discoveryStatus").setRenderFunction("render.discoveryStatusIcon");
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class ComputeImageServerInfo {
        public String id;
        public String name;
        public String imageName;
        public String imageType;
        public String imageUrl;
        public String computeImageStatus;
        public String discoveryStatus;

        public ComputeImageServerInfo() {
        }

        public ComputeImageServerInfo(ComputeImageServerRestRep computeImage) {
            this.id = computeImage.getId().toString();
            this.name = computeImage.getName();
            /*
             * this.imageName = computeImage.getImageName();
             * this.imageType = ComputeImageTypes.getDisplayValue(computeImage.getImageType());
             * this.imageUrl = computeImage.getImageUrl();
             * this.computeImageStatus = computeImage.getComputeImageStatus();
             */}
    }
}
