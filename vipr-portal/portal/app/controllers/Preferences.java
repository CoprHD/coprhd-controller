/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.MessagesUtils;
import util.UserPreferencesUtils;

import com.emc.vipr.model.catalog.UserPreferencesRestRep;
import com.emc.vipr.model.catalog.UserPreferencesUpdateParam;

import controllers.security.Security;

@With(Common.class)
public class Preferences extends Controller {

    protected static final String SAVED = "user.saved";
    private static final String FLASH_REFERER = "flash.userPreferences.referer.url";

    public static void update() {
        PreferencesForm user =  new PreferencesForm(UserPreferencesUtils.getUserPreferences());
        storeReferer(user);
        render(user);
    }

    public static void save(PreferencesForm user) {
        storeReferer(user);
        user.validate("user");
        if (Validation.hasErrors()) {
            params.flash();
            Validation.keep();
            update();
        }
        else {
            user.save();
            redirect(getRedirectUrl());
        }
    }

    private static void storeReferer(PreferencesForm user) {
        final String referer = getRefererUrl();
        flash.put(FLASH_REFERER, referer);
        user.referer = referer;
        Logger.debug("Referer stored in Flash: %s", referer);
    }

    private static String getRedirectUrl() {
        // if we have a referer URL we should use that
        final String referer = getRefererUrl();
        if (StringUtils.isNotBlank(referer)) {
            Logger.debug("redirecting to %s", referer);
            return referer;
        }

        // otherwise use the dashboard
        Logger.debug("redirecting to %s", referer);
        return Common.reverseRoute(Dashboard.class, "index");
    }

    private static String getRefererUrl() {
        // if there is a referer URL stored in the flash we should use that
        final String flashReferer = flash.get(FLASH_REFERER);
        if (StringUtils.isNotBlank(flashReferer)) {
            return flashReferer;
        }

        // otherwise look for the referer in the request headers
        final String requestHeaderReferer = getRefererRequestHeader();
        if (StringUtils.isNotBlank(requestHeaderReferer)) {
            return requestHeaderReferer;
        }

        // otherwise we didn't find a referer
        return "/";
    }

    private static String getRefererRequestHeader() {
        for (String key : request.headers.keySet()) {
            if (StringUtils.equals(key, "referer")) {
                return request.headers.get(key).value();
            }
        }
        return null;
    }

    public static class PreferencesForm {

        public static final String EMAIL_REQUIRED = "user.email.required";

        public String userId;

        @Required
        public Boolean notifyByEmail = Boolean.FALSE;

        public String email;

        public String referer;

        public PreferencesForm(UserPreferencesRestRep userPrefs) {
            doReadFrom(userPrefs);
        }

        protected void doReadFrom(UserPreferencesRestRep model) {
            this.userId = model.getUsername();
            this.notifyByEmail = model.getNotifyByEmail();
            this.email = model.getEmail();
        }

        public void save() {
            UserPreferencesUpdateParam updateParam = new UserPreferencesUpdateParam();
            updateParam.setNotifyByEmail(this.notifyByEmail);
            updateParam.setEmail(this.email);
            updateParam.setUsername(Security.getUserInfo().getCommonName());
            UserPreferencesUtils.updateUserPreferences(updateParam);
        }

        public void validate(String formName) {
            String emailFieldName = formName + ".email";
            if (notifyByEmail) {
                Validation.required(emailFieldName, email).message(MessagesUtils.get(EMAIL_REQUIRED));
            }

            if (StringUtils.isNotBlank(email)) {
                Validation.email(emailFieldName, email);
            }
        }
    }

}
