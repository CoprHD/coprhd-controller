/*
 * Copyright 2015 EMC Corporation
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.keystone.restapi.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface KeystoneExceptions {
	
	@DeclareServiceCode(ServiceCode.KEYSTONE_API_ERROR)
    KeystoneApiException authenticationFailure(String keystoneUri) ;

    @DeclareServiceCode(ServiceCode.KEYSTONE_CONFIGURATION_ERROR)
    KeystoneApiException missingService(String serviceName);

	@DeclareServiceCode(ServiceCode.KEYSTONE_REQUEST_PARSE_ERRORS)
	KeystoneApiException requestJsonPayloadParseFailure(String requestPayload);

	@DeclareServiceCode(ServiceCode.KEYSTONE_RESPONSE_PARSE_ERROR)
	KeystoneApiException responseJsonParseFailure(String responseString);
	
	@DeclareServiceCode(ServiceCode.KEYSTONE_API_ERROR)
    KeystoneApiException apiExecutionFailed(String responseString) ;
	

}
