/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.monitoring;

import java.net.URI;

/**
 * Defines an interface for providing the data for {@link Event events} recorded
 * in the database. Implementation specific event monitors can define concrete
 * classes that implement this interface and encapsulate the implementation
 * specific logic for providing the event data. These classes can then record
 * the event using the {@link RecordableEventManager}.
 */
public interface RecordableEvent {

	/**
	 * Get the id for the tenant to which the resource impacted by the event
	 * belongs.
	 * 
	 * @return The tenant id for the event.
	 */
	public URI getTenantId();

	/**
	 * Get the user id for the event.
	 * 
	 * @return The user id for the event.
	 */
	public URI getUserId();

	/**
	 * Get the id for the project to which the resource impacted by the event
	 * belongs.
	 * 
	 * @return The project id for the event.
	 */
	public URI getProjectId();

	/**
	 * Get the vpool associated with the resource impacted by the event.
	 * 
	 * @return The vpool for the event.
	 */
	public URI getVirtualPool();

	/**
	 * Get the identifier for the service that generated the event.
	 * 
	 * @return The service identifier for the event.
	 */
	public String getService();

	/**
	 * Get the id of the resource impacted by the event.
	 * 
	 * @return The resource id for the event.
	 */
	public URI getResourceId();

	/**
	 * Get the event description
	 * 
	 * @return The event description.
	 */
	public String getDescription();

	/**
	 * Get the time the event occurred.
	 * 
	 * @return The time the event occurred.
	 */
	public long getTimestamp();

	/**
	 * Get the event type.
	 * 
	 * @return The event type.
	 */
	public String getType();

	/**
	 * Get any extension data for the event.
	 * 
	 * @return The event extension data.
	 */
	public String getExtensions();

	/**
	 * Get event identifier
	 * 
	 * @return
	 */
	public String getEventId();

	/**
	 * Get event severity
	 * 
	 * @return
	 */
	public String getSeverity();

	/**
	 * Get Alert Type
	 * 
	 * @return
	 */
	public String getAlertType();

	/**
	 * Get Record Type : ENUM having values Event or Alert
	 * 
	 * @return The record type
	 */
	public String getRecordType();

	/**
	 * Get Native GUID
	 * 
	 * @return The NativeGuid
	 */
	public String getNativeGuid();

	/**
	 * Retrieves the Operational Status Descriptions as a String available as
	 * part of Indication provided
	 *
	 * @return
	 */
	public String getOperationalStatusDescriptions();

	/**
	 * Retrieves the Operational Status Codes as a String available as part of
	 * Indication provided
	 *
	 * @return
	 */
	public String getOperationalStatusCodes();


    /**
     * Identifier of the source of the event
     * 
     * @return The event Source
     */
    public String getSource();
}
