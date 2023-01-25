/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "compiler/compileLog.hpp"
#include "gc_implementation/shared/gcId.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/ostream.hpp"
#include "utilities/top.hpp"
#include "utilities/xmlstream.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "os_aix.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_bsd.inline.hpp"
#endif

extern "C" void jio_print(const char* s); // Declarationtion of jvm method

outputStream::outputStream(int width) {
  _width       = width;
  _position    = 0;
  _newlines    = 0;
  _precount    = 0;
  _indentation = 0;
}

outputStream::outputStream(int width, bool has_time_stamps) {
  _width       = width;
  _position    = 0;
  _newlines    = 0;
  _precount    = 0;
  _indentation = 0;
  if (has_time_stamps)  _stamp.update();
}

void outputStream::update_position(const char* s, size_t len) {
  for (size_t i = 0; i < len; i++) {
    char ch = s[i];
    if (ch == '\n') {
      _newlines += 1;
      _precount += _position + 1;
      _position = 0;
    } else if (ch == '\t') {
      int tw = 8 - (_position & 7);
      _position += tw;
      _precount -= tw-1;  // invariant:  _precount + _position == total count
    } else {
      _position += 1;
    }
  }
}

// Execute a vsprintf, using the given buffer if necessary.
// Return a pointer to the formatted string.
const char* outputStream::do_vsnprintf(char* buffer, size_t buflen,
                                       const char* format, va_list ap,
                                       bool add_cr,
                                       size_t& result_len) {
  assert(buflen >= 2, "buffer too small");

  const char* result;
  if (add_cr)  buflen--;
  if (!strchr(format, '%')) {
    // constant format string
    result = format;
    result_len = strlen(result);
    if (add_cr && result_len >= buflen)  result_len = buflen-1;  // truncate
  } else if (format[0] == '%' && format[1] == 's' && format[2] == '\0') {
    // trivial copy-through format string
    result = va_arg(ap, const char*);
    result_len = strlen(result);
    if (add_cr && result_len >= buflen)  result_len = buflen-1;  // truncate
  } else {
    int written = os::vsnprintf(buffer, buflen, format, ap);
    assert(written >= 0, "vsnprintf encoding error");
    result = buffer;
    if ((size_t)written < buflen) {
      result_len = written;
    } else {
      DEBUG_ONLY(warning("increase O_BUFLEN in ostream.hpp -- output truncated");)
      result_len = buflen - 1;
    }
  }
  if (add_cr) {
    if (result != buffer) {
      memcpy(buffer, result, result_len);
      result = buffer;
    }
    buffer[result_len++] = '\n';
    buffer[result_len] = 0;
  }
  return result;
}

void outputStream::print(const char* format, ...) {
  char buffer[O_BUFLEN];
  va_list ap;
  va_start(ap, format);
  size_t len;
  const char* str = do_vsnprintf(buffer, O_BUFLEN, format, ap, false, len);
  write(str, len);
  va_end(ap);
}

void outputStream::print_cr(const char* format, ...) {
  char buffer[O_BUFLEN];
  va_list ap;
  va_start(ap, format);
  size_t len;
  const char* str = do_vsnprintf(buffer, O_BUFLEN, format, ap, true, len);
  write(str, len);
  va_end(ap);
}

void outputStream::vprint(const char *format, va_list argptr) {
  char buffer[O_BUFLEN];
  size_t len;
  const char* str = do_vsnprintf(buffer, O_BUFLEN, format, argptr, false, len);
  write(str, len);
}

void outputStream::vprint_cr(const char* format, va_list argptr) {
  char buffer[O_BUFLEN];
  size_t len;
  const char* str = do_vsnprintf(buffer, O_BUFLEN, format, argptr, true, len);
  write(str, len);
}

void outputStream::fill_to(int col) {
  int need_fill = col - position();
  sp(need_fill);
}

void outputStream::move_to(int col, int slop, int min_space) {
  if (position() >= col + slop)
    cr();
  int need_fill = col - position();
  if (need_fill < min_space)
    need_fill = min_space;
  sp(need_fill);
}

void outputStream::put(char ch) {
  assert(ch != 0, "please fix call site");
  char buf[] = { ch, '\0' };
  write(buf, 1);
}

#define SP_USE_TABS false

void outputStream::sp(int count) {
  if (count < 0)  return;
  if (SP_USE_TABS && count >= 8) {
    int target = position() + count;
    while (count >= 8) {
      this->write("\t", 1);
      count -= 8;
    }
    count = target - position();
  }
  while (count > 0) {
    int nw = (count > 8) ? 8 : count;
    this->write("        ", nw);
    count -= nw;
  }
}

void outputStream::cr() {
  this->write("\n", 1);
}

void outputStream::stamp() {
  if (! _stamp.is_updated()) {
    _stamp.update(); // start at 0 on first call to stamp()
  }

  // outputStream::stamp() may get called by ostream_abort(), use snprintf
  // to avoid allocating large stack buffer in print().
  char buf[40];
  jio_snprintf(buf, sizeof(buf), "%.3f", _stamp.seconds());
  print_raw(buf);
}

void outputStream::stamp(bool guard,
                         const char* prefix,
                         const char* suffix) {
  if (!guard) {
    return;
  }
  print_raw(prefix);
  stamp();
  print_raw(suffix);
}

void outputStream::date_stamp(bool guard,
                              const char* prefix,
                              const char* suffix) {
  if (!guard) {
    return;
  }
  print_raw(prefix);
  static const char error_time[] = "yyyy-mm-ddThh:mm:ss.mmm+zzzz";
  static const int buffer_length = 32;
  char buffer[buffer_length];
  const char* iso8601_result = os::iso8601_time(buffer, buffer_length);
  if (iso8601_result != NULL) {
    print_raw(buffer);
  } else {
    print_raw(error_time);
  }
  print_raw(suffix);
  return;
}

void outputStream::gclog_stamp(const GCId& gc_id) {
  date_stamp(PrintGCDateStamps);
  stamp(PrintGCTimeStamps);
  if (PrintGCID) {
    print("#%u: ", gc_id.id());
  }
}

outputStream& outputStream::indent() {
  while (_position < _indentation) sp();
  return *this;
}

void outputStream::print_jlong(jlong value) {
  print(JLONG_FORMAT, value);
}

void outputStream::print_julong(julong value) {
  print(JULONG_FORMAT, value);
}

