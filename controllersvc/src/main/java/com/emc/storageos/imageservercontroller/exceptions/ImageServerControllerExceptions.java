/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface ImageServerControllerExceptions {

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException imageServerNotSetup(final String problem);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException unexpectedException(final String opName, final Throwable e);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException osInstallationTimedOut(final String hostName, final long timeoutValue);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException osInstallationFailed(final String hostName, final String reason);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException fileDownloadFailed(final String filePath);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException urlSanitationFailure(final String url, final Throwable cause);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException unsupportedImageVersion(final String imageVersion);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException unknownOperatingSystem();

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException unableToOpenResourceAsStream(final String resourcePath, final Throwable cause);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException unableToReadResource(final String resourcePath, final Throwable cause);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException duplicateImage(final String image);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException computeImageIsMissing(final String imageDir);

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException httpPythonServerNotRunning();

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException dhcpServerNotRunning();

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException tftpServerNotRunning();

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException deviceNotKnown();

    @DeclareServiceCode(ServiceCode.IMAGE_SERVER_CONTROLLER_ERROR)
    public ImageServerControllerException missingKickstartParameter(final String paramName);
}
