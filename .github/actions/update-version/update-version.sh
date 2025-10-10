#!/bin/bash

set -x

UPSTREAM_REMOTE=upstream-$1
VERSION_BRANCH=$2

git config user.email "no-reply@amazon.com"
git config user.name "corretto-github-robot"

git checkout ${VERSION_BRANCH}

source common/autoconf/version-numbers
MINOR=${JDK_UPDATE_VERSION}
BUILD=$(git ls-remote --tags ${UPSTREAM_REMOTE} |grep "jdk8u${MINOR}" |grep -vE "(-ga|{})$" |cut -d'-' -f2 |tr -d 'b' |sort -nr |head -n1)
BUILD=${BUILD:-"00"}
MAJOR=8
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
