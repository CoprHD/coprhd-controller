#!/bin/bash
# This script is to be used by restore or 
# syssvc

DIR=$(dirname $0)
. ${DIR}/libs.sh

start_service() {
    echo -n "Starting storageos services on all nodes.."
    local command="/etc/storageos/storageos start"
    loop_execute "${command}" "true"
    echo "done"
    finish_message
}

stop_service() {
    echo -n "lby Stopping storageos services on all nodes ... "
    local command="/etc/storageos/storageos stop"
    loop_execute "${command}" "true"
    echo "done"
}

# $1=root password
restore_data() {
    echo -n "lby0 Restoring data on all nodes.."
    set +e
    RESTORE_RESULT="successful"
    for i in $(seq 1 $NODE_COUNT)
    do
        local viprNode=$(get_nodeid)
        ls $RESTORE_DIR/*_${viprNode}_* &>/dev/null
        if [ $? == 0 ]; then
            restore_node "${viprNode}"
        else
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

RESTORE_RESULT=""
RESTORE_DIR="/data/restore-`date +%s`"
NODE_COUNT=`/etc/systool --getprops | awk -F '=' '/\<node_count\>/ {print $2}'`
LOCAL_NODE=`/etc/systool --getprops | awk -F '=' '/\<node_id\>/ {print $2}'`

ROOT_PASSWORD="$1"
RESTORE_GEO_FROM_SCRATCH="$2"
IS_CONNECTED_VDC="$3"
LOG_FILE="$4"

# if the log file is given, write the stdout/stderr
# to the log file
if [ "${LOG_FILE}" != "" ] ; then
    exec 1>${LOG_FILE} 2&>1
fi

stop_service
restore_data
