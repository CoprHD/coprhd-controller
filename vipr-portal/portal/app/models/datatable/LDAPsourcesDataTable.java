/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.datatable;

import util.AuthSourceType;
import util.MessagesUtils;
import util.datatable.DataTable;

import com.emc.storageos.model.auth.AuthnProviderRestRep;


public class LDAPsourcesDataTable extends DataTable {

    public LDAPsourcesDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("mode");  
        addColumn("domains");
        addColumn("disabled").setRenderFunction("renderBoolean");
        setDefaultSort("name", "asc");
    }

    public static class LDAPsourcesInfo {
        public String id;
        public String name;
        public String mode;
        public String domains;
        public Boolean disabled;


        public LDAPsourcesInfo() {
        }

        public LDAPsourcesInfo(AuthnProviderRestRep ldapSources) {
            this.id = ldapSources.getId().toString();
            this.name = ldapSources.getName();  
            this.mode = MessagesUtils.get("AuthSourceType."+AuthSourceType.valueOf(ldapSources.getMode()));
           
            StringBuilder doms = new StringBuilder();
            for (String dom : ldapSources.getDomains()) {
                if (doms.length() > 0) { 
                	doms.append(", ");
                }
                doms.append(dom);
            } 
            this.domains = doms.toString();
            this.disabled = !ldapSources.getDisable();
            
        }
    }
}
