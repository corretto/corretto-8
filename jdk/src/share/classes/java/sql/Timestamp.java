/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

package java.sql;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.StringTokenizer;

/**
 * <P>A thin wrapper around <code>java.util.Date</code> that allows
 * the JDBC API to identify this as an SQL <code>TIMESTAMP</code> value.
 * It adds the ability
 * to hold the SQL <code>TIMESTAMP</code> fractional seconds value, by allowing
 * the specification of fractional seconds to a precision of nanoseconds.
 * A Timestamp also provides formatting and
 * parsing operations to support the JDBC escape syntax for timestamp values.
 *
 * <p>The precision of a Timestamp object is calculated to be either:
 * <ul>
 * <li><code>19 </code>, which is the number of characters in yyyy-mm-dd hh:mm:ss
 * <li> <code> 20 + s </code>, which is the number
 * of characters in the yyyy-mm-dd hh:mm:ss.[fff...] and <code>s</code> represents  the scale of the given Timestamp,
 * its fractional seconds precision.
 *</ul>
 *
 * <P><B>Note:</B> This type is a composite of a <code>java.util.Date</code> and a
 * separate nanoseconds value. Only integral seconds are stored in the
 * <code>java.util.Date</code> component. The fractional seconds - the nanos - are
 * separate.  The <code>Timestamp.equals(Object)</code> method never returns
 * <code>true</code> when passed an object
 * that isn't an instance of <code>java.sql.Timestamp</code>,
 * because the nanos component of a date is unknown.
 * As a result, the <code>Timestamp.equals(Object)</code>
 * method is not symmetric with respect to the
 * <code>java.util.Date.equals(Object)</code>
 * method.  Also, the <code>hashCode</code> method uses the underlying
 * <code>java.util.Date</code>
 * implementation and therefore does not include nanos in its computation.
 * <P>
 * Due to the differences between the <code>Timestamp</code> class
 * and the <code>java.util.Date</code>
 * class mentioned above, it is recommended that code not view
 * <code>Timestamp</code> values generically as an instance of
 * <code>java.util.Date</code>.  The
 * inheritance relationship between <code>Timestamp</code>
 * and <code>java.util.Date</code> really
 * denotes implementation inheritance, and not type inheritance.
 */
public class Timestamp extends java.util.Date {

    /**
     * Constructs a <code>Timestamp</code> object initialized
     * with the given values.
     *
     * @param year the year minus 1900
     * @param month 0 to 11
     * @param date 1 to 31
     * @param hour 0 to 23
     * @param minute 0 to 59
     * @param second 0 to 59
     * @param nano 0 to 999,999,999
     * @deprecated instead use the constructor <code>Timestamp(long millis)</code>
     * @exception IllegalArgumentException if the nano argument is out of bounds
     */
    @Deprecated
    public Timestamp(int year, int month, int date,
                     int hour, int minute, int second, int nano) {
        super(year, month, date, hour, minute, second);
        if (nano > 999999999 || nano < 0) {
            throw new IllegalArgumentException("nanos > 999999999 or < 0");
        }
        nanos = nano;
    }

    /**
     * Constructs a <code>Timestamp</code> object
     * using a milliseconds time value. The
     * integral seconds are stored in the underlying date value; the
     * fractional seconds are stored in the <code>nanos</code> field of
     * the <code>Timestamp</code> object.
     *
     * @param time milliseconds since January 1, 1970, 00:00:00 GMT.
     *        A negative number is the number of milliseconds before
     *         January 1, 1970, 00:00:00 GMT.
     * @see java.util.Calendar
     */
    public Timestamp(long time) {
        super((time/1000)*1000);
        nanos = (int)((time%1000) * 1000000);
        if (nanos < 0) {
            nanos = 1000000000 + nanos;
            super.setTime(((time/1000)-1)*1000);
        }
    }

