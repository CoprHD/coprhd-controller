package com.emc.sa.service.vipr.rackhd.gson;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ViprOperation {

    ViprTask[] task;

    public ViprTask[] getTask() {
        return task;
    }

    public void setTask(ViprTask[] task) {
        this.task = task;
    }
    
    public List<URI> getTaskIds() throws URISyntaxException {
        List<URI> idList = new ArrayList<>();
        if (task != null) {
            for(ViprTask oneTask : task) {
                idList.add(new URI(oneTask.getId()));
            }
        }
        return idList;
    }
}
