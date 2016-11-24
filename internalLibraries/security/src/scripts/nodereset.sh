#!/bin/bash

usage() {
    echo "Usage:"
    echo "       $0 {reset,start} <corrupted node id> <...>"
    echo "For example:"
    echo "       $0 reset vipr2"
    echo "       $0 start vipr2 vipr3"
    echo "NOTE: This script could just be used in minority node recovery scenario"
}

reset() {
    for node in ${hosts[@]}; do
        echo "stoping storageos on $node..."
        ssh_execute ${node} "/etc/storageos/storageos stop"
        echo "Storageos on $node has been stopped."
        ssh_execute ${node} "bash -c \"rm -fr /data/db/*\""
        ssh_execute ${node} "bash -c \"rm -fr /data/geodb/*\""

        #ssh_execute ${node} "find /data/geodb -maxdepth 1 -mindepth 1 -print0 | xargs -0 rm -rf"
        echo "Purge db data on $node successful."
        ssh_execute ${node} "echo startupmode=hibernate >/tmp/startupmode"
        ssh_execute ${node} "cp /tmp/startupmode /data/db/"
        ssh_execute ${node} "cp /tmp/startupmode /data/geodb/"
        echo "Create startup file on $node successful."
    done
    wait
}

start_services() {
    for hosts in ${hosts[@]}; do
        ssh_execute ${hosts} "/etc/storageos/storageos start"
        echo "Finished to recover ${hosts}"
    done
}

ssh_execute() {
    local host=${1}
    local command=${2}
    ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null svcuser@${host} "echo '${root_password}' | sudo -S $command" &>/dev/null
}

case $1 in
reset)
    shift
    root_password=$1
    shift
    hosts=$@
    reset
    ;;
start)
    shift
    root_password=$1
    shift
    hosts=$@
    start_services
    ;;
*)
    usage
    exit 0
    ;;
esac




