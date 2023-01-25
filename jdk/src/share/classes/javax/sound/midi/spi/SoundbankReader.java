/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.midi.spi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Soundbank;

/**
 * A {@code SoundbankReader} supplies soundbank file-reading services. Concrete
 * subclasses of {@code SoundbankReader} parse a given soundbank file, producing
 * a {@link javax.sound.midi.Soundbank} object that can be loaded into a
 * {@link javax.sound.midi.Synthesizer}.
 *
 * @since 1.3
 * @author Kara Kytle
 */
public abstract class SoundbankReader {

    /**
     * Obtains a soundbank object from the URL provided.
     *
     * @param  url URL representing the soundbank.
     * @return soundbank object
     * @throws InvalidMidiDataException if the URL does not point to valid MIDI
     *         soundbank data recognized by this soundbank reader
     * @throws IOException if an I/O error occurs
     */
    public abstract Soundbank getSoundbank(URL url)
            throws InvalidMidiDataException, IOException;

    /**
     * Obtains a soundbank object from the {@code InputStream} provided.
     *
     * @param  stream {@code InputStream} representing the soundbank
     * @return soundbank object
     * @throws InvalidMidiDataException if the stream does not point to valid
     *         MIDI soundbank data recognized by this soundbank reader
     * @throws IOException if an I/O error occurs
     */
    public abstract Soundbank getSoundbank(InputStream stream)
            throws InvalidMidiDataException, IOException;

    /**
     * Obtains a soundbank object from the {@code File} provided.
     *
     * @param  file the {@code File} representing the soundbank
     * @return soundbank object
     * @throws InvalidMidiDataException if the file does not point to valid MIDI
     *         soundbank data recognized by this soundbank reader
     * @throws IOException if an I/O error occurs
     */
    public abstract Soundbank getSoundbank(File file)
            throws InvalidMidiDataException, IOException;
}
