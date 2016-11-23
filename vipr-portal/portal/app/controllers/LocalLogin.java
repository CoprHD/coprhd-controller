/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package controllers;

import controllers.security.Security;
import play.mvc.Controller;
import play.mvc.With;

/**
 *
 */
@With(Common.class)
public class LocalLogin extends Controller {

    public static void login() {
        if (Security.getAuthToken() == null) {
            // Check to see if we are redirected from the auth page.
            if (request.params._contains("auth-redirected")) {
                Security.noCookies();
            }
            String service = String.format("https://%s", request.domain);
            Security.redirectToAuthPage(service, true);
        } else { // to main page if already logged in
            String url = String.format("https://%s", request.domain);
            redirect(url);
        }
    }
}
