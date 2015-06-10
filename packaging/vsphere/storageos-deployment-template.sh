#!/bin/bash
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

_usage() {
    echo "Usage: $0 [-help] [-mode install | redeploy] [options]
           -mode:
              install            install a new cluster
              redeploy           redeploy a VM in a cluster

           Install mode options:
               -vip              Public virtual IPv4 address
               -ipaddr_1         IPv4 address of node 1
               -ipaddr_2         IPv4 address of node 2
               -ipaddr_3         IPv4 address of node 3
               -ipaddr_4         IPv4 address of node 4
               -ipaddr_5         IPv4 address of node 5
               -gateway          IPv4 default gateway
               -netmask          Network netmask

               -vip6             Public virtual IPv6 address
               -ipaddr6_1        IPv6 address of node 1
               -ipaddr6_2        IPv6 address of node 2
               -ipaddr6_3        IPv6 address of node 3
               -ipaddr6_4        IPv6 address of node 4
               -ipaddr6_5        IPv6 address of node 5
               -gateway6         IPv6 default gateway
               -ipv6prefixlength IPv6 network prefix length

               -nodeid           Specific node to be deployed, please input a number (start from 1)
               -nodecount        Node counts of the cluster (valid value is 1 (evaluation only), 3 or 5)
               -targeturi        Target locator

               -ds               Data store name
               -net              Network name
               -vmprefix         (Optional) prefix of virtual machine name
               -vmname           (Optional) virtual machine name
               -vmfolder         (Optional) target virtual machine folder
               -dm               (Optional) disk format:thin, lazyzeroedthick, zeroedthick (default)
               -poweron          (Optional) auto power on the VM after deploy, (no power on by default)
               -username         (Optional) username of vSphere client
               -password         (Optional) password of vSphere client
               -interactive      (Optional) interactive way to deploy

           example: $0 -mode install -vip 1.2.3.0 -ipaddr_1 1.2.3.1 -ipaddr_2 1.2.3.2 -ipaddr_3 1.2.3.3 -gateway 1.1.1.1 -netmask 255.255.255.0 -nodeId 1 -nodecount 3 -targeturi vi://username:password@vsphere_host_url -ds datastore_name -net network_name -vmprefix vmprefix- -vmfolder vm_folder -dm zeroedthick -cpucount 2 -memory 8192 -poweron

           Redeployment mode options:
               -file             The setting file
               -nodeid           Specific node to be deployed, please input a number (start from 1)
               -targeturi        Target locator
               -ds               Data store name
               -net              Network name
               -vmprefix         (Optional) prefix of virtual machine name
               -vmname           (Optional) virtual machine name
               -vmfolder         (Optional) target virtual machine folder
               -dm               (Optional) disk format:thin, lazyzeroedthick, zeroedthick (default)
               -poweron          (Optional) auto power on the VM after deploy, (no power on by default)
               -interactive      (Optional) interactive way to redeploy

           example: $0 -mode redeploy -file your_setting_file_path -nodeId 1 -targeturi vi://username:password@vsphere_host_url -ds datastore_name -net network_name -vmprefix vmprefix- -vmfolder vm_folder -dm zeroedthick -cpucount 2 -memory 8192 -poweron"

    exit 2
}

_fatal() {
    echo -e "Error: $*" >&2
    exit 1
}

_generate_file_names() {
    disk4_flat_raw_vmdk="${tmpdir}/${vmname}-flat-disk1.vmdk"
    disk4_vmdk_descriptor="${tmpdir}/${vmname}-descriptor.vmdk"
    disk4_vmx="${tmpdir}/${vmname}.vmx"
    disk4_ovf="${tmpdir}/${vmname}.ovf"
    disk4_mf="${tmpdir}/${vmname}.mf"
    disk4_streamOptimized="${tmpdir}/${vmname}-disk1.vmdk"
    disk4_vmdk_filename="${vmname}-disk4.vmdk"
    vipr_ovf_file="${vmname}-controller.ovf"
    vipr_mf="${vmname}-controller.mf"
}

_cleanup() {
    rm -f ${disk4_flat_raw_vmdk} ${disk4_vmdk_descriptor} ${disk4_vmx} ${disk4_ovf} ${disk4_mf} ${disk4_streamOptimized}
}

_set_traps() {
    trap _cleanup EXIT
    trap _cleanup ERR
}

OVFTOOL_DOWNLOAD_URI="https://my.vmware.com/web/vmware/details?productId=352&downloadGroup=OVFTOOL350"
PREREQUISTES_COMMANDS="base64 ovftool cat rm mkdir mv sha1sum"

# Check all required commands
_check_prerequistes() {
    for cmd in ${PREREQUISTES_COMMANDS} ; do
        which ${cmd} >/dev/null
        if [ $? -ne 0 ] ; then
            errMsg="The command ${cmd} is missing"
            if [ "${cmd}" == "ovftool" ] ; then
                errMsg+="\nPlease download from ${OVFTOOL_DOWNLOAD_URI}\n"
            fi
            _fatal "${errMsg}"
        fi
    done
}

_decode_base64_data() {
    echo "$1" |  base64 -d -i
}

_decode_flat_vmdk_header() {
    _decode_base64_data "${include="iso-header"}"
}

_decode_flat_vmdk_trailer() {
    _decode_base64_data "${include="iso-trailer"}"
}

_pad_ovfenv_properties() {

    local properties=$(echo -n -e "$1")
    let "padding = ${config_file_size} - ${#properties}"
    properties+=$(printf "%${padding}s" ' ')

    echo -e -n "${properties}"
}

# Generate the raw VMDK of a monolithicFlat vmdk
# The raw VMDK is an ISO file: iso_header + ovf-env.properties + iso_trailer
# where the iso_header and iso_trailer is decoded from ${encoded_iso_header} and
# ${encoded_iso_tal} (based64 encoded and set at the build time) respectively
# $1=ovf properties
_generate_flat_vmdk () {
    _decode_flat_vmdk_header
    _pad_ovfenv_properties "$1"
    _decode_flat_vmdk_trailer
}

