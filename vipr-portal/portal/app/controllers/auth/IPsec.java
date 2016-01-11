/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package controllers.auth;

import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

import controllers.Common;
import controllers.util.ViprResourceController;
import org.apache.commons.lang.StringUtils;
import play.mvc.With;
import util.IPsecUtils;
import util.MessagesUtils;

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"),
        @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class IPsec extends ViprResourceController {
    protected static final String INVALID_IPSEC_CONFIG_VERSION = "Invalid IPsec configuration version.";
    protected static final String IPSEC_KEY_ROTATION_ERROR = "ipsec.key.rotation.error";
    protected static final String IPSEC_KEY_ROTATION_SUCCESS = "ipsec.key.rotation.success";

    public static void ipsec() {
        IPSecForm ipSecForm = new IPSecForm();
        render(ipSecForm);
    }
    public static void rotateIPsecPreSharedKeys() {
        try {
            if (StringUtils.isBlank(IPsecUtils.rotateIPsecKey())) {
                flash.error(MessagesUtils.get(IPSEC_KEY_ROTATION_ERROR, INVALID_IPSEC_CONFIG_VERSION));
                ipsec();
            }

            flash.success(MessagesUtils.get(IPSEC_KEY_ROTATION_SUCCESS));
            ipsec();
        } catch (Exception e) {
            flash.error(MessagesUtils.get(IPSEC_KEY_ROTATION_ERROR, e.getMessage()));
            ipsec();
        }
    }

    public static class IPSecForm {
    }
}
