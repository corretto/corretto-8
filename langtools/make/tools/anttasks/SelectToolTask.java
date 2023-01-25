/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

package anttasks;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import javax.swing.SwingUtilities;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/**
 * Task to allow the user to control langtools tools built when using NetBeans.
 *
 * There are two primary modes.
 * 1) Property mode. In this mode, property names are provided to get values
 * that may be specified by the user, either directly in a GUI dialog, or
 * read from a properties file. If the GUI dialog is invoked, values may
 * optionally be set for future use.
 * 2) Setup mode. In this mode, no property names are provided, and the GUI
 * is invoked to allow the user to set or reset values for use in property mode.
 */
public class SelectToolTask extends Task {

    enum ToolChoices {
        NONE(""),
        JAVAC("javac"),
        JAVADOC("javadoc"),
        JAVAH("javah"),
        JAVAP("javap");

        String toolName;
        boolean bootstrap;

        ToolChoices(String toolName) {
            this(toolName, false);
        }

        ToolChoices(String toolName, boolean bootstrap) {
            this.toolName = toolName;
            this.bootstrap = bootstrap;
        }

        @Override
        public String toString() {
            return toolName;
        }
    }

    /**
     * Set the location of the private properties file used to keep the retain
     * user preferences for this repository.
     */
    public void setPropertyFile(File propertyFile) {
        this.propertyFile = propertyFile;
    }

    /**
     * Set the name of the property which will be set to the name of the
     * selected tool, if any. If no tool is selected, the property will
     * remain unset.
     */
    public void setToolProperty(String toolProperty) {
        this.toolProperty = toolProperty;
    }

    /**
     * Set the name of the property which will be set to the execution args of the
     * selected tool, if any. The args default to an empty string.
     */
    public void setArgsProperty(String argsProperty) {
        this.argsProperty = argsProperty;
    }

    /**
     * Set the name of the property which will be set to the execution args of the
     * selected tool, if any. The args default to an empty string.
     */
    public void setBootstrapProperty(String bootstrapProperty) {
        this.bootstrapProperty = bootstrapProperty;
    }

    /**
     * Specify whether or not to pop up a dialog if the user has not specified
     * a default value for a property.
     */
    public void setAskIfUnset(boolean askIfUnset) {
        this.askIfUnset = askIfUnset;
    }

    @Override
    public void execute() {
        Project p = getProject();

        Properties props = readProperties(propertyFile);
        toolName = props.getProperty("tool.name");
        toolBootstrap = props.getProperty("tool.bootstrap") != null;
        if (toolName != null) {
            toolArgs = props.getProperty(toolName + ".args", "");
        }

        if (toolProperty == null ||
            askIfUnset && (toolName == null
                || (argsProperty != null && toolArgs == null))) {
            showGUI(props);
        }

        // finally, return required values, if any
        if (toolProperty != null && !(toolName == null || toolName.equals(""))) {
            p.setProperty(toolProperty, toolName);
            if (toolBootstrap)
                p.setProperty(bootstrapProperty, "true");

            if (argsProperty != null && toolArgs != null)
                p.setProperty(argsProperty, toolArgs);
        }
    }

    void showGUI(Properties fileProps) {
        Properties guiProps = new Properties(fileProps);
        JOptionPane p = createPane(guiProps);
        p.createDialog("Select Tool").setVisible(true);

        toolName = ((ToolChoices)toolChoice.getSelectedItem()).toolName;
        toolArgs = argsField.getText();
        toolBootstrap = bootstrapCheckbox.isSelected();
        if (defaultCheck.isSelected()) {
            if (toolName.equals("")) {
                fileProps.remove("tool.name");
                fileProps.remove("tool.bootstrap");
            } else {
                fileProps.put("tool.name", toolName);
                if (toolBootstrap) {
                    fileProps.put("tool.bootstrap", "true");
                } else {
                    fileProps.remove("tool.bootstrap");
                }
                fileProps.put(toolName + ".args", toolArgs);
            }
            writeProperties(propertyFile, fileProps);
        }
    }

