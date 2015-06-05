#!/bin/bash

# Copyright 2015 EMC Corporation
# All Rights Reserved
#
#%stage: filesystem
#%provides: dmroot
#%depends:
#
#%programs: /sbin/losetup /sbin/blockdev /sbin/findfs /sbin/dumpe2fs /sbin/dmsetup /bin/dd /sbin/vgscan /sbin/vgchange /sbin/lvcreate /sbin/lvm /sbin/lvs /sbin/lvremove
#%modules: squashfs loop dm_snapshot ext3
#
#%if: ! "$root_already_mounted"
#%dontshow
#
##### dmsetup for a COW root device
##
## This script sets up a writable (copy-on-write) snapshot of a compressed
## read-only root device image.
##
## This script runs only if ${root}=/dev/mapper/root and ${rootimg} is set
##

# Breakpoint
# - Stop if the argument:
#   - is "force"
#   - or, it is listed in the boot arg ${bp} (e.g. bp=name1,name2,...)
# - Ctlr-D returns back to execution
#
_breakpoint() {
    local b
    for b in $(IFS="${IFS},:" ; echo ${bp}:force) ; do
        if [ "${b}" = "${1}" ] ; then
            echo "*** ${1}: Breaking to /bin/bash. Press <Ctrl+D> to return." >&2
            /bin/bash -i
            return 0
        fi
    done
    return 0
}
           
# Error handler
# - Print the line the resulted in error
# - Include the file name and the line number
#
_err_handler() {
    echo "*** Error: ${BASH_SOURCE[0]} Line ${BASH_LINENO[0]}: $(eval echo ${BASH_COMMAND})" >&2
    echo "*** Breaking to /bin/bash. Press <Ctrl+D> to return."                              >&2
    /bin/bash -i
}

# Mount
_mount() {
    local mnt=${@: -1: 1}
    mkdir -p "${mnt}"
    mount "${@}"
}

# Fsck
_fsck() {
    local dev=${@: -1: 1}
    echo "${dev} /mnt ext3 defaults 1 1" >/etc/fstab
    fsck "${@}"
}

#Time Sync
# - Check if the system time is lagging the SUPERBLOCk time
# - If lagging, adjust system time to be 1 minute ahead of SUPERBLOCK time
#
_timesync() {        
    #Set all the variables
    local dev_time=$(dumpe2fs -h ${1} 2>- | sed -n 's/Last mount time: *\([^ ].*\)/\1/p') #last mount time in superblock
    local dev_secs=$(date --date="${dev_time}" +%s) #Convert dev_time to UTC in seconds since epoch 
    local sys_secs=$(date +%s) 
 
    #Check if system time behind superblock time 
    if [ "${sys_secs}" -lt "${dev_secs}" ]; then 
	date --set="$(date -d @"${dev_secs}") 1 MINUTE" >- #Set System Clock 1 minute ahead of superblock time
   	echo "Adjusting system clock: ${1}=${dev_secs}s current=${sys_secs}s new=$(date +%s)s"
    fi
}

# probe if a cdrom device is readable (has medium inside)
_probe() {
    local dev=${1} 
    # udevadm settle # ; [ -e "${dev}" ] || return 1
    local retry
    for retry in 1 2 3 4 5 ; do
        local err
        err=$(dd if=${dev} of=/dev/zero count=1 2>&1) && return 0
        case "${err}" in 
            *'No medium found') return 1;;
            *)                  echo "${err}" >&2;;
        esac
        sleep 1
    done
}

