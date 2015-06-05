#!/bin/sh -ex

cc -o m.so -DDEBUG=0 -shared -fPIC monolithicSparse.c -ldl 
cc -o mtest mtest.c

[ -f input ] || bzip2 -dc <input.bz2 >input
LD_PRELOAD=$PWD/m.so ./mtest <input >output
cmp input output || hexdump -C output | more
du -sk input output

