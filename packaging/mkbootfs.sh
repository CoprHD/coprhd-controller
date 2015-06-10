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
# packaging/mkbootfs.sh
# 
# Helper script used in packaging/Makefile
#

. ./.functions

_set_traps -Ex

_usage() {
    echo "Usage: $0 <bootfs_vmx> <root_img>" >&2
    exit 2
}

[ ${#} = 2      ] || _usage
[ -d "${1%/*}"  ] || _fatal "${1}: No such directory"
[ -e "${1}"     ] && _fatal "${1}: File exists"
[ -f "${2}"     ] || _fatal "${2}: No such file"

bootfs_vmx=${1}
rootimg=${2}

tmpdir=${1%/*}
bootfs_vmdk_bz2='storageos-disk1-flat.vmdk.bz2'
bootfs_vmdk_flat="${tmpdir}/bootfs-flat-disk1-flat.vmdk"
bootfs_vmdk="${tmpdir}/bootfs-flat-disk1.vmdk"
bootfs_vmx="${tmpdir}/bootfs-flat.vmx"
bootfs_mntp="${bootfs_vmdk_flat}.d"

# Make bootfs-flat-disk1-flat.vmdk
rm -f "${bootfs_vmdk_flat}"
bzip2 -dc <"${bootfs_vmdk_bz2}" | dd bs=4096 | cp --sparse=always /dev/stdin "${bootfs_vmdk_flat}"
chmod 644 ${bootfs_vmdk_flat}
bootfs_dev=$(_loop -o 1048576 "${bootfs_vmdk_flat}")
fsck.ext3 -a ${bootfs_dev} # Because the filesystem on the disk is old, we check it now to make it less likely that it needs checking and display warning message at first bootup time
_set_on_exit "_unloop \"${bootfs_dev}\""
_mount -t ext3 -o data=journal "${bootfs_dev}" "${bootfs_mntp}"
cp mkbootfs.d/grub.cfg "${bootfs_mntp}/boot/grub/grub.cfg"
systool="../etc/emc/systool --bootfs-dev=${bootfs_dev} --bootfs-mntp=${bootfs_mntp} --DO_NOT_INCLUDE=yes"
${systool} --install "${rootimg}"
default=$(${systool} --list)
${systool} --set-default "${default}"
_umount "${bootfs_dev}"
_unloop "${bootfs_dev}"

# Make bootfs-flat-disk1.vmdk
rm -f "${bootfs_vmdk}"
cat  >"${bootfs_vmdk}" <<EOF
# Disk DescriptorFile
version=1
encoding="UTF-8"
CID=ade02b46
parentCID=ffffffff
isNativeSnapshot="no"
createType="monolithicFlat"

# Extent description
RW 8388608 FLAT "${bootfs_vmdk_flat##*/}" 0

# The Disk Data Base 
#DDB

ddb.virtualHWVersion = "7"
ddb.deletable = "true"
ddb.longContentID = "7d7a3917e713f239f60a1cdfade02b46"
ddb.uuid = "60 00 C2 95 4d 50 ca 1f-96 32 2e cd 98 71 6d 94"
ddb.geometry.cylinders = "522"
ddb.geometry.heads = "255"
ddb.geometry.sectors = "63"
ddb.adapterType = "lsilogic"
EOF

# Make bootfs-flat.vmx
rm -f "${bootfs_vmx}"
cat  >"${bootfs_vmx}" <<EOF
.encoding = "UTF-8"
displayname = "bootfs-flat"
annotation = "Bourne DevKit"
guestos = "sles11-64"
virtualhw.version = "7"
config.version = "8"
numvcpus = "2"
cpuid.coresPerSocket = "1"
memsize = "4096"
svga.autodetect = "TRUE"
pciBridge0.present = "TRUE"
mks.enable3d = "TRUE"
pciBridge4.present = "TRUE"
pciBridge4.virtualDev = "pcieRootPort"
pciBridge4.functions = "8"
pciBridge5.present = "TRUE"
pciBridge5.virtualDev = "pcieRootPort"
pciBridge5.functions = "8"
pciBridge6.present = "TRUE"
pciBridge6.virtualDev = "pcieRootPort"
pciBridge6.functions = "8"
pciBridge7.present = "TRUE"
pciBridge7.virtualDev = "pcieRootPort"
pciBridge7.functions = "8"
vmci0.present = "TRUE"
floppy0.present = "FALSE"
ide0:0.present = "TRUE"
ide0:0.deviceType = "atapi-cdrom"
ide0:0.autodetect = "TRUE"
ide0:0.startConnected = "FALSE"
scsi0:0.present = "TRUE"
scsi0:0.deviceType = "disk"
scsi0:0.fileName = "bootfs-flat-disk1.vmdk"
scsi0.virtualDev = "lsilogic"
scsi0.present = "TRUE"
ethernet0.present = "TRUE"
ethernet0.virtualDev = "vmxnet3"
ethernet0.connectionType = "bridged"
ethernet0.startConnected = "TRUE"
ethernet0.addressType = "generated"
EOF


