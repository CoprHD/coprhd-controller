package com.emc.storageos.db.client.model.uimodels;

import java.net.URI;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;

@Cf("EphemeralObject")
public class EphemeralObject extends DataObject {
    static final long serialVersionUID = -5278839624050514419L;
    
    private URI resourceId;
    private URI executionStateId;
    private URI scheduledEventId;
    
    @Name("resourceId")
    public URI getResourceId() {
        return resourceId;
    }
    
    public void setResourceId(URI resource) {
        this.resourceId = resource;
        setChanged("resourceId");
    }
    
    @Name("executionStateId")
    @RelationIndex(cf = "RelationIndex", type = ExecutionState.class)
    public URI getExecutionStateId() {
        return executionStateId;
    }
    public void setExecutionStateId(URI executionState) {
        this.executionStateId = executionState;
        setChanged("executionStateId");
    }

    @Name("scheduledEventId")
    @RelationIndex(cf = "RelationIndex", type = ScheduledEvent.class)
    public URI getScheduledEventId() {
        return scheduledEventId;
    }

    public void setScheduledEventId(URI scheduledEventId) {
        this.scheduledEventId = scheduledEventId;
        setChanged("scheduledEventId");
    }
    
}
