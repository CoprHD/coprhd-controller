package com.emc.storageos.workflow;

import static org.junit.Assert.*;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.joiner.Joiner;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ControllersvcTestBase;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocatorTest;
import com.emc.storageos.workflow.Workflow.StepState;

/**
 * This Junit uses the ControllersvcTestBase to start controllersvc and run it within the Junit.
 * Please see that class for required environmental setup / restrictions.
 * The controllersvc is started from within setup().
 *
 */
public class WorkflowTest extends ControllersvcTestBase implements Controller  {
    private static final URI nullURI = NullColumnValueGetter.getNullURI();
    protected static final Logger log = LoggerFactory.getLogger(WorkflowTest.class);
    private static int sleepMillis = 0;         // sleep time for each step in milliseconds
    
    // Set of injected failure steps. It's level * 100 + step
    private static final Set<Integer> injectedFailures = new HashSet<Integer>();
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
        printLog("Test1 started");
        Object[] args = new Object[1];
        String taskId = UUID.randomUUID().toString();
        args[0] = taskId;
        taskStatusMap.put(taskId, WorkflowState.CREATED);
        Workflow workflow = workflowService.getNewWorkflow(this, "test1", false, taskId);
        workflow.createStep("test1", "nop", null, nullURI, this.getClass().getName(), false, this.getClass(), 
                nopMethod(1, 1), nopMethod(1, 1), null);
        workflow.executePlan(null, "success", new WorkflowCallback(), args, null, null);
        WorkflowState state = waitOnWorkflowComplete(taskId);
        printLog(String.format("task %s state %s", taskId, state));
        assertTrue(state == WorkflowState.SUCCESS);
    }
    
    @Test
    /**
     * This tests a three level hierarchical workflow that passes.
     */
    public void test2() {
        printLog("Test2 started");
        // Don't cause in failures
        injectedFailures.clear();
        String taskId = UUID.randomUUID().toString();
        Workflow workflow = generate3StepWF(0, 3, taskId);
        WorkflowState state = waitOnWorkflowComplete(taskId);
        printLog("Top level workflow state: " + state);
        Map<String, WorkflowStep> stepMap = readWorkflowFromDb(taskId);
        assertTrue(state == WorkflowState.SUCCESS);
        validateStepStates(stepMap, test2SuccessSteps, test2ErrorSteps, test2CancelledSteps);
        printLog("Test2 completed");        
    }
    public static final String[] test2SuccessSteps = { "L0S1 sub", "L0S2 sub", "L0S3 sub", 
        "L1S1 sub", "L1S2 sub", "L1S3 sub", "L2S1 sub", "L2S2 sub", "L2S3 sub"};
    public static final String[] test2ErrorSteps = { };
    public static final String[] test2CancelledSteps = { };
    
    @Test
    /**
     * This tests a three level hierarchical workflow where the lowest level last step fails.
     * After the workflow suspends, remove the error and resume the workflow.
     * The resulting workflow should pass all steps.
     */
    public void test3() {
        printLog("Test3 started");
        injectedFailures.clear();
        addInjectedFailure(2,3);  // level 2, step 3
        String taskId = UUID.randomUUID().toString();
        Workflow workflow = generate3StepWF(0, 3, taskId);
        WorkflowState state = waitOnWorkflowComplete(taskId);
        printLog("Top level workflow state: " + state);
        Map<String, WorkflowStep> stepMap = readWorkflowFromDb(taskId);
        assertTrue(state == WorkflowState.SUSPENDED_ERROR);
        validateStepStates(stepMap, test3aSuccessSteps, test3aErrorSteps, test3aCancelledSteps);
        if (state == WorkflowState.SUSPENDED_ERROR) {
            // clear the error and try and resume.
            injectedFailures.clear();
            String resumeTaskId = UUID.randomUUID().toString();
            workflowService.resumeWorkflow(workflow.getWorkflowURI(), resumeTaskId);
            taskStatusMap.put(taskId, WorkflowState.CREATED);
            state = waitOnWorkflowComplete(taskId);
            printLog("Top level workflow state after resume: " + state);
            stepMap = readWorkflowFromDb(taskId);
            assertTrue(state == WorkflowState.SUCCESS);
            validateStepStates(stepMap, test3bSuccessSteps, test3bErrorSteps, test3bCancelledSteps);
        }
        printLog("Test3 completed");
    }
    private static final String[] test3aSuccessSteps = { "L0S1 sub", "L1S1 sub", "L2S1 sub", "L2S2 sub" };
    private static final String[] test3aErrorSteps = { "L0S2 sub", "L1S2 sub", "L2S3 sub" };
    private static final String[] test3aCancelledSteps = { "L0S3 sub", "L1S3 sub" };
    private static final String[] test3bSuccessSteps = { "L0S1 sub", "L0S2 sub", "L0S3 sub", 
        "L1S1 sub", "L1S2 sub", "L1S3 sub", "L2S1 sub", "L2S2 sub", "L2S3 sub" };
    private static final String[] test3bErrorSteps = { };
    private static final String[] test3bCancelledSteps = {  };
    
    @Test
    /**
     * This tests a three level hierarchical workflow where the lowest level last step fails.
     * After the workflow suspends, rollback the workflow. Then it verifies all the steps were
     * correctly cancelled or rolled back.
     */
    public void test4() {
        printLog("Test4 started");
        injectedFailures.clear();
        addInjectedFailure(2,3);  // level 2, step 3
        String taskId = UUID.randomUUID().toString();
        Workflow workflow = generate3StepWF(0, 3, taskId);
        WorkflowState state = waitOnWorkflowComplete(taskId);
        printLog("Top level workflow state: " + state);
        Map<String, WorkflowStep> stepMap = readWorkflowFromDb(taskId);
        assertTrue(state == WorkflowState.SUSPENDED_ERROR);
        validateStepStates(stepMap, test4aSuccessSteps, test4aErrorSteps, test4aCancelledSteps);
        if (state == WorkflowState.SUSPENDED_ERROR) {
            String rollbackTaskId = UUID.randomUUID().toString();
            taskStatusMap.remove(taskId);
            workflowService.rollbackWorkflow(workflow.getWorkflowURI(), rollbackTaskId);
            state = waitOnWorkflowComplete(taskId);
            printLog("Top level workflow state after rollback: " + state);
            stepMap = readWorkflowFromDb(taskId);
            // assertTrue(state == WorkflowState.ERROR);
            validateStepStates(stepMap, test4bSuccessSteps, test4bErrorSteps, test4bCancelledSteps);
        }
        printLog("Test4 completed");
    }
    
    private static final String[] test4aSuccessSteps = { "L0S1 sub", "L1S1 sub", "L2S1 sub", "L2S2 sub" };
    private static final String[] test4aErrorSteps = { "L0S2 sub", "L1S2 sub", "L2S3 sub" };
    private static final String[] test4aCancelledSteps = { "L0S3 sub", "L1S3 sub" };
    private static final String[] test4bSuccessSteps = { "L0S1 sub", "L1S1 sub", "L2S1 sub", "L2S2 sub", 
        "Rollback L0S1 sub", "Rollback L0S2 sub", "Rollback L1S1 sub", "Rollback L1S2 sub" };
    private static final String[] test4bErrorSteps = { "L0S2 sub", "L1S2 sub", "L2S3 sub", 
        "Rollback L2S3 sub" };
    private static final String[] test4bCancelledSteps = { "L0S3 sub", "L1S3 sub", 
        "Rollback L2S1 sub", "Rollback L2S2 sub"  };
    
    @Test
    /**
     * This test does a suspend of a selected step, followed by a resume after the workflow is suspended.
     * The result should be a successfully completed workflow.
     */
    public void test5() {
        printLog("Test5 started");
        injectedFailures.clear();
        sleepMillis = 10000;    // 10 seconds
        String taskId = UUID.randomUUID().toString();
        Workflow workflow = generate3StepWF(0, 1, taskId);
        Map<String, WorkflowStep> stepMap = readWorkflowFromDb(taskId);
        WorkflowStep step3 = stepMap.get("L0S3 sub");
        workflowService.suspendWorkflowStep(workflow.getWorkflowURI(), step3.getId(), UUID.randomUUID().toString());
        WorkflowState state = waitOnWorkflowComplete(taskId);
        printLog("Workflow state after suspend: " + state);
        assertTrue(state == WorkflowState.SUSPENDED_NO_ERROR);
        taskStatusMap.put(taskId, WorkflowState.CREATED);
        workflowService.resumeWorkflow(workflow.getWorkflowURI(), UUID.randomUUID().toString());
        state = waitOnWorkflowComplete(taskId);
        printLog("Workflow state after suspend: " + state);
        assertTrue(state == WorkflowState.SUCCESS);
        sleepMillis = 0;
        printLog("Test5 completed");
    }
    
    Workflow.Method nopMethod(int level, int step) {
        return new Workflow.Method("nop", level, step);
    }
    public void nop(int level, int step, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        if (sleepMillis > 0) {
            try {
                Thread.sleep(sleepMillis);
            } catch (Exception ex) {
                // no action
            }
        }
        if (hasInjectedFailure(level, step)) {
            log.info("Injecting failure in step: " + genMsg(level, step, "nop"));
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
        if (sleepMillis > 0) {
            try {
                Thread.sleep(sleepMillis);
            } catch (Exception ex) {
                // no action
            }
        }
        if (hasInjectedFailure(level, stepIndex)) {
            log.info("Injecting failure in step: " + genMsg(level, stepIndex, "sub"));
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
        String lastStep = workflow.createStep("first", genMsg(level, 1, "sub"), null, nullURI, 
                this.getClass().getName(), false, this.getClass(), nopMethod(level, 1), nopMethod(level, 1), null);
        // second step
            lastStep = workflow.createStep("second", genMsg(level, 2, "sub"), lastStep, nullURI, 
                    this.getClass().getName(), false, this.getClass(), subMethod(level, maxLevels, 2), nopMethod(level, 2), null);
        // third step
        lastStep = workflow.createStep("third", genMsg(level, 3, "sub"), lastStep, nullURI, 
                this.getClass().getName(), false, this.getClass(), nopMethod(level, 3), nopMethod(level, 3), null);
        // Execute and go
        workflow.executePlan(completer, String.format("Workflow level %d successful", level), new WorkflowCallback(), args, null, null);
        return workflow;
    }
    
    private String genMsg(int level, int step, String msg) {
        return String.format("L%dS%d %s", level, step, msg);
    }
    
    /**
     * Reads a workflow from the database. Called recursively for sub-workflows.
     * @param orchTaskId -- the Orchestration task id.
     * @return A map from step description to WorkflowStep entry
     */
    private Map<String, WorkflowStep> readWorkflowFromDb(String orchTaskId) {
        Map<String, WorkflowStep> msgToStep = new HashMap<String, WorkflowStep>();
        Joiner j = new Joiner(dbClient);
        List<WorkflowStep> steps = 
                j.join(com.emc.storageos.db.client.model.Workflow.class, "wf")
                .match("orchTaskId", orchTaskId)
                .join("wf", WorkflowStep.class, "step", "workflow")
                .go().list("step");
        for (WorkflowStep step : steps) {
            msgToStep.put(step.getDescription(), step);
            System.out.println(String.format("Step %s: status: %s message: %s", step.getDescription(), step.getState(), step.getMessage()));
            // Check for sub-workflow.
            Map<String, WorkflowStep> subWorkflowMap = readWorkflowFromDb(step.getStepId());
            msgToStep.putAll(subWorkflowMap);
        }
        return msgToStep;
    }
    
    /**
     * Given the step descrption to WorkflowStep map computed by readWorkflowsFromDb,
     * check that the steps (as given by the descriptions) are in the correct states.
     * @param descriptionToStepMap
     * @param successSteps
     * @param errorSteps
     * @param cancelledSteps
     */
    void validateStepStates(Map<String, WorkflowStep> descriptionToStepMap, 
            String[] successSteps, String[] errorSteps, String[] cancelledSteps) {
        // check success steps
        for (String successStep : successSteps) {
            WorkflowStep step = descriptionToStepMap.get(successStep);
            assertNotNull("Step not found: " + successStep, step);
            assertEquals(String.format("Step %s expected SUCCESS but in state %s", step, step.getState()), 
                    step.getState(), StepState.SUCCESS.name());
        }
     // check error steps
        for (String errorStep : errorSteps) {
            WorkflowStep step = descriptionToStepMap.get(errorStep);
            assertNotNull("Step not found: " + errorStep, step);
            assertEquals(String.format("Step %s expected ERROR but in state %s", step, step.getState()), 
                    step.getState(), StepState.ERROR.name());
        }
     // check cancelled steps
        for (String cancelledStep : cancelledSteps) {
            WorkflowStep step = descriptionToStepMap.get(cancelledStep);
            assertNotNull("Step not found: " + cancelledStep, step);
            assertEquals(String.format("Step %s expected CANCELLED but in state %s", step, step.getState()), 
                    step.getState(), StepState.CANCELLED.name());
        }
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
        while (taskStatusMap.get(taskId) == null
                || taskStatusMap.get(taskId) == WorkflowState.CREATED 
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
    
    private void printLog(String s) {
        System.out.println(s);
        log.info(s);;
    }

}
