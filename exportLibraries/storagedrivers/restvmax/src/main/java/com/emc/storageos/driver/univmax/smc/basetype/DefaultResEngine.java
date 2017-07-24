/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.basetype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.emc.storageos.driver.univmax.SymConstants;
import com.emc.storageos.driver.univmax.restengine.RestHandler;
import com.emc.storageos.driver.univmax.utils.UrlGenerator;

public class DefaultResEngine {

    protected AuthenticationInfo authenticationInfo;
    protected UrlGenerator urlGenerator;
    protected RestHandler engine;

    /**
     * @param authenticationInfo
     */
    public DefaultResEngine(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
        this.urlGenerator = new UrlGenerator(authenticationInfo);
        engine = new RestHandler(authenticationInfo);
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
