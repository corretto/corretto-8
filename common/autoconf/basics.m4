#
# Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# Test if $1 is a valid argument to $3 (often is $JAVA passed as $3)
# If so, then append $1 to $2 \
# Also set JVM_ARG_OK to true/false depending on outcome.
AC_DEFUN([ADD_JVM_ARG_IF_OK],
[
  $ECHO "Check if jvm arg is ok: $1" >&AS_MESSAGE_LOG_FD
  $ECHO "Command: $3 $1 -version" >&AS_MESSAGE_LOG_FD
  OUTPUT=`$3 $1 -version 2>&1`
  FOUND_WARN=`$ECHO "$OUTPUT" | grep -i warn`
  FOUND_VERSION=`$ECHO $OUTPUT | grep " version \""`
  if test "x$FOUND_VERSION" != x && test "x$FOUND_WARN" = x; then
    $2="[$]$2 $1"
    JVM_ARG_OK=true
  else
    $ECHO "Arg failed:" >&AS_MESSAGE_LOG_FD
    $ECHO "$OUTPUT" >&AS_MESSAGE_LOG_FD
    JVM_ARG_OK=false
  fi
])

# Appends a string to a path variable, only adding the : when needed.
AC_DEFUN([BASIC_APPEND_TO_PATH],
[
  if test "x$2" != x; then
    if test "x[$]$1" = x; then
      $1="$2"
    else
      $1="[$]$1:$2"
    fi
  fi
])

# Prepends a string to a path variable, only adding the : when needed.
AC_DEFUN([BASIC_PREPEND_TO_PATH],
[
  if test "x$2" != x; then
    if test "x[$]$1" = x; then
      $1="$2"
    else
      $1="$2:[$]$1"
    fi
  fi
])

# This will make sure the given variable points to a full and proper
# path. This means:
# 1) There will be no spaces in the path. On posix platforms,
#    spaces in the path will result in an error. On Windows,
#    the path will be rewritten using short-style to be space-free.
# 2) The path will be absolute, and it will be in unix-style (on
#     cygwin).
# $1: The name of the variable to fix
AC_DEFUN([BASIC_FIXUP_PATH],
[
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    BASIC_FIXUP_PATH_CYGWIN($1)
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
    BASIC_FIXUP_PATH_MSYS($1)
  else
    # We're on a posix platform. Hooray! :)
    path="[$]$1"
    has_space=`$ECHO "$path" | $GREP " "`
    if test "x$has_space" != x; then
      AC_MSG_NOTICE([The path of $1, which resolves as "$path", is invalid.])
      AC_MSG_ERROR([Spaces are not allowed in this path.])
    fi

    # Use eval to expand a potential ~
    eval path="$path"
    if test ! -f "$path" && test ! -d "$path"; then
      AC_MSG_ERROR([The path of $1, which resolves as "$path", is not found.])
    fi

    $1="`cd "$path"; $THEPWDCMD -L`"
  fi
])

# This will make sure the given variable points to a executable
# with a full and proper path. This means:
# 1) There will be no spaces in the path. On posix platforms,
#    spaces in the path will result in an error. On Windows,
#    the path will be rewritten using short-style to be space-free.
# 2) The path will be absolute, and it will be in unix-style (on
#     cygwin).
# Any arguments given to the executable is preserved.
# If the input variable does not have a directory specification, then
# it need to be in the PATH.
# $1: The name of the variable to fix
AC_DEFUN([BASIC_FIXUP_EXECUTABLE],
[
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    BASIC_FIXUP_EXECUTABLE_CYGWIN($1)
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
    BASIC_FIXUP_EXECUTABLE_MSYS($1)
  else
    # We're on a posix platform. Hooray! :)
    # First separate the path from the arguments. This will split at the first
    # space.
    complete="[$]$1"
    path="${complete%% *}"
    tmp="$complete EOL"
    arguments="${tmp#* }"

    # Cannot rely on the command "which" here since it doesn't always work.
    is_absolute_path=`$ECHO "$path" | $GREP ^/`
    if test -z "$is_absolute_path"; then
      # Path to executable is not absolute. Find it.
      IFS_save="$IFS"
      IFS=:
      for p in $PATH; do
        if test -f "$p/$path" && test -x "$p/$path"; then
          new_path="$p/$path"
          break
        fi
      done
      IFS="$IFS_save"
    else
      # This is an absolute path, we can use it without further modifications.
      new_path="$path"
    fi

    if test "x$new_path" = x; then
      AC_MSG_NOTICE([The path of $1, which resolves as "$complete", is not found.])
      has_space=`$ECHO "$complete" | $GREP " "`
      if test "x$has_space" != x; then
        AC_MSG_NOTICE([This might be caused by spaces in the path, which is not allowed.])
      fi
      AC_MSG_ERROR([Cannot locate the the path of $1])
    fi
  fi

  # Now join together the path and the arguments once again
  if test "x$arguments" != xEOL; then
    new_complete="$new_path ${arguments% *}"
  else
    new_complete="$new_path"
  fi

  if test "x$complete" != "x$new_complete"; then
    $1="$new_complete"
    AC_MSG_NOTICE([Rewriting $1 to "$new_complete"])
  fi
])

