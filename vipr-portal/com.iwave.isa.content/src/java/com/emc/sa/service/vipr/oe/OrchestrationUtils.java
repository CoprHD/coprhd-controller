package com.emc.sa.service.vipr.oe;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.oe.gson.ViprTask;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

//TODO: move log messages to separate file (for internationalization)

public class OrchestrationUtils {

    //TODO: externalize these values:
    private static final int TASK_CHECK_TIMEOUT = 3600;  // mins
    private static final int TASK_CHECK_INTERVAL = 10; // secs

    private static final Gson gson = new Gson();

    public static void sleep(int i) {
        try {
            Thread.sleep(i*1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        } 
    }

    public static void waitForViprTasks(List<URI> tasksStartedByOe, ViPRCoreClient client) {

        if( tasksStartedByOe.isEmpty()) {
            return;
        }  
        ExecutionUtils.currentContext().logInfo("Orchestration Engine Workflow complete.  " +
                "Waiting for Tasks in ViPR to finish that were started by " +
                "Orchestration Engine workflow.");

        long startTime = System.currentTimeMillis();
        boolean allTasksDone = false;

        while(!allTasksDone) {
            allTasksDone = true;
            for(URI taskId : tasksStartedByOe) {              
                String state = client.tasks().get(taskId).getState();
                if(state.equals("error")) {
                    throw new IllegalStateException("One or more tasks " +
                            " started by Orchestration Engine reported an error.");
                }
                if(!state.equals("ready")) {
                    allTasksDone = false;
                    break;
                }
            }
            OrchestrationUtils.sleep(TASK_CHECK_INTERVAL);

            if( (System.currentTimeMillis() - startTime)
                    > TASK_CHECK_TIMEOUT*60*1000 ) {
                throw new IllegalStateException("Timed out waiting for Task(s) started " +
                    "by Orchestration Engine to complete.");
            }
        }
    }

    public static List<URI> updateOrder(String results,ViPRCoreClient client) {
        try {   // is result a List of Tasks?
            ViprOperation viprOperation = gson.fromJson(results,ViprOperation.class);
            if(viprOperation != null && viprOperation.isValid()) {
                OrchestrationUtils.updateAffectedResources(viprOperation);
                return OrchestrationUtils.updateNewTasks(viprOperation, client);
            }
        } catch(JsonSyntaxException e) {
            // could not parse JSON as list of Tasks
        }

        try {   // is result a single Task?
            ViprTask task = gson.fromJson(results,ViprTask.class);
            if(task != null && task.isValid()) {
                OrchestrationUtils.updateAffectedResources(task);
                URI taskId = updateNewTask(task,client);
                return (taskId != null) ? Collections.singletonList(taskId) : Collections.emptyList();
            }
        } catch(JsonSyntaxException e) {
            // could not parse JSON as a Task
        }

        // could not parse.  Treat as text message and log it
        ExecutionUtils.currentContext().logInfo(results);
        return Collections.emptyList();
    }

    public static void updateAffectedResources(ViprOperation viprOperation) {
        if(viprOperation != null) {
            for(ViprTask viprTask:viprOperation.getTask()) { 
                updateAffectedResources(viprTask);
            }
        }
    }

    public static void updateAffectedResources(ViprTask viprTask) {
        StringSet currentResources = ExecutionUtils.currentContext().
                getExecutionState().getAffectedResources();
        if(!currentResources.contains(viprTask.getResource().getId())) {
            currentResources.add(viprTask.getResource().getId());
        }
    }
    
    private static List<URI> updateNewTasks(ViprOperation viprOperation, ViPRCoreClient client) {
        List<URI> urisToReturn = new ArrayList<>();
        for(String taskId : viprOperation.getTaskIds() ) {
            URI uriOfAddedTask = addOrderIdTag(taskId,client);
             if(uriOfAddedTask != null) {
                 urisToReturn.add(uriOfAddedTask);
            }
        }
        return urisToReturn;
    }
    
    private static URI updateNewTask(ViprTask viprTask, ViPRCoreClient client) {
        return addOrderIdTag(viprTask.getId(),client);
    }

    private static URI addOrderIdTag(String task, ViPRCoreClient client) {
        Order order = ExecutionUtils.currentContext().getOrder();
        if (order == null) {
            ExecutionUtils.currentContext().logWarn("OE was unable to associate a task started " +
                    "by OE to this order.  Could not locate Order.");
            return null;
        }      
        try {
            URI taskUri = new URI(task);
            if (order.getId() != null) {
                MachineTagUtils.setTaskOrderIdTag(client, taskUri, order.getId().toString());
            }
            MachineTagUtils.setTaskOrderNumberTag(client, taskUri, order.getOrderNumber());
            return taskUri;
        }
        catch(ServiceErrorException e) {
            ExecutionUtils.currentContext().logWarn("OE was unable to associate a task started " +
                    "by OE to this order.  Task ID is '" + task + "'  " + e.getMessage());
            return null;
        }
        catch (URISyntaxException e) {
            ExecutionUtils.currentContext().logWarn("OE was unable to associate a task started " +
                    "by OE to this order.  The Task ID was not a valid URI.  Task ID is '" + 
                    task + "'  " + e.getMessage());
            return null;
        }
    }
}

