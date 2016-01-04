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

    private String generateKickstartEsxEsxi(ComputeImageJob job, ComputeImage ci, ComputeImageServer imageServer) {
        log.info("generateKickstartEsxEsxi");
        String clearDevice = "--firstdisk";
        String installDevice = "--firstdisk";
        String bootDeviceUuid = null;
        if (job.getBootDevice() != null) {
            if (ImageServerUtils.isUuid(job.getBootDevice())) {
                bootDeviceUuid = ImageServerUtils.uuidFromString(job.getBootDevice()).toString().replaceAll("-", "");
            } else {
                bootDeviceUuid = job.getBootDevice();
            }
            clearDevice = "--drives=naa." + bootDeviceUuid;
            installDevice = "--disk=naa." + bootDeviceUuid;
        }

        String str = null;
        StringBuilder sb = null;
        if (ci._isEsxi5x()||ci._isEsxi6x()) {
            sb = new StringBuilder(ImageServerUtils.getResourceAsString(ESXI5X_UNATTENDED_TEMPLATE));
            if (bootDeviceUuid != null) {
                ImageServerUtils.replaceAll(sb, "${DATASTORE_SYM_LINK}",
                        "LINECOUNT=`localcli --format-param=show-header=false storage vmfs extent list | grep \"naa." + bootDeviceUuid
                                + "\" | wc -l` \n" +
                                "if [ $LINECOUNT = 1 ] ; then \n" +
                                "LOCALDS=`localcli --format-param=show-header=false storage vmfs extent list | cut -d \" \" -f 1` \n" +
                                "ln -s /vmfs/volumes/`readlink /vmfs/volumes/$LOCALDS` /vmfs/volumes/datastore1 \n" +
                                "fi");
            } else {
                ImageServerUtils.replaceAll(sb, "${DATASTORE_SYM_LINK}", "");
            }
        } else {
            throw ImageServerControllerException.exceptions.unknownOperatingSystem();
        }
        ImageServerUtils.replaceAll(sb, "${os_path}", ci.getLabel()); // does not apply for ESXi 5
        // common parameters for all versions
        ImageServerUtils.replaceAll(sb, "${clear.device}", clearDevice);
        ImageServerUtils.replaceAll(sb, "${install.device}", installDevice);
        ImageServerUtils.replaceAll(sb, "${http.ip}", imageServer.getImageServerSecondIp());
        ImageServerUtils.replaceAll(sb, "${http.port}", imageServer.getImageServerHttpPort());
        ImageServerUtils.replaceAll(sb, "${session.id}", job.getPxeBootIdentifier());
        str = sb.toString();
        log.trace(str);
        ImageServerUtils.replaceAll(sb, "${root.password}", job.getPasswordHash());
        return sb.toString();
    }

    private String generateFirstboot(ComputeImageJob job, ComputeImage ci) {
        StringBuilder sb = null;
        if (ci._isLinux()) {
            sb = new StringBuilder(ImageServerUtils.getResourceAsString(RHEL_FIRSTBOOT_SH));
        } else {
            sb = new StringBuilder(ImageServerUtils.getResourceAsString(ESXI_FIRSTBOOT_SH));
            // applies to ESXi only
            ImageServerUtils.replaceAll(sb, "__DEFAULT_VLAN_MARKER__", nonNullValue(job.getManagementNetwork()));
        }
        ImageServerUtils.replaceAll(sb, "__HOSTNAME_MARKER__", nonNullValue(job.getHostName()));
        ImageServerUtils.replaceAll(sb, "__HOSTIP_MARKER__", nonNullValue(job.getHostIp()));
        ImageServerUtils.replaceAll(sb, "__NETMASK_MARKER__", nonNullValue(job.getNetmask()));
        ImageServerUtils.replaceAll(sb, "__GATEWAY_MARKER__", nonNullValue(job.getGateway()));
        ImageServerUtils.replaceAll(sb, "__NTP_SERVER_MARKER__", nonNullValue(job.getNtpServer()));

        String[] dnsServers = nonNullValue(job.getDnsServers()).split(",");
        ImageServerUtils.replaceAll(sb, "__DNS_PRIMARY_IP_MARKER__", dnsServers[0].trim());
        ImageServerUtils.replaceAll(sb, "__DNS_SECONDARY_IP_MARKER__", dnsServers.length == 1 ? "" : dnsServers[1].trim());

        String str = sb.toString();
        log.trace(str);
        return str;
    }

    /*
     * private void createRhelPxeIdentifierFile(OsInstallSession session, InstallableImage os) {
     * // create uuid file
     * String s = ImageServerUtils.getResourceAsString(RHEL_UUID_TEMPLATE);
     * StringBuilder sb = new StringBuilder(s);
     * ImageServerUtils.replaceAll(sb, "${os_full_name}", os.getLabel());
     * ImageServerUtils.replaceAll(sb, "${os_path}", os.getLabel());
     * ImageServerUtils.replaceAll(sb, "${http.ip}", imageServer.getTftpServerIp());
     * ImageServerUtils.replaceAll(sb, "${http.port}", imageServer.getImageServerHttpPort());
     * ImageServerUtils.replaceAll(sb, "${session.id}", session.getId().toString());
     * String content = sb.toString();
     * log.trace(content);
     * ImageServerUtils.writeFile(imageServer.getTftpBootDir() + "/pxelinux.cfg/" + session.getPxeBootIdentifier(), content);
     * }
     */

    /*
     * private String generateKickstartLinux(OsInstallSession session, InstallableImage os) {
     * log.info("generateKickstartLinux");
     * 
     * String bootDeviceUuid = null;
     * if (session.getBootDevice() != null && ImageServerUtils.isUuid(session.getBootDevice())) {
     * bootDeviceUuid = ImageServerUtils.uuidFromString(session.getBootDevice()).toString().replaceAll("-", "");
     * }
     * 
     * StringBuilder sb = null;
     * if (os._isCentos()) {
     * sb = new StringBuilder(ImageServerUtils.getResourceAsString(CENTOS_UNATTENDED_TEMPLATE));
     * } else if (os._isOracle()) {
     * sb = new StringBuilder(ImageServerUtils.getResourceAsString(ORACLE_UNATTENDED_TEMPLATE));
     * } else {
     * sb = new StringBuilder(ImageServerUtils.getResourceAsString(RHEL_UNATTENDED_TEMPLATE));
     * }
     * if (bootDeviceUuid != null) {
     * ImageServerUtils.replaceAll(sb, "${ignore.disk}", "ignoredisk --only-use=/dev/disk/by-id/wwn-0x" + bootDeviceUuid);
     * ImageServerUtils.replaceAll(sb, "${boot.device.uuid}", "--ondisk=/dev/disk/by-id/wwn-0x" + bootDeviceUuid);
     * } else {
     * ImageServerUtils.replaceAll(sb, "${ignore.disk}", "");
     * ImageServerUtils.replaceAll(sb, "${boot.device.uuid}", "");
     * }
     * ImageServerUtils.replaceAll(sb, "${os_path}", os.getLabel());
     * ImageServerUtils.replaceAll(sb, "${root.password}", imageServer.getDefaultPasswordHas());
     * ImageServerUtils.replaceAll(sb, "${http.ip}", imageServer.getTftpServerIp());
     * ImageServerUtils.replaceAll(sb, "${http.port}", imageServer.getImageServerHttpPort());
     * ImageServerUtils.replaceAll(sb, "${http.files.port}", imageServer.getHttpFileServerPort());
     * ImageServerUtils.replaceAll(sb, "${session.id}", session.getId().toString());
     * String str = sb.toString();
     * log.trace(str);
     * return str;
     * }
     */

    private String nonNullValue(String val) {
        if (val == null) {
            return "";
        }
        return val;
    }
}
