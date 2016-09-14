package com.emc.sa.service.vipr.oe.tasks;

import com.emc.sa.service.vipr.oe.JsonSamples;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class OrchestrationRunnerTask extends ViPRExecutionTask<String> {

    String step;
    String result;

    public OrchestrationRunnerTask(String step) {
        super();
        this.step = step;
    }

    @Override
    public void execute() throws Exception {

        switch(step) {
        case "Step1" :
            result = JsonSamples.singleTask;
            break;
        case "Step2" :
            result = JsonSamples.listOfTasks;
            break;
        case "Step3" :
            result = JsonSamples.msg;
            break;
        }
        return;
    }

    public String getResult() {
        return result;
    }

}