_generate_common_ovf_properties() {
    common_env_properties=""
    for (( i=1; i<=${node_count}; i++ )) ; do
        common_env_properties+="network_${i}_ipaddr=${ipv4_addresses[${i}]}\n"
        common_env_properties+="network_${i}_ipaddr6=${ipv6_addresses[${i}]}\n"
    done
    common_env_properties+="
network_gateway=${gateway}
network_netmask=${netmask}
network_vip=${vip}

network_gateway6=${gateway6}
network_prefix_length=${ipv6_prefix_length}
network_vip6=${vip6}

node_count=${node_count}"
}

_generate_flat_vmdk_descriptor() {
    echo -e "${include="vmdk-descriptor-template"}"
}

_generate_vmx_descriptor() {
    echo -e "${include="vmx-template"}"
}

# $1=node id
_generate_disk4() {
    env_properties="${common_env_properties}\nnode_id=${product_name}$1\nmode=${mode}"

    _generate_flat_vmdk "${env_properties}" > ${disk4_flat_raw_vmdk} || _fatal "Failed to generate the raw vmdk file"
    _generate_flat_vmdk_descriptor ${disk4_flat_raw_vmdk} > ${disk4_vmdk_descriptor} || _fatal "Failed to generate flat vmdk descriptor file"
    _generate_vmx_descriptor ${disk4_vmdk_descriptor} > ${disk4_vmx} || _fatal "Failed to generate vmx file"

    ovftool ${disk4_vmx} ${disk4_ovf}

    mv ${disk4_streamOptimized} ${disk4_vmdk_filename}
}

_generate_ovf_file() {
   cat > "${1}" <<EOF
${include="storageos-vsphere-template.xml"}
EOF
}

_generate_mf_file() {
    vipr_mf="$1"
    shift

    FILES=$*
    mf_contents="${data_disk_mf}"

    for file in ${FILES}
    do
        output=`sha1sum ${file}`
        sha1=$(echo ${output} | { read sha1 dummy ; echo ${sha1} ; })
        mf_line=" SHA1($file)= ${sha1}"
        mf_contents+="${mf_line}\n"
    done

    echo -e "${mf_contents}" > "${vipr_mf}"
}

# $1=node id
_generate_node_files() {
    cd ${vmdk_dir}
    rm -rf ${vmname}

    mkdir ${vmname} && cd ${vmname} || _fatal "Failed to cd to ${vmname}"

    echo "Generating files for ${vmname}"
    _generate_disk4 ${1}
    echo -e "Done\n"

    # Generate .ovf used for deployment
    _generate_ovf_file ${vipr_ovf_file} ${disk4_vmdk_filename}

    # Generate .mf file
    _generate_mf_file "${vipr_mf}" "${vipr_ovf_file}" "${disk4_vmdk_filename}"
}

_set_common_ovftool_options() {
    if [ "${poweron}" == "yes" ] ; then
        common_ovftool_options+="--powerOn "
    fi

    local ovftool_dm="eagerZeroedThick"
    case ${dm} in
        thin)
            ovftool_dm="thin"
            ;;
        lazyzeroedthick)
            ovftool_dm="thick"
            ;;
    esac

    common_ovftool_options+="-dm=$ovftool_dm "
}

# $1=node id
_deploy_vm() {
    cd "${vmdk_dir}/${vmname}"

    echo -e "\n****** Deploying ${vmname} ******\n"
    node_options="${common_ovftool_options}"
    node_options+=" --name=${vmname} "

    if [ "${net}" != "" ] ; then
        node_options+="--net:'ViPR Network'="
        node_options+='"${net}"'
        node_options+=" "
    fi

    if [ "${vm_folder}" != "" ] ; then
        node_options+="--vmFolder="
        node_options+='"${vm_folder}"'
        node_options+=" "
    fi

    if [ "${ds}" != "" ] ; then
        node_options+=-ds='"${ds}"'
    fi

    # generate final target uri
    if [ "${username}" != "" ] ; then
        if [ "${password}" != "" ] ; then
            target_uri="vi://${username}:${password}@${target_uri}"
        else
            target_uri="vi://${username}@${target_uri}"
        fi
    else
        target_uri="vi://${target_uri}"
    fi

    eval ovftool ${node_options} ${vipr_ovf_file} "'${target_uri}'"
    ret=$?
    if [ "${ret}" -eq 0 ] ; then
        if [ "${node_count}" -eq 1 ] ; then
            echo "Warning: The 1+0 cluster is used for evaluation variant with no production support" 
        fi
        echo "Done"
    fi

    return "${ret}"
}

# functions to check parameters
_check_yes_or_no() {
   if [ "$1" != "yes" -a "$1" != "no" ] ; then
        error_message="Please enter yes or no"
        return 1
   fi

   return 0
}

_nocheck() {
    return 0
}

_check_no_empty() {
   if [ "$1" == "" ] ; then
        error_message="Please enter a non empty value"
        return 1
   fi

   return 0
}

_check_ipv4_address() {
    if [[ "$1" == "0.0.0.0" ]] ; then
        error_message="The address 0.0.0.0 can't be used here"
        return 1
    fi

    if [[ "$1" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]] ; then
        return 0
    fi

    error_message="Invalid IPv4 address $1"
    return 1
}

_check_netmask() {
    netmask_reg='^(254|252|248|240|224|192|128)\.0\.0\.0|255\.(254|252|248|240|224|192|128|0)\.0\.0|255\.255\.(254|252|248|240|224|192|128|0)\.0|255\.255\.255\.(254|252|248|240|224|192|128|0)$'
    if [[ "$1" =~ $netmask_reg ]] ; then
        return 0
    fi

    error_message="Invalid netmask $1"
    return 1
}

_reset_ipv4_addresses() {
    vip="0.0.0.0"
    gateway="0.0.0.0"
    netmask="255.255.255.0"
    ipv4_addresses=("${default_ipv4_addresses[@]}")
}

