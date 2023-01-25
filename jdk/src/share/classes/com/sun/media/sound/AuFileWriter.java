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

package com.sun.media.sound;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;


/**
 * AU file writer.
 *
 * @author Jan Borgersen
 */
public final class AuFileWriter extends SunFileWriter {

    //$$fb value for length field if length is not known
    public final static int UNKNOWN_SIZE=-1;

    /**
     * Constructs a new AuFileWriter object.
     */
    public AuFileWriter() {
        super(new AudioFileFormat.Type[]{AudioFileFormat.Type.AU});
    }

    public AudioFileFormat.Type[] getAudioFileTypes(AudioInputStream stream) {

        AudioFileFormat.Type[] filetypes = new AudioFileFormat.Type[types.length];
        System.arraycopy(types, 0, filetypes, 0, types.length);

        // make sure we can write this stream
        AudioFormat format = stream.getFormat();
        AudioFormat.Encoding encoding = format.getEncoding();

        if( (AudioFormat.Encoding.ALAW.equals(encoding)) ||
            (AudioFormat.Encoding.ULAW.equals(encoding)) ||
            (AudioFormat.Encoding.PCM_SIGNED.equals(encoding)) ||
            (AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding)) ) {

            return filetypes;
        }

        return new AudioFileFormat.Type[0];
    }


    public int write(AudioInputStream stream, AudioFileFormat.Type fileType, OutputStream out) throws IOException {

        // we must know the total data length to calculate the file length
        //$$fb 2001-07-13: fix for bug 4351296: do not throw an exception
        //if( stream.getFrameLength() == AudioSystem.NOT_SPECIFIED ) {
        //      throw new IOException("stream length not specified");
        //}

        // throws IllegalArgumentException if not supported
        AuFileFormat auFileFormat = (AuFileFormat)getAudioFileFormat(fileType, stream);

        int bytesWritten = writeAuFile(stream, auFileFormat, out);
        return bytesWritten;
    }



    public int write(AudioInputStream stream, AudioFileFormat.Type fileType, File out) throws IOException {

        // throws IllegalArgumentException if not supported
        AuFileFormat auFileFormat = (AuFileFormat)getAudioFileFormat(fileType, stream);

        // first write the file without worrying about length fields
        FileOutputStream fos = new FileOutputStream( out );     // throws IOException
        BufferedOutputStream bos = new BufferedOutputStream( fos, bisBufferSize );
        int bytesWritten = writeAuFile(stream, auFileFormat, bos );
        bos.close();

        // now, if length fields were not specified, calculate them,
        // open as a random access file, write the appropriate fields,
        // close again....
        if( auFileFormat.getByteLength()== AudioSystem.NOT_SPECIFIED ) {

            // $$kk: 10.22.99: jan: please either implement this or throw an exception!
            // $$fb: 2001-07-13: done. Fixes Bug 4479981
            RandomAccessFile raf=new RandomAccessFile(out, "rw");
            if (raf.length()<=0x7FFFFFFFl) {
                // skip AU magic and data offset field
                raf.skipBytes(8);
                raf.writeInt(bytesWritten-AuFileFormat.AU_HEADERSIZE);
                // that's all
            }
            raf.close();
        }

        return bytesWritten;
    }


    // -------------------------------------------------------------

    /**
     * Returns the AudioFileFormat describing the file that will be written from this AudioInputStream.
     * Throws IllegalArgumentException if not supported.
     */
    private AudioFileFormat getAudioFileFormat(AudioFileFormat.Type type, AudioInputStream stream) {

        AudioFormat format = null;
        AuFileFormat fileFormat = null;
        AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;

        AudioFormat streamFormat = stream.getFormat();
        AudioFormat.Encoding streamEncoding = streamFormat.getEncoding();


        float sampleRate;
        int sampleSizeInBits;
        int channels;
        int frameSize;
        float frameRate;
        int fileSize;

        if( !types[0].equals(type) ) {
            throw new IllegalArgumentException("File type " + type + " not supported.");
        }

        if( (AudioFormat.Encoding.ALAW.equals(streamEncoding)) ||
            (AudioFormat.Encoding.ULAW.equals(streamEncoding)) ) {

            encoding = streamEncoding;
            sampleSizeInBits = streamFormat.getSampleSizeInBits();

        } else if ( streamFormat.getSampleSizeInBits()==8 ) {

            encoding = AudioFormat.Encoding.PCM_SIGNED;
            sampleSizeInBits=8;

        } else {

            encoding = AudioFormat.Encoding.PCM_SIGNED;
            sampleSizeInBits=streamFormat.getSampleSizeInBits();
        }


        format = new AudioFormat( encoding,
                                  streamFormat.getSampleRate(),
                                  sampleSizeInBits,
                                  streamFormat.getChannels(),
                                  streamFormat.getFrameSize(),
                                  streamFormat.getFrameRate(),
                                  true);        // AU is always big endian


        if( stream.getFrameLength()!=AudioSystem.NOT_SPECIFIED ) {
            fileSize = (int)stream.getFrameLength()*streamFormat.getFrameSize() + AuFileFormat.AU_HEADERSIZE;
        } else {
            fileSize = AudioSystem.NOT_SPECIFIED;
        }

        fileFormat = new AuFileFormat( AudioFileFormat.Type.AU,
                                       fileSize,
                                       format,
                                       (int)stream.getFrameLength() );

        return fileFormat;
    }


    private InputStream getFileStream(AuFileFormat auFileFormat, InputStream audioStream) throws IOException {

        // private method ... assumes auFileFormat is a supported file type

        AudioFormat format            = auFileFormat.getFormat();

        int magic          = AuFileFormat.AU_SUN_MAGIC;
        int headerSize     = AuFileFormat.AU_HEADERSIZE;
        long dataSize       = auFileFormat.getFrameLength();
        //$$fb fix for Bug 4351296
        //int dataSizeInBytes = dataSize * format.getFrameSize();
        long dataSizeInBytes = (dataSize==AudioSystem.NOT_SPECIFIED)?UNKNOWN_SIZE:dataSize * format.getFrameSize();
        if (dataSizeInBytes>0x7FFFFFFFl) {
            dataSizeInBytes=UNKNOWN_SIZE;
        }
        int encoding_local = auFileFormat.getAuType();
        int sampleRate     = (int)format.getSampleRate();
        int channels       = format.getChannels();
        //$$fb below is the fix for 4297100.
        //boolean bigendian      = format.isBigEndian();
        boolean bigendian      = true;                  // force bigendian

        byte header[] = null;
        ByteArrayInputStream headerStream = null;
        ByteArrayOutputStream baos = null;
        DataOutputStream dos = null;
        SequenceInputStream auStream = null;

        AudioFormat audioStreamFormat = null;
        AudioFormat.Encoding encoding = null;
        InputStream codedAudioStream = audioStream;

        // if we need to do any format conversion, do it here.

        codedAudioStream = audioStream;

        if( audioStream instanceof AudioInputStream ) {


            audioStreamFormat = ((AudioInputStream)audioStream).getFormat();
            encoding = audioStreamFormat.getEncoding();

            //$$ fb 2001-07-13: Bug 4391108
            if( (AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding)) ||
                (AudioFormat.Encoding.PCM_SIGNED.equals(encoding)
                 && bigendian != audioStreamFormat.isBigEndian()) ) {

                                // plug in the transcoder to convert to PCM_SIGNED, bigendian
                                // NOTE: little endian AU is not common, so we're always converting
                                //       to big endian unless the passed in audioFileFormat is little.
                                // $$fb this NOTE is superseded. We always write big endian au files, this is by far the standard.
                codedAudioStream = AudioSystem.getAudioInputStream( new AudioFormat (
                                                                                     AudioFormat.Encoding.PCM_SIGNED,
                                                                                     audioStreamFormat.getSampleRate(),
                                                                                     audioStreamFormat.getSampleSizeInBits(),
                                                                                     audioStreamFormat.getChannels(),
                                                                                     audioStreamFormat.getFrameSize(),
                                                                                     audioStreamFormat.getFrameRate(),
                                                                                     bigendian),
                                                                    (AudioInputStream)audioStream );


            }
        }

        baos = new ByteArrayOutputStream();
        dos = new DataOutputStream(baos);


        if (bigendian) {
            dos.writeInt(AuFileFormat.AU_SUN_MAGIC);
            dos.writeInt(headerSize);
            dos.writeInt((int)dataSizeInBytes);
            dos.writeInt(encoding_local);
            dos.writeInt(sampleRate);
            dos.writeInt(channels);
        } else {
            dos.writeInt(AuFileFormat.AU_SUN_INV_MAGIC);
            dos.writeInt(big2little(headerSize));
            dos.writeInt(big2little((int)dataSizeInBytes));
            dos.writeInt(big2little(encoding_local));
            dos.writeInt(big2little(sampleRate));
            dos.writeInt(big2little(channels));
        }

        // Now create a new InputStream from headerStream and the InputStream
        // in audioStream

        dos.close();
        header = baos.toByteArray();
        headerStream = new ByteArrayInputStream( header );
        auStream = new SequenceInputStream(headerStream,
                        new NoCloseInputStream(codedAudioStream));

        return auStream;
    }

    private int writeAuFile(InputStream in, AuFileFormat auFileFormat, OutputStream out) throws IOException {

        int bytesRead = 0;
        int bytesWritten = 0;
        InputStream fileStream = getFileStream(auFileFormat, in);
        byte buffer[] = new byte[bisBufferSize];
        int maxLength = auFileFormat.getByteLength();

        while( (bytesRead = fileStream.read( buffer )) >= 0 ) {
            if (maxLength>0) {
                if( bytesRead < maxLength ) {
                    out.write( buffer, 0, (int)bytesRead );
                    bytesWritten += bytesRead;
                    maxLength -= bytesRead;
                } else {
                    out.write( buffer, 0, (int)maxLength );
                    bytesWritten += maxLength;
                    maxLength = 0;
                    break;
                }
            } else {
                out.write( buffer, 0, (int)bytesRead );
                bytesWritten += bytesRead;
            }
        }

        return bytesWritten;
    }


}
