/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class FileExportUpdateParams implements Serializable {

    private static final long serialVersionUID = 5116290126820732256L;

    protected ExportRules exportRulesToAdd;
    protected ExportRules exportRulesToDelete;
    protected ExportRules exportRulesToModify;

    // Non payload models - for internal use only
    protected String subDir;

    public enum ExportOperationType {
        ADD, MODIFY, DELETE
    }

    public enum ExportOperationErrorType {
        INVALID_SECURITY_TYPE, INVALID_ANON, EXPORT_NOT_FOUND, NO_ERROR, EXPORT_EXISTS, NO_HOSTS_FOUND,
        MULTIPLE_EXPORTS_WITH_SAME_SEC_FLAVOR,
        SNAPSHOT_EXPORT_SHOULD_BE_READ_ONLY
    }

    public enum ExportSecurityType {
        SYS, KRB5, KRB5I, KRB5P
    }

    /**
     * Default Constructor
     */
    public FileExportUpdateParams() {
    }

    @XmlElement(name = "add", required = false)
    public ExportRules getExportRulesToAdd() {
        return exportRulesToAdd;
    }

    /**
     * List of exportRules to be added
     * 
     * @param exportRulesToAdd
     */

    public void setExportRulesToAdd(ExportRules exportRulesToAdd) {
        this.exportRulesToAdd = exportRulesToAdd;
    }

    @XmlElement(name = "delete", type = ExportRules.class)
    public ExportRules getExportRulesToDelete() {
        return exportRulesToDelete;
    }

    /**
     * List of exportRules to be deleted
     * 
     * @param exportRulesToAdd
     */
    public void setExportRulesToDelete(ExportRules exportRulesToDelete) {
        this.exportRulesToDelete = exportRulesToDelete;
    }

    @XmlElement(name = "modify", required = false)
    public ExportRules getExportRulesToModify() {
        return exportRulesToModify;
    }

    /**
     * List of exportRules to be modified
     * 
     * @param exportRulesToAdd
     */
    public void setExportRulesToModify(ExportRules exportRulesToModify) {
        this.exportRulesToModify = exportRulesToModify;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("Number of ");
        sb.append("[ Add Rule(s) : ").append(
                (exportRulesToAdd != null) ? exportRulesToAdd.getExportRules()
                        .size() : 0);

        sb.append(" Delete Rule(s) : ").append(
                (exportRulesToDelete != null) ? exportRulesToDelete
                        .getExportRules().size() : 0);

        sb.append(" Modify Rule(s) : ").append(
                (exportRulesToModify != null) ? exportRulesToModify
                        .getExportRules().size() : 0);

        sb.append(" ]");
        return sb.toString();

    }

    public String getSubDir() {
        return subDir;
    }

    public void setSubDir(String subDir) {
        this.subDir = subDir;
    }

    public List<ExportRule> retrieveAllExports() {

        List<ExportRule> list = new ArrayList<>();
        if (exportRulesToAdd != null && exportRulesToAdd.getExportRules() != null
                && !exportRulesToAdd.getExportRules().isEmpty()) {
            list.addAll(exportRulesToAdd.getExportRules());
        }

        if (exportRulesToModify != null && exportRulesToModify.getExportRules() != null
                && !exportRulesToModify.getExportRules().isEmpty()) {
            list.addAll(exportRulesToModify.getExportRules());
        }

        if (exportRulesToDelete != null && exportRulesToDelete.getExportRules() != null
                && !exportRulesToDelete.getExportRules().isEmpty()) {
            list.addAll(exportRulesToDelete.getExportRules());
        }

        return list;
    }

}