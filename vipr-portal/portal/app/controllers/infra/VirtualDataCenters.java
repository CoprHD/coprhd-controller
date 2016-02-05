/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import static com.emc.vipr.client.core.util.ResourceUtils.refIds;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import models.datatable.VirtualDataCentersDataTable;
import models.datatable.VirtualDataCentersDataTable.VirtualDataCenter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import play.cache.Cache;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.TaskUtils;
import util.VirtualDataCenterUtils;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;
import util.validation.HostNameOrIpAddressCheck;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.vdc.VirtualDataCenterAddParam;
import com.emc.storageos.model.vdc.VirtualDataCenterModifyParam;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.storageos.model.vdc.VirtualDataCenterSecretKeyRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.model.keystore.CertificateChain;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN"), @Restrict("SYSTEM_MONITOR") })
public class VirtualDataCenters extends ViprResourceController {
    private static final int DATASTORE_NOT_CONFIGURED = 40013;

    protected static final String UNKNOWN = "vdcs.unknown";

    public static void list() {
        VirtualDataCentersDataTable dataTable = new VirtualDataCentersDataTable();
        String secretKey = "";
        if (Security.isSecurityAdminOrRestrictedSecurityAdmin()) {
            secretKey = secretKey();
        }
        String inProgressTask = flash.get("inProgressTask");
        // handle page refresh that empties the flash scope
        if (inProgressTask == null || inProgressTask.isEmpty()) {
            inProgressTask = getPendingVDCTask();
        }
        Common.angularRenderArgs().put("taskId", inProgressTask);
        render(dataTable, secretKey);
    }

    private static String getPendingVDCTask() {
        List<URI> viprVDCs =
                refIds(VirtualDataCenterUtils.list());
        for (URI vdc : viprVDCs) {
            List<TaskResourceRep> tasks = TaskUtils.getTasks(vdc);
            for (TaskResourceRep task : tasks) {
                if ("pending".equalsIgnoreCase(task.getState())) {
                    return task.getId().toString();
                }
            }
        }
        return null;
    }