/**
 * This prints out hex data in a 'windbg' or 'xxd' form, where each line is:
 *   <hex-address>: 8 * <hex-halfword> <ascii translation (optional)>
 * example:
 * 0000000: 7f44 4f46 0102 0102 0000 0000 0000 0000  .DOF............
 * 0000010: 0000 0000 0000 0040 0000 0020 0000 0005  .......@... ....
 * 0000020: 0000 0000 0000 0040 0000 0000 0000 015d  .......@.......]
 * ...
 *
 * indent is applied to each line.  Ends with a CR.
 */
void outputStream::print_data(void* data, size_t len, bool with_ascii) {
  size_t limit = (len + 16) / 16 * 16;
  for (size_t i = 0; i < limit; ++i) {
    if (i % 16 == 0) {
      indent().print(SIZE_FORMAT_HEX_W(07) ":", i);
    }
    if (i % 2 == 0) {
      print(" ");
    }
    if (i < len) {
      print("%02x", ((unsigned char*)data)[i]);
    } else {
      print("  ");
    }
    if ((i + 1) % 16 == 0) {
      if (with_ascii) {
        print("  ");
        for (size_t j = 0; j < 16; ++j) {
          size_t idx = i + j - 15;
          if (idx < len) {
            char c = ((char*)data)[idx];
            print("%c", c >= 32 && c <= 126 ? c : '.');
          }
        }
      }
      cr();
    }
  }
}

stringStream::stringStream(size_t initial_size) : outputStream() {
  buffer_length = initial_size;
  buffer        = NEW_RESOURCE_ARRAY(char, buffer_length);
  buffer_pos    = 0;
  buffer_fixed  = false;
  DEBUG_ONLY(rm = Thread::current()->current_resource_mark();)
}

// useful for output to fixed chunks of memory, such as performance counters
stringStream::stringStream(char* fixed_buffer, size_t fixed_buffer_size) : outputStream() {
  buffer_length = fixed_buffer_size;
  buffer        = fixed_buffer;
  buffer_pos    = 0;
  buffer_fixed  = true;
}

void stringStream::write(const char* s, size_t len) {
  size_t write_len = len;               // number of non-null bytes to write
  size_t end = buffer_pos + len + 1;    // position after write and final '\0'
  if (end > buffer_length) {
    if (buffer_fixed) {
      // if buffer cannot resize, silently truncate
      end = buffer_length;
      write_len = end - buffer_pos - 1; // leave room for the final '\0'
    } else {
      // For small overruns, double the buffer.  For larger ones,
      // increase to the requested size.
      if (end < buffer_length * 2) {
        end = buffer_length * 2;
      }
      char* oldbuf = buffer;
      assert(rm == NULL || Thread::current()->current_resource_mark() == rm,
             "stringStream is re-allocated with a different ResourceMark");
      buffer = NEW_RESOURCE_ARRAY(char, end);
      if (buffer_pos > 0) {
        memcpy(buffer, oldbuf, buffer_pos);
      }
      buffer_length = end;
    }
  }
  // invariant: buffer is always null-terminated
  guarantee(buffer_pos + write_len + 1 <= buffer_length, "stringStream oob");
  if (write_len > 0) {
    buffer[buffer_pos + write_len] = 0;
    memcpy(buffer + buffer_pos, s, write_len);
    buffer_pos += write_len;
  }

  // Note that the following does not depend on write_len.
  // This means that position and count get updated
  // even when overflow occurs.
  update_position(s, len);
}

char* stringStream::as_string() {
  char* copy = NEW_RESOURCE_ARRAY(char, buffer_pos + 1);
  strncpy(copy, buffer, buffer_pos);
  copy[buffer_pos] = 0;  // terminating null
  return copy;
}

stringStream::~stringStream() {}

xmlStream*   xtty;
outputStream* tty;
outputStream* gclog_or_tty;
CDS_ONLY(fileStream* classlist_file;) // Only dump the classes that can be stored into the CDS archive
extern Mutex* tty_lock;

#define EXTRACHARLEN   32
#define CURRENTAPPX    ".current"
// convert YYYY-MM-DD HH:MM:SS to YYYY-MM-DD_HH-MM-SS
char* get_datetime_string(char *buf, size_t len) {
  os::local_time_string(buf, len);
  int i = (int)strlen(buf);
  while (--i >= 0) {
    if (buf[i] == ' ') buf[i] = '_';
    else if (buf[i] == ':') buf[i] = '-';
  }
  return buf;
}

static const char* make_log_name_internal(const char* log_name, const char* force_directory,
                                                int pid, const char* tms) {
  const char* basename = log_name;
  char file_sep = os::file_separator()[0];
  const char* cp;
  char  pid_text[32];

  for (cp = log_name; *cp != '\0'; cp++) {
    if (*cp == '/' || *cp == file_sep) {
      basename = cp + 1;
    }
  }
  const char* nametail = log_name;
  // Compute buffer length
  size_t buffer_length;
  if (force_directory != NULL) {
    buffer_length = strlen(force_directory) + strlen(os::file_separator()) +
                    strlen(basename) + 1;
  } else {
    buffer_length = strlen(log_name) + 1;
  }

  const char* pts = strstr(basename, "%p");
  int pid_pos = (pts == NULL) ? -1 : (pts - nametail);

  if (pid_pos >= 0) {
    jio_snprintf(pid_text, sizeof(pid_text), "pid%u", pid);
    buffer_length += strlen(pid_text);
  }

  pts = strstr(basename, "%t");
  int tms_pos = (pts == NULL) ? -1 : (pts - nametail);
  if (tms_pos >= 0) {
    buffer_length += strlen(tms);
  }

  // File name is too long.
  if (buffer_length > JVM_MAXPATHLEN) {
    return NULL;
  }

  // Create big enough buffer.
  char *buf = NEW_C_HEAP_ARRAY(char, buffer_length, mtInternal);

  strcpy(buf, "");
  if (force_directory != NULL) {
    strcat(buf, force_directory);
    strcat(buf, os::file_separator());
    nametail = basename;       // completely skip directory prefix
  }

  // who is first, %p or %t?
  int first = -1, second = -1;
  const char *p1st = NULL;
  const char *p2nd = NULL;

  if (pid_pos >= 0 && tms_pos >= 0) {
    // contains both %p and %t
    if (pid_pos < tms_pos) {
      // case foo%pbar%tmonkey.log
      first  = pid_pos;
      p1st   = pid_text;
      second = tms_pos;
      p2nd   = tms;
    } else {
      // case foo%tbar%pmonkey.log
      first  = tms_pos;
      p1st   = tms;
      second = pid_pos;
      p2nd   = pid_text;
    }
  } else if (pid_pos >= 0) {
    // contains %p only
    first  = pid_pos;
    p1st   = pid_text;
  } else if (tms_pos >= 0) {
    // contains %t only
    first  = tms_pos;
    p1st   = tms;
  }

  int buf_pos = (int)strlen(buf);
  const char* tail = nametail;

  if (first >= 0) {
    tail = nametail + first + 2;
    strncpy(&buf[buf_pos], nametail, first);
    strcpy(&buf[buf_pos + first], p1st);
    buf_pos = (int)strlen(buf);
    if (second >= 0) {
      strncpy(&buf[buf_pos], tail, second - first - 2);
      strcpy(&buf[buf_pos + second - first - 2], p2nd);
      tail = nametail + second + 2;
    }
  }
  strcat(buf, tail);      // append rest of name, or all of name
  return buf;
}