AC_DEFUN([BASIC_REMOVE_SYMBOLIC_LINKS],
[
  if test "x$OPENJDK_BUILD_OS" != xwindows; then
    # Follow a chain of symbolic links. Use readlink
    # where it exists, else fall back to horribly
    # complicated shell code.
    if test "x$READLINK_TESTED" != yes; then
      # On MacOSX there is a readlink tool with a different
      # purpose than the GNU readlink tool. Check the found readlink.
      ISGNU=`$READLINK --version 2>&1 | $GREP GNU`
      if test "x$ISGNU" = x; then
        # A readlink that we do not know how to use.
        # Are there other non-GNU readlinks out there?
        READLINK_TESTED=yes
        READLINK=
      fi
    fi

    if test "x$READLINK" != x; then
      $1=`$READLINK -f [$]$1`
    else
      # Save the current directory for restoring afterwards
      STARTDIR=$PWD
      COUNTER=0
      sym_link_dir=`$DIRNAME [$]$1`
      sym_link_file=`$BASENAME [$]$1`
      cd $sym_link_dir
      # Use -P flag to resolve symlinks in directories.
      cd `$THEPWDCMD -P`
      sym_link_dir=`$THEPWDCMD -P`
      # Resolve file symlinks
      while test $COUNTER -lt 20; do
        ISLINK=`$LS -l $sym_link_dir/$sym_link_file | $GREP '\->' | $SED -e 's/.*-> \(.*\)/\1/'`
        if test "x$ISLINK" == x; then
          # This is not a symbolic link! We are done!
          break
        fi
        # Again resolve directory symlinks since the target of the just found
        # link could be in a different directory
        cd `$DIRNAME $ISLINK`
        sym_link_dir=`$THEPWDCMD -P`
        sym_link_file=`$BASENAME $ISLINK`
        let COUNTER=COUNTER+1
      done
      cd $STARTDIR
      $1=$sym_link_dir/$sym_link_file
    fi
  fi
])

# Register a --with argument but mark it as deprecated
# $1: The name of the with argument to deprecate, not including --with-
AC_DEFUN([BASIC_DEPRECATED_ARG_WITH],
[
  AC_ARG_WITH($1, [AS_HELP_STRING([--with-$1],
      [Deprecated. Option is kept for backwards compatibility and is ignored])],
      [AC_MSG_WARN([Option --with-$1 is deprecated and will be ignored.])])
])

# Register a --enable argument but mark it as deprecated
# $1: The name of the with argument to deprecate, not including --enable-
# $2: The name of the argument to deprecate, in shell variable style (i.e. with _ instead of -)
AC_DEFUN([BASIC_DEPRECATED_ARG_ENABLE],
[
  AC_ARG_ENABLE($1, [AS_HELP_STRING([--enable-$1],
      [Deprecated. Option is kept for backwards compatibility and is ignored])])
  if test "x$enable_$2" != x; then
    AC_MSG_WARN([Option --enable-$1 is deprecated and will be ignored.])
  fi
])

AC_DEFUN_ONCE([BASIC_INIT],
[
  # Save the original command line. This is passed to us by the wrapper configure script.
  AC_SUBST(CONFIGURE_COMMAND_LINE)
  DATE_WHEN_CONFIGURED=`LANG=C date`
  AC_SUBST(DATE_WHEN_CONFIGURED)
  AC_MSG_NOTICE([Configuration created at $DATE_WHEN_CONFIGURED.])
  AC_MSG_NOTICE([configure script generated at timestamp $DATE_WHEN_GENERATED.])
])

# Test that variable $1 denoting a program is not empty. If empty, exit with an error.
# $1: variable to check
AC_DEFUN([BASIC_CHECK_NONEMPTY],
[
  if test "x[$]$1" = x; then
    AC_MSG_ERROR([Could not find required tool for $1])
  fi
])

