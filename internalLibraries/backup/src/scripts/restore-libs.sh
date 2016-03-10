#!/bin/echo "This is only used as a library"
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

# $1=command
# $2=include local (a boolean flag)
loop_execute() {
    set +e
    local command=${1}
    local includeLocal=${2}

    for i in $(seq 1 ${NODE_COUNT})
    do
        local viprNode=$(get_nodeid)
        if [ "$viprNode" != "$LOCAL_NODE" -o "$includeLocal" == "true" ]; then
            ssh_execute "$viprNode" "$command" "${ROOT_PASSWORD}"&
        fi
    done
    wait
    set -e                                                                      
}

# $1=node name
# $2=command
ssh_execute() {
    local viprNode=${1}
    local command=${2}
    echo "${ROOT_PASSWORD}" | sudo -S ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null svcuser@$viprNode "echo '${ROOT_PASSWORD}' | sudo -S $command" &>/dev/null
}

get_nodeid() {
    if [ ${NODE_COUNT} -eq 1 ]; then
        echo "${LOCAL_NODE}"
    else
        echo "vipr$i"
    fi
}

finish_message() {
    echo "Restore ${RESTORE_RESULT}!"
    if [ "${RESTORE_RESULT}" == "failed" ]; then
        echo "Please check bkutils.log for the details."
        exit 1
    fi
    echo "Note: nodes will reboot if there is any change of property in this cluster."
    if [[ ${IS_CONNECTED_VDC} == true ]]; then
        if [ "$RESTORE_GEO_FROM_SCRATCH" == "false" ]; then
            echo "Please reconnect this vdc after the status of cluster is stable."
        fi
        echo "(If there is any vdc with version 2.1 in this geo federation, then you need to remove blacklist manually from other vdcs,"
        echo "by using this command: \"/opt/storageos/bin/dbutils geoblacklist reset <vdc short id>\")"
    fi    
}

# local backup includes:
# 1. The backup created locally
# 2. The downloaded backup
is_local_backup() {
    if [[ "${RESTORE_DIR}" =~ ^\/data\/backup ]]; then
        echo "true"
    else
        echo "false"
    fi
}

is_vdc_connected() {
    cd ${RESTORE_DIR}
    local geo_files=($(ls -f *geodb*.zip))
    geodb_type=${geo_files[0]}
    geodb_type=${geodb_type#*_}
    geodb_type=${geodb_type%%_*}

    if [ "$geodb_type" == "geodb" ]; then
        IS_CONNECTED_VDC=false
    elif [ "$geodb_type" == "geodbmultivdc" ]; then
        IS_CONNECTED_VDC=true
    else
        echo -e "\nInvalid geodb type: $geodb_type, exiting.."
        exit 2
    fi
}

clean_up() {
    local is_local_backup=$(is_local_backup)
    local command

    if [[ "${is_local_backup}" == "false" ]]; then
        command="rm -rf $RESTORE_DIR"
        loop_execute "${command}" "true" "${NODE_COUNT}" "${LOCAL_NODE}" "${ROOT_PASSWORD}"
    else
       command="rm -f ${RESTORE_DIR}/*_zk.*" 
       for node in ${nodes_without_zk_data[@]}
       do
            ssh_execute "${node}" "${command}" "${ROOT_PASSWORD}"
       done

       command="rm -f ${RESTORE_DIR}/*_info.properties" 
       for node in ${nodes_without_properties_file[@]}
       do
            ssh_execute "${node}" "${command}" "${ROOT_PASSWORD}"
       done
    fi
}
