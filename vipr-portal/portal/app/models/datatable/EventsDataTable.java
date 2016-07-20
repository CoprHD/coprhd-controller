/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.Map;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.event.EventRestRep;
import com.google.common.collect.Maps;

import play.mvc.Router;
import util.datatable.DataTable;

public class EventsDataTable extends DataTable {
    // Currently the backend only shows progresses of 0 or 100, so for show this as the miminum progress

    public EventsDataTable() {
        setupTable(false);
    }

    public EventsDataTable(boolean addResourceColumn) {
        setupTable(addResourceColumn);
    }

    private void setupTable(boolean addResourceColumn) {
        addColumn("systemName").hidden();
        addColumn("id").hidden().setSearchable(false);
        addColumn("description");
        addColumn("eventStatus");
        if (addResourceColumn) {
            addColumn("resourceId").setSearchable(false).setRenderFunction("render.taskResource");
            addColumn("resourceName").hidden();
        }
        addColumn("creationTime").setRenderFunction("render.localDate");
        setDefaultSort("creationTime", "desc");

        setRowCallback("createRowLink");
    }

    // public static List<Event> fetch(URI resourceId) {
    // if (resourceId == null) {
    // return Collections.EMPTY_LIST;
    // }
    //
    // EventRestRep event = EventUtils.getEvent(resourceId);
    //
    // List<Task> dataTableTasks = Lists.newArrayList();
    // if (clientTasks != null) {
    // for (TaskResourceRep clientTask : clientTasks) {
    // dataTableTasks.add(new Task(clientTask));
    // }
    // }
    // return dataTableTasks;
    // }

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

        public Event(EventRestRep eventRestRep) {
            load(eventRestRep);
        }

        // public Event(com.emc.vipr.client.T<?> clientTask) {
        // load(clientTask.getTaskResource());
        // }

        private void load(EventRestRep eventRestRep) {
            this.name = eventRestRep.getName();
            if (eventRestRep.getCreationTime() != null) {
                this.creationTime = eventRestRep.getCreationTime().getTimeInMillis();
            }
            this.id = eventRestRep.getId().toString();
            this.eventStatus = eventRestRep.getStatus();
            if (eventRestRep.getResource() != null && eventRestRep.getResource().getId() != null) {
                this.resourceId = eventRestRep.getResource().getId().toString();
                this.resourceType = ResourceType.fromResourceId(this.resourceId).toString();
                this.resourceName = eventRestRep.getResource().getName();
            }

            this.description = eventRestRep.getDescription();

            // Create Row Link
            Map<String, Object> args = Maps.newHashMap();
            args.put("eventId", id);
            this.rowLink = Router.reverse("Events.details", args).url;
        }
    }
}
