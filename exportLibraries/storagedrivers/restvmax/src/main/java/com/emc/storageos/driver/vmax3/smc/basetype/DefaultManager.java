/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.emc.storageos.driver.vmax3.restengine.RestEngine;
import com.emc.storageos.driver.vmax3.smc.SymConstants;
import com.emc.storageos.driver.vmax3.utils.UrlGenerator;

public class DefaultManager {

    protected AuthenticationInfo authenticationInfo;
    protected UrlGenerator urlGenerator;
    protected RestEngine engine;

    /**
     * @param authenticationInfo
     */
    public DefaultManager(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
        this.urlGenerator = new UrlGenerator(authenticationInfo);
        engine = new RestEngine(authenticationInfo);
    }

    /**
     * @return the authenticationInfo
     */
    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

    public void appendExceptionMessage(IResponse responseBean, String template, Object... params) {
        responseBean.setHttpStatusCode(SymConstants.StatusCode.EXCEPTION);
        responseBean.appendCustMessage(String.format(template, params));
    }

    public List<String> genUrlFillers(String... fillers) {
        List<String> urlFillers = new ArrayList<String>();
        urlFillers.add(this.authenticationInfo.getSn());
        urlFillers.addAll(Arrays.asList(fillers));
        return urlFillers;
    }

}
