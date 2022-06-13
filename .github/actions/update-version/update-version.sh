#!/bin/bash

set -x

UPSTREAM_REMOTE=upstream-$1
VERSION_BRANCH=$2

git config user.email "no-reply@amazon.com"
git config user.name "corretto-github-robot"

git checkout ${VERSION_BRANCH}

UPDATE=$(git ls-remote --tags ${UPSTREAM_REMOTE} |grep "jdk8u" |grep -vE "(-ga|{})$" |cut -d'u' -f2 |sort -nr |head -n1)
MAJOR=8
MINOR=$(echo "${UPDATE}" |cut -d- -f1)
BUILD=$(echo "${UPDATE}" |cut -db -f2)
CURRENT_VERSION=$(cat version.txt)

if [[ ${CURRENT_VERSION} == ${MAJOR}.${MINOR}.${BUILD}.* ]]; then
    echo "Corretto version is current."
else
    echo "Updating Corretto version"
    NEW_VERSION="${MAJOR}.${MINOR}.${BUILD}.1"
    echo  "${NEW_VERSION}" > version.txt
    git commit -m "Update Corretto version to match upstream: ${NEW_VERSION}" version.txt
    git push origin ${VERSION_BRANCH}
fi
