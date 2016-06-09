/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import util.EnumOption;
import util.StorageSystemTypeUtils;
import util.StringOption;

import com.emc.storageos.db.server.impl.StorageSystemTypesInitUtils;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.google.common.collect.Lists;

public class StorageSystemTypes {
    private static final String OPTION_PREFIX = "StorageSystemType";
    public static final String NONE = "NONE";
    public static final String ISILON = "isilon";
    public static final String VNX_BLOCK = "vnxblock";
    public static final String VNXe = "vnxe";
    public static final String VNX_FILE = "vnxfile";
    public static final String VMAX = "vmax";
    public static final String NETAPP = "netapp";
    public static final String NETAPPC = "netappc";
    public static final String HITACHI = "hds";
    public static final String IBMXIV = "ibmxiv";
    public static final String VPLEX = "vplex";
    public static final String OPENSTACK = "openstack";
    public static final String SCALEIO = "scaleio";
    public static final String SCALEIOAPI = "scaleioapi";
    public static final String XTREMIO = "xtremio";
    public static final String DATA_DOMAIN = "datadomain";
    public static final String ECS = "ecs";

    public static final String STORAGE_PROVIDER_VMAX = "STORAGE_PROVIDER.vmax";
    public static final String STORAGE_PROVIDER_HITACHI = "STORAGE_PROVIDER.hds";
    public static final String STORAGE_PROVIDER_VPLEX = "STORAGE_PROVIDER.vplex";
    public static final String STORAGE_PROVIDER_OPENSTACK = "STORAGE_PROVIDER.cinder";
    public static final String STORAGE_PROVIDER_SCALEIO = "STORAGE_PROVIDER.scaleio";
    public static final String STORAGE_PROVIDER_SCALEIOAPI = "STORAGE_PROVIDER.scaleioapi";
    public static final String STORAGE_PROVIDER_DATA_DOMAIN = "STORAGE_PROVIDER.ddmc";
    public static final String STORAGE_PROVIDER_IBMXIV = "STORAGE_PROVIDER.ibmxiv";
    public static final String STORAGE_PROVIDER_XTREMIO = "STORAGE_PROVIDER.xtremio";

    public static final String[] BLOCK_TYPES = { VMAX, VNX_BLOCK, VPLEX,
            HITACHI, OPENSTACK, SCALEIO, SCALEIOAPI, XTREMIO, VNXe, IBMXIV };
    public static final String[] FILE_TYPES = { ISILON, VNX_FILE, NETAPP,
            DATA_DOMAIN, VNXe, NETAPPC };
    public static final String[] STORAGE_PROVIDER_TYPES = { VMAX, VNX_BLOCK,
            HITACHI, VPLEX, OPENSTACK, SCALEIO, SCALEIOAPI, DATA_DOMAIN,
            IBMXIV, XTREMIO };
    public static final String[] NON_SMIS_TYPES = { ISILON, VNX_FILE, NETAPP,
            XTREMIO, VNXe, NETAPPC, ECS };

    public static boolean isNone(String type) {
        return NONE.equals(type);
    }

    public static boolean isIsilon(String type) {
        return ISILON.equals(type);
    }

    public static boolean isVnxBlock(String type) {
        return VNX_BLOCK.equals(type);
    }

    public static boolean isVnxFile(String type) {
        return VNX_FILE.equals(type);
    }

    public static boolean isVmax(String type) {
        return VMAX.equals(type);
    }

    public static boolean isNetapp(String type) {
        return NETAPP.equals(type);
    }

    public static boolean isNetappc(String type) {
        return NETAPPC.equals(type);
    }

    public static boolean isVplex(String type) {
        return VPLEX.equals(type);
    }

    public static boolean isScaleIO(String type) {
        return SCALEIO.equals(type);
    }

    public static boolean isScaleIOApi(String type) {
        return SCALEIOAPI.equals(type);
    }

    public static boolean isXtremIO(String type) {
        return XTREMIO.equals(type);
    }

    public static boolean isVNXe(String type) {
        return VNXe.equals(type);
    }

    public static boolean isECS(String type) {
        return ECS.equals(type);
    }

    public static boolean isFileStorageSystem(String type) {
        return contains(FILE_TYPES, type);
    }

    public static boolean isBlockStorageSystem(String type) {
        return contains(BLOCK_TYPES, type);
    }

    public static boolean isStorageProvider(String type) {
        return contains(STORAGE_PROVIDER_TYPES, type);
    }

