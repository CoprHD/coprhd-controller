/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.audit;


import junit.framework.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class RequestAuditFilterTest {

    private static final Logger _log = LoggerFactory.getLogger(RequestAuditFilterTest.class);
    public static final String PASSWORD_IN_REQUEST =
            "GET - https://10.145.23.155:4443/formlogin - username=root&password=ChangeMe from 10.33.108.208";
    public static final String PORTAL_TOKEN_IN_RESPONSE =
            "Response headers: HTTP/1.1 302\n" +
            "Location: https://lglw1102.lss.emc.com/?auth-redirected\n" +
            "X-SDS-PORTAL-AUTH-TOKEN: BAAcamltZEJDeFNwUEswd3lpSE5tS2tlUnN5dVFBPQMAVAQADTE0MTExNTAwNjE1MDUCAAEABQA9dXJuOnN0b3JhZ2VvczpUb2tlbjpkZTFhNmU0Mi0wZDI3LTRiMGEtOTU3OS1mMGNiNTVjNTgxNTU6dmRjMQIAAtAP\n" +
            "Set-Cookie: X-SDS-PORTAL-AUTH-TOKEN=BAAcamltZEJDeFNwUEswd3lpSE5tS2tlUnN5dVFBPQMAVAQADTE0MTExNTAwNjE1MDUCAAEABQA9dXJuOnN0b3JhZ2VvczpUb2tlbjpkZTFhNmU0Mi0wZDI3LTRiMGEtOTU3OS1mMGNiNTVjNTgxNTU6dmRjMQIAAtAP;HttpOnly;Version=1;Secure\n" +
            "Content-Type: text/html";

    public static final String TOKEN_IN_RESPONSE =
            "Response headers: HTTP/1.1 302\n" +
            "Location: https://lglw1102.lss.emc.com/?auth-redirected\n" +
            "X-SDS-AUTH-TOKEN: BAAcamltZEJDeFNwUEswd3lpSE5tS2tlUnN5dVFBPQMAVAQADTE0MTExNTAwNjE1MDUCAAEABQA9dXJuOnN0b3JhZ2VvczpUb2tlbjpkZTFhNmU0Mi0wZDI3LTRiMGEtOTU3OS1mMGNiNTVjNTgxNTU6dmRjMQIAAtAP\n" +
            "Set-Cookie: X-SDS-AUTH-TOKEN=BAAcamltZEJDeFNwUEswd3lpSE5tS2tlUnN5dVFBPQMAVAQADTE0MTExNTAwNjE1MDUCAAEABQA9dXJuOnN0b3JhZ2VvczpUb2tlbjpkZTFhNmU0Mi0wZDI3LTRiMGEtOTU3OS1mMGNiNTVjNTgxNTU6dmRjMQIAAtAP;HttpOnly;Version=1;Secure\n" +
            "Content-Type: text/html";

    @Test
    public void protectPasswordTest() {
        String result = RequestAuditFilter.stripCookieToken(PASSWORD_IN_REQUEST);
        _log.info("result: " + result);
        Assert.assertFalse(result.contains("ChangeMe"));
    }

    @Test
    public void protectTokenTest() {
        String result = RequestAuditFilter.stripCookieToken(TOKEN_IN_RESPONSE);
        _log.info("result: " + result);
        Assert.assertFalse(result.contains("BAAcamltZEJDeFNwUEswd"));

        result = RequestAuditFilter.stripCookieToken(PORTAL_TOKEN_IN_RESPONSE);
        _log.info("result: " + result);
        Assert.assertFalse(result.contains("BAAcamltZEJDeFNwUEswd"));

    }
}
