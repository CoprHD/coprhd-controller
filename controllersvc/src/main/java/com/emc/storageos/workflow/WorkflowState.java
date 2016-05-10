package com.emc.storageos.workflow;

public enum WorkflowState {
	CREATED, 			  // Initial state
	RUNNING, 			  // Running
	SUCCESS, 		      // Terminated with SUCCESS
	ERROR,                // Terminated with ERROR 
	ROLLING_BACK,         // Working on rolling back after error 
	SUSPENDED_ERROR,      // Suspended 
	SUSPENDED_NO_ERROR    // Suspended, but no error currently
}
