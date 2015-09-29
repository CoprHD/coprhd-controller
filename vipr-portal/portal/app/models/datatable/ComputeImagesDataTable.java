/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.ComputeImageTypes;
import util.datatable.DataTable;

import com.emc.storageos.model.compute.ComputeImageRestRep;

public class ComputeImagesDataTable extends DataTable {

    public ComputeImagesDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("imageName");
        addColumn("imageType");
        addColumn("importedServers");// Servers the image succesfully imported to
        addColumn("nonImportedServers");// Servers the image failed to import to
        addColumn("discoveryStatus").setRenderFunction("render.discoveryStatusIcon");
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class ComputeImagesInfo {
        public String id;
        public String name;
        public String imageName;
        public String imageType;
        public String imageUrl;
        public String computeImageStatus;
        public String discoveryStatus;
        public String importedServers;
        public String nonImportedServers;

        public ComputeImagesInfo() {
        }

        public ComputeImagesInfo(ComputeImageRestRep computeImage) {
            this.id = computeImage.getId().toString();
            this.name = computeImage.getName();
            this.imageName = computeImage.getImageName();
            this.imageType = ComputeImageTypes.getDisplayValue(computeImage.getImageType());
            this.imageUrl = computeImage.getImageUrl();
            this.computeImageStatus = computeImage.getComputeImageStatus();
            this.importedServers = "lgly7185,lgly6195";
            this.nonImportedServers = "lgly6186";
            String displayStatus = computeImage.getComputeImageStatus();
            if (displayStatus.equalsIgnoreCase("AVAILABLE")) {
                this.discoveryStatus = "COMPLETE";
            } else if (displayStatus.equalsIgnoreCase("NOT_AVAILABLE")) {
                this.discoveryStatus = "ERROR";
            } else {
                this.discoveryStatus = displayStatus;
            }
        }
    }
}
