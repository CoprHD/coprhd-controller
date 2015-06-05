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

package com.emc.storageos.volumecontroller;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Exception thrown from storage controller service for service requests from
 * provisioning layer.
 */
public abstract class ControllerException extends InternalException {

	private static final long serialVersionUID = -6900306922128500639L;

    protected ControllerException(final boolean retryable, final ServiceCode code,
            final Throwable cause, final String detailBase, final String detailKey,
            final Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

	/**
	 * <b>ControllerException Constructor</b><br>
	 *
	 * <p>Creates a ControllerException with the given cause and a detailed message based on the given pattern and parameters.
	 * Use this constructor if there is a clear causing exception.</p>
	 *
	 *
	 * <p><b>Example:</b><br>
	 * <code>
	 * throw new ControllerException(e, "Caused by: {0}", new Object[] { e.getMessage() });
	 * </code></p>
	 *
	 * @param serviceCode ServiceCode representing the internal server error causing this exception
	 * @param cause the cause (which is saved for later retrieval by the getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
	 * @param pattern Pattern for the detailed message of this exception
	 * @param parameters List of parameters that will be injected in the given pattern. (A null value is permitted, and indicates that no parameters need to be injected.)
	 */
	protected ControllerException(ServiceCode serviceCode, final Throwable cause, final String pattern, final Object[] parameters) {
		super(serviceCode == ServiceCode.UNFORSEEN_ERROR ? ServiceCode.CONTROLLER_ERROR : serviceCode, cause, pattern, parameters);
	}

	/**
	 * <b>ControllerException Constructor</b><br>
	 *
	 * <p>Creates a ControllerException with a detailed message based on the given pattern and parameters.</p>
	 *
	 * <p>This is equivalent to :<br>
	 * <code>
	 * new ControllerException(code, null, pattern, parameters)
	 * </code><p>
	 *
	 * <p><b>Example:</b><br>
	 * <code>
	 * new ControllerException(ServiceCode.IO_ERROR, "Could not find a Storage pool for Protection VirtualArray: {0}", new Object[]{varray.getLabel()})
	 * </code></p>
	 *
	 * @param pattern Pattern for the detailed message of this exception
	 * @param parameters List of parameters that will be injected in the given pattern. (A null value is permitted, and indicates that no parameters need to be injected.)
	 */
	protected ControllerException(final String pattern, final Object[] parameters) {
        super(ServiceCode.CONTROLLER_ERROR, null, pattern, parameters);
	}

	/**
	 * <b>ControllerException Constructor</b><br>
	 *
	 * <p>Creates a ControllerException with the given cause and a detailed message based on the given pattern and parameters.
	 * Use this constructor if there is a clear causing exception.</p>
	 *
	 *
	 * <p><b>Example:</b><br>
	 * <code>
	 * throw new ControllerException(e, "Caused by: {0}", new Object[] { e.getMessage() });
	 * </code></p>
	 *
	 * @param cause the cause (which is saved for later retrieval by the getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
	 * @param pattern Pattern for the detailed message of this exception
	 * @param parameters List of parameters that will be injected in the given pattern. (A null value is permitted, and indicates that no parameters need to be injected.)
	 */
	protected ControllerException(final Throwable cause, final String pattern, final Object[] parameters) {
		super(ServiceCode.CONTROLLER_ERROR, cause, pattern, parameters);
	}

	/**
	 * Do not use this constructor, use any of the ones that are not deprecated
	 */
	protected ControllerException(String msg) {
		super(ServiceCode.CONTROLLER_ERROR, null, msg, null);
	}

	/**
	 * Do not use this constructor, use any of the ones that are not deprecated
	 */
	protected ControllerException(Throwable cause) {
		super(ServiceCode.CONTROLLER_ERROR, cause, "Caused by: {0}",
				new Object[] { cause.getMessage() });
	}

	/**
	 * Do not use this constructor, use any of the ones that are not deprecated
	 */
	protected ControllerException(String msg, Throwable cause) {
		super(ServiceCode.CONTROLLER_ERROR, cause, "{0}. Caused by: {1}",
				new Object[] { msg, cause.getMessage() });
	}
}
