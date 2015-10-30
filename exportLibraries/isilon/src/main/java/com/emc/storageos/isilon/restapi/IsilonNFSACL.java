/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.util.ArrayList;

/*
 * Class representing the isilon nfs acl  object
 * member names should match the key names in json object
 */
public class IsilonNFSACL {

    public enum AccessRights {
        dir_gen_read, dir_gen_execute,
        std_write_dac
    }

    public class Persona {
        private final String type;   // optional
        private final String id;     // optional
        private final String name;

        public Persona(String account_type, String id, String name) {
            this.type = account_type;
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("(type: " + type);
            str.append(", id: " + id);
            str.append(", name: " + name);
            str.append(")");
            return str.toString();
        }
    }

    public class Acl {
        private Persona trustee;

        private String accesstype = "allow";
        private ArrayList<String> inherit_flags;
        private ArrayList<String> accessrights;
        private String op;

        public Persona getTrustee() {
            return trustee;
        }

        public void setTrustee(Persona trustee) {
            this.trustee = trustee;
        }

        public String getAccesstype() {
            return accesstype;
        }

        public void setAccesstype(String accesstype) {
            this.accesstype = accesstype;
        }

        public ArrayList<String> getInherit_flags() {
            return inherit_flags;
        }

        public void setInherit_flags(ArrayList<String> inherit_flags) {
            this.inherit_flags = inherit_flags;
        }

        public ArrayList<String> getAccessrights() {
            return accessrights;
        }

        public void setAccessrights(ArrayList<String> accessrights) {
            this.accessrights = accessrights;
        }

        public String getOp() {
            return op;
        }

        public void setOp(String op) {
            this.op = op;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("( trustee: " + trustee);
            str.append(", access type: " + accesstype);
            str.append(", inherit flags: " + inherit_flags);
            str.append(", access type: " + accesstype);
            str.append(", access rights: " + accessrights);
            str.append(", operation: " + op);
            str.append(")");
            return str.toString();

        }
    }

    private ArrayList<Acl> acl;
    private Persona owner;
    private Persona group;
    private String authoritative;
    private String mode;
    private String action;

    public ArrayList<Acl> getAcl() {
        return acl;
    }

    public void setAcl(ArrayList<Acl> acl) {
        this.acl = acl;
    }

    public Persona getOwner() {
        return owner;
    }

    public void setOwner(Persona owner) {
        this.owner = owner;
    }

    public Persona getGroup() {
        return group;
    }

    public void setGroup(Persona group) {
        this.group = group;
    }

    public String getAuthoritative() {
        return authoritative;
    }

    public void setAuthoritative(String authoritative) {
        this.authoritative = authoritative;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("( acl: " + acl);
        str.append(", owner: " + owner);
        str.append(", group: " + group);
        str.append(", authoritative: " + authoritative);
        str.append(", mode: " + mode);
        str.append(", action: " + action);
        str.append(")");
        return str.toString();

    }

}