# Check that there are no unprocessed overridden variables left.
# If so, they are an incorrect argument and we will exit with an error.
AC_DEFUN([BASIC_CHECK_LEFTOVER_OVERRIDDEN],
[
  if test "x$CONFIGURE_OVERRIDDEN_VARIABLES" != x; then
    # Replace the separating ! with spaces before presenting for end user.
    unknown_variables=${CONFIGURE_OVERRIDDEN_VARIABLES//!/ }
    AC_MSG_WARN([The following variables might be unknown to configure: $unknown_variables])
  fi
])

# Setup a tool for the given variable. If correctly specified by the user, 
# use that value, otherwise search for the tool using the supplied code snippet.
# $1: variable to set
# $2: code snippet to call to look for the tool
AC_DEFUN([BASIC_SETUP_TOOL],
[
  # Publish this variable in the help.
  AC_ARG_VAR($1, [Override default value for $1])

  if test "x[$]$1" = x; then
    # The variable is not set by user, try to locate tool using the code snippet
    $2
  else
    # The variable is set, but is it from the command line or the environment?

    # Try to remove the string !$1! from our list.
    try_remove_var=${CONFIGURE_OVERRIDDEN_VARIABLES//!$1!/}
    if test "x$try_remove_var" = "x$CONFIGURE_OVERRIDDEN_VARIABLES"; then
      # If it failed, the variable was not from the command line. Ignore it,
      # but warn the user (except for BASH, which is always set by the calling BASH).
      if test "x$1" != xBASH; then
        AC_MSG_WARN([Ignoring value of $1 from the environment. Use command line variables instead.])
      fi
      # Try to locate tool using the code snippet
      $2
    else
      # If it succeeded, then it was overridden by the user. We will use it
      # for the tool.

      # First remove it from the list of overridden variables, so we can test
      # for unknown variables in the end.
      CONFIGURE_OVERRIDDEN_VARIABLES="$try_remove_var"

      # Check if the provided tool contains a complete path.
      tool_specified="[$]$1"
      tool_basename="${tool_specified##*/}"
      if test "x$tool_basename" = "x$tool_specified"; then
        # A command without a complete path is provided, search $PATH.
        AC_MSG_NOTICE([Will search for user supplied tool $1=$tool_basename])
        AC_PATH_PROG($1, $tool_basename)
        if test "x[$]$1" = x; then
          AC_MSG_ERROR([User supplied tool $tool_basename could not be found])
        fi
      else
        # Otherwise we believe it is a complete path. Use it as it is.
        AC_MSG_NOTICE([Will use user supplied tool $1=$tool_specified])
        AC_MSG_CHECKING([for $1])
        if test ! -x "$tool_specified"; then
          AC_MSG_RESULT([not found])
          AC_MSG_ERROR([User supplied tool $1=$tool_specified does not exist or is not executable])
        fi
        AC_MSG_RESULT([$tool_specified])
      fi
    fi
  fi
])

# Call BASIC_SETUP_TOOL with AC_PATH_PROGS to locate the tool
# $1: variable to set
# $2: executable name (or list of names) to look for
AC_DEFUN([BASIC_PATH_PROGS],
[
  BASIC_SETUP_TOOL($1, [AC_PATH_PROGS($1, $2)])
])

# Call BASIC_SETUP_TOOL with AC_CHECK_TOOLS to locate the tool
# $1: variable to set
# $2: executable name (or list of names) to look for
AC_DEFUN([BASIC_CHECK_TOOLS],
[
  BASIC_SETUP_TOOL($1, [AC_CHECK_TOOLS($1, $2)])
])

# Like BASIC_PATH_PROGS but fails if no tool was found.
# $1: variable to set
# $2: executable name (or list of names) to look for
AC_DEFUN([BASIC_REQUIRE_PROGS],
[
  BASIC_PATH_PROGS($1, $2)
  BASIC_CHECK_NONEMPTY($1)
])

# Like BASIC_SETUP_TOOL but fails if no tool was found.
# $1: variable to set
# $2: autoconf macro to call to look for the special tool
AC_DEFUN([BASIC_REQUIRE_SPECIAL],
[
  BASIC_SETUP_TOOL($1, [$2])
  BASIC_CHECK_NONEMPTY($1)
])

# Setup the most fundamental tools that relies on not much else to set up,
# but is used by much of the early bootstrap code.
AC_DEFUN_ONCE([BASIC_SETUP_FUNDAMENTAL_TOOLS],
[
  # Start with tools that do not need have cross compilation support
  # and can be expected to be found in the default PATH. These tools are
  # used by configure. Nor are these tools expected to be found in the
  # devkit from the builddeps server either, since they are
  # needed to download the devkit.

  # First are all the simple required tools.
  BASIC_REQUIRE_PROGS(BASENAME, basename)
  BASIC_REQUIRE_PROGS(BASH, bash)
  BASIC_REQUIRE_PROGS(CAT, cat)
  BASIC_REQUIRE_PROGS(CHMOD, chmod)
  BASIC_REQUIRE_PROGS(CMP, cmp)
  BASIC_REQUIRE_PROGS(COMM, comm)
  BASIC_REQUIRE_PROGS(CP, cp)
  BASIC_REQUIRE_PROGS(CUT, cut)
  BASIC_REQUIRE_PROGS(DATE, date)
  BASIC_REQUIRE_PROGS(DIFF, [gdiff diff])
  BASIC_REQUIRE_PROGS(DIRNAME, dirname)
  BASIC_REQUIRE_PROGS(ECHO, echo)
  BASIC_REQUIRE_PROGS(EXPR, expr)
  BASIC_REQUIRE_PROGS(FILE, file)
  BASIC_REQUIRE_PROGS(FIND, find)
  BASIC_REQUIRE_PROGS(HEAD, head)
  BASIC_REQUIRE_PROGS(LN, ln)
  BASIC_REQUIRE_PROGS(LS, ls)
  BASIC_REQUIRE_PROGS(MKDIR, mkdir)
  BASIC_REQUIRE_PROGS(MKTEMP, mktemp)
  BASIC_REQUIRE_PROGS(MV, mv)
  BASIC_REQUIRE_PROGS(NAWK, [nawk gawk awk])
  BASIC_REQUIRE_PROGS(PRINTF, printf)
  BASIC_REQUIRE_PROGS(RM, rm)
  BASIC_REQUIRE_PROGS(SH, sh)
  BASIC_REQUIRE_PROGS(SORT, sort)
  BASIC_REQUIRE_PROGS(TAIL, tail)
  BASIC_REQUIRE_PROGS(TAR, tar)
  BASIC_REQUIRE_PROGS(TEE, tee)
  BASIC_REQUIRE_PROGS(TOUCH, touch)
  BASIC_REQUIRE_PROGS(TR, tr)
  BASIC_REQUIRE_PROGS(UNAME, uname)
  BASIC_REQUIRE_PROGS(UNIQ, uniq)
  BASIC_REQUIRE_PROGS(WC, wc)
  BASIC_REQUIRE_PROGS(WHICH, which)
  BASIC_REQUIRE_PROGS(XARGS, xargs)

  # Then required tools that require some special treatment.
  BASIC_REQUIRE_SPECIAL(AWK, [AC_PROG_AWK])
  BASIC_REQUIRE_SPECIAL(GREP, [AC_PROG_GREP])
  BASIC_REQUIRE_SPECIAL(EGREP, [AC_PROG_EGREP])
  BASIC_REQUIRE_SPECIAL(FGREP, [AC_PROG_FGREP])
  BASIC_REQUIRE_SPECIAL(SED, [AC_PROG_SED])

  # Always force rm.
  RM="$RM -f"

  # pwd behaves differently on various platforms and some don't support the -L flag.
  # Always use the bash builtin pwd to get uniform behavior.
  THEPWDCMD=pwd

  # These are not required on all platforms
  BASIC_PATH_PROGS(CYGPATH, cygpath)
  BASIC_PATH_PROGS(READLINK, [greadlink readlink])
  BASIC_PATH_PROGS(DF, df)
  BASIC_PATH_PROGS(SETFILE, SetFile)
  BASIC_PATH_PROGS(CPIO, [cpio bsdcpio])
])

# Setup basic configuration paths, and platform-specific stuff related to PATHs.
AC_DEFUN_ONCE([BASIC_SETUP_PATHS],
[
  # Save the current directory this script was started from
  CURDIR="$PWD"

  if test "x$OPENJDK_TARGET_OS" = "xwindows"; then
    PATH_SEP=";"
    BASIC_CHECK_PATHS_WINDOWS
  else
    PATH_SEP=":"
  fi
  AC_SUBST(PATH_SEP)

  # We get the top-level directory from the supporting wrappers.
  AC_MSG_CHECKING([for top-level directory])
  AC_MSG_RESULT([$TOPDIR])
  AC_SUBST(TOPDIR)

  # We can only call BASIC_FIXUP_PATH after BASIC_CHECK_PATHS_WINDOWS.
  BASIC_FIXUP_PATH(CURDIR)
  BASIC_FIXUP_PATH(TOPDIR)
  # SRC_ROOT is a traditional alias for TOPDIR.
  SRC_ROOT=$TOPDIR

  # Locate the directory of this script.
  AUTOCONF_DIR=$TOPDIR/common/autoconf
])

# Evaluates platform specific overrides for devkit variables.
# $1: Name of variable
AC_DEFUN([BASIC_EVAL_DEVKIT_VARIABLE],
[
  if test "x[$]$1" = x; then
    eval $1="\${$1_${OPENJDK_TARGET_CPU}}"
  fi
])

AC_DEFUN_ONCE([BASIC_SETUP_DEVKIT],
[
  AC_ARG_WITH([devkit], [AS_HELP_STRING([--with-devkit],
      [use this devkit for compilers, tools and resources])])

  if test "x$with_devkit" = xyes; then
    AC_MSG_ERROR([--with-devkit must have a value])
  elif test "x$with_devkit" != x && test "x$with_devkit" != xno; then
    BASIC_FIXUP_PATH([with_devkit])
    DEVKIT_ROOT="$with_devkit"
    # Check for a meta data info file in the root of the devkit
    if test -f "$DEVKIT_ROOT/devkit.info"; then
      . $DEVKIT_ROOT/devkit.info
      # This potentially sets the following:
      # A descriptive name of the devkit
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_NAME])
      # Corresponds to --with-extra-path
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_EXTRA_PATH])
      # Corresponds to --with-toolchain-path
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_TOOLCHAIN_PATH])
      # Corresponds to --with-sysroot
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_SYSROOT])

      # Identifies the Visual Studio version in the devkit
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_VS_VERSION])
      # The Visual Studio include environment variable
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_VS_INCLUDE])
      # The Visual Studio lib environment variable
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_VS_LIB])
      # Corresponds to --with-msvcr-dll
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_MSVCR_DLL])
      # Corresponds to --with-msvcp-dll
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_MSVCP_DLL])
      # Corresponds to --with-ucrt-dll-dir
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_UCRT_DLL_DIR])
    fi

    AC_MSG_CHECKING([for devkit])
    if test "x$DEVKIT_NAME" != x; then
      AC_MSG_RESULT([$DEVKIT_NAME in $DEVKIT_ROOT])
    else
      AC_MSG_RESULT([$DEVKIT_ROOT])
    fi

    BASIC_PREPEND_TO_PATH([EXTRA_PATH],$DEVKIT_EXTRA_PATH)

    # Fallback default of just /bin if DEVKIT_PATH is not defined
    if test "x$DEVKIT_TOOLCHAIN_PATH" = x; then
      DEVKIT_TOOLCHAIN_PATH="$DEVKIT_ROOT/bin"
    fi
    BASIC_PREPEND_TO_PATH([TOOLCHAIN_PATH],$DEVKIT_TOOLCHAIN_PATH)

    # If DEVKIT_SYSROOT is set, use that, otherwise try a couple of known
    # places for backwards compatiblity.
    if test "x$DEVKIT_SYSROOT" != x; then
      SYSROOT="$DEVKIT_SYSROOT"
    elif test -d "$DEVKIT_ROOT/$host_alias/libc"; then
      SYSROOT="$DEVKIT_ROOT/$host_alias/libc"
    elif test -d "$DEVKIT_ROOT/$host/sys-root"; then
      SYSROOT="$DEVKIT_ROOT/$host/sys-root"
    fi

    if test "x$DEVKIT_ROOT" != x; then
      DEVKIT_LIB_DIR="$DEVKIT_ROOT/lib"
      if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
        DEVKIT_LIB_DIR="$DEVKIT_ROOT/lib64"
      fi
      AC_SUBST(DEVKIT_LIB_DIR)
    fi
  fi

  # You can force the sysroot if the sysroot encoded into the compiler tools
  # is not correct.
  AC_ARG_WITH(sys-root, [AS_HELP_STRING([--with-sys-root],
      [alias for --with-sysroot for backwards compatability])],
      [SYSROOT=$with_sys_root]
  )

  AC_ARG_WITH(sysroot, [AS_HELP_STRING([--with-sysroot],
      [use this directory as sysroot)])],
      [SYSROOT=$with_sysroot]
  )

  AC_ARG_WITH([tools-dir], [AS_HELP_STRING([--with-tools-dir],
      [alias for --with-toolchain-path for backwards compatibility])],
      [BASIC_PREPEND_TO_PATH([TOOLCHAIN_PATH],$with_tools_dir)]
  )

  AC_ARG_WITH([toolchain-path], [AS_HELP_STRING([--with-toolchain-path],
      [prepend these directories when searching for toolchain binaries (compilers etc)])],
      [BASIC_PREPEND_TO_PATH([TOOLCHAIN_PATH],$with_toolchain_path)]
  )

  AC_ARG_WITH([extra-path], [AS_HELP_STRING([--with-extra-path],
      [prepend these directories to the default path])],
      [BASIC_PREPEND_TO_PATH([EXTRA_PATH],$with_extra_path)]
  )

  # Prepend the extra path to the global path
  BASIC_PREPEND_TO_PATH([PATH],$EXTRA_PATH)

  if test "x$OPENJDK_BUILD_OS" = "xsolaris"; then
    # Add extra search paths on solaris for utilities like ar and as etc...
    PATH="$PATH:/usr/ccs/bin:/usr/sfw/bin:/opt/csw/bin"
  fi

  # Xcode version will be validated later
  AC_ARG_WITH([xcode-path], [AS_HELP_STRING([--with-xcode-path],
      [explicit path to Xcode application (generally for building on 10.9 and later)])],
      [XCODE_PATH=$with_xcode_path]
  )

  AC_MSG_CHECKING([for sysroot])
  AC_MSG_RESULT([$SYSROOT])
  AC_MSG_CHECKING([for toolchain path])
  AC_MSG_RESULT([$TOOLCHAIN_PATH])
  AC_MSG_CHECKING([for extra path])
  AC_MSG_RESULT([$EXTRA_PATH])
])

