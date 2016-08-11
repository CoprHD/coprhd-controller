/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.ScheduledEventRestRep;
import controllers.catalog.Approvals;
import util.datatable.DataTable;

/**
 * @author Chris Dail
 */
public class ApprovalsDataTable extends DataTable {

    public ApprovalsDataTable() {
        addColumn("orderNumber");
        addColumn("serviceTitle").setRenderFunction("renderLink");
        addColumn("status").setRenderFunction("render.approvalStatus");
        addColumn("orderUserName");
        addColumn("createdDate").setRenderFunction("render.localDate");
        addColumn("recurring").setRenderFunction("render.boolean");;
        addColumn("approvedBy");
        sortAll();
        setDefaultSort("createdDate", "desc");
        setRowCallback("createRowLink");
    }

    public static class ApprovalRequestInfo {
        public String id;
        public String orderNumber;
        public String rowLink;
        public String status;
        public String serviceTitle;
        public String orderUserName;
        public boolean recurring;

        public Long createdDate;
        public Long dateActioned;
        public String message;
        public String approvedBy;

        public ApprovalRequestInfo() {
        }

        public ApprovalRequestInfo(ApprovalRestRep approval, OrderRestRep order, CatalogServiceRestRep service, ScheduledEventRestRep scheduledEvent) {
            this.id = approval.getId().toString();
            this.rowLink = createLink(Approvals.class, "edit", "id", id);
            this.status = approval.getApprovalStatus();
            this.message = approval.getMessage();
            this.approvedBy = approval.getApprovedBy();
            this.recurring = (scheduledEvent!= null && scheduledEvent.getScheduleInfo().getReoccurrence() != 1);

            if (approval.getCreationTime() != null) {
                this.createdDate = approval.getCreationTime().getTime().getTime();
            }
            if (approval.getDateActioned() != null) {
                this.dateActioned = approval.getDateActioned().getTime();
            }

            if (order != null) {
                this.orderNumber = order.getOrderNumber();
                this.orderUserName = order.getSubmittedBy();
            }
            if (service != null) {
                this.serviceTitle = service.getTitle();
            }

        }
    }
}
