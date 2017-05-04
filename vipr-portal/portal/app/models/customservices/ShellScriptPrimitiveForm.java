package models.customservices;

import play.data.validation.Required;

import java.io.File;

/**
 * Created by balak1 on 5/4/2017.
 */
public class ShellScriptPrimitiveForm {
    private String id; // this is empty for CREATE
    // Name and Description step
    @Required
    private String name;
    @Required
    private String description;
    @Required
    private File script;
    @Required
    private String scriptName;
    private String inputs; // comma separated list of inputs
    private String outputs; // comma separated list of ouputs
    private boolean newScript; // if true create new resource(delete any existing)
    private String wfDirID; // this is empty for EDIT

    // TODO
    public void validate() {
        // check if script is not null
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isNewScript() {
        return newScript;
    }

    public void setNewScript(boolean newScript) {
        this.newScript = newScript;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public File getScript() {
        return script;
    }

    public void setScript(File script) {
        this.script = script;
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getInputs() {
        return inputs;
    }

    public void setInputs(String inputs) {
        this.inputs = inputs;
    }

    public String getOutputs() {
        return outputs;
    }

    public void setOutputs(String outputs) {
        this.outputs = outputs;
    }

    public String getWfDirID() {
        return wfDirID;
    }

    public void setWfDirID(String wfDirID) {
        this.wfDirID = wfDirID;
    }


}
