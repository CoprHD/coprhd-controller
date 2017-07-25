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

check_password() {
    local password="$1"
    ssh_execute "vipr1" "/etc/systool --getprops" "${password}"
}

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
            ssh_execute "$viprNode" "$command" "${ROOT_PASSWORD}"
        fi
    done
    wait
    set -e
}

# $1=node name
# $2=command
# $3=password
ssh_execute() {
    local viprNode="${1}"
    local command="${2}"
    local password="${3}"
    echo "${password}" | sudo -S ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null svcuser@$viprNode "echo '${password}' | sudo -S $command" &>/dev/null
}

ssh_execute_with_output() {
    local viprNode="${1}"
    local command="${2}"
    local password="${3}"
    local result=`echo "${password}" | sudo -S ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null svcuser@$viprNode "echo '${password}' | sudo -S $command" 2>/dev/null`
    echo ${result}
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
        return
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
#
# Determine if a backup is local from collected backup info in all vipr nodes
#
# params:
# $1=backup folder

is_local_backup() {
    set +e
    local backup=$1

    if ! [[ "${backup}" =~ ^\/data\/backup ]]; then
        echo "false"
        return
    fi

    if [ ${#BACKUP_INFO[@]} == 0 ]; then
        echo "false"
        return
    fi

    for info in ${BACKUP_INFO[@]}
    do
        if [ "${info}" != "" ]; then
            echo "true"
            return
        fi
    done
    set -e
    echo "false"
    return
}


# $1=backup folder
get_backup_info_from_nodes() {
    set +e
    local backup=$1
    local ls_command="ls ${backup}"
    local test_command="test -d ${backup}"
    for i in $(seq 1 ${NODE_COUNT})
    do
        local viprNode=$(get_nodeid)
        ssh_execute "${viprNode}" "${test_command}" "${ROOT_PASSWORD}"
        if [ `echo $?` == 0 ]; then
            local result=`ssh_execute_with_output "${viprNode}" "${ls_command}" "${ROOT_PASSWORD}"`
            BACKUP_INFO[$i]=${result}
        fi
    done
    echo ${BACKUP_INFO[@]}
}


is_vdc_connected() {
    BACKUP_INFO="$1"
    for info in ${BACKUP_INFO}
    do
        if [[ ${info} =~ "geodb" ]]; then
            geodb_type=${info#*_}
            geodb_type=${geodb_type%%_*}
            if [ "$geodb_type" == "geodb" ]; then
                IS_CONNECTED_VDC=false
                return
            elif [ "$geodb_type" == "geodbmultivdc" ]; then
                IS_CONNECTED_VDC=true
                return
            fi
        fi
    done
    echo -e "\nInvalid geodb type: $geodb_type, exiting.."
    exit 2
}

clean_up() {
    local command

    ret=$(is_local_backup ${RESTORE_ORIGIN})

    if [[ "${ret}" == "false" ]]; then
        command="rm -rf ${RESTORE_ORIGIN}"
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

    command="rm -rf ${TEMP_DIR}"
    loop_execute "${command}" "true"
}
