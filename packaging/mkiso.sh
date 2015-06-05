#!/bin/bash -e

# Copyright 2015 EMC Corporation
# All Rights Reserved
#
# Copyright (c) 2014 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# packaging/mkiso.sh
# 
# Helper script used in packaging/Makefile
#

if [[ ${0} == /* ]] ; then
    fullpath=${0%/*};
else
    fullpath=$(/bin/pwd)"/"${0%/*};
fi

. ${fullpath}/.functions

_set_traps -Ex
_usage() {
    echo "Usage: $0 <baremetal_iso> <rootimg>" >&2
    exit 2
}
[ ${#} = 2      ] || _usage
[ -e "${1}"     ] && _fatal "${1}: File exists"
[ -f "${2}"     ] || _fatal "${2}: No such file"

iso_image="${1}"
rootimg=${2}
tmpdir="${iso_image%/*}"
isofs_file="${tmpdir}/isofs"
isofs_mntp="${tmpdir}/isofs.d"

xorriso=${fullpath}"/mkisofs_wrapper.sh"

dd if=/dev/zero of=${isofs_file} bs=1M count=768
isofs_dev=$(losetup --show -f ${isofs_file})
_set_on_exit "_unloop \"${isofs_dev}\""
#udevadm settle

mkfs -t ext3 ${isofs_dev}
_mount -t ext3 ${isofs_dev} ${isofs_mntp}

mkdir -p ${isofs_mntp}/boot/grub
cp ${fullpath}/mkiso.d/grub.cfg ${isofs_mntp}/boot/grub/grub.cfg
cp ${fullpath}/mkbootfs.d/grub.cfg ${isofs_mntp}/boot/grub/grub.cfg.installed

systool=${fullpath}"/../etc/systool --bootfs-dev=${isofs_dev} --bootfs-mntp=${isofs_mntp} --DO_NOT_INCLUDE=yes"
${systool} --install "${rootimg}"
default=$(${systool} --list)
${systool} --set-default "${default}"

mount -n -o remount,rw,noatime,barrier=1 ${isofs_dev} ${isofs_mntp}

# use customized mkisofs wrapper script as xorriso
#grub2-mkrescue -o ${iso_image} ${isofs_mntp}
grub2-mkrescue --xorriso=${xorriso} -o ${iso_image} ${isofs_mntp}

_umount "${isofs_dev}"
_unloop "${isofs_dev}"

