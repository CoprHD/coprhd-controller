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

    local node_with_zk_data=""
    local cmd="ls ${RESTORE_DIR}/*_zk.zip"

    set +e  # allow command to be executed failed
    for i in $(seq 1 ${NODE_COUNT})
    do                                                                                        
        local viprNode=$(get_nodeid)
        ssh_execute "$viprNode" "$cmd" "${ROOT_PASSWORD}"
        if [[ $? -eq 0 ]]; then
            node_with_zk_data="$viprNode"
        else
            nodes_without_zk_data+=(${viprNode})
        fi
    done
    set -e

    echo "nodes wihtout zk data: ${nodes_without_zk_data[@]}"
    echo "node with zk data: ${node_with_zk_data}"
    if [ "${#nodes_without_zk_data[@]}" == 0 ] ; then
       #all nodes have zk data
       return
    fi

    #copy zk data from node_with_zk_data to nodes_without_zk_data
    cmd="scp svcuser@${node_with_zk_data}:${RESTORE_DIR}/*_zk.* ${RESTORE_DIR}"
    for node in ${nodes_without_zk_data[@]}
    do
        ssh_execute "${node}" "${cmd}"
    done
}

restore_data() {
    echo -n "Restoring data on all nodes.."
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

trap clean_up EXIT

RESTORE_RESULT=""
NODE_COUNT=`/etc/systool --getprops | awk -F '=' '/\<node_count\>/ {print $2}'`
LOCAL_NODE=`/etc/systool --getprops | awk -F '=' '/\<node_id\>/ {print $2}'`
nodes_without_zk_data=()

RESTORE_DIR="$1"
ROOT_PASSWORD="$2"
RESTORE_GEO_FROM_SCRATCH="$3"
IS_CONNECTED_VDC="$4"
LOG_FILE="$5"

echo "Restore_dir=${RESTORE_DIR}"
# if the log file is given, write the stdout/stderr
# to the log file
if [ "${LOG_FILE}" != "" ] ; then
    exec 1>${LOG_FILE} 2&>1
fi

stop_service
copy_zk_data
restore_data
