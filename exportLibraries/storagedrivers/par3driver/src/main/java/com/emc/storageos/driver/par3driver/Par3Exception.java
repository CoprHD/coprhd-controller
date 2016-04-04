package com.emc.storageos.driver.par3driver;

import jdk.nashorn.internal.runtime.regexp.joni.exception.InternalException;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;


public class Par3Exception extends InternalException {
    public Par3Exception(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	private static final long serialVersionUID = 8903079831758201184L;

    /** Holds the methods used to create ECS related exceptions */
    public static final Par3Exceptions exceptions = ExceptionMessagesProxy.create(Par3Exceptions.class);

    private Par3Exception(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(detailBase);
    }
}