// log_name comes from -XX:LogFile=log_name, -Xloggc:log_name or
// -XX:DumpLoadedClassList=<file_name>
// in log_name, %p => pid1234 and
//              %t => YYYY-MM-DD_HH-MM-SS
static const char* make_log_name(const char* log_name, const char* force_directory) {
  char timestr[32];
  get_datetime_string(timestr, sizeof(timestr));
  return make_log_name_internal(log_name, force_directory, os::current_process_id(),
                                timestr);
}

#ifndef PRODUCT
void test_loggc_filename() {
  int pid;
  char  tms[32];
  char  i_result[JVM_MAXPATHLEN];
  const char* o_result;
  get_datetime_string(tms, sizeof(tms));
  pid = os::current_process_id();

  // test.log
  jio_snprintf(i_result, JVM_MAXPATHLEN, "test.log", tms);
  o_result = make_log_name_internal("test.log", NULL, pid, tms);
  assert(strcmp(i_result, o_result) == 0, "failed on testing make_log_name(\"test.log\", NULL)");
  FREE_C_HEAP_ARRAY(char, o_result, mtInternal);

  // test-%t-%p.log
  jio_snprintf(i_result, JVM_MAXPATHLEN, "test-%s-pid%u.log", tms, pid);
  o_result = make_log_name_internal("test-%t-%p.log", NULL, pid, tms);
  assert(strcmp(i_result, o_result) == 0, "failed on testing make_log_name(\"test-%%t-%%p.log\", NULL)");
  FREE_C_HEAP_ARRAY(char, o_result, mtInternal);

  // test-%t%p.log
  jio_snprintf(i_result, JVM_MAXPATHLEN, "test-%spid%u.log", tms, pid);
  o_result = make_log_name_internal("test-%t%p.log", NULL, pid, tms);
  assert(strcmp(i_result, o_result) == 0, "failed on testing make_log_name(\"test-%%t%%p.log\", NULL)");
  FREE_C_HEAP_ARRAY(char, o_result, mtInternal);

  // %p%t.log
  jio_snprintf(i_result, JVM_MAXPATHLEN, "pid%u%s.log", pid, tms);
  o_result = make_log_name_internal("%p%t.log", NULL, pid, tms);
  assert(strcmp(i_result, o_result) == 0, "failed on testing make_log_name(\"%%p%%t.log\", NULL)");
  FREE_C_HEAP_ARRAY(char, o_result, mtInternal);

  // %p-test.log
  jio_snprintf(i_result, JVM_MAXPATHLEN, "pid%u-test.log", pid);
  o_result = make_log_name_internal("%p-test.log", NULL, pid, tms);
  assert(strcmp(i_result, o_result) == 0, "failed on testing make_log_name(\"%%p-test.log\", NULL)");
  FREE_C_HEAP_ARRAY(char, o_result, mtInternal);

  // %t.log
  jio_snprintf(i_result, JVM_MAXPATHLEN, "%s.log", tms);
  o_result = make_log_name_internal("%t.log", NULL, pid, tms);
  assert(strcmp(i_result, o_result) == 0, "failed on testing make_log_name(\"%%t.log\", NULL)");
  FREE_C_HEAP_ARRAY(char, o_result, mtInternal);

  {
    // longest filename
    char longest_name[JVM_MAXPATHLEN];
    memset(longest_name, 'a', sizeof(longest_name));
    longest_name[JVM_MAXPATHLEN - 1] = '\0';
    o_result = make_log_name_internal((const char*)&longest_name, NULL, pid, tms);
    assert(strcmp(longest_name, o_result) == 0, err_msg("longest name does not match. expected '%s' but got '%s'", longest_name, o_result));
    FREE_C_HEAP_ARRAY(char, o_result, mtInternal);
  }

  {
    // too long file name
    char too_long_name[JVM_MAXPATHLEN + 100];
    int too_long_length = sizeof(too_long_name);
    memset(too_long_name, 'a', too_long_length);
    too_long_name[too_long_length - 1] = '\0';
    o_result = make_log_name_internal((const char*)&too_long_name, NULL, pid, tms);
    assert(o_result == NULL, err_msg("Too long file name should return NULL, but got '%s'", o_result));
  }

  {
    // too long with timestamp
    char longest_name[JVM_MAXPATHLEN];
    memset(longest_name, 'a', JVM_MAXPATHLEN);
    longest_name[JVM_MAXPATHLEN - 3] = '%';
    longest_name[JVM_MAXPATHLEN - 2] = 't';
    longest_name[JVM_MAXPATHLEN - 1] = '\0';
    o_result = make_log_name_internal((const char*)&longest_name, NULL, pid, tms);
    assert(o_result == NULL, err_msg("Too long file name after timestamp expansion should return NULL, but got '%s'", o_result));
  }

  {
    // too long with pid
    char longest_name[JVM_MAXPATHLEN];
    memset(longest_name, 'a', JVM_MAXPATHLEN);
    longest_name[JVM_MAXPATHLEN - 3] = '%';
    longest_name[JVM_MAXPATHLEN - 2] = 'p';
    longest_name[JVM_MAXPATHLEN - 1] = '\0';
    o_result = make_log_name_internal((const char*)&longest_name, NULL, pid, tms);
    assert(o_result == NULL, err_msg("Too long file name after pid expansion should return NULL, but got '%s'", o_result));
  }
}

//////////////////////////////////////////////////////////////////////////////
// Test os::vsnprintf and friends.

void check_snprintf_result(int expected, size_t limit, int actual, bool expect_count) {
  if (expect_count || ((size_t)expected < limit)) {
    assert(expected == actual, "snprintf result not expected value");
  } else {
    // Make this check more permissive for jdk8u, don't assert that actual == 0.
    // e.g. jio_vsnprintf_wrapper and jio_snprintf return -1 when expected >= limit
    if (expected >= (int) limit) {
      assert(actual == -1, "snprintf result should be -1 for expected >= limit");
    } else {
      assert(actual > 0, "snprintf result should be >0 for expected < limit");
    }
  }
}

