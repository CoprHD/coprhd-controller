#!/bin/bash

#This script is used for minority node recovery on vApp
usage() {
    echo "Usage:"
    echo "       $0 <corrupted host ip 1> <...>"
    echo "For example:"
    echo "       $0 xxx.xxx.xxx.1 xxx.xxx.xxx.2"
    echo "NOTE: This script could just be used in minority node corrupted scenario"
}

purge_data() {
    for corrupted_host in ${CORRUPTED_HOST[@]}; do
        echo "Purging data of $corrupted_host"
        ssh_execute ${corrupted_host} "/etc/storageos/storageos stop"
        ssh_execute ${corrupted_host} "rm -rf /data/db/1 /data/geodb/1"
    done
}

db_repair() {
   local ports=(7199 7299)
   for corrupted_host in ${CORRUPTED_HOST[@]}; do
       echo "Removing $corrupted_host from gossip ring.."
       for port in ${ports[@]}; do 
           db_corrupted_hostid=`/opt/storageos/bin/nodetool -p ${port} status | grep ${corrupted_host} | awk -F ' ' '{print $7}'`
           /opt/storageos/bin/nodetool -p ${port} removenode ${db_corrupted_hostid} &>/dev/null
       done
   done
   echo "Triggering db repair.."
   echo "/opt/storageos/bin/dbutils repair_db -db" | su storageos
   echo "/opt/storageos/bin/dbutils repair_db -geodb" | su storageos
   confirm_db_repair_finished
}

clean_tracker_info() {
    if [[ "${PRODUCT_VERSION}" == "vipr-2.4."* ]]; then
        echo "delete /config/dbDowntimeTracker/dbsvc" | /opt/storageos/bin/zkCli.sh &>/dev/null
        echo "delete /config/dbDowntimeTracker/geodbsvc" | /opt/storageos/bin/zkCli.sh &>/dev/null
    elif [[ "${PRODUCT_VERSION}" == "vipr-3.0."* ]]; then
        echo "Please check if need to delete db downtime info in zk.."
    fi
}

rebuild_data() {
    for corrupted_host in ${CORRUPTED_HOST[@]}; do
        ssh_execute ${corrupted_host} "/etc/storageos/storageos start"
        echo "Finished to recover ${corrupted_host}"
    done
}

confirm_db_repair_finished() {
    local message="Please check 'Database Housekeeping Stauts', it's finished?"
    while true; do
        read -p "$message(yes/no)" yn
        case $yn in
            [Yy]es ) echo "Db repair finished"; break;;
            [Nn]o )  echo "Waiting for db repair finished..";;
            * ) echo "Invalid input.";;
        esac
    done
}

input_password() {
    TMP_DIR="/data/test-`date +%s`"
    while true; do
        read -p "Please input cluster password for root user: " -s ROOT_PASSWORD; echo ""
        ssh_execute "$LOCAL_NODE" "mkdir -p $TMP_DIR"
        if [ -d $TMP_DIR ]; then
            rm -rf $TMP_DIR 
            break
        fi
        echo "Password is incorrect."
    done
}

ssh_execute() {
    local host=${1}
    local command=${2}
    ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null svcuser@${host} "echo '${ROOT_PASSWORD}' | sudo -S $command" &>/dev/null
}

NODE_COUNT=$(sudo /etc/systool --getprops | awk -F '=' '/\<node_count\>/ {print $2}')
LOCAL_NODE=$(sudo /etc/systool --getprops | awk -F '=' '/\<node_id\>/ {print $2}')
PRODUCT_VERSION=$(cat /opt/storageos/etc/product)
CORRUPTED_HOST=($@)
if [ ${#} -eq 0 -o ${#} -gt $[ $NODE_COUNT / 2 ] ] ; then
    usage
    exit 2
fi

comands=(input_password purge_data db_repair clean_tracker_info rebuild_data)
for cmd in ${comands[@]}
do
    $cmd
done
