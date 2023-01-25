@echo off
REM
REM Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
REM DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
REM
REM This code is free software; you can redistribute it and/or modify it
REM under the terms of the GNU General Public License version 2 only, as
REM published by the Free Software Foundation.
REM
REM This code is distributed in the hope that it will be useful, but WITHOUT
REM ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
REM FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
REM version 2 for more details (a copy is included in the LICENSE file that
REM accompanied this code).
REM
REM You should have received a copy of the GNU General Public License version
REM 2 along with this work; if not, write to the Free Software Foundation,
REM Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
REM
REM Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
REM or visit www.oracle.com if you need additional information or have any
REM questions.
REM  
REM

REM This is the interactive build setup script (as opposed to the batch
REM build execution script). It creates $HotSpotBuildSpace if necessary,
REM copies the appropriate files out of $HotSpotWorkSpace into it, and
REM builds and runs ProjectCreator in it. This has the side-effect of creating
REM the vm.vcproj file in the buildspace, which is then used in Visual C++.

REM
REM Since we don't have uname and we could be cross-compiling,
REM Use the compiler to determine which ARCH we are building
REM 
REM Note: Running this batch file from the Windows command shell requires
REM that "grep" be accessible on the PATH. An MKS install does this.
REM 

cl 2>NUL >NUL
if %errorlevel% == 0 goto nexttest
echo Make sure cl.exe is in your PATH before running this script.
goto end

:nexttest
grep -V 2>NUL >NUL
if %errorlevel% == 0 goto testit
echo Make sure grep.exe is in your PATH before running this script. Either cygwin or MKS should work.
goto end


:testit
cl 2>&1 | grep "x64" >NUL
if %errorlevel% == 0 goto amd64
set ARCH=x86
set BUILDARCH=i486
set Platform_arch=x86
set Platform_arch_model=x86_32
goto done
:amd64
set ARCH=x86
set BUILDARCH=amd64
set Platform_arch=x86
set Platform_arch_model=x86_64
:done

setlocal

if "%1" == "" goto usage

if not "%2" == "" goto usage

REM Set HotSpotWorkSpace to the directy two steps above this script
for %%i in ("%~dp0..") do ( set HotSpotWorkSpace=%%~dpi)
set HotSpotBuildRoot=%HotSpotWorkSpace%build
set HotSpotBuildSpace=%HotSpotBuildRoot%\vs-%BUILDARCH%
set HotSpotJDKDist=%1


REM figure out MSC version
for /F %%i in ('sh %HotSpotWorkSpace%/make/windows/get_msc_ver.sh') do set %%i

echo **************************************************************
set ProjectFile=%HotSpotBuildSpace%\jvm.vcproj
echo MSC_VER = "%MSC_VER%" 
if "%MSC_VER%" == "1200" (
set ProjectFile=%HotSpotBuildSpace%\jvm.dsp
echo Will generate VC6 project {unsupported}
) else (
if "%MSC_VER%" == "1400" (
echo Will generate VC8 {Visual Studio 2005}
) else (
if "%MSC_VER%" == "1500" (
echo Will generate VC9 {Visual Studio 2008}
) else (
if "%MSC_VER%" == "1600" (
echo Will generate VC10 {Visual Studio 2010}
set ProjectFile=%HotSpotBuildSpace%\jvm.vcxproj
) else (
if "%MSC_VER%" == "1700" (
echo Will generate VC10 {compatible with Visual Studio 2012}
echo After opening in VS 2012, click "Update" when prompted.
set ProjectFile=%HotSpotBuildSpace%\jvm.vcxproj
) else (
echo Will generate VC7 project {Visual Studio 2003 .NET}
)
)
)
)
)
echo %ProjectFile%
echo **************************************************************

REM Test all variables to see whether the directories they
REM reference exist

if exist %HotSpotWorkSpace% goto test1

echo Error: directory pointed to by HotSpotWorkSpace
echo does not exist, or the variable is not set.
echo.
goto usage

:test1
if exist %HotSpotBuildSpace% goto test2
if not "%HotSpotBuildSpace%" == "" mkdir %HotSpotBuildSpace%
if exist %HotSpotBuildSpace% goto test2
echo Error: directory pointed to by HotSpotBuildSpace
echo does not exist, or the variable is not set.
echo.
goto usage

:test2
if exist %HotSpotJDKDist% goto test3
echo Error: directory pointed to by %HotSpotJDKDist%
echo does not exist, or the variable is not set.
echo.
goto usage

