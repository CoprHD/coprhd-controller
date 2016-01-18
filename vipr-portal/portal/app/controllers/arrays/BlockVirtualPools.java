/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.angularRenderArgs;
import static controllers.Common.copyRenderArgsToAngular;
import static controllers.Common.flashException;
import static controllers.Common.getReferrer;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jobs.vipr.ConnectedBlockVirtualPoolsCall;
import jobs.vipr.TenantsCall;
import jobs.vipr.VirtualArraysCall;
import models.BlockProtocols;
import models.DriveTypes;
import models.HighAvailability;
import models.PoolAssignmentTypes;
import models.ProtectionSystemTypes;
import models.ProvisioningTypes;
import models.RaidLevel;
import models.RemoteCopyMode;
import models.RpoType;
import models.SizeUnit;
import models.StorageSystemTypes;
import models.datatable.StoragePoolDataTable;
import models.datatable.StoragePoolDataTable.StoragePoolInfo;
import models.datatable.VirtualPoolDataTable;
import models.datatable.VirtualPoolDataTable.VirtualPoolInfo;
import models.virtualpool.BlockVirtualPoolForm;
import models.virtualpool.RPCopyForm;
import models.virtualpool.SrdfCopyForm;

import org.apache.commons.beanutils.BeanUtils;
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
import util.EnumOption;
import util.MessagesUtils;
import util.StorageSystemUtils;
import util.StringOption;
import util.TenantUtils;
import util.ValidationResponse;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class BlockVirtualPools extends ViprResourceController {
    protected static final String SAVED_SUCCESS = "VirtualPools.save.success";
    protected static final String SAVED_ERROR = "VirtualPools.save.error";
    protected static final String DELETED_SUCCESS = "VirtualPools.delete.success";
    protected static final String DELETED_ERROR = "VirtualPools.delete.error";
    protected static final String UNKNOWN = "VirtualPools.unknown";

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
        for (BlockVirtualPoolRestRep virtualPool : VirtualPoolUtils.getBlockVirtualPools()) {
            items.add(new VirtualPoolInfo(virtualPool));
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    public static void duplicate(String ids) {
        BlockVirtualPoolRestRep targetVPool = VirtualPoolUtils.getBlockVirtualPool(ids);
        if (targetVPool == null) {
            flash.error(MessagesUtils.get(UNKNOWN, ids));
            backToReferrer();
        }
        BlockVirtualPoolForm copy = new BlockVirtualPoolForm();
        copy.load(targetVPool);
        copy.id = null;
        copy.name = Messages.get("virtualPools.duplicate.name", copy.name);
        // Target VPool could have resources, set resources to 0 on the new Copy VPool so user can modify form
        copy.numResources = 0;
        edit(copy);
    }

    public static void create() {
        BlockVirtualPoolForm vpool = new BlockVirtualPoolForm();
        vpool.provisioningType = ProvisioningTypes.THIN;
        vpool.protocols = Sets.newHashSet(BlockProtocols.FC);
        vpool.minPaths = 1;
        vpool.maxPaths = 2;
        vpool.initiatorPaths = 1;
        vpool.expandable = true;
        vpool.rpJournalSizeUnit = SizeUnit.x;
        vpool.rpJournalSize = RPCopyForm.JOURNAL_DEFAULT_MULTIPLIER;
        vpool.rpRpoValue = Long.valueOf(25);
        vpool.rpRpoType = RpoType.SECONDS;
        vpool.protectSourceSite = true;
        vpool.enableAutoCrossConnExport = true;

        edit(vpool);
    }

    public static void edit(String id) {
        BlockVirtualPoolRestRep virtualPool = VirtualPoolUtils.getBlockVirtualPool(id);
        if (virtualPool == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            backToReferrer();
        }

        BlockVirtualPoolForm vpool = new BlockVirtualPoolForm();
        vpool.load(virtualPool);
        edit(vpool);
    }

    private static void edit(BlockVirtualPoolForm vpool) {
        applyFlashParam(vpool, "vpool", "autoTierPolicy", "systemType", "provisioningType", "haVirtualArray", "haVirtualPool",
                "highAvailability", "remoteProtection");
        List<String> varrays = getFlashList("vpool", "virtualArrays");
        if (varrays != null) {
            vpool.virtualArrays = varrays;
        }
        Boolean uniqueNamesBool = getFlashBoolean("vpool", "uniqueAutoTierPolicyNames");
        if (uniqueNamesBool != null) {
            vpool.uniqueAutoTierPolicyNames = uniqueNamesBool;
        }
        addStaticOptions();
        addDynamicOptions(vpool);
        renderArgs.put("storagePoolsDataTable", createStoragePoolDataTable());

        copyRenderArgsToAngular();
        angularRenderArgs().put("vpool", vpool);

        render("@edit", vpool);
    }

    private static StoragePoolDataTable createStoragePoolDataTable() {
        StoragePoolDataTable dataTable = new StoragePoolDataTable();
        dataTable.alterColumn("registrationStatus").hidden();
        return dataTable;
    }

    private static Boolean getFlashBoolean(String beanName, String name) {
        String bool = flash.get(beanName + "." + name);
        if (bool != null) {
            return Boolean.valueOf(bool);
        }
        else {
            return null;
        }
    }

    private static List<String> getFlashList(String beanName, String name) {
        String value = flash.get(beanName + "." + name);
        if (value != null) {
            return Lists.newArrayList(value.split(","));
        }
        return null;
    }

    private static void applyFlashParam(BlockVirtualPoolForm bean, String beanName, String... names) {
        for (String name : names) {
            String value = flash.get(beanName + "." + name);
            if (value != null) {
                try {
                    BeanUtils.setProperty(bean, name, value);
                } catch (IllegalAccessException e) {
                    Logger.warn(e, "Could not set property %s from flash", name);
                } catch (InvocationTargetException e) {
                    Logger.warn(e, "Could not set property %s from flash", name);
                }
            }
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

    public static void listVirtualArrayAttributesJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        renderJSON(vpool.getVirtualPoolAttributes());
    }

    public static void listContinuousCopyVirtualPoolsJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        List<BlockVirtualPoolRestRep> pools = await(vpool.connectedVirtualPools().asPromise());
        renderJSON(dataObjectOptions(pools));
    }

    public static void listRecoverPointVirtualArraysJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        List<VirtualArrayRestRep> virtualArrays = await(vpool.recoverPointVirtualArrays().asPromise());
        renderJSON(dataObjectOptions(virtualArrays));
    }

    public static void listRecoverPointVirtualPoolsJson(RPCopyForm rpCopy) {
        if (rpCopy == null) {
            renderJSON(Collections.emptyList());
        }
        List<BlockVirtualPoolRestRep> pools = await(rpCopy.recoverPointVirtualPools().asPromise());
        renderJSON(dataObjectOptions(pools));
    }

    public static void listRecoverPointJournalVPoolsJson(RPCopyForm rpCopy) {
        if (rpCopy == null) {
            renderJSON(Collections.emptyList());
        }
        List<BlockVirtualPoolRestRep> pools = await(rpCopy.recoverPointJournalVirtualPools().asPromise());
        renderJSON(dataObjectOptions(pools));
    }

    public static void listSourceRpJournalVPoolsJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        List<BlockVirtualPoolRestRep> pools = await(vpool.sourceRpJournalVirtualPools().asPromise());
        renderJSON(dataObjectOptions(pools));
    }

    public static void listHaRpJournalVPoolsJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        List<BlockVirtualPoolRestRep> pools = await(vpool.haRpJournalVirtualPools().asPromise());
        renderJSON(dataObjectOptions(pools));
    }

    public static void listSrdfVirtualArraysJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        vpool.deserialize();
        List<StringOption> actualOptions = Lists.newArrayList();
        List<VirtualArrayRestRep> virtualArrays = await(vpool.srdfVirtualArrays().asPromise());
        for (StringOption option : dataObjectOptions(virtualArrays)) {
            if (!varrayAlreadyInSRDFCopies(option.id, vpool.srdfCopies)) {
                actualOptions.add(option);
            }
        }
        renderJSON(actualOptions);
    }

    public static void listSrdfVirtualPoolsJson(String virtualArray) {
        if (virtualArray == null) {
            renderJSON(Collections.emptyList());
        }
        List<BlockVirtualPoolRestRep> pools = await(new ConnectedBlockVirtualPoolsCall(uris(virtualArray)).asPromise());
        renderJSON(dataObjectOptions(pools));
    }

    public static void listHighAvailabilityVirtualArraysJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        List<VirtualArrayRestRep> virtualArrays = await(vpool.highAvailabilityVirtualArrays().asPromise());
        renderJSON(dataObjectOptions(virtualArrays));
    }

    public static void listHighAvailabilityVirtualPoolsJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        List<BlockVirtualPoolRestRep> pools = await(vpool.highAvailabilityVirtualPools().asPromise());
        renderJSON(dataObjectOptions(pools));
    }

    public static void listAutoTierPoliciesJson(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            renderJSON(Collections.emptyList());
        }
        List<String> policyNames = await(vpool.autoTierPolicyNames().asPromise());
        renderJSON(StringOption.options(policyNames));
    }

    public static void listStoragePoolsJson(BlockVirtualPoolForm vpool) {
        List<StoragePoolInfo> items = Lists.newArrayList();
        if (vpool != null && vpool.protocols != null && !vpool.protocols.isEmpty()) {
            vpool.deserialize();
            Map<URI, String> storageSystemNames = StorageSystemUtils.getStorageSystemNames();
            List<StoragePoolRestRep> pools = getMatchingStoragePools(vpool);
            for (StoragePoolRestRep pool : pools) {
                String storageSystemName = storageSystemNames.get(id(pool.getStorageSystem()));
                items.add(new StoragePoolInfo(pool, storageSystemName));
            }
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    private static List<StoragePoolRestRep> getMatchingStoragePools(BlockVirtualPoolForm vpool) {
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

    public static void validateRecoverPointCopy(RPCopyForm rpCopy) {
        if (rpCopy == null) {
            renderJSON(ValidationResponse.invalid());
        }
        rpCopy.validate("rpCopy");
        if (Validation.hasErrors()) {
            renderJSON(ValidationResponse.collectErrors());
        }
        else {
            renderJSON(ValidationResponse.valid());
        }
    }

    public static void validateSrdfCopy(SrdfCopyForm srdfCopy) {
        if (srdfCopy == null) {
            renderJSON(ValidationResponse.invalid());
        }
        srdfCopy.validate("srdfCopy");
        if (Validation.hasErrors()) {
            renderJSON(ValidationResponse.collectErrors());
        }
        else {
            renderJSON(ValidationResponse.valid());
        }
    }

    public static void save(BlockVirtualPoolForm vpool) {
        if (vpool == null) {
            list();
        }
        vpool.deserialize();
        vpool.validate("vpool");
        if (Validation.hasErrors()) {
            error(vpool);
        }

        try {
            BlockVirtualPoolRestRep virtualPool = vpool.save();
            flash.success(MessagesUtils.get(SAVED_SUCCESS, virtualPool.getName()));
            backToReferrer();
        } catch (ViPRException e) {
            exception(vpool, e);
        }
    }

    private static boolean varrayAlreadyInSRDFCopies(String varrayId, SrdfCopyForm[] copies) {
        for (SrdfCopyForm copy : copies) {
            if (copy.virtualArray.equals(varrayId)) {
                return true;
            }
        }

        return false;
    }

    private static void backToReferrer() {
        String referrer = getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            redirect(referrer);
        }
        else {
            list();
        }
    }

    private static void exception(BlockVirtualPoolForm vpool, ViPRException e) {
        flashException(e);
        error(vpool);
    }

    private static void error(BlockVirtualPoolForm vpool) {
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

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeactivateOperation(), DELETED_SUCCESS, DELETED_ERROR);
        backToReferrer();
    }

    private static void addStaticOptions() {
        renderArgs.put("provisioningTypeOptions", ProvisioningTypes.options(
                ProvisioningTypes.THICK,
                ProvisioningTypes.THIN
                ));
        renderArgs.put("protocolsOptions", BlockProtocols.options(
                BlockProtocols.FC,
                BlockProtocols.iSCSI,
                BlockProtocols.ScaleIO,
                BlockProtocols.RBD
                ));
        renderArgs.put("systemTypeOptions", StorageSystemTypes.options(
                StorageSystemTypes.NONE,
                StorageSystemTypes.VMAX,
                StorageSystemTypes.VNX_BLOCK,
                StorageSystemTypes.VNXe,
                StorageSystemTypes.HITACHI,
                StorageSystemTypes.OPENSTACK,
                StorageSystemTypes.SCALEIO,
                StorageSystemTypes.XTREMIO,
                StorageSystemTypes.IBMXIV
                ));
        renderArgs.put("driveTypeOptions", DriveTypes.options(
                DriveTypes.NONE,
                DriveTypes.FC,
                DriveTypes.SAS,
                DriveTypes.NL_SAS,
                DriveTypes.SATA,
                DriveTypes.SSD
                ));
        renderArgs.put("raidLevelsOptions", RaidLevel.options(
                RaidLevel.RAID0,
                RaidLevel.RAID1,
                RaidLevel.RAID2,
                RaidLevel.RAID3,
                RaidLevel.RAID4,
                RaidLevel.RAID5,
                RaidLevel.RAID6,
                RaidLevel.RAID10
                ));
        renderArgs.put("poolAssignmentOptions", PoolAssignmentTypes.options(
                PoolAssignmentTypes.AUTOMATIC,
                PoolAssignmentTypes.MANUAL
                ));
        renderArgs.put("highAvailabilityOptions", Lists.newArrayList(
                HighAvailability.option(HighAvailability.VPLEX_LOCAL),
                HighAvailability.option(HighAvailability.VPLEX_DISTRIBUTED)
                ));
        renderArgs.put("vplexActiveSiteOptions", Lists.newArrayList(
                HighAvailability.option(HighAvailability.VPLEX_SOURCE),
                HighAvailability.option(HighAvailability.VPLEX_HA)
                ));
        renderArgs.put("remoteProtectionOptions", Lists.newArrayList(
                ProtectionSystemTypes.option(ProtectionSystemTypes.RECOVERPOINT),
                ProtectionSystemTypes.option(ProtectionSystemTypes.SRDF)
                ));
        renderArgs.put("rpRemoteCopyModeOptions", RemoteCopyMode.OPTIONS);
        renderArgs.put("rpRpoTypeOptions", RpoType.OPTIONS);
        renderArgs.put("srdfCopyModeOptions", RemoteCopyMode.OPTIONS);
        renderArgs.put(
                "numPathsOptions",
                StringOption.options(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                        "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32" }, false));
        renderArgs.put("rpJournalSizeUnitOptions", EnumOption.options(SizeUnit.values(), false));
        renderArgs.put("varrayAttributeNames", VirtualArrayUtils.ATTRIBUTES);
    }

    private static void addDynamicOptions(BlockVirtualPoolForm vpool) {
        // Runs all queries in jobs
        Promise<List<VirtualArrayRestRep>> virtualArrays = new VirtualArraysCall().asPromise();
        Promise<List<String>> autoTierPolicies = vpool.autoTierPolicyNames().asPromise();
        Promise<List<BlockVirtualPoolRestRep>> connectedVirtualPools = vpool.connectedVirtualPools().asPromise();
        Promise<List<VirtualArrayRestRep>> haVirtualArrays = vpool.highAvailabilityVirtualArrays().asPromise();
        Promise<List<BlockVirtualPoolRestRep>> haVirtualPools = vpool.highAvailabilityVirtualPools().asPromise();
        Promise<List<VirtualArrayRestRep>> rpVirtualArrays = vpool.recoverPointVirtualArrays().asPromise();
        Promise<List<VirtualArrayRestRep>> rpJournalVirtualArrays = vpool.recoverPointVirtualArrays().asPromise();
        Promise<List<VirtualArrayRestRep>> sourceJournalVirtualArrays = vpool.sourceJournalVirtualArrays().asPromise();
        Promise<List<VirtualArrayRestRep>> haJournalVirtualArrays = vpool.haRpJournalVirtualArrays().asPromise();
        Promise<List<BlockVirtualPoolRestRep>> sourceJournalVirtualPools = vpool.sourceRpJournalVirtualPools().asPromise();
        Promise<List<BlockVirtualPoolRestRep>> haJournalVirtualPools = vpool.haRpJournalVirtualPools().asPromise();
        Promise<List<VirtualArrayRestRep>> srdfVirtualArrays = vpool.srdfVirtualArrays().asPromise();

        if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
            addDataObjectOptions("tenantOptions", new TenantsCall().asPromise());
        }
        addDataObjectOptions("virtualArrayOptions", virtualArrays);
        addStringOptions("autoTierPolicyOptions", autoTierPolicies);
        addDataObjectOptions("continuousCopyVirtualPoolOptions", connectedVirtualPools);
        addDataObjectOptions("haVirtualArrayOptions", haVirtualArrays);
        addDataObjectOptions("haVirtualPoolOptions", haVirtualPools);
        addDataObjectOptions("rpVirtualArrayOptions", rpVirtualArrays);
        addDataObjectOptions("rpJournalVirtualArrayOptions", rpJournalVirtualArrays);
        addDataObjectOptions("vpoolSourceJournalVirtualArrayOptions", sourceJournalVirtualArrays);
        addDataObjectOptions("vpoolSourceJournalVirtualPoolOptions", sourceJournalVirtualPools);
        addDataObjectOptions("vpoolHAJournalVirtualArrayOptions", haJournalVirtualArrays);
        addDataObjectOptions("vpoolHAJournalVirtualPoolOptions", haJournalVirtualPools);
        addDataObjectOptions("srdfVirtualArrayOptions", srdfVirtualArrays);
        addDataObjectOptions("srdfVirtualPoolOptions", connectedVirtualPools);
    }

    protected static class DeactivateOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            VirtualPoolCommonRestRep virtualPool = VirtualPoolUtils.getVirtualPool(id.toString());
            if (virtualPool instanceof BlockVirtualPoolRestRep) {
                VirtualPoolUtils.deactivateBlock(id);
            }
            else if (virtualPool instanceof FileVirtualPoolRestRep) {
                VirtualPoolUtils.deactivateFile(id);
            }
            return null;
        }
    }
}
