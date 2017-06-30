/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.storageos.primitives.CustomServicesConstants;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;

/**
 * This creates the Ansible command line as per the given parameter provided by the user
 */

///chroot /opt/storageos/customroot /usr/bin/ansible-playbook -i "localhost," -u "root" -l "" -t "" playbook.yml --extra-vars " "

public class AnsibleCommandLine {
    private final String ansiblePath;
    private final String playbook;
    private String prefix;
    private String extraVars;
    private String shellArgs;
    private boolean isRemoteAnsible = false;
    private String chrootCmd;
    private final ImmutableList.Builder<String> optionalParam = ImmutableList.builder();

    public AnsibleCommandLine(final String ansiblePath, final String playbook) {
        this.ansiblePath = ansiblePath;
        this.playbook = playbook;
    }

    public AnsibleCommandLine setPrefix(final String prefix) {
        if (!StringUtils.isEmpty(prefix))
            this.prefix = prefix;

        return this;
    }

    public AnsibleCommandLine setUser(final String user) {
        if (!StringUtils.isEmpty(user))
            optionalParam.add("-u").add(user);

        return this;
    }

    public AnsibleCommandLine setHostFile(final String hostFile) {
        if (!StringUtils.isEmpty(hostFile))
            optionalParam.add("-i").add(hostFile);

        return this;
    }

    public AnsibleCommandLine setCommandLine(final String commandLine) {
        if (!StringUtils.isEmpty(commandLine)) {
            optionalParam.add(commandLine);
	}

        return this;
    }

    public AnsibleCommandLine setLimit(final String limit) {
        if (!StringUtils.isEmpty(limit))
            optionalParam.add("-l").add(limit);

        return this;
    }

    public AnsibleCommandLine setTags(final String tags) {
        if (!StringUtils.isEmpty(tags))
            optionalParam.add("-t").add(tags);

        return this;
    }

    public AnsibleCommandLine setExtraVars(final String vars) {
        if (!StringUtils.isEmpty(vars))
            this.extraVars = vars;

        return this;
    }

    public AnsibleCommandLine setShellArgs(final String vars) {
        if (!StringUtils.isEmpty(vars))
            this.shellArgs = vars;

        return this;
    }

    public AnsibleCommandLine setIsRemoteAnsible(final boolean isRemoteAnsible) {
        this.isRemoteAnsible = isRemoteAnsible;

	return this;
    }

    public AnsibleCommandLine setChrootCmd(final String chrootcmd) {
        if (!StringUtils.isEmpty(chrootcmd)) {
            this.chrootCmd = chrootcmd;
        }

        return this;
    }

    public String[] build() {
        final ImmutableList.Builder<String> builder = ImmutableList.builder();
        if (!StringUtils.isEmpty(prefix)) {
            builder.add(prefix);
        }

        if (!StringUtils.isEmpty(chrootCmd)) {
            builder.add(chrootCmd);
            builder.add(CustomServicesConstants.CHROOT_DIR);
        }

        if (!StringUtils.isEmpty(shellArgs)) {
            builder.add(CustomServicesConstants.BIN_BASH).add(CustomServicesConstants.BIN_BASH_OPTION).add(shellArgs + " " + ansiblePath + " " + playbook);
            final ImmutableList<String> cmdList = builder.build();

            return cmdList.toArray(new String[cmdList.size()]);
        }


        final ImmutableList<String> opt = optionalParam.build();
        builder.add(ansiblePath).add(opt.toArray(new String[opt.size()])).add(playbook);

        if (!StringUtils.isEmpty(extraVars)) {
            if (isRemoteAnsible) {
                builder.add("--extra-vars").add("\"").add(extraVars).add("\"");
            } else {
                builder.add("--extra-vars").add(extraVars);
            }
        }

        final ImmutableList<String> cmdList = builder.build();

        return cmdList.toArray(new String[cmdList.size()]);
    }
}
