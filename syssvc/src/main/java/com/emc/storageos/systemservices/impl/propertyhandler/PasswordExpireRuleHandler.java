/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.propertyhandler;


import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.systemservices.impl.util.LocalPasswordHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

public class PasswordExpireRuleHandler implements UpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(PasswordExpireRuleHandler.class);

    private String _propertyName = Constants.PASSWORD_EXPIRE_DAYS;

    private LocalPasswordHandler _passwordHandler;
    public void setPasswordHandler(LocalPasswordHandler passwordHandler) {
        _passwordHandler = passwordHandler;
    }

    public String getPropertyName() {
        return _propertyName;
    }

    /**
     * check if new password_expire_days value is in range [grace_days, 365], if not fail the property update.
     *
     * it also calculate new values for properties below:
     *     system_root_expiry_date
     *     system_svcuser_expiry_date
     * and add the properties in newProps, so they can be updated along with password_expire_days property.
     *
     * above 2 properties is used for genenating /etc/shadow file to set expire days for root and svcuser.
     *
     * @param oldProps
     * @param newProps
     */
    public void before(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        String oldValue = oldProps.getProperty(getPropertyName());
        String newValue = newProps.getProperty(getPropertyName());
        if (newValue == null) {
            return;
        }

        if (oldValue == null) {
            oldValue = "0";
        }

        int graceDays = Constants.GRACE_DAYS;
        int intNewValue = Integer.parseInt(newValue);
        if (intNewValue != 0 &&
                (intNewValue < graceDays || intNewValue > Constants.MAX_PASSWORD_EXPIRY_IN_DAYS)) {
            throw BadRequestException.badRequests.passwordInvalidExpireDays(
                    graceDays,
                    Constants.MAX_PASSWORD_EXPIRY_IN_DAYS);
        }

        // calculate values for root/svcuser's new expiry_date properties
        // add the 2 properties to newProps
        int oldDays = Integer.parseInt(oldValue);
        int newDays = Integer.parseInt(newValue);

        String rootExpirydaysProperty = String.format(Constants.SYSTEM_PASSWORD_EXPIRY_FORMAT, "root");
        String svcuserExpirydaysProperty = String.format(Constants.SYSTEM_PASSWORD_EXPIRY_FORMAT, "svcuser");

        if (oldDays == 0 && newDays != 0) {
            Calendar newExpireTime = Calendar.getInstance();
            newExpireTime.add(Calendar.DATE, newDays);
            _log.info("turn on password expire rule, update root and svcuser's expiry_date properties accordingly");
            int daysAfterEpoch = PasswordUtils.getDaysAfterEpoch(newExpireTime);
            _log.info("updating  root/svcuser expiry_days properties to " + daysAfterEpoch);
            newProps.addProperty(rootExpirydaysProperty, String.valueOf(daysAfterEpoch));
            newProps.addProperty(svcuserExpirydaysProperty, String.valueOf(daysAfterEpoch));

        } else if (newDays == 0) {  // disable password expire rule: new = 0
            _log.info("turn off expire rule, update, update root and svcuser's expiry_date properties accordingly");
            _log.info("updating  root/svcuser expiry_days properties to 0");
            newProps.addProperty(rootExpirydaysProperty, "0");
            newProps.addProperty(svcuserExpirydaysProperty,"0");

        } else {  // change expire rule
            _log.info("re-configure expire days from " + oldDays + " to " + newDays);
            Calendar newExpireTime = _passwordHandler.getPasswordUtils().calculateExpireDateForUser("root", newDays);
            int daysAfterEpoch = PasswordUtils.getDaysAfterEpoch(newExpireTime);
            _log.info("updating root expiry_date property to " + daysAfterEpoch);
            newProps.addProperty(rootExpirydaysProperty, String.valueOf(daysAfterEpoch));

            newExpireTime = _passwordHandler.getPasswordUtils().calculateExpireDateForUser("svcuser", newDays);
            daysAfterEpoch = PasswordUtils.getDaysAfterEpoch(newExpireTime);
            _log.info("updating svcuser expiry_date property to " + daysAfterEpoch);
            newProps.addProperty(svcuserExpirydaysProperty, String.valueOf(daysAfterEpoch));
        }
    }

    /**
     * adjust all local user's expire date in PasswordHistory CF, according to the new value of password_expire_days.
     *
     * @param oldProps
     * @param newProps
     */
    public void after(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        String oldValue = oldProps.getProperty(getPropertyName());
        String newValue = newProps.getProperty(getPropertyName());

        _log.info("old value: " + oldValue + ", newValue: " + newValue);
        // doesn't change expire rule config, skip this handler.
        if (newValue == null ) {
            return;
        }

        if (oldValue == null) {
            oldValue = "0";
        }

        if (oldValue.equals(newValue)) {
            return;
        }

        int oldDays = Integer.parseInt(oldValue);
        int newDays = Integer.parseInt(newValue);

        // enable password expire rule, which means: old = 0, new != 0
        if (oldDays == 0 && newDays != 0) {
            Calendar expireDate = Calendar.getInstance();
            expireDate.add(Calendar.DATE, newDays);
            _log.info("turn on expire rule, set new expire date to " + expireDate.getTime());
            _passwordHandler.getPasswordUtils().setExpireDateToAll(expireDate);
        } else if (newDays == 0) {  // disable password expire rule: new = 0
            _log.info("turn off expire rule");
            _passwordHandler.getPasswordUtils().setExpireDateToAll(null);
            return;
        } else {  // change expire rule
            _log.info("re-configure expire days from " + oldDays + " to " + newDays);
            _passwordHandler.getPasswordUtils().adjustExpireTime(newDays);
        }
    }



}