AC_DEFUN_ONCE([BASIC_SETUP_OUTPUT_DIR],
[

  AC_ARG_WITH(conf-name, [AS_HELP_STRING([--with-conf-name],
      [use this as the name of the configuration @<:@generated from important configuration options@:>@])],
      [ CONF_NAME=${with_conf_name} ])

  # Test from where we are running configure, in or outside of src root.
  AC_MSG_CHECKING([where to store configuration])
  if test "x$CURDIR" = "x$SRC_ROOT" || test "x$CURDIR" = "x$SRC_ROOT/common" \
      || test "x$CURDIR" = "x$SRC_ROOT/common/autoconf" \
      || test "x$CURDIR" = "x$SRC_ROOT/make" ; then
    # We are running configure from the src root.
    # Create a default ./build/target-variant-debuglevel output root.
    if test "x${CONF_NAME}" = x; then
      AC_MSG_RESULT([in default location])
      CONF_NAME="${OPENJDK_TARGET_OS}-${OPENJDK_TARGET_CPU}-${JDK_VARIANT}-${ANDED_JVM_VARIANTS}-${DEBUG_LEVEL}"
    else
      AC_MSG_RESULT([in build directory with custom name])
    fi
    OUTPUT_ROOT="$SRC_ROOT/build/${CONF_NAME}"
    $MKDIR -p "$OUTPUT_ROOT"
    if test ! -d "$OUTPUT_ROOT"; then
      AC_MSG_ERROR([Could not create build directory $OUTPUT_ROOT])
    fi
  else
    # We are running configure from outside of the src dir.
    # Then use the current directory as output dir!
    # If configuration is situated in normal build directory, just use the build
    # directory name as configuration name, otherwise use the complete path.
    if test "x${CONF_NAME}" = x; then
      CONF_NAME=`$ECHO $CURDIR | $SED -e "s!^${SRC_ROOT}/build/!!"`
    fi
    OUTPUT_ROOT="$CURDIR"
    AC_MSG_RESULT([in current directory])

    # WARNING: This might be a bad thing to do. You need to be sure you want to
    # have a configuration in this directory. Do some sanity checks!

    if test ! -e "$OUTPUT_ROOT/spec.gmk"; then
      # If we have a spec.gmk, we have run here before and we are OK. Otherwise, check for
      # other files
      files_present=`$LS $OUTPUT_ROOT`
      # Configure has already touched config.log and confdefs.h in the current dir when this check
      # is performed.
      filtered_files=`$ECHO "$files_present" \
          | $SED -e 's/config.log//g' \
              -e 's/configure.log//g' \
              -e 's/confdefs.h//g' \
              -e 's/ //g' \
          | $TR -d '\n'`
      if test "x$filtered_files" != x; then
        AC_MSG_NOTICE([Current directory is $CURDIR.])
        AC_MSG_NOTICE([Since this is not the source root, configure will output the configuration here])
        AC_MSG_NOTICE([(as opposed to creating a configuration in <src_root>/build/<conf-name>).])
        AC_MSG_NOTICE([However, this directory is not empty. This is not allowed, since it could])
        AC_MSG_NOTICE([seriously mess up just about everything.])
        AC_MSG_NOTICE([Try 'cd $SRC_ROOT' and restart configure])
        AC_MSG_NOTICE([(or create a new empty directory and cd to it).])
        AC_MSG_ERROR([Will not continue creating configuration in $CURDIR])
      fi
    fi
  fi
  AC_MSG_CHECKING([what configuration name to use])
  AC_MSG_RESULT([$CONF_NAME])

  BASIC_FIXUP_PATH(OUTPUT_ROOT)

  AC_SUBST(SPEC, $OUTPUT_ROOT/spec.gmk)
  AC_SUBST(CONF_NAME, $CONF_NAME)
  AC_SUBST(OUTPUT_ROOT, $OUTPUT_ROOT)

  # Most of the probed defines are put into config.h
  AC_CONFIG_HEADERS([$OUTPUT_ROOT/config.h:$AUTOCONF_DIR/config.h.in])
  # The spec.gmk file contains all variables for the make system.
  AC_CONFIG_FILES([$OUTPUT_ROOT/spec.gmk:$AUTOCONF_DIR/spec.gmk.in])
  # The hotspot-spec.gmk file contains legacy variables for the hotspot make system.
  AC_CONFIG_FILES([$OUTPUT_ROOT/hotspot-spec.gmk:$AUTOCONF_DIR/hotspot-spec.gmk.in])
  # The bootcycle-spec.gmk file contains support for boot cycle builds.
  AC_CONFIG_FILES([$OUTPUT_ROOT/bootcycle-spec.gmk:$AUTOCONF_DIR/bootcycle-spec.gmk.in])
  # The compare.sh is used to compare the build output to other builds.
  AC_CONFIG_FILES([$OUTPUT_ROOT/compare.sh:$AUTOCONF_DIR/compare.sh.in])
  # Spec.sh is currently used by compare-objects.sh
  AC_CONFIG_FILES([$OUTPUT_ROOT/spec.sh:$AUTOCONF_DIR/spec.sh.in])
  # The generated Makefile knows where the spec.gmk is and where the source is.
  # You can run make from the OUTPUT_ROOT, or from the top-level Makefile
  # which will look for generated configurations
  AC_CONFIG_FILES([$OUTPUT_ROOT/Makefile:$AUTOCONF_DIR/Makefile.in])
])

