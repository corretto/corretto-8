/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.sampled;

import java.util.Arrays;

/**
 * <code>DataLine</code> adds media-related functionality to its
 * superinterface, <code>{@link Line}</code>.  This functionality includes
 * transport-control methods that start, stop, drain, and flush
 * the audio data that passes through the line.  A data line can also
 * report the current position, volume, and audio format of the media.
 * Data lines are used for output of audio by means of the
 * subinterfaces <code>{@link SourceDataLine}</code> or
 * <code>{@link Clip}</code>, which allow an application program to write data.  Similarly,
 * audio input is handled by the subinterface <code>{@link TargetDataLine}</code>,
 * which allows data to be read.
 * <p>
 * A data line has an internal buffer in which
 * the incoming or outgoing audio data is queued.  The
 * <code>{@link #drain()}</code> method blocks until this internal buffer
 * becomes empty, usually because all queued data has been processed.  The
 * <code>{@link #flush()}</code> method discards any available queued data
 * from the internal buffer.
 * <p>
 * A data line produces <code>{@link LineEvent.Type#START START}</code> and
 * <code>{@link LineEvent.Type#STOP STOP}</code> events whenever
 * it begins or ceases active presentation or capture of data.  These events
 * can be generated in response to specific requests, or as a result of
 * less direct state changes.  For example, if <code>{@link #start()}</code> is called
 * on an inactive data line, and data is available for capture or playback, a
 * <code>START</code> event will be generated shortly, when data playback
 * or capture actually begins.  Or, if the flow of data to an active data
 * line is constricted so that a gap occurs in the presentation of data,
 * a <code>STOP</code> event is generated.
 * <p>
 * Mixers often support synchronized control of multiple data lines.
 * Synchronization can be established through the Mixer interface's
 * <code>{@link Mixer#synchronize synchronize}</code> method.
 * See the description of the <code>{@link Mixer Mixer}</code> interface
 * for a more complete description.
 *
 * @author Kara Kytle
 * @see LineEvent
 * @since 1.3
 */
public interface DataLine extends Line {


    /**
     * Drains queued data from the line by continuing data I/O until the
     * data line's internal buffer has been emptied.
     * This method blocks until the draining is complete.  Because this is a
     * blocking method, it should be used with care.  If <code>drain()</code>
     * is invoked on a stopped line that has data in its queue, the method will
     * block until the line is running and the data queue becomes empty.  If
     * <code>drain()</code> is invoked by one thread, and another continues to
     * fill the data queue, the operation will not complete.
     * This method always returns when the data line is closed.
     *
     * @see #flush()
     */
    public void drain();

    /**
     * Flushes queued data from the line.  The flushed data is discarded.
     * In some cases, not all queued data can be discarded.  For example, a
     * mixer can flush data from the buffer for a specific input line, but any
     * unplayed data already in the output buffer (the result of the mix) will
     * still be played.  You can invoke this method after pausing a line (the
     * normal case) if you want to skip the "stale" data when you restart
     * playback or capture. (It is legal to flush a line that is not stopped,
     * but doing so on an active line is likely to cause a discontinuity in the
     * data, resulting in a perceptible click.)
     *
     * @see #stop()
     * @see #drain()
     */
    public void flush();

    /**
     * Allows a line to engage in data I/O.  If invoked on a line
     * that is already running, this method does nothing.  Unless the data in
     * the buffer has been flushed, the line resumes I/O starting
     * with the first frame that was unprocessed at the time the line was
     * stopped. When audio capture or playback starts, a
     * <code>{@link LineEvent.Type#START START}</code> event is generated.
     *
     * @see #stop()
     * @see #isRunning()
     * @see LineEvent
     */
    public void start();

    /**
     * Stops the line.  A stopped line should cease I/O activity.
     * If the line is open and running, however, it should retain the resources required
     * to resume activity.  A stopped line should retain any audio data in its buffer
     * instead of discarding it, so that upon resumption the I/O can continue where it left off,
     * if possible.  (This doesn't guarantee that there will never be discontinuities beyond the
     * current buffer, of course; if the stopped condition continues
     * for too long, input or output samples might be dropped.)  If desired, the retained data can be
     * discarded by invoking the <code>flush</code> method.
     * When audio capture or playback stops, a <code>{@link LineEvent.Type#STOP STOP}</code> event is generated.
     *
     * @see #start()
     * @see #isRunning()
     * @see #flush()
     * @see LineEvent
     */
    public void stop();

