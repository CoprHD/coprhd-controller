/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.FileExportUpdateParams.ExportOperationErrorType;
import com.emc.storageos.model.file.FileExportUpdateParams.ExportOperationType;
import com.emc.storageos.model.file.FileExportUpdateParams.ExportSecurityType;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class ExportVerificationUtility {

    private static final Logger _log = LoggerFactory
            .getLogger(ExportVerificationUtility.class);
    private DbClient _dbClient;
    private FileShare fs;
    private Snapshot snapshot;
    private FileExportUpdateParams param;
    public static final String SEC_TYPE = "secType";
    public static final String ANON_TYPE = "anon";
    public static final String NO_HOSTS_FOUND = "hosts";

    private List<String> secFlavorsFound = new ArrayList<>();
    private String invalidXMLElementErrorToReport;
    private List<URI> allSavedURIs = new ArrayList<>();

    public ExportVerificationUtility(DbClient dbClient) {
        _dbClient = dbClient;
        invalidXMLElementErrorToReport = null;
    }

    public void saveAndRetrieveExportURIs(List<ExportRule> listExportRule, ExportOperationType type) {

        // FileExportRule

        if (listExportRule == null) {
            return;
        }

        for (ExportRule exportRule : listExportRule) {
            if (exportRule.isToProceed())
            {
                FileExportRule rule = new FileExportRule();
                copyPropertiesToSave(rule, exportRule);
                rule.setOpType(type.name());
                URI uri = URIUtil.createId(FileExportRule.class);
                rule.setId(uri);
                _log.debug("Saving Object {}", rule.toString());
                _dbClient.createObject(rule);
                allSavedURIs.add(uri);
            }
            continue;
        }

    }

    private void copyPropertiesToSave(FileExportRule dest, ExportRule orig) {

        if (snapshot != null) {
            dest.setSnapshotId(snapshot.getId());
            dest.setExportPath(snapshot.getPath());
        } else {
            dest.setFileSystemId(fs.getId());
            dest.setExportPath(fs.getPath());
        }
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        dest.setReadOnlyHosts(new StringSet(dest.getReadOnlyHosts()));
        dest.setReadWriteHosts(new StringSet(dest.getReadWriteHosts()));
        dest.setRootHosts(new StringSet(dest.getRootHosts()));

    }

    /**
     * Since, Modifying an export is not allowed This method verifies the
     * existing export params with the new one issued to modify.
     * 
     * @param fs
     * @param param
     */

    public void verifyExports(FileShare fileShare, Snapshot snapshot, FileExportUpdateParams fsParam)
            throws Exception {

        fs = fileShare;
        param = fsParam;
        this.snapshot = snapshot;

        // Add Payload
        ExportRules exportRules = param.getExportRulesToAdd();
        validateExportRules(exportRules, ExportOperationType.ADD);
        reportErrors(fsParam, ExportOperationType.ADD);

        // Modify Payload
        ExportRules exportModifyRules = param.getExportRulesToModify();
        validateExportRules(exportModifyRules, ExportOperationType.MODIFY);
        reportErrors(fsParam, ExportOperationType.MODIFY);

        // Delete Payload
        ExportRules exportDeleteRules = param.getExportRulesToDelete();
        validateExportRules(exportDeleteRules, ExportOperationType.DELETE);
        reportErrors(fsParam, ExportOperationType.DELETE);

        // letThisObjEligibleForGC();
    }

    /**
     * Verify the list of export rules that can be added
     * 
     * @param fs
     * @param listExportRules
     */
    private void validateExportRules(ExportRules listExportRules,
            ExportOperationType type) throws Exception {

        if (listExportRules == null) {
            _log.info("Missing Export Rules - Ignoring the operation type {} ",
                    type.name());
            invalidXMLElementErrorToReport = "Missing Export Rules - Ignoring the operation type "
                    + type.name();
            return;
        }

        switch (type) {
            case ADD: {
                verifyAddExportRule(listExportRules.getExportRules());

                break;
            }
            case MODIFY: {
                verifyModifyExportRule(listExportRules.getExportRules());

                break;
            }
            case DELETE: {
                verifyDeleteExportRule(listExportRules.getExportRules());

                break;
            }
        }

    }

    /**
     * Verify each export rule that can be added
     * 
     * @param listExportRule
     */
    private void verifyAddExportRule(List<ExportRule> listExportRule)
            throws Exception {
        if (listExportRule == null) {
            return;
        }

        _log.info("Checking if file system is exported before adding export rule");

        if (!isFileSystemExported()) {

            String msg = "File system is not exported. To add export rule, file system must be exported first.";
            _log.error(msg);
            String urn = fs != null ? fs.getId().toString() : snapshot.getId().toString();
            throw APIException.badRequests.fileSystemNotExported(
                    ExportOperationType.ADD.name(), urn);
        }
        _log.info("Number of Export Rule(s) Requested to Add {} - Iterating ..",
                listExportRule.size());

        for (ExportRule exportRule : listExportRule) {
            exportRule.setIsToProceed(true, ExportOperationErrorType.NO_ERROR);
            _log.info("Verifying Export Rule {}", exportRule.toString());

            // is same security flavor found in several exports - report the error
            scanForDuplicateSecFlavor(exportRule);

            if (exportRule.getErrorTypeIfNotToProceed() != null
                    && !(exportRule.getErrorTypeIfNotToProceed().name().equals(ExportOperationErrorType.NO_ERROR.name()))) {
                _log.info("Same Security Flavor found across the exports {}", exportRule.toString());
                break;
            }

            // Now validate hosts
            FileExportRule rule = validateHosts(exportRule);

            // If same export already found -- Don't allow to add again.
            if (rule != null) {
                _log.info("Duplicate Export to Add {} Requested : {}", rule, exportRule);
                exportRule.setIsToProceed(false, ExportOperationErrorType.EXPORT_EXISTS);
                break;
            }
            // If not found proceed for further verifications.
            else {
                if (exportRule.isToProceed()) {
                    _log.info("No Existing Export found in DB {}", exportRule);
                    verifyExportAnon(exportRule);
                }
            }
        }
    }

    private void scanForDuplicateSecFlavor(ExportRule rule) {

        if (rule == null) {
            return;
        }
        String secRuleToValidate = rule.getSecFlavor();
        // MULTIPLE_EXPORTS_WITH_SAME_SEC_FLAVOR
        if (!secFlavorsFound.contains(secRuleToValidate)) {
            secFlavorsFound.add(rule.getSecFlavor());
        }
        else
        {
            rule.setIsToProceed(false, ExportOperationErrorType.MULTIPLE_EXPORTS_WITH_SAME_SEC_FLAVOR);
        }
    }

    /**
     * Verify each export rule that can be added
     * 
     * @param fs
     * @param listExportRule
     */
    private void verifyModifyExportRule(List<ExportRule> listExportRule)
            throws Exception {
        if (listExportRule == null) {
            return;
        }
        _log.info("{} Export Rule(s) Requested to Modify {} - Iterating ..",
                listExportRule.size());
        for (ExportRule exportRule : listExportRule) {
            exportRule.setIsToProceed(true, ExportOperationErrorType.NO_ERROR);
            _log.info("Verifying Export Rule {}", exportRule.toString());

            // is same security flavor found in several exports - report the error
            scanForDuplicateSecFlavor(exportRule);
            if (!exportRule.isToProceed()) {
                _log.info("Same Security Flavor found across the exports {}", exportRule.toString());
                break;
            }

            FileExportRule rule = validateHosts(exportRule);
            // If found -- allow to modify.
            if (rule != null) {
                verifyExportAnon(exportRule);
            }
            // If not, stop proceed further.
            else {
                if (exportRule.isToProceed()) {
                    _log.info("Export not found to modify");
                    exportRule.setIsToProceed(false,
                            ExportOperationErrorType.EXPORT_NOT_FOUND);
                }
            }
        }

    }

    /**
     * Verify each export rule that can be added
     * 
     * @param fs
     * @param listExportRule
     */
    private void verifyDeleteExportRule(List<ExportRule> listExportRule)
            throws Exception {
        if (listExportRule == null) {
            return;
        }
        _log.info("{} Export Rule(s) Requested to Delete {} - Iterating ..",
                listExportRule.size());
        for (ExportRule exportRule : listExportRule) {
            exportRule.setIsToProceed(true, ExportOperationErrorType.NO_ERROR);
            _log.info("Verifying Export Rule {}", exportRule.toString());

            // is same security flavor found in several exports - report the error
            scanForDuplicateSecFlavor(exportRule);
            if (!exportRule.isToProceed()) {
                _log.info("Same Security Flavor found across the exports {}", exportRule.toString());
                break;
            }

            FileExportRule rule = validateInputAndQueryDB(exportRule);
            // If found -- allow to delete.
            if (rule != null) {
                exportRule.setIsToProceed(true, ExportOperationErrorType.NO_ERROR);
            }
            // If not, stop proceed further.
            else {
                _log.info("Export not found to delete");
                exportRule.setIsToProceed(false,
                        ExportOperationErrorType.EXPORT_NOT_FOUND);
            }
        }

    }

    /**
     * Verifying the validity of secflavor. If any new in future, verify them at
     * here.
     * 
     * @param exportRule
     */
    private void verifyExportAnon(ExportRule exportRule) {

        if (!exportRule.isToProceed()) {
            return;
        }
        String anon = exportRule.getAnon();
        if (anon != null) {
            exportRule.setIsToProceed(true, ExportOperationErrorType.NO_ERROR);
        } else {
            _log.info("No Anon supplied");
            exportRule
                    .setIsToProceed(false, ExportOperationErrorType.INVALID_ANON);
        }

    }

    /**
     * Copy the properties and set Fs Id.
     * 
     * @param dest
     * @param orig
     * @param fs
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private void copyProperties(FileExportRule dest, ExportRule orig) {

        String subDirPath = "";
        if (param.getSubDir() != null && param.getSubDir().length() > 0) {
            subDirPath = "/" + param.getSubDir();
        }
        _log.info("Sub Dir Path : {}", subDirPath);
        dest.setSecFlavor(orig.getSecFlavor());
        if (snapshot != null) {
            dest.setSnapshotId(snapshot.getId());
            dest.setExportPath(snapshot.getPath() + subDirPath);
        }
        else {
            dest.setFileSystemId(fs.getId());
            dest.setExportPath(fs.getPath() + subDirPath);
        }
        // BeanUtils.copyProperties(dest, orig);
        _log.info("After copying properties to DB Model Export Rule {}",
                dest.toString());

    }

    private List<FileExportRule> queryExports(FileShare fs, Snapshot snapshot, boolean isFile)
    {

        try {
            ContainmentConstraint containmentConstraint;

            if (isFile) {
                _log.info("Querying all ExportRules Using FsId {}", fs.getId());
                containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
            } else {
                URI snapshotId = snapshot.getId();
                _log.info("Querying all ExportRules Using Snapshot Id {}", snapshotId);
                containmentConstraint = ContainmentConstraint.Factory.getSnapshotExportRulesConstraint(snapshotId);
            }

            List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileExportRule.class,
                    containmentConstraint);
            return fileExportRules;

        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return null;

    }

    /**
     * Verify is the rule available in the database
     * 
     * @param compositeKey
     * @throws URISyntaxException
     */
    private FileExportRule getAvailableExportRule(FileExportRule exportRule)
            throws URISyntaxException {
        String exportIndex = exportRule.getFsExportIndex();
        if (snapshot != null) {
            exportIndex = exportRule.getSnapshotExportIndex();
        }

        _log.info("Retriving DB Model using its index {}", exportIndex);
        FileExportRule rule = null;
        URIQueryResultList result = new URIQueryResultList();

        if (snapshot != null) {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotExportRuleConstraint(exportIndex), result);
        } else {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileExportRuleConstraint(exportIndex), result);
        }

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            if (result.iterator().hasNext()) {
                rule = _dbClient.queryObject(FileExportRule.class, it.next());
                if (rule != null && !rule.getInactive()) {
                    _log.info("Existing DB Model found {}", rule);
                    break;
                } else {
                    rule = null;
                }
            }
        }

        return rule;

    }

    /**
     * Fail the request and report errors accordingly
     * 
     * @param param
     * @throws Exception
     */
    private void reportErrors(FileExportUpdateParams param,
            ExportOperationType type) throws Exception {

        _log.info("Working on reporting errors found if any");

        // if (invalidXMLElementErrorToReport != null) {
        // throw APIException.badRequests
        // .invalidFileExportXML(invalidXMLElementErrorToReport);
        // }

        switch (type) {
            case ADD: {
                reportAddErrors(param);
                break;
            }
            case MODIFY: {
                reportModifyErrors(param);
                break;
            }
            case DELETE: {
                reportDeleteErrors(param);
                break;
            }
        }

    }

    private void reportAddErrors(FileExportUpdateParams param)
            throws Exception {

        String opName = ExportOperationType.ADD.name();
        // Report Add Export Errors
        ExportRules listExportRules = param.getExportRulesToAdd();
        if (listExportRules == null || listExportRules.getExportRules().isEmpty()) {
            return;
        }

        List<ExportRule> listExportRule = listExportRules.getExportRules();
        for (ExportRule exportRule : listExportRule) {
            if (!exportRule.isToProceed()) {
                ExportOperationErrorType error = exportRule
                        .getErrorTypeIfNotToProceed();

                switch (error) {

                    case SNAPSHOT_EXPORT_SHOULD_BE_READ_ONLY: {
                        throw APIException.badRequests.snapshotExportPermissionReadOnly();
                    }
                    case INVALID_SECURITY_TYPE: {
                        if (exportRule.getSecFlavor() != null) {
                            throw APIException.badRequests
                                    .invalidSecurityType(exportRule
                                            .getSecFlavor());
                        } else {
                            throw APIException.badRequests
                                    .missingInputTypeFound(SEC_TYPE, opName);
                        }
                    }
                    case MULTIPLE_EXPORTS_WITH_SAME_SEC_FLAVOR: {
                        if (exportRule.getSecFlavor() != null) {
                            throw APIException.badRequests
                                    .sameSecurityFlavorInMultipleExportsFound(exportRule
                                            .getSecFlavor(), opName);
                        } else {
                            throw APIException.badRequests
                                    .missingInputTypeFound(SEC_TYPE, opName);
                        }
                    }
                    case INVALID_ANON: {
                        if (exportRule.getAnon() != null) {
                            throw APIException.badRequests
                                    .invalidAnon(exportRule.getAnon());
                        } else {
                            throw APIException.badRequests
                                    .missingInputTypeFound(ANON_TYPE, opName);
                        }
                    }
                    case NO_HOSTS_FOUND: {
                        throw APIException.badRequests.missingInputTypeFound(
                                NO_HOSTS_FOUND, opName);
                    }
                    case EXPORT_EXISTS: {
                        throw APIException.badRequests.exportExists(opName,
                                exportRule.toString());
                    }
                    case STORAGE_SYSTEM_NOT_SUPPORT_MUL_SECS: {
                        StorageSystem system = null;
                        String systemName = "";
                        if (fs != null) {
                            system = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
                        } else if (snapshot != null) {
                            FileShare fileSystem = _dbClient.queryObject(FileShare.class, snapshot.getParent());
                            system = _dbClient.queryObject(StorageSystem.class, fileSystem.getStorageDevice());
                        }
                        if (system != null) {
                            systemName = system.getSystemType();
                        }

                        throw APIException.badRequests.storageDoesNotSupportMulSecRule(opName,
                                systemName, exportRule.toString());
                    }
                    case EXPORT_NOT_FOUND:
                    case NO_ERROR:
                    default:
                        break;
                }

            }
        }

    }

    private void reportModifyErrors(FileExportUpdateParams param)
            throws Exception {

        String opName = ExportOperationType.MODIFY.name();
        // Report Modify Export Errors
        ExportRules listExportRules = param.getExportRulesToModify();
        if (listExportRules == null || listExportRules.getExportRules().isEmpty()) {
            return;
        }

        List<ExportRule> listExportRule = listExportRules.getExportRules();
        for (ExportRule exportRule : listExportRule) {
            if (!exportRule.isToProceed()) {
                ExportOperationErrorType error = exportRule
                        .getErrorTypeIfNotToProceed();
                switch (error) {
                    case SNAPSHOT_EXPORT_SHOULD_BE_READ_ONLY: {
                        throw APIException.badRequests.snapshotExportPermissionReadOnly();
                    }
                    case EXPORT_NOT_FOUND: {
                        throw APIException.badRequests.exportNotFound(opName,
                                exportRule.toString());
                    }
                    case INVALID_SECURITY_TYPE: {
                        if (exportRule.getSecFlavor() != null) {
                            throw APIException.badRequests
                                    .invalidSecurityType(exportRule
                                            .getSecFlavor());
                        } else {
                            throw APIException.badRequests
                                    .missingInputTypeFound(SEC_TYPE, opName);
                        }
                    }
                    case MULTIPLE_EXPORTS_WITH_SAME_SEC_FLAVOR: {
                        if (exportRule.getSecFlavor() != null) {
                            throw APIException.badRequests
                                    .sameSecurityFlavorInMultipleExportsFound(exportRule
                                            .getSecFlavor(), opName);
                        } else {
                            throw APIException.badRequests
                                    .missingInputTypeFound(SEC_TYPE, opName);
                        }
                    }
                    case INVALID_ANON: {
                        if (exportRule.getAnon() != null) {
                            throw APIException.badRequests
                                    .invalidAnon(exportRule.getAnon());
                        } else {
                            throw APIException.badRequests
                                    .missingInputTypeFound(ANON_TYPE, opName);
                        }
                    }
                    case NO_HOSTS_FOUND: {
                        throw APIException.badRequests.missingInputTypeFound(
                                NO_HOSTS_FOUND, opName);
                    }
                    case EXPORT_EXISTS:
                    case NO_ERROR:
                    default:
                        break;
                }

            }
        }

    }

    private void reportDeleteErrors(FileExportUpdateParams param)
            throws Exception {
        String opName = ExportOperationType.DELETE.name();
        // Report Delete Export Errors
        ExportRules listExportRules = param.getExportRulesToDelete();
        if (listExportRules == null || listExportRules.getExportRules().isEmpty()) {
            return;
        }

        List<ExportRule> listExportRule = listExportRules.getExportRules();
        for (ExportRule exportRule : listExportRule) {
            if (!exportRule.isToProceed()) {
                ExportOperationErrorType error = exportRule
                        .getErrorTypeIfNotToProceed();
                switch (error) {
                    case INVALID_SECURITY_TYPE: {
                        if (exportRule.getSecFlavor() != null) {
                            throw APIException.badRequests
                                    .invalidSecurityType(exportRule
                                            .getSecFlavor());
                        } else {
                            throw APIException.badRequests
                                    .missingInputTypeFound(SEC_TYPE,
                                            ExportOperationType.DELETE.name());
                        }
                    }
                    case MULTIPLE_EXPORTS_WITH_SAME_SEC_FLAVOR: {
                        if (exportRule.getSecFlavor() != null) {
                            throw APIException.badRequests
                                    .sameSecurityFlavorInMultipleExportsFound(exportRule
                                            .getSecFlavor(), opName);
                        } else {
                            throw APIException.badRequests
                                    .missingInputTypeFound(SEC_TYPE, opName);
                        }
                    }
                    case EXPORT_NOT_FOUND: {
                        throw APIException.badRequests.exportNotFound(
                                ExportOperationType.DELETE.name(),
                                exportRule.toString());
                    }
                    case EXPORT_EXISTS:
                    case INVALID_ANON:
                    case NO_ERROR:
                    default:
                        break;
                }

            }
        }

    }

    // Disable and handle at device layer
    private FileExportRule validateHosts(ExportRule exportRule)
            throws Exception {
        _log.info("Validating Export hosts");

        if (snapshot != null) {
            // snapshot specific validation - ro permission - can't export - per old code. So adding this validation here.
            if ((exportRule.getReadWriteHosts() != null && !exportRule.getReadWriteHosts().isEmpty())
                    || (exportRule.getRootHosts() != null && !exportRule.getRootHosts().isEmpty()))
            {
                exportRule.setIsToProceed(false, ExportOperationErrorType.SNAPSHOT_EXPORT_SHOULD_BE_READ_ONLY);
                _log.info("Snapshot export permission should be read only");
                return null;
            }
        }

        return validateInputAndQueryDB(exportRule);

    }

    private FileExportRule validateInputAndQueryDB(ExportRule exportRule)
            throws Exception {
        FileExportRule rule = null;
        verifyExportSecurity(exportRule);
        if (exportRule.isToProceed()) {
            rule = new FileExportRule();
            copyProperties(rule, exportRule);
            rule = getAvailableExportRule(rule);
        }
        return rule;
    }

    /**
     * Verifying the validity of secflavor. If any new in future, verify them at
     * here.
     * 
     * @param exportRule
     */
    private void verifyExportSecurity(ExportRule exportRule) {
        _log.info("Validating Export Security");
        try {
            List<String> secTypes = new ArrayList<String>();
            exportRule.setIsToProceed(true, ExportOperationErrorType.NO_ERROR);
            for (String securityType : exportRule.getSecFlavor().split(",")) {
                if (!securityType.trim().isEmpty()) {
                    secTypes.add(securityType.trim());
                    ExportSecurityType secType = ExportSecurityType.valueOf(securityType.trim().toUpperCase());
                    if (secType == null) {
                        exportRule.setIsToProceed(false,
                                ExportOperationErrorType.INVALID_SECURITY_TYPE);
                    }
                }
            }
            // Multiple security types in a single rule allowed for Isilon storage only!!!
            if (secTypes.size() > 1) {
                StorageSystem system = null;
                if (fs != null) {
                    system = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
                } else if (snapshot != null) {
                    FileShare fileSystem = _dbClient.queryObject(FileShare.class, snapshot.getParent());
                    system = _dbClient.queryObject(StorageSystem.class, fileSystem.getStorageDevice());
                }
                if (!DiscoveredDataObject.Type.isilon.name().equals(system.getSystemType())) {
                    exportRule.setIsToProceed(false,
                            ExportOperationErrorType.STORAGE_SYSTEM_NOT_SUPPORT_MUL_SECS);
                }
            }

        } catch (Exception e) {
            _log.info("Invalid Security Type found in Request {}",
                    exportRule.getSecFlavor());
            exportRule.setIsToProceed(false,
                    ExportOperationErrorType.INVALID_SECURITY_TYPE);

        }

    }

    private void letThisObjEligibleForGC() {
        _dbClient = null;
        fs = null;
        param = null;
    }

    private boolean isFileSystemExported() {

        FileObject fileObject = null;

        if (fs != null) {
            fileObject = fs;
        } else {
            fileObject = snapshot;
        }

        String path = fileObject.getPath();
        String subDirectory = param.getSubDir();

        if (subDirectory != null && !subDirectory.equalsIgnoreCase("null")
                && subDirectory.length() > 0) {
            // Add subdirectory to the path as this is a subdirectory export
            path += "/" + subDirectory;
        }

        FSExportMap exportMap = fileObject.getFsExports();
        if (exportMap != null) {
            Iterator<String> it = fileObject.getFsExports().keySet().iterator();
            while (it.hasNext()) {
                String fsExpKey = (String) it.next();
                FileExport fileExport = fileObject.getFsExports().get(fsExpKey);
                if (fileExport.getPath().equalsIgnoreCase(path)) {
                    _log.info("File system path: {} is exported", path);
                    return true;
                }
            }
        }
        _log.info("File system path: {} is not exported", path);
        return false;
    }
}
