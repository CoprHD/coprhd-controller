/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.flashException;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jobs.vipr.TenantsCall;
import jobs.vipr.VirtualArraysCall;
import models.FileProtocols;
import models.PoolAssignmentTypes;
import models.ProvisioningTypes;
import models.StorageSystemTypes;
import models.datatable.StoragePoolDataTable;
import models.datatable.StoragePoolDataTable.StoragePoolInfo;
import models.datatable.VirtualPoolDataTable;
import models.datatable.VirtualPoolDataTable.VirtualPoolInfo;
import models.virtualpool.FileVirtualPoolForm;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.Validation;
import play.exceptions.UnexpectedException;
import play.i18n.Messages;
import play.libs.F.Promise;
import play.mvc.Catch;
import play.mvc.Http;
import play.mvc.With;
import util.MessagesUtils;
import util.StorageSystemUtils;
import util.TenantUtils;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class FileVirtualPools extends ViprResourceController {
    protected static final String SAVED_SUCCESS = "VirtualPools.save.success";
    protected static final String SAVED_ERROR = "VirtualPools.save.error";
    protected static final String DELETED_SUCCESS = "VirtualPools.delete.success";
    protected static final String DELETED_ERROR = "VirtualPools.delete.error";
    protected static final String UNKNOWN = "VirtualPools.unknown";

    private static void backToReferrer() {
        String referrer = Common.getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            redirect(referrer);
        }
        else {
            list();
        }
    }

    private static void throwFlashException(FileVirtualPoolForm vpool, ViPRException e) {
        flashException(e);
        setErrorReferrer(vpool);
    }

    private static void setErrorReferrer(FileVirtualPoolForm vpool) {
        // Only flash vpool parameters to reduce amount stored in flash scope
        for (String param : params.all().keySet()) {
            if (param.startsWith("vpool.")) {
                params.flash(param);
            }
        }
        Validation.keep();
        if (vpool.isNew()) {
            create();
        }
        else {
            edit(vpool.id);
        }
    }

    public static void list() {
        VirtualPoolDataTable dataTable = createVirtualPoolDataTable();
        render(dataTable);
    }

    private static VirtualPoolDataTable createVirtualPoolDataTable() {
        VirtualPoolDataTable dataTable = new VirtualPoolDataTable();
        dataTable.alterColumn("provisionedAs").hidden();
        dataTable.alterColumn("provisioningType").setVisible(true);
        return dataTable;
    }

    public static void listJson() {
        List<VirtualPoolInfo> items = Lists.newArrayList();
        for (FileVirtualPoolRestRep virtualPool : VirtualPoolUtils.getFileVirtualPools()) {
            items.add(new VirtualPoolInfo(virtualPool));
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    public static void create() {
        FileVirtualPoolForm form = new FileVirtualPoolForm();
        // Set default values
        form.protocols = Sets.newHashSet(FileProtocols.CIFS, FileProtocols.NFS);
        edit(form);
    }

    public static void duplicate(String ids) {
        FileVirtualPoolRestRep targetVpool = VirtualPoolUtils.getFileVirtualPool(ids);
        if (targetVpool == null) {
            flash.error(MessagesUtils.get(UNKNOWN, ids));
            backToReferrer();
        }
        FileVirtualPoolForm copy = new FileVirtualPoolForm();
        copy.load(targetVpool);
        copy.id = null;
        copy.name = Messages.get("virtualPools.duplicate.name", copy.name);
        // Target VPool could have resources, set resources to 0 on the new Copy VPool so user can modify form
        copy.numResources = 0;
        edit(copy);
    }

    public static void edit(String id) {
        FileVirtualPoolRestRep virtualPool = VirtualPoolUtils.getFileVirtualPool(id);
        if (virtualPool == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            backToReferrer();
        }
        FileVirtualPoolForm form = new FileVirtualPoolForm();
        form.load(virtualPool);
        edit(form);
    }

    private static void edit(FileVirtualPoolForm vpool) {
        addStaticOptions();
        addDynamicOptions(vpool);
        renderArgs.put("storagePoolsDataTable", createStoragePoolDataTable());
        render("@edit", vpool);
    }

    private static StoragePoolDataTable createStoragePoolDataTable() {
        StoragePoolDataTable dataTable = new StoragePoolDataTable();
        dataTable.alterColumn("registrationStatus").hidden();
        dataTable.alterColumn("driveTypes").hidden();
        return dataTable;
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(FileVirtualPoolForm vpool) {
        if (vpool == null) {
            list();
        }

        vpool.validate("vpool");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        try {
            FileVirtualPoolRestRep result = vpool.save();
            flash.success(MessagesUtils.get(SAVED_SUCCESS, result.getName()));
            backToReferrer();
        } catch (ViPRException e) {
            throwFlashException(vpool, e);
        }
    }

    /**
     * Handles errors that might arise during JSON requests and returns the error message.
     * 
     * @param e
     */
    @Catch({ UnexpectedException.class, ViPRException.class })
    public static void handleJsonError(Exception e) {
        if (request.isAjax() || StringUtils.endsWithIgnoreCase(request.action, "json")) {
            Throwable cause = Common.unwrap(e);
            String message = Common.getUserMessage(cause);
            Logger.error(e, "AJAX request failed: %s.%s [%s]", request.controller, request.action, message);
            error(message);
        }
    }

    public static void listStoragePoolsJson(FileVirtualPoolForm vpool) {
        List<StoragePoolInfo> items = Lists.newArrayList();
        if (vpool != null && vpool.protocols != null && !vpool.protocols.isEmpty()) {
            Map<URI, String> storageSystemNames = StorageSystemUtils.getStorageSystemNames();
            List<StoragePoolRestRep> pools = getMatchingStoragePools(vpool);
            for (StoragePoolRestRep pool : pools) {
                String storageSystemName = storageSystemNames.get(id(pool.getStorageSystem()));
                items.add(new StoragePoolInfo(pool, storageSystemName));
            }
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    private static List<StoragePoolRestRep> getMatchingStoragePools(FileVirtualPoolForm vpool) {
        try {
            return await(vpool.matchingStoragePools().asPromise());
        } catch (UnexpectedException e) {
            Throwable cause = Common.unwrap(e);
            if (cause instanceof ViPRHttpException) {
                int httpCode = ((ViPRHttpException) cause).getHttpCode();
                // Bad request is usually a result of partially completing the form
                if (httpCode == Http.StatusCode.BAD_REQUEST) {
                    Logger.warn(cause, "Bad Request when querying matching storage pools, returning no matches");
                    return Lists.newArrayList();
                }
            }
            throw e;
        }
    }

    public static void listVirtualArrayAttributesJson(FileVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        renderJSON(vpool.getVirtualPoolAttributes());
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeactivateOperation(), DELETED_SUCCESS, DELETED_ERROR);
        backToReferrer();
    }

    private static void addStaticOptions() {
        renderArgs.put("provisioningTypeOptions",
                ProvisioningTypes.options(ProvisioningTypes.THICK, ProvisioningTypes.THIN));
        renderArgs.put("protocolOptions", FileProtocols.options(FileProtocols.CIFS, FileProtocols.NFS, FileProtocols.NFSV4));
        renderArgs.put("systemTypeOptions",
                StorageSystemTypes.options(
                        StorageSystemTypes.NONE,
                        StorageSystemTypes.ISILON,
                        StorageSystemTypes.VNX_FILE,
                        StorageSystemTypes.VNXe,
                        StorageSystemTypes.NETAPP,
                        StorageSystemTypes.NETAPPC,
                        StorageSystemTypes.DATA_DOMAIN));
        renderArgs.put("poolAssignmentOptions",
                PoolAssignmentTypes.options(PoolAssignmentTypes.AUTOMATIC, PoolAssignmentTypes.MANUAL));
        renderArgs.put("varrayAttributeNames", VirtualArrayUtils.ATTRIBUTES);
    }

    private static void addDynamicOptions(FileVirtualPoolForm vpool) {
        // Runs all queries in jobs
        Promise<List<VirtualArrayRestRep>> virtualArrays = new VirtualArraysCall().asPromise();

        if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
            addDataObjectOptions("tenantOptions", new TenantsCall().asPromise());
        }
        addDataObjectOptions("virtualArrayOptions", virtualArrays);
    }

    protected static class DeactivateOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            VirtualPoolUtils.deactivateFile(id);
            return null;
        }
    }
}