    /**
     * Indicates whether the line is running.  The default is <code>false</code>.
     * An open line begins running when the first data is presented in response to an
     * invocation of the <code>start</code> method, and continues
     * until presentation ceases in response to a call to <code>stop</code> or
     * because playback completes.
     * @return <code>true</code> if the line is running, otherwise <code>false</code>
     * @see #start()
     * @see #stop()
     */
    public boolean isRunning();

    /**
     * Indicates whether the line is engaging in active I/O (such as playback
     * or capture).  When an inactive line becomes active, it sends a
     * <code>{@link LineEvent.Type#START START}</code> event to its listeners.  Similarly, when
     * an active line becomes inactive, it sends a
     * <code>{@link LineEvent.Type#STOP STOP}</code> event.
     * @return <code>true</code> if the line is actively capturing or rendering
     * sound, otherwise <code>false</code>
     * @see #isOpen
     * @see #addLineListener
     * @see #removeLineListener
     * @see LineEvent
     * @see LineListener
     */
    public boolean isActive();

    /**
     * Obtains the current format (encoding, sample rate, number of channels,
     * etc.) of the data line's audio data.
     *
     * <p>If the line is not open and has never been opened, it returns
     * the default format. The default format is an implementation
     * specific audio format, or, if the <code>DataLine.Info</code>
     * object, which was used to retrieve this <code>DataLine</code>,
     * specifies at least one fully qualified audio format, the
     * last one will be used as the default format. Opening the
     * line with a specific audio format (e.g.
     * {@link SourceDataLine#open(AudioFormat)}) will override the
     * default format.
     *
     * @return current audio data format
     * @see AudioFormat
     */
    public AudioFormat getFormat();

    /**
     * Obtains the maximum number of bytes of data that will fit in the data line's
     * internal buffer.  For a source data line, this is the size of the buffer to
     * which data can be written.  For a target data line, it is the size of
     * the buffer from which data can be read.  Note that
     * the units used are bytes, but will always correspond to an integral
     * number of sample frames of audio data.
     *
     * @return the size of the buffer in bytes
     */
    public int getBufferSize();

    /**
     * Obtains the number of bytes of data currently available to the
     * application for processing in the data line's internal buffer.  For a
     * source data line, this is the amount of data that can be written to the
     * buffer without blocking.  For a target data line, this is the amount of data
     * available to be read by the application.  For a clip, this value is always
     * 0 because the audio data is loaded into the buffer when the clip is opened,
     * and persists without modification until the clip is closed.
     * <p>
     * Note that the units used are bytes, but will always
     * correspond to an integral number of sample frames of audio data.
     * <p>
     * An application is guaranteed that a read or
     * write operation of up to the number of bytes returned from
     * <code>available()</code> will not block; however, there is no guarantee
     * that attempts to read or write more data will block.
     *
     * @return the amount of data available, in bytes
     */
    public int available();

    /**
     * Obtains the current position in the audio data, in sample frames.
     * The frame position measures the number of sample
     * frames captured by, or rendered from, the line since it was opened.
     * This return value will wrap around after 2^31 frames. It is recommended
     * to use <code>getLongFramePosition</code> instead.
     *
     * @return the number of frames already processed since the line was opened
     * @see #getLongFramePosition()
     */
    public int getFramePosition();


    /**
     * Obtains the current position in the audio data, in sample frames.
     * The frame position measures the number of sample
     * frames captured by, or rendered from, the line since it was opened.
     *
     * @return the number of frames already processed since the line was opened
     * @since 1.5
     */
    public long getLongFramePosition();


    /**
     * Obtains the current position in the audio data, in microseconds.
     * The microsecond position measures the time corresponding to the number
     * of sample frames captured by, or rendered from, the line since it was opened.
     * The level of precision is not guaranteed.  For example, an implementation
     * might calculate the microsecond position from the current frame position
     * and the audio sample frame rate.  The precision in microseconds would
     * then be limited to the number of microseconds per sample frame.
     *
     * @return the number of microseconds of data processed since the line was opened
     */
    public long getMicrosecondPosition();