AC_DEFUN_ONCE([BASIC_SETUP_LOGGING],
[
  # Setup default logging of stdout and stderr to build.log in the output root.
  BUILD_LOG='$(OUTPUT_ROOT)/build.log'
  BUILD_LOG_PREVIOUS='$(OUTPUT_ROOT)/build.log.old'
  BUILD_LOG_WRAPPER='$(BASH) $(SRC_ROOT)/common/bin/logger.sh $(BUILD_LOG)'
  AC_SUBST(BUILD_LOG)
  AC_SUBST(BUILD_LOG_PREVIOUS)
  AC_SUBST(BUILD_LOG_WRAPPER)
])


#%%% Simple tools %%%

# Check if we have found a usable version of make
# $1: the path to a potential make binary (or empty)
# $2: the description on how we found this
AC_DEFUN([BASIC_CHECK_MAKE_VERSION],
[
  MAKE_CANDIDATE="$1"
  DESCRIPTION="$2"
  if test "x$MAKE_CANDIDATE" != x; then
    AC_MSG_NOTICE([Testing potential make at $MAKE_CANDIDATE, found using $DESCRIPTION])
    MAKE_VERSION_STRING=`$MAKE_CANDIDATE --version | $HEAD -n 1`
    IS_GNU_MAKE=`$ECHO $MAKE_VERSION_STRING | $GREP 'GNU Make'`
    if test "x$IS_GNU_MAKE" = x; then
      AC_MSG_NOTICE([Found potential make at $MAKE_CANDIDATE, however, this is not GNU Make. Ignoring.])
    else
      IS_MODERN_MAKE=`$ECHO $MAKE_VERSION_STRING | $GREP -e '3\.8[[12]]' -e '4\.'`
      if test "x$IS_MODERN_MAKE" = x; then
        AC_MSG_NOTICE([Found GNU make at $MAKE_CANDIDATE, however this is not version 3.81 or later. (it is: $MAKE_VERSION_STRING). Ignoring.])
      else
        if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
          if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
            MAKE_EXPECTED_ENV='cygwin'
          elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
            MAKE_EXPECTED_ENV='msys'
          else
            AC_MSG_ERROR([Unknown Windows environment])
          fi
          MAKE_BUILT_FOR=`$MAKE_CANDIDATE --version | $GREP -i 'built for'`
          IS_MAKE_CORRECT_ENV=`$ECHO $MAKE_BUILT_FOR | $GREP $MAKE_EXPECTED_ENV`
        else
          # Not relevant for non-Windows
          IS_MAKE_CORRECT_ENV=true
        fi
        if test "x$IS_MAKE_CORRECT_ENV" = x; then
          AC_MSG_NOTICE([Found GNU make version $MAKE_VERSION_STRING at $MAKE_CANDIDATE, but it is not for $MAKE_EXPECTED_ENV (it says: $MAKE_BUILT_FOR). Ignoring.])
        else
          FOUND_MAKE=$MAKE_CANDIDATE
          BASIC_FIXUP_EXECUTABLE(FOUND_MAKE)
        fi
      fi
    fi
  fi
])

