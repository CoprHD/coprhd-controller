#!/bin/bash
#
# Copyright 2016 EMC Corporation
# All Rights Reserved
#

function installDockerEnv
{
  workspace=$2
  node_count=$3
  [ ! -z "${workspace}" ] || workspace="${PWD}"
  [ ! -z "${node_count}" ] || node_count=1

  cat > ${workspace}/docker-env.conf <<EOF
# docker-env
#
# Docker Configuration

description "ADG Pre Docker configuration"

start on (started networking)

script
  exec /opt/ADG/conf/configure.sh enableStorageOS
end script
EOF

  for i in $(seq 1 ${node_count}); do
    mkdir -p ${workspace}/data.$i
    chmod 777 ${workspace}/data.$i
  done

  network_vip=(172.17.0.100)
  network_vip6=(2001:0db8:0001:0000:0000:0242:ac11:0064)
  vip=${network_vip[0]}
  vip6=${network_vip6[0]}
  for i in $(seq 1 ${node_count}); do
    echo "Starting vipr$i..."
    docker stop vipr$i &> /dev/null
    docker rm vipr$i &> /dev/null
    docker run --privileged -d -e "HOSTNAME=vipr$i" -v ${workspace}/data.$i:/data -v ${workspace}/docker-env.conf:/etc/init/docker-env.conf --name=vipr$i coprhd /sbin/init
    docker exec vipr$i /bin/bash -c "sed /$(docker inspect -f {{.Config.Hostname}} vipr$i)/d /etc/hosts > /etc/hosts.new"
    docker exec vipr$i /bin/bash -c "cat /etc/hosts.new > /etc/hosts"
    docker exec vipr$i /bin/bash -c "rm /etc/hosts.new"
    docker exec vipr$i /bin/bash -c "echo \"vipr$i\" > /etc/HOSTNAME"
    docker exec vipr$i /bin/bash -c "echo \"${network_vip[0]}	coordinator\" >> /etc/hosts"
    docker exec vipr$i /bin/bash -c "echo \"${network_vip[0]}	coordinator.bridge\" >> /etc/hosts"
    docker exec vipr$i hostname vipr$i
    network_vip+=($(docker inspect -f {{.NetworkSettings.IPAddress}} vipr$i))
    network_vip6+=($(docker inspect -f {{.NetworkSettings.GlobalIPv6Address}} vipr$i))
  done
  if [ ${node_count} -eq 1 ]; then
    vip=${network_vip[1]}
    vip6=${network_vip6[1]}    
  fi

  for i in $(seq 1 ${node_count}); do
    echo "#!/bin/bash" > ${workspace}/data.$i/dockerenv.sh
    echo "network_prefix_length=$(docker inspect -f {{.NetworkSettings.IPPrefixLen}} vipr$i)" >> ${workspace}/data.$i/dockerenv.sh
    echo "network_prefix_length6=$(docker inspect -f {{.NetworkSettings.GlobalIPv6PrefixLen}} vipr$i)" >> ${workspace}/data.$i/dockerenv.sh
    for j in $(seq 1 ${node_count}); do
      echo "network_${j}_ipaddr=${network_vip[$j]}" >> ${workspace}/data.$i/dockerenv.sh
      echo "network_${j}_ipaddr6=${network_vip6[$j]}" >> ${workspace}/data.$i/dockerenv.sh
    done
    echo "network_gateway=$(docker inspect -f {{.NetworkSettings.Gateway}} vipr$i)" >> ${workspace}/data.$i/dockerenv.sh
    echo "network_gateway6=$(docker inspect -f {{.NetworkSettings.IPv6Gateway}} vipr$i)" >> ${workspace}/data.$i/dockerenv.sh
    echo "sed -i s/network_netmask=.*/network_netmask=255.255.0.0/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    echo "sed -i s/node_count=.*/node_count=$node_count/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    echo "sed -i s/node_id=.*/node_id=vipr$i/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    echo "sed -i s/network_1_ipaddr=.*/network_1_ipaddr=\${network_1_ipaddr}/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    echo "sed -i s/network_gateway=.*/network_gateway=\${network_gateway}/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    # echo "sed -i s/network_1_ipaddr6=.*/network_1_ipaddr6=\${network_1_ipaddr6}/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    # echo "sed -i s/network_gateway6=.*/network_gateway6=\${network_gateway6}/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    echo "sed -i s/network_prefix_length=.*/network_prefix_length=\${network_prefix_length}/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    echo "sed -i s/network_vip=.*/network_vip=${vip}/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    # echo "sed -i s/network_vip6=.*/network_vip6=${vip6}/g /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    for j in $(seq 2 ${node_count}); do
      echo "echo \"network_${j}_ipaddr=\${network_${j}_ipaddr}\" >> /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
      # echo "echo \"network_${j}_ipaddr6=\${network_${j}_ipaddr6}\" >> /etc/ovfenv.properties" >> ${workspace}/data.$i/dockerenv.sh
    done
    echo "exit 0" >> ${workspace}/data.$i/dockerenv.sh
    chmod a+x ${workspace}/data.$i/dockerenv.sh
    docker exec vipr$i chown -R storageos:storageos /data
    docker exec vipr$i /opt/ADG/conf/configure.sh installNetworkConfigurationFile
    docker exec vipr$i /data/dockerenv.sh
  done
  iptables -t nat -A DOCKER -p tcp --dport 443 -j DNAT --to-destination ${vip}:443
  iptables -t nat -A DOCKER -p tcp --dport 4443 -j DNAT --to-destination ${vip}:4443
  iptables -t nat -A DOCKER -p tcp --dport 8080 -j DNAT --to-destination ${vip}:8080
  iptables -t nat -A DOCKER -p tcp --dport 8443 -j DNAT --to-destination ${vip}:8443
}

function uninstallDockerEnv
{
  workspace=$2
  node_count=$3
  [ ! -z "${workspace}" ] || workspace="${PWD}"
  [ ! -z "${node_count}" ] || node_count=1
  for i in $(seq 1 ${node_count}); do
    echo "Stopping vipr$i..."
    docker stop vipr$i &> /dev/null
    docker rm vipr$i &> /dev/null
    rm -fr ${workspace}/data.$i
  done
  rm -fr ${workspace}/docker-env.service
  iptables -F DOCKER -t nat
}

function waitStorageOS
{
  docker exec vipr1 /opt/ADG/conf/configure.sh waitStorageOS
}

$1 "$@"