    private static boolean contains(String[] systemTypes, String type) {
        for (String systemType : systemTypes) {
            if (systemType.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static StringOption option(String type) {
        return new StringOption(type, getDisplayValue(type));
    }

    public static List<StringOption> options(String... types) {
        List<StringOption> options = Lists.newArrayList();
        for (String type : types) {
            options.add(option(type));
        }
        return options;
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, OPTION_PREFIX);
    }

    public static List<StringOption> getStorageOption() {
        String alltypes = "all";
        HashMap<String, String> arrayProviderMap = StorageSystemTypesInitUtils.arrToProviderDsiplayName();
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);

        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            // Add all storage systems plus VPLEX, SCALEIO, IBMXIV, XTREMIO
            if (!storagetypeRest.getIsSmiProvider() || StringUtils.equals(VPLEX, storagetypeRest.getStorageTypeName())
                    || StringUtils.equals(SCALEIOAPI, storagetypeRest.getStorageTypeName())
                    || StringUtils.equals(IBMXIV, storagetypeRest.getStorageTypeName())
                    || StringUtils.equals(XTREMIO, storagetypeRest.getStorageTypeName())) {

                if (null != arrayProviderMap.get(storagetypeRest.getStorageTypeName())) {
                    allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(), arrayProviderMap.get(storagetypeRest
                            .getStorageTypeName())));
                }
                else {
                    if (!StringUtils.equals(VNX_BLOCK, storagetypeRest.getStorageTypeName())) { // VNX block is covered by VMAX
                        allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(), storagetypeRest.getStorageTypeDispName()));
                    }
                }
            }
        }
        return allproviders;
    }

    public static List<StringOption> getBlockStorageOptions() {
        String alltypes = "all";
        List<StringOption> storageoptions = new ArrayList<StringOption>();
        // Add NONE option
        storageoptions.add(new StringOption("NONE", "none"));

        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getStorageTypeType().equalsIgnoreCase("block")) {
                if (storagetypeRest.getIsSmiProvider() && (StringUtils.equals(SCALEIO, storagetypeRest.getStorageTypeName()))
                        || StringUtils.equals(IBMXIV, storagetypeRest.getStorageTypeName())
                        || StringUtils.equals(XTREMIO, storagetypeRest.getStorageTypeName())) {
                    storageoptions.add(new StringOption(storagetypeRest.getStorageTypeName(), storagetypeRest.getStorageTypeDispName()));
                }
                else {
                    storageoptions.add(new StringOption(storagetypeRest.getStorageTypeName(), storagetypeRest.getStorageTypeDispName()));
                }
            }
        }
        return storageoptions;
    }

    public static List<StringOption> getFileStorageOptions() {
        String alltypes = "all";
        List<StringOption> storageoptions = new ArrayList<StringOption>();
        // Add NONE option
        storageoptions.add(new StringOption("NONE", "none"));
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist
                .getStorageSystemTypes()) {
            if (storagetypeRest.getStorageTypeType().equalsIgnoreCase("file") && !storagetypeRest.getIsSmiProvider()) {
                storageoptions.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
            }
        }
        return storageoptions;
    }

    public static List<StringOption> getObjectStorageOptions() {
        String alltypes = "all";
        List<StringOption> storageoptions = new ArrayList<StringOption>();
        // Add NONE option
        storageoptions.add(new StringOption("NONE", "none"));
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils
                .getAllStorageSystemTypes(alltypes);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist
                .getStorageSystemTypes()) {
            if (storagetypeRest.getStorageTypeType().equalsIgnoreCase("object")) {
                storageoptions.add(new StringOption(storagetypeRest
                        .getStorageTypeName(), storagetypeRest
                        .getStorageTypeDispName()));
            }
        }
        return storageoptions;
    }

    public static List<StringOption> getProvidersWithSSL() {
        String alltypes = "all";
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils
                .getAllStorageSystemTypes(alltypes);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist
                .getStorageSystemTypes()) {
            if (storagetypeRest.getIsDefaultSsl()) {
                allproviders.add(new StringOption(storagetypeRest
                        .getStorageTypeName(), storagetypeRest
                        .getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static List<StringOption> getProvidersWithoutSSL() {
        String alltypes = "all";
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils
                .getAllStorageSystemTypes(alltypes);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist
                .getStorageSystemTypes()) {
            if (!storagetypeRest.getIsDefaultSsl()) {
                allproviders.add(new StringOption(storagetypeRest
                        .getStorageTypeName(), storagetypeRest
                        .getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static List<StringOption> getProvidersWithMDM() {
        String alltypes = "all";
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils
                .getAllStorageSystemTypes(alltypes);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist
                .getStorageSystemTypes()) {
            if (storagetypeRest.getIsDefaultMDM()) {
                allproviders.add(new StringOption(storagetypeRest
                        .getStorageTypeName(), storagetypeRest
                        .getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static List<StringOption> getProvidersWithOnlyMDM() {
        String alltypes = "all";
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils
                .getAllStorageSystemTypes(alltypes);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist
                .getStorageSystemTypes()) {
            if (storagetypeRest.getIsOnlyMDM()) {
                allproviders.add(new StringOption(storagetypeRest
                        .getStorageTypeName(), storagetypeRest
                        .getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static List<StringOption> getProvidersWithEMS() {
        String alltypes = "all";
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils
                .getAllStorageSystemTypes(alltypes);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist
                .getStorageSystemTypes()) {
            if (storagetypeRest.getIsElementMgr()) {
                allproviders.add(new StringOption(storagetypeRest
                        .getStorageTypeName(), storagetypeRest
                        .getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

}
