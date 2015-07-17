package com.emc.storageos.workflow;

import static org.junit.Assert.*;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ControllersvcTestBase;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocatorTest;

/**
 * This Junit uses the ControllersvcTestBase to start controllersvc and run it within the Junit.
 * Please see that class for required environmental setup / restrictions.
 * The controllersvc is started from within setup().
 *
 */
public class WorkflowTest extends ControllersvcTestBase implements Controller  {
    private static final URI nullURI = NullColumnValueGetter.getNullURI();
    protected static final Logger log = LoggerFactory.getLogger(WorkflowTest.class);
    
    // Set of injected failure steps. It's level * 100 + step
    private Set<Integer> injectedFailures = new HashSet<Integer>();
    private boolean hasInjectedFailure(int level, int step) {
        return injectedFailures.contains(level*100+step);
    }
    private void addInjectedFailure(int level, int step) {
        injectedFailures.add(level*100+step);
    }
    private void removeInjectedFailure(int level, int step) {
        injectedFailures.remove(level*100+step);
    }
    
    @Before
    public void setup() {
        startControllersvc();
        // Add our controller to the set of supported controllers.
        Set<Controller> controllerSet = new HashSet<Controller>();
        controllerSet.addAll(dispatcher.getControllerMap().values());
        controllerSet.add(this);
        dispatcher.setController(controllerSet);
    }
    
    private static Map<String, WorkflowState> taskStatusMap = new HashMap<String, WorkflowState>();

    @Test
    /**
     * This test a simple one step passing workflow.
     */
    public void test1() {
        log.info("Test1 started");
        Object[] args = new Object[1];
        String taskId = UUID.randomUUID().toString();
        args[0] = taskId;
        taskStatusMap.put(taskId, WorkflowState.CREATED);
        Workflow workflow = workflowService.getNewWorkflow(this, "test1", false, taskId);
        workflow.createStep("test1", "nop", null, nullURI, this.getClass().getName(), false, this.getClass(), 
                nopMethod(1, 1), nopMethod(1, 1), null);
        workflow.executePlan(null, "success", new WorkflowCallback(), args, null, null);
        WorkflowState state = waitOnWorkflowComplete(taskId);
        log.info(String.format("task %s state %s", taskId, state));
        assertTrue(state == WorkflowState.SUCCESS);
    }
    
    @Test
    /**
     * This tests a three level hierarchical workflow that passes.
     */
    public void test2() {
        log.info("Test2 started");
        // Don't cause in failures
        injectedFailures.clear();
        String taskId = UUID.randomUUID().toString();
        Workflow workflow = generate3StepWF(0, 3, taskId);
        WorkflowState state = waitOnWorkflowComplete(taskId);
        log.info("Top level workflow state: " + state);
        
    }
    
    @Test
    /**
     * This tests a three level hierarchical workflow where the lowest level last step fails.
     * After the workflow suspends, remove the error and resume the workflow.
     */
    public void test3() {
        log.info("Test started");
        // Don't cause in failures
        injectedFailures.clear();
        addInjectedFailure(2,3);  // level 2, step 3
        String taskId = UUID.randomUUID().toString();
        Workflow workflow = generate3StepWF(0, 3, taskId);
        WorkflowState state = waitOnWorkflowComplete(taskId);
        log.info("Top level workflow state: " + state);
        if (state == WorkflowState.SUSPENDED_ERROR) {
            // clear the error and try and resume.
            injectedFailures.clear();
            String resumeTaskId = UUID.randomUUID().toString();
            workflowService.resumeWorkflow(workflow.getWorkflowURI(), resumeTaskId);
            taskStatusMap.put(taskId, WorkflowState.CREATED);
            state = waitOnWorkflowComplete(taskId);
            log.info("Top level workflow state after resume: " + state);
        }
    }
    
    @Ignore
    @Test
    /**
     * This tests a three level hierarchical workflow where the lowest level last step fails.
     * After the workflow suspends, rollback the workflow.
     */
    public void test4() {
        log.info("Test started");
        // Don't cause in failures
        injectedFailures.clear();
        addInjectedFailure(2,3);  // level 2, step 3
        String taskId = UUID.randomUUID().toString();
        Workflow workflow = generate3StepWF(0, 3, taskId);
        WorkflowState state = waitOnWorkflowComplete(taskId);
        log.info("Top level workflow state: " + state);
        assertTrue(state == WorkflowState.SUSPENDED_ERROR);
        if (state == WorkflowState.SUSPENDED_ERROR) {
            String rollbackTaskId = UUID.randomUUID().toString();
            workflowService.rollbackWorkflow(workflow.getWorkflowURI(), rollbackTaskId);
            state = waitOnWorkflowComplete(taskId);
            log.info("Top level workflow state after rollback: " + state);
            // assertTrue(state == WorkflowState.ERROR);
        }
        try {
        Thread.sleep(1200000);
        } catch (Exception ex) {
        }
    }
    
