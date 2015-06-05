#!/bin/bash -e

# Copyright 2015 EMC Corporation
# All Rights Reserved
#
# Copyright (c) 2012-2015 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# packaging/mkrootimg.sh
# 
# Helper script used in packaging/Makefile
#

. ./.functions

_set_traps -Ex

_usage() {
    echo "Usage: $0 <rootimg> <base_disk_flat_vmdk> <rpm_file> [rpm_file2] ..." >&2
    exit 2
}

# These names must match etc/systool
bootfs_title="label"
bootfs_menuentry="boot"
bootfs_rootimg="rootimg"
ext3_name="rootfs"

# Main
[ ${#} -ge 4    ] || _usage
[ -e "${1}"     ] && _fatal "${1}: File exists"
[ -d "${1%/*}"  ] || _fatal "${1}: No such directory"
[ -f "${2}"     ] || _fatal "${2}: No such file"
for f in "${@:4}" ; do
    [ -f "${f}" ] || _fatal "${f}: No such file"
done

sqfs_file="${1}"
disk_file="${2}"
upgrade_metadata_file="${3}"
rpm_files="${@:4}"

tmpdir="${sqfs_file%/*}"
title_file="${tmpdir}/${bootfs_title}"
menuentry_file="${tmpdir}/${bootfs_menuentry}"
menuentry_append="splash=silent quiet"
ext3_file="${tmpdir}/${ext3_name}"
ext3_mntp="${tmpdir}/${ext3_name}.d"
tmp_metadata_file="${tmpdir}/vipr.md"

# Extract the base ext3 file system
# - the partition starts at the offset of 1 MB and contains 10473472 MB of data
rm -f "${ext3_file}"
dd if="${disk_file}" ibs=1024k skip=1 obs=4k | cp --sparse=always /dev/stdin "${ext3_file}"
chmod 644 ${ext3_file}

# e2fsck and e2tunefs the extracted rootfs
_set_on_exit "_unloop ${loop}"
loop=$(_loop "${ext3_file}")
e2fsck -f -p "${loop}" || { code=$? ; [ ${code} = 1 ] || _fatal "e2fsck returned ${code}" ; }
# the arguments -c 0 and -i 0 disable fsck based on time interval and mount count
tune2fs -L "/" -U "3c426f75-726e-6520-2f73-797374656d3e" -c 0 -i 0 "${loop}"
_unloop "${loop}"

# Make rootfs
# - mount
# - patch /etc/fstab
# - install rpm
# - save the /opt/storageos/etc/product title
#
_mount -t ext3 -o loop,data=journal "${ext3_file}" "${ext3_mntp}"
_mount --bind                       /dev           "${ext3_mntp}/dev"
mkdir -p                                           "${ext3_mntp}/usr/lib/dracut/modules.d/90dmroot"
_xcopy mkrootimg.manifest mkrootimg.d              "${ext3_mntp}"
                                            chroot "${ext3_mntp}" /sbin/mkinitrd_setup                    
rootdev=/dev/sda1 rootfstype=ext3           chroot "${ext3_mntp}" /sbin/mkinitrd -v -B -f dmroot
DO_NOT_START="yes" rpm -iv --root                  "${ext3_mntp}" ${rpm_files} 
title=$(<"${ext3_mntp}/opt/storageos/etc/product")
_umount "${ext3_mntp}/dev"
_umount "${ext3_mntp}"

# Make "label" file  
rm -f "${title_file}"
cat  >"${title_file}"<<EOF
${title}
EOF
chmod 644 "${title_file}"

# Make "menuentry" files
# Prepare the 2nd grub.cfg for the vipr running env (boot from disk)
rm -f "${menuentry_file}"
cat  >"${menuentry_file}" <<EOF

set default=0
set gfxpayload=keep

menuentry "ViPR(${title}) Controller" {
        loopback sqfs /\${rootimg}
        loopback ext3 (sqfs)/${ext3_name}
        set root=(ext3)
        linux  /boot/vmlinuz root=dmroot:\${bootfs_dev} bootfs_dev=\${bootfs_dev} rootimg=\${rootimg} fips=1 product=${title} dev_mode=${devmode} ${menuentry_append} modprobe.blacklist=hyperv_fb,vmwgfx rd.driver.blacklist=floppy
        initrd /boot/initrd
}

menuentry "Configuration of a single ViPR(${title}) Controller node" {
        loopback sqfs /\${rootimg}
        loopback ext3 (sqfs)/${ext3_name}
        set root=(ext3)
        linux  /boot/vmlinuz root=dmroot:\${bootfs_dev} bootfs_dev=\${bootfs_dev} rootimg=\${rootimg} fips=1 boot_mode=config dev_mode=${devmode} product=${title} ${menuentry_append} modprobe.blacklist=hyperv_fb,vmwgfx rd.driver.blacklist=floppy
        initrd /boot/initrd
}

menuentry "Redeploy of a single ViPR(${title}) Controller node" {
        loopback sqfs /\${rootimg}
        loopback ext3 (sqfs)/${ext3_name}
        set root=(ext3)
        linux  /boot/vmlinuz root=dmroot:\${bootfs_dev} bootfs_dev=\${bootfs_dev} rootimg=\${rootimg} fips=1 boot_mode=redeploy dev_mode=${devmode} product=${title} ${menuentry_append} modprobe.blacklist=hyperv_fb,vmwgfx rd.driver.blacklist=floppy
        initrd /boot/initrd
}

menuentry "Rollback to the latest network configuration of a single ViPR(${title}) Controller" {
        loopback sqfs /\${rootimg}
        loopback ext3 (sqfs)/${ext3_name}
        set root=(ext3)
        linux  /boot/vmlinuz root=dmroot:\${bootfs_dev} bootfs_dev=\${bootfs_dev} rootimg=\${rootimg} fips=0 ipreconfig_mode=rollback product=${title} dev_mode=${devmode} ${menuentry_append} modprobe.blacklist=hyperv_fb,vmwgfx rd.driver.blacklist=floppy
        initrd /boot/initrd
}

menuentry "ViPR(${title}) Controller (rescue)" {
        loopback sqfs /\${rootimg}
        loopback ext3 (sqfs)/${ext3_name}
        set root=(ext3)
        linux  /boot/vmlinuz root=dmroot:\${bootfs_dev} bootfs_dev=\${bootfs_dev} rootimg=\${rootimg} rootcow=initramfs product=${title} ${menuentry_append} modprobe.blacklist=hyperv_fb,vmwgfx rd.driver.blacklist=floppy
        initrd /boot/initrd
}

EOF

# Prepare the 2nd grub.cfg for installation iso image 
rm -f "${menuentry_file}.install"
cat  >"${menuentry_file}.install" <<EOF

set default=0
#set gfxpayload=keep

menuentry "Installation of a new ViPR(${title}) Controller" {
        loopback sqfs /\${rootimg}
        loopback ext3 (sqfs)/${ext3_name}
        set root=(ext3)
        linux  /boot/vmlinuz root=dmroot:\${bootfs_dev} bootfs_dev=\${bootfs_dev} rootimg=\${rootimg} rootcow=initramfs boot_mode=install dev_mode=${devmode} product=${title} ${menuentry_append}
        initrd /boot/initrd
}

menuentry "Re-deployment of a single ViPR(${title}) Controller node" {
        loopback sqfs /\${rootimg}
        loopback ext3 (sqfs)/${ext3_name}
        set root=(ext3)
        linux  /boot/vmlinuz root=dmroot:\${bootfs_dev}:initramfs bootfs_dev=\${bootfs_dev} rootimg=\${rootimg} rootcow=initramfs boot_mode=redeploy dev_mode=${devmode} product=${title} ${menuentry_append}
        initrd /boot/initrd
}

EOF

# Package rootfs, label and menuentry into rootimg.temp
rm -f                                                              "${sqfs_file}".temp
cp "${upgrade_metadata_file}" "${tmp_metadata_file}"
truncate --size=20480 "${tmp_metadata_file}"  # Pad the file, make it 2kB large
chmod 400       "${title_file}" "${menuentry_file}" "${ext3_file}" "${tmp_metadata_file}"
mksquashfs      "${title_file}" "${menuentry_file}" "${menuentry_file}.install" "${ext3_file}" "${sqfs_file}".temp
cat "${tmp_metadata_file}" >> "${sqfs_file}".temp # Append the vipr metadata to the squashfs file
./gentrailer.py                                                    "${sqfs_file}".temp
chmod 666                                                          "${sqfs_file}".temp

# Rename rootimg.temp to rootimg
rm -f                                                                                   "${sqfs_file}"
rm -f                                                                                   "${tmp_metadata_file}"
mv                                                                 "${sqfs_file}".temp "${sqfs_file}"

# END
