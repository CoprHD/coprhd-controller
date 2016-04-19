/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.Map;

import util.datatable.DataTable;

import com.google.common.collect.Maps;

import controllers.resources.ObjectBuckets.BucketACLForm;

public class BucketACLDataTable extends DataTable {

    public BucketACLDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("domain");
        addColumn("type");
        addColumn("permission");
        sortAll();
        setDefaultSortField("name");
    }

    public static class AclInfo {

        public String id;
        public String name;
        public String domain;
        public String type;
        public String permission;
        public String bucketId;

        public AclInfo(String name, String type, String permission, String bucketId, String domain) {
            this.name = name;
            this.domain = domain;
            this.type = type;
            this.permission = formatPermissions(permission);
            this.bucketId = bucketId;
            id = BucketACLForm.createId(this.name, this.type, this.bucketId, this.domain, permission);
        }

        private String formatPermissions(String permission) {
            StringBuffer formatedValues = new StringBuffer("");
            if (!permission.isEmpty()) {
                Map<String, String> permissionMap = getPermissionsMap();
                String[] permArray = permission.split("\\|");
                for (String acePermission : permArray) {
                    formatedValues.append(permissionMap.get(acePermission)).append(", ");
                }
            }

            String stringToReturn = "";
            stringToReturn = formatedValues.toString();
            if (!stringToReturn.isEmpty() && stringToReturn.length() > 2) {
                stringToReturn = stringToReturn.substring(0, stringToReturn.length() - 2);
            }
            return stringToReturn;
        }

        private Map<String, String> getPermissionsMap() {
            Map<String, String> permissionMap = Maps.newHashMap();
            permissionMap.put("read", "Read");
            permissionMap.put("write", "Write");
            permissionMap.put("execute", "Execute");
            permissionMap.put("full_control", "Full Control");
            permissionMap.put("delete", "Delete");
            permissionMap.put("none", "None");
            permissionMap.put("privileged_write", "Privileged Write");
            permissionMap.put("read_acl", "Read Acl");
            permissionMap.put("write_acl", "Write Acl");
            return permissionMap;
        }
    }

}
