package com.emc.sa.service.vipr.oe.tasks;

import com.emc.sa.service.vipr.oe.OrchestrationService;
import com.iwave.ext.command.Command;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.iwave.utility.ssh.ShellCommandExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by sonalisahu on 9/7/16.
 */
public final class RunAnsible extends ViPRExecutionTask<String>
{
 @Override
    public String executeTask() throws Exception {
        return super.executeTask();
    }

   /* public final Map<String, String> runAnsible(OrchestrationService.Step step, HashMap<String, Map<String, String>> input)
    {

       switch ('')
        {
            case "LINUX" :
            {
                ShellCommandExecutor exec = new ShellCommandExecutor();
                exec.executeCommand(new Command("sh +x ", "/data/"+step.getOpName()+"sh"));
                break;
            }
            case "PYTHON" :
            {
                break;
            }
            case "ANSIBLE" :
            {
                ShellCommandExecutor exec = new ShellCommandExecutor();
                exec.executeCommand(new Command("ansible-playbook", "/data/"+step.getOpName()));
                break;
            }
            default:
                break;
        }
        

        return null;
    }*/

}