_check_vip() {
    if [ "$1" = "0.0.0.0" ] ; then
       _reset_ipv4_addresses
       return 0
    fi

    _check_ipv4_address "$1"
}

_check_ipv6_address() {
    if [[ "$1" =~ ^::0$ ]] ; then
        error_message="The IPv6 address ::0 can't be used here"
        return 1
    fi

    if [[ "$1" =~ ^[0-9a-fA-F]{0,4}:[a-fA-F0-9]{0,4}:[a-fA-F0-9]{0,4}:[0-9a-fA-F]{0,4}:[a-fA-F0-9]{0,4}:[a-fA-F0-9]{1,4}$ ]] ; then
        return 0
    fi

    error_message="Invalid IPv6 address"
    return 1
}

_reset_ipv6_address() {
    vip6="::0"
    gateway6="::0"
    ipv6_prefix_length=64
    ipv6_addresses=("${default_ipv6_addresses[@]}")
}

_check_vip6() {
    if [[ "$1" == "::0" ]] ; then
        _reset_ipv6_address
        return 0
    fi

    _check_ipv6_address "$1"
}

_check_ipv6_prefix_length() {
    if [ "$1" -ge 0 -a "$1" -le 128 ] ; then
        return 0
    fi

    error_message="IPv6 prefix length should >= 0 and <= 128"
    return 1
}

_check_config_file() {
    if [ -f "$1" ] ; then
        return 0
    fi

    error_message="File $1 doesn't exist"
    return 1
}

_check_nodeid() {
   if [ "$1" -ge 1 -a "$1" -le ${node_count} ] ; then
        return 0
   fi

   error_message="The node ID $1 should be in [1, ${node_count}]"
   return 1
}

_check_dm() {
    if [ "$1" == "thin" -o "$1" == "lazyzeroedthick" -o "$1" == "zeroedthick" ] ; then
        return 0
    fi

    error_message="The valid value should be thin, lazyzeroedthick or zeroedthick"
    return 1
}

_check_number() {
    number='^[1-9]+'
    if [[ "$1" =~ $number ]] ; then
        return 0
    fi

    error_message="Please enter a number"
    return 1
}

_check_nodecount() {
    if [ "$1" -eq 3 -o "$1" -eq 5 -o "$1" -eq 1 ] ; then
        return 0
    fi

    error_message="The node count should be 1 (evaluation only), 3 or 5"
    return 1
}

_check_target_uri() {
    return 0
}

# set a parameter
# $1=option name
# $2=parameter name
# $3=check function
# $4: (optional) value not shown
_set_parameter() {
    local val
    local hidden_value=${4:-""}
    local no_echo=${5:-"false"}

    local opt=""
    if [ "${no_echo}" == "true" ] ; then
        opt="-s"
    fi

    while true ; do
        current_value=$(eval echo \${$2})

        if [[ "${hidden_value}" != "" && "${current_value}" =~ ${hidden_value} ]] ; then
            read -r -p "$1 (): " ${opt} val
        else
            read -r -p "$1 (${current_value}): " ${opt} val
        fi

        if [ "${val}" == "" ] ; then
            # use current value
            val="${current_value}"
        fi

        # if user gives "" or '', it means empty value
        if [ "${val}" == "\"\"" -o "${val}" == "''" ] ; then
            val=""
        fi

        # check the input from user
        eval $3 '${val}'
        if [ $? -eq 1 ] ; then
            echo "${error_message}"
            continue
        fi

        break
    done

    eval $2='${val}'
}

_init_cpucount() {
    if [ "${is_cpu_given}" = true ] ; then
        return
    fi

    case "${node_count}" in
        1)
            cpu_count=2 ;;
        3)
            cpu_count=2 ;;
        5)
            cpu_count=4 ;;
    esac
}

_init_memory() {
    if [ "${is_memory_given}" = true ] ; then
        return
    fi

    case "${node_count}" in
        1)
            memory=8192 ;;  #8G
        3)
            memory=8192 ;;  #8G
        5)
            memory=16384 ;; #16G
    esac
}

_set_nodecount() {
    _set_parameter "${node_count_label}" node_count _check_nodecount
    _init_cpucount
    _init_memory
}

_set_vip() {
    _set_parameter "${vip_label}" vip _check_vip
}

_set_gateway() {
    _set_parameter "${gateway_label}" gateway _check_ipv4_address "0.0.0.0"
}

_set_netmask() {
    _set_parameter "${netmask_label}" netmask _check_netmask
}

_set_ipaddr_1() {
    _set_parameter "${ipv4_addresses_labels[1]}" ipv4_addresses[1] _check_ipv4_address "${default_ipv4_addresses[1]}"
}

_set_ipaddr_2() {
    _set_parameter "${ipv4_addresses_labels[2]}" ipv4_addresses[2] _check_ipv4_address "${default_ipv4_addresses[2]}"
}

_set_ipaddr_3() {
    _set_parameter "${ipv4_addresses_labels[3]}" ipv4_addresses[3] _check_ipv4_address "${default_ipv4_addresses[3]}"
}

_set_ipaddr_4() {
    _set_parameter "${ipv4_addresses_labels[4]}" ipv4_addresses[4] _check_ipv4_address "${default_ipv4_addresses[4]}"
}

_set_ipaddr_5() {
    _set_parameter "${ipv4_addresses_labels[5]}" ipv4_addresses[5] _check_ipv4_address "${default_ipv4_addresses[5]}"
}

#IPv6 related functions
_set_vip6() {
    _set_parameter "${vip6_label}" vip6 _check_vip6
}

_set_ipaddr6_1() {
    _set_parameter "${ipv6_addresses_labels[1]}" ipv6_addresses[1] _check_ipv6_address "${default_ipv6_addresses[1]}"
}

_set_ipaddr6_2() {
    _set_parameter "${ipv6_addresses_labels[2]}" ipv6_addresses[2] _check_ipv6_address "${default_ipv6_addresses[2]}"
}

_set_ipaddr6_3() {
    _set_parameter "${ipv6_addresses_labels[3]}" ipv6_addresses[3] _check_ipv6_address "${default_ipv6_addresses[3]}"
}

