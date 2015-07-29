/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.ApprovalStatus;
import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.URIUtil;

public class ApprovalRequestTest extends BaseModelTest<ApprovalRequest> {

    private static final Logger _logger = Logger.getLogger(ApprovalRequestTest.class);

    public ApprovalRequestTest() {
        super(ApprovalRequest.class);
    }

    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist ApprovalRequest test");

        ApprovalRequest model = new ApprovalRequest();
        model.setLabel("foo");
        model.setApprovalStatus(ApprovalStatus.APPROVED.name());
        model.setApprovedBy("billy");
        Date d = new Date();
        model.setDateActioned(d);
        model.setMessage("my message");
        URI orderId = URIUtil.createId(Order.class);
        model.setOrderId(orderId);
        model.setTenant(DEFAULT_TENANT);

        save(model);
        model = findById(model.getId());

        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals(ApprovalStatus.APPROVED.name(), model.getApprovalStatus());
        Assert.assertEquals("billy", model.getApprovedBy());
        Assert.assertEquals(d, model.getDateActioned());
        Assert.assertEquals("my message", model.getMessage());
        Assert.assertEquals(orderId, model.getOrderId());
        Assert.assertEquals(DEFAULT_TENANT, model.getTenant());

    }

    @Test
    public void testMultiTenant() throws Exception {
        _logger.info("Starting multi tenant ApprovalRequest test");

        ModelClient modelClient = getModelClient();

        ApprovalRequest ar1 = create("t1", "foo1", ApprovalStatus.PENDING);
        modelClient.save(ar1);

        ApprovalRequest ar2 = create("t1", "bar2", ApprovalStatus.REJECTED);
        modelClient.save(ar2);

        ApprovalRequest ar3 = create("t2", "foo3", ApprovalStatus.REJECTED);
        modelClient.save(ar3);

        ApprovalRequest ar4 = create("t2", "bar4", ApprovalStatus.REJECTED);
        modelClient.save(ar4);

        ApprovalRequest ar5 = create("t2", "foo5", ApprovalStatus.APPROVED);
        modelClient.save(ar5);

        ApprovalRequest ar6 = create("t3", "bar6", ApprovalStatus.PENDING);
        modelClient.save(ar6);

        List<ApprovalRequest> approvalRequests = modelClient.approvalRequests().findAll("t1");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(2, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findAll("t2");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(3, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findAll("t3");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByLabel("t1", "foo");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByLabel("t1", "ba");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByLabel("t1", "ab");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(0, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByLabel("t2", "ba");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByLabel("t2", "fo");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(2, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByLabel("t3", "ba");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByLabel("t3", "foo");
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(0, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t1", ApprovalStatus.PENDING);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t1", ApprovalStatus.APPROVED);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(0, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t1", ApprovalStatus.REJECTED);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t2", ApprovalStatus.PENDING);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(0, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t2", ApprovalStatus.APPROVED);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t2", ApprovalStatus.REJECTED);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(2, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t3", ApprovalStatus.PENDING);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(1, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t3", ApprovalStatus.APPROVED);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(0, approvalRequests.size());

        approvalRequests = modelClient.approvalRequests().findByApprovalStatus("t3", ApprovalStatus.REJECTED);
        Assert.assertNotNull(approvalRequests);
        Assert.assertEquals(0, approvalRequests.size());

    }

    private static ApprovalRequest create(String tenant, String label, ApprovalStatus status) {
        ApprovalRequest model = new ApprovalRequest();
        model.setLabel(label);
        model.setApprovalStatus(status.name());
        model.setApprovedBy("billy");
        Date d = new Date();
        model.setDateActioned(d);
        model.setMessage("my message");
        URI orderId = URIUtil.createId(Order.class);
        model.setOrderId(orderId);
        model.setTenant(tenant);
        return model;
    }

}
