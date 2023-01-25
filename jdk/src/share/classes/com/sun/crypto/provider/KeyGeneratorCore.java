/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

/**
 * KeyGeneratore core implementation and individual key generator
 * implementations. Because of US export regulations, we cannot use
 * subclassing to achieve code sharing between the key generator
 * implementations for our various algorithms. Instead, we have the
 * core implementation in this KeyGeneratorCore class, which is used
 * by the individual implementations. See those further down in this
 * file.
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
final class KeyGeneratorCore {

    // algorithm name to use for the generator keys
    private final String name;

    // default key size in bits
    private final int defaultKeySize;

    // current key size in bits
    private int keySize;

    // PRNG to use
    private SecureRandom random;

    /**
     * Construct a new KeyGeneratorCore object with the specified name
     * and defaultKeySize. Initialize to default key size in case the
     * application does not call any of the init() methods.
     */
    KeyGeneratorCore(String name, int defaultKeySize) {
        this.name = name;
        this.defaultKeySize = defaultKeySize;
        implInit(null);
    }

    // implementation for engineInit(), see JCE doc
    // reset keySize to default
    void implInit(SecureRandom random) {
        this.keySize = defaultKeySize;
        this.random = random;
    }

    // implementation for engineInit(), see JCE doc
    // we do not support any parameters
    void implInit(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        throw new InvalidAlgorithmParameterException
            (name + " key generation does not take any parameters");
    }

    // implementation for engineInit(), see JCE doc
    // we enforce a general 40 bit minimum key size for security
    void implInit(int keysize, SecureRandom random) {
        if (keysize < 40) {
            throw new InvalidParameterException
                ("Key length must be at least 40 bits");
        }
        this.keySize = keysize;
        this.random = random;
    }

    // implementation for engineInit(), see JCE doc
    // generate the key
    SecretKey implGenerateKey() {
        if (random == null) {
            random = SunJCE.getRandom();
        }
        byte[] b = new byte[(keySize + 7) >> 3];
        random.nextBytes(b);
        return new SecretKeySpec(b, name);
    }

    // nested static classes for the HmacSHA-2 family of key generator
    abstract static class HmacSHA2KG extends KeyGeneratorSpi {
        private final KeyGeneratorCore core;
        protected HmacSHA2KG(String algoName, int len) {
            core = new KeyGeneratorCore(algoName, len);
        }
        protected void engineInit(SecureRandom random) {
            core.implInit(random);
        }
        protected void engineInit(AlgorithmParameterSpec params,
                SecureRandom random) throws InvalidAlgorithmParameterException {
            core.implInit(params, random);
        }
        protected void engineInit(int keySize, SecureRandom random) {
            core.implInit(keySize, random);
        }
        protected SecretKey engineGenerateKey() {
            return core.implGenerateKey();
        }

        public static final class SHA224 extends HmacSHA2KG {
            public SHA224() {
                super("HmacSHA224", 224);
            }
        }
        public static final class SHA256 extends HmacSHA2KG {
            public SHA256() {
                super("HmacSHA256", 256);
            }
        }
        public static final class SHA384 extends HmacSHA2KG {
            public SHA384() {
                super("HmacSHA384", 384);
            }
        }
        public static final class SHA512 extends HmacSHA2KG {
            public SHA512() {
                super("HmacSHA512", 512);
            }
        }
    }

    // nested static class for the RC2 key generator
    public static final class RC2KeyGenerator extends KeyGeneratorSpi {
        private final KeyGeneratorCore core;
        public RC2KeyGenerator() {
            core = new KeyGeneratorCore("RC2", 128);
        }
        protected void engineInit(SecureRandom random) {
            core.implInit(random);
        }
        protected void engineInit(AlgorithmParameterSpec params,
                SecureRandom random) throws InvalidAlgorithmParameterException {
            core.implInit(params, random);
        }
        protected void engineInit(int keySize, SecureRandom random) {
            if ((keySize < 40) || (keySize > 1024)) {
                throw new InvalidParameterException("Key length for RC2"
                    + " must be between 40 and 1024 bits");
            }
            core.implInit(keySize, random);
        }
        protected SecretKey engineGenerateKey() {
            return core.implGenerateKey();
        }
    }

    // nested static class for the ARCFOUR (RC4) key generator
    public static final class ARCFOURKeyGenerator extends KeyGeneratorSpi {
        private final KeyGeneratorCore core;
        public ARCFOURKeyGenerator() {
            core = new KeyGeneratorCore("ARCFOUR", 128);
        }
        protected void engineInit(SecureRandom random) {
            core.implInit(random);
        }
        protected void engineInit(AlgorithmParameterSpec params,
                SecureRandom random) throws InvalidAlgorithmParameterException {
            core.implInit(params, random);
        }
        protected void engineInit(int keySize, SecureRandom random) {
            if ((keySize < 40) || (keySize > 1024)) {
                throw new InvalidParameterException("Key length for ARCFOUR"
                    + " must be between 40 and 1024 bits");
            }
            core.implInit(keySize, random);
        }
        protected SecretKey engineGenerateKey() {
            return core.implGenerateKey();
        }
    }

}
