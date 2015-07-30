/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.sample;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

@Service("SampleService")
public class SampleService extends ViPRService {
    @Param
    protected String text;
    @Param
    protected String password;
    @Param
    protected Integer number;
    @Param
    protected String choice;
    @Param
    protected String completion;
    @Param(required = false)
    protected Integer sleepTime;
    @Param(required = false)
    protected Boolean repeat = false;

    private long getPause() {
        return sleepTime != null ? sleepTime * 1000 : 5000;
    }

    @Override
    public void execute() throws Exception {

        ExecutionUtils.currentContext().logInfo("sampleService.textInfo", text);
        ExecutionUtils.currentContext().logInfo("sampleService.passwordInfo", password);
        ExecutionUtils.currentContext().logInfo("sampleService.numberInfo", number);
        ExecutionUtils.currentContext().logInfo("sampleService.choiceInfo", choice);
        ExecutionUtils.currentContext().logInfo("sampleService.completionInfo", completion);
        ExecutionUtils.currentContext().logInfo("sampleService.sleepInfo", sleepTime);
        ExecutionUtils.currentContext().logInfo("sampleService.repeatInfo", repeat);

        String lower = ExecutionUtils.execute(new ToLowerCase(text));
        ExecutionUtils.addRollback(new ToUpperCase(lower));

        int repeats = repeat != null && repeat ? 1 : 0;
        while (repeats >= 0) {
            ExecutionUtils.execute(new Multiply(number, 42));
            repeats--;
        }

        String upper = ExecutionUtils.execute(new ToUpperCase(choice));
        ExecutionUtils.addRollback(new ToLowerCase(upper));

        if (StringUtils.equals(completion, "rollback")) {
            ExecutionUtils.execute(new TriggerRollback());
        }

        if (StringUtils.equals(completion, "partial")) {
            setPartialSuccess();
        }

        ExecutionUtils.execute(new ProcessPassword(password));

    }

    private void pause() throws InterruptedException {
        long pause = getPause();
        ExecutionUtils.currentContext().logInfo("sampleService.pausing", pause);
        Thread.sleep(pause);
    }

    private class ProcessPassword extends ViPRExecutionTask<Void> {
        private String encryptedPassword;

        public ProcessPassword(String encryptedPassword) {
            this.encryptedPassword = encryptedPassword;
            provideDetailArgs(encryptedPassword);
        }

        @Override
        public Void executeTask() throws Exception {
            pause();
            logInfo("sampleService.encrypted", encryptedPassword);

            String decryptedPassword = decrypt(encryptedPassword);
            logInfo("sampleService.decrypted", decryptedPassword);

            return null;
        }
    }

    private class ToLowerCase extends ExecutionTask<String> {
        private String value;

        public ToLowerCase(String value) {
            this.value = value;
            provideDetailArgs(value);
        }

        @Override
        public String executeTask() throws Exception {
            pause();
            return value.toLowerCase();
        }
    }

    private class ToUpperCase extends ExecutionTask<String> {
        private String value;

        public ToUpperCase(String value) {
            this.value = value;
            provideDetailArgs(value);
        }

        @Override
        public String executeTask() throws Exception {
            pause();
            return value.toUpperCase();
        }
    }

    private class Multiply extends ExecutionTask<Integer> {
        private Integer a;
        private Integer b;

        public Multiply(Integer a, Integer b) {
            this.a = a;
            this.b = b;
            provideDetailArgs(a, b);
        }

        @Override
        public Integer executeTask() throws Exception {
            Thread.sleep(getPause());
            return a * b;
        }
    }

    private class TriggerRollback extends ExecutionTask<Void> {
        @Override
        public Void executeTask() throws Exception {
            pause();
            throw new Exception(getMessage("sampleService.triggerRollback.exception"));
        }
    }
}
