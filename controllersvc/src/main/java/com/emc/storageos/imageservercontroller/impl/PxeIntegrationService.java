/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller.impl;

import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_FAILURE_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_FIRSTBOOT_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_KICKSTART_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_SUCCESS_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.PXELINUX_CFG_DIR;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImageJob;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.imageservercontroller.exceptions.ImageServerControllerException;

public class PxeIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(PxeIntegrationService.class);

    private static final String ESXI5X_UUID_TEMPLATE = "imageserver/esxi5x_uuid.template";
    private static final String ESXI5X_UNATTENDED_TEMPLATE = "imageserver/esxi5x_unattended.template";
    private static final String RHEL_FIRSTBOOT_SH = "imageserver/rhel-firstboot.sh";
    private static final String ESXI_FIRSTBOOT_SH = "imageserver/esxi-firstboot.sh";

    public void createSession(ImageServerDialog d, ComputeImageJob job, ComputeImage ci, ComputeImageServer imageServer) {
        if (ci._isEsxi5x()||ci._isEsxi6x()) {
            createEsxiSession(d, job, ci, imageServer);
        } else if (ci._isRedhat() || ci._isCentos() || ci._isOracle()) {
            throw ImageServerControllerException.exceptions.unknownOperatingSystem();
        } else {
            throw ImageServerControllerException.exceptions.unknownOperatingSystem();
        }
    }

    /**
     * Create PXE UUID config and PXE uuid boot.cfg files for ESXi 5.x and 6.x
     * and put them under tftpboot/pxelinux.cfg/.
     * 
     * @param session
     * @param os
     */
    private void createEsxiSession(ImageServerDialog d, ComputeImageJob job, ComputeImage ci, ComputeImageServer imageServer) {
        // create uuid file
        String s = ImageServerUtils.getResourceAsString(ESXI5X_UUID_TEMPLATE);
        StringBuilder sb = new StringBuilder(s);
        ImageServerUtils.replaceAll(sb, "${os_full_name}", ci.getImageName());
        ImageServerUtils.replaceAll(sb, "${os_path}", ci.getPathToDirectory());
        ImageServerUtils.replaceAll(sb, "${pxe_identifier}", job.getPxeBootIdentifier());
        String content = sb.toString();
        log.trace(content);
        d.writeFile(imageServer.getTftpBootDir() + PXELINUX_CFG_DIR + job.getPxeBootIdentifier(), content);

        // create uuid.boot.cfg - only for esxi 5
        s = d.readFile(imageServer.getTftpBootDir() + ci.getPathToDirectory() + "/boot.cfg");
        sb = new StringBuilder(s.trim());
        ImageServerUtils.replaceAll(sb, "/", "/" + ci.getPathToDirectory());
        ImageServerUtils.replaceAll(sb, "runweasel", "runweasel vmkopts=debugLogToSerial:1 ks=http://"
                + imageServer.getImageServerSecondIp() + ":" + imageServer.getImageServerHttpPort() + "/ks/"
                + job.getPxeBootIdentifier() + " kssendmac");

        content = sb.toString();
        log.trace(content);
        d.writeFile(imageServer.getTftpBootDir() + PXELINUX_CFG_DIR + job.getPxeBootIdentifier() + ".boot.cfg", content);

        // create kick-start
        content = generateKickstart(job, ci, imageServer);
        d.writeFile(imageServer.getTftpBootDir() + HTTP_KICKSTART_DIR + job.getPxeBootIdentifier(), content);

        // create first boot
        content = generateFirstboot(job, ci);
        d.writeFile(imageServer.getTftpBootDir() + HTTP_FIRSTBOOT_DIR + job.getPxeBootIdentifier(), content);

        // remove these in case there was previous installation that succeeded or failed after we timed out
        d.rm(imageServer.getTftpBootDir() + HTTP_SUCCESS_DIR + job.getPxeBootIdentifier());
        d.rm(imageServer.getTftpBootDir() + HTTP_FAILURE_DIR + job.getPxeBootIdentifier());
    }

    private String generateKickstart(ComputeImageJob job, ComputeImage ci, ComputeImageServer imageServer) {
        log.info("generateKickstart for sess: " + job.getId());
        return generateKickstartEsxEsxi(job, ci, imageServer);
    }

    /**
     * Generates a kick-start script that runs on the server after the PXE boot base OS is loaded
     * from LAN. It usually formats a (boot) disk on the SAN for installation of the OS and
     * creates instructions for the first boot script to run. For details, see the esxi5x_unattended.template
     * file for details.
     * 
     * @param job
     *            compute image job
     * @param ci
     *            compute image info
     * @param imageServer
     *            compute image server
     * @return the kick-start script contents with all variables replaced.
     */
    private String generateKickstartEsxEsxi(ComputeImageJob job, ComputeImage ci, ComputeImageServer imageServer) {
        log.info("generateKickstartEsxEsxi");
        String clearDevice = null;
        String installDevice = null;
        String bootDeviceUuid = null;
        if (job.getBootDevice() != null) {
            if (ImageServerUtils.isUuid(job.getBootDevice())) {
                bootDeviceUuid = ImageServerUtils.uuidFromString(job.getBootDevice()).toString().replaceAll("-", "");
            } else {
                bootDeviceUuid = job.getBootDevice();
            }
            clearDevice = "--drives=naa." + bootDeviceUuid;
            installDevice = "--disk=naa." + bootDeviceUuid;
        } else {
            // If we can't find the boot device, it may be an issue with retrying the operation. Nevertheless,
            // don't try to do anything else at this point.
            throw ImageServerControllerException.exceptions.deviceNotKnown();
        }

        String str = null;
        StringBuilder sb = null;
        if (ci._isEsxi5x()||ci._isEsxi6x()) {
            sb = new StringBuilder(ImageServerUtils.getResourceAsString(ESXI5X_UNATTENDED_TEMPLATE));
            ImageServerUtils.replaceAll(sb, "${DATASTORE_SYM_LINK}",
                    "LINECOUNT=`localcli --format-param=show-header=false storage vmfs extent list | grep \"naa." + bootDeviceUuid
                            + "\" | wc -l` \n" +
                            "if [ $LINECOUNT = 1 ] ; then \n" +
                            "LOCALDS=`localcli --format-param=show-header=false storage vmfs extent list | cut -d \" \" -f 1` \n" +
                            "ln -s /vmfs/volumes/`readlink /vmfs/volumes/$LOCALDS` /vmfs/volumes/datastore1 \n" +
                            "fi");
        } else {
            throw ImageServerControllerException.exceptions.unknownOperatingSystem();
        }

        ImageServerUtils.replaceAll(sb, "${os_path}", ci.getLabel()); // does not apply for ESXi 5

        assertKickStartParam(clearDevice, "clearpart device");
        assertKickStartParam(installDevice, "install device");
        assertKickStartParam(imageServer.getImageServerSecondIp(), "image server second IP");
        assertKickStartParam(imageServer.getImageServerHttpPort(), "image server HTTP port");
        assertKickStartParam(job.getPxeBootIdentifier(), "PXE boot identifier");
        assertKickStartParam(job.getPasswordHash(), "Password hash");

        // common parameters for all versions
        ImageServerUtils.replaceAll(sb, "${raw.uuid}", bootDeviceUuid);
        ImageServerUtils.replaceAll(sb, "${clear.device}", clearDevice);
        ImageServerUtils.replaceAll(sb, "${install.device}", installDevice);
        ImageServerUtils.replaceAll(sb, "${http.ip}", imageServer.getImageServerSecondIp());
        ImageServerUtils.replaceAll(sb, "${http.port}", imageServer.getImageServerHttpPort());
        ImageServerUtils.replaceAll(sb, "${session.id}", job.getPxeBootIdentifier());
        str = sb.toString();
        log.info(str);
        ImageServerUtils.replaceAll(sb, "${root.password}", job.getPasswordHash());
        return sb.toString();
    }

    /**
     * Parameter assertion to ensure valid values are getting set in the scripts.
     * 
     * @param param
     *            parameter variable
     * @param paramName
     *            parameter variable's name
     */
    private void assertKickStartParam(String param, String paramName) {
        if (param == null || param.isEmpty()) {
            throw ImageServerControllerException.exceptions.missingKickstartParameter(paramName);
        }
    }

    /**
     * Generates a first-boot script that runs after the host is kick-started. Sets critical
     * IP parameters so the host will be reachable on the network and can be added to clusters,
     * etc.
     * 
     * @param job
     *            compute image job
     * @param ci
     *            compute image info
     * @return the firstboot script contents with all variables replaced.
     */
    private String generateFirstboot(ComputeImageJob job, ComputeImage ci) {
        StringBuilder sb = null;
        if (ci._isLinux()) {
            sb = new StringBuilder(ImageServerUtils.getResourceAsString(RHEL_FIRSTBOOT_SH));
        } else {
            sb = new StringBuilder(ImageServerUtils.getResourceAsString(ESXI_FIRSTBOOT_SH));
            // applies to ESXi only
            // VBDU TODO: COP-28460, Check for explicit ESX type
            ImageServerUtils.replaceAll(sb, "__DEFAULT_VLAN_MARKER__", nonNullValue(job.getManagementNetwork()));
        }
        ImageServerUtils.replaceAll(sb, "__HOSTNAME_MARKER__", nonNullValue(job.getHostName()));
        ImageServerUtils.replaceAll(sb, "__HOSTIP_MARKER__", nonNullValue(job.getHostIp()));
        ImageServerUtils.replaceAll(sb, "__NETMASK_MARKER__", nonNullValue(job.getNetmask()));
        ImageServerUtils.replaceAll(sb, "__GATEWAY_MARKER__", nonNullValue(job.getGateway()));
        ImageServerUtils.replaceAll(sb, "__NTP_SERVER_MARKER__", nonNullValue(job.getNtpServer()));
        String[] tokens = nonNullValue(job.getHostName()).split(".");
        String shortHostName = tokens[0];
        String bootDatastoreLabel = shortHostName +"_boot";
        ImageServerUtils.replaceAll(sb, "__BOOT_VOLUME_LABEL_MARKER__", bootDatastoreLabel);
        String[] dnsServers = nonNullValue(job.getDnsServers()).split(",");
        ImageServerUtils.replaceAll(sb, "__DNS_PRIMARY_IP_MARKER__", dnsServers[0].trim());
        ImageServerUtils.replaceAll(sb, "__DNS_SECONDARY_IP_MARKER__", dnsServers.length == 1 ? "" : dnsServers[1].trim());

        String str = sb.toString();
        log.info(str);
        return str;
    }

    /**
     * Convenience method to replace any null String with an empty string.
     * Useful for parameter replacement in script generation where the parameter is
     * allowed to be empty.
     * 
     * @param val
     *            string object
     * @return empty string if val is null, otherwise val
     */
    private String nonNullValue(String val) {
        if (val == null) {
            return "";
        }
        return val;
    }
}
