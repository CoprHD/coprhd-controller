/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;

import util.EnumOption;
import util.StorageSystemTypeUtils;
import util.StringOption;

public class StorageProviderTypes {
    private static final String OPTION_PREFIX = "storageProvider.interfaceType";
    private static final String BLOCK_TYPE = "block";
    private static final String FILE_TYPE = "file";
    private static final String OBJECT_TYPE = "object";
    private static final String ALL_TYPE = "all";

    public static final String HITACHI = "hicommand";
    public static final String SMIS = "smis";
    public static final String VPLEX = "vplex";
    public static final String CINDER = "cinder";
    public static final String SCALEIO = "scaleio";
    public static final String SCALEIOAPI = "scaleioapi";
    public static final String DATA_DOMAIN = "ddmc";
    public static final String IBMXIV = "ibmxiv";
    public static final String XTREMIO = "xtremio";
    public static final String CEPH = "ceph";
    public static final String DELLPROVIDER = "dellscprovider";

    public static final Map<String, String> fromStorageArrayTypeMap = new HashMap<String, String>() {
        private static final long serialVersionUID = -8628274587467033626L;

        {
            for (String storageSystemType : StorageSystemTypes.STORAGE_PROVIDER_TYPES) {
                put(storageSystemType, SMIS);
            }
            put(StorageSystemTypes.HITACHI, HITACHI);
            put(StorageSystemTypes.VPLEX, VPLEX);
            put(StorageSystemTypes.OPENSTACK, CINDER);
            put(StorageSystemTypes.SCALEIO, SCALEIO);
            put(StorageSystemTypes.SCALEIOAPI, SCALEIOAPI);
            put(StorageSystemTypes.DATA_DOMAIN, DATA_DOMAIN);
            put(StorageSystemTypes.IBMXIV, IBMXIV);
            put(StorageSystemTypes.XTREMIO, XTREMIO);
            put(StorageSystemTypes.CEPH, CEPH);
            put(StorageSystemTypes.DELLSCSYSTEM, DELLPROVIDER);
        }
    };

    public static boolean isXIV(String type) {
        return IBMXIV.equals(type);
    }
    
    public static boolean isScaleIOApi(String type) {
    	return SCALEIOAPI.equals(type);
    }

    public static boolean isCeph(String type) {
    	return CEPH.equals(type);
    }

    public static String fromStorageArrayType(String storageArrayType) {
        return fromStorageArrayTypeMap.get(storageArrayType);
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, OPTION_PREFIX);
    }

    public static List<StringOption> getProviderOption() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getIsSmiProvider()) {
                if (!StringUtils.equals(SCALEIO, storagetypeRest.getStorageTypeName())) {
                    allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                            storagetypeRest.getStorageTypeDispName()));
                }
            }
        }
        return allproviders;
    }

    public static List<StringOption> getAllFlashProviderOption() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getIsSmiProvider()) {
                if (StringUtils.equals(SMIS, storagetypeRest.getStorageTypeName()) || StringUtils.equals(XTREMIO, storagetypeRest.getStorageTypeName()) ) {
                    allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(), storagetypeRest.getStorageTypeDispName()));
                }
            }
        }
        Collections.sort(allproviders);
        return allproviders;
    }

    public static List<StringOption> getProvidersWithSSL() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getIsDefaultSsl()) {
                allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
    }
}

        return allproviders;
    }

    public static List<StringOption> getProvidersWithoutSSL() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (!storagetypeRest.getIsDefaultSsl()) {
                allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static List<StringOption> getProvidersWithMDM() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getIsDefaultMDM()) {
                allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static List<StringOption> getProvidersWithOnlyMDM() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getIsOnlyMDM()) {
                allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static List<StringOption> getProvidersWithEMS() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getIsElementMgr()) {
                allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static Object getProvidersWithSecretKey() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getIsSecretKey()) {
                allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
            }
        }

        return allproviders;
    }

    public static List<EnumOption> getStoragePortMap() {
        List<EnumOption> StorageProviderPortMap = new ArrayList<EnumOption>();

        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            StorageProviderPortMap
                    .add(new EnumOption(storagetypeRest.getStorageTypeName(), storagetypeRest.getNonSslPort()));

            StorageProviderPortMap.add(
                    new EnumOption(storagetypeRest.getStorageTypeName() + "_useSSL", storagetypeRest.getSslPort()));
        }

        return StorageProviderPortMap;
    }

    public static List<EnumOption> getSupportedStorageType() {
        List<EnumOption> supportedStorageType = new ArrayList<EnumOption>();
        supportedStorageType.add(new EnumOption(BLOCK_TYPE, BLOCK_TYPE));
        supportedStorageType.add(new EnumOption(FILE_TYPE, FILE_TYPE));
        supportedStorageType.add(new EnumOption(OBJECT_TYPE, OBJECT_TYPE));
        supportedStorageType.add(new EnumOption(ALL_TYPE, ALL_TYPE));
        return supportedStorageType;
    }

    public static List<StringOption> getScaleIoOption() {
        List<StringOption> allproviders = new ArrayList<StringOption>();
        StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(ALL_TYPE);
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getStorageTypeName().equals(SCALEIO)) {
                allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
            }
            if (storagetypeRest.getStorageTypeName().equals(SCALEIOAPI)) {
                allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
                        storagetypeRest.getStorageTypeDispName()));
            }
        }
        return allproviders;
    }

}