// PrintFn is expected to be int (*)(char*, size_t, const char*, ...).
// But jio_snprintf is a C-linkage function with that signature, which
// has a different type on some platforms (like Solaris).
template<typename PrintFn>
void test_snprintf(PrintFn pf, bool expect_count) {
  const char expected[] = "abcdefghijklmnopqrstuvwxyz";
  const int expected_len = sizeof(expected) - 1;
  const size_t padding_size = 10;
  char buffer[2 * (sizeof(expected) + padding_size)];
  char check_buffer[sizeof(buffer)];
  const char check_char = '1';  // Something not in expected.
  memset(check_buffer, check_char, sizeof(check_buffer));
  const size_t sizes_to_test[] = {
    sizeof(buffer) - padding_size,       // Fits, with plenty of space to spare.
    sizeof(buffer)/2,                    // Fits, with space to spare.
    sizeof(buffer)/4,                    // Doesn't fit.
    sizeof(expected) + padding_size + 1, // Fits, with a little room to spare
    sizeof(expected) + padding_size,     // Fits exactly.
    sizeof(expected) + padding_size - 1, // Doesn't quite fit.
    2,                                   // One char + terminating NUL.
    1,                                   // Only space for terminating NUL.
    0 };                                 // No space at all.
  for (unsigned i = 0; i < ARRAY_SIZE(sizes_to_test); ++i) {
    memset(buffer, check_char, sizeof(buffer)); // To catch stray writes.
    size_t test_size = sizes_to_test[i];
    ResourceMark rm;
    stringStream s;
    s.print("test_size: " SIZE_FORMAT, test_size);
    size_t prefix_size = padding_size;
    guarantee(test_size <= (sizeof(buffer) - prefix_size), "invariant");
    size_t write_size = MIN2(sizeof(expected), test_size);
    size_t suffix_size = sizeof(buffer) - prefix_size - write_size;
    char* write_start = buffer + prefix_size;
    char* write_end = write_start + write_size;

    int result = pf(write_start, test_size, "%s", expected);

    check_snprintf_result(expected_len, test_size, result, expect_count);

    // Verify expected output.
    if (test_size > 0) {
      assert(0 == strncmp(write_start, expected, write_size - 1), "strncmp failure");
      // Verify terminating NUL of output.
      assert('\0' == write_start[write_size - 1], "null terminator failure");
    } else {
      guarantee(test_size == 0, "invariant");
      guarantee(write_size == 0, "invariant");
      guarantee(prefix_size + suffix_size == sizeof(buffer), "invariant");
      guarantee(write_start == write_end, "invariant");
    }

    // Verify no scribbling on prefix or suffix.
    assert(0 == strncmp(buffer, check_buffer, prefix_size), "prefix scribble");
    assert(0 == strncmp(write_end, check_buffer, suffix_size), "suffix scribble");
  }

  // Special case of 0-length buffer with empty (except for terminator) output.
  check_snprintf_result(0, 0, pf(NULL, 0, "%s", ""), expect_count);
  check_snprintf_result(0, 0, pf(NULL, 0, ""), expect_count);
}

// This is probably equivalent to os::snprintf, but we're being
// explicit about what we're testing here.
static int vsnprintf_wrapper(char* buf, size_t len, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED_INTERNAL
  int result = os::vsnprintf(buf, len, fmt, args);
PRAGMA_DIAG_POP
  va_end(args);
  return result;
}

// These are declared in jvm.h; test here, with related functions.
extern "C" {
int jio_vsnprintf(char*, size_t, const char*, va_list);
int jio_snprintf(char*, size_t, const char*, ...);
}

// This is probably equivalent to jio_snprintf, but we're being
// explicit about what we're testing here.
static int jio_vsnprintf_wrapper(char* buf, size_t len, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  int result = jio_vsnprintf(buf, len, fmt, args);
  va_end(args);
  return result;
}

void test_snprintf() {
  test_snprintf(vsnprintf_wrapper, true);
  test_snprintf(os::snprintf, true);
  test_snprintf(jio_vsnprintf_wrapper, false); // jio_vsnprintf returns -1 on error including exceeding buffer size
  test_snprintf(jio_snprintf, false);          // jio_snprintf calls jio_vsnprintf
}
#endif // PRODUCT

fileStream::fileStream(const char* file_name) {
  _file = fopen(file_name, "w");
  if (_file != NULL) {
    _need_close = true;
  } else {
    warning("Cannot open file %s due to %s\n", file_name, strerror(errno));
    _need_close = false;
  }
}

fileStream::fileStream(const char* file_name, const char* opentype) {
  _file = fopen(file_name, opentype);
  if (_file != NULL) {
    _need_close = true;
  } else {
    warning("Cannot open file %s due to %s\n", file_name, strerror(errno));
    _need_close = false;
  }
}

void fileStream::write(const char* s, size_t len) {
  if (_file != NULL)  {
    // Make an unused local variable to avoid warning from gcc 4.x compiler.
    size_t count = fwrite(s, 1, len, _file);
  }
  update_position(s, len);
}

long fileStream::fileSize() {
  long size = -1;
  if (_file != NULL) {
    long pos  = ::ftell(_file);
    if (::fseek(_file, 0, SEEK_END) == 0) {
      size = ::ftell(_file);
    }
    ::fseek(_file, pos, SEEK_SET);
  }
  return size;
}

char* fileStream::readln(char *data, int count ) {
  char * ret = ::fgets(data, count, _file);
  //Get rid of annoying \n char
  data[::strlen(data)-1] = '\0';
  return ret;
}

fileStream::~fileStream() {
  if (_file != NULL) {
    if (_need_close) fclose(_file);
    _file      = NULL;
  }
}

void fileStream::flush() {
  fflush(_file);
}

fdStream::fdStream(const char* file_name) {
  _fd = open(file_name, O_WRONLY | O_CREAT | O_TRUNC, 0666);
  _need_close = true;
}

fdStream::~fdStream() {
  if (_fd != -1) {
    if (_need_close) close(_fd);
    _fd = -1;
  }
}

void fdStream::write(const char* s, size_t len) {
  if (_fd != -1) {
    // Make an unused local variable to avoid warning from gcc 4.x compiler.
    size_t count = ::write(_fd, s, (int)len);
  }
  update_position(s, len);
}

// dump vm version, os version, platform info, build id,
// memory usage and command line flags into header
void gcLogFileStream::dump_loggc_header() {
  if (is_open()) {
    print_cr("%s", Abstract_VM_Version::internal_vm_info_string());
    os::print_memory_info(this);
    print("CommandLine flags: ");
    CommandLineFlags::printSetFlags(this);
  }
}

