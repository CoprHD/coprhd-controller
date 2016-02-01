/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.glance.errorhandling;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface GlanceExceptions {
	
	@DeclareServiceCode(ServiceCode.GLANCE_RESPONSE_PARSE_ERROR)
	GlanceApiException clientResponseGetFailure(String responseString);
	
}
