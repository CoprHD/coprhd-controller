/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.ComputeImageTypes;
import util.ComputeImageUtils;
import util.datatable.DataTable;

import com.emc.storageos.model.compute.ComputeImageRestRep;

public class ComputeImagesDataTable extends DataTable {

    public ComputeImagesDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("imageName");
        addColumn("imageType");
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

        public ComputeImagesInfo() {
        }

        public ComputeImagesInfo(ComputeImageRestRep computeImage) {
            this.id = computeImage.getId().toString();
            this.name = computeImage.getName();
            this.imageName = computeImage.getImageName();
            this.imageType = ComputeImageTypes.getDisplayValue(computeImage.getImageType());
            this.imageUrl = computeImage.getImageUrl();
            this.computeImageStatus = computeImage.getComputeImageStatus();
            String displayStatus = computeImage.getComputeImageStatus();
            ComputeImageRestRep cirr = ComputeImageUtils.getComputeImage(id);
            if (displayStatus.equalsIgnoreCase("AVAILABLE") && cirr.getFailedImageServers().size() == 0) {
                this.discoveryStatus = "COMPLETE";
            } else if (displayStatus.equalsIgnoreCase("AVAILABLE") && cirr.getFailedImageServers().size() > 0) {
                this.discoveryStatus = "PARTIAL_SUCCESS";
            } else if (displayStatus.equalsIgnoreCase("NOT_AVAILABLE")) {
                this.discoveryStatus = "ERROR";
            } else {
                this.discoveryStatus = displayStatus;
            }
        }
    }
}