_set_ipaddr6_4() {
    _set_parameter "${ipv6_addresses_labels[4]}" ipv6_addresses[4] _check_ipv6_address "${default_ipv6_addresses[4]}"
}

_set_ipaddr6_5() {
    _set_parameter "${ipv6_addresses_labels[5]}" ipv6_addresses[5] _check_ipv6_address "${default_ipv6_addresses[5]}"
}

_set_gateway6() {
    _set_parameter "${gateway6_label}" gateway6 _check_ipv6_address "::0"
}

_set_ipv6prefixlength() {
    _set_parameter "${ipv6_prefix_length_label}" ipv6_prefix_length _check_ipv6_prefix_length
}

_set_dm() {
    _set_parameter "${dm_label}" dm _check_dm
}

_set_ds() {
    _set_parameter "${ds_label}" ds _check_no_empty
}

_set_cpucount() {
    _set_parameter "${cpu_count_label}" cpu_count _check_number
    is_cpucount_given=true
}

_set_memory() {
    _set_parameter "${memory_label}" memory _check_number
    is_memory_given=true
}

_set_vmname() {
    _set_parameter "${vmname_label}" vmname _check_no_empty
}

_set_net() {
    _set_parameter "${net_label}" net _check_no_empty
}

_set_vmfolder() {
    _set_parameter "${vm_folder_label}" vm_folder _check_no_empty
}

_set_nodeid() {
    _set_parameter "${node_id_label}" node_id _check_nodeid
}

_set_poweron() {
    _set_parameter "${poweron_label}" poweron _check_yes_or_no
}

_set_file() {
    _set_parameter "${config_file_label}" config_file _check_config_file
}

