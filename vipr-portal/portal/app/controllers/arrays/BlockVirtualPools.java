/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static controllers.Common.angularRenderArgs;
import static controllers.Common.copyRenderArgsToAngular;
import static controllers.Common.flashException;
import static controllers.Common.getReferrer;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jobs.vipr.AutoTierPolicyNamesCall;
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
import models.SRDFCopyMode;
import models.SizeUnit;
import models.StorageSystemTypes;
import models.VirtualPoolPlacementPolicy;
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
import util.StoragePoolUtils;
import util.StorageSystemTypeUtils;
import util.StorageSystemUtils;
import util.StringOption;
import util.TenantUtils;
import util.ValidationResponse;
import util.VirtualArrayUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
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

    private static final String GUIDE_DATA = "GUIDE_DATA";
    private static final String STORAGE_SYSTEMS = "storage_systems";
    private static final String VARRAYS = "varrays";
    private static final String VPOOL_COOKIES = "vpools";
    private static final String ATTRIBUTE_SYSTEM_TYPES = "system_type";
    private static final String UNITY = "unity";
    private static final String VMAX = "vmax";
    private static final String XTREMIO = "xtremio";
    private static final String VP_ALL_FLASH = "all-flash-diamond";
    private static final String VP_VMAX_DIAMOND = "vmax-diamond";
    private static final String VP_VMAX_DIAMOND_COMPRESSED = "vmax-diamond-compressed";
    private static final String VP_XIO_DIAMOND = "xio-diamond";
    private static final String VP_UNITY_DIAMOND = "unity-diamond";
    private static final String DEFAULT_AUTO_TIER = "Diamond SLO (0.8ms)";
    private static final String DIAMOND_SLO = "Diamond";
    

    private static List<String> getSupportAutoTierTypes() {
        List<String> result = new ArrayList<String>();
        StorageSystemTypeList types = StorageSystemTypeUtils.getAllStorageSystemTypes(StorageSystemTypeUtils.ALL_TYPE);
        for (StorageSystemTypeRestRep type : types.getStorageSystemTypes()) {
            if (type.isNative() || type.getIsSmiProvider() || !type.isSupportAutoTierPolicy()) {
                continue;
            }
            result.add(type.getStorageTypeName());
        }
        return result;
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
        for (BlockVirtualPoolRestRep virtualPool : VirtualPoolUtils.getBlockVirtualPools()) {
            items.add(new VirtualPoolInfo(virtualPool));
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    public static void checkDisconnectedStoragePools(@As(",") String[] ids) {
        List<BlockVirtualPoolRestRep> virtualpools = VirtualPoolUtils.getBlockVirtualPools();
        Set<String> connectedstoragepools = new HashSet<String>();
        List<String> failedArrays = new ArrayList<String>();
        for (BlockVirtualPoolRestRep virtualpool:virtualpools) {
            if(virtualpool.getUseMatchedPools()) {
                for (RelatedResourceRep pool : virtualpool.getMatchedStoragePools()) {
                    connectedstoragepools.add(pool.getId().toString());
                }
            } else {
                for (RelatedResourceRep pool : virtualpool.getAssignedStoragePools()) {
                    connectedstoragepools.add(pool.getId().toString());
                }
            }
        }
        for (String id:ids) {
            StorageSystemRestRep storageSystem =StorageSystemUtils.getStorageSystem(id);
            if (storageSystem != null && !storageSystem.getRegistrationStatus().equals("UNREGISTERED")) {
                boolean found = false;
                List<StoragePoolRestRep> storagepools = StoragePoolUtils.getStoragePools(id);
                for (StoragePoolRestRep storagepool : storagepools) {
                    if (connectedstoragepools.contains(storagepool.getId().toString())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    failedArrays.add(storageSystem.getName());
                }
            }
        }
        renderJSON(failedArrays);
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
        vpool.placementPolicy = VirtualPoolPlacementPolicy.DEFAULT;
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

    public static void createAllFlash() {
    	// This method should only list available storage type discovered
    	Set<String> typesList = new HashSet<String>();
		JsonObject dataObject = getCookieAsJson(GUIDE_DATA);
		JsonArray storage_systems = dataObject.getAsJsonArray(STORAGE_SYSTEMS);

		if(storage_systems != null ) {
			for(Object storageobject : storage_systems) {
				JsonObject storage = (JsonObject) storageobject;
				String storageid = storage.get("id").getAsString();
				StorageSystemRestRep storagesys = StorageSystemUtils.getStorageSystem(storageid);
				if(storagesys != null && !storagesys.getRegistrationStatus().equals("UNREGISTERED")) {
					typesList.add(storagesys.getSystemType());
				}
			}
		}

		renderArgs.put("types", typesList);
		render();
    }

    /**
     * This method create virtual pool according to storage system types
     * @param types : List of storage system types discovered
     */
    public static void createAllFlashAuto(List<String> types) {
		List<String> vaIds4allflash = new ArrayList<String>();
		List<String> vaIds4vmax = new ArrayList<String>();
		List<String> vaIds4xio = new ArrayList<String>();
		List<String> vaIds4unity = new ArrayList<String>();
        JsonArray vpools = new JsonArray();

    	// Read cookies for storage systems and virtual pools
		JsonObject dataObject = getCookieAsJson(GUIDE_DATA);
		if(dataObject != null) {
			JsonArray varrays = dataObject.getAsJsonArray(VARRAYS);
			if (varrays != null) {
				for (Object varray : varrays) {
					JsonObject jsonvarray = (JsonObject) varray;
					String varrayid = jsonvarray.get("id").getAsString();

					// Check virtual array first
					VirtualArrayRestRep varrayRest = VirtualArrayUtils.getVirtualArray(varrayid);
					if (varrayRest != null) {
						// Get virtual array attributes
						Map<String, Set<String>> attributes = VirtualArrayUtils.getAvailableAttributes(uris(varrayid));
						Set<String> system_type = attributes.get(ATTRIBUTE_SYSTEM_TYPES);

						if (system_type != null && !system_type.isEmpty()) {
							if (system_type.size() > 1) {
								vaIds4allflash.add(varrayid);
							}
							else { // It has one system type
								for (String vasystemtype : system_type) {
									if (StringUtils.equals(UNITY, vasystemtype)) {
										vaIds4unity.add(varrayid);
									}
									if (StringUtils.equals(VMAX, vasystemtype)) {
										vaIds4vmax.add(varrayid);
									}
									if (StringUtils.equals(XTREMIO, vasystemtype)) {
										vaIds4xio.add(varrayid);
									}
								}
							}
						}
						else { //Control should not reach here but let add in all flash
							vaIds4allflash.add(varrayid);
						}
					}
				}
			}
		}

		if(!vaIds4allflash.isEmpty()){
			Map<String, String> virtualpoolAllFlashMap = allFlashVirtualPool();
			String vpid = virtualpoolAllFlashMap.get(VP_ALL_FLASH);
			if(vpid != null) {
				BlockVirtualPoolRestRep blockvpool = VirtualPoolUtils.getBlockVirtualPool(vpid);
				List<RelatedResourceRep> virtualarrays = blockvpool.getVirtualArrays();
				for(String vaid:vaIds4allflash) {
					RelatedResourceRep newVaId = new RelatedResourceRep(); 
					newVaId.setId(URI.create(vaid));
					virtualarrays.add(newVaId);
				}
				blockvpool.setVirtualArrays(virtualarrays);
				updateAutoVirtualPool(vpid, blockvpool,vpools);
			}
			else {
				createBaseVPool(VP_ALL_FLASH, StorageSystemTypes.NONE, vaIds4allflash, Messages.get("gettingStarted.vpool.allflash.desc"), vpools);
			}
		}

		if(!vaIds4vmax.isEmpty()) {
			Map<String, String> virtualpoolAllFlashMap = allFlashVirtualPool();
			// Check if we should add in existing compressed vpool
			boolean isCompression = isCompressionEnable(vaIds4vmax);
			String vpid = virtualpoolAllFlashMap.get(VP_VMAX_DIAMOND);
			if(vpid != null && !isCompression) {
				BlockVirtualPoolRestRep blockvpool = VirtualPoolUtils.getBlockVirtualPool(vpid);
				List<RelatedResourceRep> virtualarrays = blockvpool.getVirtualArrays();
				for(String vaid:vaIds4vmax) {
					RelatedResourceRep newVaId = new RelatedResourceRep(); 
					newVaId.setId(URI.create(vaid));
					virtualarrays.add(newVaId);
				}
				blockvpool.setVirtualArrays(virtualarrays);
				updateAutoVirtualPool(vpid, blockvpool,vpools);
			}
			else {
				//Check compressed vpool
				vpid = virtualpoolAllFlashMap.get(VP_VMAX_DIAMOND_COMPRESSED);
				if(vpid != null && isCompression) {
					BlockVirtualPoolRestRep blockvpool = VirtualPoolUtils.getBlockVirtualPool(vpid);
					List<RelatedResourceRep> virtualarrays = blockvpool.getVirtualArrays();
					for(String vaid:vaIds4vmax) {
						RelatedResourceRep newVaId = new RelatedResourceRep(); 
						newVaId.setId(URI.create(vaid));
						virtualarrays.add(newVaId);
					}
					blockvpool.setVirtualArrays(virtualarrays);
					updateAutoVirtualPool(vpid, blockvpool,vpools);
				}
				else {
					createBaseVPool(VP_VMAX_DIAMOND, StorageSystemTypes.VMAX, vaIds4vmax, Messages.get("gettingStarted.vpool.vmax.desc"), vpools);
				}
			}
		}

		if(!vaIds4xio.isEmpty()) {
			Map<String, String> virtualpoolAllFlashMap = allFlashVirtualPool();
			String vpid = virtualpoolAllFlashMap.get(VP_XIO_DIAMOND);
			if(vpid != null) {
				BlockVirtualPoolRestRep blockvpool = VirtualPoolUtils.getBlockVirtualPool(vpid);
				List<RelatedResourceRep> virtualarrays = blockvpool.getVirtualArrays();
				for(String vaid:vaIds4xio) {
					RelatedResourceRep newVaId = new RelatedResourceRep(); 
					newVaId.setId(URI.create(vaid));
					virtualarrays.add(newVaId);
				}
				blockvpool.setVirtualArrays(virtualarrays);
				updateAutoVirtualPool(vpid, blockvpool,vpools);
			}
			else {
				createBaseVPool(VP_XIO_DIAMOND, StorageSystemTypes.XTREMIO, vaIds4xio, Messages.get("gettingStarted.vpool.xio.desc"), vpools);
			}
		}

		if(!vaIds4unity.isEmpty()) {
			Map<String, String> virtualpoolAllFlashMap = allFlashVirtualPool();
			String vpid = virtualpoolAllFlashMap.get(VP_UNITY_DIAMOND);
			if(vpid != null) {
				BlockVirtualPoolRestRep blockvpool = VirtualPoolUtils.getBlockVirtualPool(vpid);
				List<RelatedResourceRep> virtualarrays = blockvpool.getVirtualArrays();
				for(String vaid:vaIds4unity) {
					RelatedResourceRep newVaId = new RelatedResourceRep(); 
					newVaId.setId(URI.create(vaid));
					virtualarrays.add(newVaId);
				}
				blockvpool.setVirtualArrays(virtualarrays);
				updateAutoVirtualPool(vpid, blockvpool,vpools);
			}
			else {
				createBaseVPool(VP_UNITY_DIAMOND, StorageSystemTypes.UNITY, vaIds4unity, Messages.get("gettingStarted.vpool.unity.desc"), vpools);
			}
		}
        dataObject.add(VPOOL_COOKIES, vpools);
        saveJsonAsCookie(GUIDE_DATA, dataObject);
        list();
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
        List<String> autoTierTypes = getSupportAutoTierTypes();
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
        // Virtual Pool has policy, but the policy is not present on given storage system,
        // means, the system been upgraded and the old policy might have have removed.
        // VMAX Cypress to Elm, SLO workload has been dropped
        // Diamond + any WL --> Diamond in Elm
        
        // As the new set of SLO policies on ELM does not have Diamond + WL SLO
        // Add the vPool policy to autoTierPolicyOptions list, 
        // so that the vpool policy would be selected in edit page!!!
        renderArgs.get("autoTierPolicyOptions");
        if(vpool.autoTierPolicy != null && vpool.autoTierPolicy.contains(DIAMOND_SLO)) {
            List<StringOption> options = (List<StringOption>) renderArgs.get("autoTierPolicyOptions");
            StringOption vPoolPolicyName = new StringOption(vpool.autoTierPolicy);
            if(!options.contains(vPoolPolicyName)) {
                options.add(vPoolPolicyName);
            }
        }
        copyRenderArgsToAngular();
        angularRenderArgs().put("vpool", vpool);

        render("@edit", vpool, autoTierTypes);
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

    public static void listStoragePoolsbyIdJson(String id) {
        BlockVirtualPoolRestRep virtualPool = VirtualPoolUtils.getBlockVirtualPool(id);
        if (virtualPool == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            backToReferrer();
        }

        BlockVirtualPoolForm vpool = new BlockVirtualPoolForm();
        vpool.load(virtualPool);
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
            list();
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
            if (param.startsWith("vpool.") && !StringUtils.equalsIgnoreCase(param, "vpool.rpCopiesJson")) {
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
        renderArgs.put("placementPolicyOptions", VirtualPoolPlacementPolicy.options(
                VirtualPoolPlacementPolicy.DEFAULT,
                VirtualPoolPlacementPolicy.ARRAY_AFFINITY
                ));
        renderArgs.put("systemTypeOptions", StorageSystemTypes.getBlockStorageOptions());

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
        renderArgs.put("srdfCopyModeOptions", SRDFCopyMode.OPTIONS);
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

    private static Map<String, String> allFlashVirtualPool() {
    	// Build virtual pool map name:id only for all-flash virtualpool.
    	Map<String, String> virtualpoolAllFlashMap = new HashMap <String, String> ();
    	List<BlockVirtualPoolRestRep> allblockvps = VirtualPoolUtils.getBlockVirtualPools();
    	for(BlockVirtualPoolRestRep blockvp: allblockvps) {
    		if(StringUtils.equalsIgnoreCase(VP_VMAX_DIAMOND, blockvp.getName())) {
    			virtualpoolAllFlashMap.put(VP_VMAX_DIAMOND, blockvp.getId().toString());
    		}
    		if(StringUtils.equalsIgnoreCase(VP_VMAX_DIAMOND_COMPRESSED, blockvp.getName())) {
    			virtualpoolAllFlashMap.put(VP_VMAX_DIAMOND_COMPRESSED, blockvp.getId().toString());
    		}
    		if(StringUtils.equalsIgnoreCase(VP_XIO_DIAMOND, blockvp.getName())) {
    			virtualpoolAllFlashMap.put(VP_XIO_DIAMOND, blockvp.getId().toString());
    		}
    		if(StringUtils.equalsIgnoreCase(VP_UNITY_DIAMOND, blockvp.getName())) {
    			virtualpoolAllFlashMap.put(VP_UNITY_DIAMOND, blockvp.getId().toString());
    		}
    		if(StringUtils.equalsIgnoreCase(VP_ALL_FLASH, blockvp.getName())) {
    			virtualpoolAllFlashMap.put(VP_ALL_FLASH, blockvp.getId().toString());
    		}
    	}
    	return virtualpoolAllFlashMap;
    }

    private static void buildVpoolCookies( String vpoolid, String vpoolname, JsonArray vpools) {
        JsonObject jsonvarray = new JsonObject();
        jsonvarray.addProperty("id", vpoolid);
        jsonvarray.addProperty("name", vpoolname);
        vpools.add(jsonvarray);
    }

    private static void createBaseVPool(String vpoolName, String storageType, List<String> virtualarrayIds, String vpdesc,JsonArray vpools) {
		BlockVirtualPoolForm vpool = new BlockVirtualPoolForm();
		// defaults
		vpool.provisioningType = ProvisioningTypes.THIN;
		vpool.protocols = Sets.newHashSet(BlockProtocols.FC);
		vpool.minPaths = 1;
		vpool.maxPaths = 1;
		vpool.initiatorPaths = 1;
		vpool.expandable = true;
		vpool.rpJournalSizeUnit = SizeUnit.x;
		vpool.rpJournalSize = RPCopyForm.JOURNAL_DEFAULT_MULTIPLIER;
		vpool.rpRpoValue = Long.valueOf(25);
		vpool.rpRpoType = RpoType.SECONDS;
		vpool.protectSourceSite = true;
		vpool.enableAutoCrossConnExport = true;
		vpool.poolAssignment = PoolAssignmentTypes.AUTOMATIC;
		vpool.maxSnapshots = 10;

		vpool.name = vpoolName;
		vpool.systemType = storageType;
		vpool.virtualArrays = virtualarrayIds;
		vpool.description = vpdesc;
		vpool.enableCompression = false;

		// Check if creating a vmax AFA virtual pool, need to set auto-tiering
		if(StringUtils.equals(VMAX, storageType)) {
			vpool.uniqueAutoTierPolicyNames = true;
			boolean isAutoTier = false;
			AutoTierPolicyNamesCall auto_tiering = vpool.autoTierPolicyNames();
			List<String> autoPolicyList = auto_tiering.call();
			for(String policy: autoPolicyList) {
				if(StringUtils.equals(DEFAULT_AUTO_TIER, policy)) {
					vpool.autoTierPolicy = policy;
					isAutoTier = true;
					break;
				}
			}
			if(!isAutoTier) { //This means we did not find the pattern, set random
				for(String policy: autoPolicyList) {
					vpool.autoTierPolicy = policy;
					break;
				}
			}
			for(String virtualArrayId: virtualarrayIds) {
				List<StoragePoolRestRep> spList = StoragePoolUtils.getStoragePoolsAssignedToVirtualArray(virtualArrayId);
				for(StoragePoolRestRep sp: spList) {
					if(sp.getCompressionEnabled() != null && sp.getCompressionEnabled()) {
						vpool.enableCompression = true;
						//rename with compression enable
						vpool.name = VP_VMAX_DIAMOND_COMPRESSED;
						break;
					}
				}
				if(vpool.enableCompression) {
					break;
				}
			}
		}

		BlockVirtualPoolRestRep vpoolTask = vpool.save();
		if (vpoolTask != null) {
            buildVpoolCookies(vpoolTask.getId().toString(), vpool.name,vpools);
		}
    }

    private static boolean isCompressionEnable(List<String> virtualarrayIds) {
    	boolean enableCompression = false;
    	for(String virtualArrayId: virtualarrayIds) {
			List<StoragePoolRestRep> spList = StoragePoolUtils.getStoragePoolsAssignedToVirtualArray(virtualArrayId);
			for(StoragePoolRestRep sp: spList) {
				if(sp.getCompressionEnabled() != null && sp.getCompressionEnabled()) {
					enableCompression = true;
					break;
				}
			}
			if(enableCompression) {
				break;
			}
		}
    	return enableCompression;
    }

    private static void updateAutoVirtualPool(String vpid, BlockVirtualPoolRestRep blockvpool,JsonArray vpools) {
		BlockVirtualPoolForm vpool = new BlockVirtualPoolForm();
		vpool.load(blockvpool);
		blockvpool = vpool.save();
		if (blockvpool != null) {
            buildVpoolCookies(vpid, vpool.name,vpools);
		}
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