gcLogFileStream::~gcLogFileStream() {
  if (_file != NULL) {
    if (_need_close) fclose(_file);
    _file = NULL;
  }
  if (_file_name != NULL) {
    FREE_C_HEAP_ARRAY(char, _file_name, mtInternal);
    _file_name = NULL;
  }

  delete _file_lock;
}

gcLogFileStream::gcLogFileStream(const char* file_name) : _file_lock(NULL) {
  _cur_file_num = 0;
  _bytes_written = 0L;
  _file_name = make_log_name(file_name, NULL);

  if (_file_name == NULL) {
    warning("Cannot open file %s: file name is too long.\n", file_name);
    _need_close = false;
    UseGCLogFileRotation = false;
    return;
  }

  // gc log file rotation
  if (UseGCLogFileRotation && NumberOfGCLogFiles > 1) {
    char tempbuf[JVM_MAXPATHLEN];
    jio_snprintf(tempbuf, sizeof(tempbuf), "%s.%d" CURRENTAPPX, _file_name, _cur_file_num);
    _file = fopen(tempbuf, "w");
  } else {
    _file = fopen(_file_name, "w");
  }
  if (_file != NULL) {
    _need_close = true;
    dump_loggc_header();

    if (UseGCLogFileRotation) {
      _file_lock = new Mutex(Mutex::leaf, "GCLogFile");
    }
  } else {
    warning("Cannot open file %s due to %s\n", _file_name, strerror(errno));
    _need_close = false;
  }
}

void gcLogFileStream::write(const char* s, size_t len) {
  if (_file != NULL) {
    // we can't use Thread::current() here because thread may be NULL
    // in early stage(ostream_init_log)
    Thread* thread = ThreadLocalStorage::thread();

    // avoid the mutex in the following cases
    // 1) ThreadLocalStorage::thread() hasn't been initialized
    // 2) _file_lock is not in use.
    // 3) current() is VMThread and its reentry flag is set
    if (!thread || !_file_lock || (thread->is_VM_thread()
                               && ((VMThread* )thread)->is_gclog_reentry())) {
      size_t count = fwrite(s, 1, len, _file);
      _bytes_written += count;
    }
    else {
      MutexLockerEx ml(_file_lock, Mutex::_no_safepoint_check_flag);
      size_t count = fwrite(s, 1, len, _file);
      _bytes_written += count;
    }
  }
  update_position(s, len);
}

// rotate_log must be called from VMThread at a safepoint. In case need change parameters
// for gc log rotation from thread other than VMThread, a sub type of VM_Operation
// should be created and be submitted to VMThread's operation queue. DO NOT call this
// function directly. It is safe to rotate log through VMThread because
// no mutator threads run concurrently with the VMThread, and GC threads that run
// concurrently with the VMThread are synchronized in write and rotate_log via _file_lock.
// rotate_log can write log entries, so write supports reentry for it.
void gcLogFileStream::rotate_log(bool force, outputStream* out) {
 #ifdef ASSERT
   Thread *thread = Thread::current();
   assert(thread == NULL ||
          (thread->is_VM_thread() && SafepointSynchronize::is_at_safepoint()),
          "Must be VMThread at safepoint");
 #endif

  VMThread* vmthread = VMThread::vm_thread();
  {
    // nop if _file_lock is NULL.
    MutexLockerEx ml(_file_lock, Mutex::_no_safepoint_check_flag);
    vmthread->set_gclog_reentry(true);
    rotate_log_impl(force, out);
    vmthread->set_gclog_reentry(false);
  }
}

void gcLogFileStream::rotate_log_impl(bool force, outputStream* out) {
  char time_msg[O_BUFLEN];
  char time_str[EXTRACHARLEN];
  char current_file_name[JVM_MAXPATHLEN];
  char renamed_file_name[JVM_MAXPATHLEN];

  if (!should_rotate(force)) {
    return;
  }

  if (NumberOfGCLogFiles == 1) {
    // rotate in same file
    rewind();
    _bytes_written = 0L;
    jio_snprintf(time_msg, sizeof(time_msg), "File  %s rotated at %s\n",
                 _file_name, os::local_time_string((char *)time_str, sizeof(time_str)));
    write(time_msg, strlen(time_msg));

    if (out != NULL) {
      out->print("%s", time_msg);
    }

    dump_loggc_header();
    return;
  }

#if defined(_WINDOWS)
#ifndef F_OK
#define F_OK 0
#endif
#endif // _WINDOWS

  // rotate file in names extended_filename.0, extended_filename.1, ...,
  // extended_filename.<NumberOfGCLogFiles - 1>. Current rotation file name will
  // have a form of extended_filename.<i>.current where i is the current rotation
  // file number. After it reaches max file size, the file will be saved and renamed
  // with .current removed from its tail.
  if (_file != NULL) {
    jio_snprintf(renamed_file_name, JVM_MAXPATHLEN, "%s.%d",
                 _file_name, _cur_file_num);
    int result = jio_snprintf(current_file_name, JVM_MAXPATHLEN,
                              "%s.%d" CURRENTAPPX, _file_name, _cur_file_num);
    if (result >= JVM_MAXPATHLEN) {
      warning("Cannot create new log file name: %s: file name is too long.\n", current_file_name);
      return;
    }

    const char* msg = force ? "GC log rotation request has been received."
                            : "GC log file has reached the maximum size.";
    jio_snprintf(time_msg, sizeof(time_msg), "%s %s Saved as %s\n",
                     os::local_time_string((char *)time_str, sizeof(time_str)),
                                                         msg, renamed_file_name);
    write(time_msg, strlen(time_msg));

    if (out != NULL) {
      out->print("%s", time_msg);
    }

    fclose(_file);
    _file = NULL;

    bool can_rename = true;
    if (access(current_file_name, F_OK) != 0) {
      // current file does not exist?
      warning("No source file exists, cannot rename\n");
      can_rename = false;
    }
    if (can_rename) {
      if (access(renamed_file_name, F_OK) == 0) {
        if (remove(renamed_file_name) != 0) {
          warning("Could not delete existing file %s\n", renamed_file_name);
          can_rename = false;
        }
      } else {
        // file does not exist, ok to rename
      }
    }
    if (can_rename && rename(current_file_name, renamed_file_name) != 0) {
      warning("Could not rename %s to %s\n", _file_name, renamed_file_name);
    }
  }

  _cur_file_num++;
  if (_cur_file_num > NumberOfGCLogFiles - 1) _cur_file_num = 0;
  int result = jio_snprintf(current_file_name,  JVM_MAXPATHLEN, "%s.%d" CURRENTAPPX,
               _file_name, _cur_file_num);
  if (result >= JVM_MAXPATHLEN) {
    warning("Cannot create new log file name: %s: file name is too long.\n", current_file_name);
    return;
  }

  _file = fopen(current_file_name, "w");

  if (_file != NULL) {
    _bytes_written = 0L;
    _need_close = true;
    // reuse current_file_name for time_msg
    jio_snprintf(current_file_name, JVM_MAXPATHLEN,
                 "%s.%d", _file_name, _cur_file_num);
    jio_snprintf(time_msg, sizeof(time_msg), "%s GC log file created %s\n",
                 os::local_time_string((char *)time_str, sizeof(time_str)), current_file_name);
    write(time_msg, strlen(time_msg));

    if (out != NULL) {
      out->print("%s", time_msg);
    }

    dump_loggc_header();
    // remove the existing file
    if (access(current_file_name, F_OK) == 0) {
      if (remove(current_file_name) != 0) {
        warning("Could not delete existing file %s\n", current_file_name);
      }
    }
  } else {
    warning("failed to open rotation log file %s due to %s\n"
            "Turned off GC log file rotation\n",
                  _file_name, strerror(errno));
    _need_close = false;
    FLAG_SET_DEFAULT(UseGCLogFileRotation, false);
  }
}

