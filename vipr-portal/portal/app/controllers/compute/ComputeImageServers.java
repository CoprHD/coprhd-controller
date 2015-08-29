/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import models.datatable.ComputeImageServersDataTable;
import models.datatable.ComputeImageServersDataTable.ComputeImageServerInfo;

/*import models.ComputeImageServerTypes;
 import models.datatable.ComputeImageServersDataTable;
 import models.datatable.ComputeImageServersDataTable.ComputeImageServerInfo;
 */
import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.ComputeImageServerUtils;
import util.MessagesUtils;

import com.emc.storageos.model.compute.ComputeImageServerRestRep;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class ComputeImageServers extends ViprResourceController {

    protected static final String SAVED = "ComputeImageServers.saved";
    protected static final String UNKNOWN = "ComputeImageServers.unknown";
    protected static final String MODEL_NAME = "ComputeImageServers";
    protected static final String DELETED_SUCCESS = "ComputeImageServers.deleted.success";
    protected static final String DELETED_ERROR = "ComputeImageServers.deleted.error";

    //
    // Add reference data so that they can be reference in html template
    //

    private static void addReferenceData() {

    }

    public static void list() {
        renderArgs.put("dataTable", new ComputeImageServersDataTable());
        render();
    }

    public static void listJson() {
        performListJson(ComputeImageServerUtils.getComputeImageServers(),
                new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(ComputeImageServerUtils.getComputeImageServers(ids),
                new JsonItemOperation());
    }

    public static void itemDetails(String id) {
        ComputeImageServerRestRep computeImageServer = ComputeImageServerUtils
                .getComputeImageServer(id);
        if (computeImageServer == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        render(computeImageServer);
    }

    public static void create() {
        addReferenceData();
        ComputeImageServerForm ComputeImageServer = new ComputeImageServerForm();
        render("@edit", ComputeImageServer);
    }

    /*
     * public static void createClone(@As(",") String[] ids) {
     * if (ids != null && ids.length > 0) {
     * String imageId = ids[0];
     * createAClone(imageId);
     * }
     * }
     * 
     * public static void createAClone(String ImageServerId) {
     * addReferenceData();
     * 
     * ComputeImageServerRestRep computeImageServer = ComputeImageServerUtils
     * .getComputeImageServer(ImageServerId);
     * ComputeImageServerForm ComputeImageServer = new ComputeImageServerForm(computeImageServer, true);
     * render("@edit", ComputeImageServer);
     * }
     */

    @FlashException("list")
    public static void edit(String id) {
        addReferenceData();

        ComputeImageServerRestRep computeImageServer = ComputeImageServerUtils
                .getComputeImageServer(id);
        if (computeImageServer != null) {
            ComputeImageServerForm ComputeImageServer = new ComputeImageServerForm(
                    computeImageServer);
            render("@edit", ComputeImageServer);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    /*
     * @FlashException(keep = true, referrer = { "create", "edit" })
     * public static void save(ComputeImageServerForm ComputeImageServer) {
     * 
     * ComputeImageServer.validate("ComputeImageServers");
     * 
     * if (Validation.hasErrors()) {
     * handleError(ComputeImageServer);
     * }
     * ComputeImageServer.save();
     * String name = ComputeImageServers.name;
     * flash.success(MessagesUtils.get(SAVED, name));
     * backToReferrer();
     * list();
     * }
     * 
     * private static void handleError(ComputeImageServerForm ComputeImageServers) {
     * params.flash();
     * Validation.keep();
     * if (ComputeImageServers.isNew()) {
     * if (ComputeImageServers.ImageServerId == null) {
     * create();
     * }
     * else {
     * createAClone(ComputeImageServers.ImageServerId.toString());
     * }
     * }
     * else {
     * edit(ComputeImageServers.id);
     * }
     * }
     */
    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeleteOperation(), DELETED_SUCCESS, DELETED_ERROR);
        list();
    }

    public static void cloneImageServer(String id) {
        cloneImageServer(id);
        list();
    }

    public static class ComputeImageServerForm {

        public String id;

        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        @MaxSize(2048)
        public String ImageServerUrl;

        public URI ImageServerId;

        public String ImageServerName;

        public String ImageServerType;

        public String ComputeImageServerStatus;

        public String lastImageServerStatusMessage;

        public String cloneName;
        public String cloneExtractedName;
        public String cloneType;;
        public String cloneUrl;

        public ComputeImageServerForm() {
        }

        public ComputeImageServerForm(ComputeImageServerRestRep computeImageServer) {
            this.id = computeImageServer.getId().toString();
            this.name = computeImageServer.getName();
            /*
             * this.ImageServerName = computeImageServer.getImageServerName();
             * this.ImageServerType = ComputeImageServerTypes.getDisplayValue(computeImageServer.getImageServerType());
             * this.ImageServerUrl = computeImageServer.getImageServerUrl();
             * this.ComputeImageServerStatus = computeImageServer.getComputeImageServerStatus();
             * this.lastImageServerStatusMessage = computeImageServer.getLastImportStatusMessage();
             */}

        public ComputeImageServerForm(ComputeImageServerRestRep computeImageServer, boolean clone) {
            this.cloneName = computeImageServer.getName();
            /*
             * this.ImageServerId = computeImageServer.getImageServerId();
             * this.cloneExtractedName = computeImageServer.getImageServerName();
             * this.cloneType = ComputeImageServerTypes.getDisplayValue(computeImageServer.getImageServerType());
             * this.cloneUrl = computeImageServer.getImageServerUrl();
             */}

        public boolean isNew() {
            return StringUtils.isBlank(this.id);
        }

        public boolean isCreate() {
            return StringUtils.isBlank(this.id) && this.ImageServerId == null;
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
            Validation.required(fieldName + ".name", this.name);
            if (isCreate()) {
                Validation.required(fieldName + ".ImageServerUrl", this.ImageServerUrl);
                try {
                    new URL(this.ImageServerUrl);// NOSONAR
                    // ("Suppressing Sonar violation of Object being dropped without using it. Object is used for validation purpose")
                } catch (MalformedURLException e) {
                    Validation.addError(fieldName + ".ImageServerUrl",
                            MessagesUtils.get("computeImageServer.invalid.url"));
                }
            }

        }

        /*
         * public Task<ComputeImageServerRestRep> save() {
         * if (isNew()) {
         * return create();
         * } else {
         * return update();
         * }
         * }
         * 
         * private Task<ComputeImageServerRestRep> create() {
         * ComputeImageServerCreate createParam = new ComputeImageServerCreate();
         * // createParam.setName(this.name);
         * // createParam.setImageServerUrl(this.ImageServerUrl);
         * return ComputeImageServerUtils.create(createParam);
         * }
         * 
         * private Task<ComputeImageServerRestRep> update() {
         * ComputeImageServerUpdate updateParam = new ComputeImageServerUpdate();
         * 
         * updateParam.setName(this.name);
         * 
         * if (this.ComputeImageServerStatus.equals("NOT_AVAILABLE") && this.ImageServerUrl != null && this.ImageServerUrl.length() > 0)
         * {
         * updateParam.setImageServerUrl(this.ImageServerUrl);
         * }
         * return ComputeImageServerUtils.update(id, updateParam);
         * }
         */
    }

    protected static class JsonItemOperation implements
            ResourceValueOperation<ComputeImageServerInfo, ComputeImageServerRestRep> {

        @Override
        public ComputeImageServerInfo performOperation(
                ComputeImageServerRestRep computeImageServer) throws Exception {
            return new ComputeImageServerInfo(computeImageServer);
        }
    }

    protected static class DeleteOperation implements ResourceIdOperation<Void> {

        @Override
        public Void performOperation(URI id) throws Exception {
            ComputeImageServerUtils.deactivate(id);
            return null;
        }
    }

}
