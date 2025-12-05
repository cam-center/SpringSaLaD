#!/bin/bash

#=========================================#
# For building Spring Salad installers
#=========================================#

# Get environment variables
# CONFIG_DIR: directory containing configuration files
# MAVEN_ROOT_DIR: root directory of the Maven project
# INSTALL4J_PATH: path to the install4j compiler executable
source install4j.env


if [[ "$#" -ge 3 ]]; then
  echo "Using command line arguments for configuration."
  CONFIG_DIR="$1"
  MAVEN_ROOT_DIR="$2"
  INSTALL4J_PATH="$3"
  if [[ "$#" == 4 ]]; then
    INSTALL4J_LICENSE="$4"
  fi
elif [[ -z "${CONFIG_DIR}" ]] || [[ -z "${MAVEN_ROOT_DIR}" ]] || [[ -z "${INSTALL4J_PATH}" ]]; then
    echo "One or more required environment variables are not set:"
    echo "  CONFIG_DIR='${CONFIG_DIR}'"
    echo "  MAVEN_ROOT_DIR='${MAVEN_ROOT_DIR}'"
    echo "  INSTALL4J_PATH='${INSTALL4J_PATH}'"
    exit 1
fi

#--------------------------------#

if [[ -n "${INSTALL4J_LICENSE}" ]]; then
  echo "Installing Install4J license."
  $INSTALL4J_PATH -L "${INSTALL4J_LICENSE}"
fi

$INSTALL4J_PATH \
--disable-signing \
--faster \
--debug \
-D \
macKeystore="${CONFIG_DIR}"/Apple_Dev_Id_Certificate_exp_20270924.p12,\
mavenRootDir="${MAVEN_ROOT_DIR}",\
updateSiteBaseUrl='http://vcell.org/webstart/Fake' \
"${MAVEN_ROOT_DIR}"/SpringSaLaDAll.install4j