_set_target_uri() {
    _set_parameter "${target_uri_label}" target_uri _check_target_uri

    # Romove leading and ending " and '
    target_uri=${target_uri#\'}
    target_uri=${target_uri%\'}
    target_uri=${target_uri#\"}
    target_uri=${target_uri%\"}

}

_set_username() {
    _set_parameter "${username_label}" username _nocheck
}

_set_password() {
    _set_parameter "${password_label}" password _nocheck '.*' true #true=no echo
    echo -e "\n"
}

_check_missing_vm_parameters() {
    if [ "${node_id}" != "" ] ; then
        _check_nodeid ${node_id}
        if [ $? -eq 1 ] ; then
            parameters_to_set+="nodeid "
        fi
    else
        parameters_to_set+="nodeid "
    fi

    if [ "${ds}" == "" ] ; then
        parameters_to_set+="ds "
    fi

    if [ "${net}" == "" ] ; then
        parameters_to_set+="net "
    fi

    if [ "${target_uri}" == "" ] ; then
        parameters_to_set+="target_uri "
    fi
}

_show_summary_title() {
    echo "*******************************************"
    echo "         Deployment settings               "
    echo "*******************************************"
}

_show_network_settings() {
    local summary="
Network properties
   ${node_count_label}: ${node_count}"

    ipv4_summary="
   IPv4 Settings
       ${vip_label}: ${vip}
       ${gateway_label}: ${gateway}
       ${netmask_label}: ${netmask}"

    local i
    for (( i=1; i<=${node_count}; i++ )) ; do
        ipv4_summary+="
        ${ipv4_addresses_labels[${i}]}: ${ipv4_addresses[i]}"
    done

    ipv6_summary="
   IPv6 Settings
       ${vip6_label}: ${vip6}
       ${gateway6_label}: ${gateway6}
       ${ipv6_prefix_length_label}: ${ipv6_prefix_length}"

    for (( i=1; i<=${node_count}; i++ )) ; do
        ipv6_summary+="
       ${ipv6_addresses_labels[${i}]}: ${ipv6_addresses[i]}"
    done

    summary+="${ipv4_summary}\n\n${ipv6_summary}"
    echo -e "${summary}" | more
}

_show_vm_settings() {
    vm_options_summary="\nVM Settings
       ${mode_label}: ${mode}
       ${node_id_label}: ${node_id}
       ${vmname_label}: ${vmname}
       ${ds_label}: ${ds}
       ${dm_label}: ${dm}
       ${net_label}: ${net}
       ${vm_folder_label}: ${vm_folder}
       ${cpu_count_label}: ${cpu_count}
       ${memory_label}: ${memory}
       ${poweron_label}: ${poweron}

       ${username_label}: ${username}
       ${target_uri_label}: ${target_uri}"

    echo -e "${vm_options_summary}" | more
}

_show_summary() {
    _show_summary_title
    _show_network_settings
    _show_vm_settings
}

_confirm() {
    confirmed="x"
    while ! [[ "${confirmed}" =~ [y|Y|n|N] ]] ; do
        read -p "$1 (Y/N/y/n):" confirmed
    done
}

_check_dup_ipv4_address() {

    local ret=0
    local count=0

    error_message=""

    if [ "$1" == "0.0.0.0" ] ; then
        return 0
    fi

    if [ "$1" == "${vip}" ] ; then
        (( count=count+1 ))
    fi

    if [ "$1" == "${netmask}" ] ; then
        (( count=count+1 ))
    fi

    if [ "$1" == "${gateway}" ] ; then
        (( count=count+1 ))
    fi

    local address
    for address in "${ipv4_addresses[@]}" ; do
        if [ "${address}" == "0.0.0.0" ] ; then
            continue
        fi

        if [ "$1" == "${address}" ] ; then
            (( count=count+1 ))
        fi
    done

    if [ ${count} -gt 1 ] ; then
        if [ "${error_message}" == "" ] ; then
            error_message="The following addresses are duplicated: "
        fi
        error_message+="$1 "
        ret=1
    fi

    if [ "${error_message}" != "" ] ; then
        echo -e "${error_message}"
    fi

    return ${ret}
}

_check_ipv4_duplicate_address() {

    local ret=0
    _check_dup_ipv4_address "${vip}"
    (( ret+=$? ))
    _check_dup_ipv4_address "${netmask}"
    (( ret+=$? ))
    _check_dup_ipv4_address "${gateway}"
    (( ret+=$? ))

    local address
    for address in "${ipv4_addresses[@]}" ; do
        _check_dup_ipv4_address "${address}"
        (( ret+=$? ))
    done

    return ${ret}
}

_check_ipv4_parameters() {
    local has_error

    #Check duplicate addresses
    _check_ipv4_duplicate_address
    has_error=$?

    _check_ipv6_duplicate_address
    (( has_error+=$? ))

    return ${has_error}
}

_get_ipv4_parameters_to_config() {
    parameters_to_set=""

    _set_vip

    if [ "${vip}" != "0.0.0.0" ]; then
        parameters_to_set="gateway netmask "

        local i
        for (( i=1; i<=${node_count}; i++ )) ; do
            parameters_to_set+="ipaddr_$i "
        done
    fi
}

_get_ipv6_parameters_to_config() {
    parameters_to_set=""

    _set_vip6

    if [ "${vip6}" != "::0" ]; then
        parameters_to_set="gateway6 ipv6prefixlength "

        local i
        for (( i=1; i<=${node_count}; i++ )) ; do
            parameters_to_set+="ipaddr6_$i "
        done
    fi
}

_check_dup_ipv6_address() {
    local ret=0
    local count=0

    error_message=""

    if [ "$1" == "::0" ] ; then
        return 0
    fi

    if [ "$1" == "${vip6}" ] ; then
        (( count=count+1 ))
    fi

    if [ "$1" == "${gateway6}" ] ; then
        (( count=count+1 ))
    fi

    local address
    for address in "${ipv6_addresses[@]}" ; do
        if [ "${address}" == "::0" ] ; then
            continue
        fi

        if [ "$1" == "${address}" ] ; then
            (( count=count+1 ))
        fi
    done

    if [ ${count} -gt 1 ] ; then
        if [ "${error_message}" == "" ] ; then
            error_message="The following addresses are duplicated:"
        fi
        error_message+=" $1"
        ret=1
    fi

    if [ "${error_message}" != "" ] ; then
        echo -e "${error_message}"
    fi

    return ${ret}
}

_check_ipv6_duplicate_address() {

    local ret=0
    _check_dup_ipv6_address "${vip6}"
    (( ret+=$? ))
    _check_dup_ipv6_address "${gateway6}"
    (( ret+=$? ))

    local address
    for address in "${ipv6_addresses[@]}" ; do
        _check_dup_ipv6_address "${address}"
        (( ret+=$? ))
    done

    return ${ret}
}

# $2=function to evaluate parameters to be configured
# $3=checking function
_set_network_parameters1() {
    while true ; do
        parameters_to_set=""

        eval "$1"

        if [ "${parameters_to_set}" != "" ] ; then
            _set_parameters
        fi

        eval "$2"

        if [ $? -eq 0 ] ; then
            break;
        fi

    done
}

_check_network_parameters() {
    if [ "${vip}" == "0.0.0.0" -a "${vip6}" == "::0" ] ; then
        error_message="At least IPv4 addresses or IPv6 addresses should be set"
        return 1
    fi

    return 0
}

_set_network_parameters() {
    while true ; do
        _set_nodecount
        _set_network_parameters1 _get_ipv4_parameters_to_config _check_ipv4_duplicate_address
        _set_network_parameters1 _get_ipv6_parameters_to_config _check_ipv6_duplicate_address

        _check_network_parameters

        if [ $? -eq 0 ] ; then
            break
        fi
    done
}

_set_parameters() {
    for parameter in ${parameters_to_set} ; do
        eval _set_${parameter}
    done
}

_set_settings_interactive() {
    local prompt="$1"
    local show_func="$2"
    local get_parameters_func="$3"
    while true ; do
        _confirm "Would you like to keep ${prompt}"
        if [[ "${confirmed}" =~ [Y|y] ]] ; then
            break
        fi
        eval ${get_parameters_func}
        eval ${show_func}
    done
}

_set_vm_parameters() {
    parameters_to_set="nodeid vmname ds dm net vmfolder cpucount memory poweron username password target_uri"
    _set_parameters
}

_init_parameters() {
    local i
    local option

    for (( i=0; i<${#settings[@]}; i++ )) ; do
        option=${settings[$i]}
        (( i=i+1 ))
        case ${option} in
        -help)
            _usage ;;
        -mode)
            mode="${settings[$i]}" ;;
        -nodecount)
            node_count="${node_count:-${settings[$i]}}" ;;
        -vip)
            vip="${vip:-${settings[$i]}}"
            ;;
        -ipaddr_[1-5])
            local index=${option:8}
            ipv4_addresses[${index}]="${ipv4_addresses[${index}]:-${settings[$i]}}"
            ;;
        -gateway)
            gateway="${gateway:-${settings[$i]}}"
            ;;
        -netmask)
            netmask="${netmask:-${settings[$i]}}"
            ;;
        -vip6)
            vip6="${vip6:-${settings[$i]}}"
            ;;
        -ipaddr6_[1-5])
            local index=${option:9}
            ipv6_addresses[${index}]="${ipv6_addresses[${index}]:-${settings[$i]}}"
            ;;
        -gateway6)
            gateway6="${gateway6:-${settings[$i]}}"
            ;;
        -ipv6prefixlength)
            ipv6_prefix_length="${ipv6_prefix_length:-${settings[$i]}}"
            ;;
        -vmprefix)
            vmname_prefix="${vmname_prefix:-${settings[$i]}}"
            vmname_prefix="${vmname_prefix%-}-"
            ;;
        -vmname)
            vmname="${vmname:-${settings[$i]}}" ;;
        -file)
            config_file="${config_file:-${settings[$i]}}" ;;
        -nodeid)
            node_id="${node_id:-${settings[$i]}}" ;;
        -ds)
            ds="${ds:-${settings[$i]}}" ;;
        -dm)
            dm="${dm:-${settings[$i]}}" ;;
        -net)
            net="${net:-${settings[$i]}}" ;;
        -vmfolder)
            vm_folder="${vm_folder:-${settings[$i]}}" ;;
        -poweron)
            (( i=i-1 ))
            poweron="yes"
            continue ;;
        -username)
            username="${username:-${settings[$i]}}" ;;
        -password)
            password="${password:-${settings[$i]}}" ;;
        -cpucount)
            cpu_count="${cpu_count:-${settings[$i]}}" ;;
        -memory)
            memory="${memory:-${settings[$i]}}" ;;
        -interactive)
            (( i=i-1 ))
            interactive=true
            continue ;;
        -targeturi)
            target_uri="${target_uri:-${settings[$i]}}"
            ;;
        -clusterversion)
            # comes from the config file of redeploy mode
            cluster_version="${settings[$i]}"
            ;;
        *)
            _fatal "Unknown option ${option}" ;;
        esac
    done
}

