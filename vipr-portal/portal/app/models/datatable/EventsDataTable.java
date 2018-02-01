/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.event.EventRestRep;

import util.datatable.DataTable;

public class EventsDataTable extends DataTable {

    public EventsDataTable() {
        setupTable(false);
    }

    public EventsDataTable(boolean addResourceColumn) {
        setupTable(addResourceColumn);
    }

    private void setupTable(boolean addResourceColumn) {
        addColumn("systemName").hidden();
        addColumn("warning").setCssClass("none");
        if (addResourceColumn) {
            addColumn("resourceId").setSearchable(false).setRenderFunction("render.taskResource");
            addColumn("resourceName").hidden();
        }
        addColumn("name").setRenderFunction("render.actionableEvent");
        addColumn("eventStatus");
        addColumn("id").hidden();
        addColumn("creationTime").setRenderFunction("render.localDate");
        setDefaultSort("creationTime", "desc");
        sortAllExcept("id");
        setRowCallback("createRowLink");
    }

    public static class Event {
        public String name;
        public String rowLink;
        public String resourceId;
        public String resourceType;
        public String resourceName;
        public String id;
        public String description;
        public Long creationTime;
        public String eventStatus;
        public String eventCode;
        public String warning;

        public Event(EventRestRep eventRestRep) {
            load(eventRestRep);
        }

        private void load(EventRestRep eventRestRep) {
            this.warning = eventRestRep.getWarning();
            this.name = eventRestRep.getName();
            if (eventRestRep.getCreationTime() != null) {
                this.creationTime = eventRestRep.getCreationTime().getTimeInMillis();
            }
            this.id = eventRestRep.getId().toString();
            this.eventStatus = eventRestRep.getEventStatus();
            this.eventCode = eventRestRep.getEventCode();
            if (eventRestRep.getResource() != null && eventRestRep.getResource().getId() != null) {
                this.resourceId = eventRestRep.getResource().getId().toString();
                this.resourceType = ResourceType.fromResourceId(this.resourceId).toString();
                this.resourceName = eventRestRep.getResource().getName();
            }

            this.description = eventRestRep.getDescription();
        }
    }
}
