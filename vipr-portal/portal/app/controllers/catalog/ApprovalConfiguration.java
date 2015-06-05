/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package controllers.catalog;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.CatalogPreferenceUtils;
import util.MessagesUtils;
import util.validation.HostNameOrIpAddressCheck;

import com.emc.vipr.model.catalog.CatalogPreferencesRestRep;
import com.emc.vipr.model.catalog.CatalogPreferencesUpdateParam;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class ApprovalConfiguration extends Controller {
    public static void edit() {
        CatalogPreferencesRestRep prefs = CatalogPreferenceUtils.getCatalogPreferences(Models.currentAdminTenant());
        Form approvalConfig = new Form();
        approvalConfig.readFrom(prefs);
        TenantSelector.addRenderArgs();
        render(approvalConfig);
    }

    @FlashException("edit")
    public static void save(Form approvalConfig) {
        approvalConfig.validate("approvalConfig");
        if (Validation.hasErrors()) {
            params.flash();
            Validation.keep();
            edit();
        }

        CatalogPreferencesUpdateParam updateParam = new CatalogPreferencesUpdateParam();
        updateParam.setTenantId(Models.currentAdminTenant());
        approvalConfig.writeTo(updateParam);
        
        CatalogPreferenceUtils.updatePreferences(updateParam);
        
        flash.success(MessagesUtils.get("approvalConfig.saved"));
        edit();
    }

    public static class Form {
        public String approverEmail;
        public String approvalUrl;

        public void validate(String formName) {
            if (StringUtils.isNotBlank(approverEmail)) {
                for (String email : StringUtils.split(approverEmail, ",")) {
                    email = StringUtils.trim(email);
                    Validation.email(formName + ".approverEmail", email);
                }
            }
            if (StringUtils.isNotBlank(approvalUrl)) {
                // Play's URL validation is not very good, it doesn't allow IP address URLs 
                try {
                    URL url = new URL(approvalUrl);
                    if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
                        Validation.addError(formName + ".approvalUrl", "validation.url");
                    }
                    else if (!HostNameOrIpAddressCheck.isValidHostNameOrIp(url.getHost())) {
                        Validation.addError(formName + ".approvalUrl", "validation.url");
                    }
                }
                catch (MalformedURLException e) {
                    Validation.addError(formName + ".approvalUrl", "validation.url");
                }
            }
        }

        public void readFrom(CatalogPreferencesRestRep prefs) {
            if (prefs != null) {
                approverEmail = prefs.getApproverEmail();
                approvalUrl = prefs.getApprovalUrl();
            }
        }

        public void writeTo(CatalogPreferencesUpdateParam updateParam) {
            updateParam.setApproverEmail(StringUtils.defaultString(approverEmail));
            updateParam.setApprovalUrl(StringUtils.defaultString(approvalUrl));
        }
    }
}
