#!/bin/bash

# Copyright 2015 EMC Corporation
# All Rights Reserved
#
#
#%programs: /sbin/losetup /sbin/blockdev /sbin/dumpe2fs /sbin/dmsetup /bin/dd /sbin/vgscan /sbin/vgchange /sbin/lvcreate /sbin/lvm /sbin/lvs /sbin/lvremove
#%modules: squashfs loop dm_snapshot ext3
#
#

##
## Dracut mount hook 
##
## This script sets up a writable (copy-on-write) snapshot of a compressed
## read-only root device image, and mount root filesystem
##
## This script runs only if root=dmroot:[bootfs_dev][:cow_dev] and rootimg=${rootimg}
## is set on kernel command line
##

. /lib/dmroot-lib.sh

# Check kernel command line
root=$(getarg root=)
[ -z "${root}" ] && die "***** no root specified"
dmroot_to_var ${root}
# bootfs_dev may be empty when upgrade from previous version. Use default value /dev/sda1 
# for this case
[ -z "${bootfs_dev}" ] && echo "***** no bootfs dev specified"
rootcow=${cow_dev}
rootimg=$(getarg rootimg=)
[ -z "${rootimg}" ] && die "***** no rootimg specified"

# Breakpoint (Not applied to dracut anymore)
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
    die "*** Error: ${BASH_SOURCE[0]} Line ${BASH_LINENO[0]}: $(eval echo ${BASH_COMMAND})"
}

# Mount
_mount() {
    local mnt=${@: -1: 1}
    [ -d ${mnt} ] || mkdir -p "${mnt}"
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

# HACK - disable lvm lock. we need create logical volume. Same trick is used in 90lvm/lvm_scan.sh
# No need to re-enable the lock again on initramfs. We are almost last step of kernel boot
_hack_lvlock() {
    sed -i -e 's/\(^[[:space:]]*\)locking_type[[:space:]]*=[[:space:]]*[[:digit:]]/\1locking_type =  1/' ${initdir}/etc/lvm/lvm.conf
}

# Setup a copy-on-write root device 
#
_dmroot() {
    local rootimg=${1}
    local rootcow=${2}

    _hack_lvlock

    _breakpoint dmroot_start

    # Set all the variables
    local bootfs_dev=${bootfs_dev:-'/dev/sda1'}
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
    _fsck  -t ext3 -V -a          "${bootfs_dev}"
    _mount -t ext3 -o ro          "${bootfs_dev}" "${bootfs_mntp}"
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
            rootcow=$(losetup --show -f /cow)
            udevadm settle
            _breakpoint dmroot_dmsetup
            dmsetup create root --table "0 ${ext3_size} snapshot ${ext3_loop} ${rootcow} n 1"
            export rootflags='errors=remount-ro'
            ;;
        '')         
            local rootcow_vg="cow"
            rootcow="/dev/mapper/${rootcow_vg}-root"
            vgscan --mknodes
            udevadm settle
            vgchange -a y "${rootcow_vg}"
            udevadm settle
            _breakpoint dmroot_lvremove
            [ -b "${rootcow}" ] && lvremove -f "${rootcow}"
            udevadm settle
            _breakpoint dmroot_lvcreate
            lvcreate --noudevsync --contiguous y --zero y --name root --size $(( ${ext3_size} / 2 ))K ${rootcow_vg}
            udevadm settle
            [ -e "${rootcow}" ] || { echo "${rootcow}: No such file or device." >&2 ; exit 1 ; }
            [ -b "${rootcow}" ] || { echo "${rootcow}: No a block device."      >&2 ; exit 1 ; }
            _breakpoint dmroot_dmsetup
            dmsetup create root --table "0 ${ext3_size} snapshot ${ext3_loop} ${rootcow} p 512"
            ;;
        *)
            echo "${rootcow}: Assuming a regular block device." 
            _breakpoint dmroot_dmsetup
            dmsetup create root --table "0 ${ext3_size} snapshot ${ext3_loop} ${rootcow} p 512"
    esac
    udevadm settle

    export rootdev="/dev/mapper/root"
    export rootfstype=ext3
    export rootflags=${rootflags:-rw,barrier=1,errors=panic}

    _breakpoint dmroot_mount

    _fsck  -t "${rootfstype}" -a                "${rootdev}"
    _mount -t "${rootfstype}" -o "${rootflags}" "${rootdev}" /sysroot

    # change fs root to unshared. at default fs root is 'shared' mount and it is 
    # not allowed to move /.volumes/bootfs to /sysroot
    _mount --make-private /
    _mount -M "${bootfs_mntp}"                               /sysroot/.volumes/bootfs
    export root_already_mounted=true
    # set this variable so that boot.rootfsck
    # does not try to execute fsck on rootfs
    export ROOTFS_FSCK=0

    _breakpoint dmroot_end
}

if [ -n "${rootimg}" ] ; then
    trap _err_handler ERR 
    set -E
    _dmroot ${rootimg} ${rootcow}
    set +E
    trap - ERR
fi

# END
