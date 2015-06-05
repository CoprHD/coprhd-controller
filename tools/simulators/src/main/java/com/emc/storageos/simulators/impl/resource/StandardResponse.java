/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.simulators.impl.resource;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

public class StandardResponse {

    @XmlRootElement
    private static class ErrorResp {
        @XmlElement(name = "errors")
        private String errors;
    }

    @XmlRootElement
     private static class SuccessResp {
        @XmlElement(name = "success")
        private String success;
    }

    @XmlRootElement
    private static class SuccessIdResp {
        @XmlElement(name="id")
        private String id;

        @XmlElement(name = "success")
        private String success;
    }

    /**
     * Get standard error response  - isilon style
     * @param error     string error
     * @return          ErrorResp
     */
    public static ErrorResp getErrorResponse(String error) {
        ErrorResp resp = new ErrorResp();
        resp.errors = error;
        return resp;
    }

    /**
     * Get standard success response - isilon style
     * @return      SuccessResp
     */
    public static SuccessResp getSuccessResponse() {
        SuccessResp resp = new SuccessResp();
        resp.success = "true";
        return resp;
    }

    /**
     * Get standard success response with id - isilon style
     * @param id        Identifier
     * @return          SuccessIdResp
     */
    public static SuccessIdResp getSuccessIdResponse(String id) {
        SuccessIdResp resp = new SuccessIdResp();
        resp.id = id;
        resp.success = "true";
        return resp;
    }
}
