#!/bin/bash

BASE_DIR=$(dirname $0) && CUR_DIR=$(pwd) && cd ${BASE_DIR} && BASE_DIR=$(pwd)
cd ${BASE_DIR} && trap 'echo && cd ${CUR_DIR};exit' 0 1 2 15

DOWNLOADS_URL="https://downloads.bitnami.com/files/stacksmith"
DOWNLOADED_DIR="/edata/bitnami"

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

   wget -P ${DOWNLOADED_DIR} "${_l_remote_file}"
   wget -P ${DOWNLOADED_DIR} "${_l_remote_file}.sha256"
}

function __download(){
   local _l_package_file=$1
   ! test -f ${_l_package_file} && echo "Not found: ${_l_package_file}" && exit 1 
   while read -r line; do
      [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
   
      __download_file "${line}" "amd64" 

      __download_file "${line}" "arm64" 
   
   done < "${_l_package_file}"
}

function download_base(){
   __download "${CURRENT_PACKAGE_FILE}"
}

function download_update(){
   source ${BASE_DIR}/update.sh
   __download "${DELTA_PACKAGE_FILE}"
}

test -z "${MODE}" && echo "Please input mode: base|update" && exit 0

download_${MODE}