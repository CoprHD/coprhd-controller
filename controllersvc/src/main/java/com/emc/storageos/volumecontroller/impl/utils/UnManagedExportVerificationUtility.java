/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.model.file.FileExportUpdateParams.ExportSecurityType;

public class UnManagedExportVerificationUtility {

    private static final Logger _log = LoggerFactory
            .getLogger(UnManagedExportVerificationUtility.class);
    private DbClient _dbClient;

    public UnManagedExportVerificationUtility(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public boolean validateUnManagedExportRules(
            List<UnManagedFileExportRule> unManagedExportRules) {

        return validateUnManagedExportRules(unManagedExportRules, true);
    }

    public boolean validateUnManagedExportRules(
            List<UnManagedFileExportRule> unManagedExportRules, Boolean checkUMExpRuleInDB) {

        if (unManagedExportRules == null) {
            return true;
        }

        if (!scanForDuplicateSecFlavor(unManagedExportRules)) {
            return false;
        }

        if (!verifyExportSecurityRule(unManagedExportRules)) {
            return false;
        }
        // Disabling anon temp
        // isToProceed = verifyExportAnon(unManagedExportRules) && isToProceed;

        if (checkUMExpRuleInDB) {
            if (!checkUnManagedFsExportRuleExistsInDB(unManagedExportRules)) {
                return false;
            }
        }
        return true;
    }

    private boolean scanForDuplicateSecFlavor(
            List<UnManagedFileExportRule> unManagedExportRules) {

        _log.info("Validating Sec Flavor");
        List<String> secFlavorsFound = new ArrayList<>();
        for (UnManagedFileExportRule rule : unManagedExportRules) {
            String secRuleToValidate = rule.getSecFlavor();

            // MULTIPLE_EXPORTS_WITH_SAME_SEC_FLAVOR
            if (!secFlavorsFound.contains(secRuleToValidate)) {
                secFlavorsFound.add(rule.getSecFlavor());
                _log.info("Secuity rules found as of now {}, size {}", secFlavorsFound, secFlavorsFound.size());
            } else {
                _log.warn("Duplicate SecFlavor found {}", secRuleToValidate);
                return false;
            }
        }
        return true;
    }

    private boolean verifyExportAnon(
            List<UnManagedFileExportRule> unManagedExportRules) {
        _log.info("Validating Anon");
        String anon = null;
        for (UnManagedFileExportRule exportRule : unManagedExportRules) {
            anon = exportRule.getAnon();
            if (anon == null) {
                _log.warn("No Anon supplied");
                return false;
            }
        }
        return true;

    }

    private boolean verifyExportSecurityRule(
            List<UnManagedFileExportRule> unManagedExportRules) {
        _log.info("Validating Export Security");

        for (UnManagedFileExportRule exportRule : unManagedExportRules) {
            try {
                // NET APP Can have comma separated sec flavors
                if (exportRule.getSecFlavor().indexOf(",") != -1) {
                    String[] secs = exportRule.getSecFlavor().split(",");
                    for (String sec : secs) {
                        ExportSecurityType.valueOf(sec.toUpperCase());
                    }
                } else {
                    ExportSecurityType.valueOf(exportRule.getSecFlavor()
                            .toUpperCase());
                }
            } catch (Exception e) {
                _log.info("Invalid Security Type found {}",
                        exportRule.getSecFlavor());
                return false;
            }

        }
        return true;

    }

    protected boolean checkUnManagedFsExportRuleExistsInDB(
            List<UnManagedFileExportRule> unManagedExportRules) {
        for (UnManagedFileExportRule exportRule : unManagedExportRules) {
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileExporRuleNativeGUIdConstraint(exportRule
                            .getNativeGuid()), result);
            List<URI> filesystemUris = new ArrayList<URI>();
            Iterator<URI> iter = result.iterator();
            while (iter.hasNext()) {
                URI unFileSystemtURI = iter.next();
                filesystemUris.add(unFileSystemtURI);
            }
            if (!filesystemUris.isEmpty()) {
                _log.warn(
                        "Rule with native guid {} already exists in DB.",
                        exportRule.getNativeGuid());
                return false;
            }
        }

        return true;
    }
}
