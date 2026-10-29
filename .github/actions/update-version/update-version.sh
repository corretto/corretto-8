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
BUILD=${BUILD:-"0"}
MAJOR=8
CURRENT_VERSION=$(cat version.txt)

# Upstream tags can lag Corretto's current build. Compare numerically because
# JDK 8 build numbers are commonly zero-padded, including 08 and 09.
if [[ ${CURRENT_VERSION} =~ ^${MAJOR}\.${MINOR}\.([0-9]+)\.[0-9]+$ ]]; then
    CURRENT_BUILD=${BASH_REMATCH[1]}
    if (( 10#${BUILD} <= 10#${CURRENT_BUILD} )); then
        echo "Upstream build ${BUILD} is not newer than ${CURRENT_VERSION}; leaving it unchanged."
        exit 0
    fi
fi

if [[ ${CURRENT_VERSION} == ${MAJOR}.${MINOR}.${BUILD}.* ]]; then
    echo "Corretto version is current."
else
    echo "Updating Corretto version"
    NEW_VERSION="${MAJOR}.${MINOR}.${BUILD}.1"
    echo  "${NEW_VERSION}" > version.txt
    git commit -m "Update Corretto version to match upstream: ${NEW_VERSION}" version.txt
    git push origin ${VERSION_BRANCH}
fi