defaultStream* defaultStream::instance = NULL;
int defaultStream::_output_fd = 1;
int defaultStream::_error_fd  = 2;
FILE* defaultStream::_output_stream = stdout;
FILE* defaultStream::_error_stream  = stderr;

#define LOG_MAJOR_VERSION 160
#define LOG_MINOR_VERSION 1

void defaultStream::init() {
  _inited = true;
  if (LogVMOutput || LogCompilation) {
    init_log();
  }
}

bool defaultStream::has_log_file() {
  // lazily create log file (at startup, LogVMOutput is false even
  // if +LogVMOutput is used, because the flags haven't been parsed yet)
  // For safer printing during fatal error handling, do not init logfile
  // if a VM error has been reported.
  if (!_inited && !is_error_reported())  init();
  return _log_file != NULL;
}

fileStream* defaultStream::open_file(const char* log_name) {
  const char* try_name = make_log_name(log_name, NULL);
  if (try_name == NULL) {
    warning("Cannot open file %s: file name is too long.\n", log_name);
    return NULL;
  }

  fileStream* file = new(ResourceObj::C_HEAP, mtInternal) fileStream(try_name);
  FREE_C_HEAP_ARRAY(char, try_name, mtInternal);
  if (file->is_open()) {
    return file;
  }

  // Try again to open the file in the temp directory.
  delete file;
  char warnbuf[O_BUFLEN*2];
  jio_snprintf(warnbuf, sizeof(warnbuf), "Warning:  Cannot open log file: %s\n", log_name);
  // Note:  This feature is for maintainer use only.  No need for L10N.
  jio_print(warnbuf);
  try_name = make_log_name(log_name, os::get_temp_directory());
  if (try_name == NULL) {
    warning("Cannot open file %s: file name is too long for directory %s.\n", log_name, os::get_temp_directory());
    return NULL;
  }

  jio_snprintf(warnbuf, sizeof(warnbuf),
               "Warning:  Forcing option -XX:LogFile=%s\n", try_name);
  jio_print(warnbuf);

  file = new(ResourceObj::C_HEAP, mtInternal) fileStream(try_name);
  FREE_C_HEAP_ARRAY(char, try_name, mtInternal);
  if (file->is_open()) {
    return file;
  }

  delete file;
  return NULL;
}

void defaultStream::init_log() {
  // %%% Need a MutexLocker?
  const char* log_name = LogFile != NULL ? LogFile : "hotspot_%p.log";
  fileStream* file = open_file(log_name);

  if (file != NULL) {
    _log_file = file;
    _outer_xmlStream = new(ResourceObj::C_HEAP, mtInternal) xmlStream(file);
    start_log();
  } else {
    // and leave xtty as NULL
    LogVMOutput = false;
    DisplayVMOutput = true;
    LogCompilation = false;
  }
}

void defaultStream::start_log() {
  xmlStream*xs = _outer_xmlStream;
    if (this == tty)  xtty = xs;
    // Write XML header.
    xs->print_cr("<?xml version='1.0' encoding='UTF-8'?>");
    // (For now, don't bother to issue a DTD for this private format.)
    jlong time_ms = os::javaTimeMillis() - tty->time_stamp().milliseconds();
    // %%% Should be: jlong time_ms = os::start_time_milliseconds(), if
    // we ever get round to introduce that method on the os class
    xs->head("hotspot_log version='%d %d'"
             " process='%d' time_ms='" INT64_FORMAT "'",
             LOG_MAJOR_VERSION, LOG_MINOR_VERSION,
             os::current_process_id(), (int64_t)time_ms);
    // Write VM version header immediately.
    xs->head("vm_version");
    xs->head("name"); xs->text("%s", VM_Version::vm_name()); xs->cr();
    xs->tail("name");
    xs->head("release"); xs->text("%s", VM_Version::vm_release()); xs->cr();
    xs->tail("release");
    xs->head("info"); xs->text("%s", VM_Version::internal_vm_info_string()); xs->cr();
    xs->tail("info");
    xs->tail("vm_version");
    // Record information about the command-line invocation.
    xs->head("vm_arguments");  // Cf. Arguments::print_on()
    if (Arguments::num_jvm_flags() > 0) {
      xs->head("flags");
      Arguments::print_jvm_flags_on(xs->text());
      xs->tail("flags");
    }
    if (Arguments::num_jvm_args() > 0) {
      xs->head("args");
      Arguments::print_jvm_args_on(xs->text());
      xs->tail("args");
    }
    if (Arguments::java_command() != NULL) {
      xs->head("command"); xs->text()->print_cr("%s", Arguments::java_command());
      xs->tail("command");
    }
    if (Arguments::sun_java_launcher() != NULL) {
      xs->head("launcher"); xs->text()->print_cr("%s", Arguments::sun_java_launcher());
      xs->tail("launcher");
    }
    if (Arguments::system_properties() !=  NULL) {
      xs->head("properties");
      // Print it as a java-style property list.
      // System properties don't generally contain newlines, so don't bother with unparsing.
      for (SystemProperty* p = Arguments::system_properties(); p != NULL; p = p->next()) {
        xs->text()->print_cr("%s=%s", p->key(), p->value());
      }
      xs->tail("properties");
    }
    xs->tail("vm_arguments");
    // tty output per se is grouped under the <tty>...</tty> element.
    xs->head("tty");
    // All further non-markup text gets copied to the tty:
    xs->_text = this;  // requires friend declaration!
}