    JOptionPane createPane(final Properties props) {
        JPanel body = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        lc.insets.right = 10;
        lc.insets.bottom = 3;
        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.gridwidth = GridBagConstraints.NONE;
        fc.insets.bottom = 3;

        JPanel toolPane = new JPanel(new GridBagLayout());

        JLabel toolLabel = new JLabel("Tool:");
        body.add(toolLabel, lc);
        EnumSet<ToolChoices> toolChoices = toolProperty == null ?
                EnumSet.allOf(ToolChoices.class) : EnumSet.range(ToolChoices.JAVAC, ToolChoices.JAVAP);
        toolChoice = new JComboBox(toolChoices.toArray());
        if (toolName != null)
            toolChoice.setSelectedItem(ToolChoices.valueOf(toolName.toUpperCase()));
        toolChoice.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                String tn = ((ToolChoices)e.getItem()).toolName;
                argsField.setText(getDefaultArgsForTool(props, tn));
                if (toolProperty != null)
                    okButton.setEnabled(!tn.equals(""));
            }
        });
        GridBagConstraints checkConstraint = new GridBagConstraints();
        fc.anchor = GridBagConstraints.EAST;

        GridBagConstraints toolConstraint = new GridBagConstraints();
        fc.anchor = GridBagConstraints.WEST;

        toolPane.add(toolChoice, toolConstraint);
        bootstrapCheckbox = new JCheckBox("bootstrap", toolBootstrap);
        toolPane.add(bootstrapCheckbox, checkConstraint);

        body.add(toolPane, fc);

        argsField = new JTextField(getDefaultArgsForTool(props, toolName), 40);
        if (toolProperty == null || argsProperty != null) {
            JLabel argsLabel = new JLabel("Args:");
            body.add(argsLabel, lc);
            body.add(argsField, fc);
            argsField.addFocusListener(new FocusListener() {
                public void focusGained(FocusEvent e) {
                }
                public void focusLost(FocusEvent e) {
                    String toolName = ((ToolChoices)toolChoice.getSelectedItem()).toolName;
                    if (toolName.length() > 0)
                        props.put(toolName + ".args", argsField.getText());
                }
            });
        }

        defaultCheck = new JCheckBox("Set as default");
        if (toolProperty == null)
            defaultCheck.setSelected(true);
        else
            body.add(defaultCheck, fc);

        final JOptionPane p = new JOptionPane(body);
        okButton = new JButton("OK");
        okButton.setEnabled(toolProperty == null || (toolName != null && !toolName.equals("")));
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JDialog d = (JDialog) SwingUtilities.getAncestorOfClass(JDialog.class, p);
                d.setVisible(false);
            }
        });
        p.setOptions(new Object[] { okButton });

        return p;
    }

    Properties readProperties(File file) {
        Properties p = new Properties();
        if (file != null && file.exists()) {
            Reader in = null;
            try {
                in = new BufferedReader(new FileReader(file));
                p.load(in);
                in.close();
            } catch (IOException e) {
                throw new BuildException("error reading property file", e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        throw new BuildException("cannot close property file", e);
                    }
                }
            }
        }
        return p;
    }

    void writeProperties(File file, Properties p) {
        if (file != null) {
            Writer out = null;
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists())
                    dir.mkdirs();
                out = new BufferedWriter(new FileWriter(file));
                p.store(out, "langtools properties");
                out.close();
            } catch (IOException e) {
                throw new BuildException("error writing property file", e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        throw new BuildException("cannot close property file", e);
                    }
                }
            }
        }
    }

    String getDefaultArgsForTool(Properties props, String tn) {
        return (tn == null || tn.equals("")) ? "" : props.getProperty(tn + ".args", "");
    }

    // Ant task parameters
    private boolean askIfUnset;
    private String toolProperty;
    private String bootstrapProperty;
    private String argsProperty;
    private File propertyFile;

    // GUI components
    private JComboBox toolChoice;
    private JCheckBox bootstrapCheckbox;
    private JTextField argsField;
    private JCheckBox defaultCheck;
    private JButton okButton;

    // Result values for the client
    private String toolName;
    private boolean toolBootstrap;
    private String toolArgs;
}