# Goes looking for a usable version of GNU make.
AC_DEFUN([BASIC_CHECK_GNU_MAKE],
[
  # We need to find a recent version of GNU make. Especially on Solaris, this can be tricky.
  if test "x$MAKE" != x; then
    # User has supplied a make, test it.
    if test ! -f "$MAKE"; then
      AC_MSG_ERROR([The specified make (by MAKE=$MAKE) is not found.])
    fi
    BASIC_CHECK_MAKE_VERSION("$MAKE", [user supplied MAKE=$MAKE])
    if test "x$FOUND_MAKE" = x; then
      AC_MSG_ERROR([The specified make (by MAKE=$MAKE) is not GNU make 3.81 or newer.])
    fi
  else
    # Try our hardest to locate a correct version of GNU make
    AC_PATH_PROGS(CHECK_GMAKE, gmake)
    BASIC_CHECK_MAKE_VERSION("$CHECK_GMAKE", [gmake in PATH])

    if test "x$FOUND_MAKE" = x; then
      AC_PATH_PROGS(CHECK_MAKE, make)
      BASIC_CHECK_MAKE_VERSION("$CHECK_MAKE", [make in PATH])
    fi

    if test "x$FOUND_MAKE" = x; then
      if test "x$TOOLCHAIN_PATH" != x; then
        # We have a toolchain path, check that as well before giving up.
        OLD_PATH=$PATH
        PATH=$TOOLCHAIN_PATH:$PATH
        AC_PATH_PROGS(CHECK_TOOLSDIR_GMAKE, gmake)
        BASIC_CHECK_MAKE_VERSION("$CHECK_TOOLSDIR_GMAKE", [gmake in tools-dir])
        if test "x$FOUND_MAKE" = x; then
          AC_PATH_PROGS(CHECK_TOOLSDIR_MAKE, make)
          BASIC_CHECK_MAKE_VERSION("$CHECK_TOOLSDIR_MAKE", [make in tools-dir])
        fi
        PATH=$OLD_PATH
      fi
    fi

    if test "x$FOUND_MAKE" = x; then
      AC_MSG_ERROR([Cannot find GNU make 3.81 or newer! Please put it in the path, or add e.g. MAKE=/opt/gmake3.81/make as argument to configure.])
    fi
  fi

  MAKE=$FOUND_MAKE
  AC_SUBST(MAKE)
  AC_MSG_NOTICE([Using GNU make 3.81 (or later) at $FOUND_MAKE (version: $MAKE_VERSION_STRING)])
])

