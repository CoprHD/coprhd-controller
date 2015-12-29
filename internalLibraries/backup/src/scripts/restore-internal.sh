#!/bin/bash
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

# This script is to be used by restore or 
# syssvc

DIR=$(dirname $0)
. ${DIR}/restore-libs.sh

start_service() {
    echo -n "Starting storageos services on all nodes ... "
    local command="/etc/storageos/storageos start"
    loop_execute "${command}" "true"
    echo "done"
    finish_message
}

stop_service() {
    echo -n "Stopping storageos services on all nodes ... "
    local command="/etc/storageos/storageos stop"
    loop_execute "${command}" "true"
    echo "done"
}

copy_zk_data() {
    local is_local_backup=$(is_local_backup)

    if [[ "${is_local_backup}" == "false" ]]; then
       # remote backup has already copied zk data to all nodes
       return
    fi

    copy_missing_files '*_zk.zip'

    nodes_without_zk_data=${nodes_without_files[@]}
}

copy_properties_file() {
    local is_local_backup=$(is_local_backup)

    if [[ "${is_local_backup}" == "false" ]]; then
       # remote backup has already copied zk data to all nodes
       return
    fi

    copy_missing_files '*_info.properties'
    nodes_without_properties_file=${nodes_without_files[@]}
}

#$1=mising files
copy_missing_files() {
    nodes_without_files=()

    local node_with_file
    local missing_files="$1"
    local cmd="bash -c 'ls ${RESTORE_DIR}/${missing_files}'"
    set +e  # allow command to be executed failed
    for i in $(seq 1 ${NODE_COUNT})
    do                                                                                        
        local viprNode=$(get_nodeid)
        ssh_execute "$viprNode" "$cmd" "${ROOT_PASSWORD}"
        if [[ $? -eq 0 ]]; then
            node_with_file="$viprNode"
        else
            nodes_without_files+=(${viprNode})
        fi
    done
    set -e

    echo "Nodes without ${missing_files}: ${nodes_without_files[@]}"
    echo "Node with file ${missing_files}: ${node_with_file}"
    if [ "${#nodes_without_files[@]}" == 0 ] ; then
       #all nodes have zk data
       return
    fi

    #copy the missing files from node_with_that file to nodes_without them
    cmd="scp svcuser@${node_with_file}:${RESTORE_DIR}/${missing_files} ${RESTORE_DIR}"
    for node in ${nodes_without_files[@]}
    do
        ssh_execute "${node}" "${cmd}"
    done
}

restore_data() {
    echo "Restoring data on all nodes ... "
    set +e
    RESTORE_RESULT="successful"
    for i in $(seq 1 $NODE_COUNT)
    do
        local viprNode=$(get_nodeid)
        local command="bash -c 'ls $RESTORE_DIR/*_${viprNode}* &>/dev/null'"
        ssh_execute "$viprNode" "${command}"
        if [ $? == 0 ]; then
            echo "To restore node ${viprNode}"
            restore_node "${viprNode}"
        else
            echo "To purge node ${viprNode}"
            purge_node "${viprNode}"
        fi
        if [ $? != 0 ]; then
            echo -n "failed on ${viprNode}.."
            RESTORE_RESULT="failed"
        fi
    done
    set -e
    echo "done"
}

# $1=node name
restore_node() {
    local viprNode=${1}
    cd ${RESTORE_DIR}
    local backupTag=`ls *_info.properties | awk '{split($0,a,"_"); print a[1]}'`
    local command="/opt/storageos/bin/bkutils -r $RESTORE_DIR $backupTag"
    if [ "$RESTORE_GEO_FROM_SCRATCH" == "true" ]; then
        command="/opt/storageos/bin/bkutils -r ${RESTORE_DIR} $backupTag -f"
    fi
    ssh_execute "$viprNode" "$command"
}

purge_node() {
    local viprNode=${1}
    initdb="no"
    if [ "$IS_CONNECTED_VDC" == "true" ]; then
        initdb="yes"
    fi
    local command="/opt/storageos/bin/bkutils -p $initdb"
    ssh_execute "$viprNode" "$command"
}

sigterm_handler() {
   echo "SIGTERM is received"
}

trap sigterm_handler SIGTERM
trap clean_up EXIT

RESTORE_RESULT=""
NODE_COUNT=`sudo /etc/systool --getprops | awk -F '=' '/\<node_count\>/ {print $2}'`
LOCAL_NODE=`sudo /etc/systool --getprops | awk -F '=' '/\<node_id\>/ {print $2}'`
nodes_without_zk_data=()
nodes_without_properties_file=()

RESTORE_DIR="$1"
ROOT_PASSWORD="$2"
RESTORE_GEO_FROM_SCRATCH="$3"
LOG_FILE="$4"

# if the log file is given, write the stdout/stderr
# to the log file
if [ "${LOG_FILE}" != "" ] ; then
    exec 1>${LOG_FILE} 2>&1
fi

copy_zk_data
copy_properties_file
is_vdc_connected
stop_service
restore_data
if [[ "${RESTORE_RESULT}" == "failed" ]]; then
   finish_message
fi
start_service
