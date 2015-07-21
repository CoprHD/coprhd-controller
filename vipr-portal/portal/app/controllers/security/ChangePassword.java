/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package controllers.security;

import controllers.Common;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.security.password.Constants;

import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.With;
import util.BourneUtil;
import util.MessagesUtils;
import util.PasswordUtil;
import util.ValidationResponse;

@With(Common.class)
public class ChangePassword extends Controller {

    public static void update() {
        render();
    }

    public static void save(PasswordForm passwordForm) {
        passwordForm.save();
        update();
    }

    public static void cancel() {
        update();
    }

    public static void validatePasswords(String oldPassword, String password, String fieldName) {
    	String validation = PasswordUtil.validatePasswordforUpdate(oldPassword, password);
        if (StringUtils.isNotBlank(validation)) {
            Validation.addError(fieldName , validation);
        } 
        if (Validation.hasErrors()) {
            renderJSON(ValidationResponse.collectErrors());
        }    
        else {
            renderJSON(ValidationResponse.valid());
        }
        
    }
    
    public static String getPasswordValidPromptRule() {
    	String promptString = PasswordUtil.getPasswordValidPromptRules(Constants.PASSWORD_UPDATE_PROMPT);
        return promptString;
    }
    
    public static class PasswordForm {
        public String oldPassword;
        public String newPassword;
        public String confirmPassword;

        public void save() {
            if (StringUtils.isEmpty(oldPassword) || StringUtils.isEmpty(newPassword)
                    || StringUtils.isEmpty(confirmPassword)) {
                flash.error(MessagesUtils.get("passwordForm.emptyField"));
                return;
            }

            if (!StringUtils.equals(newPassword, confirmPassword)) {
                flash.error(MessagesUtils.get("passwordForm.doesNotMatch"));
                return;
            }

            try {
                BourneUtil.getSysClient().password().update(oldPassword, newPassword, false);
                flash.success(Messages.get("passwordForm.success"));
            } catch (Exception e) {
                Common.flashException(e);
            }
        }
    }
}
