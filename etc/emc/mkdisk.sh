#!/bin/bash -e
#
# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# etc/mkdisk.sh
#
# Helper script to initialize container disks
#

declare -a on_exit_actions

_fatal() {
    echo "$0: Error: $*" >&2
    exit 1
}

_err_handler() {
    set +Ex
    _fatal "Line ${LINENO}: $(eval echo \"${BASH_COMMAND}\")"
}

_exit_handler() {
    set +E
    for action in "${on_exit_actions[@]}" ; do
        eval "${action}"
    done
}

_set_on_exit() {
    on_exit_actions=( "${@}" "${on_exit_actions[@]}" )
}

_set_traps() {
    trap _exit_handler EXIT
    trap _err_handler  ERR
    if [ -n "${*}" ] ; then set "${@}" ; fi
}

_ismounted() {
    ( set +x ; while read dev mnt type flags dummy ; do case ${#} in
        1) [ "${dev}" = "${1}" -o "${mnt}" = "${1}" ] &&  break ;;
        2) [ "${dev}" = "${1}" -a "${mnt}" = "${2}" ] &&  break ;;
    esac ; done </proc/mounts )
}

_mount() {
    local dev=${@: -2: 1}
    local mnt=${@: -1: 1}
    if ! _ismounted "${dev}" "${mnt}" ; then
        mkdir -p "${mnt}"
        mount "${@}" && _set_on_exit "_umount ${mnt}"
    fi
}

_umount() {
    if _ismounted "${1}" ; then
        umount "${1}" || return $?
    fi
}

_set_traps -Ex

LABELS="
    bootfs 3c426f75-726e-6520-2f62-6f6f7466733e
    cow    ######-###B-ourn-e#co-w#PV-####-######
    data   3c426f75-726e-6520-2f64-6174613e0000
    ovfenv 3c426f75-726e-6520-2f6f-7666656e763e
    native 3c426f75-726e-6520-2f6e-61746976653e"
    
BOOTFS_PARTITION_UUID="3c426f75-726e-6520-2f62-6f6f7466733e"
COW_PARTITION_UUID="######-###B-ourn-e#co-w#PV-####-######"
DATA_PARTITION_UUID="3c426f75-726e-6520-2f64-6174613e0000"

_usage() {
    set +Ex
    [ -n "${*}" ] && echo "${*}"  >&2
    echo "Usage: $0 label device" >&2
    echo "Labels:"                >&2
    for label in $(_getlabels) ; do
        echo "    ${label}"       >&2
    done
    exit 2
}

_getuuid() {
    echo "${LABELS}" | while read label uuid ; do [ "${label}" = "${1}" ] && echo "${uuid}" && return 0 ; done
}

_getlabels() {
    echo "${LABELS}" | while read label uuid ; do echo "${label}" ; done
}

_mkpart() {
    local dev=${1}
    local label=${2}
    local size
    udevadm settle
    size=$(blockdev --getsz ${dev}) 
    dd if=/dev/zero of="${dev}" bs=10240k count=1 oflag=direct
    blockdev --rereadpt ${dev} >/dev/null || { sleep 1; blockdev --rereadpt ${dev} >/dev/null ; }
    udevadm settle
    parted --script ${dev} mktable gpt
    parted --script ${dev} mkpart ${label} 2048s $(( ${size} / 2048 * 2048 - 2049 ))s
    if [ "${label}" == "bootfs" ] ; then
        parted --script ${dev} mkpart biosboot 128s 2047s
        parted --script ${dev} set 2 bios_grub on
    fi
    udevadm settle
}         
         
