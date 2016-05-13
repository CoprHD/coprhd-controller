package com.emc.sa.service.vipr.rackhd.gson;

public class Context {
    private String[] ansibleResultFile;

    public String[] getAnsibleResultFile() {
        return ansibleResultFile == null ? new String[0] : ansibleResultFile;
    }

    public void setAnsibleResultFile(String[] ansibleResultFile) {
        this.ansibleResultFile = ansibleResultFile;
    }

}