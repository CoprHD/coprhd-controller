/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.backToReferrer;

import java.net.URI;
import java.util.List;

import models.SearchScopes;
import models.datatable.ComputeImageServersDataTable;
import models.datatable.ComputeImageServersDataTable.ComputeImageServerInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.ComputeImageServerUtils;
import util.MessagesUtils;
import util.validation.HostNameOrIpAddressCheck;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.compute.ComputeImageServerCreate;
import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.storageos.model.compute.ComputeImageServerUpdate;
import com.emc.vipr.client.Task;

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
        renderArgs.put("searchScopeTypeList", SearchScopes.options(
                SearchScopes.ONELEVEL, SearchScopes.SUBTREE));
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
        ComputeImageServer.tftpBootDir = MessagesUtils.get("ComputeImageServer.default.tftpBootDir");
        ComputeImageServer.osInstallTimeOut = Integer.parseInt(MessagesUtils.get("ComputeImageServer.default.installTimeout"));
        render("@edit", ComputeImageServer);
    }

    @FlashException("list")
    public static void edit(String id) {
        addReferenceData();

        ComputeImageServerRestRep computeImageServer = ComputeImageServerUtils.getComputeImageServer(id);
        if (computeImageServer != null) {
            ComputeImageServerForm computeImageServers = new ComputeImageServerForm(computeImageServer);
            render("@edit", computeImageServers);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(ComputeImageServerForm computeImageServers) {
        computeImageServers.validate("computeImageServers");
        if (Validation.hasErrors()) {
            handleError(computeImageServers);
        }
        computeImageServers.save();
        flash.success(MessagesUtils.get(SAVED, computeImageServers.imageServerIp));
        backToReferrer();
        list();
    }

    private static void handleError(ComputeImageServerForm computeImageServers) {
        params.remove("computeImageServers.password");
        params.remove("computeImageServers.confirmPassword");
        params.flash();
        Validation.keep();
        if (computeImageServers.isNew()) {
            create();
        }
        else {
            edit(computeImageServers.id);
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

    public static class ComputeImageServerForm {

        public String id;

        @MaxSize(128)
        @MinSize(2)
        public String name;

        @MaxSize(2048)
        public String imageServerIp;

        @MaxSize(2048)
        public String imageServerSecondIp;

        public String osInstallNetworkAddress;

        public Integer osInstallTimeOut;

        public String status;

        public String tftpBootDir;

        public List<NamedRelatedResourceRep> computeImages;

        public List<NamedRelatedResourceRep> failedImages;

        @MaxSize(2048)
        @Required
        public String userName;

        @MaxSize(2048)
        public String password = "";// NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")

        @MaxSize(2048)
        public String confirmPassword = "";// NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")

        public ComputeImageServerForm() {
        }

        public ComputeImageServerForm(ComputeImageServerRestRep computeImageServer) {
            this.id = computeImageServer.getId().toString();
            this.name = computeImageServer.getName();
            this.imageServerIp = computeImageServer.getImageServerIp();
            this.osInstallNetworkAddress = computeImageServer.getImageServerSecondIp();
            this.status = computeImageServer.getComputeImageServerStatus();
            this.tftpBootDir = computeImageServer.getTftpBootDir();
            this.userName = computeImageServer.getImageServerUser();
            this.osInstallTimeOut = computeImageServer.getOsInstallTimeout();
            this.computeImages = computeImageServer.getComputeImages();
            this.failedImages = computeImageServer.getFailedImages();
            this.password = ""; // the platform will never return the real password //NOSONAR
            // ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")
        }

        public boolean isNew() {
            return StringUtils.isBlank(this.id);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
            if (isNew()) {
                Validation.required(fieldName + ".password", this.password);
                Validation.required(fieldName + ".confirmPassword", this.confirmPassword);
                Validation.required(fieldName + ".imageServerIp", this.imageServerIp);
                if (!HostNameOrIpAddressCheck.isValidIp(imageServerIp)) {
                    Validation.addError(fieldName + ".imageServerIp",
                            MessagesUtils.get("computeSystem.invalid.ipAddress"));
                }
                Validation.required(fieldName + ".imageServerIp", this.osInstallNetworkAddress);
                if (!HostNameOrIpAddressCheck.isValidIp(osInstallNetworkAddress)) {
                    Validation.addError(fieldName + ".osInstallNetworkAddress",
                            MessagesUtils.get("computeSystem.invalid.ipAddress"));
                }
                if (!StringUtils.equals(StringUtils.trim(password),
                        StringUtils.trim(confirmPassword))) {
                    Validation.addError(fieldName + ".confirmPassword", MessagesUtils
                            .get("computeImageServers.confirmPassword.not.match"));
                }
            }
        }

        public URI save() {
            if (isNew()) {
                return create().getResourceId();
            }
            else {
                return update().getId();
            }
        }

        private Task<ComputeImageServerRestRep> create() {
            ComputeImageServerCreate createParam = new ComputeImageServerCreate();
            createParam.setName(this.name);
            createParam.setImageServerIp(this.imageServerIp);
            createParam.setImageServerUser(this.userName);
            createParam.setImageServerPassword(this.password);
            createParam.setOsInstallTimeout(this.osInstallTimeOut);
            createParam.setTftpBootDir(this.tftpBootDir);
            createParam.setImageServerSecondIp(this.osInstallNetworkAddress);
            return ComputeImageServerUtils.create(createParam);
        }

        private ComputeImageServerRestRep update() {
            ComputeImageServerUpdate updateParam = new ComputeImageServerUpdate();
            updateParam.setName(this.name);
            updateParam.setImageServerIp(this.imageServerIp);
            updateParam.setTftpBootDir(this.tftpBootDir);
            updateParam.setOsInstallTimeout(this.osInstallTimeOut);
            updateParam.setImageServerUser(this.userName);
            updateParam.setImageServerSecondIp(this.osInstallNetworkAddress);
            if (this.password != null && this.password.length() > 0) {
                updateParam.setImageServerPassword(this.password);
            }
            return ComputeImageServerUtils.update(id, updateParam);
        }
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