    /**
     * Sets this <code>Timestamp</code> object to represent a point in time that is
     * <tt>time</tt> milliseconds after January 1, 1970 00:00:00 GMT.
     *
     * @param time   the number of milliseconds.
     * @see #getTime
     * @see #Timestamp(long time)
     * @see java.util.Calendar
     */
    public void setTime(long time) {
        super.setTime((time/1000)*1000);
        nanos = (int)((time%1000) * 1000000);
        if (nanos < 0) {
            nanos = 1000000000 + nanos;
            super.setTime(((time/1000)-1)*1000);
        }
    }

    /**
     * Returns the number of milliseconds since January 1, 1970, 00:00:00 GMT
     * represented by this <code>Timestamp</code> object.
     *
     * @return  the number of milliseconds since January 1, 1970, 00:00:00 GMT
     *          represented by this date.
     * @see #setTime
     */
    public long getTime() {
        long time = super.getTime();
        return (time + (nanos / 1000000));
    }


    /**
     * @serial
     */
    private int nanos;

    /**
     * Converts a <code>String</code> object in JDBC timestamp escape format to a
     * <code>Timestamp</code> value.
     *
     * @param s timestamp in format <code>yyyy-[m]m-[d]d hh:mm:ss[.f...]</code>.  The
     * fractional seconds may be omitted. The leading zero for <code>mm</code>
     * and <code>dd</code> may also be omitted.
     *
     * @return corresponding <code>Timestamp</code> value
     * @exception java.lang.IllegalArgumentException if the given argument
     * does not have the format <code>yyyy-[m]m-[d]d hh:mm:ss[.f...]</code>
     */
    public static Timestamp valueOf(String s) {
        final int YEAR_LENGTH = 4;
        final int MONTH_LENGTH = 2;
        final int DAY_LENGTH = 2;
        final int MAX_MONTH = 12;
        final int MAX_DAY = 31;
        String date_s;
        String time_s;
        String nanos_s;
        int year = 0;
        int month = 0;
        int day = 0;
        int hour;
        int minute;
        int second;
        int a_nanos = 0;
        int firstDash;
        int secondDash;
        int dividingSpace;
        int firstColon = 0;
        int secondColon = 0;
        int period = 0;
        String formatError = "Timestamp format must be yyyy-mm-dd hh:mm:ss[.fffffffff]";
        String zeros = "000000000";
        String delimiterDate = "-";
        String delimiterTime = ":";

        if (s == null) throw new java.lang.IllegalArgumentException("null string");

        // Split the string into date and time components
        s = s.trim();
        dividingSpace = s.indexOf(' ');
        if (dividingSpace > 0) {
            date_s = s.substring(0,dividingSpace);
            time_s = s.substring(dividingSpace+1);
        } else {
            throw new java.lang.IllegalArgumentException(formatError);
        }

        // Parse the date
        firstDash = date_s.indexOf('-');
        secondDash = date_s.indexOf('-', firstDash+1);

        // Parse the time
        if (time_s == null)
            throw new java.lang.IllegalArgumentException(formatError);
        firstColon = time_s.indexOf(':');
        secondColon = time_s.indexOf(':', firstColon+1);
        period = time_s.indexOf('.', secondColon+1);

        // Convert the date
        boolean parsedDate = false;
        if ((firstDash > 0) && (secondDash > 0) && (secondDash < date_s.length() - 1)) {
            String yyyy = date_s.substring(0, firstDash);
            String mm = date_s.substring(firstDash + 1, secondDash);
            String dd = date_s.substring(secondDash + 1);
            if (yyyy.length() == YEAR_LENGTH &&
                    (mm.length() >= 1 && mm.length() <= MONTH_LENGTH) &&
                    (dd.length() >= 1 && dd.length() <= DAY_LENGTH)) {
                 year = Integer.parseInt(yyyy);
                 month = Integer.parseInt(mm);
                 day = Integer.parseInt(dd);

                if ((month >= 1 && month <= MAX_MONTH) && (day >= 1 && day <= MAX_DAY)) {
                    parsedDate = true;
                }
            }
        }
        if (! parsedDate) {
            throw new java.lang.IllegalArgumentException(formatError);
        }

        // Convert the time; default missing nanos
        if ((firstColon > 0) & (secondColon > 0) &
            (secondColon < time_s.length()-1)) {
            hour = Integer.parseInt(time_s.substring(0, firstColon));
            minute =
                Integer.parseInt(time_s.substring(firstColon+1, secondColon));
            if ((period > 0) & (period < time_s.length()-1)) {
                second =
                    Integer.parseInt(time_s.substring(secondColon+1, period));
                nanos_s = time_s.substring(period+1);
                if (nanos_s.length() > 9)
                    throw new java.lang.IllegalArgumentException(formatError);
                if (!Character.isDigit(nanos_s.charAt(0)))
                    throw new java.lang.IllegalArgumentException(formatError);
                nanos_s = nanos_s + zeros.substring(0,9-nanos_s.length());
                a_nanos = Integer.parseInt(nanos_s);
            } else if (period > 0) {
                throw new java.lang.IllegalArgumentException(formatError);
            } else {
                second = Integer.parseInt(time_s.substring(secondColon+1));
            }
        } else {
            throw new java.lang.IllegalArgumentException(formatError);
        }

        return new Timestamp(year - 1900, month - 1, day, hour, minute, second, a_nanos);
    }