AC_DEFUN([BASIC_CHECK_FIND_DELETE],
[
  # Test if find supports -delete
  AC_MSG_CHECKING([if find supports -delete])
  FIND_DELETE="-delete"

  DELETEDIR=`$MKTEMP -d tmp.XXXXXXXXXX` || (echo Could not create temporary directory!; exit $?)

  echo Hejsan > $DELETEDIR/TestIfFindSupportsDelete

  TEST_DELETE=`$FIND "$DELETEDIR" -name TestIfFindSupportsDelete $FIND_DELETE 2>&1`
  if test -f $DELETEDIR/TestIfFindSupportsDelete; then
    # No, it does not.
    rm $DELETEDIR/TestIfFindSupportsDelete
    FIND_DELETE="-exec rm \{\} \+"
    AC_MSG_RESULT([no])
  else
    AC_MSG_RESULT([yes])
  fi
  rmdir $DELETEDIR
  AC_SUBST(FIND_DELETE)
])

AC_DEFUN_ONCE([BASIC_SETUP_COMPLEX_TOOLS],
[
  BASIC_CHECK_GNU_MAKE

  BASIC_CHECK_FIND_DELETE

  # These tools might not be installed by default,
  # need hint on how to install them.
  BASIC_REQUIRE_PROGS(UNZIP, unzip)
  BASIC_REQUIRE_PROGS(ZIP, zip)

  # Non-required basic tools

  BASIC_PATH_PROGS(LDD, ldd)
  if test "x$LDD" = "x"; then
    # List shared lib dependencies is used for
    # debug output and checking for forbidden dependencies.
    # We can build without it.
    LDD="true"
  fi
  BASIC_PATH_PROGS(READELF, [readelf greadelf])
  BASIC_PATH_PROGS(HG, hg)
  BASIC_PATH_PROGS(GIT, git)
  BASIC_PATH_PROGS(STAT, stat)
  BASIC_PATH_PROGS(TIME, time)
  # Check if it's GNU time
  IS_GNU_TIME=`$TIME --version 2>&1 | $GREP 'GNU time'`
  if test "x$IS_GNU_TIME" != x; then
    IS_GNU_TIME=yes
  else
    IS_GNU_TIME=no
  fi
  AC_SUBST(IS_GNU_TIME)

  if test "x$OPENJDK_TARGET_OS" = "xwindows"; then
    BASIC_REQUIRE_PROGS(COMM, comm)
  fi

  if test "x$OPENJDK_TARGET_OS" = "xmacosx"; then
    BASIC_REQUIRE_PROGS(DSYMUTIL, dsymutil)
    BASIC_REQUIRE_PROGS(XATTR, xattr)
    BASIC_PATH_PROGS(CODESIGN, codesign)
    if test "x$CODESIGN" != "x"; then
      # Verify that the openjdk_codesign certificate is present
      AC_MSG_CHECKING([if openjdk_codesign certificate is present])
      rm -f codesign-testfile
      touch codesign-testfile
      codesign -s openjdk_codesign codesign-testfile 2>&AS_MESSAGE_LOG_FD >&AS_MESSAGE_LOG_FD || CODESIGN=
      rm -f codesign-testfile
      if test "x$CODESIGN" = x; then
        AC_MSG_RESULT([no])
      else
        AC_MSG_RESULT([yes])
      fi
    fi
  fi
])

