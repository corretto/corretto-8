# Change Log for Amazon Corretto 8

The following sections describe the changes for each release of Amazon Corretto 8.

## Corretto version: 8.352.08.1

Release Date: October 18, 2022

**Target Platforms <sup>1</sup>**

+ RPM-based Linux using glibc 2.12 or later, x86_64
+ Debian-based Linux using glibc 2.12 or later, x86_64
+ RPM-based Linux using glibc 2.17 or later, aarch64
+ Debian-based Linux using glibc 2.17 or later, aarch64
+ Alpine-based Linux, x86_64
+ Windows 7 or later, x86, x86_64
+ macOS 10.15 and later, x86_64
+ macOS 11.0 and later, aarch64

**1.** This is the platform targeted by the build. See [Using Amazon Corretto](https://aws.amazon.com/corretto/faqs/#Using_Amazon_Corretto)
in the Amazon Corretto FAQ for supported platforms

The following issues are addressed in 8.352.08.1:

| Issue Name                    | Platform | Description                                               | Link |
|------------------------------------------|-----------------------------------------------------------|------|
| Import jdk8u352-b08           | All      | Updates Corretto baseline to OpenJDK 8u352-b08            | [jdk8u352-b08](https://github.com/openjdk/jdk8u/releases/tag/jdk8u352-b08) |
| JSR 337 MR4 Changes           | All      | All JSR 337 Maintenance Release 4 related release changes | [JSR 337 MR4](https://jcp.org/aboutJava/communityprocess/maintenance/jsr337/jsr337-mr4-changes.html) |
| Update Timezone Data to 2022e | All      | All tzdata updates up to 2022e                            | [#426](https://github.com/corretto/corretto-8/pull/426) [#427](https://github.com/corretto/corretto-8/pull/427) |
| Update amazon cacerts         | All      | Update amazon cacerts file from amazonlinux               | |

The following CVEs are addressed in 8.352.08.1:

| CVE            | CVSS | Component                   |
|----------------|------|-----------------------------|
| CVE-2022-21626 | 5.3  | security-libs/java.security |
| CVE-2022-21628 | 5.3  | core-libs/java.net          |
| CVE-2022-21619 | 3.7  | security-libs/java.security |
| CVE-2022-21624 | 3.7  | core-libs/javax.naming      |


## Corretto version: 8.342.07.4

Release Date: July 27, 2022

**Target Platforms**

+ RPM-based Linux using glibc 2.12 or later, x86_64
+ Debian-based Linux using glibc 2.12 or later, x86_64
+ RPM-based Linux using glibc 2.17 or later, aarch64
+ Debian-based Linux using glibc 2.17 or later, aarch64
+ Alpine-based Linux, x86_64

The following issues are addressed in 8.342.07.4

| Issue Name          | Platform | Description                           | Link |
|---------------------| --- |---------------------------------------| --- |
| Segmentation Fault with -XX:NativeMemoryTracking=detail | Linux | | [#411](https://github.com/corretto/corretto-8/issues/411) |

## Corretto version: 8.342.07.3

Release Date: July 22, 2022

**Target Platforms**

+ RPM-based Linux using glibc 2.12 or later, x86_64
+ Debian-based Linux using glibc 2.12 or later, x86_64
+ RPM-based Linux using glibc 2.17 or later, aarch64
+ Debian-based Linux using glibc 2.17 or later, aarch64
+ Alpine-based Linux, x86_64
+ Windows 7 or later, x86, x86_64
+ macOS 10.15 and later, x86_64
+ macOS 11.0 and later, aarch64

The following issues are addressed in 8.342.07.3

| Issue Name          | Platform | Description                           | Link |
|---------------------| --- |---------------------------------------| --- |
| Revert JDK-8194154 | All | Revert "8194154: System property user.dir should not be changed" | [JDK-8290832](https://bugs.openjdk.org/browse/JDK-8290832) / [JDK-8194154](https://bugs.openjdk.org/browse/JDK-8194154) |
| Fix macOS packaging | macOS | Update macos packaging logic to avoid duplicate paths after installation | [#409](https://github.com/corretto/corretto-8/pull/409) |

## Corretto version: 8.342.07.2
 
Release Date: July 19, 2022
 
**Target Platforms**
 
+ Amazon Linux 2022, x86_64
+ Amazon Linux 2022, aarch64
 
The following issues are addressed in 8.342.07.2
 
| Issue Name          | Platform | Description                           | Link |
|---------------------| --- |---------------------------------------| --- |
| Disable LinkTimeOptimization | Amazon Linux 2022 | Additional fixes for Amazon Linux 2022 | | 
 

## Corretto version: 8.342.07.1

Release Date: July 19, 2022

**Target Platforms**

+ RPM-based Linux using glibc 2.12 or later, x86_64
+ Debian-based Linux using glibc 2.12 or later, x86_64
+ RPM-based Linux using glibc 2.17 or later, aarch64
+ Debian-based Linux using glibc 2.17 or later, aarch64
+ Alpine-based Linux, x86_64
+ Windows 7 or later, x86, x86_64
+ macOS 10.15 and later, x86_64
+ macOS 11.0 and later, aarch64

The following issues are addressed in 8.342.07.1

| Issue Name          | Platform | Description                           | Link |
|---------------------| --- |---------------------------------------| --- |
| Import jdk8u342-b07 | All | Updates Corretto baseline to OpenJDK 8u342-b07 | [jdk8u342-b08](https://github.com/openjdk/jdk8u/releases/tag/jdk8u342-b07)
| Update CACerts      | All | Pull in the latest CA Certificates from Amazon Linux | [PR #403](https://github.com/corretto/corretto-8/pull/403) |
| Fix builds on Alpine 3.13 | Alpine Linux 3.13 | Backport [JDK-8263718](https://bugs.openjdk.org/browse/JDK-8263718) to fix builds on alpine-3.13 | [PR #397](https://github.com/corretto/corretto-8/pull/397) |
| Alpine package missing "provides" variable | Alpine Linux | Add `provides java-jdk` for Corretto alpine packages | [Issues #391](https://github.com/corretto/corretto-8/issues/391) |
| Update OS detection code to recognize Windows 10/11 | Windows 10/11 | Backport [JDK-8071530](https://bugs.openjdk.org/browse/JDK-8071530) to recognize Windows 10 and [JDK-8274840](https://bugs.openjdk.org/browse/JDK-8274840) to recognize Windows 11 when calling `System.getProperty("os.name")` | [Issues #396](https://github.com/corretto/corretto-8/issues/396) |
| Migrate pkg builds to productbuild from packages | macOS       | Updates to macos packaging                                                           | [PR #390](https://github.com/corretto/corretto-8/pull/390) |
| Enable bundled zlib library via Gradle           | macOS       | Updates to use bundled (not the system) version of the zlib library on macOS aarch64 | [PR #404](https://github.com/corretto/corretto-8/pull/404) |
| AL2022 updates                                   | AL2, AL2022 | Updates to support Corretto in Amazon Linux 2022                                     | [PR #394](https://github.com/corretto/corretto-8/pull/394) [PR #398](https://github.com/corretto/corretto-8/pull/398) [PR #407](https://github.com/corretto/corretto-8/pull/407) |

The following CVEs are addressed in 8.342.07.1

| CVE            | CVSS | Component        |
|----------------|------|------------------|
| CVE-2022-34169 | 7.5  | xml/jaxp         |
| CVE-2022-21541 | 5.9  | hotspot/runtime  |
| CVE-2022-21540 | 5.3  | hotspot/compiler |

## Corretto version: 8.332.08.1

Release Date: April 19, 2022

**Target Platforms**

+ RPM-based Linux using glibc 2.12 or later, x86_64
+ Debian-based Linux using glibc 2.12 or later, x86_64
+ RPM-based Linux using glibc 2.17 or later, aarch64
+ Debian-based Linux using glibc 2.17 or later, aarch64
+ Alpine-based Linux, x86_64
+ Windows 7 or later, x86, x86_64
+ macOS 10.13 and later, x86_64
+ macOS 11.0 and later, aarch64

The following issues are addressed in 8.332.08.1

| Issue Name          | Platform | Description                           | Link |
|---------------------| --- |---------------------------------------| --- |
| Import jdk8u332-b08 | All | Updates Corretto baseline to OpenJDK 8u332-b08 | [jdk8u332-b08](https://github.com/openjdk/jdk8u/releases/tag/jdk8u332-b08)
| Update zlib         | All | CVE-2018-25032: based upon our analysis, OpenJDK/Corretto is not affected by CVE-2018-25032, because the zlib "memLevel" parameter is not settable and is fixed at 8, and the usage of the Z_FIXED strategy is prevented. With these settings there is no way to invoke the issue described in the CVE and we only include this fix out of an abundance of caution. | |

The following CVEs are addressed in 8.332.08.1

| CVE | CVSS | Component |
| --- | --- | --- |
| CVE-2022-21476 | 7.5 | security-libs/java.security |
| CVE-2022-21496 | 5.3 | core-libs/javax.naming |
| CVE-2022-21434 | 5.3 | core-libs/java.lang |
| CVE-2022-21426 | 5.3 | xml/jaxp |
| CVE-2022-21443 | 3.7 | security-libs/java.security |

## Corretto version: 8.322.06.3

Release Date: January 25, 2022

The following platforms are updated in this release.

**Target Platforms**
+  Amazon Linux 2 x86_64 and aarch64

The following issues are addressed in 8.322.06.3

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Update JavaFX | Amazon Linux 2 | Rebuild with the latest JavaFX | |



## Corretto version: 8.322.06.2

Release Date: January 18, 2022

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Alpine-based Linux, x86_64


The following issues are addressed in 8.322.06.2

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Fix RPM and Deb issues | RPM/Deb Based | Fix issue with RPM and Deb alternatives | [#352](https://github.com/corretto/corretto-8/issues/352)]|

## Corretto version: 8.322.06.1

Release Date: January 18, 2022

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Alpine-based Linux, x86_64
+  Windows 7 or later, x86, x86_64
+  macOS 10.10 and later, x86_64


The following issues are addressed in 8.322.06.1

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Update Corretto to 8.322.06 | All | Update Corretto 8 patch set to 8.322.06. Update the security baseline to OpenJDK8u322. ||
| Fix Windows x86 crashes in Swing apps | Windows x86 | Swing Application crashes / hangs since update from 8.275.01.1 to 8.282.08.1 | [#305](https://github.com/corretto/corretto-8/issues/305) |
| Use SHA256 hashes in RPM File Digest | RPM-based Linux | Update plugins to support FIPS compliant file digests in generated RPMs | [#344](https://github.com/corretto/corretto-8/issues/344) |
| Sign Mac bundle | Macos x64 | The Macos tgz contained signed files, but was not signed as a bundle. | |
| Update Amazon CACerts | All | Pull in the latest CA Certificates from Amazon Linux | |

The following CVEs are addressed in 8.322.06.1

| CVE | CVSS | Component |
| --- | --- | --- |
| CVE-2022-21341 | 5.3 | core-libs/java.io:serialization |
| CVE-2022-21365 | 5.3 | client-libs/javax.imageio |
| CVE-2022-21282 | 5.3 | xml/jaxp |
| CVE-2022-21305 | 5.3 | hotspot/compiler |
| CVE-2022-21299 | 5.3 | xml/jaxp |
| CVE-2022-21296 | 5.3 | xml/jaxp |
| CVE-2022-21349 | 5.3 | client-libs/2d |
| CVE-2022-21283 | 5.3 | core-libs/java.util |
| CVE-2022-21340 | 5.3 | security-libs/java.security |
| CVE-2022-21293 | 5.3 | core-libs/java.lang |
| CVE-2022-21294 | 5.3 | core-libs/java.util |
| CVE-2022-21360 | 5.3 | client-libs/javax.imageio |
| CVE-2022-21248 | 3.7 | core-libs/java.io:serialization |


## Corretto version: 8.312.07.1

Release Date: October 19, 2021

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Alpine-based Linux, x86_64
+  Windows 7 or later, x86, x86_64
+  macOS 10.10 and later, x86_64


The following issues are addressed in 8.312.07.1

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Update Corretto to 8.312.07 | All | Update Corretto 8 patch set to 8.312.07. Update the security baseline to OpenJDK8u312. ||
| Fix issue with jcmd and other tools on Alpine Linux | Alpine Linux | Commands such as jcmd failed when the java process id was 1 in Alpine Docker images. | [#321](https://github.com/corretto/corretto-8/pull/321) |
| Update version strings to recommended formats | macOS | Fix incorrect reporting of version numbers in Corretto 8 .pkg metadata | [#329](https://github.com/corretto/corretto-8/pull/329) |
| Fix for Font rendering corruption | macOS | Fix for font rendering corruption issue reported in [#333](https://github.com/corretto/corretto-8/issues/333) | [#335](https://github.com/corretto/corretto-8/pull/335) |
| Enable building with XCode 13 | macOS | Enable builds with the most recent versions of macOS / XCode | [#334](https://github.com/corretto/corretto-8/pull/334) |

The following CVEs are addressed in 8.312.07.1

| CVE | CVSS | Component |
| --- | --- | --- |
| CVE-2021-35567 | 6.8 | security-libs/java.security |
| CVE-2021-35550 | 5.9 | security-libs/javax.net.ssl |
| CVE-2021-35586 | 5.3 | client-libs/javax.imageio |
| CVE-2021-35564 | 5.3 | security-libs/java.security |
| CVE-2021-35561 | 5.3 | core-libs/java.util |
| CVE-2021-35565 | 5.3 | core-libs/java.net |
| CVE-2021-35559 | 5.3 | client-libs/javax.swing |
| CVE-2021-35578 | 5.3 | security-libs/javax.net.ssl |
| CVE-2021-35556 | 5.3 | client-libs/javax.swing |
| CVE-2021-35603 | 3.7 | security-libs/javax.net.ssl |
| CVE-2021-35588 | 3.1 | hotspot/runtime |

## Corretto version: 8.302.08.1

Release Date: Jul 20, 2021

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Alpine-based Linux, x86_64
+  Windows 7 or later, x86, x86_64
+  macOS 10.10 and later, x86_64


The following issues are addressed in 8.302.08.1

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Update Corretto to 8.302.08.1  |  All  |  Update Corretto 8 patch set to 8.302.08.1. Update the security baseline to OpenJDK 8u302.  | |
| Remove root certificates with 1024 bit keys | All | Removed all root certificates with 1024 bit keys.  | [JDK-8243559](https://bugs.openjdk.java.net/browse/JDK-8243559) |
| Enable building with XCode 12 | macOS | Enable builds with the most recent versions of macOS / XCode | [JDK-8267545](https://bugs.openjdk.java.net/browse/JDK-8267545) |
| Corretto8 JRE MSI | Windows | Provide JRE MSI installer on both x86 and x86_64 | [corretto-8#108](https://github.com/corretto/corretto-8/pull/108) |

The following CVEs are addressed in 8.302.08.1

| CVE | CVSS | Component |
| --- | --- | --- |
| CVE-2021-2388  | 7.5 | hotspot/compiler
| CVE-2021-2369  | 4.3 | security-libs/java.security
| CVE-2021-2341  | 3.1 | core-libs/java.net

## Corretto version: 8.292.10.1

Release Date: Apr 20, 2021

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Alpine-based Linux, x86_64
+  Windows 7 or later, x86, x86_64
+  macOS 10.10 and later, x86_64


The following issues are addressed in 8.292.10.1

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Update Corretto to 8.292.10.1  |  All  |  Update Corretto 8 patch set to 8.292.10.1. Update the security baseline to OpenJDK 8u292.  | |
| Remove expired certificates | All | Removed expired certificates thawtepremiumserverca and verisigntsaca | |
| Update Aarch64 port | Linux aarch64 | Replace the IcedTea based aarch64 port with the one in OpenJDK8u | |
| Enable TLS 1.0 and 1.1 | All | Re-enable TLS 1.0 and 1.1 which have been disabled in upstream 8u292 | |
| 8033289: clang: clean up unused function warning | All | Fixes a warning that appears building with clang (macOS) | [JDK-8033289](https://bugs.openjdk.java.net/browse/JDK-8033289) |
| 8265238: [macos] build failure in OpenJDK8u after JDK-8211301 in older xcode  | macOS | Fixes an error when using Xcode 6.3 |[JDK-8265238](https://bugs.openjdk.java.net/browse/JDK-8265238) |

The following CVEs are addressed in 8.292.10.1

| CVE | CVSS | Component |
| --- | --- | --- |
| CVE-2021-2161  | 5.9 | core-libs/java.io
| CVE-2021-2163  | 5.3 | security-libs/java.security

## Corretto version: 8.282.08.1

Release Date: Jan 19, 2021

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Alpine-based Linux, x86_64
+  Windows 7 or later, x86, x86_64
+  macOS 10.10 and later, x86_64


The following issues are addressed in 8.282.08.1

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Update Corretto to 8.282.08.1  |  All  |  Update Corretto 8 patch set to 8.272.10.3. Update the security baseline to OpenJDK 8u282.  | |
| Update windows build toolchain to VS2013 | Windows | Update the Windows build tool from VS2010 to VS2013 | [corretto-8#278](https://github.com/corretto/corretto-8/pull/278) |
| Integrate Alpine port | Linux | Alpine port used to be in different branch to avoid build issue on other Linux platforms. This has been addressed. | |

This version addresses a number of vulnerabilities that do not have an associated CVE.

## Corretto version: 8.275.01.1

Release Date: Nov 4, 2020

 The following platforms are updated in this release.

**Target Platforms**

+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Windows 7 or later, x86, x86_64
+  macOS 10.10 and later, x86_64


The following issues are addressed in 8.275.01.1

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| jdk8u275-b01: Import from upstream  |  all  |  Update Corretto baseline to OpenJDK jdk8u275-b01 | |
| 8255603: Memory/Performance regression after JDK-8210985| all | Fixes a problem with the initial size of SSLSessionCache being too big | [JDK-8255603](https://bugs.openjdk.java.net/browse/JDK-8255603) |


## Corretto version: 8.272.10.3

Release Date: Oct 21, 2020

 The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Windows 7 or later, x86, x86_64
+  macOS 10.10 and later, x86_64

The following issues are addressed in 8.272.10.3


| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Update Corretto to 8.272.10.3  |  All  |  Update Corretto 8 patch set to 8.272.10.3. Update the security baseline to OpenJDK 8u272.  |   |
| On AL2 AARCH64 builds use -moutline-atomics flags | Amazon Linux 2 aarch64 | Improves performance on aarch64 systems | |
| Allow passing OpenJFX source location as Gradle property | Linux x86_64 & macOS | Allows building with OpenJFX in a custom location | |

The following CVEs are addressed in 8.272.10.3

| CVE | CVSS | Component |
| --- | --- | --- |
| CVE-2020-14803  | 5.3 | core-libs/java.io
| CVE-2020-14792  | 4.2 | hotspot/compiler
| CVE-2020-14782  | 3.7 | security-libs/java.security
| CVE-2020-14797  | 3.7 | core-libs/java.nio
| CVE-2020-14781  | 3.7 | core-libs/javax.naming
| CVE-2020-14779  | 3.7 | core-libs/java.io:serialization
| CVE-2020-14796  | 3.1 | core-libs/java.io
| CVE-2020-14798  | 3.1 | core-libs/java.io

## Corretto version: 8.265.01.1

Release Date: Jul 29, 2020

 The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64
+  Debian-based Linux using glibc 2.12 or later, x86_64
+  RPM-based Linux using glibc 2.17 or later, aarch64
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Windows 7 or later, x86, x86_64
+  macOS 10.10 and later, x86_64

The following issues are addressed in 8.265.01.1


| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Update Corretto to 8.265.01.1  |  All  |  Updates corretto to be based on OpenJDK 8u265b01  |   |
| 8249677: Regression after JDK-8237117: Better ForkJoinPool behavior  |  All  |  Fixes a regression added to ForkJoinPool in 8.262.10.1.  | [JDK-8249677](https://bugs.openjdk.java.net/browse/JDK-8249677)  |
| Exclude Alpine patches | Linux | Alpine related patches are built independently | |

## Corretto version: 8.262.10.2

Release Date: Jul 14, 2020

 The following platforms are updated in this release.

**Target Platforms**
+  Alpine-based Linux, x86_64

The following issues are addressed in 8.262.10.2

| Issue Name | Platform | Description | Link |
| --- | --- | --- | --- |
| Corretto Alpine GA | Linux | Corretto 8 on Alpine Linux is now general available | |

## Corretto version: 8.262.10.1

Release Date: Jul 14, 2020

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  RPM-based Linux using glibc 2.17 or later, aarch64 
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues are addressed in 8.262.10.1


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
| Update Corretto to 8.262.10.1  |  All  |  Update Corretto 8 patch set to 8.262.10.1. Update the security baseline to OpenJDK 8u262.  |   |
| Update aarch64 port to IcedTea 3.16.0 | Linux |  Sync corretto-8 aarch64 port with IcedTea 3.16.0 | [icedtea-3.16.0](http://icedtea.classpath.org/hg/icedtea8/rev/c2851d8a1375) |
| Update Amazon linux cacerts	| All | Updates the set of certificates from Amazon Linux bundled with Corretto | |
| Remove expired certificates | All | Removed three expired CA certificates present in upstream | |
| Enable JDK Flight Recorder | All | Enable JDK Flight Recorder feature in Corretto 8 | [JDK-8223147](https://bugs.openjdk.java.net/browse/JDK-8223147) |
| Backport JDK-8186090 | Linux | 8186090: java.nio.Bits.unaligned() doesn't handle aarch64 | [corretto8#248](https://github.com/corretto/corretto-8/issues/248) |

The following CVEs are addressed in 8.262.10.1

| CVE | CVSS | Component |
| --- | --- | --- |
| CVE-2020-14583 | 8.3 | core-libs/java.io
| CVE-2020-14664 | 8.3 | javafx/graphics
| CVE-2020-14593 | 7.4 | client-libs/2d
| CVE-2020-14621 | 5.3 | xml/jaxp
| CVE-2020-14556 | 4.8 | core-libs/java.util.concurrent
| CVE-2020-14577 | 3.7 | security-libs/javax.net.ssl
| CVE-2020-14578 | 3.7 | security-libs/java.security
| CVE-2020-14579 | 3.7 | security-libs/java.security
| CVE-2020-14581 | 3.7 | client-libs/2d

## Corretto version: 8.252.09.2

Release Date: Apr 17, 2020

 The following platforms are updated in this release. 

**Target Platforms**
+  Windows 7 or later, x86_64 

The following issues are addressed in 8.252.09.2


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
| Fix Windows x64 signing error  | Windows x64  | Corretto8 for Windows x64 had a defective sunec.dll | [corretto-8#230](https://github.com/corretto/corretto-8/issues/230)  |

## Corretto version: 8.252.09.1

Release Date: Apr 14, 2020

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  RPM-based Linux using glibc 2.17 or later, aarch64 
+  Debian-based Linux using glibc 2.17 or later, aarch64
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in 8.252.09.1


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
| Update Corretto to 8.252.09.1  |  All  |  Update Corretto 8 patch set to 8.252.09.1. Update the security baseline to OpenJDK 8u252.  |   |
| Additional provides for debian packages | debian-based Linux | Add old java (headless) runtime provides to the debian package (Java2) | [corretto-8#190](https://github.com/corretto/corretto-8/issues/190) |
| hsdis library not picked up correctly on expected paths  | Windows  |  Fixes a portability issue in hsdis with the file separator.  |  [JDK-8043264](https://bugs.openjdk.java.net/browse/JDK-8043264)  | 
| Update build logic to handle certificates post build | All | Ensure Corretto still ships the expected set of ca certificates after [JDK-8193255](https://bugs.openjdk.java.net/browse/JDK-8193255) was backported into upstream |  |
| Fix CHANGELOG.md for 8.242.07.1 | All | [corretto-8#193](https://github.com/corretto/corretto-8/issues/193) was not fixed in this release |  | 

The following CVEs are addressed in 8.252.09.1

| CVE | CVSS | Component |
| --- | --- | --- |
| CVE-2020-2803 | 8.3 | core-libs/java.nio |
| CVE-2020-2805 | 8.3 | core-libs/java.io |
| CVE-2020-2781 | 5.3 | security-libs/java.security |
| CVE-2020-2830 | 5.3 | core-libs/java.util |
| CVE-2020-2800 | 4.8 | core-libs/java.net |
| CVE-2020-2754 | 3.7 | core-libs/javax.script |
| CVE-2020-2755 | 3.7 | core-libs/javax.script |
| CVE-2020-2773 | 3.7 | security-libs/javax.xml.crypto |
| CVE-2020-2756 | 3.7 | core-libs/java.io:serialization |
| CVE-2020-2757 | 3.7 | core-libs/java.io:serialization |


## Corretto version: 8.242.08.1-1

Release Date: Feb 5, 2020

 The following platform is updated in this release.

**Target Platforms**
+  macOS 10.10 and later, x86_64

The following issues and enhancements are addressed in 8.242.08.1-1

| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
| Fixed tgz packaging error in 8.242.08.1  |  macOS  |  A packaging error was found in the macOS tar.gz in 8.242.08.1. The mac installer (pkg) is not affected. |   | 

## Corretto version: 8.242.08.1

Release Date: Jan 18, 2020

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  RPM-based Linux using glibc 2.17 or later, aarch64 
+  Debian-based Linux using glibc 2.17 or later, aarch64 
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in 8.242.08.1

| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
| Update Corretto to 8.242.08.1.  |  All  |  Update Corretto to 8.242.08.1.  |   | 
| Missing memory barriers for CMS collector | aarch64 | Missing StoreStore barriers in C1 generated code for CMS | [corretto-8#201](https://github.com/corretto/corretto-8/issues/201)  |

## Corretto version: 8.242.07.1

Release Date: Jan 14, 2020

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  RPM-based Linux using glibc 2.17 or later, aarch64 
+  Debian-based Linux using glibc 2.17 or later, aarch64 
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in 8.242.07.1


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
| Additional provides for debian packages | debian-based Linux | Add old java (headless) runtime provides to the debian package (Java5-8) | [corretto-8#190](https://github.com/corretto/corretto-8/issues/190) |



The following CVEs are addressed in 8.242.07.1


| CVE # | Component Affected | 
| --- | --- | 
|  CVE-2020-2604  |  Serialization  | 
|  CVE-2020-2601  |  Security  | 
|  CVE-2020-2655  |  JSSE  | 
|  CVE-2020-2593  |  Networking  | 
|  CVE-2020-2654  |  Libraries  | 
|  CVE-2020-2590  |  Security  | 
|  CVE-2020-2659  |  Networking  | 
|  CVE-2020-2583  |  Serialization  | 
|  CVE-2019-13117 |   OpenJFX (libxslt)
|  CVE-2019-13118 |  OpenJFX (libxslt)
|  CVE-2019-16168 |  OpenJFX (SQLite)
|  CVE-2020-2585  |  OpenJFX

## Corretto version: 8.232.09.2

Release Date: Nov 20, 2019

 The following platforms are updated in this release. 

**Target Platforms**
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in 8.232.09.2.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Java2D Queue Flusher crash when closing lid and/or switching away from external monitors  |  macOS  |  JVM crashes when closing the lid of the macbook or switching between different monitors. This issue was reproducible in both OpenJDK8 and 11.  |  [corretto-11#46](https://github.com/corretto/corretto-11/issues/46)  | 



## October 2019 critical patch update: 8.232.09.1: Amazon Corretto 8

Release Date: Oct 15, 2019

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  RPM-based Linux using glibc 2.17 or later, aarch64 
+  Debian-based Linux using glibc 2.17 or later, aarch64 
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in 8.232.09.1.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto to 8.232.09.1  |  All  |  Update Corretto 8 patch set to 8.232.09.1. Update the security baseline to OpenJDK 8u232.  |   | 
|  Explicitly convert `_lh_array_tag_type_value` from unsigned to int  |  macOS  |  This is caused by the backport of [JDK-8152856](https://bugs.openjdk.java.net/browse/JDK-8152856). It caused `_lh_array_tag_obj_value` being implicitly converted to unsigned int during the compilation.  |   | 
|  Turn on the `-Wreturn-type` warning  |  All  |  This is a backport of [JDK-8062808](https://bugs.openjdk.java.net/browse/JDK-8062808), which help catching missing return statements earlier in the development cycle.  |   | 

The following CVEs are addressed in 8.232.09.1.


| CVE # | Component Affected | 
| --- | --- | 
|  CVE-2019-2949  |  security-libs/javax.net.ssl  | 
|  CVE-2019-2989  |  core-libs/java.net  | 
|  CVE-2019-2958  |  core-libs/java.lang  | 
|  CVE-2019-2975  |  core-libs/javax.script  | 
|  CVE-2019-2999  |  tools/javadoc  | 
|  CVE-2019-2964  |  core-libs/java.util.regex  | 
|  CVE-2019-2962  |  client-libs/2d  | 
|  CVE-2019-2973  |  xml/jaxp  | 
|  CVE-2019-2978  |  core-libs/java.net  | 
|  CVE-2019-2981  |  xml/jaxp  | 
|  CVE-2019-2983  |  client-libs/2d  | 
|  CVE-2019-2987  |  client-libs/2d  | 
|  CVE-2019-2988  |  client-libs/2d  | 
|  CVE-2019-2992  |  client-libs/2d  | 
|  CVE-2019-2894  |  security-libs/javax.net.ssl  | 
|  CVE-2019-2933  |  core-libs  | 
|  CVE-2019-2945  |  core-libs/java.net  | 

## GA release: 8.222.10.4: Amazon Corretto 8

Release Date: Sept 17, 2019

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.17 or later, aarch64 
+  Debian-based Linux using glibc 2.17 or later, aarch64 

The following issues and enhancements are addressed in 8.222.10.4.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto 8.222.10.4 RC to 8.222.10.4 GA.  |  aarch64  |  Amazon Corretto 8.222.10.4 for aarch64 is now GA.  |   | 

## Corretto version 8.222.10.2 for Amazon Linux 2 Release Candidate

Release Date: September 04, 2019

The following platforms are updated in this release.

**Target Platforms**
+  Amazon Linux 2, x64 and aarch64. 

The following enhancement is addressed in 8.222.10.2.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto to 8.222.10.2.  |  Amazon Linux 2, x64 and aarch64  |  Update Corretto to 8.222.10.2.  |   | 

## Corretto version 8.222.10.4 for aarch64 Release Candidate

Release Date: July 26, 2019

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.17 or later, aarch64 
+  Debian-based Linux using glibc 2.17 or later, aarch64 

The following enhancement is addressed in 8.222.10.4.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto to 8.222.10.4.  |  aarch64  |  Update aarch64 backend. Update Corretto 8 patch set to 8.222.10.4.  |   | 

## Corretto version 8.222.10.3

Release Date: July 16, 2019

The following platforms are updated in this release.

**Target Platforms**
+  Windows 7 or later, x86, x86_64 

The following issue is addressed in 8.222.10.3.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  The `java.vm.vendor` property is incorrect in Corretto 8.222.10.1 Windows build.  |  Windows  |  The `java.vm.vendor` property should have the value "Amazon.com Inc.".  |   | 

## Corretto version 8.222.10.2 for aarch64 preview

Release Date: July 16, 2019

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.17 or later, aarch64 
+  Debian-based Linux using glibc 2.17 or later, aarch64 

The following enhancement is addressed in 8.222.10.2.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto to 8.222.10.2.  |  aarch64  |  Update aarch64 backend. Update Corretto 8 patch set to 8.222.10.2.  |   | 

## July 2019 critical patch update: Corretto version 8.222.10.1

Release Date: July 16, 2019

The following platforms are updated in this release.

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in 8.222.10.1.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto to 8.222.10.1  |  All  |  Update Corretto 8 patch set to 8.222.10.1. Update the security baseline to OpenJDK 8u222.  |   | 
|  `JAVA_HOME/PATH` and empty folder remains after uninstall Corretto x86 and x64  |  Windows  |  When uninstalling Corretto on Windows, the `JAVA_HOME` and `PATH` environment variable remains, and the installed directory remains empty.  |  [corretto-8#38](https://github.com/corretto/corretto-8/issues/38)  | 
|  javafx_font.dll Error when executing JavaFX tests  |  Windows  |  The Corretto JVM crashes when executing JavaFX tests using the Surefire plugin.  |  [corretto-8#49](https://github.com/corretto/corretto-8/issues/49)  | 
|  Windows MSI in unbranded and doesn't show what version I'm installing  |  Windows, macOS  |  When installing on Windows or MacOS using the Corretto installers, the installer does not tell the Corretto version to be installed.  |  [corretto-8#112](https://github.com/corretto/corretto-8/issues/112)  | 
|  Windows UAC popup is cryptic and unbranded  |  Windows  |  When using Corretto Windows MSI installer, the ﻿UAC popup Window﻿ is unbranded and doesn't tell the users what they are approving.  |  [corretto-8#113](https://github.com/corretto/corretto-8/issues/113)  | 
|  MSI upgrade does not remove old version  |  Windows  |  Corretto MSI should remove old artifacts when installing a newer version.  |  [corretto-8#115](https://github.com/corretto/corretto-8/issues/115)  | 
|  Windows binaries don't include MS Visual Studio 2017 redistributables needed for OpenJFX  |  Windows  |  Missing of VS runtime dll causes QuantumRenderer failed to be initialized.  |  [corretto-8#116](https://github.com/corretto/corretto-8/issues/116)  | 
|  JDK MSI Installer Registry Keys  |  Windows  |  Applications using Corretto JRE fail to launch if they depend on the Windows registry keys to check the Java version.  |  [corretto-8#122](https://github.com/corretto/corretto-8/issues/122)  | 
|  msvcr120.dll is missing in the final JRE distribution for Windows  |  Windows  |   |  [corretto-8#131](https://github.com/corretto/corretto-8/issues/131)  | 

The following CVEs are addressed in 8.222.10.1.


| CVE # | Component Affected | 
| --- | --- | 
|  CVE-2019-7317  |  AWT (libpng)  | 
|  CVE-2019-2842  |  JCE  | 
|  CVE-2019-2766  |  Networking  | 
|  CVE-2019-2816  |  Networking  | 
|  CVE-2019-2745  |  Security  | 
|  CVE-2019-2786  |  Security  | 
|  CVE-2019-2762  |  Utilities  | 
|  CVE-2019-2769  |  Utilities  | 

## New platform releases: Version 8.212.04.3 for aarch64 preview

Release Date: June 14, 2019

The following new platforms are supported.

**New Platforms**
+  RPM-based Linux using glibc 2.17 or later, **aarch64** 
+  Debian-based Linux using glibc 2.17 or later, **aarch64** 

The following issues and enhancements are addressed in 8.212.04.03


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto to 8.212.04.3.  |  aarch64  |  Update aarch64 backend. Update Corretto 8 patch set to 8.212.04.3.  |   | 
|  Backport JDK-8219006: AArch64: Register corruption in slow subtype check  |  aarch64  |  This patch fixes the intrinsic arraycopy in debug build. The compiled method of System.arraycopy crashed due to register corruption.  |   | 
|  Backport JDK-8224671: AArch64: mauve System.arraycopy test failure  |  aarch64  |  This patch fixes the intrinsic arraycopy. The instruction eonw in the codestub arraycopy was encoded with the wrong operand register zr.  |   | 
|  Backport JDK-8155627: Enable SA on AArch64  |  aarch64  |  This patch puts sa-jdi.jar into the JDK image. developer tools such as hsdb depends on it.  |   | 

## Corretto version 8.212.04.2 for Amazon Linux 2

Release Date: May 02, 2019

 The following platforms are updated in this release. 

**Target Platforms**
+  Amazon Linux 2, x64 and aarch64. 

The following issues and enhancements are addressed in 8.212.04.2.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto to 8.212.04.2.  |  All  |  Update Corretto 8 patch set to 8.212.04.2.  |   | 
|  Backport JDK-8048782: OpenJDK: PiscesCache : xmax/ymax rounding up can cause RasterFormatException  |  All  |  This patch fixes issue where sun.java2d.pisces.PiscesCache constructor that accepts min/max x and y arguments - the internal 'bboxX1' and 'bboxY1' are set to values one greater than given maximum X and Y values. This effectively causes an "off by 1" error.  |  [corretto-8#94](https://github.com/corretto/corretto-8/issues/94)  | 

## Corretto version 8.212.04.2

Release Date: Apr 21, 2019

8.212.04.2 improves handling of TrueType fonts (JDK-8219066). The following platforms are updated:

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

## April 2019 critical patch update: Corretto version 8.212.04.1

Release Date: Apr 16, 2019

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in 8.212.04.1.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update Corretto to 8.212.04.1.  |  All  |  Update Corretto 8 patch set to 8.212.04.1.  |   | 
|  Backport JDK-8048782: OpenJDK: PiscesCache : xmax/ymax rounding up can cause RasterFormatException  |  All  |  This patch fixes issue where sun.java2d.pisces.PiscesCache constructor that accepts min/max x and y arguments - the internal 'bboxX1' and 'bboxY1' are set to values one greater than given maximum X and Y values. This effectively causes an "off by 1" error.  |  [corretto-8#94](https://github.com/corretto/corretto-8/issues/94)  | 
|  Add jinfo file to Corretto Debian package.  |  Debian-based Linux  |  This patch fixes Corretto 8 does not provide a .jinfo file, which used by update-java-alternatives to switch all java related symlinks to another distribution.  |  [corretto-8#63](https://github.com/corretto/corretto-8/issues/63)  | 
|  Include /jre/lib/applet directory in rpm and deb packaging  |  RPM-based Linux，Debian-based Linux  |  /jre/lib/applet directory is missing in Corretto8 generic Linux deb and rpm, which makes it inconsistent with generic Linux tgz and other artifacts. This patch adds it back to deb and rpm.  |   | 

## 8.202.08.2: Amazon Corretto 8 RC.

Release Date: Jan 25, 2019

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in 8.202.08.2.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Update java.vendor/java.vm.vendor.  |  All  |  Vendor-related metadata has been updated to identify Amazon as the vendor of this OpenJDK distribution.  |  [corretto-8#3](https://github.com/corretto/corretto-8/issues/3)  | 
|  The Windows Installer should set file association for .jar files.  |  Windows  |  Windows users will now be able to run executable JARs using the file explorer.  |  [corretto-8#43](https://github.com/corretto/corretto-8/issues/43)  | 
|  Javapackager fails to load DLLs.  |  Windows  |  The JavaFX Packager on Windows has been fixed to allow bundling of MSVC DLLs.  |  [corretto-8#47](https://github.com/corretto/corretto-8/issues/47)  | 

## 8u202 PSU releases: Corretto version 8.202.08.1 for Amazon Linux 2.

Release Date: Jan 25, 2019

The following new platforms are supported.

**New Platforms**
+  Experimental support for aarch64 on Amazon Linux 2. 

 The following platforms are updated in this release.

**Target Platforms**
+  Amazon Linux 2 

## 8u202 PSU releases: Corretto version 8.202.08.1

Release Date: Jan 23, 2019

 The following platforms are updated in this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in this release.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Remove DIZ files in Windows distribution.  |  Windows  |  Previous releases of Corretto on Windows contained debugging-related DIZ files. We received feedback that removing these files would benefit resource-constrained environments.  |  [corretto-8#33](https://github.com/corretto/corretto-8/issues/33)  | 
|  Improvements to JAVA_HOME-related variables on Windows.  |  Windows  |  Two fixes that will improve the ability for Windows applications to detect and use Amazon Corretto.  |  [corretto-8#39](https://github.com/corretto/corretto-8/issues/39) and [corretto-8#40](https://github.com/corretto/corretto-8/issues/40)  | 

## New platform releases: Version 1.8.0_192-amazon-corretto-preview2-b12 and 1.8.0_192-amazon-corretto-preview2_1-b12

Release Date: Jan 16, 2019

The following new platforms are supported.

**New Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 

 The following platforms are compatible with this release. 

**Target Platforms**
+  RPM-based Linux using glibc 2.12 or later, x86_64 
+  Debian-based Linux using glibc 2.12 or later, x86_64 
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

The following issues and enhancements are addressed in this release.


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  Support distribution via tar.gz/ZIP archives  |  Linux  |  To support other distribution systems (eg: Docker images, SDKMan), Corretto should also be offered in "plain" archives (tar.gz, ZIP).  |  [corretto-8#2](https://github.com/corretto/corretto-8/issues/2) and [corretto-8#10](https://github.com/corretto/corretto-8/issues/10)  | 
|  Debian Package distribution  |  Linux  |  Corretto should be offered in Debian package format in favor of customers using Debian-based Linux.  |  [corretto-8#16](https://github.com/corretto/corretto-8/issues/16)  | 
|  Support older versions of GLIBC  |  Linux  |  Current RPM for Amazon Linux 2 contains binaries that require GLIBC_2.26.  |  [corretto-8#20](https://github.com/corretto/corretto-8/issues/20)  | 
|  File javafx-src.zip missing: source code debugging for OpenJFX not enabled  |  Windows  |  The preview release is missing file javafx-src.zip with the compressed source code of OpenJFX.  |  [corretto-8#19](https://github.com/corretto/corretto-8/issues/19)  | 
|  Easy identification x86/x64 in Apps & Features of Windows  |  Windows  |  The Windows installer/uninstaller of Corretto should clearly display the architecture (x86/x64) information.  |  [corretto-8#37](https://github.com/corretto/corretto-8/issues/37)  | 
|  macOS Corretto installer without root access  |  macOS  |  Corretto Mac installer requires root access during the installation however some environments will require Corretto to be installed without necessarily having root access.  |  [corretto-8#31](https://github.com/corretto/corretto-8/issues/31)  | 

## Bug fix releases: Version 1.8.0_192-amazon-corretto-preview2-b12

Release Date: Dec 17, 2018

 The following platforms are compatible with this release. 

**Target Platforms**
+  Windows 7 or later, x86, x86_64 
+  macOS 10.10 and later, x86_64 

 The following are the bugs and enhancements addressed in this release. 


| Issue Name | Platform | Description | Link | 
| --- | --- | --- | --- | 
|  libfreetype.dylib is incorrectly packaged  |  macOS  |  The libfontmanager in Corretto is linked to the libfreetype in X11 and breaks when X11 is not installed.  |  [corretto-8#6﻿](https://github.com/corretto/corretto-8/issues/6﻿)  | 
|  Eclipse and Eclipse-installer fail to run when using Amazon Corretto 8  |  macOS  |  The libjli.dylib under `amazon-corretto-8.jdk/Contents/MacOS` should be a symlink to `../Home/jre/lib/jli/libjli.dylib` but was dereferenced. This causes the native JVM invoker in Ecplise failed to locate the JRE.  |  [corretto-8#18](https://github.com/corretto/corretto-8/issues/18)  | 
|  Enhance Installer to add standard registry keys on Windows for compact  |  Windows  |  Enhance the Windows installer to add registry keys for Corretto during the installation process.  |  [corretto-8#14](https://github.com/corretto/corretto-8/issues/14)  | 
|  Allow JAVA_HOME and PATH to be configured from installer  |  Windows  |  Enhance the Windows installer to add `JAVA_HOME` environment variable and also update the `PATH` environment variable with the Corretto installation location.  |  [corretto-8#15](https://github.com/corretto/corretto-8/issues/15)  | 
|  Support Windows 32bit binaries  |  Windows  |  Provide certified build for Windows 32bit OS.  |  [corretto-8#22](https://github.com/corretto/corretto-8/issues/22)  | 

## Initial Release: Version 1.8.0_192-amazon-corretto-preview-b12

Release Date: Nov 14, 2018

 The following platforms are compatible with this release. 

**Target Platforms**
+  Amazon Linux 2, x86_64 
+  Windows 7 or later, x86_64 
+  macOS 10.10 and later, x86_64 

 The following are the changes for this release. 


| Patch | Description | Release Date | 
| --- | --- | --- | 
|   [C8-1] Prevent premature OutOfMemoryException when G1 GC invocation is suspended by a long-running native call.   |   Programs that use the G1 GC could experience spurious out-of-memory (OOM) exceptions even when the Java heap was far from filling up. This happened when a spin loop that waited for long-running native calls gave up after only two rounds. This small patch makes this loop wait as long as it takes. Typically a few more rounds suffice. Worst case, a full GC would eventually occur (thanks to JDK-8137099) and also resolve the situation. The patch includes a unit test that provokes needing more than two rounds and succeeds only if the patch is in place. See JDK-8137099 for discussion.  |  2018-11-14  | 
|  [C8-2] Back port from OpenJDK 10, fixing JDK-8177809: “File.lastModified() is losing milliseconds (always ends in 000)”.  |  This patch removes inconsistencies in how the last-modified timestamp of a file is reported. It standardizes the behavior across build platforms and Java methods so that the user receives second-level precision.   |  2018-11-14  | 
|   [C8-3] Back port from OpenJDK9, fixing JDK-8150013, “ParNew: Prune nmethods scavengable list”.   |   This patch reduces pause latencies for the Parallel and the CMS garbage collector. GC “root scanning” speeds up by up to three orders of magnitude by reducing redundant code inspections.   |  2018-11-14  | 
|   [C8-4] Back port from OpenJDK 9, fixing JDK-8047338: “javac is not correctly filtering non-members methods to obtain the function descriptor”.   |   This patch fixes a compiler bug that caused compile-time errors when a functional interface threw an exception that extended Exception.   |  2018-11-14  | 
|   [C8-5] Back port from OpenJDK 10, fixing JDK-8144185: “javac produces incorrect RuntimeInvisibleTypeAnnotations length attribute”.   |   This problem made Findbugs, JaCoCo, and Checker Framework fail on some well-formed input programs.   |  2018-11-14  | 
|   [C8-6] Trigger string table cleanup in G1 based on string table growth.   |   This patch triggers “mixed” G1 collections needed to clean out the string table entries based on string table growth, not just Java heap usage. The latter is an independent measurement and can trigger too rarely or even never, in some applications. Then the string table can grow without bounds, which is effectively a native memory leak. See JDK-8213198.  |  2018-11-14  | 
|   [C8-7] Backport from OpenJDK 9, fixing JDK-8149442: “MonitorInUseLists should be on by default, deflate idle monitors taking too long”.   |   This patch makes removing a performance bottleneck for highly thread-intensive applications the default setting. Enabling MonitorInUseLists allows more efficient deflation of only potentially in-use monitors, instead of the entire population of monitors.   |  2018-11-14  | 
|   [C8-8] Back port from OpenJDK 11, fixing JDK-8198794: “Hotspot crash on Cassandra 3.11.1 startup with libnuma 2.0.3”.   |   This patch prevents Cassandra 3.11.1 from crashing at startup.   |  2018-11-14  | 
|   [C8-9] Back port from OpenJDK 11, fixing JDK-8195115: “G1 Old Gen MemoryPool CollectionUsage.used values don't reflect mixed GC results”.   |   Without this patch, it's impossible to determine how full the heap is by means of JMX when using the G1 GC.   |  2018-11-14  | 
|   [C8-10] Speed up Class.getSimpleName() and Class.getCanonicalName().   |   Memorization greatly speeds up these functions. This patch includes correctness unit tests. See JDK-8187123.   |  2018-11-14  | 
|   [C8-11] Back port of JDK-8068736 from OpenJDK9, fixing “Avoid synchronization on Executable/Field.declaredAnnotations”.   |   Improves the performance of Executable/Field.declaredAnnotations() by result caching that avoids thread synchronization.   |  2018-11-14  | 
|   [C8-12] Back port from OpenJDK 9, fixing JDK-8077605: “Initializing static fields causes unbounded recursion in javac”.   |   N/A   |  2018-11-14  | 
|   [C8-13] Fixed JDK-8130493: “javac silently ignores malformed classes in the annotation processor”.   |   javac silently swallowed malformed class files in an annotation processor and returned with exit code 0. With this patch, javac reports an error message and returns with a non-zero exit code.   |  2018-11-14  | 
|   [C8-14] Improved error message for the jmap tool.   |   Suggests additional approaches when the target process is unresponsive. See JDK-8213443.   |  2018-11-14  | 
|   [C8-15] Fixed JDK-8185005: “Improve performance of ThreadMXBean.getThreadInfo(long ids[], int maxDepth)”.   |   This patch improves the performance of a JVM-internal function that looks up a Java Thread instance from an OS thread ID. This benefits several ThreadMXBean calls such as getThreadInfo(), getThreadCpuTime(), and getThreadUserTime(). The relative performance improvement increases with the number of threads in the JVM, as linear search is replaced by a hash table lookup.   |  2018-11-14  | 
|   [C8-16] Back port from OpenJDK 12, fixing JDK-8206075: “On x86, assert on unbound assembler Labels used as branch targets”.   |   Label class instances (used to define pseudo-assembly code) can be used incorrectly in both the C1 and Interpreter. The most common mistake for a label is being "branched to" but never defined as a location in code via bind(). An assert was added to catch these and thus triggered 106 jtreg/hotspot and 17 jtreg/jdk test failures. We then determined that the label backedge_counter_overflow was not bound when UseLoopCounter was True but UseOnStackReplacement was False. This is now fixed and guarded by the above tests.   |  2018-11-14  | 
|   [C8-17] Improve portability of JVM source code when using gcc7.   |   This patch places up-to-date type declarations in all places where the gcc switch “-Wno-deprecated-declarations” would flag problems. It also enables the switch to catch future related issues. This makes the source code compile on all present Amazon Linux versions. This is a combination of much of JDK-8152856, JDK-8184309, JDK-8185826, JDK-8185900, JDK-8187676, JDK-8196909, JDK-8196985, JDK-8199685, JDK-8200052, JDK-8200110, JDK-8209786, JDK-8210836, JDK-8211146, JDK-8211370, JDK-8211929, JDK-8213414, and JDK-8213575.   |  2018-11-14  | 
|   [C8-18] Back port from JDK 10, fixing JDK-8195848: “JTREG test for StartManagementAgent fails”.   |   See [http://serviceability-dev.openjdk.java.narkive.com/cDFwZce9](http://serviceability-dev.openjdk.java.narkive.com/cDFwZce9) for more details.   |  2018-11-14  | 
|   [C8-19] Re-enables a legacy/disabled cipher suite to pass two TCK tests that would otherwise fail.   |   N/A   |  2018-11-14  | 
