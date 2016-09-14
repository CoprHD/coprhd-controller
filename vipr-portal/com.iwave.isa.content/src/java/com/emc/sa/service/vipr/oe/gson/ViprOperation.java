package com.emc.sa.service.vipr.oe.gson;

import java.util.ArrayList;
import java.util.List;

public class ViprOperation {

    public ViprOperation(ViprTask t){
        task = new ViprTask[1];
        task[0] = t;
    }

    ViprTask[] task;

    public ViprTask[] getTask() {
        return task;
    }

    public void setTask(ViprTask[] task) {
        this.task = task;
    }

    public List<String> getTaskIds() {
        List<String> idList = new ArrayList<>();
        if (task != null) {
            for(ViprTask oneTask : task) {
                idList.add(oneTask.getId());
            }
        }
        return idList;
    }

    public boolean isValid() {
        return task != null;
    }

}
