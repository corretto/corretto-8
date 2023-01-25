/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.sampled.spi;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Provider for audio file reading services.  Classes providing concrete
 * implementations can parse the format information from one or more types of
 * audio file, and can produce audio input streams from files of these types.
 *
 * @author Kara Kytle
 * @since 1.3
 */
public abstract class AudioFileReader {

    /**
     * Obtains the audio file format of the input stream provided.  The stream must
     * point to valid audio file data.  In general, audio file readers may
     * need to read some data from the stream before determining whether they
     * support it.  These parsers must
     * be able to mark the stream, read enough data to determine whether they
     * support the stream, and, if not, reset the stream's read pointer to its original
     * position.  If the input stream does not support this, this method may fail
     * with an <code>IOException</code>.
     * @param stream the input stream from which file format information should be
     * extracted
     * @return an <code>AudioFileFormat</code> object describing the audio file format
     * @throws UnsupportedAudioFileException if the stream does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     * @see InputStream#markSupported
     * @see InputStream#mark
     */
    public abstract AudioFileFormat getAudioFileFormat(InputStream stream) throws UnsupportedAudioFileException, IOException;

    /**
     * Obtains the audio file format of the URL provided.  The URL must
     * point to valid audio file data.
     * @param url the URL from which file format information should be
     * extracted
     * @return an <code>AudioFileFormat</code> object describing the audio file format
     * @throws UnsupportedAudioFileException if the URL does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    public abstract AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException;

    /**
     * Obtains the audio file format of the <code>File</code> provided.  The <code>File</code> must
     * point to valid audio file data.
     * @param file the <code>File</code> from which file format information should be
     * extracted
     * @return an <code>AudioFileFormat</code> object describing the audio file format
     * @throws UnsupportedAudioFileException if the <code>File</code> does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    public abstract AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException;

    /**
     * Obtains an audio input stream from the input stream provided.  The stream must
     * point to valid audio file data.  In general, audio file readers may
     * need to read some data from the stream before determining whether they
     * support it.  These parsers must
     * be able to mark the stream, read enough data to determine whether they
     * support the stream, and, if not, reset the stream's read pointer to its original
     * position.  If the input stream does not support this, this method may fail
     * with an <code>IOException</code>.
     * @param stream the input stream from which the <code>AudioInputStream</code> should be
     * constructed
     * @return an <code>AudioInputStream</code> object based on the audio file data contained
     * in the input stream.
     * @throws UnsupportedAudioFileException if the stream does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     * @see InputStream#markSupported
     * @see InputStream#mark
     */
    public abstract AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException;

    /**
     * Obtains an audio input stream from the URL provided.  The URL must
     * point to valid audio file data.
     * @param url the URL for which the <code>AudioInputStream</code> should be
     * constructed
     * @return an <code>AudioInputStream</code> object based on the audio file data pointed
     * to by the URL
     * @throws UnsupportedAudioFileException if the URL does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    public abstract AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException;

    /**
     * Obtains an audio input stream from the <code>File</code> provided.  The <code>File</code> must
     * point to valid audio file data.
     * @param file the <code>File</code> for which the <code>AudioInputStream</code> should be
     * constructed
     * @return an <code>AudioInputStream</code> object based on the audio file data pointed
     * to by the File
     * @throws UnsupportedAudioFileException if the <code>File</code> does not point to valid audio
     * file data recognized by the system
     * @throws IOException if an I/O exception occurs
     */
    public abstract AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException;
}
