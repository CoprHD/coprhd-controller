#!/bin/bash
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC. 
#   
# genconfig_ssh_test.sh
# 
# This is a simple unit test script that should be executed on a devkit
# The script does the following:
# 1. backup the local host/user key pair and /etc/genconfig* files.
# 2. copy the etc/genconfig* files from the code repository to local filesystem
# 3. generate the host/user key pairs using the default private key files in the 
#    code repository
# 4. make sure that the generated private key files match the original files, and
#    the public key files are non-empty, abort if any of the test fails
# 5. recover the backup files in step 1.


BASEDIR=$(dirname $0)/../../../../..
SECRETS=${BASEDIR}/packaging/secrets
nl='
'

get_modified_files() {
    cat <<EOF
/etc/ssh/sshd_config
/etc/ssh/ssh_host_ecdsa_key
/etc/ssh/ssh_host_ecdsa_key.pub
/etc/ssh/ssh_host_dsa_key
/etc/ssh/ssh_host_dsa_key.pub
/etc/ssh/ssh_host_rsa_key
/etc/ssh/ssh_host_rsa_key.pub
/etc/ssh/ssh_config
/root/.ssh/id_dsa
/root/.ssh/id_dsa.pub
/root/.ssh/id_rsa
/root/.ssh/id_rsa.pub
/root/.ssh/id_ecdsa
/root/.ssh/id_ecdsa.pub
/home/svcuser/.ssh/id_dsa
/home/svcuser/.ssh/id_dsa.pub
/home/svcuser/.ssh/id_rsa
/home/svcuser/.ssh/id_rsa.pub
/home/svcuser/.ssh/id_ecdsa
/home/svcuser/.ssh/id_ecdsa.pub
/opt/storageos/.ssh/id_dsa
/opt/storageos/.ssh/id_dsa.pub
/opt/storageos/.ssh/id_rsa
/opt/storageos/.ssh/id_rsa.pub
/opt/storageos/.ssh/id_ecdsa
EOF
}

print_content_inline() {
    IFS=${nl}
    for line in $(cat ${SECRETS}/${1}); do
        echo -n ${line}\\\\n
    done
}

get_props() {
    echo node_count=3
    echo node_id=vipr1
    echo ssh_host_dsa_key=$(print_content_inline ssh_host_dsa_key)
    echo ssh_host_ecdsa_key=$(print_content_inline ssh_host_ecdsa_key)
    echo ssh_host_rsa_key=$(print_content_inline ssh_host_rsa_key)
    echo root_id_dsa=$(print_content_inline root_id_dsa)
    echo root_id_ecdsa=$(print_content_inline root_id_ecdsa)
    echo root_id_rsa=$(print_content_inline root_id_rsa)
    echo storageos_id_dsa=$(print_content_inline storageos_id_dsa)
    echo storageos_id_ecdsa=$(print_content_inline storageos_id_ecdsa)
    echo storageos_id_rsa=$(print_content_inline storageos_id_rsa)
    echo svcuser_id_dsa=$(print_content_inline svcuser_id_dsa)
    echo svcuser_id_ecdsa=$(print_content_inline svcuser_id_ecdsa)
    echo svcuser_id_rsa=$(print_content_inline svcuser_id_rsa)
}

setup() {
    for file in $(get_modified_files); do
        mv ${file} ${file}.bak 2>/dev/null
    done
    cp /etc/ssh/sshd_config.bak /etc/ssh/sshd_config
    mv /etc/genconfig /etc/genconfig.bak
    mv /etc/genconfig.d /etc/genconfig.d.bak
    cp ${BASEDIR}/etc/genconfig /etc/genconfig
    mkdir /etc/genconfig.d
    for file in genconfig-core ssh ssh_auth_key; do
        cp ${BASEDIR}/etc/genconfig.d/${file} /etc/genconfig.d/
        chmod +x /etc/genconfig.d/${file}
    done
    trap cleanup EXIT
}

cleanup() {
    for file in $(get_modified_files); do
        mv ${file}.bak ${file} 2>/dev/null
    done
    mv /etc/genconfig.bak /etc/genconfig
    rm -rf /etc/genconfig.d
    mv /etc/genconfig.d.bak /etc/genconfig.d
}

fatal() {
    echo $@ >&2
    exit 1
}

test_keypair() {
    diff -b -B ${1} ${2} || fatal "${1} and ${2} doesn't match"
    [ -s ${2}.pub ] || fatal "${2}.pub doesn't exist or is empty"
}

setup

echo "begin genconfig_ssh test"
get_props | /etc/genconfig.d/ssh
test_keypair ${SECRETS}/ssh_host_dsa_key /etc/ssh/ssh_host_dsa_key
test_keypair ${SECRETS}/ssh_host_ecdsa_key /etc/ssh/ssh_host_ecdsa_key 
test_keypair ${SECRETS}/ssh_host_rsa_key /etc/ssh/ssh_host_rsa_key 
test_keypair ${SECRETS}/root_id_dsa /root/.ssh/id_dsa 
test_keypair ${SECRETS}/root_id_ecdsa /root/.ssh/id_ecdsa 
test_keypair ${SECRETS}/root_id_rsa /root/.ssh/id_rsa 
test_keypair ${SECRETS}/storageos_id_dsa /opt/storageos/.ssh/id_dsa 
test_keypair ${SECRETS}/storageos_id_ecdsa /opt/storageos/.ssh/id_ecdsa 
test_keypair ${SECRETS}/storageos_id_rsa /opt/storageos/.ssh/id_rsa 
test_keypair ${SECRETS}/svcuser_id_dsa /home/svcuser/.ssh/id_dsa 
test_keypair ${SECRETS}/svcuser_id_ecdsa /home/svcuser/.ssh/id_ecdsa 
test_keypair ${SECRETS}/svcuser_id_rsa /home/svcuser/.ssh/id_rsa 
echo "genconfig_ssh test passed!"