_mkparts() {
    local dev=${1}
    local size
    udevadm settle
    size=$(blockdev --getsz ${dev}) 

    # init gpt 
    dd if=/dev/zero of="${dev}" count=3 oflag=direct # Wipe the gpt at the beginning of the device
    dd if=/dev/zero of="${dev}" seek=$((size-2)) count=2 oflag=direct # Wipe the gpt backup at the end of the device
    dd if=/dev/zero of="${dev}" bs=10240k count=1 oflag=direct
    blockdev --rereadpt ${dev} >/dev/null || { sleep 1; blockdev --rereadpt ${dev} >/dev/null ; }
    udevadm settle
    parted --script ${dev} mktable gpt
    
    # create bootfs part (4G)
    bootfssize=$((4 * 1024 * 1024 * 2))
    bootfs_end_size=${bootfssize}
    bootfs_start="2048s"
    bootfs_end=$(( ${bootfs_end_size} / 2048 * 2048 - 1 ))s
    parted --script ${dev} mkpart "bootfs" ${bootfs_start} ${bootfs_end}

    # create cow part (16G) 
    cowsize=$((16 * 1024 * 1024 * 2 ))
    cow_end_size=$((${cowsize} + ${bootfssize})) 
    cow_start=$(( ${bootfs_end_size} / 2048 * 2048 ))s
    cow_end=$(( ${cow_end_size} / 2048 * 2048 - 1 ))s
    parted --script ${dev} mkpart "cow" ${cow_start} ${cow_end}

    # create data part (Take the rest of the disk) 
    ovfsize=$((10 * 1024 * 2 ))
    data_end_size=$((${size} - ${ovfsize} -2)) # Need to set last two sectors apart for the partition table backup
    data_start=$(( ${cow_end_size} / 2048 * 2048 ))s
    data_end=$(( ${data_end_size} / 2048 * 2048 - 1))s
    parted --script ${dev} mkpart "data" ${data_start} ${data_end}
    
    # create ovf-env iso part (10M)   
    ovf_end_size=$((${size} -2))
    ovf_start=$(( ${data_end_size} / 2048 * 2048 ))s
    ovf_end=$(( ${ovf_end_size} / 2048 * 2048 - 1))s
    parted --script ${dev} mkpart "ovf" ${ovf_start} ${ovf_end}

    # create biosboot part and set bios_grub flag 
    parted --script ${dev} mkpart biosboot 128s 2047s
    parted --script ${dev} set 5 bios_grub on

    udevadm settle
}    

_mkfs() { 
    local dev=${1}
    local label=${2}
    local uuid=${3}
    mkfs -t ext3 -L ${label} -U ${uuid} ${dev}
}
    
_mklvm() {
    local dev=${1}
    local label=${2}
    local uuid=${3}
    pvcreate --uuid ${uuid}   --norestorefile    ${dev} -ff -y
    vgcreate --physicalextentsize 1024K ${label} ${dev}
}

_mkgrub() {
    local dev=${1}
    local mnt=/mnt/.volumes/bootfs
    export PATH=/usr/local/sbin:/usr/local/bin:$PATH
    
    mkdir -p ${mnt} 
    mount -t ext3 ${dev}1 ${mnt}
    grub2-install --root-directory=${mnt} ${dev}
    umount ${mnt}
}

_check_existing_system() {
    local dev=$1 
    # Look for all the partitions on the selected device
    _is_uuid_used "${dev}" "${BOOTFS_PARTITION_UUID}" && {
        _is_uuid_used "${dev}" "${COW_PARTITION_UUID}"
    } && {
        _is_uuid_used "${dev}" "${DATA_PARTITION_UUID}"
    } && {
        echo -e "\n\e[1;31mThis device has all the partitions needed by ViPR, It might have ViPR installed already, please try boot from hard drive to check it.\e[0m"
        echo -e "\n\e[1;31mYou can choose to install ViPR anyway, but it will wipe the data on this device\n***\e[0m"
        exit 1
    } || _check_uuids_on_other_devices $dev
}

_is_uuid_used() {
    local uuid=${2}
    local dev=${1}
    local devoutput=$(blkid ${dev}* | grep $uuid) 
    [ -z $devoutput ] && return 1 || return 0
}

_check_uuids_on_other_devices() {
    local dev=${1}
    local output
    output+=$(_check_single_uuid_on_other_devices "${dev}" "${BOOTFS_PARTITION_UUID}")
    output+=$(_check_single_uuid_on_other_devices "${dev}" "${COW_PARTITION_UUID}")
    output+=$(_check_single_uuid_on_other_devices "${dev}" "${DATA_PARTITION_UUID}")
    [ -z $output ] && return 0 || {
        echo -e "${output}"
        exit 2
    }
}

_check_single_uuid_on_other_devices() {
    local dev=${1}
    local uuid=${2}
    local devname=$(_get_partition_on_other_disk_by_uuid $dev ${uuid}) # It's only possible to find the filesystem with that uuid on other disk
    [ -z $devname ] || {
        # If we find such a partition
        local newuuig=$(uuidgen)
        echo -e "\n\e[1;31mDevice ${devname} is using uuid ${uuid} already, ViPR installation will change it to a randomly generated uuid ${newuuig} and free ${uuid} for use\e[0m"
    }   
}

_get_partition_on_other_disk_by_uuid() {
    local dev=${1}
    # Get partition with this uuid that is not on selected device
    local uuid=${2}
    local devname=$(blkid | grep -v ${dev} | grep $uuid) # We are looking for partitions not on the selected device, and dev variable is passed down from _check_existing_system method
    [ -z $devname ] || devname=${devname%%:*}      
    echo $devname
}

