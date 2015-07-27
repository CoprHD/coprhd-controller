/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.security.mail.MailHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.email.ApprovalRequiredEmail;
import com.emc.sa.email.ApprovalUpdatedEmail;
import com.emc.sa.email.OrderUpdatedEmail;
import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.TenantPreferences;
import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.sa.util.SSLUtil;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

@Component
public class NotificationManagerImpl implements NotificationManager {
    
    private static final Logger log = Logger.getLogger(NotificationManagerImpl.class);

    public static final String SMTP_PROPERTY_PREFIX = "system_connectemc_smtp_";

    public static final String NETWORK_VIRTUAL_IP = "network_vip";
    
    public static final String NETWORK_VIP = "network_vip";
    public static final String NETWORK_VIP6 = "network_vip6";

    @Autowired
    private UserPreferenceManager userPreferenceManager;
    
    @Autowired
    private CatalogPreferenceManager catalogPreferenceManager;
    
    @Autowired
    protected CoordinatorClient coordinatorClient;
    private MailHelper mailHelper;

    @PostConstruct
    private void init() {
        SSLUtil.trustAllSSLCertificates();
        SSLUtil.trustAllHostnames();
    }    

    public void notifyUserOfOrderStatus(Order order, CatalogService service, ApprovalRequest approvalRequest) {
        if (isUserNotificationsEnabled(order)) {
            sendOrderStatusEmail(order, service, approvalRequest);
        }
        else {
            if (order != null && service != null) {
                log.info(String.format("(%s) %s %s (Order: %s)", order.getSubmittedByUserId(), service.getTitle(),
                        order.getOrderStatus(), order.getId()));
            }
        }        
    }
    
    public void notifyApproversOfApprovalRequest(Order order, CatalogService service, ApprovalRequest approval) {
        if (isApproverNotificationsEnabled(order)) {
            sendApproversApprovalRequestEmail(order, service, approval);
        }
        else {
            if (order != null && service != null) {
                log.info(String.format("(Approvers) %s requires approval for %s (Order: %s)", order.getSubmittedByUserId(),
                        service.getTitle(), order.getId()));
            }
        }

        String approvalUrl = getApprovalUrl(order);
        if (approvalUrl != null) {
            notifyApprovalService(approvalUrl, order, approval);
        }
    }  
    
    public void notifyUserOfApprovalStatus(Order order, CatalogService service, ApprovalRequest approval) {
        if (isUserNotificationsEnabled(order)) {
            sendApprovalStatusEmail(order, service, approval);
        }
        else {
            if (order != null && service != null) {
                log.info(String.format("(%s) %s has been %s by %s (Order: %s)", order.getSubmittedByUserId(), service.getTitle(),
                        approval.getApprovalStatus(), approval.getApprovedBy(), order.getId()));
            }
        }
    }
    
    private boolean isUserNotificationsEnabled(Order order) {
        return getUserEmailAddress(order) != null;
    }

    private boolean isApproverNotificationsEnabled(Order order) {
        return getApproversEmailAddress(order) != null;
    }    
    
    private String getUserEmailAddress(Order order) {
        UserPreferences prefs = userPreferenceManager.getPreferences(order.getSubmittedByUserId());
        if ((prefs != null) && Boolean.TRUE.equals(prefs.getNotifyByEmail())) {
            return StringUtils.trimToNull(prefs.getEmail());
        }
        return null;
    }

    private String getApproversEmailAddress(Order order) {
        TenantPreferences prefs = catalogPreferenceManager.getPreferencesByTenant(order.getTenant());
        return (prefs != null) ? StringUtils.trimToNull(prefs.getApproverEmail()) : null;
    }

    private String getApprovalUrl(Order order) {
        TenantPreferences prefs = catalogPreferenceManager.getPreferencesByTenant(order.getTenant());
        return (prefs != null) ? StringUtils.trimToNull(prefs.getApprovalUrl()) : null;
    }       
    
    public void sendOrderStatusEmail(Order order, CatalogService catalogService, ApprovalRequest approvalRequest) {
        
        String virtualIp = getNetworkVirtualIP();
        
        OrderUpdatedEmail orderUpdatedEmail = new OrderUpdatedEmail(order, catalogService, approvalRequest, virtualIp);
        
        String to = getUserEmailAddress(order);
        String subject = orderUpdatedEmail.getTitle();
        String html = orderUpdatedEmail.getEmailContent();    

        getMailHelper().sendMailMessage(to, subject, html);
    }    
    
