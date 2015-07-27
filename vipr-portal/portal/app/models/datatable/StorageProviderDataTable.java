/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import models.StorageProviderTypes;

import org.apache.commons.lang.StringUtils;

import util.datatable.DataTable;

import com.emc.storageos.model.smis.StorageProviderRestRep;

import controllers.Common;
import controllers.arrays.ScaleIO;

public class StorageProviderDataTable extends DataTable {
    public static final String SUPPORTED_SCALEIO_DASHBOARD_VERSION = "1.21";
    public static final String SUPPORTED_SCALEIO_JNLP_VERSION = "1.3";
    public static final String SCALEIO_JNLP_URL = "%s/dashboard.jnlp";

    public StorageProviderDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("host");
        addColumn("interfaceType");
        addColumn("userName").hidden();
        StorageProviderInfo.addDiscoveryColumns(this);
        addColumn("manageUrl").setRenderFunction("render.manageUrl");
        sortAllExcept("manageUrl");
        this.setDefaultSort("name", "asc");
    }

    public static class StorageProviderInfo extends DiscoveredSystemInfo {
        public String id;
        public String name;
        public String host;
        public String interfaceType;
        public String version;
        public String userName;
        public String manageUrl;

        public StorageProviderInfo() {
        }

        public StorageProviderInfo(StorageProviderRestRep storageProvider) {
            super(storageProvider);
            this.id = storageProvider.getId().toString();
            this.name = storageProvider.getName();
            this.host = StringUtils.defaultIfEmpty(storageProvider.getIPAddress(), "0.0.0.0");
            this.interfaceType = StorageProviderTypes.getDisplayValue(storageProvider.getInterface());
            this.version = StringUtils.defaultIfEmpty(storageProvider.getVersionString(), "N/A");
            this.userName = storageProvider.getUserName();
            this.manageUrl = getManageUrl(storageProvider);
        }
    }

    private static String getManageUrl(StorageProviderRestRep storageProvider) {
    	String manageUrl = storageProvider.getElementManagerURL();
    	String prefix = !StringUtils.isEmpty(manageUrl) && !manageUrl.matches("^(https?)://.*$") ? "https://" : "";
        if (StorageProviderTypes.SCALEIO.equals(storageProvider.getInterface())&&!StringUtils.isEmpty(manageUrl)) {
            if (StringUtils.startsWith(storageProvider.getVersionString(), SUPPORTED_SCALEIO_DASHBOARD_VERSION)) {
                return Common.reverseRoute(ScaleIO.class, "dashboard", "id", storageProvider.getId());
            } else if (StringUtils.startsWith(storageProvider.getVersionString(), SUPPORTED_SCALEIO_JNLP_VERSION)) {
            	return String.format(SCALEIO_JNLP_URL, prefix + manageUrl);
            }
        } else if (!StringUtils.isEmpty(manageUrl)) {
        	return prefix + manageUrl;
        }
        
        return "";
    }
}