# Check if build directory is on local disk. If not possible to determine,
# we prefer to claim it's local.
# Argument 1: directory to test
# Argument 2: what to do if it is on local disk
# Argument 3: what to do otherwise (remote disk or failure)
AC_DEFUN([BASIC_CHECK_DIR_ON_LOCAL_DISK],
[
  # df -l lists only local disks; if the given directory is not found then
  # a non-zero exit code is given
  if test "x$DF" = x; then
    if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
      # msys does not have df; use Windows "net use" instead.
      IS_NETWORK_DISK=`net use | grep \`pwd -W | cut -d ":" -f 1 | tr a-z A-Z\`:`
      if test "x$IS_NETWORK_DISK" = x; then
        $2
      else
        $3
      fi
    else
      # No df here, say it's local
      $2
    fi
  else
    if $DF -l $1 > /dev/null 2>&1; then
      $2
    else
      $3
    fi
  fi
])

# Check that source files have basic read permissions set. This might
# not be the case in cygwin in certain conditions.
AC_DEFUN_ONCE([BASIC_CHECK_SRC_PERMS],
[
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    file_to_test="$SRC_ROOT/LICENSE"
    if test `$STAT -c '%a' "$file_to_test"` -lt 400; then
      AC_MSG_ERROR([Bad file permissions on src files. This is usually caused by cloning the repositories with a non cygwin hg in a directory not created in cygwin.])
    fi
  fi
])

AC_DEFUN_ONCE([BASIC_TEST_USABILITY_ISSUES],
[
  # Did user specify any unknown variables?
  BASIC_CHECK_LEFTOVER_OVERRIDDEN

  AC_MSG_CHECKING([if build directory is on local disk])
  BASIC_CHECK_DIR_ON_LOCAL_DISK($OUTPUT_ROOT,
      [OUTPUT_DIR_IS_LOCAL="yes"],
      [OUTPUT_DIR_IS_LOCAL="no"])
  AC_MSG_RESULT($OUTPUT_DIR_IS_LOCAL)

  BASIC_CHECK_SRC_PERMS

  # Check if the user has any old-style ALT_ variables set.
  FOUND_ALT_VARIABLES=`env | grep ^ALT_`

  # Before generating output files, test if they exist. If they do, this is a reconfigure.
  # Since we can't properly handle the dependencies for this, warn the user about the situation
  if test -e $OUTPUT_ROOT/spec.gmk; then
    IS_RECONFIGURE=yes
  else
    IS_RECONFIGURE=no
  fi
])

# Code to run after AC_OUTPUT
AC_DEFUN_ONCE([BASIC_POST_CONFIG_OUTPUT],
[
  # Try to move the config.log file to the output directory.
  if test -e ./config.log; then
    $MV -f ./config.log "$OUTPUT_ROOT/config.log" 2> /dev/null
  fi

  # Rotate our log file (configure.log)
  if test -e "$OUTPUT_ROOT/configure.log.old"; then
    $RM -f "$OUTPUT_ROOT/configure.log.old"
  fi
  if test -e "$OUTPUT_ROOT/configure.log"; then
    $MV -f "$OUTPUT_ROOT/configure.log" "$OUTPUT_ROOT/configure.log.old" 2> /dev/null
  fi

  # Move configure.log from current directory to the build output root
  if test -e ./configure.log; then
    echo found it
    $MV -f ./configure.log "$OUTPUT_ROOT/configure.log" 2> /dev/null
  fi

  # Make the compare script executable
  $CHMOD +x $OUTPUT_ROOT/compare.sh
])
