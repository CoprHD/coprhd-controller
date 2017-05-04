package models.customservices;

import play.data.validation.Required;

import java.io.File;
import java.util.List;

/**
 * Created by balak1 on 5/4/2017.
 */
public class LocalAnsiblePrimitiveForm {
    private String id;
    @Required
    private String name;
    private String description;
    private boolean existing;
    private String existingResource;
    private File ansiblePackage;
    @Required
    private String ansiblePackageName;
    @Required
    private String ansiblePlaybook;
    private String inputs; // comma separated list of inputs
    private String outputs; // comma separated list of ouputs
    private String wfDirID;
    // This is list of newly uloaded inventory files
    private File[] inventoryFiles;
    // This is comma separated list of updated inventory file names
    private String updatedInventoryFiles;

    // TODO
    public void validate() {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getExistingResource() {
        return existingResource;
    }

    public void setExistingResource(String existingResource) {
        this.existingResource = existingResource;
    }

    public File getAnsiblePackage() {
        return ansiblePackage;
    }

    public void setAnsiblePackage(File ansiblePackage) {
        this.ansiblePackage = ansiblePackage;
    }

    public String getAnsiblePackageName() {
        return ansiblePackageName;
    }

    public void setAnsiblePackageName(String ansiblePackageName) {
        this.ansiblePackageName = ansiblePackageName;
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

    public String getAnsiblePlaybook() {
        return ansiblePlaybook;
    }

    public void setAnsiblePlaybook(String ansiblePlaybook) {
        this.ansiblePlaybook = ansiblePlaybook;
    }

    public boolean isExisting() {
        return existing;
    }

    public void setExisting(boolean existing) {
        this.existing = existing;
    }

    public String getWfDirID() {
        return wfDirID;
    }

    public void setWfDirID(String wfDirID) {
        this.wfDirID = wfDirID;
    }

    public File[] getInventoryFiles() {
        return inventoryFiles;
    }

    public void setInventoryFiles(File[] inventoryFiles) {
        this.inventoryFiles = inventoryFiles;
    }

    public String getUpdatedInventoryFiles() {
        return updatedInventoryFiles;
    }

    public void setUpdatedInventoryFiles(String updatedInventoryFiles) {
        this.updatedInventoryFiles = updatedInventoryFiles;
    }
}