# Setup a copy-on-write root device 
#
_dmroot() {
    local bootfs_dev=${1}
    local rootimg=${2}
    local rootcow=${3}

    # ViPR installation, configuration, redeployment and normal running 
    # scenarios are sharing the same kernel and initrd.  They might be 
    # put into different medias (hard disk and cdrom etc.)
    # Bootfs Device Detail:
    #   1. If the top level ${bootfs_dev} has been set to UUID=... in the top level grub.cfg, then bootfs_dev will be set to real bootfs device name.
    #   2. If the top level ${bootfs_dev} has been set to device name in the top level grub.cfg, then bootfs_dev will be kept as it is.
    #   3. If the top level ${bootfs_dev} has not been set in the top level grub.cfg, then bootfs_dev will be set by default to "/dev/sda1"
    if [[ ${bootfs_dev} == UUID=* ]]; then
        # Boot from bootfs disk (identified via UUID)
        bootfs_dev=$(blkid -U 3c426f75-726e-6520-2f62-6f6f7466733e)
    elif [[ ${bootfs_dev} == /dev/* ]]; then
        # Boot from specific bootfs device (identified via device name)
        :
    else
        # Boot from default bootfs device (/dev/sda1) for backward compatibility
        bootfs_dev="/dev/sda1"
    fi
    echo "Booting from bootfs device ${bootfs_dev} ..."
   
    _breakpoint dmroot_start

    # Set all the variables
    local bootfs_mntp='/.volumes/bootfs'
    local sqfs_file="${bootfs_mntp}/${rootimg}"
    local sqfs_mntp="/.volumes/${rootimg##*/}"
    local ext3_file="${sqfs_mntp}/rootfs"
    local ext3_loop
    local ext3_size

    # Load necessary modules
    modprobe loop max_loop=${max_loop:-64} max_part=${max_part:-64}
    modprobe squashfs
    modprobe dm_snapshot
    udevadm settle

    # Set up a read-only loopback over the system disk partition
    _breakpoint dmroot_premount
    _timesync "${bootfs_dev}"
   
    if [ "X${bootfs_dev}" = "X/dev/sr" ]; then
        # Probe which cdrom device is readable and mount it
        trap - ERR
        for number in 0 1 2 3 4 ; do
            _probe ${bootfs_dev}${number}
            if [ $? -eq 0 ]; then
                bootfs_dev=${bootfs_dev}${number}
                echo "Found cdrom ${bootfs_dev}" >&2
                break
            fi
        done
        trap _err_handler ERR 
    
        _mount -t iso9660 -o ro       "${bootfs_dev}" "${bootfs_mntp}"
    else
        _fsck  -t ext3 -V -a          "${bootfs_dev}"
        _mount -t ext3 -o ro          "${bootfs_dev}" "${bootfs_mntp}"
    fi
    _mount -t squashfs -o ro,loop "${sqfs_file}"  "${sqfs_mntp}"
    _breakpoint dmroot_mount
    ext3_loop=$(losetup --show -r -f "${ext3_file}")
    _breakpoint dmroot_losetup
    ext3_size=$(blockdev --getsz "${ext3_loop}")
    umount -l "${sqfs_mntp}"
    _timesync "${ext3_loop}"    

    # First, set up the cow device
    # Then, create a cow snapshot base root device (512 means 256KB block)
    case ${rootcow} in
        initramfs)
            dd if=/dev/zero of=/cow bs=1M count=512
            rootcow_dev=$(losetup --show -f /cow)
            udevadm settle
            _breakpoint dmroot_dmsetup
            dmsetup create root --table "0 ${ext3_size} snapshot ${ext3_loop} ${rootcow_dev} n 1"
            #export rootflags='errors=remount-ro'
            ;;
        '')         
            local rootcow_vg="cow"
            rootcow_dev="/dev/mapper/${rootcow_vg}-root"
            vgscan --mknodes
            udevadm settle
            vgchange -a y "${rootcow_vg}"
            udevadm settle
            _breakpoint dmroot_lvremove
            [ -b "${rootcow_dev}" ] && lvremove -f "${rootcow_dev}"
            udevadm settle
            _breakpoint dmroot_lvcreate
            lvcreate --noudevsync --contiguous y --zero y --name "root" --size $(( ${ext3_size} / 2 ))K ${rootcow_vg}
            udevadm settle
            [ -e "${rootcow_dev}" ] || { echo "${rootcow_dev}: No such file or device." >&2 ; exit 1 ; }
            [ -b "${rootcow_dev}" ] || { echo "${rootcow_dev}: No a block device."      >&2 ; exit 1 ; }
            _breakpoint dmroot_dmsetup
            dmsetup create root --table "0 ${ext3_size} snapshot ${ext3_loop} ${rootcow_dev} p 512"
            ;;
        *)
            echo "${rootcow}: Assuming a regular block device." 
            _breakpoint dmroot_dmsetup
            dmsetup create root --table "0 ${ext3_size} snapshot ${ext3_loop} ${rootcow} p 512"
    esac
    udevadm settle

    export root="/dev/mapper/root"
    export rootdev=${root}
    export rootfstype=ext3
    export rootflags=${rootflags:-rw,barrier=1,errors=panic}

    _breakpoint dmroot_mount

    _fsck  -t "${rootfstype}" -a                "${rootdev}"
    _mount -t "${rootfstype}" -o "${rootflags}" "${rootdev}" /root

    if [ "${rootcow}" = "initramfs" ]; then
        echo "${rootcow}: preventing default /data file system to be mounted during installation ... " 
        sed -i -s '/UUID/ s/^/#/' /root/etc/fstab
        sed -i -s '/\/data\/logs/ s/^/#/' /root/etc/fstab
    fi

    _mount -M "${bootfs_mntp}"                               /root/.volumes/bootfs
    export root_already_mounted=true
    # set this variable so that boot.rootfsck
    # does not try to execute fsck on rootfs
    export ROOTFS_FSCK=0

    _breakpoint dmroot_end
}

if [ -n ${bootfs_dev} -a -n "${rootimg}" -a "${root}" = "/dev/mapper/root" ] ; then
    trap _err_handler ERR 
    set -E
    echo "bootfs_dev=${bootfs_dev}"
    echo "rootcow=${rootcow}"
    _dmroot ${bootfs_dev} ${rootimg} ${rootcow} 
    set +E
    trap - ERR
fi

# END