    /**
     * Obtains the current volume level for the line.  This level is a measure
     * of the signal's current amplitude, and should not be confused with the
     * current setting of a gain control. The range is from 0.0 (silence) to
     * 1.0 (maximum possible amplitude for the sound waveform).  The units
     * measure linear amplitude, not decibels.
     *
     * @return the current amplitude of the signal in this line, or
     * <code>{@link AudioSystem#NOT_SPECIFIED}</code>
     */
    public float getLevel();

    /**
     * Besides the class information inherited from its superclass,
     * <code>DataLine.Info</code> provides additional information specific to data lines.
     * This information includes:
     * <ul>
     * <li> the audio formats supported by the data line
     * <li> the minimum and maximum sizes of its internal buffer
     * </ul>
     * Because a <code>Line.Info</code> knows the class of the line its describes, a
     * <code>DataLine.Info</code> object can describe <code>DataLine</code>
     * subinterfaces such as <code>{@link SourceDataLine}</code>,
     * <code>{@link TargetDataLine}</code>, and <code>{@link Clip}</code>.
     * You can query a mixer for lines of any of these types, passing an appropriate
     * instance of <code>DataLine.Info</code> as the argument to a method such as
     * <code>{@link Mixer#getLine Mixer.getLine(Line.Info)}</code>.
     *
     * @see Line.Info
     * @author Kara Kytle
     * @since 1.3
     */
    public static class Info extends Line.Info {

        private final AudioFormat[] formats;
        private final int minBufferSize;
        private final int maxBufferSize;

        /**
         * Constructs a data line's info object from the specified information,
         * which includes a set of supported audio formats and a range for the buffer size.
         * This constructor is typically used by mixer implementations
         * when returning information about a supported line.
         *
         * @param lineClass the class of the data line described by the info object
         * @param formats set of formats supported
         * @param minBufferSize minimum buffer size supported by the data line, in bytes
         * @param maxBufferSize maximum buffer size supported by the data line, in bytes
         */
        public Info(Class<?> lineClass, AudioFormat[] formats, int minBufferSize, int maxBufferSize) {

            super(lineClass);

            if (formats == null) {
                this.formats = new AudioFormat[0];
            } else {
                this.formats = Arrays.copyOf(formats, formats.length);
            }

            this.minBufferSize = minBufferSize;
            this.maxBufferSize = maxBufferSize;
        }


        /**
         * Constructs a data line's info object from the specified information,
         * which includes a single audio format and a desired buffer size.
         * This constructor is typically used by an application to
         * describe a desired line.
         *
         * @param lineClass the class of the data line described by the info object
         * @param format desired format
         * @param bufferSize desired buffer size in bytes
         */
        public Info(Class<?> lineClass, AudioFormat format, int bufferSize) {

            super(lineClass);

            if (format == null) {
                this.formats = new AudioFormat[0];
            } else {
                this.formats = new AudioFormat[]{format};
            }

            this.minBufferSize = bufferSize;
            this.maxBufferSize = bufferSize;
        }


        /**
         * Constructs a data line's info object from the specified information,
         * which includes a single audio format.
         * This constructor is typically used by an application to
         * describe a desired line.
         *
         * @param lineClass the class of the data line described by the info object
         * @param format desired format
         */
        public Info(Class<?> lineClass, AudioFormat format) {
            this(lineClass, format, AudioSystem.NOT_SPECIFIED);
        }


