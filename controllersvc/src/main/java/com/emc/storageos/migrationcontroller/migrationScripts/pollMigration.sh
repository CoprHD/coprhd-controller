#!/bin/bash
ostype=`uname`
    
for arg in "$@"; do
    [ "$arg" != "${arg#pid=}" ] && ddpid="${arg#pid=}"
    [ "$arg" != "${arg#name=}" ] && tempfile="${arg#name=}"
    [ "$arg" != "${arg#if=}" ] && infile="${arg#if=}"
done
    
    
if [ -f "$infile" ]; then
    totalbytes=`du -D -k "$infile" | tr ' ' '\t' | cut -f 1`
    totalbytes=`expr $totalbytes \* 1024` # busybox's "du" mode doesn't support -b
else
# we don't know the transfer size, so can't calculate percent
        totalbytes=`blockdev --getsize64 "$infile" | tr ' ' '\t'`
fi
    
dd_is_running() {
    # this COULD potentially fail if the same PID is assigned to another dd process inside
    # one loop iteration. If that ever happens... :D
if [ "$ostype" = "Linux" ] && mount | grep /proc >/dev/null; then
    if [ "$ddpid" != "" ] && \
        [ -f "/tmp/coprhdMigration/$tempfile" ] && \
        [ -d /proc/${ddpid} ] && \
        cat /proc/${ddpid}/cmdline 2>/dev/null | grep "dd" >/dev/null; then
        return 0
    else
        return 1
    fi
else # fallback to using ps
    if [ "$ddpid" != "" ] && \
        [ -f "/tmp/coprhdMigration/$tempfile" ] && \
        ps xau | grep " $ddpid " | grep "dd" >/dev/null; then
        return 0
    else
        return 1
    fi
fi
}
    
print_once() {
    read nbytes
    #percent=$[nbytes*100/totalbytes]
    percent=`expr $nbytes \* 100 / $totalbytes`
    percent=`echo -n " $percent" | tail -c 3`
    echo -e -n "\\033[7D[$percent%] "  # >&2
        echo -e -n 
}
    
    
poll_dd() {
    #echo "poll_dd: start"
        dd_is_running && {
    kill -USR1 $ddpid 2>/dev/null
    }
    #echo "poll_dd: end"
}
    
    
poll_dd
sleep 0.2   
[ -f "/tmp/coprhdMigration/$tempfile" ] && {
     dd_is_running && {
        bytetext=$(tail -n 1 "/tmp/coprhdMigration/$tempfile" )
        # echo "bytetext=" $bytetext
        echo "$bytetext" | grep "bytes" 2>&1 >/dev/null && {
        bytetext=`echo $bytetext | cut -d ' ' -f 1`
        echo $bytetext | print_once
        }
        exit
     }

        echo -e -n "\\033[7D[100%] "  # >&2
        exit
}