// finish_log() is called during normal VM shutdown. finish_log_on_error() is
// called by ostream_abort() after a fatal error.
//
void defaultStream::finish_log() {
  xmlStream* xs = _outer_xmlStream;
  xs->done("tty");

  // Other log forks are appended here, at the End of Time:
  CompileLog::finish_log(xs->out());  // write compile logging, if any, now

  xs->done("hotspot_log");
  xs->flush();

  fileStream* file = _log_file;
  _log_file = NULL;

  delete _outer_xmlStream;
  _outer_xmlStream = NULL;

  file->flush();
  delete file;
}

void defaultStream::finish_log_on_error(char *buf, int buflen) {
  xmlStream* xs = _outer_xmlStream;

  if (xs && xs->out()) {

    xs->done_raw("tty");

    // Other log forks are appended here, at the End of Time:
    CompileLog::finish_log_on_error(xs->out(), buf, buflen);  // write compile logging, if any, now

    xs->done_raw("hotspot_log");
    xs->flush();

    fileStream* file = _log_file;
    _log_file = NULL;
    _outer_xmlStream = NULL;

    if (file) {
      file->flush();

      // Can't delete or close the file because delete and fclose aren't
      // async-safe. We are about to die, so leave it to the kernel.
      // delete file;
    }
  }
}

intx defaultStream::hold(intx writer_id) {
  bool has_log = has_log_file();  // check before locking
  if (// impossible, but who knows?
      writer_id == NO_WRITER ||

      // bootstrap problem
      tty_lock == NULL ||

      // can't grab a lock or call Thread::current() if TLS isn't initialized
      ThreadLocalStorage::thread() == NULL ||

      // developer hook
      !SerializeVMOutput ||

      // VM already unhealthy
      is_error_reported() ||

      // safepoint == global lock (for VM only)
      (SafepointSynchronize::is_synchronizing() &&
       Thread::current()->is_VM_thread())
      ) {
    // do not attempt to lock unless we know the thread and the VM is healthy
    return NO_WRITER;
  }
  if (_writer == writer_id) {
    // already held, no need to re-grab the lock
    return NO_WRITER;
  }
  tty_lock->lock_without_safepoint_check();
  // got the lock
  if (writer_id != _last_writer) {
    if (has_log) {
      _log_file->bol();
      // output a hint where this output is coming from:
      _log_file->print_cr("<writer thread='" UINTX_FORMAT "'/>", writer_id);
    }
    _last_writer = writer_id;
  }
  _writer = writer_id;
  return writer_id;
}

void defaultStream::release(intx holder) {
  if (holder == NO_WRITER) {
    // nothing to release:  either a recursive lock, or we scribbled (too bad)
    return;
  }
  if (_writer != holder) {
    return;  // already unlocked, perhaps via break_tty_lock_for_safepoint
  }
  _writer = NO_WRITER;
  tty_lock->unlock();
}


// Yuck:  jio_print does not accept char*/len.
static void call_jio_print(const char* s, size_t len) {
  char buffer[O_BUFLEN+100];
  if (len > sizeof(buffer)-1) {
    warning("increase O_BUFLEN in ostream.cpp -- output truncated");
    len = sizeof(buffer)-1;
  }
  strncpy(buffer, s, len);
  buffer[len] = '\0';
  jio_print(buffer);
}


void defaultStream::write(const char* s, size_t len) {
  intx thread_id = os::current_thread_id();
  intx holder = hold(thread_id);

  if (DisplayVMOutput &&
      (_outer_xmlStream == NULL || !_outer_xmlStream->inside_attrs())) {
    // print to output stream. It can be redirected by a vfprintf hook
    if (s[len] == '\0') {
      jio_print(s);
    } else {
      call_jio_print(s, len);
    }
  }

  // print to log file
  if (has_log_file()) {
    int nl0 = _newlines;
    xmlTextStream::write(s, len);
    // flush the log file too, if there were any newlines
    if (nl0 != _newlines){
      flush();
    }
  } else {
    update_position(s, len);
  }

  release(holder);
}

intx ttyLocker::hold_tty() {
  if (defaultStream::instance == NULL)  return defaultStream::NO_WRITER;
  intx thread_id = os::current_thread_id();
  return defaultStream::instance->hold(thread_id);
}

void ttyLocker::release_tty(intx holder) {
  if (holder == defaultStream::NO_WRITER)  return;
  defaultStream::instance->release(holder);
}

bool ttyLocker::release_tty_if_locked() {
  intx thread_id = os::current_thread_id();
  if (defaultStream::instance->writer() == thread_id) {
    // release the lock and return true so callers know if was
    // previously held.
    release_tty(thread_id);
    return true;
  }
  return false;
}

void ttyLocker::break_tty_lock_for_safepoint(intx holder) {
  if (defaultStream::instance != NULL &&
      defaultStream::instance->writer() == holder) {
    if (xtty != NULL) {
      xtty->print_cr("<!-- safepoint while printing -->");
    }
    defaultStream::instance->release(holder);
  }
  // (else there was no lock to break)
}

void ostream_init() {
  if (defaultStream::instance == NULL) {
    defaultStream::instance = new(ResourceObj::C_HEAP, mtInternal) defaultStream();
    tty = defaultStream::instance;

    // We want to ensure that time stamps in GC logs consider time 0
    // the time when the JVM is initialized, not the first time we ask
    // for a time stamp. So, here, we explicitly update the time stamp
    // of tty.
    tty->time_stamp().update_to(1);
  }
}

void ostream_init_log() {
  // For -Xloggc:<file> option - called in runtime/thread.cpp
  // Note : this must be called AFTER ostream_init()

  gclog_or_tty = tty; // default to tty
  if (Arguments::gc_log_filename() != NULL) {
    fileStream * gclog  = new(ResourceObj::C_HEAP, mtInternal)
                             gcLogFileStream(Arguments::gc_log_filename());
    if (gclog->is_open()) {
      // now we update the time stamp of the GC log to be synced up
      // with tty.
      gclog->time_stamp().update_to(tty->time_stamp().ticks());
    }
    gclog_or_tty = gclog;
  }

#if INCLUDE_CDS
  // For -XX:DumpLoadedClassList=<file> option
  if (DumpLoadedClassList != NULL) {
    const char* list_name = make_log_name(DumpLoadedClassList, NULL);
    classlist_file = new(ResourceObj::C_HEAP, mtInternal)
                         fileStream(list_name);
    FREE_C_HEAP_ARRAY(char, list_name, mtInternal);
  }
#endif

  // If we haven't lazily initialized the logfile yet, do it now,
  // to avoid the possibility of lazy initialization during a VM
  // crash, which can affect the stability of the fatal error handler.
  defaultStream::instance->has_log_file();
}