        /**
         * Obtains a set of audio formats supported by the data line.
         * Note that <code>isFormatSupported(AudioFormat)</code> might return
         * <code>true</code> for certain additional formats that are missing from
         * the set returned by <code>getFormats()</code>.  The reverse is not
         * the case: <code>isFormatSupported(AudioFormat)</code> is guaranteed to return
         * <code>true</code> for all formats returned by <code>getFormats()</code>.
         *
         * Some fields in the AudioFormat instances can be set to
         * {@link javax.sound.sampled.AudioSystem#NOT_SPECIFIED NOT_SPECIFIED}
         * if that field does not apply to the format,
         * or if the format supports a wide range of values for that field.
         * For example, a multi-channel device supporting up to
         * 64 channels, could set the channel field in the
         * <code>AudioFormat</code> instances returned by this
         * method to <code>NOT_SPECIFIED</code>.
         *
         * @return a set of supported audio formats.
         * @see #isFormatSupported(AudioFormat)
         */
        public AudioFormat[] getFormats() {
            return Arrays.copyOf(formats, formats.length);
        }

        /**
         * Indicates whether this data line supports a particular audio format.
         * The default implementation of this method simply returns <code>true</code> if
         * the specified format matches any of the supported formats.
         *
         * @param format the audio format for which support is queried.
         * @return <code>true</code> if the format is supported, otherwise <code>false</code>
         * @see #getFormats
         * @see AudioFormat#matches
         */
        public boolean isFormatSupported(AudioFormat format) {

            for (int i = 0; i < formats.length; i++) {
                if (format.matches(formats[i])) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Obtains the minimum buffer size supported by the data line.
         * @return minimum buffer size in bytes, or <code>AudioSystem.NOT_SPECIFIED</code>
         */
        public int getMinBufferSize() {
            return minBufferSize;
        }


        /**
         * Obtains the maximum buffer size supported by the data line.
         * @return maximum buffer size in bytes, or <code>AudioSystem.NOT_SPECIFIED</code>
         */
        public int getMaxBufferSize() {
            return maxBufferSize;
        }


        /**
         * Determines whether the specified info object matches this one.
         * To match, the superclass match requirements must be met.  In
         * addition, this object's minimum buffer size must be at least as
         * large as that of the object specified, its maximum buffer size must
         * be at most as large as that of the object specified, and all of its
         * formats must match formats supported by the object specified.
         * @return <code>true</code> if this object matches the one specified,
         * otherwise <code>false</code>.
         */
        public boolean matches(Line.Info info) {

            if (! (super.matches(info)) ) {
                return false;
            }

            Info dataLineInfo = (Info)info;

            // treat anything < 0 as NOT_SPECIFIED
            // demo code in old Java Sound Demo used a wrong buffer calculation
            // that would lead to arbitrary negative values
            if ((getMaxBufferSize() >= 0) && (dataLineInfo.getMaxBufferSize() >= 0)) {
                if (getMaxBufferSize() > dataLineInfo.getMaxBufferSize()) {
                    return false;
                }
            }

            if ((getMinBufferSize() >= 0) && (dataLineInfo.getMinBufferSize() >= 0)) {
                if (getMinBufferSize() < dataLineInfo.getMinBufferSize()) {
                    return false;
                }
            }

            AudioFormat[] localFormats = getFormats();

            if (localFormats != null) {

                for (int i = 0; i < localFormats.length; i++) {
                    if (! (localFormats[i] == null) ) {
                        if (! (dataLineInfo.isFormatSupported(localFormats[i])) ) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }

        /**
         * Obtains a textual description of the data line info.
         * @return a string description
         */
        public String toString() {

            StringBuffer buf = new StringBuffer();

            if ( (formats.length == 1) && (formats[0] != null) ) {
                buf.append(" supporting format " + formats[0]);
            } else if (getFormats().length > 1) {
                buf.append(" supporting " + getFormats().length + " audio formats");
            }

            if ( (minBufferSize != AudioSystem.NOT_SPECIFIED) && (maxBufferSize != AudioSystem.NOT_SPECIFIED) ) {
                buf.append(", and buffers of " + minBufferSize + " to " + maxBufferSize + " bytes");
            } else if ( (minBufferSize != AudioSystem.NOT_SPECIFIED) && (minBufferSize > 0) ) {
                buf.append(", and buffers of at least " + minBufferSize + " bytes");
            } else if (maxBufferSize != AudioSystem.NOT_SPECIFIED) {
                buf.append(", and buffers of up to " + minBufferSize + " bytes");
            }

            return new String(super.toString() + buf);
        }
    } // class Info

} // interface DataLine
