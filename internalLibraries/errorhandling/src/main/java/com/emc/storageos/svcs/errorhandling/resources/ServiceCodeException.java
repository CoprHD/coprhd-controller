/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.svcs.errorhandling.resources;

import java.util.Locale;

import javax.ws.rs.core.Response.StatusType;

import com.emc.storageos.svcs.errorhandling.model.StatusCoded;
import com.emc.storageos.svcs.errorhandling.utils.Messages;

/**
 * This class has been deprecated, if you need to create a ViPR exception, you will need to use the utility interfaces.
 *
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@Deprecated
public class ServiceCodeException extends RuntimeException implements StatusCoded {

    private static final long serialVersionUID = 6664389884641912560L;

    /**
     * ServiceCode representing the internal server error causing this exception
     */
    private final ServiceCode _serviceCode;

    /**
     * Detailed message pattern
     */
    private final String _pattern;

    /**
     * Arguments to be injected in the <code>_pattern</code>
     */
    private final Object[] _parameters;

    /**
     * <b>ServiceCodeException Constructor</b><br>
     *
     * <p>Creates a ServiceCodeException with a detailed message based on the given pattern and parameters.</p>
     *
     * <p>This is equivalent to :<br>
     * <code>
     * new ServiceCodeException(code, null, pattern, parameters)
     * </code><p>
     *
     * <p><b>Example:</b><br>
     * <code>
     * new ServiceCodeException(ServiceCode.IO_ERROR, "Could not find a Storage pool for Protection VirtualArray: {0}", new Object[]{varray.getLabel()})
     * </code></p>
     *
     * @param code ServiceCode representing the internal server error causing this exception
     * @param pattern Pattern for the detailed message of this exception
     * @param parameters List of parameters that will be injected in the given pattern. (A null value is permitted, and indicates that no parameters need to be injected.)
     */
    @Deprecated
    public ServiceCodeException(final ServiceCode code, final String pattern, final Object[] parameters) {
        this(code, null, pattern, parameters);
    }

    /**
     * <b>ServiceCodeException Constructor</b><br>
     *
     * <p>Creates a ServiceCodeException with the given cause and a detailed message based on the given pattern and parameters.
     * Use this constructor if there is a clear causing exception.</p>
     *
     *
     * <p><b>Example:</b><br>
     * <code>
     * throw new ServiceCodeException(CONTROLLER_ERROR, e, "Caused by: {0}", new Object[] { e.getMessage() });
     * </code></p>
     *
     * @param code ServiceCode representing the internal server error causing this exception
     * @param cause the cause (which is saved for later retrieval by the getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     * @param pattern Pattern for the detailed message of this exception
     * @param parameters List of parameters that will be injected in the given pattern. (A null value is permitted, and indicates that no parameters need to be injected.)
     */
    @Deprecated
    public ServiceCodeException(final ServiceCode code, final Throwable cause, final String pattern, final Object[] parameters) {
        super(cause);
        _serviceCode = code;
        _pattern = pattern;
        _parameters = parameters;
    }

    @Override
    public ServiceCode getServiceCode() {
        return _serviceCode;
    }

    @Override
    public String getMessage() {
        return getMessage(Locale.ENGLISH);
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage(Locale.getDefault());
    }

    @Override
    public String getMessage(final Locale locale) {
        return Messages.localize(null, locale, _pattern, _parameters);
    }

    @Override
    public boolean isRetryable() {
        return getServiceCode().isRetryable();
    }

    @Override
    public StatusType getStatus() {
        return getServiceCode().getHTTPStatus();
    }
}
