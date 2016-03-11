/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.EnumOption;
import util.StorageSystemTypeUtils;
import util.StringOption;

import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.google.common.collect.Lists;

public class StorageProviderTypes {
	private static final String OPTION_PREFIX = "storageProvider.interfaceType";
	public static final String HITACHI = "hicommand";
	public static final String SMIS = "smis";
	public static final String VPLEX = "vplex";
	public static final String CINDER = "cinder";
	public static final String SCALEIO = "scaleio";
	public static final String SCALEIOAPI = "scaleioapi";
	public static final String DATA_DOMAIN = "ddmc";
	public static final String IBMXIV = "ibmxiv";
	public static final String XTREMIO = "xtremio";

	// public static final StringOption[] OPTIONS = {
	// option(SMIS),
	// option(HITACHI),
	// option(VPLEX),
	// option(CINDER),
	// option(SCALEIOAPI),
	// option(DATA_DOMAIN),
	// option(IBMXIV),
	// option(XTREMIO)
	// };

	// public static final StringOption[] optionSIO = {
	// option(SCALEIO),
	// option(SCALEIOAPI)
	// };

	// public static final StringOption[] SSL_DEFAULT_OPTIONS =
	// StringOption.options(new String[] { SMIS, SCALEIOAPI, VPLEX, IBMXIV },
	// OPTION_PREFIX);

	private static final Map<String, String> fromStorageArrayTypeMap = new HashMap<String, String>() {
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
		}
	};

	public static boolean isScaleIOApi(String type) {
		return SCALEIOAPI.equals(type);
	}

	public static String fromStorageArrayType(String storageArrayType) {
		return fromStorageArrayTypeMap.get(storageArrayType);
	}

	// public static StringOption option(String type) {
	// return new StringOption(type, getDisplayValue(type));
	// }

	// public static List<StringOption> options(String... types) {
	// List<StringOption> options = Lists.newArrayList();
	// for (String type : types) {
	// options.add(option(type));
	// }
	// return options;
	// }

	public static String getDisplayValue(String type) {
		return StringOption.getDisplayValue(type, OPTION_PREFIX);
	}

	public static List<StringOption> getProviderOption() {
		String alltypes = "all";
		List<StringOption> allproviders = new ArrayList<StringOption>();
		StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
		for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
			if (storagetypeRest.getIsSmiProvider()) {
				allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
						storagetypeRest.getStorageTypeDispName()));
			}
		}
		return allproviders;
	}

	public static List<StringOption> getProvidersWithSSL() {
		String alltypes = "all";
		List<StringOption> allproviders = new ArrayList<StringOption>();
		StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
		for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
			if (storagetypeRest.getIsDefaultSsl()) {
				allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
						storagetypeRest.getStorageTypeDispName()));
			}
		}

		return allproviders;
	}

	public static List<StringOption> getProvidersWithoutSSL() {
		String alltypes = "all";
		List<StringOption> allproviders = new ArrayList<StringOption>();
		StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
		for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
			if (!storagetypeRest.getIsDefaultSsl()) {
				allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
						storagetypeRest.getStorageTypeDispName()));
			}
		}

		return allproviders;
	}

	public static List<StringOption> getProvidersWithMDM() {
		String alltypes = "all";
		List<StringOption> allproviders = new ArrayList<StringOption>();
		StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
		for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
			if (storagetypeRest.getIsDefaultMDM()) {
				allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
						storagetypeRest.getStorageTypeDispName()));
			}
		}

		return allproviders;
	}

	public static List<StringOption> getProvidersWithOnlyMDM() {
		String alltypes = "all";
		List<StringOption> allproviders = new ArrayList<StringOption>();
		StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
		for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
			if (storagetypeRest.getIsOnlyMDM()) {
				allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
						storagetypeRest.getStorageTypeDispName()));
			}
		}

		return allproviders;
	}

	public static List<StringOption> getProvidersWithEMS() {
		String alltypes = "all";
		List<StringOption> allproviders = new ArrayList<StringOption>();
		StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
		for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
			if (storagetypeRest.getIsElementMgr()) {
				allproviders.add(new StringOption(storagetypeRest.getStorageTypeName(),
						storagetypeRest.getStorageTypeDispName()));
			}
		}

		return allproviders;
	}

	public static List<EnumOption> getStorageProviderPortMap() {
		String alltypes = "all";
		List<EnumOption> StorageProviderPortMap = new ArrayList<EnumOption>();

		StorageSystemTypeList storagetypelist = StorageSystemTypeUtils.getAllStorageSystemTypes(alltypes);
		for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
			StorageProviderPortMap
					.add(new EnumOption(storagetypeRest.getStorageTypeName(), storagetypeRest.getNonSslPort()));

			StorageProviderPortMap.add(
					new EnumOption(storagetypeRest.getStorageTypeName() + "_useSSL", storagetypeRest.getSslPort()));
		}

		return StorageProviderPortMap;
	}

}
