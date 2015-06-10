#!/bin/bash -e
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
# packaging/mkisofs_wrapper.sh
# 
# This script is used to replace default xorriso (which has license issue) 
# used by grub2-mkresuce during building native cdrom installation image.

mkisofs_parameters=""

while true ; do  
    case "$1" in 
        -as) echo "Option as, argument \`$2'" ; shift 2 ;;
        -graft-points) echo "Option graft-points" ; 
            mkisofs_parameters="${mkisofs_parameters} -graft-points"; shift ;;  
        --modification-date=*) echo "Option modification-date" ; shift ;;
        -b) echo "Option b, argument \`$2'" ; eltorito_img=$2; shift 2;;
        -no-emul-boot) echo "Option no-emul-boot" ; 
            mkisofs_parameters="${mkisofs_parameters} -no-emul-boot"; shift ;;  
        -boot-info-table) echo "Option boot-info-table" ; 
            mkisofs_parameters="${mkisofs_parameters} -boot-info-table"; shift ;;  
        --embedded-boot) embedded_boot=$2; shift 2;;
        --efi-boot) efi_boot=$2; shift 2;;
        --protective-msdos-label) echo "Option protective-msdos-label" ; shift ;;
        -o) iso_image=$2; shift 2;; 
        -r) tmp_img_dir=$2; shift 2;; 
	--sort-weight)
            echo "Option sort-weight" ; shift 3;;  
        *) 
            [ -d "${1}" ] || _fatal "${2}: No such dir";
            isofs_mntp=${1}; echo "isofs mountpoint:${isofs_mntp}"; shift; break;
    esac  
done  

cp -dpR ${tmp_img_dir}/boot/grub/i386-pc ${isofs_mntp}/boot/grub

genisoimage -R -b boot/grub/i386-pc/eltorito.img ${mkisofs_parameters} -o ${iso_image} ${isofs_mntp}

