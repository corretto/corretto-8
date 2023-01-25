/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.sound.midi.*;


/**
 * A Real Time Sequencer
 *
 * @author Florian Bomers
 */

/* TODO:
 * - rename PlayThread to PlayEngine (because isn't a thread)
 */
final class RealTimeSequencer extends AbstractMidiDevice
        implements Sequencer, AutoConnectSequencer {

    // STATIC VARIABLES

    /** debugging flags */
    private final static boolean DEBUG_PUMP = false;
    private final static boolean DEBUG_PUMP_ALL = false;

    /**
     * Event Dispatcher thread. Should be using a shared event
     * dispatcher instance with a factory in EventDispatcher
     */
    private static final Map<ThreadGroup, EventDispatcher> dispatchers =
            new WeakHashMap<>();

    /**
     * All RealTimeSequencers share this info object.
     */
    static final RealTimeSequencerInfo info = new RealTimeSequencerInfo();


    private static final Sequencer.SyncMode[] masterSyncModes = { Sequencer.SyncMode.INTERNAL_CLOCK };
    private static final Sequencer.SyncMode[] slaveSyncModes  = { Sequencer.SyncMode.NO_SYNC };

    private static final Sequencer.SyncMode masterSyncMode    = Sequencer.SyncMode.INTERNAL_CLOCK;
    private static final Sequencer.SyncMode slaveSyncMode     = Sequencer.SyncMode.NO_SYNC;


    /**
     * Sequence on which this sequencer is operating.
     */
    private Sequence sequence = null;

    // caches

    /**
     * Same for setTempoInMPQ...
     * -1 means not set.
     */
    private double cacheTempoMPQ = -1;


    /**
     * cache value for tempo factor until sequence is set
     * -1 means not set.
     */
    private float cacheTempoFactor = -1;


    /** if a particular track is muted */
    private boolean[] trackMuted = null;
    /** if a particular track is solo */
    private boolean[] trackSolo = null;

    /** tempo cache for getMicrosecondPosition */
    private final MidiUtils.TempoCache tempoCache = new MidiUtils.TempoCache();

    /**
     * True if the sequence is running.
     */
    private volatile boolean running;


    /** the thread for pushing out the MIDI messages */
    private PlayThread playThread;


    /**
     * True if we are recording
     */
    private volatile boolean recording;


    /**
     * List of tracks to which we're recording
     */
    private final List recordingTracks = new ArrayList();


    private long loopStart = 0;
    private long loopEnd = -1;
    private int loopCount = 0;


    /**
     * Meta event listeners
     */
    private final ArrayList metaEventListeners = new ArrayList();


    /**
     * Control change listeners
     */
    private final ArrayList controllerEventListeners = new ArrayList();


    /** automatic connection support */
    private boolean autoConnect = false;

    /** if we need to autoconnect at next open */
    private boolean doAutoConnectAtNextOpen = false;

    /** the receiver that this device is auto-connected to */
    Receiver autoConnectedReceiver = null;


    /* ****************************** CONSTRUCTOR ****************************** */

    RealTimeSequencer() throws MidiUnavailableException {
        super(info);

        if (Printer.trace) Printer.trace(">> RealTimeSequencer CONSTRUCTOR");
        if (Printer.trace) Printer.trace("<< RealTimeSequencer CONSTRUCTOR completed");
    }


    /* ****************************** SEQUENCER METHODS ******************** */

    public synchronized void setSequence(Sequence sequence)
        throws InvalidMidiDataException {

        if (Printer.trace) Printer.trace(">> RealTimeSequencer: setSequence(" + sequence +")");

        if (sequence != this.sequence) {
            if (this.sequence != null && sequence == null) {
                setCaches();
                stop();
                // initialize some non-cached values
                trackMuted = null;
                trackSolo = null;
                loopStart = 0;
                loopEnd = -1;
                loopCount = 0;
                if (getDataPump() != null) {
                    getDataPump().setTickPos(0);
                    getDataPump().resetLoopCount();
                }
            }

            if (playThread != null) {
                playThread.setSequence(sequence);
            }

            // store this sequence (do not copy - we want to give the possibility
            // of modifying the sequence at runtime)
            this.sequence = sequence;

            if (sequence != null) {
                tempoCache.refresh(sequence);
                // rewind to the beginning
                setTickPosition(0);
                // propagate caches
                propagateCaches();
            }
        }
        else if (sequence != null) {
            tempoCache.refresh(sequence);
            if (playThread != null) {
                playThread.setSequence(sequence);
            }
        }

        if (Printer.trace) Printer.trace("<< RealTimeSequencer: setSequence(" + sequence +") completed");
    }


    public synchronized void setSequence(InputStream stream) throws IOException, InvalidMidiDataException {

        if (Printer.trace) Printer.trace(">> RealTimeSequencer: setSequence(" + stream +")");

        if (stream == null) {
            setSequence((Sequence) null);
            return;
        }

        Sequence seq = MidiSystem.getSequence(stream); // can throw IOException, InvalidMidiDataException

        setSequence(seq);

        if (Printer.trace) Printer.trace("<< RealTimeSequencer: setSequence(" + stream +") completed");

    }


    public Sequence getSequence() {
        return sequence;
    }


    public synchronized void start() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: start()");

        // sequencer not open: throw an exception
        if (!isOpen()) {
            throw new IllegalStateException("sequencer not open");
        }

        // sequence not available: throw an exception
        if (sequence == null) {
            throw new IllegalStateException("sequence not set");
        }

        // already running: return quietly
        if (running == true) {
            return;
        }

        // start playback
        implStart();

        if (Printer.trace) Printer.trace("<< RealTimeSequencer: start() completed");
    }


    public synchronized void stop() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: stop()");

        if (!isOpen()) {
            throw new IllegalStateException("sequencer not open");
        }
        stopRecording();

        // not running; just return
        if (running == false) {
            if (Printer.trace) Printer.trace("<< RealTimeSequencer: stop() not running!");
            return;
        }

        // stop playback
        implStop();

        if (Printer.trace) Printer.trace("<< RealTimeSequencer: stop() completed");
    }


    public boolean isRunning() {
        return running;
    }


    public void startRecording() {
        if (!isOpen()) {
            throw new IllegalStateException("Sequencer not open");
        }

        start();
        recording = true;
    }


    public void stopRecording() {
        if (!isOpen()) {
            throw new IllegalStateException("Sequencer not open");
        }
        recording = false;
    }


    public boolean isRecording() {
        return recording;
    }


    public void recordEnable(Track track, int channel) {
        if (!findTrack(track)) {
            throw new IllegalArgumentException("Track does not exist in the current sequence");
        }

        synchronized(recordingTracks) {
            RecordingTrack rc = RecordingTrack.get(recordingTracks, track);
            if (rc != null) {
                rc.channel = channel;
            } else {
                recordingTracks.add(new RecordingTrack(track, channel));
            }
        }

    }


    public void recordDisable(Track track) {
        synchronized(recordingTracks) {
            RecordingTrack rc = RecordingTrack.get(recordingTracks, track);
            if (rc != null) {
                recordingTracks.remove(rc);
            }
        }

    }


    private boolean findTrack(Track track) {
        boolean found = false;
        if (sequence != null) {
            Track[] tracks = sequence.getTracks();
            for (int i = 0; i < tracks.length; i++) {
                if (track == tracks[i]) {
                    found = true;
                    break;
                }
            }
        }
        return found;
    }


    public float getTempoInBPM() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: getTempoInBPM() ");

        return (float) MidiUtils.convertTempo(getTempoInMPQ());
    }


    public void setTempoInBPM(float bpm) {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: setTempoInBPM() ");
        if (bpm <= 0) {
            // should throw IllegalArgumentException
            bpm = 1.0f;
        }

        setTempoInMPQ((float) MidiUtils.convertTempo((double) bpm));
    }


    public float getTempoInMPQ() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: getTempoInMPQ() ");

        if (needCaching()) {
            // if the sequencer is closed, return cached value
            if (cacheTempoMPQ != -1) {
                return (float) cacheTempoMPQ;
            }
            // if sequence is set, return current tempo
            if (sequence != null) {
                return tempoCache.getTempoMPQAt(getTickPosition());
            }

            // last resort: return a standard tempo: 120bpm
            return (float) MidiUtils.DEFAULT_TEMPO_MPQ;
        }
        return (float)getDataPump().getTempoMPQ();
    }


    public void setTempoInMPQ(float mpq) {
        if (mpq <= 0) {
            // should throw IllegalArgumentException
            mpq = 1.0f;
        }

        if (Printer.trace) Printer.trace(">> RealTimeSequencer: setTempoInMPQ() ");

        if (needCaching()) {
            // cache the value
            cacheTempoMPQ = mpq;
        } else {
            // set the native tempo in MPQ
            getDataPump().setTempoMPQ(mpq);

            // reset the tempoInBPM and tempoInMPQ values so we won't use them again
            cacheTempoMPQ = -1;
        }
    }


    public void setTempoFactor(float factor) {
        if (factor <= 0) {
            // should throw IllegalArgumentException
            return;
        }

        if (Printer.trace) Printer.trace(">> RealTimeSequencer: setTempoFactor() ");

        if (needCaching()) {
            cacheTempoFactor = factor;
        } else {
            getDataPump().setTempoFactor(factor);
            // don't need cache anymore
            cacheTempoFactor = -1;
        }
    }


    public float getTempoFactor() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: getTempoFactor() ");

        if (needCaching()) {
            if (cacheTempoFactor != -1) {
                return cacheTempoFactor;
            }
            return 1.0f;
        }
        return getDataPump().getTempoFactor();
    }


    public long getTickLength() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: getTickLength() ");

        if (sequence == null) {
            return 0;
        }

        return sequence.getTickLength();
    }


    public synchronized long getTickPosition() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: getTickPosition() ");

        if (getDataPump() == null || sequence == null) {
            return 0;
        }

        return getDataPump().getTickPos();
    }


    public synchronized void setTickPosition(long tick) {
        if (tick < 0) {
            // should throw IllegalArgumentException
            return;
        }

        if (Printer.trace) Printer.trace(">> RealTimeSequencer: setTickPosition("+tick+") ");

        if (getDataPump() == null) {
            if (tick != 0) {
                // throw new InvalidStateException("cannot set position in closed state");
            }
        }
        else if (sequence == null) {
            if (tick != 0) {
                // throw new InvalidStateException("cannot set position if sequence is not set");
            }
        } else {
            getDataPump().setTickPos(tick);
        }
    }


    public long getMicrosecondLength() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: getMicrosecondLength() ");

        if (sequence == null) {
            return 0;
        }

        return sequence.getMicrosecondLength();
    }


    public long getMicrosecondPosition() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: getMicrosecondPosition() ");

        if (getDataPump() == null || sequence == null) {
            return 0;
        }
        synchronized (tempoCache) {
            return MidiUtils.tick2microsecond(sequence, getDataPump().getTickPos(), tempoCache);
        }
    }


    public void setMicrosecondPosition(long microseconds) {
        if (microseconds < 0) {
            // should throw IllegalArgumentException
            return;
        }

        if (Printer.trace) Printer.trace(">> RealTimeSequencer: setMicrosecondPosition("+microseconds+") ");

        if (getDataPump() == null) {
            if (microseconds != 0) {
                // throw new InvalidStateException("cannot set position in closed state");
            }
        }
        else if (sequence == null) {
            if (microseconds != 0) {
                // throw new InvalidStateException("cannot set position if sequence is not set");
            }
        } else {
            synchronized(tempoCache) {
                setTickPosition(MidiUtils.microsecond2tick(sequence, microseconds, tempoCache));
            }
        }
    }


    public void setMasterSyncMode(Sequencer.SyncMode sync) {
        // not supported
    }


    public Sequencer.SyncMode getMasterSyncMode() {
        return masterSyncMode;
    }


    public Sequencer.SyncMode[] getMasterSyncModes() {
        Sequencer.SyncMode[] returnedModes = new Sequencer.SyncMode[masterSyncModes.length];
        System.arraycopy(masterSyncModes, 0, returnedModes, 0, masterSyncModes.length);
        return returnedModes;
    }


    public void setSlaveSyncMode(Sequencer.SyncMode sync) {
        // not supported
    }


    public Sequencer.SyncMode getSlaveSyncMode() {
        return slaveSyncMode;
    }


    public Sequencer.SyncMode[] getSlaveSyncModes() {
        Sequencer.SyncMode[] returnedModes = new Sequencer.SyncMode[slaveSyncModes.length];
        System.arraycopy(slaveSyncModes, 0, returnedModes, 0, slaveSyncModes.length);
        return returnedModes;
    }

    int getTrackCount() {
        Sequence seq = getSequence();
        if (seq != null) {
            // $$fb wish there was a nicer way to get the number of tracks...
            return sequence.getTracks().length;
        }
        return 0;
    }



    public synchronized void setTrackMute(int track, boolean mute) {
        int trackCount = getTrackCount();
        if (track < 0 || track >= getTrackCount()) return;
        trackMuted = ensureBoolArraySize(trackMuted, trackCount);
        trackMuted[track] = mute;
        if (getDataPump() != null) {
            getDataPump().muteSoloChanged();
        }
    }


    public synchronized boolean getTrackMute(int track) {
        if (track < 0 || track >= getTrackCount()) return false;
        if (trackMuted == null || trackMuted.length <= track) return false;
        return trackMuted[track];
    }


    public synchronized void setTrackSolo(int track, boolean solo) {
        int trackCount = getTrackCount();
        if (track < 0 || track >= getTrackCount()) return;
        trackSolo = ensureBoolArraySize(trackSolo, trackCount);
        trackSolo[track] = solo;
        if (getDataPump() != null) {
            getDataPump().muteSoloChanged();
        }
    }


    public synchronized boolean getTrackSolo(int track) {
        if (track < 0 || track >= getTrackCount()) return false;
        if (trackSolo == null || trackSolo.length <= track) return false;
        return trackSolo[track];
    }


    public boolean addMetaEventListener(MetaEventListener listener) {
        synchronized(metaEventListeners) {
            if (! metaEventListeners.contains(listener)) {

                metaEventListeners.add(listener);
            }
            return true;
        }
    }


    public void removeMetaEventListener(MetaEventListener listener) {
        synchronized(metaEventListeners) {
            int index = metaEventListeners.indexOf(listener);
            if (index >= 0) {
                metaEventListeners.remove(index);
            }
        }
    }


    public int[] addControllerEventListener(ControllerEventListener listener, int[] controllers) {
        synchronized(controllerEventListeners) {

            // first find the listener.  if we have one, add the controllers
            // if not, create a new element for it.
            ControllerListElement cve = null;
            boolean flag = false;
            for(int i=0; i < controllerEventListeners.size(); i++) {

                cve = (ControllerListElement) controllerEventListeners.get(i);

                if (cve.listener.equals(listener)) {
                    cve.addControllers(controllers);
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                cve = new ControllerListElement(listener, controllers);
                controllerEventListeners.add(cve);
            }

            // and return all the controllers this listener is interested in
            return cve.getControllers();
        }
    }


    public int[] removeControllerEventListener(ControllerEventListener listener, int[] controllers) {
        synchronized(controllerEventListeners) {
            ControllerListElement cve = null;
            boolean flag = false;
            for (int i=0; i < controllerEventListeners.size(); i++) {
                cve = (ControllerListElement) controllerEventListeners.get(i);
                if (cve.listener.equals(listener)) {
                    cve.removeControllers(controllers);
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                return new int[0];
            }
            if (controllers == null) {
                int index = controllerEventListeners.indexOf(cve);
                if (index >= 0) {
                    controllerEventListeners.remove(index);
                }
                return new int[0];
            }
            return cve.getControllers();
        }
    }


    ////////////////// LOOPING (added in 1.5) ///////////////////////

    public void setLoopStartPoint(long tick) {
        if ((tick > getTickLength())
            || ((loopEnd != -1) && (tick > loopEnd))
            || (tick < 0)) {
            throw new IllegalArgumentException("invalid loop start point: "+tick);
        }
        loopStart = tick;
    }

    public long getLoopStartPoint() {
        return loopStart;
    }

    public void setLoopEndPoint(long tick) {
        if ((tick > getTickLength())
            || ((loopStart > tick) && (tick != -1))
            || (tick < -1)) {
            throw new IllegalArgumentException("invalid loop end point: "+tick);
        }
        loopEnd = tick;
    }

    public long getLoopEndPoint() {
        return loopEnd;
    }

    public void setLoopCount(int count) {
        if (count != LOOP_CONTINUOUSLY
            && count < 0) {
            throw new IllegalArgumentException("illegal value for loop count: "+count);
        }
        loopCount = count;
        if (getDataPump() != null) {
            getDataPump().resetLoopCount();
        }
    }

    public int getLoopCount() {
        return loopCount;
    }


    /* *********************************** play control ************************* */

    /*
     */
    protected void implOpen() throws MidiUnavailableException {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: implOpen()");

        //openInternalSynth();

        // create PlayThread
        playThread = new PlayThread();

        //id = nOpen();
        //if (id == 0) {
        //    throw new MidiUnavailableException("unable to open sequencer");
        //}
        if (sequence != null) {
            playThread.setSequence(sequence);
        }

        // propagate caches
        propagateCaches();

        if (doAutoConnectAtNextOpen) {
            doAutoConnect();
        }
        if (Printer.trace) Printer.trace("<< RealTimeSequencer: implOpen() succeeded");
    }

    private void doAutoConnect() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: doAutoConnect()");
        Receiver rec = null;
        // first try to connect to the default synthesizer
        // IMPORTANT: this code needs to be synch'ed with
        //            MidiSystem.getSequencer(boolean), because the same
        //            algorithm needs to be used!
        try {
            Synthesizer synth = MidiSystem.getSynthesizer();
            if (synth instanceof ReferenceCountingDevice) {
                rec = ((ReferenceCountingDevice) synth).getReceiverReferenceCounting();
            } else {
                synth.open();
                try {
                    rec = synth.getReceiver();
                } finally {
                    // make sure that the synth is properly closed
                    if (rec == null) {
                        synth.close();
                    }
                }
            }
        } catch (Exception e) {
            // something went wrong with synth
        }
        if (rec == null) {
            // then try to connect to the default Receiver
            try {
                rec = MidiSystem.getReceiver();
            } catch (Exception e) {
                // something went wrong. Nothing to do then!
            }
        }
        if (rec != null) {
            autoConnectedReceiver = rec;
            try {
                getTransmitter().setReceiver(rec);
            } catch (Exception e) {}
        }
        if (Printer.trace) Printer.trace("<< RealTimeSequencer: doAutoConnect() succeeded");
    }

    private synchronized void propagateCaches() {
        // only set caches if open and sequence is set
        if (sequence != null && isOpen()) {
            if (cacheTempoFactor != -1) {
                setTempoFactor(cacheTempoFactor);
            }
            if (cacheTempoMPQ == -1) {
                setTempoInMPQ((new MidiUtils.TempoCache(sequence)).getTempoMPQAt(getTickPosition()));
            } else {
                setTempoInMPQ((float) cacheTempoMPQ);
            }
        }
    }

    /** populate the caches with the current values */
    private synchronized void setCaches() {
        cacheTempoFactor = getTempoFactor();
        cacheTempoMPQ = getTempoInMPQ();
    }



    protected synchronized void implClose() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: implClose() ");

        if (playThread == null) {
            if (Printer.err) Printer.err("RealTimeSequencer.implClose() called, but playThread not instanciated!");
        } else {
            // Interrupt playback loop.
            playThread.close();
            playThread = null;
        }

        super.implClose();

        sequence = null;
        running = false;
        cacheTempoMPQ = -1;
        cacheTempoFactor = -1;
        trackMuted = null;
        trackSolo = null;
        loopStart = 0;
        loopEnd = -1;
        loopCount = 0;

        /** if this sequencer is set to autoconnect, need to
         * re-establish the connection at next open!
         */
        doAutoConnectAtNextOpen = autoConnect;

        if (autoConnectedReceiver != null) {
            try {
                autoConnectedReceiver.close();
            } catch (Exception e) {}
            autoConnectedReceiver = null;
        }

        if (Printer.trace) Printer.trace("<< RealTimeSequencer: implClose() completed");
    }

    void implStart() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: implStart()");

        if (playThread == null) {
            if (Printer.err) Printer.err("RealTimeSequencer.implStart() called, but playThread not instanciated!");
            return;
        }

        tempoCache.refresh(sequence);
        if (!running) {
            running  = true;
            playThread.start();
        }
        if (Printer.trace) Printer.trace("<< RealTimeSequencer: implStart() completed");
    }


    void implStop() {
        if (Printer.trace) Printer.trace(">> RealTimeSequencer: implStop()");

        if (playThread == null) {
            if (Printer.err) Printer.err("RealTimeSequencer.implStop() called, but playThread not instanciated!");
            return;
        }

        recording = false;
        if (running) {
            running = false;
            playThread.stop();
        }
        if (Printer.trace) Printer.trace("<< RealTimeSequencer: implStop() completed");
    }

    private static EventDispatcher getEventDispatcher() {
        // create and start the global event thread
        //TODO  need a way to stop this thread when the engine is done
        final ThreadGroup tg = Thread.currentThread().getThreadGroup();
        synchronized (dispatchers) {
            EventDispatcher eventDispatcher = dispatchers.get(tg);
            if (eventDispatcher == null) {
                eventDispatcher = new EventDispatcher();
                dispatchers.put(tg, eventDispatcher);
                eventDispatcher.start();
            }
            return eventDispatcher;
        }
    }

    /**
     * Send midi player events.
     * must not be synchronized on "this"
     */
    void sendMetaEvents(MidiMessage message) {
        if (metaEventListeners.size() == 0) return;

        //if (Printer.debug) Printer.debug("sending a meta event");
        getEventDispatcher().sendAudioEvents(message, metaEventListeners);
    }

    /**
     * Send midi player events.
     */
    void sendControllerEvents(MidiMessage message) {
        int size = controllerEventListeners.size();
        if (size == 0) return;

        //if (Printer.debug) Printer.debug("sending a controller event");

        if (! (message instanceof ShortMessage)) {
            if (Printer.debug) Printer.debug("sendControllerEvents: message is NOT instanceof ShortMessage!");
            return;
        }
        ShortMessage msg = (ShortMessage) message;
        int controller = msg.getData1();
        List sendToListeners = new ArrayList();
        for (int i = 0; i < size; i++) {
            ControllerListElement cve = (ControllerListElement) controllerEventListeners.get(i);
            for(int j = 0; j < cve.controllers.length; j++) {
                if (cve.controllers[j] == controller) {
                    sendToListeners.add(cve.listener);
                    break;
                }
            }
        }
        getEventDispatcher().sendAudioEvents(message, sendToListeners);
    }



    private boolean needCaching() {
        return !isOpen() || (sequence == null) || (playThread == null);
    }

    /**
     * return the data pump instance, owned by play thread
     * if playthread is null, return null.
     * This method is guaranteed to return non-null if
     * needCaching returns false
     */
    private DataPump getDataPump() {
        if (playThread != null) {
            return playThread.getDataPump();
        }
        return null;
    }

    private MidiUtils.TempoCache getTempoCache() {
        return tempoCache;
    }

    private static boolean[] ensureBoolArraySize(boolean[] array, int desiredSize) {
        if (array == null) {
            return new boolean[desiredSize];
        }
        if (array.length < desiredSize) {
            boolean[] newArray = new boolean[desiredSize];
            System.arraycopy(array, 0, newArray, 0, array.length);
            return newArray;
        }
        return array;
    }


    // OVERRIDES OF ABSTRACT MIDI DEVICE METHODS

    protected boolean hasReceivers() {
        return true;
    }

    // for recording
    protected Receiver createReceiver() throws MidiUnavailableException {
        return new SequencerReceiver();
    }


    protected boolean hasTransmitters() {
        return true;
    }


    protected Transmitter createTransmitter() throws MidiUnavailableException {
        return new SequencerTransmitter();
    }


    // interface AutoConnectSequencer
    public void setAutoConnect(Receiver autoConnectedReceiver) {
        this.autoConnect = (autoConnectedReceiver != null);
        this.autoConnectedReceiver = autoConnectedReceiver;
    }



    // INNER CLASSES

    /**
     * An own class to distinguish the class name from
     * the transmitter of other devices
     */
    private class SequencerTransmitter extends BasicTransmitter {
        private SequencerTransmitter() {
            super();
        }
    }


    final class SequencerReceiver extends AbstractReceiver {

        void implSend(MidiMessage message, long timeStamp) {
            if (recording) {
                long tickPos = 0;

                // convert timeStamp to ticks
                if (timeStamp < 0) {
                    tickPos = getTickPosition();
                } else {
                    synchronized(tempoCache) {
                        tickPos = MidiUtils.microsecond2tick(sequence, timeStamp, tempoCache);
                    }
                }

                // and record to the first matching Track
                Track track = null;
                // do not record real-time events
                // see 5048381: NullPointerException when saving a MIDI sequence
                if (message.getLength() > 1) {
                    if (message instanceof ShortMessage) {
                        ShortMessage sm = (ShortMessage) message;
                        // all real-time messages have 0xF in the high nibble of the status byte
                        if ((sm.getStatus() & 0xF0) != 0xF0) {
                            track = RecordingTrack.get(recordingTracks, sm.getChannel());
                        }
                    } else {
                        // $$jb: where to record meta, sysex events?
                        // $$fb: the first recording track
                        track = RecordingTrack.get(recordingTracks, -1);
                    }
                    if (track != null) {
                        // create a copy of this message
                        if (message instanceof ShortMessage) {
                            message = new FastShortMessage((ShortMessage) message);
                        } else {
                            message = (MidiMessage) message.clone();
                        }

                        // create new MidiEvent
                        MidiEvent me = new MidiEvent(message, tickPos);
                        track.add(me);
                    }
                }
            }
        }
    }


    private static class RealTimeSequencerInfo extends MidiDevice.Info {

        private static final String name = "Real Time Sequencer";
        private static final String vendor = "Oracle Corporation";
        private static final String description = "Software sequencer";
        private static final String version = "Version 1.0";

        private RealTimeSequencerInfo() {
            super(name, vendor, description, version);
        }
    } // class Info


    private class ControllerListElement {

        // $$jb: using an array for controllers b/c its
        //       easier to deal with than turning all the
        //       ints into objects to use a Vector
        int []  controllers;
        final ControllerEventListener listener;

        private ControllerListElement(ControllerEventListener listener, int[] controllers) {

            this.listener = listener;
            if (controllers == null) {
                controllers = new int[128];
                for (int i = 0; i < 128; i++) {
                    controllers[i] = i;
                }
            }
            this.controllers = controllers;
        }

        private void addControllers(int[] c) {

            if (c==null) {
                controllers = new int[128];
                for (int i = 0; i < 128; i++) {
                    controllers[i] = i;
                }
                return;
            }
            int temp[] = new int[ controllers.length + c.length ];
            int elements;

            // first add what we have
            for(int i=0; i<controllers.length; i++) {
                temp[i] = controllers[i];
            }
            elements = controllers.length;
            // now add the new controllers only if we don't already have them
            for(int i=0; i<c.length; i++) {
                boolean flag = false;

                for(int j=0; j<controllers.length; j++) {
                    if (c[i] == controllers[j]) {
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    temp[elements++] = c[i];
                }
            }
            // now keep only the elements we need
            int newc[] = new int[ elements ];
            for(int i=0; i<elements; i++){
                newc[i] = temp[i];
            }
            controllers = newc;
        }

        private void removeControllers(int[] c) {

            if (c==null) {
                controllers = new int[0];
            } else {
                int temp[] = new int[ controllers.length ];
                int elements = 0;


                for(int i=0; i<controllers.length; i++){
                    boolean flag = false;
                    for(int j=0; j<c.length; j++) {
                        if (controllers[i] == c[j]) {
                            flag = true;
                            break;
                        }
                    }
                    if (!flag){
                        temp[elements++] = controllers[i];
                    }
                }
                // now keep only the elements remaining
                int newc[] = new int[ elements ];
                for(int i=0; i<elements; i++) {
                    newc[i] = temp[i];
                }
                controllers = newc;

            }
        }

        private int[] getControllers() {

            // return a copy of our array of controllers,
            // so others can't mess with it
            if (controllers == null) {
                return null;
            }

            int c[] = new int[controllers.length];

            for(int i=0; i<controllers.length; i++){
                c[i] = controllers[i];
            }
            return c;
        }

    } // class ControllerListElement


    static class RecordingTrack {

        private final Track track;
        private int channel;

        RecordingTrack(Track track, int channel) {
            this.track = track;
            this.channel = channel;
        }

        static RecordingTrack get(List recordingTracks, Track track) {

            synchronized(recordingTracks) {
                int size = recordingTracks.size();

                for (int i = 0; i < size; i++) {
                    RecordingTrack current = (RecordingTrack)recordingTracks.get(i);
                    if (current.track == track) {
                        return current;
                    }
                }
            }
            return null;
        }

        static Track get(List recordingTracks, int channel) {

            synchronized(recordingTracks) {
                int size = recordingTracks.size();
                for (int i = 0; i < size; i++) {
                    RecordingTrack current = (RecordingTrack)recordingTracks.get(i);
                    if ((current.channel == channel) || (current.channel == -1)) {
                        return current.track;
                    }
                }
            }
            return null;

        }
    }


    final class PlayThread implements Runnable {
        private Thread thread;
        private final Object lock = new Object();

        /** true if playback is interrupted (in close) */
        boolean interrupted = false;
        boolean isPumping = false;

        private final DataPump dataPump = new DataPump();


        PlayThread() {
            // nearly MAX_PRIORITY
            int priority = Thread.NORM_PRIORITY
                + ((Thread.MAX_PRIORITY - Thread.NORM_PRIORITY) * 3) / 4;
            thread = JSSecurityManager.createThread(this,
                                                    "Java Sound Sequencer", // name
                                                    false,                  // daemon
                                                    priority,               // priority
                                                    true);                  // doStart
        }

        DataPump getDataPump() {
            return dataPump;
        }

        synchronized void setSequence(Sequence seq) {
            dataPump.setSequence(seq);
        }


        /** start thread and pump. Requires up-to-date tempoCache */
        synchronized void start() {
            // mark the sequencer running
            running = true;

            if (!dataPump.hasCachedTempo()) {
                long tickPos = getTickPosition();
                dataPump.setTempoMPQ(tempoCache.getTempoMPQAt(tickPos));
            }
            dataPump.checkPointMillis = 0; // means restarted
            dataPump.clearNoteOnCache();
            dataPump.needReindex = true;

            dataPump.resetLoopCount();

            // notify the thread
            synchronized(lock) {
                lock.notifyAll();
            }

            if (Printer.debug) Printer.debug(" ->Started MIDI play thread");

        }

        // waits until stopped
        synchronized void stop() {
            playThreadImplStop();
            long t = System.nanoTime() / 1000000l;
            while (isPumping) {
                synchronized(lock) {
                    try {
                        lock.wait(2000);
                    } catch (InterruptedException ie) {
                        // ignore
                    }
                }
                // don't wait for more than 2 seconds
                if ((System.nanoTime()/1000000l) - t > 1900) {
                    if (Printer.err) Printer.err("Waited more than 2 seconds in RealTimeSequencer.PlayThread.stop()!");
                    //break;
                }
            }
        }

        void playThreadImplStop() {
            // mark the sequencer running
            running = false;
            synchronized(lock) {
                lock.notifyAll();
            }
        }

        void close() {
            Thread oldThread = null;
            synchronized (this) {
                // dispose of thread
                interrupted = true;
                oldThread = thread;
                thread = null;
            }
            if (oldThread != null) {
                // wake up the thread if it's in wait()
                synchronized(lock) {
                    lock.notifyAll();
                }
            }
            // wait for the thread to terminate itself,
            // but max. 2 seconds. Must not be synchronized!
            if (oldThread != null) {
                try {
                    oldThread.join(2000);
                } catch (InterruptedException ie) {}
            }
        }


        /**
         * Main process loop driving the media flow.
         *
         * Make sure to NOT synchronize on RealTimeSequencer
         * anywhere here (even implicit). That is a sure deadlock!
         */
        public void run() {

            while (!interrupted) {
                boolean EOM = false;
                boolean wasRunning = running;
                isPumping = !interrupted && running;
                while (!EOM && !interrupted && running) {
                    EOM = dataPump.pump();

                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ie) {
                        // ignore
                    }
                }
                if (Printer.debug) {
                    Printer.debug("Exited main pump loop because: ");
                    if (EOM) Printer.debug(" -> EOM is reached");
                    if (!running) Printer.debug(" -> running was set to false");
                    if (interrupted) Printer.debug(" -> interrupted was set to true");
                }

                playThreadImplStop();
                if (wasRunning) {
                    dataPump.notesOff(true);
                }
                if (EOM) {
                    dataPump.setTickPos(sequence.getTickLength());

                    // send EOT event (mis-used for end of media)
                    MetaMessage message = new MetaMessage();
                    try{
                        message.setMessage(MidiUtils.META_END_OF_TRACK_TYPE, new byte[0], 0);
                    } catch(InvalidMidiDataException e1) {}
                    sendMetaEvents(message);
                }
                synchronized (lock) {
                    isPumping = false;
                    // wake up a waiting stop() method
                    lock.notifyAll();
                    while (!running && !interrupted) {
                        try {
                            lock.wait();
                        } catch (Exception ex) {}
                    }
                }
            } // end of while(!EOM && !interrupted && running)
            if (Printer.debug) Printer.debug("end of play thread");
        }
    }


    /**
     * class that does the actual dispatching of events,
     * used to be in native in MMAPI
     */
    private class DataPump {
        private float currTempo;         // MPQ tempo
        private float tempoFactor;       // 1.0 is default
        private float inverseTempoFactor;// = 1.0 / tempoFactor
        private long ignoreTempoEventAt; // ignore next META tempo during playback at this tick pos only
        private int resolution;
        private float divisionType;
        private long checkPointMillis;   // microseconds at checkoint
        private long checkPointTick;     // ticks at checkpoint
        private int[] noteOnCache;       // bit-mask of notes that are currently on
        private Track[] tracks;
        private boolean[] trackDisabled; // if true, do not play this track
        private int[] trackReadPos;      // read index per track
        private long lastTick;
        private boolean needReindex = false;
        private int currLoopCounter = 0;

        //private sun.misc.Perf perf = sun.misc.Perf.getPerf();
        //private long perfFreq = perf.highResFrequency();


        DataPump() {
            init();
        }

        synchronized void init() {
            ignoreTempoEventAt = -1;
            tempoFactor = 1.0f;
            inverseTempoFactor = 1.0f;
            noteOnCache = new int[128];
            tracks = null;
            trackDisabled = null;
        }

        synchronized void setTickPos(long tickPos) {
            long oldLastTick = tickPos;
            lastTick = tickPos;
            if (running) {
                notesOff(false);
            }
            if (running || tickPos > 0) {
                // will also reindex
                chaseEvents(oldLastTick, tickPos);
            } else {
                needReindex = true;
            }
            if (!hasCachedTempo()) {
                setTempoMPQ(getTempoCache().getTempoMPQAt(lastTick, currTempo));
                // treat this as if it is a real time tempo change
                ignoreTempoEventAt = -1;
            }
            // trigger re-configuration
            checkPointMillis = 0;
        }

        long getTickPos() {
            return lastTick;
        }

        // hasCachedTempo is only valid if it is the current position
        boolean hasCachedTempo() {
            if (ignoreTempoEventAt != lastTick) {
                ignoreTempoEventAt = -1;
            }
            return ignoreTempoEventAt >= 0;
        }

        // this method is also used internally in the pump!
        synchronized void setTempoMPQ(float tempoMPQ) {
            if (tempoMPQ > 0 && tempoMPQ != currTempo) {
                ignoreTempoEventAt = lastTick;
                this.currTempo = tempoMPQ;
                // re-calculate check point
                checkPointMillis = 0;
            }
        }

        float getTempoMPQ() {
            return currTempo;
        }

        synchronized void setTempoFactor(float factor) {
            if (factor > 0 && factor != this.tempoFactor) {
                tempoFactor = factor;
                inverseTempoFactor = 1.0f / factor;
                // re-calculate check point
                checkPointMillis = 0;
            }
        }

        float getTempoFactor() {
            return tempoFactor;
        }

        synchronized void muteSoloChanged() {
            boolean[] newDisabled = makeDisabledArray();
            if (running) {
                applyDisabledTracks(trackDisabled, newDisabled);
            }
            trackDisabled = newDisabled;
        }



        synchronized void setSequence(Sequence seq) {
            if (seq == null) {
                init();
                return;
            }
            tracks = seq.getTracks();
            muteSoloChanged();
            resolution = seq.getResolution();
            divisionType = seq.getDivisionType();
            trackReadPos = new int[tracks.length];
            // trigger re-initialization
            checkPointMillis = 0;
            needReindex = true;
        }

        synchronized void resetLoopCount() {
            currLoopCounter = loopCount;
        }

        void clearNoteOnCache() {
            for (int i = 0; i < 128; i++) {
                noteOnCache[i] = 0;
            }
        }

        void notesOff(boolean doControllers) {
            int done = 0;
            for (int ch=0; ch<16; ch++) {
                int channelMask = (1<<ch);
                for (int i=0; i<128; i++) {
                    if ((noteOnCache[i] & channelMask) != 0) {
                        noteOnCache[i] ^= channelMask;
                        // send note on with velocity 0
                        getTransmitterList().sendMessage((ShortMessage.NOTE_ON | ch) | (i<<8), -1);
                        done++;
                    }
                }
                /* all notes off */
                getTransmitterList().sendMessage((ShortMessage.CONTROL_CHANGE | ch) | (123<<8), -1);
                /* sustain off */
                getTransmitterList().sendMessage((ShortMessage.CONTROL_CHANGE | ch) | (64<<8), -1);
                if (doControllers) {
                    /* reset all controllers */
                    getTransmitterList().sendMessage((ShortMessage.CONTROL_CHANGE | ch) | (121<<8), -1);
                    done++;
                }
            }
            if (DEBUG_PUMP) Printer.println("  noteOff: sent "+done+" messages.");
        }


        private boolean[] makeDisabledArray() {
            if (tracks == null) {
                return null;
            }
            boolean[] newTrackDisabled = new boolean[tracks.length];
            boolean[] solo;
            boolean[] mute;
            synchronized(RealTimeSequencer.this) {
                mute = trackMuted;
                solo = trackSolo;
            }
            // if one track is solo, then only play solo
            boolean hasSolo = false;
            if (solo != null) {
                for (int i = 0; i < solo.length; i++) {
                    if (solo[i]) {
                        hasSolo = true;
                        break;
                    }
                }
            }
            if (hasSolo) {
                // only the channels with solo play, regardless of mute
                for (int i = 0; i < newTrackDisabled.length; i++) {
                    newTrackDisabled[i] = (i >= solo.length) || (!solo[i]);
                }
            } else {
                // mute the selected channels
                for (int i = 0; i < newTrackDisabled.length; i++) {
                    newTrackDisabled[i] = (mute != null) && (i < mute.length) && (mute[i]);
                }
            }
            return newTrackDisabled;
        }

        /**
         * chase all events from beginning of Track
         * and send note off for those events that are active
         * in noteOnCache array.
         * It is possible, of course, to catch notes from other tracks,
         * but better than more complicated logic to detect
         * which notes are really from this track
         */
        private void sendNoteOffIfOn(Track track, long endTick) {
            int size = track.size();
            int done = 0;
            try {
                for (int i = 0; i < size; i++) {
                    MidiEvent event = track.get(i);
                    if (event.getTick() > endTick) break;
                    MidiMessage msg = event.getMessage();
                    int status = msg.getStatus();
                    int len = msg.getLength();
                    if (len == 3 && ((status & 0xF0) == ShortMessage.NOTE_ON)) {
                        int note = -1;
                        if (msg instanceof ShortMessage) {
                            ShortMessage smsg = (ShortMessage) msg;
                            if (smsg.getData2() > 0) {
                                // only consider Note On with velocity > 0
                                note = smsg.getData1();
                            }
                        } else {
                            byte[] data = msg.getMessage();
                            if ((data[2] & 0x7F) > 0) {
                                // only consider Note On with velocity > 0
                                note = data[1] & 0x7F;
                            }
                        }
                        if (note >= 0) {
                            int bit = 1<<(status & 0x0F);
                            if ((noteOnCache[note] & bit) != 0) {
                                // the bit is set. Send Note Off
                                getTransmitterList().sendMessage(status | (note<<8), -1);
                                // clear the bit
                                noteOnCache[note] &= (0xFFFF ^ bit);
                                done++;
                            }
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                // this happens when messages are removed
                // from the track while this method executes
            }
            if (DEBUG_PUMP) Printer.println("  sendNoteOffIfOn: sent "+done+" messages.");
        }


        /**
         * Runtime application of mute/solo:
         * if a track is muted that was previously playing, send
         *    note off events for all currently playing notes
         */
        private void applyDisabledTracks(boolean[] oldDisabled, boolean[] newDisabled) {
            byte[][] tempArray = null;
            synchronized(RealTimeSequencer.this) {
                for (int i = 0; i < newDisabled.length; i++) {
                    if (((oldDisabled == null)
                         || (i >= oldDisabled.length)
                         || !oldDisabled[i])
                        && newDisabled[i]) {
                        // case that a track gets muted: need to
                        // send appropriate note off events to prevent
                        // hanging notes

                        if (tracks.length > i) {
                            sendNoteOffIfOn(tracks[i], lastTick);
                        }
                    }
                    else if ((oldDisabled != null)
                             && (i < oldDisabled.length)
                             && oldDisabled[i]
                             && !newDisabled[i]) {
                        // case that a track was muted and is now unmuted
                        // need to chase events and re-index this track
                        if (tempArray == null) {
                            tempArray = new byte[128][16];
                        }
                        chaseTrackEvents(i, 0, lastTick, true, tempArray);
                    }
                }
            }
        }

        /** go through all events from startTick to endTick
         * chase the controller state and program change state
         * and then set the end-states at once.
         *
         * needs to be called in synchronized state
         * @param tempArray an byte[128][16] to hold controller messages
         */
        private void chaseTrackEvents(int trackNum,
                                      long startTick,
                                      long endTick,
                                      boolean doReindex,
                                      byte[][] tempArray) {
            if (startTick > endTick) {
                // start from the beginning
                startTick = 0;
            }
            byte[] progs = new byte[16];
            // init temp array with impossible values
            for (int ch = 0; ch < 16; ch++) {
                progs[ch] = -1;
                for (int co = 0; co < 128; co++) {
                    tempArray[co][ch] = -1;
                }
            }
            Track track = tracks[trackNum];
            int size = track.size();
            try {
                for (int i = 0; i < size; i++) {
                    MidiEvent event = track.get(i);
                    if (event.getTick() >= endTick) {
                        if (doReindex && (trackNum < trackReadPos.length)) {
                            trackReadPos[trackNum] = (i > 0)?(i-1):0;
                            if (DEBUG_PUMP) Printer.println("  chaseEvents: setting trackReadPos["+trackNum+"] = "+trackReadPos[trackNum]);
                        }
                        break;
                    }
                    MidiMessage msg = event.getMessage();
                    int status = msg.getStatus();
                    int len = msg.getLength();
                    if (len == 3 && ((status & 0xF0) == ShortMessage.CONTROL_CHANGE)) {
                        if (msg instanceof ShortMessage) {
                            ShortMessage smsg = (ShortMessage) msg;
                            tempArray[smsg.getData1() & 0x7F][status & 0x0F] = (byte) smsg.getData2();
                        } else {
                            byte[] data = msg.getMessage();
                            tempArray[data[1] & 0x7F][status & 0x0F] = data[2];
                        }
                    }
                    if (len == 2 && ((status & 0xF0) == ShortMessage.PROGRAM_CHANGE)) {
                        if (msg instanceof ShortMessage) {
                            ShortMessage smsg = (ShortMessage) msg;
                            progs[status & 0x0F] = (byte) smsg.getData1();
                        } else {
                            byte[] data = msg.getMessage();
                            progs[status & 0x0F] = data[1];
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                // this happens when messages are removed
                // from the track while this method executes
            }
            int numControllersSent = 0;
            // now send out the aggregated controllers and program changes
            for (int ch = 0; ch < 16; ch++) {
                for (int co = 0; co < 128; co++) {
                    byte controllerValue = tempArray[co][ch];
                    if (controllerValue >= 0) {
                        int packedMsg = (ShortMessage.CONTROL_CHANGE | ch) | (co<<8) | (controllerValue<<16);
                        getTransmitterList().sendMessage(packedMsg, -1);
                        numControllersSent++;
                    }
                }
                // send program change *after* controllers, to
                // correctly initialize banks
                if (progs[ch] >= 0) {
                    getTransmitterList().sendMessage((ShortMessage.PROGRAM_CHANGE | ch) | (progs[ch]<<8), -1);
                }
                if (progs[ch] >= 0 || startTick == 0 || endTick == 0) {
                    // reset pitch bend on this channel (E0 00 40)
                    getTransmitterList().sendMessage((ShortMessage.PITCH_BEND | ch) | (0x40 << 16), -1);
                    // reset sustain pedal on this channel
                    getTransmitterList().sendMessage((ShortMessage.CONTROL_CHANGE | ch) | (64 << 8), -1);
                }
            }
            if (DEBUG_PUMP) Printer.println("  chaseTrackEvents track "+trackNum+": sent "+numControllersSent+" controllers.");
        }


        /** chase controllers and program for all tracks */
        synchronized void chaseEvents(long startTick, long endTick) {
            if (DEBUG_PUMP) Printer.println(">> chaseEvents from tick "+startTick+".."+(endTick-1));
            byte[][] tempArray = new byte[128][16];
            for (int t = 0; t < tracks.length; t++) {
                if ((trackDisabled == null)
                    || (trackDisabled.length <= t)
                    || (!trackDisabled[t])) {
                    // if track is not disabled, chase the events for it
                    chaseTrackEvents(t, startTick, endTick, true, tempArray);
                }
            }
            if (DEBUG_PUMP) Printer.println("<< chaseEvents");
        }


        // playback related methods (pumping)

        private long getCurrentTimeMillis() {
            return System.nanoTime() / 1000000l;
            //return perf.highResCounter() * 1000 / perfFreq;
        }

        private long millis2tick(long millis) {
            if (divisionType != Sequence.PPQ) {
                double dTick = ((((double) millis) * tempoFactor)
                                * ((double) divisionType)
                                * ((double) resolution))
                    / ((double) 1000);
                return (long) dTick;
            }
            return MidiUtils.microsec2ticks(millis * 1000,
                                            currTempo * inverseTempoFactor,
                                            resolution);
        }

        private long tick2millis(long tick) {
            if (divisionType != Sequence.PPQ) {
                double dMillis = ((((double) tick) * 1000) /
                                  (tempoFactor * ((double) divisionType) * ((double) resolution)));
                return (long) dMillis;
            }
            return MidiUtils.ticks2microsec(tick,
                                            currTempo * inverseTempoFactor,
                                            resolution) / 1000;
        }

        private void ReindexTrack(int trackNum, long tick) {
            if (trackNum < trackReadPos.length && trackNum < tracks.length) {
                trackReadPos[trackNum] = MidiUtils.tick2index(tracks[trackNum], tick);
                if (DEBUG_PUMP) Printer.println("  reindexTrack: setting trackReadPos["+trackNum+"] = "+trackReadPos[trackNum]);
            }
        }

        /* returns if changes are pending */
        private boolean dispatchMessage(int trackNum, MidiEvent event) {
            boolean changesPending = false;
            MidiMessage message = event.getMessage();
            int msgStatus = message.getStatus();
            int msgLen = message.getLength();
            if (msgStatus == MetaMessage.META && msgLen >= 2) {
                // a meta message. Do not send it to the device.
                // 0xFF with length=1 is a MIDI realtime message
                // which shouldn't be in a Sequence, but we play it
                // nonetheless.

                // see if this is a tempo message. Only on track 0.
                if (trackNum == 0) {
                    int newTempo = MidiUtils.getTempoMPQ(message);
                    if (newTempo > 0) {
                        if (event.getTick() != ignoreTempoEventAt) {
                            setTempoMPQ(newTempo); // sets ignoreTempoEventAt!
                            changesPending = true;
                        }
                        // next loop, do not ignore anymore tempo events.
                        ignoreTempoEventAt = -1;
                    }
                }
                // send to listeners
                sendMetaEvents(message);

            } else {
                // not meta, send to device
                getTransmitterList().sendMessage(message, -1);

                switch (msgStatus & 0xF0) {
                case ShortMessage.NOTE_OFF: {
                    // note off - clear the bit in the noteOnCache array
                    int note = ((ShortMessage) message).getData1() & 0x7F;
                    noteOnCache[note] &= (0xFFFF ^ (1<<(msgStatus & 0x0F)));
                    break;
                }

                case ShortMessage.NOTE_ON: {
                    // note on
                    ShortMessage smsg = (ShortMessage) message;
                    int note = smsg.getData1() & 0x7F;
                    int vel = smsg.getData2() & 0x7F;
                    if (vel > 0) {
                        // if velocity > 0 set the bit in the noteOnCache array
                        noteOnCache[note] |= 1<<(msgStatus & 0x0F);
                    } else {
                        // if velocity = 0 clear the bit in the noteOnCache array
                        noteOnCache[note] &= (0xFFFF ^ (1<<(msgStatus & 0x0F)));
                    }
                    break;
                }

                case ShortMessage.CONTROL_CHANGE:
                    // if controller message, send controller listeners
                    sendControllerEvents(message);
                    break;

                }
            }
            return changesPending;
        }


        /** the main pump method
         * @return true if end of sequence is reached
         */
        synchronized boolean pump() {
            long currMillis;
            long targetTick = lastTick;
            MidiEvent currEvent;
            boolean changesPending = false;
            boolean doLoop = false;
            boolean EOM = false;

            currMillis = getCurrentTimeMillis();
            int finishedTracks = 0;
            do {
                changesPending = false;

                // need to re-find indexes in tracks?
                if (needReindex) {
                    if (DEBUG_PUMP) Printer.println("Need to re-index at "+currMillis+" millis. TargetTick="+targetTick);
                    if (trackReadPos.length < tracks.length) {
                        trackReadPos = new int[tracks.length];
                    }
                    for (int t = 0; t < tracks.length; t++) {
                        ReindexTrack(t, targetTick);
                        if (DEBUG_PUMP_ALL) Printer.println("  Setting trackReadPos["+t+"]="+trackReadPos[t]);
                    }
                    needReindex = false;
                    checkPointMillis = 0;
                }

                // get target tick from current time in millis
                if (checkPointMillis == 0) {
                    // new check point
                    currMillis = getCurrentTimeMillis();
                    checkPointMillis = currMillis;
                    targetTick = lastTick;
                    checkPointTick = targetTick;
                    if (DEBUG_PUMP) Printer.println("New checkpoint to "+currMillis+" millis. "
                                                       +"TargetTick="+targetTick
                                                       +" new tempo="+MidiUtils.convertTempo(currTempo)+"bpm");
                } else {
                    // calculate current tick based on current time in milliseconds
                    targetTick = checkPointTick + millis2tick(currMillis - checkPointMillis);
                    if (DEBUG_PUMP_ALL) Printer.println("targetTick = "+targetTick+" at "+currMillis+" millis");
                    if ((loopEnd != -1)
                        && ((loopCount > 0 && currLoopCounter > 0)
                            || (loopCount == LOOP_CONTINUOUSLY))) {
                        if (lastTick <= loopEnd && targetTick >= loopEnd) {
                            // need to loop!
                            // only play until loop end
                            targetTick = loopEnd - 1;
                            doLoop = true;
                            if (DEBUG_PUMP) Printer.println("set doLoop to true. lastTick="+lastTick
                                                               +"  targetTick="+targetTick
                                                               +"  loopEnd="+loopEnd
                                                               +"  jumping to loopStart="+loopStart
                                                               +"  new currLoopCounter="+currLoopCounter);
                            if (DEBUG_PUMP) Printer.println("  currMillis="+currMillis
                                                               +"  checkPointMillis="+checkPointMillis
                                                               +"  checkPointTick="+checkPointTick);

                        }
                    }
                    lastTick = targetTick;
                }

                finishedTracks = 0;

                for (int t = 0; t < tracks.length; t++) {
                    try {
                        boolean disabled = trackDisabled[t];
                        Track thisTrack = tracks[t];
                        int readPos = trackReadPos[t];
                        int size = thisTrack.size();
                        // play all events that are due until targetTick
                        while (!changesPending && (readPos < size)
                               && (currEvent = thisTrack.get(readPos)).getTick() <= targetTick) {

                            if ((readPos == size -1) &&  MidiUtils.isMetaEndOfTrack(currEvent.getMessage())) {
                                // do not send out this message. Finished with this track
                                readPos = size;
                                break;
                            }
                            // TODO: some kind of heuristics if the MIDI messages have changed
                            // significantly (i.e. deleted or inserted a bunch of messages)
                            // since last time. Would need to set needReindex = true then
                            readPos++;
                            // only play this event if the track is enabled,
                            // or if it is a tempo message on track 0
                            // Note: cannot put this check outside
                            //       this inner loop in order to detect end of file
                            if (!disabled ||
                                ((t == 0) && (MidiUtils.isMetaTempo(currEvent.getMessage())))) {
                                changesPending = dispatchMessage(t, currEvent);
                            }
                        }
                        if (readPos >= size) {
                            finishedTracks++;
                        }
                        if (DEBUG_PUMP_ALL) {
                            System.out.print(" pumped track "+t+" ("+size+" events) "
                                             +" from index: "+trackReadPos[t]
                                             +" to "+(readPos-1));
                            System.out.print(" -> ticks: ");
                            if (trackReadPos[t] < size) {
                                System.out.print(""+(thisTrack.get(trackReadPos[t]).getTick()));
                            } else {
                                System.out.print("EOT");
                            }
                            System.out.print(" to ");
                            if (readPos < size) {
                                System.out.print(""+(thisTrack.get(readPos-1).getTick()));
                            } else {
                                System.out.print("EOT");
                            }
                            System.out.println();
                        }
                        trackReadPos[t] = readPos;
                    } catch(Exception e) {
                        if (Printer.debug) Printer.debug("Exception in Sequencer pump!");
                        if (Printer.debug) e.printStackTrace();
                        if (e instanceof ArrayIndexOutOfBoundsException) {
                            needReindex = true;
                            changesPending = true;
                        }
                    }
                    if (changesPending) {
                        break;
                    }
                }
                EOM = (finishedTracks == tracks.length);
                if (doLoop
                    || ( ((loopCount > 0 && currLoopCounter > 0)
                          || (loopCount == LOOP_CONTINUOUSLY))
                         && !changesPending
                         && (loopEnd == -1)
                         && EOM)) {

                    long oldCheckPointMillis = checkPointMillis;
                    long loopEndTick = loopEnd;
                    if (loopEndTick == -1) {
                        loopEndTick = lastTick;
                    }

                    // need to loop back!
                    if (loopCount != LOOP_CONTINUOUSLY) {
                        currLoopCounter--;
                    }
                    if (DEBUG_PUMP) Printer.println("Execute loop: lastTick="+lastTick
                                                       +"  loopEnd="+loopEnd
                                                       +"  jumping to loopStart="+loopStart
                                                       +"  new currLoopCounter="+currLoopCounter);
                    setTickPos(loopStart);
                    // now patch the checkPointMillis so that
                    // it points to the exact beginning of when the loop was finished

                    // $$fb TODO: although this is mathematically correct (i.e. the loop position
                    //            is correct, and doesn't drift away with several repetition,
                    //            there is a slight lag when looping back, probably caused
                    //            by the chasing.

                    checkPointMillis = oldCheckPointMillis + tick2millis(loopEndTick - checkPointTick);
                    checkPointTick = loopStart;
                    if (DEBUG_PUMP) Printer.println("  Setting currMillis="+currMillis
                                                       +"  new checkPointMillis="+checkPointMillis
                                                       +"  new checkPointTick="+checkPointTick);
                    // no need for reindexing, is done in setTickPos
                    needReindex = false;
                    changesPending = false;
                    // reset doLoop flag
                    doLoop = false;
                    EOM = false;
                }
            } while (changesPending);

            return EOM;
        }

    } // class DataPump

}