    Workflow.Method nopMethod(int level, int step) {
        return new Workflow.Method("nop", level, step);
    }
    public void nop(int level, int step, String stepId) {
        if (hasInjectedFailure(level, step)) {
            ServiceCoded coded = WorkflowException.errors.unforeseen();
            WorkflowStepCompleter.stepFailed(stepId, coded);
        } else {
            WorkflowStepCompleter.stepSucceded(stepId);
        }
    }
    
    Workflow.Method subMethod(int level, int maxLevels, int step) {
        return new Workflow.Method("sub", level, maxLevels, step);
    }
    /**
     * Workflow step to optionally create a sub-workflow.
     * @param level - current workflow level
     * @param maxLevels - maximum number of workflow levels
     * @param error - generate error if requested.
     * @param stepId 
     */
    public void sub(int level, int maxLevels,  int stepIndex, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        if (hasInjectedFailure(level, stepIndex)) {
            ServiceCoded coded = WorkflowException.errors.unforeseen();
            WorkflowStepCompleter.stepFailed(stepId, coded);
            return;
        }
        if (++level >= maxLevels) {
            WorkflowStepCompleter.stepSucceded(stepId);
        } else {
            // Generate a sub-workflow. The completer will complete this step.
            generate3StepWF(level, maxLevels, stepId);
        }
    }
    
    /**
     * Generates a 3 step workflow. May generate a sub workflow for the middle step.
     * First step is nop, second step is a sub-workflow or nop, and third step is nop.
     * @param level -- current level  ... maxLevels
     * @param maxLevels -- max level
     * @param orchTaskId -- the orchestration task id for the workflow
     * @return
     */
    public Workflow generate3StepWF(int level, int maxLevels, String orchTaskId) {
        String[] args = new String[1];
        args[0] = orchTaskId;
        taskStatusMap.put(orchTaskId, WorkflowState.CREATED);
        Workflow workflow = workflowService.getNewWorkflow(this, "generate3StepWF", false, orchTaskId);
        WorkflowTaskCompleter completer = new WorkflowTaskCompleter(workflow.getWorkflowURI(), orchTaskId);
        // first step
        String lastStep = workflow.createStep("first", "first nop", null, nullURI, 
                this.getClass().getName(), false, this.getClass(), nopMethod(level, 1), nopMethod(level, 1), null);
        // second step
//        if (level  >= maxLevels) {
//            // this is the lowest level, generate nop
//            lastStep = workflow.createStep("second", "second nop", lastStep, nullURI, 
//                    this.getClass().getName(), false, this.getClass(), nopMethod(level, 2), nopMethod(level, 2), null);
//        } else {
//            // not the lowest level, generate a sub-workflow
            lastStep = workflow.createStep("second", "second sub", lastStep, nullURI, 
                    this.getClass().getName(), false, this.getClass(), subMethod(level, maxLevels, 2), nopMethod(level, 2), null);
//        }
        // third step
        lastStep = workflow.createStep("third", "third", lastStep, nullURI, 
                this.getClass().getName(), false, this.getClass(), nopMethod(level, 3), nopMethod(level, 3), null);
        // Execute and go
        workflow.executePlan(completer, String.format("Workflow level %d successful", level), new WorkflowCallback(), args, null, null);
        return workflow;
    }
    
    private static class WorkflowCallback implements Workflow.WorkflowCallbackHandler, Serializable {
        @SuppressWarnings("unchecked")
        @Override
        public void workflowComplete(Workflow workflow, Object[] args)
                throws WorkflowException {
                String taskId = (String) args[0];
                WorkflowTest.getTaskStatusMap().put(taskId, workflow.getWorkflowState());
        }
    }
    
    private WorkflowState waitOnWorkflowComplete(String taskId) {
        while (taskStatusMap.get(taskId) == WorkflowState.CREATED 
                || taskStatusMap.get(taskId) == WorkflowState.RUNNING
                || taskStatusMap.get(taskId) == WorkflowState.ROLLING_BACK)  {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                log.info("Sleep interrupted");;
            }
        }
        log.info(String.format("Workflow task %s reported state %s", taskId, taskStatusMap.get(taskId)));
        return taskStatusMap.get(taskId);
    }

    public static Map<String, WorkflowState> getTaskStatusMap() {
        return taskStatusMap;
    }

    public static void setTaskStatusMap(Map<String, WorkflowState> taskStatusMap) {
        WorkflowTest.taskStatusMap = taskStatusMap;
    }

}