    /**
     * Formats a timestamp in JDBC timestamp escape format.
     *         <code>yyyy-mm-dd hh:mm:ss.fffffffff</code>,
     * where <code>ffffffffff</code> indicates nanoseconds.
     * <P>
     * @return a <code>String</code> object in
     *           <code>yyyy-mm-dd hh:mm:ss.fffffffff</code> format
     */
    @SuppressWarnings("deprecation")
    public String toString () {

        int year = super.getYear() + 1900;
        int month = super.getMonth() + 1;
        int day = super.getDate();
        int hour = super.getHours();
        int minute = super.getMinutes();
        int second = super.getSeconds();
        String yearString;
        String monthString;
        String dayString;
        String hourString;
        String minuteString;
        String secondString;
        String nanosString;
        String zeros = "000000000";
        String yearZeros = "0000";
        StringBuffer timestampBuf;

        if (year < 1000) {
            // Add leading zeros
            yearString = "" + year;
            yearString = yearZeros.substring(0, (4-yearString.length())) +
                yearString;
        } else {
            yearString = "" + year;
        }
        if (month < 10) {
            monthString = "0" + month;
        } else {
            monthString = Integer.toString(month);
        }
        if (day < 10) {
            dayString = "0" + day;
        } else {
            dayString = Integer.toString(day);
        }
        if (hour < 10) {
            hourString = "0" + hour;
        } else {
            hourString = Integer.toString(hour);
        }
        if (minute < 10) {
            minuteString = "0" + minute;
        } else {
            minuteString = Integer.toString(minute);
        }
        if (second < 10) {
            secondString = "0" + second;
        } else {
            secondString = Integer.toString(second);
        }
        if (nanos == 0) {
            nanosString = "0";
        } else {
            nanosString = Integer.toString(nanos);

            // Add leading zeros
            nanosString = zeros.substring(0, (9-nanosString.length())) +
                nanosString;

            // Truncate trailing zeros
            char[] nanosChar = new char[nanosString.length()];
            nanosString.getChars(0, nanosString.length(), nanosChar, 0);
            int truncIndex = 8;
            while (nanosChar[truncIndex] == '0') {
                truncIndex--;
            }

            nanosString = new String(nanosChar, 0, truncIndex + 1);
        }

        // do a string buffer here instead.
        timestampBuf = new StringBuffer(20+nanosString.length());
        timestampBuf.append(yearString);
        timestampBuf.append("-");
        timestampBuf.append(monthString);
        timestampBuf.append("-");
        timestampBuf.append(dayString);
        timestampBuf.append(" ");
        timestampBuf.append(hourString);
        timestampBuf.append(":");
        timestampBuf.append(minuteString);
        timestampBuf.append(":");
        timestampBuf.append(secondString);
        timestampBuf.append(".");
        timestampBuf.append(nanosString);

        return (timestampBuf.toString());
    }