    public void sendApproversApprovalRequestEmail(Order order, CatalogService catalogService, ApprovalRequest approvalRequest) {

        String virtualIp = getNetworkVirtualIP();
        
        ApprovalRequiredEmail approvalRequiredEmail = new ApprovalRequiredEmail(order, catalogService, approvalRequest, virtualIp);
        
        String to = getApproversEmailAddress(order);
        String subject = approvalRequiredEmail.getTitle();
        String html = approvalRequiredEmail.getEmailContent();

        getMailHelper().sendMailMessage(to, subject, html);
    }
    
    public void sendApprovalStatusEmail(Order order, CatalogService catalogService, ApprovalRequest approvalRequest) {

        String virtualIp = getNetworkVirtualIP();
        
        ApprovalUpdatedEmail approvalUpdatedEmail = new ApprovalUpdatedEmail(order, catalogService, approvalRequest, virtualIp);
        
        String to = getUserEmailAddress(order);
        String subject = approvalUpdatedEmail.getTitle();
        String html = approvalUpdatedEmail.getEmailContent();

        getMailHelper().sendMailMessage(to, subject, html);
    }
    
    private String getNetworkVirtualIP() {
        Map<String, String> props = getPropertiesFromCoordinator();
        return props.get(NETWORK_VIP);
    }

    private Map<String, String> getPropertiesFromCoordinator() {
        com.emc.storageos.model.property.PropertyInfo propertyInfo = coordinatorClient.getPropertyInfo();
        if (propertyInfo != null) {
            return propertyInfo.getAllProperties();
        }
        return Maps.newHashMap();
    }

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }    
    
    public boolean containsSmtpSettings(Map<String, String> properties) {
        for (String key : properties.keySet()) {
            if (key.startsWith(SMTP_PROPERTY_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private synchronized MailHelper getMailHelper() {
        if (mailHelper == null) {
            mailHelper = new MailHelper(this.coordinatorClient);
        }

        return mailHelper;
    }

    
    private void notifyApprovalService(String approvalUrl, Order order, ApprovalRequest approval) {
        
        try
        {
            ApprovalRestRep approvalRestRep = map(approval);
            
            ClientConfig config = new DefaultClientConfig();
            config.getClasses().add(JacksonJaxbJsonProvider.class);
            Client client = Client.create(config);
            WebResource.Builder webResource = client.resource(UriBuilder.fromUri(approvalUrl).build()).type(MediaType.APPLICATION_JSON);
            
            ClientResponse response = webResource.post(ClientResponse.class, approvalRestRep);
            
            if (isError(response)) {
                log.error(String.format("Approval POST failed: %s, %s %s", approvalUrl, response.getStatus(), response.getEntity(String.class)));
            }
            else {
                log.debug(String.format("Approval POST succeeded: %s, %s %s", approvalUrl, response.getStatus(), response.getEntity(String.class)));
            }
        } catch(ClientHandlerException e) {
            log.error(String.format("Approval POST failed: %s, %s, %s", approvalUrl, e.getCause(), e.getMessage()));
        }
    }
    
    public ApprovalRestRep map(ApprovalRequest from) {
        if (from == null) {
            return null;
        }
        ApprovalRestRep to = new ApprovalRestRep();
        mapDataObjectFields(from, to);
        
        if (from.getTenant() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, uri(from.getTenant())));
        }  
        
        if (from.getOrderId() != null) {
            to.setOrder(toRelatedResource(ResourceTypeEnum.ORDER, from.getOrderId()));
        }
        
        
        to.setApprovedBy(from.getApprovedBy());
        to.setDateActioned(from.getDateActioned());
        to.setMessage(from.getMessage());
        to.setApprovalStatus(from.getApprovalStatus());

        return to;
    }    

    protected boolean isError(ClientResponse response) {
        int statusCode = response.getStatus();
        if (statusCode >= 400) {
            return true;
        }
        else {
            return false;
        }
    }    

}