    @FlashException(value = "list", keep = true)
    public static void listJson() {
        List<VirtualDataCenterRestRep> viprVDCs =
                VirtualDataCenterUtils.listByIds(refIds(VirtualDataCenterUtils.list()));
        List<VirtualDataCentersDataTable.VirtualDataCenter> vdcs = Lists.newArrayList();
        for (VirtualDataCenterRestRep vdc : viprVDCs) {
            if (Security.isSystemAdminOrRestrictedSystemAdmin() || Security.isSecurityAdminOrRestrictedSecurityAdmin()
                    || Security.isSystemMonitor()) {
                vdcs.add(new VirtualDataCentersDataTable.VirtualDataCenter(vdc));
            }
        }
        renderJSON(DataTablesSupport.createJSON(vdcs, params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    public static void itemDetails(String id) {
        VirtualDataCenterRestRep vdc = VirtualDataCenterUtils.get(id);
        if (vdc == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        Tasks<VirtualDataCenterRestRep> vdcTasks =
                VirtualDataCenterUtils.getTasks(uri(id));
        Task<VirtualDataCenterRestRep> latestTask = null;
        if (vdcTasks != null) {
            latestTask = vdcTasks.latestFinishedTask();
        }
        render(vdc, latestTask);
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(VirtualDataCenterUtils.listByIds(ids), new JsonItemOperation());
    }

    @FlashException(value = "list", keep = true)
    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN") })
    public static void create() {
        VirtualDataCenterForm vdc = new VirtualDataCenterForm();
        addRenderArgs();
        render("@edit", vdc);
    }

    private static String secretKey() {

        String secretKey = null;
        VirtualDataCenterSecretKeyRestRep keyResp =
                VirtualDataCenterUtils.getSecretKey();
        if (keyResp != null) {
            secretKey = keyResp.getSecretKey();
        }
        return secretKey;
    }

    @FlashException(value = "list", keep = true)
    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void edit(String id) {
        VirtualDataCenterRestRep viprVDC = VirtualDataCenterUtils.get(id);

        if (viprVDC != null) {
            VirtualDataCenterForm vdc = new VirtualDataCenterForm().from(viprVDC);
            addRenderArgs();
            render(vdc);
        }
        else {
            flash.error(MessagesUtils.get("vdcs.unknown", id));
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void save(VirtualDataCenterForm vdc) {
        vdc.validate("vdc");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        if (vdc.isNew()) {

            VirtualDataCenterAddParam vdcToAdd = new VirtualDataCenterAddParam();
            vdcToAdd.setName(vdc.name);
            vdcToAdd.setApiEndpoint(vdc.apiEndpoint);
            vdcToAdd.setDescription(vdc.description);
            vdcToAdd.setSecretKey(vdc.secretKey);
            try {
                vdcToAdd.setCertificateChain(FileUtils.readFileToString(vdc.certChain));
            } catch (Exception e) {
                flash.error(MessagesUtils.get("vdc.certChain.invalid.error"));
                Common.handleError();
            }

            Task<VirtualDataCenterRestRep> task =
                    VirtualDataCenterUtils.create(vdcToAdd);
            flash.put("inProgressTask", task.getTaskResource().getId());

        }
        else {
            VirtualDataCenterRestRep currentVDC = VirtualDataCenterUtils.get(vdc.id);
            if (currentVDC != null) {
                VirtualDataCenterModifyParam vdcToUpdate = new VirtualDataCenterModifyParam();
                vdcToUpdate.setName(vdc.name);
                vdcToUpdate.setDescription(vdc.description);
                // TODO: update this when supported.
                vdcToUpdate.setRotateKeyCert(null);
                // TODO: update this once API is updated to accept these
                // vdcToUpdate.setApiEndpoint(vdc.apiEndpoint);
                // vdcToUpdate.setSecretKey(vdc.secretKey);

                Task<VirtualDataCenterRestRep> task =
                        VirtualDataCenterUtils.update(uri(vdc.id), vdcToUpdate);
                flash.put("inProgressTask", task.getTaskResource().getId());
            }
        }

        Cache.delete(Common.VDCS);

        if (StringUtils.isNotBlank(vdc.referrerUrl)) {
            redirect(vdc.referrerUrl);
        }
        else {
            list();
        }
    }

    @FlashException("list")
    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN") })
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
        list();
    }

    private static void delete(List<URI> ids) {
        if (!ids.isEmpty()) {
            // UI only support single selection, ignore any additional IDs
            URI id = ids.get(0);
            Task<VirtualDataCenterRestRep> task = VirtualDataCenterUtils.delete(id);
            flash.put("inProgressTask", task.getTaskResource().getId());
            Cache.delete(Common.VDCS);
        }
    }

    @FlashException("list")
    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN") })
    public static void disconnect(@As(",") String[] ids) {
        disconnect(uris(ids));
        list();
    }

    private static void disconnect(List<URI> ids) {
        if (!ids.isEmpty()) {
            // UI only support single selection, ignore any additional IDs
            URI id = ids.get(0);
            Task<VirtualDataCenterRestRep> task = VirtualDataCenterUtils.disconnect(id);
            flash.put("inProgressTask", task.getTaskResource().getId());
        }
    }

    @FlashException("list")
    @Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("SECURITY_ADMIN") })
    public static void reconnect(@As(",") String[] ids) {
        reconnect(uris(ids));
        list();
    }

    private static void reconnect(List<URI> ids) {
        if (!ids.isEmpty()) {
            // UI only support single selection, ignore any additional IDs
            URI id = ids.get(0);
            Task<VirtualDataCenterRestRep> task = VirtualDataCenterUtils.reconnect(id);
            flash.put("inProgressTask", task.getTaskResource().getId());
        }
    }

    public static void downloadCertificateChain() throws UnsupportedEncodingException {
        CertificateChain cert =
                BourneUtil.getViprClient().vdc().getCertificateChain();
        if (cert == null || cert.getChain() == null || cert.getChain().isEmpty()) {
            flash.error(MessagesUtils.get("vdc.certChain.empty.error"));
        }
        String chain = cert.getChain();
        ByteArrayInputStream is =
                new ByteArrayInputStream(chain.getBytes("UTF-8"));
        renderBinary(is, "vdc_certificate", "text/plain", false);
    }

    private static void addRenderArgs() {
    }

    public static class VirtualDataCenterForm {

        public String id;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        public String shortId;

        @HostNameOrIpAddress
        public String apiEndpoint;

        public String description;

        public String secretKey;

        public String referrerUrl;

        public boolean local;

        public boolean rotate;

        public File certChain;

        public File certKey;

        public VirtualDataCenterForm from(VirtualDataCenterRestRep from) {
            this.id = from.getId().toString();
            this.name = from.getName();
            this.apiEndpoint = from.getApiEndpoint();
            this.description = from.getDescription();
            this.shortId = from.getShortId();
            this.local = from.isLocal();
            return this;
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            if (isNew()) {
                Validation.required(String.format("%s.name", formName), this.name);
                Validation.required(String.format("%s.description", formName), this.description);
                Validation.required(String.format("%s.apiEndpoint", formName), this.apiEndpoint);
                Validation.required(String.format("%s.secretKey", formName), this.secretKey);
                Validation.required(String.format("%s.certChain", formName), this.certChain);
            } else {
                Validation.required(String.format("%s.name", formName), this.name);
                Validation.required(String.format("%s.description", formName), this.description);
            }
        }
    }

    private static boolean validateEndpoints(String endpoints) {
        if (endpoints != null && !endpoints.isEmpty()) {
            List<String> endpointsToValidate =
                    Arrays.asList(endpoints.split(","));
            for (String endpoint : endpointsToValidate) {
                if (!HostNameOrIpAddressCheck.hasValidPort(endpoint)) {
                    return false;
                }
                endpoint = HostNameOrIpAddressCheck.trimPortFromEndpoint(endpoint);
                if (!HostNameOrIpAddressCheck.isValidHostNameOrIp(
                        StringUtils.deleteWhitespace(endpoint))) {
                    return false;
                }
            }
        }

        return true;
    }

    protected static class JsonItemOperation implements ResourceValueOperation<VirtualDataCenter, VirtualDataCenterRestRep> {
        @Override
        public VirtualDataCenter performOperation(VirtualDataCenterRestRep vdc) throws Exception {
            VirtualDataCenterRestRep vdcRestRep = VirtualDataCenterUtils.get(vdc.getId().toString());
            return new VirtualDataCenter(vdcRestRep);
        }
    }
}
