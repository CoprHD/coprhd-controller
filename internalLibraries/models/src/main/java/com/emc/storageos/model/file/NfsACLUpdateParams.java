/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class NfsACLUpdateParams implements Serializable {

    private static final long serialVersionUID = 3196927504346045761L;

    /*
     * Payload attributes
     */
    protected List<NfsACE> acesToAdd;
    protected List<NfsACE> acesToModify;
    protected List<NfsACE> acesToDelete;

    protected String subDir;
    protected List<String> inheritFlags;

    // Valid values for NFSv4 inherit ACL flag
    public enum NFSInheritFlag {
        object_inherit, container_inherit, no_prop_inherit, inherit_only, inherited_ace;
    }
    public NfsACLUpdateParams() {

    }

    @XmlElementWrapper(name = "add")
    @XmlElement(name = "ace")
    public List<NfsACE> getAcesToAdd() {
        return acesToAdd;
    }

    public void setAcesToAdd(List<NfsACE> acesToAdd) {
        this.acesToAdd = acesToAdd;
    }

    @XmlElementWrapper(name = "modify")
    @XmlElement(name = "ace")
    public List<NfsACE> getAcesToModify() {
        return acesToModify;
    }

    public void setAcesToModify(List<NfsACE> acesToModify) {
        this.acesToModify = acesToModify;
    }

    @XmlElementWrapper(name = "delete")
    @XmlElement(name = "ace")
    public List<NfsACE> getAcesToDelete() {
        return acesToDelete;
    }

    public void setAcesToDelete(List<NfsACE> acesToDelete) {
        this.acesToDelete = acesToDelete;
    }

    @XmlElement(name = "subDir", required = false)
    public String getSubDir() {
        return subDir;
    }

    public void setSubDir(String subDir) {
        this.subDir = subDir;
    }


    /**
     * List of inheritFlags for the file system NFS ACL.
     */
    @XmlElementWrapper(required = false, name = "inheritFlags")
    @XmlElement(name = "inheritFlag")
    public List<String> getInheritFlags() {
        if (inheritFlags == null) {
            inheritFlags = new ArrayList<String>();
        }
        return inheritFlags;
    }

    public void setInheritFlags(List<String> inheritFlags) {
        this.inheritFlags = inheritFlags;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NfsACLUpdateParams [Subdirectory=");
        builder.append(subDir);
        builder.append(", inheritFlags=");
        builder.append(inheritFlags);
        builder.append(", acesToAdd=");
        builder.append(acesToAdd);
        builder.append(", acesToModify=");
        builder.append(acesToModify);
        builder.append(", acesToDelete=");
        builder.append(acesToDelete);
        builder.append("]");
        return builder.toString();
    }

    public List<NfsACE> retrieveAllACL() {

        List<NfsACE> aclList = new ArrayList<NfsACE>();

        if (acesToAdd != null && !acesToAdd.isEmpty()) {
            aclList.addAll(acesToAdd);
        }

        if (acesToModify != null && !acesToModify.isEmpty()) {
            aclList.addAll(acesToModify);
        }

        if (acesToDelete != null && !acesToDelete.isEmpty()) {
            aclList.addAll(acesToDelete);
        }
        return aclList;
    }

}
