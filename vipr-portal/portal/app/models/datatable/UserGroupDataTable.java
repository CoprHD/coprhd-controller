/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package models.datatable;

import com.emc.storageos.model.usergroup.UserAttributeParam;
import com.emc.storageos.model.usergroup.UserGroupRestRep;
import controllers.util.ViprResourceController;
import util.datatable.DataTable;

public class UserGroupDataTable extends DataTable {

    public UserGroupDataTable() {
        addColumn("name").setRenderFunction("render.editableLink");
        addColumn("domain");
        addColumn("attributes");
        setDefaultSort("name", "asc");
    }

    public static class UserGroupInfo extends ViprResourceController {
        public String id;
        public String name;
        public String domain;
        public String attributes;
        public boolean editable;

        public UserGroupInfo() {
        }

        public UserGroupInfo(UserGroupRestRep userGroupRestRep, boolean editable) {
            this.id = userGroupRestRep.getId().toString();
            this.name = userGroupRestRep.getName();
            this.domain = userGroupRestRep.getDomain();
            this.editable = editable;

            StringBuilder ss = getAttributesStringBuilder(userGroupRestRep);
            this.attributes = ss.toString();
        }

        /**
         * Build the stringBuilder in the format of attribute1 = [values]; attribute2 = [values]
         * for the set of attributes returned.
         * 
         * @param userGroupRestRep
         * @return string builder in the above format.
         */
        private StringBuilder getAttributesStringBuilder(UserGroupRestRep userGroupRestRep) {
            StringBuilder ss = new StringBuilder();
            for (UserAttributeParam param : userGroupRestRep.getAttributes()) {
                ss.append(param.getKey());
                ss.append(" = ");
                ss.append(param.getValues().toString());
                ss.append("; ");
            }
            return ss;
        }
    }
}