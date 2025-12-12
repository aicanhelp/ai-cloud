#!/bin/bash

BASE_DIR=$(dirname $0) && CUR_DIR=$(pwd) && cd ${BASE_DIR} && BASE_DIR=$(pwd)
cd ${BASE_DIR} && trap 'echo && cd ${CUR_DIR};exit' 0 1 2 15

LATEST_PACKAGE_FILE="${BASE_DIR}/package.latest"
CURRENT_PACKAGE_FILE="${BASE_DIR}/packages.conf"
DELTA_PACKAGE_FILE="${BASE_DIR}/packages.delta"

BITNAMI_DIR=${BASE_DIR}/../resources/bitnami-containers/bitnami

grep -r '\-linux-${OS_ARCH}\-' --include="Dockerfile" ${BITNAMI_DIR}|sed 's/.*: *"//'|sed 's/".*//' > ${LATEST_PACKAGE_FILE}


# 在file1中但不在file2中的行
grep -Fxvf ${CURRENT_PACKAGE_FILE} ${LATEST_PACKAGE_FILE} >${DELTA_PACKAGE_FILE}