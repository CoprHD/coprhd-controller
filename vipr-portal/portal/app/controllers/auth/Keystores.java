/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.auth;

import java.io.File;

import jobs.RegenerateCertificateJob;
import jobs.UpdateCertificateJob;

import org.apache.commons.io.FileUtils;

import play.Logger;
import play.data.validation.Validation;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;

import com.emc.vipr.client.core.Keystore;
import com.emc.vipr.model.keystore.CertificateChain;
import com.emc.vipr.model.keystore.KeyAndCertificateChain;

import controllers.Common;
import controllers.Maintenance;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"),
        @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class Keystores extends ViprResourceController {


    private static Keystore api() {
        return BourneUtil.getViprClient().keystore();
    }

    public static void updateCertificate() {
        KeystoreForm keystore = new KeystoreForm();
        String viewChain = viewChain(keystore);
        render(viewChain, keystore);
    }

    @FlashException(value = "updateCertificate", keep = true)
    public static void save(KeystoreForm keystore) {
        keystore.validate("keystore");
        if (Validation.hasErrors()) {
            handleError(keystore);
        }
        if (keystore.rotate) {
            try {
                // Here we need a sync call. Else no way to catch exception
                api().regenerateKeyAndCertificate();
            } catch (Exception e) {
                flash.error(e.getMessage());
                handleError(keystore);
            }
        } else {

            String key = null;
            String cert = null;

            try {
                key = FileUtils.readFileToString(keystore.certKey);
            } catch (Exception e) {
                flash.error(MessagesUtils
                        .get("keystore.certKey.invalid.error"));
                handleError(keystore);
            }

            try {
                cert = FileUtils.readFileToString(keystore.certChain);
            } catch (Exception e) {
                flash.error(MessagesUtils
                        .get("keystore.certChain.invalid.error"));
                handleError(keystore);
            }

            try {
                KeyAndCertificateChain keyAndCertChain = new KeyAndCertificateChain();
                keyAndCertChain.setCertificateChain(cert);
                keyAndCertChain.setPrivateKey(key);
                new UpdateCertificateJob(api(), keyAndCertChain).in(3);
            } catch (Exception e) {
                flash.error(e.getMessage());
                handleError(keystore);
            }
        }

        flash.success(MessagesUtils.get("keystore.saved.reboot"));
        Maintenance.maintenance(Common.reverseRoute(Keystores.class, "updateCertificate"));
    }

    public static String viewChain(KeystoreForm keystore) {
        CertificateChain chain = BourneUtil.getViprClient().keystore().getCertificateChain();
        if (chain == null || chain.getChain() == null || chain.getChain().isEmpty()) {
            flash.error(MessagesUtils.get("vdc.certChain.empty.error"));
        }
        return chain.getChain();
    }

    private static void handleError(KeystoreForm form) {
        params.flash();
        Validation.keep();
        updateCertificate();
    }

    public static class KeystoreForm {

        public boolean rotate;
        public File certChain;
        public File certKey;

        public void validate(String formName) {

            if (!this.rotate) {
                Validation.required(formName + ".certKey", certKey);
                Validation.required(formName + ".certChain", certChain);
            }

        }
    }

}
