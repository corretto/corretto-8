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

package javax.security.auth.callback;

/**
 * <p> Underlying security services instantiate and pass a
 * {@code TextInputCallback} to the {@code handle}
 * method of a {@code CallbackHandler} to retrieve generic text
 * information.
 *
 * @see javax.security.auth.callback.CallbackHandler
 */
public class TextInputCallback implements Callback, java.io.Serializable {

    private static final long serialVersionUID = -8064222478852811804L;

    /**
     * @serial
     * @since 1.4
     */
    private String prompt;
    /**
     * @serial
     * @since 1.4
     */
    private String defaultText;
    /**
     * @serial
     * @since 1.4
     */
    private String inputText;

    /**
     * Construct a {@code TextInputCallback} with a prompt.
     *
     * <p>
     *
     * @param prompt the prompt used to request the information.
     *
     * @exception IllegalArgumentException if {@code prompt} is null
     *                  or if {@code prompt} has a length of 0.
     */
    public TextInputCallback(String prompt) {
        if (prompt == null || prompt.length() == 0)
            throw new IllegalArgumentException();
        this.prompt = prompt;
    }

    /**
     * Construct a {@code TextInputCallback} with a prompt
     * and default input value.
     *
     * <p>
     *
     * @param prompt the prompt used to request the information. <p>
     *
     * @param defaultText the text to be used as the default text displayed
     *                  with the prompt.
     *
     * @exception IllegalArgumentException if {@code prompt} is null,
     *                  if {@code prompt} has a length of 0,
     *                  if {@code defaultText} is null
     *                  or if {@code defaultText} has a length of 0.
     */
    public TextInputCallback(String prompt, String defaultText) {
        if (prompt == null || prompt.length() == 0 ||
            defaultText == null || defaultText.length() == 0)
            throw new IllegalArgumentException();

        this.prompt = prompt;
        this.defaultText = defaultText;
    }

    /**
     * Get the prompt.
     *
     * <p>
     *
     * @return the prompt.
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Get the default text.
     *
     * <p>
     *
     * @return the default text, or null if this {@code TextInputCallback}
     *          was not instantiated with {@code defaultText}.
     */
    public String getDefaultText() {
        return defaultText;
    }

    /**
     * Set the retrieved text.
     *
     * <p>
     *
     * @param text the retrieved text, which may be null.
     *
     * @see #getText
     */
    public void setText(String text) {
        this.inputText = text;
    }

    /**
     * Get the retrieved text.
     *
     * <p>
     *
     * @return the retrieved text, which may be null.
     *
     * @see #setText
     */
    public String getText() {
        return inputText;
    }
}