_set_parameters_interactive() {

    local network_prompt="network parameters"
    local vm_prompt="VM parameters"

    while true ; do
        if [ "${show_summary}" = true ] ; then
            _show_summary
            _confirm "Would you like to keep those settings"
            if [[ "${confirmed}" =~ [Y|y] ]] ; then
                break
            fi
        fi

        if [ "${parameters_to_set}" != "" ] ; then
            _set_parameters
        else
            _set_settings_interactive "${network_prompt}" _show_network_settings _set_network_parameters
            _set_settings_interactive "${vm_prompt}" _show_vm_settings _set_vm_parameters
        fi
        parameters_to_set=""
        show_summary=true
    done
}

_check_missing_ipv4_parameters() {

    local err_msg=""
    error_message=""

    if [ "${vip}" != "" ] ; then
        _check_ipv4_address "${vip}"
        if [ $? -eq 1 ] ; then
            error_message="Invalid options:\n-vip option: ${vip}\n"
            parameters_to_set+="vip "
        fi
    else
        parameters_to_set+="vip "
    fi

    if [ "${gateway}" != "" ] ; then
        err_msg="${error_message}"
        _check_ipv4_address "${gateway}"
        if [ $? -eq 1 ] ; then
            error_message="${err_msg} -gateway: ${gateway}\n"
            parameters_to_set+="gateway "
        fi
    else
        parameters_to_set+="gateway "
    fi

    if [ "${netmask}" != "" ] ; then
        err_msg="${error_message}"
        _check_ipv4_address "${netmask}"
        if [ $? -eq 1 ] ; then
            error_message="${err_msg} -netmask: ${netmask}\n"
            parameters_to_set+="netmask "
        fi
    else
        parameters_to_set+="netmask "
    fi

    for (( i=1; i<=${node_count}; i++ )) ; do
        if [ "${ipv4_addresses[$i]}" != "" ] ; then
            err_msg="${error_message}"
            _check_ipv4_address "${ipv4_addresses[$i]}"
            if [ $? -eq 1 ] ; then
                error_message="${err_msg} -ipaddr_$i: invalid IPv4 address ${ipv4_addresses[$i]}\n"
                parameters_to_set+="ipaddr_$i "
            fi
        else
            parameters_to_set+="ipaddr_$i "
        fi
    done

    if [ "${error_message}" != "" ] ; then
        echo -e "${error_message}"
    fi
}

_check_missing_ipv6_parameters() {

    local err_msg=""
    error_message=""

    if [ "${vip6}" != "" ] ; then
        _check_ipv6_address "${vip6}"
        if [ $? -eq 1 ] ; then
            error_message+="Invalid options:\n-vip6: ${vip6}\n"
            parameters_to_set+="vip6 "
        fi
    else
        parameters_to_set+="vip6 "
    fi

    if [ "${gateway6}" != "" ] ; then
        err_msg="${error_message}"
        _check_ipv6_address "${gateway6}"
        if [ $? -eq 1 ] ; then
            error_message="${err_msg} -gateway6: ${gateway6}\n"
            parameters_to_set+="gateway6 "
        fi
    else
        parameters_to_set+="gateway6 "
    fi

    if [ "${ipv6_prefix_length}" != "" ] ; then
        err_msg="${error_message}"
        _check_ipv6_prefix_length "${ipv6_prefix_length}"
        if [ $? -eq 1 ] ; then
            error_message="${err_msg} -ipv6prefixlength: ${ipv6_prefix_length}\n"
            parameters_to_set+="ipv6prefixlength "
        fi
    fi

    for (( i=1; i<=${node_count}; i++ )) ; do
        if [ "${ipv6_addresses[$i]}" != "" ] ; then
            err_msg="${error_message}"
            _check_ipv6_address "${ipv6_addresses[$i]}"
            if [ $? -eq 1 ] ; then
                error_message="${err_msg} -ipaddr6_$i: invalid IPv6 address ${ipv6_addresses[$i]}\n"
                parameters_to_set+="ipaddr6_$i "
            fi
        else
            parameters_to_set+="ipaddr6_$i "
        fi
    done

    if [ "${error_message}" != "" ] ; then
        echo -e "${error_message}"
    fi
}

_has_valid_ipv4_parameters() {
    if [ "${vip}" != "" -a "${vip}" != "0.0.0.0" -o \
         "${gateway}" != "" -a "${gateway}" != "0.0.0.0" -o \
         "${netmask}" != "" -a "${netmask}" != "255.255.255.0"  ] ; then
        has_ipv4_options=true
        return 0
    fi

    for addr in "${ipv4_addresses[@]}" ; do
        if [ "${addr}" != "0.0.0.0" ] ; then
            has_ipv4_options=true
            return 0
        fi
    done
    return 1
}

_has_valid_ipv6_parameters() {
    if [ "${vip6}" != "" -a "${vip6}" != "::0" -o \
         "${gateway6}" != "" -a "${gateway6}" != "::0" ] ; then
        has_ipv6_options=true
        return 0
    fi

    if [ "${ipv6_prefix_length}" != ""  ] ; then
        if [ ${ipv6_prefix_length} -ne 64 ] ; then
            has_ipv6_options=true
            return 0
        fi
    fi

    for addr in "${ipv6_addresses[@]}" ; do
        if [ "${addr}" != "::0" ] ; then
            has_ipv6_options=true
            return 0
        fi
    done

    return 1
}

