/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.keystone.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface KeystoneExceptions {
	
	@DeclareServiceCode(ServiceCode.KEYSTONE_API_ERROR)
    KeystoneApiException authenticationFailure(String keystoneUri) ;
	
	@DeclareServiceCode(ServiceCode.KEYSTONE_REQUEST_PARSE_ERRORS)
	KeystoneApiException requestJsonPayloadParseFailure(String requestPayload);

	@DeclareServiceCode(ServiceCode.KEYSTONE_RESPONSE_PARSE_ERROR)
	KeystoneApiException responseJsonParseFailure(String responseString);
	
	@DeclareServiceCode(ServiceCode.KEYSTONE_API_ERROR)
    KeystoneApiException apiExecutionFailed(String responseString) ;
	

}
