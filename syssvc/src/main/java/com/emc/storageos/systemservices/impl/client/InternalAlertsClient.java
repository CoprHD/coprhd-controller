/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.client;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import static com.emc.storageos.coordinator.client.model.Constants.CONTROL_NODE_SYSSVC_ID_PATTERN;
import com.emc.storageos.model.event.EventParameters;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.logsvc.LogRequestParam;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class InternalAlertsClient {
    public static final URI URI_SEND_INTERNAL_ALERT = URI.create
            ("/callhome/internal/alert");
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    private static final Logger _log = LoggerFactory.getLogger(InternalAlertsClient
            .class);

    private String _host;
    private int _sysServicePort;
    private CoordinatorClient coordinatorClient;
    private final static String SYS_SVC_LOOKUP_KEY = "syssvc";
    private static final String SERVICE_LOOKUP_VERSION = "1";

    public InternalAlertsClient(String host, int port) {
        _host = host;
        _sysServicePort = port;
    }

    public InternalAlertsClient(String host, int port, InternalApiSignatureKeyGenerator keyGenerator, int timeout) {
        this(host, port);
        SysClientFactory.setKeyGenerator(keyGenerator);
        SysClientFactory.setTimeout(timeout);
        SysClientFactory.init();
    }

    public InternalAlertsClient(String host, int port, InternalApiSignatureKeyGenerator keyGenerator, int timeout,
            CoordinatorClient coordinatorClient) {
        this(host, port);
        this.coordinatorClient = coordinatorClient;
        SysClientFactory.setKeyGenerator(keyGenerator);
        SysClientFactory.setTimeout(timeout);
        SysClientFactory.init();
    }

    private SysClientFactory.SysClient getSysClient() {
        String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT, _host,
                _sysServicePort);
        return SysClientFactory.getSysClient(URI.create(baseNodeURL));
    }

    /**
     * Returns client for active controller VMs syssvc instance.
     */
    private SysClientFactory.SysClient getControllerSysClient() {
        List<Service> services = coordinatorClient.locateAllServices(
                SYS_SVC_LOOKUP_KEY,
                SERVICE_LOOKUP_VERSION, null, null);
        URI hostUri = null;
        for (Service service : services) {
            try {
                // service could be null, if so get next service.
                if (service != null && CONTROL_NODE_SYSSVC_ID_PATTERN.matcher(service.getId()).matches()) {
                    _log.info("Using {} to send alert event", service.getId());
                    hostUri = service.getEndpoint();
                    String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT,
                            hostUri.getHost(), hostUri.getPort());
                    return SysClientFactory.getSysClient(URI.create(baseNodeURL));
                }
            } catch (SysClientException exception) {
                _log.error("InternalAlertsClient. Cannot connect to host: {}"
                        , hostUri != null ? hostUri.toString() : "");
            }
        }
        throw APIException.internalServerErrors.getObjectError("controller sysvsc instance", null);
    }

    private String getDateString(Date date) {
        if (date == null) {
            return null;
        }
        return (new SimpleDateFormat(DATE_TIME_FORMAT)).format(date);
    }

    public void sendInternalAlert(String src,
                                  int eventId,
                                  List<String> nodeIds,
                                  List<String> nodeNames,
                                  List<String> logNames,
                                  int severity,
                                  Date start,
                                  Date end,
                                  String msgRegex,
                                  String user,
                                  String contact) throws SysClientException {

        UriBuilder b = UriBuilder.fromUri(URI_SEND_INTERNAL_ALERT);
        b.queryParam("source", src).queryParam("event_id", Integer.toString(eventId));
        if (nodeIds != null) {
            for (String nodeId : nodeIds) {
                b.queryParam(LogRequestParam.NODE_ID, nodeId);
            }
        }

        if (nodeNames != null) {
            for (String nodeName : nodeNames) {
                b.queryParam(LogRequestParam.NODE_NAME, nodeName);
            }
        }

        if (logNames != null) {
            for (String logName : logNames) {
                b.queryParam(LogRequestParam.LOG_NAME, logName);
            }
        }

        b.queryParam(LogRequestParam.SEVERITY, Integer.toString(severity))
                .queryParam(LogRequestParam.START_TIME, getDateString(start))
                .queryParam(LogRequestParam.END_TIME, getDateString(end))
                .queryParam(LogRequestParam.MSG_REGEX, msgRegex);

        URI uri = b.build();
        EventParameters eventParams = new EventParameters(user, contact);
        SysClientFactory.SysClient sysClient = coordinatorClient != null ?
                getControllerSysClient() : getSysClient();
        sysClient.post(uri, String.class, eventParams);
    }
}