_check_missing_network_parameters() {
    _has_valid_ipv4_parameters
    if [ "${has_ipv4_options}" = true ] ; then
        _check_missing_ipv4_parameters
    fi

    _has_valid_ipv6_parameters
    if [ "${has_ipv6_options}" = true ] ; then
        _check_missing_ipv6_parameters
    fi

    if [ "${has_ipv4_options}" = false -a "${has_ipv6_options}" = false ] ; then
        parameters_to_set+="network_parameters "
    elif [ "${is_nodecount_given}" = false ] ; then
        parameters_to_set="nodecount ${parameters_to_set}"
    fi
}

_parse_target_uri() {
    # Remove leading vi://
    target_uri=${target_uri#vi://}

    local pattern='.*@.*'
    if ! [[ "${target_uri}" =~ ${pattern} ]] ; then
        #no username and password
        return
    fi

    local str

    #extract username[:password]
    str=${target_uri%%@*}

    local user=""
    local passwd=""

    user=${str%%:*}
    pattern='.*:'

    if [[ "${str}" =~ ${pattern} ]] ; then
        passwd=${str#*:}
    fi

    username=${username:-$user}
    password=${password:-$passwd}

    #remove the username and password
    target_uri=${target_uri#*@}
}

_encode_string() {
    local str=$(eval echo \${$1})
    local length=${#str}
    local encoded_str=""
    local i
    local c
    for (( i=0; i<${length}; i++ )); do
        c=${str:$i:1}
        case $c in
            []\\\ \	\[%\#\|?{,\;:\"\'/\<\>~\'!@$^\&\*\(\)+=\}])
                encoded_str+=`printf '%%%x' "'$c"` ;;
            *)
                encoded_str+=$c ;; # normal character
        esac
    done
    eval $1='${encoded_str}'
}

_check_parameters() {
    if [ "${mode}" == "" ] ; then
        _fatal "-mode option is missing"
    fi

    if [ "${mode}" != "install" -a "${mode}" != "redeploy" ] ; then
        _fatal "Invalid mode ${mode} which should be 'install' or 'redeploy'"
    fi

    if [ "${mode}" == "install" ] ; then
        if [ "${node_count}" = "" ] ; then
            node_count=3
            if [ "${ipv4_addresses[4]}" != "" -o "${ipv4_addresses[5]}" != "" \
                  -o "${ipv6_addresses[4]}" != "" -o "${ipv6_addresses[5]}" != "" ] ; then
                node_count=5
            fi
            is_nodecount_given=false

            interactive=true
        fi
    else
        # redeploy mode
        if [ "${config_file}" = "" ] ; then
            _set_file
            _parse_setting_file "${config_file}"
            _init_parameters
            interactive=true
        fi
    fi

    _check_missing_network_parameters
    _check_missing_vm_parameters

    if [ "${parameters_to_set}" != "" ] ; then
        show_summary=false
    fi

    if [ -f "${setting_file}" ] ; then
        local ipv4_options=${has_ipv4_options}
        local ipv6_options=${has_ipv6_options}

        _parse_setting_file "${setting_file}"

        # init parameters from .settings file
        # only overwrite the parameters that current value=default value
        _init_parameters

        if [  "${ipv4_options}" = false -a "${vip}" != "0.0.0.0" -o "${ipv6_options}" = false -a "${vip6}" != "::0"  ] ; then
            # command line doesn't provide IPv4/IPv6 settings, but .settings does
            # this may or may not what user wants, we need to let user know about this
            # so go into 'interactive' mode
            interactive=true
        fi

        # the node_id is the only required parameter after initing with .setting file
        # if it is missing, we should ask it from user instead of showing a summary
        # page first
        if [ "${node_id}" != "" ] ; then
            show_summary=true
        fi
    fi

    #init parameters with default values if they are not set
    settings=("${default_settings[@]}")
    _init_parameters
    poweron=${poweron:-"no"}

    if [ "${cpu_count}" == "" ] ; then
        _init_cpucount
    else
        is_cpucount_given=true
    fi
 
    if [ "${memory}" == "" ] ; then
        _init_memory
    else
        is_memory_given=true
    fi

    if [ "${parameters_to_set}" == "" -a "${interactive}" = false ] ; then
        # no missing options and -interactive is not given
        vmname=${vmname:-${vmname_prefix}${product_name}${node_id}}
        return
    fi

    vmname=${vmname:-${vmname_prefix}${product_name}${node_id}}
    vmname_prefix=${vmname_prefix:-vipr}

    _set_parameters_interactive
}

_persist_settings() {
    ipv4_settings="
#IPv4 settings
-vip=${vip}
-gateway=${gateway}
-netmask=${netmask}
"
    ipv6_settings="
#IPv6 settings
-vip6=${vip6}
-gateway6=${gateway6}
-ipv6prefixlength=${ipv6_prefix_length}
"
    local i
    for (( i=1; i<=${node_count}; i++ )) ; do
        ipv4_settings+="-ipaddr_$i=${ipv4_addresses[$i]}\n"
        ipv6_settings+="-ipaddr6_$i=${ipv6_addresses[$i]}\n"
    done

    other_settings="
#Other settings
-nodecount=${node_count}

-ds=${ds}
-dm=${dm}
-net=${net}
-vmfolder=${vm_folder}
"

    # for the security reason, the password is not persisted
    local uri
    if [ "${username}" != "" ] ; then
        uri="vi://${username}@${target_uri}"
    else
        uri="vi://${target_uri}"
    fi

    other_settings+="-targeturi=${uri}"

    echo -e "${ipv4_settings} ${ipv6_settings} ${other_settings}" > "${setting_file}"
}

_parse_setting_file() {
    local option
    local value
    local comment='^[[:space:]]*#.*$'
    local emptyline='^[[:space:]]*$'
    local setting_line='^-.*=*$'
    local i=0
    settings=()
    while read line ; do
        if [[ ${line} =~ $comment ]] || [[ ${line} =~ $emptyline ]] ; then
            continue
        fi

        if ! [[ ${line} =~ ${setting_line} ]] ; then
            echo -e "Warning: Unrecognized line ${line}"
            continue
        fi

        option=${line%%=*}
        value=${line##*=}

        if [ ${option} != "-poweron" ] ; then
            settings[$i]="${option}"
            (( i=i+1))
            settings[$i]="${value}"
        else
            if [ "${value}" = "yes" ] ; then
                settings[$i]="${option}"
            else
               (( i=i-1 ))
            fi
        fi
        (( i=i+1 ))
    done < "$1"
}

_confirm_license() {
    license='${include="storageos-license.txt"}'
    echo -e "${license}" | more

    _confirm "Accept the license?"

    if [[ "${confirmed}" =~ [n|N] ]] ; then
        # Don't accept the license, so quit
        exit 1
    fi
}

_check_version() {
    if [ "${version}" != "${cluster_version}" ] ; then
        _fatal "The versions are different: cluster:${cluster_version}, the node to be redeploy: ${version}"
    fi
}

_set_data_disks_path() {
    current_dir=`dirname $0`
    vmdk_dir=`cd ${current_dir} && pwd`
}

default_settings=(
"-vip" "0.0.0.0"
"-gateway" "0.0.0.0"
"-netmask" "255.255.255.0"
"-ipaddr_1" "0.0.0.0"
"-ipaddr_2" "0.0.0.0"
"-ipaddr_3" "0.0.0.0"
"-ipaddr_4" "0.0.0.0"
"-ipaddr_5" "0.0.0.0"
"-vip6" "::0"
"-gateway6" "::0"
"-ipv6prefixlength" "64"
"-ipaddr6_1" "::0"
"-ipaddr6_2" "::0"
"-ipaddr6_3" "::0"
"-ipaddr6_4" "::0"
"-ipaddr6_5" "::0"
"-nodeid" "1"
"-net" "VM Network"
"-dm" "zeroedthick"
"-vmfolder" "/"
)

settings=()
setting_file=".settings"

# Settings for IPv4
vip=
vip_label="Public virtual IPv4 address"

gateway=
gateway_label="IPv4 default gateway"

netmask=
netmask_label="Network netmask"

default_ipv4_addresses=([0]="0.0.0.0" [1]="0.0.0.0" [2]="0.0.0.0" [3]="0.0.0.0" [4]="0.0.0.0" [5]="0.0.0.0")
ipv4_addresses=()
ipv4_addresses_labels=([1]="IPv4 address of node 1" [2]="IPv4 address of node 2" [3]="IPv4 address of node 3" [4]="IPv4 address of node 4" [5]="IPv4 address of node 5")

# Settings for IPv6
vip6=""
vip6_label="Public virtual IPv6 address"

gateway6=""
gateway6_label="IPv6 default gateway"

ipv6_prefix_length=""
ipv6_prefix_length_label="IPv6 prefix length"

default_ipv6_addresses=("::0" "::0" "::0" "::0" "::0" "::0")
ipv6_addresses=()
ipv6_addresses_labels=([1]="IPv6 address of node 1" [2]="IPv6 address of node 2" [3]="IPv6 address of node 3" [4]="IPv6 address of node 4" [5]="IPv6 address of node 5")

# Other settings
mode=""
mode_label="Mode [ install | redeploy ]"

node_count=
is_nodecount_given=true
node_count_label="Node count [ 1 (evaluation only) | 3 | 5 ]"

node_id=
node_id_label="Node ID"

username=""
username_label="Username"
password=""
password_label="Password"
target_uri=""
target_uri_label="Target URI"

ds=""
ds_label="Datastore"

net=""
net_label="Network name"

vm_folder=""
vm_folder_label="Folder"

dm=""
dm_label="Disk provisioning [thin | lazyzeroedthick | zeroedthick]"

poweron=""
poweron_label="Power on [yes | no]"

vmname_prefix=""

vmname=""
vmname_label="Virtual machine name"

cpu_count=""
is_cpucount_given=false
cpu_count_label="CPU count"

memory=""
is_memory_given=false
memory_label="Memory size (in MB)"

config_file=""
config_file_label="Config file"

interactive=false
has_ipv4_options=false
has_ipv6_options=false

release=${product_version}.${product_release}
cluster_version=""

version="${product_name}-${release}"
data_disks="${version}-disk1.vmdk ${version}-disk2.vmdk ${version}-disk5.vmdk"

common_env_properties=""
tmpdir=/tmp

disk4_flat_raw_vmdk=""
disk4_vmdk_descriptor=""
disk4_vmx=""
disk4_ovf=""
disk4_mf=""
disk4_streamOptimized=""
disk4_vmdk_filename=""
vipr_ovf_file=""
vipr_mf=""

vmdk_dir=""
common_ovftool_options="--acceptAllEulas "
confirmed="x"
error_message=""
show_summary=false

# If no arguments are given
# print the usage
if [ "$#" -eq 0 ] ; then
    _usage
fi

_check_prerequistes

# the command arguments have highest priority
settings=("$@")
_init_parameters

if [ ! -f "${setting_file}" ] ; then
    _confirm_license
fi

#if -file is given, use it to initalize the settings again.
if [ "${config_file}" != ""  ] ; then
    _parse_setting_file "${config_file}"
    _init_parameters
fi

_check_parameters

if [ "${username}" != "" ] ; then
    _encode_string username
fi

if [ "${password}" != "" ] ; then
    _encode_string password
fi

_parse_target_uri

_generate_file_names

#All parameters are passed check
#so save them to .settings file
_persist_settings
_set_traps
_set_data_disks_path
_set_common_ovftool_options
_generate_common_ovf_properties

if [ "${mode}" == "redeploy" ] ; then
    _check_version
fi

_generate_node_files "${node_id}"

# Deploy vipr VM
_deploy_vm "${node_id}"
