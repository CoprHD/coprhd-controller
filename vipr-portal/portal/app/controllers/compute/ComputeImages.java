/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.backToReferrer;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import models.ComputeImageTypes;
import models.datatable.ComputeImagesDataTable;
import models.datatable.ComputeImagesDataTable.ComputeImagesInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.ComputeImageUtils;
import util.MessagesUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.compute.ComputeImageCreate;
import com.emc.storageos.model.compute.ComputeImageRestRep;
import com.emc.storageos.model.compute.ComputeImageUpdate;
import com.emc.vipr.client.Task;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class ComputeImages extends ViprResourceController {

    protected static final String SAVED = "ComputeImages.saved";
    protected static final String UNKNOWN = "ComputeImages.unknown";
    protected static final String MODEL_NAME = "ComputeImages";
    protected static final String DELETED_SUCCESS = "ComputeImages.deleted.success";
    protected static final String DELETED_ERROR = "ComputeImages.deleted.error";

    //
    // Add reference data so that they can be reference in html template
    //

    private static void addReferenceData() {

    }

    public static void list() {
        renderArgs.put("dataTable", new ComputeImagesDataTable());
        render();
    }

    public static void listJson() {
        performListJson(ComputeImageUtils.getComputeImages(),
                new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(ComputeImageUtils.getComputeImages(ids),
                new JsonItemOperation());
    }

    public static void itemDetails(String id) {
        ComputeImageRestRep computeImage = ComputeImageUtils
                .getComputeImage(id);
        if (computeImage == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        render(computeImage);
    }

    public static void create() {
        addReferenceData();
        ComputeImageForm computeImages = new ComputeImageForm();
        render("@edit", computeImages);
    }

    public static void createClone(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            String imageId = ids[0];
            createAClone(imageId);
        }
    }

    public static void createAClone(String imageId) {
        addReferenceData();

        ComputeImageRestRep computeImage = ComputeImageUtils
                .getComputeImage(imageId);
        ComputeImageForm computeImages = new ComputeImageForm(computeImage, true);
        render("@edit", computeImages);
    }

    @FlashException("list")
    public static void edit(String id) {
        addReferenceData();

        ComputeImageRestRep computeImage = ComputeImageUtils
                .getComputeImage(id);
        if (computeImage != null) {
            ComputeImageForm computeImages = new ComputeImageForm(computeImage);
            renderArgs.put("availableImageServersNames", computeImages.availableImageServerNames);
            renderArgs.put("failedImageServersNames", computeImages.failedImageServerNames);
            render("@edit", computeImages);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(ComputeImageForm computeImages) {

        computeImages.validate("computeImages");

        if (Validation.hasErrors()) {
            handleError(computeImages);
        }
        computeImages.save();
        String name = computeImages.name;
        flash.success(MessagesUtils.get(SAVED, name));
        backToReferrer();
        list();
    }

    private static void handleError(ComputeImageForm computeImages) {
        params.flash();
        Validation.keep();
        if (computeImages.isNew()) {
            if (computeImages.imageId == null) {
                create();
            }
            else {
                createAClone(computeImages.imageId.toString());
            }
        }
        else {
            edit(computeImages.id);
        }
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeleteOperation(), DELETED_SUCCESS, DELETED_ERROR);
        list();
    }

    public static void cloneImage(String id) {
        cloneImage(id);
        list();
    }

    public static class ComputeImageForm {

        public String id;

        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        @MaxSize(2048)
        public String imageUrl;

        public URI imageId;

        public String imageName;

        public String imageType;

        public String computeImageStatus;

        public String lastImageStatusMessage;

        public String availableImageServerNames = "";

        public String failedImageServerNames = "";

        private List<NamedRelatedResourceRep> availableImageServers;

        private List<NamedRelatedResourceRep> failedImageServers;

        public String cloneName;
        public String cloneExtractedName;
        public String cloneType;;
        public String cloneUrl;

        public ComputeImageForm() {
        }

        public ComputeImageForm(ComputeImageRestRep computeImage) {
            this.id = computeImage.getId().toString();
            this.name = computeImage.getName();
            this.imageName = computeImage.getImageName();
            this.imageType = ComputeImageTypes.getDisplayValue(computeImage.getImageType());
            this.imageUrl = computeImage.getImageUrl();
            this.computeImageStatus = computeImage.getComputeImageStatus();
            this.lastImageStatusMessage = computeImage.getLastImportStatusMessage();
            this.availableImageServers = computeImage.getAvailableImageServers();
            this.failedImageServers = computeImage.getFailedImageServers();

            for (NamedRelatedResourceRep availableImageServer : availableImageServers) {
                if (availableImageServer.getName() != null) {
                    this.availableImageServerNames = availableImageServerNames.concat(availableImageServer.getName() + ",   ");
                }
            }
            for (NamedRelatedResourceRep failedImageServer : failedImageServers) {
                if (failedImageServer.getName() != null) {
                    this.failedImageServerNames = failedImageServerNames.concat(failedImageServer.getName() + ",   ");
                }
            }
        }

        public ComputeImageForm(ComputeImageRestRep computeImage, boolean clone) {
            this.imageId = computeImage.getImageId();
            this.cloneName = computeImage.getName();
            this.cloneExtractedName = computeImage.getImageName();
            this.cloneType = ComputeImageTypes.getDisplayValue(computeImage.getImageType());
            this.cloneUrl = computeImage.getImageUrl();
        }

        public boolean isNew() {
            return StringUtils.isBlank(this.id);
        }

        public boolean isCreate() {
            return StringUtils.isBlank(this.id) && this.imageId == null;
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
            Validation.required(fieldName + ".name", this.name);
            if (isCreate()) {
                Validation.required(fieldName + ".imageUrl", this.imageUrl);
                try {
                    new URL(this.imageUrl);// NOSONAR
                                           // ("Suppressing Sonar violation of Object being dropped without using it. Object is used for validation purpose")
                } catch (MalformedURLException e) {
                    Validation.addError(fieldName + ".imageUrl",
                            MessagesUtils.get("computeImage.invalid.url"));
                }
            }

        }

        public Task<ComputeImageRestRep> save() {
            if (isNew()) {
                return create();
            } else {
                return update();
            }
        }

        private Task<ComputeImageRestRep> create() {
            ComputeImageCreate createParam = new ComputeImageCreate();
            createParam.setName(this.name);
            createParam.setImageUrl(this.imageUrl);
            return ComputeImageUtils.create(createParam);
        }

        private Task<ComputeImageRestRep> update() {
            ComputeImageUpdate updateParam = new ComputeImageUpdate();
            updateParam.setName(this.name);

            if (this.computeImageStatus.equals("NOT_AVAILABLE") && this.imageUrl != null && this.imageUrl.length() > 0) {
                updateParam.setImageUrl(this.imageUrl);
            }
            return ComputeImageUtils.update(id, updateParam);
        }
    }

    protected static class JsonItemOperation implements
            ResourceValueOperation<ComputeImagesInfo, ComputeImageRestRep> {
        @Override
        public ComputeImagesInfo performOperation(
                ComputeImageRestRep computeImage) throws Exception {
            return new ComputeImagesInfo(computeImage);
        }
    }

    protected static class DeleteOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            ComputeImageUtils.deactivate(id);
            return null;
        }
    }

}