:test3
if not "%HOTSPOTMKSHOME%" == "" goto makedir
if exist c:\cygwin\bin set HOTSPOTMKSHOME=c:\cygwin\bin
if not "%HOTSPOTMKSHOME%" == "" goto makedir
echo Warning: please set variable HOTSPOTMKSHOME to place where 
echo          your MKS/Cygwin installation is
echo.
goto usage

:makedir
echo NOTE: Using the following settings:
echo   HotSpotWorkSpace=%HotSpotWorkSpace%
echo   HotSpotBuildSpace=%HotSpotBuildSpace%
echo   HotSpotJDKDist=%HotSpotJDKDist%


REM This is now safe to do.
:copyfiles
for /D %%i in (compiler1, compiler2, tiered ) do (
if NOT EXIST %HotSpotBuildSpace%\%%i\generated mkdir %HotSpotBuildSpace%\%%i\generated
copy %HotSpotWorkSpace%\make\windows\projectfiles\%%i\* %HotSpotBuildSpace%\%%i\generated > NUL
)

REM force regneration of ProjectFile
if exist %ProjectFile% del %ProjectFile%

for /D %%i in (compiler1, compiler2, tiered ) do (
echo -- %%i --
echo # Generated file!                                                        >    %HotSpotBuildSpace%\%%i\local.make
echo # Changing a variable below and then deleting %ProjectFile% will cause  >>    %HotSpotBuildSpace%\%%i\local.make
echo # %ProjectFile% to be regenerated with the new values.  Changing the    >>    %HotSpotBuildSpace%\%%i\local.make
echo # version requires rerunning create.bat.                                >>    %HotSpotBuildSpace%\%%i\local.make
echo.                                      >>    %HotSpotBuildSpace%\%%i\local.make
echo Variant=%%i			   >>    %HotSpotBuildSpace%\%%i\local.make
echo WorkSpace=%HotSpotWorkSpace%   	   >>    %HotSpotBuildSpace%\%%i\local.make
echo HOTSPOTWORKSPACE=%HotSpotWorkSpace%   >>    %HotSpotBuildSpace%\%%i\local.make
echo HOTSPOTBUILDROOT=%HotSpotBuildRoot%   >>    %HotSpotBuildSpace%\%%i\local.make
echo HOTSPOTBUILDSPACE=%HotSpotBuildSpace% >>    %HotSpotBuildSpace%\%%i\local.make
echo HOTSPOTJDKDIST=%HotSpotJDKDist%       >>    %HotSpotBuildSpace%\%%i\local.make
echo ARCH=%ARCH%                           >>    %HotSpotBuildSpace%\%%i\local.make
echo BUILDARCH=%BUILDARCH%                 >>    %HotSpotBuildSpace%\%%i\local.make
echo Platform_arch=%Platform_arch%         >>    %HotSpotBuildSpace%\%%i\local.make
echo Platform_arch_model=%Platform_arch_model% >>    %HotSpotBuildSpace%\%%i\local.make
echo MSC_VER=%MSC_VER% 			   >>    %HotSpotBuildSpace%\%%i\local.make

for /D %%j in (debug, fastdebug, product) do (
if NOT EXIST %HotSpotBuildSpace%\%%i\%%j mkdir %HotSpotBuildSpace%\%%i\%%j
)

pushd %HotSpotBuildSpace%\%%i\generated
nmake /nologo
popd

)

pushd %HotSpotBuildRoot%

REM It doesn't matter which variant we use here, "compiler1" is as good as any of the others - we need the common variables
nmake /nologo /F %HotSpotWorkSpace%/make/windows/projectfiles/common/Makefile LOCAL_MAKE=%HotSpotBuildSpace%\compiler1\local.make %ProjectFile%

popd

goto end

:usage
echo Usage: create HotSpotJDKDist
echo.
echo This is the VS build setup script (as opposed to the batch
echo build execution script). It creates a build directory if necessary,
echo copies the appropriate files out of the workspace into it, and
echo builds and runs ProjectCreator in it. This has the side-effect of creating
echo the %ProjectFile% file in the build space, which is then used in Visual C++.
echo.
echo The HotSpotJDKDist defines the JDK that should be used when running the JVM.
echo Environment variable FORCE_MSC_VER allows to override MSVC version autodetection.
echo.
echo NOTE that it is now NOT safe to modify any of the files in the build
echo space, since they may be overwritten whenever this script is run or
echo nmake is run in that directory.

:end

endlocal