// ostream_exit() is called during normal VM exit to finish log files, flush
// output and free resource.
void ostream_exit() {
  static bool ostream_exit_called = false;
  if (ostream_exit_called)  return;
  ostream_exit_called = true;
#if INCLUDE_CDS
  if (classlist_file != NULL) {
    delete classlist_file;
  }
#endif
  if (gclog_or_tty != tty) {
      delete gclog_or_tty;
  }
  {
      // we temporaly disable PrintMallocFree here
      // as otherwise it'll lead to using of almost deleted
      // tty or defaultStream::instance in logging facility
      // of HeapFree(), see 6391258
      DEBUG_ONLY(FlagSetting fs(PrintMallocFree, false);)
      if (tty != defaultStream::instance) {
          delete tty;
      }
      if (defaultStream::instance != NULL) {
          delete defaultStream::instance;
      }
  }
  tty = NULL;
  xtty = NULL;
  gclog_or_tty = NULL;
  defaultStream::instance = NULL;
}

// ostream_abort() is called by os::abort() when VM is about to die.
void ostream_abort() {
  // Here we can't delete gclog_or_tty and tty, just flush their output
  if (gclog_or_tty) gclog_or_tty->flush();
  if (tty) tty->flush();

  if (defaultStream::instance != NULL) {
    static char buf[4096];
    defaultStream::instance->finish_log_on_error(buf, sizeof(buf));
  }
}

staticBufferStream::staticBufferStream(char* buffer, size_t buflen,
                                       outputStream *outer_stream) {
  _buffer = buffer;
  _buflen = buflen;
  _outer_stream = outer_stream;
  // compile task prints time stamp relative to VM start
  _stamp.update_to(1);
}

void staticBufferStream::write(const char* c, size_t len) {
  _outer_stream->print_raw(c, (int)len);
}

void staticBufferStream::flush() {
  _outer_stream->flush();
}

void staticBufferStream::print(const char* format, ...) {
  va_list ap;
  va_start(ap, format);
  size_t len;
  const char* str = do_vsnprintf(_buffer, _buflen, format, ap, false, len);
  write(str, len);
  va_end(ap);
}

void staticBufferStream::print_cr(const char* format, ...) {
  va_list ap;
  va_start(ap, format);
  size_t len;
  const char* str = do_vsnprintf(_buffer, _buflen, format, ap, true, len);
  write(str, len);
  va_end(ap);
}

void staticBufferStream::vprint(const char *format, va_list argptr) {
  size_t len;
  const char* str = do_vsnprintf(_buffer, _buflen, format, argptr, false, len);
  write(str, len);
}

void staticBufferStream::vprint_cr(const char* format, va_list argptr) {
  size_t len;
  const char* str = do_vsnprintf(_buffer, _buflen, format, argptr, true, len);
  write(str, len);
}

bufferedStream::bufferedStream(size_t initial_size, size_t bufmax) : outputStream() {
  buffer_length = initial_size;
  buffer        = NEW_C_HEAP_ARRAY(char, buffer_length, mtInternal);
  buffer_pos    = 0;
  buffer_fixed  = false;
  buffer_max    = bufmax;
}

bufferedStream::bufferedStream(char* fixed_buffer, size_t fixed_buffer_size, size_t bufmax) : outputStream() {
  buffer_length = fixed_buffer_size;
  buffer        = fixed_buffer;
  buffer_pos    = 0;
  buffer_fixed  = true;
  buffer_max    = bufmax;
}

void bufferedStream::write(const char* s, size_t len) {

  if(buffer_pos + len > buffer_max) {
    flush();
  }

  size_t end = buffer_pos + len;
  if (end >= buffer_length) {
    if (buffer_fixed) {
      // if buffer cannot resize, silently truncate
      len = buffer_length - buffer_pos - 1;
    } else {
      // For small overruns, double the buffer.  For larger ones,
      // increase to the requested size.
      if (end < buffer_length * 2) {
        end = buffer_length * 2;
      }
      buffer = REALLOC_C_HEAP_ARRAY(char, buffer, end, mtInternal);
      buffer_length = end;
    }
  }
  memcpy(buffer + buffer_pos, s, len);
  buffer_pos += len;
  update_position(s, len);
}

char* bufferedStream::as_string() {
  char* copy = NEW_RESOURCE_ARRAY(char, buffer_pos+1);
  strncpy(copy, buffer, buffer_pos);
  copy[buffer_pos] = 0;  // terminating null
  return copy;
}

bufferedStream::~bufferedStream() {
  if (!buffer_fixed) {
    FREE_C_HEAP_ARRAY(char, buffer, mtInternal);
  }
}

#ifndef PRODUCT

#if defined(SOLARIS) || defined(LINUX) || defined(AIX) || defined(_ALLBSD_SOURCE)
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#endif

// Network access
networkStream::networkStream() : bufferedStream(1024*10, 1024*10) {

  _socket = -1;

  int result = os::socket(AF_INET, SOCK_STREAM, 0);
  if (result <= 0) {
    assert(false, "Socket could not be created!");
  } else {
    _socket = result;
  }
}

int networkStream::read(char *buf, size_t len) {
  return os::recv(_socket, buf, (int)len, 0);
}

void networkStream::flush() {
  if (size() != 0) {
    int result = os::raw_send(_socket, (char *)base(), size(), 0);
    assert(result != -1, "connection error");
    assert(result == (int)size(), "didn't send enough data");
  }
  reset();
}

networkStream::~networkStream() {
  close();
}

void networkStream::close() {
  if (_socket != -1) {
    flush();
    os::socket_close(_socket);
    _socket = -1;
  }
}

bool networkStream::connect(const char *ip, short port) {

  struct sockaddr_in server;
  server.sin_family = AF_INET;
  server.sin_port = htons(port);

  server.sin_addr.s_addr = inet_addr(ip);
  if (server.sin_addr.s_addr == (uint32_t)-1) {
    struct hostent* host = os::get_host_by_name((char*)ip);
    if (host != NULL) {
      memcpy(&server.sin_addr, host->h_addr_list[0], host->h_length);
    } else {
      return false;
    }
  }


  int result = os::connect(_socket, (struct sockaddr*)&server, sizeof(struct sockaddr_in));
  return (result >= 0);
}

#endif