_change_uuid_if_needed() {
    local dev=${1}
    local uuid=${2}
    local devname=$(_get_partition_on_other_disk_by_uuid $dev ${uuid}) # It's only possible to find the filesystem with that uuid on other disk
    [ -z $devname ] || {
        # If we find such a partition
        local newuuig=$(uuidgen)      
        tune2fs -U ${newuuig} ${devname} # Change the uuid of that filesystem to a randomly generated uuid  
    }   
}

_mkdir2() {
    [ -d "${3}" ] || {
        [ -e "${3}" ] && rm "${3}"
        mkdir "${3}"
    }
    [ $(stat -c "%U:%G" "${3}") = ${2} ] || chown "${2}" "${3}"
    [ $(stat -c %a "${3}") = ${1} ] || chmod "${1}" "${3}"
}

_mkfile() {
    [ -f "${3}" ] || {
        [ -e "${3}" ] && rm -rf "${3}"
        touch "${3}"
    }
    [ $(stat -c "%U:%G" "${3}") = ${2} ] || chown "${2}" "${3}"
    [ $(stat -c %a "${3}") = ${1} ] || chmod "${1}" "${3}"
}

_make_data_disk_layout() {
    dev=${1}
    mnt=/data
    _mount -t ext3 ${dev} ${mnt}
    _mkdir2  755 storageos:storageos ${mnt}/db
    _mkdir2  755 storageos:storageos ${mnt}/geodb
    _mkdir2  755 storageos:storageos ${mnt}/zk
    _mkdir2  755 storageos:storageos ${mnt}/backup
    _mkdir2  755 storageos:storageos ${mnt}/install
    _mkdir2  755 storageos:storageos ${mnt}/connectemc
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/archive
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/failed
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/history
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/logs
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/output
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/poll
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/queue
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/recycle
    _mkdir2  755 storageos:storageos ${mnt}/connectemc/tmp
    _mkdir2 1777 root:root           ${mnt}/logs
    _mkdir2  755 root:root           ${mnt}/logs/ConsoleKit
    _mkdir2  700 root:root           ${mnt}/logs/YaST2
    _mkdir2  755 root:root           ${mnt}/logs/atop
    _mkdir2  700 root:root           ${mnt}/logs/krb5
    _mkdir2  755 root:root           ${mnt}/logs/sa
    _mkdir2  755 root:root           ${mnt}/logs/zypp
    _mkfile  654 root:tty            ${mnt}/logs/wtmp
    _mkfile  644 root:tty            ${mnt}/logs/lastlog
}

if [[ $EUID -ne 0 ]]; then
  echo "You are not running the script as root user" 2>&1
  exit 1
fi
label=${1}
dev=${2} ; [ -b ${dev} ]   || _usage "Invalid device: ${dev}"

case ${label} in
    'bootfs') 
              uuid=$(_getuuid ${label})  || _usage "Invalid label: ${label}"
              _mkpart  ${dev}  ${label}
              _mkfs    ${dev}1 ${label} ${uuid}
              _mkgrub  ${dev}
              ;;
    'cow')    
              uuid=$(_getuuid ${label})  || _usage "Invalid label: ${label}"
              _mkpart  ${dev}  ${label}
              _mklvm   ${dev}1 ${label} ${uuid}
              ;;
    'data')   
              uuid=$(_getuuid ${label})  || _usage "Invalid label: ${label}"
              _mkpart  ${dev}  ${label}
              _mkfs    ${dev}1 ${label} ${uuid}
              _make_data_disk_layout  ${dev}1
              ;;
    'ovfenv')   
              uuid=$(_getuuid ${label})  || _usage "Invalid label: ${label}"
              _mkpart  ${dev}  ${label}
              _mkfs    ${dev}1 ${label} ${uuid}
              ;;
    'check')
              _check_existing_system ${dev}
              ;;
    'native') 
              _mkparts ${dev}
              
              label="bootfs" 
              uuid=$(_getuuid ${label}) || _usage "Invalid label: ${label}"
              _change_uuid_if_needed ${dev} $uuid
              _mkfs    ${dev}1 ${label} ${uuid}
              _mkgrub  ${dev}
                           
              label="cow" 
              uuid=$(_getuuid ${label}) || _usage "Invalid label: ${label}"
              _change_uuid_if_needed ${dev} $uuid
              _mklvm   ${dev}2 ${label} ${uuid}
                          
              label="data"
              uuid=$(_getuuid ${label}) || _usage "Invalid label: ${label}"
              _change_uuid_if_needed ${dev} $uuid
              _mkfs    ${dev}3 ${label} ${uuid}
                           
              label="ovfenv"
              uuid=$(_getuuid ${label}) || _usage "Invalid label: ${label}"
              _change_uuid_if_needed ${dev} $uuid
              _mkfs    ${dev}4 ${label} ${uuid}
              
              _make_data_disk_layout  ${dev}3
              ;;
    *)        _usage "Invalid label: ${label}"
esac

