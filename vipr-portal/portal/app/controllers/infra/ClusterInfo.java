/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.IPv4Address;
import play.data.validation.IPv6Address;
import play.data.validation.Range;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import plugin.StorageOsPlugin;
import util.BourneUtil;
import util.MessagesUtils;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.model.sys.ClusterInfo.ClusterState;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpInfo;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpv4Setting;
import com.emc.vipr.model.sys.ipreconfig.ClusterIpv6Setting;
import com.emc.vipr.model.sys.ipreconfig.ClusterNetworkReconfigStatus;
import com.google.gson.Gson;

import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;

@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class ClusterInfo extends Controller {
    public static final String DEFAULT_IPV4_ADDR = "0.0.0.0";
    public static final String DEFAULT_NETMASK = "255.255.255.0";
    public static final String DEFAULT_IPV6_ADDR = "::0";
    public static final int DEFAULT_PREFIX_LEN = 64;

    private static String SUCCESS_KEY = "ipreconfig.successful";
    private static String ERROR_KEY = "ipreconfig.error";
    private static String EXCEPTION_KEY = "ipreconfig.exception";
    private static String UNSUPPORTED_KEY = "ipReconfig.notSupported";
    private static String UNSTABLE_KEY = "ipReconfig.clusterNotStable";
    private static String RECONFIGURATION_STATUS_SUCCESS = "ipreconfig.status.success";
    private static String RECONFIGURATION_STATUS_ERROR = "ipreconfig.status.error";

    public static String vip = null;

    /**
     * loads render args
     */
    private static void loadRenderArgs() {
        boolean reconfigSupported = true;

        boolean unsupportedPlatform = BourneUtil.getViprClient().vdcs().isGeoSetup();
        if (unsupportedPlatform) {
            reconfigSupported = false;
            Logger.info(UNSUPPORTED_KEY + "  isGeo: " + unsupportedPlatform);
            flash.put("warning", MessagesUtils.get(UNSUPPORTED_KEY));
        }

        ClusterState clusterState = getClusterStateFromCoordinator();
        if (clusterState == null || !clusterState.equals(ClusterState.STABLE)) {
            reconfigSupported = false;
            flash.put("warning", MessagesUtils.get(UNSTABLE_KEY));
        }

        renderArgs.put("reconfigSupported", reconfigSupported);
        renderArgs.put("vip", vip);
    }

    /**
     * loads cluster ip configuration details
     */
    public static void clusterIpInfo(ClusterIpInfoForm ipReconfigForm) {
        if (ipReconfigForm == null) {
            ViPRSystemClient client = BourneUtil.getSysClient();
            ClusterIpInfo clusterIpInfo = client.control().getClusterIpinfo();
            ipReconfigForm = new ClusterIpInfoForm();
            ipReconfigForm.load(clusterIpInfo);

            ClusterNetworkReconfigStatus ipReconfigStatus = client.control().getClusterIpReconfigStatus();
            if (ipReconfigStatus != null && ipReconfigStatus.getStatus() != null) {
                if (ipReconfigStatus.getStatus().equals(ClusterNetworkReconfigStatus.Status.FAILED)) {
                    flash.error(MessagesUtils.get(RECONFIGURATION_STATUS_ERROR, ipReconfigStatus.getMessage()));

                } else if (ipReconfigStatus.getStatus().equals(ClusterNetworkReconfigStatus.Status.SUCCEED)) {
                    if (ipReconfigStatus.isRecentlyReconfigured()) {
                        flash.put("info", MessagesUtils.get(RECONFIGURATION_STATUS_SUCCESS));
                    }
                }
            }
        }
        vip = ipReconfigForm.selectVipforStatusQuery();// NOSONAR
                                                       // ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for vip. vip only fetches network info. Sync not needed.")
        loadRenderArgs();
        render(ipReconfigForm);
    }

    /**
     * gets ip reconfigurations status in json format
     */
    @FlashException()
    public static void ipReconfigStatusJson() {
        ViPRSystemClient client = BourneUtil.getSysClient();
        ClusterNetworkReconfigStatus ipReconfigStatus = client.control().getClusterIpReconfigStatus();
        Gson gson = new Gson();
        String ipReconfigStatusJSON = gson.toJson(ipReconfigStatus);
        renderJSON(ipReconfigStatusJSON);
    }

    /**
     * Submit cluster IPs reconfiguration requests.
     * 
     * @param ipReconfigForm
     */
    public static void ipReconfig(final ClusterIpInfoForm ipReconfigForm) {
        ipReconfigForm.validate();
        if (Validation.hasErrors()) {
            params.flash();
            Validation.keep();
            clusterIpInfo(ipReconfigForm);
        }
        final ViPRSystemClient client = BourneUtil.getSysClient();
        final ClusterIpInfo clusterIpInfo = ipReconfigForm.getClusterIpInfo();

        try {
            boolean isAccepted = client.control().reconfigClusterIps(clusterIpInfo, ipReconfigForm.powerOff);
            if (isAccepted) {
                flash.put("info", MessagesUtils.get(SUCCESS_KEY));
            } else {
                flash.error(MessagesUtils.get(ERROR_KEY));
            }
        } catch (Exception e) {

            flash.error(MessagesUtils.get(EXCEPTION_KEY, e.getMessage()));
            Logger.error(e, e.getMessage());
        }
        clusterIpInfo(ipReconfigForm);
    }

    private static ClusterState getClusterStateFromCoordinator() {
        if (StorageOsPlugin.isEnabled()) {
            CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
            ClusterState clusterState = coordinatorClient.getControlNodesState();
            return clusterState;
        }
        return null;
    }

    /**
     * If it is a VMware app, then N/W re-configure should be available.
     * 
     * @return Returns true if N/W re-configure should be available.
     */
    public static boolean isVMwareVapp() {
        boolean isEnabled = false;
        try {
            if (!PlatformUtils.isVMwareVapp()) {
                isEnabled = true;
            }
        } catch (IllegalStateException ise) {
            // Thrown if method could not determine platform.
            Logger.warn("Could not determine platform.");
        }

        return isEnabled;
    }

    /**
     * If it is a DR Multi-sites, then N/W re-configure should not be available.
     * 
     * @return Returns true if for DR Multi-sites.
     */
    public static boolean isDRMultisites() {
        boolean isEnabled = false;
        try {
            if (PlatformUtils.hasMultipleSites()) {
                isEnabled = true;
            }
        } catch (IllegalStateException ise) {
            // Thrown if method could not determine platform.
            Logger.warn("Could not Disaster Recovery environments.");
        }

        return isEnabled;
    }

    // "Suppressing Sonar violation of Field names should comply with naming convention"
    @SuppressWarnings("squid:S00116")
    public static class ClusterIpInfoForm {

        @Required
        public int nodeCount;

        @Required
        public boolean powerOff;

        @Required
        @IPv4Address
        public String network_vip;

        @Required
        @IPv4Address
        public String ipv4_network_addrs1;

        @IPv4Address
        public String ipv4_network_addrs2;

        @IPv4Address
        public String ipv4_network_addrs3;

        @IPv4Address
        public String ipv4_network_addrs4;

        @IPv4Address
        public String ipv4_network_addrs5;

        @Required
        @IPv4Address
        public String network_netmask;

        @Required
        @IPv4Address
        public String network_gateway;

        @Required
        @IPv6Address
        public String network_vip6;

        @Required
        @IPv6Address
        public String ipv6_network_addrs1;

        @IPv6Address
        public String ipv6_network_addrs2;

        @IPv6Address
        public String ipv6_network_addrs3;

        @IPv6Address
        public String ipv6_network_addrs4;

        @IPv6Address
        public String ipv6_network_addrs5;

        @Required
        @Range(min = 1, max = 128)
        public int network_prefix_length;

        @Required
        @IPv6Address
        public String network_gateway6;

        public ClusterIpInfoForm() {
        }

        /**
         * loads domain object from the form object
         * 
         * @return
         */
        public ClusterIpInfo getClusterIpInfo() {
            ClusterIpInfo clusterIpInfo = new ClusterIpInfo();
            ClusterIpv4Setting ipv4_setting = new ClusterIpv4Setting();

            ipv4_setting.setNetworkVip(network_vip.trim());
            ipv4_setting.setNetworkGateway(network_gateway.trim());
            ipv4_setting.setNetworkNetmask(network_netmask.trim());

            List<String> network_addrs = new ArrayList<String>();
            network_addrs.add(ipv4_network_addrs1.trim());
            if (!StringUtils.isEmpty(ipv4_network_addrs2)) {
                network_addrs.add(ipv4_network_addrs2.trim());
            }
            if (!StringUtils.isEmpty(ipv4_network_addrs3)) {
                network_addrs.add(ipv4_network_addrs3.trim());
            }
            if (!StringUtils.isEmpty(ipv4_network_addrs4)) {
                network_addrs.add(ipv4_network_addrs4.trim());
            }
            if (!StringUtils.isEmpty(ipv4_network_addrs5)) {
                network_addrs.add(ipv4_network_addrs5.trim());
            }
            ipv4_setting.setNetworkAddrs(network_addrs);

            ClusterIpv6Setting ipv6_setting = new ClusterIpv6Setting();

            ipv6_setting.setNetworkVip6(network_vip6.trim());
            ipv6_setting.setNetworkGateway6(network_gateway6.trim());
            ipv6_setting.setNetworkPrefixLength(network_prefix_length);

            List<String> network_addrs6 = new ArrayList<String>();
            network_addrs6.add(ipv6_network_addrs1.trim());

            if (!StringUtils.isEmpty(ipv6_network_addrs2)) {
                network_addrs6.add(ipv6_network_addrs2.trim());
            }
            if (!StringUtils.isEmpty(ipv6_network_addrs3)) {
                network_addrs6.add(ipv6_network_addrs3.trim());
            }
            if (!StringUtils.isEmpty(ipv6_network_addrs4)) {
                network_addrs6.add(ipv6_network_addrs4.trim());
            }
            if (!StringUtils.isEmpty(ipv6_network_addrs5)) {
                network_addrs6.add(ipv6_network_addrs5.trim());
            }
            ipv6_setting.setNetworkAddrs(network_addrs6);

            clusterIpInfo.setIpv4Setting(ipv4_setting);
            clusterIpInfo.setIpv6Setting(ipv6_setting);

            return clusterIpInfo;
        }

        /**
         * load form from the domain object
         * 
         * @param getClusterIpInfo
         */
        public void load(ClusterIpInfo getClusterIpInfo) {
            if (getClusterIpInfo != null) {
                if (getClusterIpInfo.getIpv4Setting() != null) {
                    network_vip = getClusterIpInfo.getIpv4Setting().getNetworkVip();
                    network_netmask = getClusterIpInfo.getIpv4Setting().getNetworkNetmask();
                    network_gateway = getClusterIpInfo.getIpv4Setting().getNetworkGateway();

                    if (getClusterIpInfo.getIpv4Setting().getNetworkAddrs().size() >= 1) { // NOSONAR
                                                                                           // ("Suppressing Sonar violation of Use isEmpty() to check whether the collection is empty. No empty check required.")
                        ipv4_network_addrs1 = getClusterIpInfo.getIpv4Setting().getNetworkAddrs().get(0);
                    }
                    if (getClusterIpInfo.getIpv4Setting().getNetworkAddrs().size() >= 2) {
                        ipv4_network_addrs2 = getClusterIpInfo.getIpv4Setting().getNetworkAddrs().get(1);
                    }
                    if (getClusterIpInfo.getIpv4Setting().getNetworkAddrs().size() >= 3) {
                        ipv4_network_addrs3 = getClusterIpInfo.getIpv4Setting().getNetworkAddrs().get(2);
                    }
                    if (getClusterIpInfo.getIpv4Setting().getNetworkAddrs().size() >= 4) {
                        ipv4_network_addrs4 = getClusterIpInfo.getIpv4Setting().getNetworkAddrs().get(3);
                    }
                    if (getClusterIpInfo.getIpv4Setting().getNetworkAddrs().size() >= 5) {
                        ipv4_network_addrs5 = getClusterIpInfo.getIpv4Setting().getNetworkAddrs().get(4);
                    }

                    if (ipv4_network_addrs4 != null && !ipv4_network_addrs4.equals(DEFAULT_IPV4_ADDR)) {
                        this.nodeCount = 5;
                    } else if (ipv4_network_addrs2 != null && !ipv4_network_addrs2.equals(DEFAULT_IPV4_ADDR)) {
                        this.nodeCount = 3;
                    } else {
                        this.nodeCount = 1;
                    }

                } else {
                    loadIpv4SettingsDefaults();
                }
                if (getClusterIpInfo.getIpv6Setting() != null) {
                    network_vip6 = getClusterIpInfo.getIpv6Setting().getNetworkVip6();
                    network_prefix_length = getClusterIpInfo.getIpv6Setting().getNetworkPrefixLength();
                    network_gateway6 = getClusterIpInfo.getIpv6Setting().getNetworkGateway6();

                    if (getClusterIpInfo.getIpv6Setting().getNetworkAddrs().size() >= 1) { // NOSONAR
                                                                                           // ("Suppressing Sonar violation of Use isEmpty() to check whether the collection is empty. No empty check required.")
                        ipv6_network_addrs1 = getClusterIpInfo.getIpv6Setting().getNetworkAddrs().get(0);
                    }
                    if (getClusterIpInfo.getIpv6Setting().getNetworkAddrs().size() >= 2) {
                        ipv6_network_addrs2 = getClusterIpInfo.getIpv6Setting().getNetworkAddrs().get(1);
                    }
                    if (getClusterIpInfo.getIpv6Setting().getNetworkAddrs().size() >= 3) {
                        ipv6_network_addrs3 = getClusterIpInfo.getIpv6Setting().getNetworkAddrs().get(2);
                    }
                    if (getClusterIpInfo.getIpv6Setting().getNetworkAddrs().size() >= 4) {
                        ipv6_network_addrs4 = getClusterIpInfo.getIpv6Setting().getNetworkAddrs().get(3);
                    }
                    if (getClusterIpInfo.getIpv6Setting().getNetworkAddrs().size() >= 5) {
                        ipv6_network_addrs5 = getClusterIpInfo.getIpv6Setting().getNetworkAddrs().get(4);
                    }
                    int v6NodeCount = 0;
                    if (ipv6_network_addrs4 != null && !ipv6_network_addrs4.equals(DEFAULT_IPV6_ADDR)) {
                        v6NodeCount = 5;
                    } else if (ipv6_network_addrs2 != null && !ipv6_network_addrs2.equals(DEFAULT_IPV6_ADDR)) {
                        v6NodeCount = 3;
                    } else {
                        v6NodeCount = 1;
                    }
                    if (v6NodeCount > this.nodeCount) {
                        this.nodeCount = v6NodeCount;
                    }

                } else {
                    loadIpv6SettingsDefaults();
                }
            }
        }

        public void loadIpv4SettingsDefaults() {
            network_vip = DEFAULT_IPV4_ADDR;
            network_netmask = DEFAULT_NETMASK;
            network_gateway = DEFAULT_IPV4_ADDR;

            ipv4_network_addrs1 = DEFAULT_IPV4_ADDR;
            ipv4_network_addrs2 = DEFAULT_IPV4_ADDR;
            ipv4_network_addrs3 = DEFAULT_IPV4_ADDR;
        }

        public void loadIpv6SettingsDefaults() {
            network_vip6 = DEFAULT_IPV6_ADDR;
            network_prefix_length = DEFAULT_PREFIX_LEN;
            network_gateway6 = DEFAULT_IPV6_ADDR;

            ipv6_network_addrs1 = DEFAULT_IPV6_ADDR;
            ipv6_network_addrs2 = DEFAULT_IPV6_ADDR;
            ipv6_network_addrs3 = DEFAULT_IPV6_ADDR;
        }

        /**
         * alternate/custom validations
         */
        public void validate() {
            Validation.valid("ipReconfigForm", this);
            if (nodeCount >= 3) {
                Validation.required("ipReconfigForm.ipv4_network_addrs2", this.ipv4_network_addrs2);
                Validation.required("ipReconfigForm.ipv6_network_addrs2", this.ipv6_network_addrs2);
                Validation.required("ipReconfigForm.ipv4_network_addrs3", this.ipv4_network_addrs3);
                Validation.required("ipReconfigForm.ipv6_network_addrs3", this.ipv6_network_addrs3);
            }
            if (nodeCount == 5) {
                Validation.required("ipReconfigForm.ipv4_network_addrs4", this.ipv4_network_addrs4);
                Validation.required("ipReconfigForm.ipv6_network_addrs4", this.ipv6_network_addrs4);
                Validation.required("ipReconfigForm.ipv4_network_addrs5", this.ipv4_network_addrs5);
                Validation.required("ipReconfigForm.ipv6_network_addrs5", this.ipv6_network_addrs5);
            }

            ClusterIpInfo ipInfo = this.getClusterIpInfo();
            if (ipInfo != null && ipInfo.getIpv4Setting() != null && ipInfo.getIpv6Setting() != null) {
                if (ipInfo.getIpv4Setting().isDefault() && ipInfo.getIpv6Setting().isDefault()) {
                    Validation.addError(null, "validation.noConfiguration");
                }
            }

            if (ipInfo != null) { // NOSONAR ("Suppressing Sonar violation of Redundant null check of ipInfo�)
                if (ipInfo.getIpv4Setting() != null && !ipInfo.getIpv4Setting().isDefault()) {
                    if (!ipInfo.getIpv4Setting().isOnSameNetworkIPv4()) {
                        Validation.addError(null, "validation.notOnSameNwIpv4");
                    }
                }
                if (ipInfo.getIpv6Setting() != null && !ipInfo.getIpv6Setting().isDefault()) {
                    if (!ipInfo.getIpv6Setting().isOnSameNetworkIPv6()) {
                        Validation.addError(null, "validation.notOnSameNwIpv6");
                    }
                }
            }

            Set<String> dupValidationSet = new HashSet<String>();
            if (network_vip != DEFAULT_IPV4_ADDR) {
                dupValidationSet.add(network_vip);

                if (isDuplicate(dupValidationSet, ipv4_network_addrs1)) {
                    Validation.addError("ipReconfigForm.ipv4_network_addrs1", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, ipv4_network_addrs2)) {
                    Validation.addError("ipReconfigForm.ipv4_network_addrs2", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, ipv4_network_addrs3)) {
                    Validation.addError("ipReconfigForm.ipv4_network_addrs3", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, ipv4_network_addrs4)) {
                    Validation.addError("ipReconfigForm.ipv4_network_addrs4", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, ipv4_network_addrs5)) {
                    Validation.addError("ipReconfigForm.ipv4_network_addrs5", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, network_gateway)) {
                    Validation.addError("ipReconfigForm.network_gateway", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, network_netmask)) {
                    Validation.addError("ipReconfigForm.network_netmask", "validation.duplicateIpAddress");
                }

                if (isDuplicate(dupValidationSet, network_vip6)) {
                    Validation.addError("ipReconfigForm.network_vip6", "validation.duplicateIpAddress");
                }

                if (isDuplicate(dupValidationSet, ipv6_network_addrs1)) {
                    Validation.addError("ipReconfigForm.ipv6_network_addrs1", "validation.duplicateIpAddress");
                }

                if (isDuplicate(dupValidationSet, ipv6_network_addrs2)) {
                    Validation.addError("ipReconfigForm.ipv6_network_addrs2", "validation.duplicateIpAddress");
                }

                if (isDuplicate(dupValidationSet, ipv6_network_addrs3)) {
                    Validation.addError("ipReconfigForm.ipv6_network_addrs3", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, ipv6_network_addrs4)) {
                    Validation.addError("ipReconfigForm.ipv6_network_addrs4", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, ipv6_network_addrs5)) {
                    Validation.addError("ipReconfigForm.ipv6_network_addrs5", "validation.duplicateIpAddress");
                }
                if (isDuplicate(dupValidationSet, network_gateway6)) {
                    Validation.addError("ipReconfigForm.network_gateway6", "validation.duplicateIpAddress");
                }
            }
        }

        private boolean isDuplicate(Set<String> dupValidationSet, String ip) {
            boolean isDuplicate = false;
            if (!StringUtils.isEmpty(ip) && (!ip.equals(DEFAULT_IPV4_ADDR) && !ip.equals(DEFAULT_IPV6_ADDR))) {
                int expectedSize = dupValidationSet.size() + 1;
                dupValidationSet.add(ip);
                if (dupValidationSet.size() < expectedSize) {
                    isDuplicate = true;
                }
            }
            return isDuplicate;
        }

        private String selectVipforStatusQuery() {
            if (!StringUtils.isEmpty(network_vip) && !network_vip.equals(DEFAULT_IPV4_ADDR)) {
                return network_vip;
            } else {
                return network_vip6;
            }
        }
    }
}
