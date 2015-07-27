/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.api;

import com.emc.vipr.model.catalog.ValidationError;
import controllers.deadbolt.Deadbolt;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import play.mvc.Controller;
import play.mvc.With;
import util.ConfigProperty;
import util.ConfigPropertyUtils;
import util.MessagesUtils;
import util.SetupUtils;
import java.util.Map;
import static render.RenderApiModel.*;

@With(Deadbolt.class)
@Restrictions({@Restrict({"SYSTEM_ADMIN", "SECURITY_ADMIN"}), @Restrict({"RESTRICTED_SYSTEM_ADMIN", "RESTRICTED_SECURITY_ADMIN"})})
public class SetupApi extends Controller {
    // This API is intended to allow skipping the initial setup. It requires the system properties to be set
    // manually through the proper APIs.
    public static void skip() {
        if (SetupUtils.isSetupComplete()) {
            ok();
        }

        Map<String,String> properties = ConfigPropertyUtils.getProperties();
        String proxyPassword = properties.get(ConfigProperty.PROXYUSER_PASSWORD);
        if (StringUtils.isEmpty(proxyPassword)) {
            response.status = HttpStatus.SC_BAD_REQUEST;
            renderApi(new ValidationError(ConfigProperty.PROXYUSER_PASSWORD, MessagesUtils.get("setup.no.proxy.password")));
        }

        SetupUtils.markSetupComplete();
        ok();
    }
}
