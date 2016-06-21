# ===
# FILE: /kiwi-hooks/preCallInit.sh
# ===

# include KIWI modules
/include

# clenup /run/initramfs created by 'KIWILinuxRC.sh'
rm -fr /run/initramfs

# turn off on screen console messages after first boot
dmesg --console-off

# cleanup kiwi-hooks folder
rm -fr /kiwi-hooks
