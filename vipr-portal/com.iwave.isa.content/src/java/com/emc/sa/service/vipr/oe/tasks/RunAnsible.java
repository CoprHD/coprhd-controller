package com.emc.sa.service.vipr.oe.tasks;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.linux.tasks.LinuxExecutionTask;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.services.util.Exec;
import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.command.LinuxCommand;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by sonalisahu on 10/21/16.
 */
public class RunAnsible  extends ViPRExecutionTask<String> {
private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunAnsible.class);
    private String name;
    private Map<String, List<String>> input;

    public RunAnsible(final String name, final Map<String, List<String>> input)
    {
        this.name = name;
        this.input = input;
    }

    @Override
    public String executeTask() throws Exception {

        //ansible-playbook -i "localhost" release.yml --extra-vars "version=1.23.45 other_variable=foo"

        ExecutionUtils.currentContext().logInfo("Starting Ansible WF Step. Operation:" + name);

	String extra_vars = makeExtraArg(input);
        String[] cmds = { "/usr/bin/ansible-playbook", name };
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);

        //CommandOutput result = exec.executeCommand(new Command("ansible-playbook -i " + "" + name+".yml" + "--extra-vars " + extra_vars));
        ExecutionUtils.currentContext().logInfo("Done Executing Ansible WF Step. Operation:" + name);

        AnsibleResult res = new AnsibleResult();
        res.setResult(
        result.getExitValue(),
        result.getStdOutput(),
        result.getStdError());

        return Integer.toString(result.getExitValue());
    }

    private String makeExtraArg(Map<String, List<String>> input) throws Exception
  {
        String extra_vars = "\"";
        Set s = input.keySet();

        Iterator it = s.iterator();
        while(it.hasNext())
        {
            String key = it.next().toString();
            String value = input.get(key).get(0);
            logger.info("key:{} value:{}", key, value);
            extra_vars = extra_vars + key + "=" +value;
        }
        extra_vars = extra_vars + "\"";
        logger.info("extra vars:{}", extra_vars);

        return extra_vars;
    }
    
    public static class AnsibleResult {
        
        private int exitValue;
        private String out;
        private String err;
        
        public void setResult(int exitValue, String out, String err) {
            this.exitValue = exitValue;
            this.out = out;
            this.err = err;               
        }       
    }

    public static class AnsibleTask extends LinuxExecutionTask<Void> {
        private AnsibleCommand command;

        public AnsibleTask(final AnsibleCommand command) {
            this.command = command;
        }
        @Override
        public void execute() {
            executeCommand(command);
        }
    }

    public static class AnsibleCommand extends LinuxCommand {
        public AnsibleCommand(final String name) {
            setCommand("ansible-playbook");
            addArgument(name);
            setRunAsRoot(true);
        }
    }

}
