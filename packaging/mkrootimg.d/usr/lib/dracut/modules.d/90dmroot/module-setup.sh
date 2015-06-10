#!/bin/bash

## Dracut module installation descriptor. Executed at build time.

# called by dracut
# exit with0 indicating this module should be included by default
check() {
    return 0
}

# called by dracut
# echo all dracut module names that we depend on
depends() {
    echo dm rootfs-block
    return 0
}

# called by dracut
# install kernel modules that we depend on 
installkernel() {
    instmods squashfs loop iso9660 jbd ext3
}

# called by dracut
# install everything else non-kernel related into initramfs
install() {
    # binaries required by dmroot.sh
    inst_multiple umount dmsetup lvscan lvcreate dd losetup grep blockdev vgscan vgchange \
             lvm lvs more date dumpe2fs fsck fsck.ext3 e2fsck lvremove
    
    inst_hook cmdline 90 "$moddir/parse-dmroot.sh"
    inst_hook mount 99 "$moddir/dmroot.sh"
    inst_script "$moddir/dmroot-lib.sh" "/lib/dmroot-lib.sh"
    dracut_need_initqueue
}
