#!/usr/bin/python
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

import subprocess
import os
import re
import sys
import tempfile
import uuid
import time

ZK = '/opt/storageos/bin/zkCli.sh'
bourne_ip = os.getenv('BOURNE_IPADDR', '127.0.0.1')
bourne_data_ip = os.getenv('BOURNE_DATA_IPADDR', '127.0.0.1')
rootPassword = os.getenv('Password', 'ChangeMe')


def execute(command):
    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE)
    (out, err) = process.communicate()
    if process.returncode != 0:
        sys.stderr.write("ERROR: failed to execute command: " + command)
        sys.exit(1)
    return out


def ssh(ip, command):
    ssh_command = 'sshpass -p ' + rootPassword + ' ssh -q -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null root@' + ip + ' ' + command
    print ssh_command
    return execute(ssh_command)


def scp(src, ip, dst):
    scp_command = 'sshpass -p ' + rootPassword + ' scp -q -r -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ' + src + ' root@' + ip + ':' + dst
    return execute(scp_command)


def zk_ls(ip, kind):
    ls_result = ssh(ip, ZK + " ls " + kind)
    # avoid take '[ZooKeeper]' in log 'INFO main [ZooKeeper]' as value
    value = re.findall(r'[^ ]\[.*\]', ls_result)
    if len(value)<=0:
        return []
    value = value[0]
    result = re.split(',', value.replace('[', '').replace(']', '').replace(' ', ''))
    return result


def zk_show(ip, kind, id):
    value = ssh(ip, ZK + " get " + kind + "/" + id)
    print(value.split('WATCHER::')[1])

def zk_get(ip, kind, id):
    value = ssh(ip, ZK + " get " + kind + "/" + id)
    return value.split('WATCHER::')[1]

def zk_remove_node(ip, kind):
    ls_result = zk_ls(ip, kind)
    for id in ls_result:
        zk_remove_leaf(ip, kind, id)


def zk_remove_leaf(ip, kind, id):
    ssh(ip, ZK + " rmr " + kind + "/" + id)


def zk_create(ip, kind, id, value, date):
    data = date + '\n' + 'Value=' + value + '\n_kind=' + kind + '\n_id=' + id + '\n'
    ssh(ip, ZK + " create " + kind + "/" + id + " " + data)

def zk_create2(ip, kind, id, value):
    data = "#" + time.strftime("%c") + '\n_kind=' + kind + '\n_id=' + id + '\n' + value + '\n\n'

    f = tempfile.NamedTemporaryFile(delete=False)
    f.write(data)
    f.close()

    dest = "/tmp/%s" % uuid.uuid4()

    scp(f.name, ip, dest)
    os.unlink(f.name)

    cmd = "'val=`cat %s`; %s create %s/%s \"$val\"'" % (dest, ZK, kind, id)
    ssh(ip, cmd)

    ssh(ip, "rm %s" % dest)

def zk_set(ip, kind, id, data):
    ssh(ip, ZK + " set " + kind + "/" + id + " " + data)

