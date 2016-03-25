/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.auth;

import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.vipr.client.core.Truststore;
import com.emc.vipr.model.keystore.TrustedCertificate;
import com.emc.vipr.model.keystore.TrustedCertificateChanges;
import com.emc.vipr.model.keystore.TruststoreSettings;
import com.emc.vipr.model.keystore.TruststoreSettingsChanges;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.Maintenance;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import jobs.SaveCertificateSettingsJob;
import models.datatable.CertificateDataTable;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.Validation;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.With;
import sun.security.provider.X509Factory;
import util.BourneUtil;
import util.MessagesUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;

import java.io.File;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static controllers.Common.angularRenderArgs;

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class Certificates extends Controller {

    private static Truststore api() {
        return BourneUtil.getViprClient().truststore();
    }

    @Before(unless = { "list", "listJson" })
    static void isClusterStable() {
        if (!Common.isClusterStable()) {
            flash.error(MessagesUtils.get("configProperties.error.clusterNotStable"));
            list();
        }
    }

    @FlashException
    public static void list() {
        CertificateDataTable dataTable = new CertificateDataTable();

        List<StringOption> options = Lists.newArrayList();
        options.add(new StringOption("true", MessagesUtils.get("common.yes")));
        options.add(new StringOption("false", MessagesUtils.get("common.no")));

        TruststoreSettings certificateSettings = null;
        certificateSettings = api().getTruststoreSettings();

        angularRenderArgs().putAll(ImmutableMap.of(
                "options", options,
                "certificateSettings", certificateSettings
                ));

        render(dataTable);
    }

    public static void listJson() throws CertificateException {
        List<CertificateDataTable.CertificateInfo> certs = Lists.newArrayList();
        for (TrustedCertificate cert : api().getTrustedCertificates()) {
            certs.add(new CertificateDataTable.CertificateInfo(cert));
        }
        renderJSON(DataTablesSupport.createJSON(certs, params));
    }

    public static void edit(String id) {
        error("Not implemented");
    }

    public static void create() {
        render();
    }

    @FlashException(value = "list")
    public static void delete(@As(",") List<String> ids) {
        TrustedCertificateChanges changes = new TrustedCertificateChanges();
        changes.setRemove(ids);
        api().updateTrustedCertificate(changes);
        flash.success(MessagesUtils.get("certificateChanges.submittedReconfigure"));
        list();
    }

    public static void saveSettings(TruststoreSettingsChanges certificateSettings) {
        new SaveCertificateSettingsJob(api(), certificateSettings).in(3);
        flash.success(MessagesUtils.get("certificateSettings.submitted"));
        Maintenance.maintenance(Common.reverseRoute(Certificates.class, "list"));
    }

    @FlashException(value = "list")
    public static void addCertificates(CertificateChangesForm certificateChanges) {
        if (certificateChanges.validateAndExtractAdds("certificateChanges")) {
            if (certificateChanges.hasChanges()) {
                TrustedCertificateChanges changes = new TrustedCertificateChanges();
                changes.setAdd(certificateChanges.adds);

                api().updateTrustedCertificate(changes);
                flash.success(MessagesUtils.get("certificateChanges.submittedReconfigure"));
                list();
            } else {
                // shouldn't actually be possible.
                // The save button is disabled when there are no changes.
                flash.error(MessagesUtils.get("certificates.nothing"));
            }
        } else {
            params.flash();
            Validation.keep();
        }
        create();
    }

    public static void validCertificate(File file) {
        try {
            CertificateChangesForm.pemFromFile(file);
        } catch (Exception e) {
            response.status = 406;
            renderText(MessagesUtils.get("certificateChanges.files.invalidAjax"));
        }
    }

    public static class CertificateChangesForm {
        public File[] files;
        public List<String> adds = new ArrayList();

        public static String pemFromFile(File file) throws Exception {
            X509Certificate cert = (X509Certificate) KeyCertificatePairGenerator.getCertificateFromString(
                    FileUtils.readFileToString(file));
            if (cert == null) {
                throw new CertificateException("No certificate found in file " + file.getName());
            }
            // some characters (such as non printable characters) are ignored by the cert parser but
            // trip up our XML encoding/decoding. Get the PEM to send to API from the validated certificate
            // instead of the uploaded file.
            return (X509Factory.BEGIN_CERT + System.lineSeparator() +
                    new Base64().encodeToString(cert.getEncoded()) + System.lineSeparator() + X509Factory.END_CERT);
        }

        public boolean validateAndExtractAdds(String formName) {
            if (files != null) {
                for (File file : files) {
                    try {
                        adds.add(pemFromFile(file));
                    } catch (Exception e) {
                        Logger.error(e, "Unable to parse certificate file " + file.getName());
                        Validation.addError(formName + ".files",
                                MessagesUtils.get("certificateChanges.files.invalid", file.getName()));
                        return false;
                    }
                }
            }
            return true;
        }

        public boolean hasChanges() {
            return !adds.isEmpty();
        }
    }
}
