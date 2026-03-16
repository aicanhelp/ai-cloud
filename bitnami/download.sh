#!/bin/bash

BASE_DIR=$(dirname $0) && CUR_DIR=$(pwd) && cd ${BASE_DIR} && BASE_DIR=$(pwd)
cd ${BASE_DIR} && trap 'echo && cd ${CUR_DIR};exit' 0 1 2 15

DOWNLOADS_URL="https://downloads.bitnami.com/files/stacksmith"
DOWNLOADED_DIR="/edata/bitnami"


PACKAGES_LIST=($(ls -d ${BASE_DIR}/containers/* | sed 's/.*-//'))

LATEST_PACKAGE_FILE="${BASE_DIR}/package.latest"
CURRENT_PACKAGE_FILE="${BASE_DIR}/packages.conf"
DELTA_PACKAGE_FILE="${BASE_DIR}/packages.delta"

MODE=$1

function __checksum(){
   cd ${DOWNLOADED_DIR}
   if sha256sum -c "${1}.sha256"; then
      cd ${BASE_DIR} && return 0
   else
      cd ${BASE_DIR} && return 1
   fi
}

function __download_file(){
   local _l_line="$1" && local _l_arch=$2

   OS_ARCH="${_l_arch}" && eval "_l_file_name=${_l_line}.tar.gz"
   _l_remote_file="${DOWNLOADS_URL}/${_l_file_name}"
   _l_local_file="${DOWNLOADED_DIR}/${_l_file_name}"

   echo
   echo "Downloading file ${_l_remote_file} to ${DOWNLOADED_DIR} ..."

   if test -f "${_l_local_file}" && test -f "${_l_local_file}.sha256"; then
      if __checksum "${_l_local_file}"; then
        echo "Already downloaded into ${_l_local_file}."  && return 0
      fi
      rm -f  "${_l_remote_file}" "${_l_remote_file}.sha256"
   fi 

   # wget -P ${DOWNLOADED_DIR} "${_l_remote_file}"
   axel -n 8  -o "${DOWNLOADED_DIR}/${_l_file_name}" "${_l_remote_file}"
   wget -P ${DOWNLOADED_DIR} "${_l_remote_file}.sha256"
}

function download(){
   local _l_package_file="packages-${MODE}.conf"
   if ! test -f ${_l_package_file}; then
      grep -r '\-linux-${OS_ARCH}\-' --include="Dockerfile" ${BITNAMI_DIR}|sed 's/.*: *"//'|sed 's/".*//'|sort|uniq > ${_l_package_file}
   fi
   while read -r line; do
      [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
   
      __download_file "${line}" "amd64" 

      __download_file "${line}" "arm64" 
   
   done < "${_l_package_file}"
}

test -z "${MODE}" && echo "Please input group: ${PACKAGES_LIST[*]}" && exit 0

download