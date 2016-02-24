monitors="%s";
user="%s";
key="%s";
pool="%s";
vol="%s";
snap="%s";
log="/tmp/coprhd.$pool.$vol.$snap.log";

if [[ "$pool" == "-" ]]; then
  vol_path="/dev/rbd/$pool/$vol"; # pool/vol
else
  vol_path="/dev/rbd/$pool/$vol@$snap"; # pool/vol@snap
fi;

echo "Map device: $vol_path" > "$log";

# exit if already mounted
if [ -f "$vol_path" ]; then
  echo Error: Device already mounted >> "$log";
  cat $log >&2
  exit -1;
fi;

# current state
volumes=( $(ls /sys/bus/rbd/devices/ | sort) );
size=${#volumes[@]};
echo "Current devices list: $volumes: (count=$size)" >> "$log";

# do mapping
echo "Do mount...." >> "$log";
echo "$monitors name=$user,secret=$key $pool $vol $snap" 1>"/sys/bus/rbd/add" 2>>"$log" || (cat $log >&2 && exit -1);
echo "Do mount.... Done" >> "$log";

# detect volume id (number)
for i in {0..1}; do
  echo Detect id try $i >> "$log";
  v=$(readlink "$vol_path");
  if [ ! -z "$v" ]; then break; fi;
  sleep 1;
done;
id=$(echo "$v" | grep -o '[0-9]\\+');
if [ -z "$id" ]; then
  # old driver version
  echo Fallback to old driver >> "$log";
  new_volumes=( $(ls /sys/bus/rbd/devices/ | sort) );
  new_size=${#new_volumes[@]};
  echo "New devices list: $new_volumes: (count=$new_size)" >> "$log";
  index=0;
  while [  $index -lt $new_size ]; do
    v1=${new_volumes[$index]};
    v2=${volumes[$index]};
    echo "Check index $index: v1=$v2, v2=$v2" >> "$log";
    if [[ $v1 != $v2 ]]; then
      p=$(cat /sys/bus/rbd/devices/$v1/pool);
      n=$(cat /sys/bus/rbd/devices/$v1/name);
      s=$(cat /sys/bus/rbd/devices/$v1/current_snap);
      echo "Check device: p=$p, n=$n s=$s" >> "$log";
      if [[ "$p" == "$pool" && "$n" == "$vol" && "$s" == "$snap" ]]; then
        id=$v1;
        break;
      fi;
    fi;
    let index=$index+1;
  done;
fi;

if [ -z "$id" ]; then
  echo Error: Device not found >> "$log";
  cat $log >&2
  exit -1;
fi;

# if ceph common installed (rbdmap service scripts) add info for persistent mapping (on reboot)
RBDMAP_FILE="/etc/ceph/rbdmap";
if [ -f "$RBDMAP_FILE" ]; then
  echo "Add device to $RBDMAP_FILE to enable automap on reboot" >> "$log";
  keyfile="/etc/ceph/$pool.$vol.$snap.keyfile";
  echo "Create keyfile $keyfile" >> "$log";
  echo "$key" > "$keyfile";
  yes | cp -f "$RBDMAP_FILE" "$RBDMAP_FILE.org";
  yes | cp -f "$RBDMAP_FILE" "$RBDMAP_FILE.new";
  echo "$pool/$vol@$snap        id=$user,keyfile=$keyfile" >> "$RBDMAP_FILE.new";
  mv -f "$RBDMAP_FILE.new" "$RBDMAP_FILE";
  rm -f "$RBDMAP_FILE.org";
fi;

# echo number of new volume
echo "Device mapped with number $id" >> "$log";
echo "$id";
