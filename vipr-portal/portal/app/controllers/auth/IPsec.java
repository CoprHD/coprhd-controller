/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package controllers.auth;

import com.emc.storageos.model.ipsec.IPsecStatus;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

import controllers.Common;
import controllers.util.ViprResourceController;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.util.CollectionUtils;
import play.mvc.With;
import util.IPsecUtils;
import util.MessagesUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"),
        @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class IPsec extends ViprResourceController {
    protected static final String INVALID_IPSEC_CONFIG_VERSION = "Invalid IPsec configuration version.";
    protected static final String IPSEC_KEY_ROTATION_ERROR = "ipsec.key.rotation.error";
    protected static final String IPSEC_KEY_ROTATION_SUCCESS = "ipsec.key.rotation.success";

    public static void ipsec() {
        render();
    }

    public static void ipsecStatus() {
        IPsecStatus ipsecStatus = IPsecUtils.getIPsecStatus();
        IPSecStatusInfo ipSecStatusInfo = new IPSecStatusInfo(ipsecStatus);
        render(ipSecStatusInfo);
    }

    public static void rotateIPsecKeys() {
        try {
            if (StringUtils.isBlank(IPsecUtils.rotateIPsecKey())) {
                flash.error(MessagesUtils.get(IPSEC_KEY_ROTATION_ERROR, INVALID_IPSEC_CONFIG_VERSION));
                ipsec();
            }

            flash.success(MessagesUtils.get(IPSEC_KEY_ROTATION_SUCCESS));
            ipsec();
        } catch (Exception e) {
            flash.error(MessagesUtils.get(IPSEC_KEY_ROTATION_ERROR, e.getMessage()));
            ipsec();
        }
    }

    public static class IPsecFailedNodeInfo {
        public static final String DISCONNECTED_NODE_STATE = "Degraded";

        public String node; //NOSONAR("Suppressing Sonar violation of node being public member. This is needed for the html template. Visibility Modifier")
        public String status; //NOSONAR("Suppressing Sonar violation of status being public member. This is needed for the html template. Visibility Modifier")

        public IPsecFailedNodeInfo() {

        }

        public IPsecFailedNodeInfo(String node, String status) {
            this.node = node;
            this.status = status;
        }
    }

    public static class IPSecStatusInfo {
        protected static final String DATE_TIME_FORMAT = "yyyy MMMMM dd hh:mm:ss:SSS aaa";
        protected static final String UNKNOWN_DATE_TIME = "Unknown";

        public String status; //NOSONAR("Suppressing Sonar violation of status being public member. This is needed for the html template. Visibility Modifier")
        public String configGeneratedDate; //NOSONAR("Suppressing Sonar violation of configGeneratedDate being public member. This is needed for the html template. Visibility Modifier")
        public List<IPsecFailedNodeInfo> failureNodes; //NOSONAR("Suppressing Sonar violation of failureNodes being public member. This is needed for the html template. Visibility Modifier")

        public IPSecStatusInfo(IPsecStatus ipsecStatus) {
            status = ipsecStatus.getStatus();
            configGeneratedDate = convertToDateTime(ipsecStatus.getVersion());
            failureNodes = new ArrayList<IPsecFailedNodeInfo>();
            if (!CollectionUtils.isEmpty(ipsecStatus.getDisconnectedNodes())) {
                for (String disconnectedNode : ipsecStatus.getDisconnectedNodes()) {
                    IPsecFailedNodeInfo ipsecFailedNodeInfo = new IPsecFailedNodeInfo(disconnectedNode,
                            IPsecFailedNodeInfo.DISCONNECTED_NODE_STATE);
                    failureNodes.add(ipsecFailedNodeInfo);
                }
            }
        }

        private String convertToDateTime(String configVersion) {
            if (StringUtils.isBlank(configVersion)) {
                return UNKNOWN_DATE_TIME;
            }

            long geTime= Long.parseLong(configVersion);

            Date date = new Date(geTime);
            DateTime dateTime = new DateTime(date.getTime());
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(DATE_TIME_FORMAT);

            return dateTimeFormatter.print(dateTime);
        }
    }
}