    /**
     * Gets this <code>Timestamp</code> object's <code>nanos</code> value.
     *
     * @return this <code>Timestamp</code> object's fractional seconds component
     * @see #setNanos
     */
    public int getNanos() {
        return nanos;
    }

    /**
     * Sets this <code>Timestamp</code> object's <code>nanos</code> field
     * to the given value.
     *
     * @param n the new fractional seconds component
     * @exception java.lang.IllegalArgumentException if the given argument
     *            is greater than 999999999 or less than 0
     * @see #getNanos
     */
    public void setNanos(int n) {
        if (n > 999999999 || n < 0) {
            throw new IllegalArgumentException("nanos > 999999999 or < 0");
        }
        nanos = n;
    }

    /**
     * Tests to see if this <code>Timestamp</code> object is
     * equal to the given <code>Timestamp</code> object.
     *
     * @param ts the <code>Timestamp</code> value to compare with
     * @return <code>true</code> if the given <code>Timestamp</code>
     *         object is equal to this <code>Timestamp</code> object;
     *         <code>false</code> otherwise
     */
    public boolean equals(Timestamp ts) {
        if (super.equals(ts)) {
            if  (nanos == ts.nanos) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Tests to see if this <code>Timestamp</code> object is
     * equal to the given object.
     *
     * This version of the method <code>equals</code> has been added
     * to fix the incorrect
     * signature of <code>Timestamp.equals(Timestamp)</code> and to preserve backward
     * compatibility with existing class files.
     *
     * Note: This method is not symmetric with respect to the
     * <code>equals(Object)</code> method in the base class.
     *
     * @param ts the <code>Object</code> value to compare with
     * @return <code>true</code> if the given <code>Object</code> is an instance
     *         of a <code>Timestamp</code> that
     *         is equal to this <code>Timestamp</code> object;
     *         <code>false</code> otherwise
     */
    public boolean equals(java.lang.Object ts) {
      if (ts instanceof Timestamp) {
        return this.equals((Timestamp)ts);
      } else {
        return false;
      }
    }

    /**
     * Indicates whether this <code>Timestamp</code> object is
     * earlier than the given <code>Timestamp</code> object.
     *
     * @param ts the <code>Timestamp</code> value to compare with
     * @return <code>true</code> if this <code>Timestamp</code> object is earlier;
     *        <code>false</code> otherwise
     */
    public boolean before(Timestamp ts) {
        return compareTo(ts) < 0;
    }

    /**
     * Indicates whether this <code>Timestamp</code> object is
     * later than the given <code>Timestamp</code> object.
     *
     * @param ts the <code>Timestamp</code> value to compare with
     * @return <code>true</code> if this <code>Timestamp</code> object is later;
     *        <code>false</code> otherwise
     */
    public boolean after(Timestamp ts) {
        return compareTo(ts) > 0;
    }

    /**
     * Compares this <code>Timestamp</code> object to the given
     * <code>Timestamp</code> object.
     *
     * @param   ts   the <code>Timestamp</code> object to be compared to
     *                this <code>Timestamp</code> object
     * @return  the value <code>0</code> if the two <code>Timestamp</code>
     *          objects are equal; a value less than <code>0</code> if this
     *          <code>Timestamp</code> object is before the given argument;
     *          and a value greater than <code>0</code> if this
     *          <code>Timestamp</code> object is after the given argument.
     * @since   1.4
     */
    public int compareTo(Timestamp ts) {
        long thisTime = this.getTime();
        long anotherTime = ts.getTime();
        int i = (thisTime<anotherTime ? -1 :(thisTime==anotherTime?0 :1));
        if (i == 0) {
            if (nanos > ts.nanos) {
                    return 1;
            } else if (nanos < ts.nanos) {
                return -1;
            }
        }
        return i;
    }

    /**
     * Compares this <code>Timestamp</code> object to the given
     * <code>Date</code> object.
     *
     * @param o the <code>Date</code> to be compared to
     *          this <code>Timestamp</code> object
     * @return  the value <code>0</code> if this <code>Timestamp</code> object
     *          and the given object are equal; a value less than <code>0</code>
     *          if this  <code>Timestamp</code> object is before the given argument;
     *          and a value greater than <code>0</code> if this
     *          <code>Timestamp</code> object is after the given argument.
     *
     * @since   1.5
     */
    public int compareTo(java.util.Date o) {
       if(o instanceof Timestamp) {
            // When Timestamp instance compare it with a Timestamp
            // Hence it is basically calling this.compareTo((Timestamp))o);
            // Note typecasting is safe because o is instance of Timestamp
           return compareTo((Timestamp)o);
      } else {
            // When Date doing a o.compareTo(this)
            // will give wrong results.
          Timestamp ts = new Timestamp(o.getTime());
          return this.compareTo(ts);
      }
    }

    /**
     * {@inheritDoc}
     *
     * The {@code hashCode} method uses the underlying {@code java.util.Date}
     * implementation and therefore does not include nanos in its computation.
     *
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    static final long serialVersionUID = 2745179027874758501L;

    private static final int MILLIS_PER_SECOND = 1000;

    /**
     * Obtains an instance of {@code Timestamp} from a {@code LocalDateTime}
     * object, with the same year, month, day of month, hours, minutes,
     * seconds and nanos date-time value as the provided {@code LocalDateTime}.
     * <p>
     * The provided {@code LocalDateTime} is interpreted as the local
     * date-time in the local time zone.
     *
     * @param dateTime a {@code LocalDateTime} to convert
     * @return a {@code Timestamp} object
     * @exception NullPointerException if {@code dateTime} is null.
     * @since 1.8
     */
    @SuppressWarnings("deprecation")
    public static Timestamp valueOf(LocalDateTime dateTime) {
        return new Timestamp(dateTime.getYear() - 1900,
                             dateTime.getMonthValue() - 1,
                             dateTime.getDayOfMonth(),
                             dateTime.getHour(),
                             dateTime.getMinute(),
                             dateTime.getSecond(),
                             dateTime.getNano());
    }

    /**
     * Converts this {@code Timestamp} object to a {@code LocalDateTime}.
     * <p>
     * The conversion creates a {@code LocalDateTime} that represents the
     * same year, month, day of month, hours, minutes, seconds and nanos
     * date-time value as this {@code Timestamp} in the local time zone.
     *
     * @return a {@code LocalDateTime} object representing the same date-time value
     * @since 1.8
     */
    @SuppressWarnings("deprecation")
    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.of(getYear() + 1900,
                                getMonth() + 1,
                                getDate(),
                                getHours(),
                                getMinutes(),
                                getSeconds(),
                                getNanos());
    }

    /**
     * Obtains an instance of {@code Timestamp} from an {@link Instant} object.
     * <p>
     * {@code Instant} can store points on the time-line further in the future
     * and further in the past than {@code Date}. In this scenario, this method
     * will throw an exception.
     *
     * @param instant  the instant to convert
     * @return an {@code Timestamp} representing the same point on the time-line as
     *  the provided instant
     * @exception NullPointerException if {@code instant} is null.
     * @exception IllegalArgumentException if the instant is too large to
     *  represent as a {@code Timesamp}
     * @since 1.8
     */
    public static Timestamp from(Instant instant) {
        try {
            Timestamp stamp = new Timestamp(instant.getEpochSecond() * MILLIS_PER_SECOND);
            stamp.nanos = instant.getNano();
            return stamp;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Converts this {@code Timestamp} object to an {@code Instant}.
     * <p>
     * The conversion creates an {@code Instant} that represents the same
     * point on the time-line as this {@code Timestamp}.
     *
     * @return an instant representing the same point on the time-line
     * @since 1.8
     */
    @Override
    public Instant toInstant() {
        return Instant.ofEpochSecond(super.getTime() / MILLIS_PER_SECOND, nanos);
    }
}
