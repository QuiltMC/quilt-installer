/*
 * Copyright 2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.installer;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// We enter in java 8 so we can display a nice error message if things don't work
public class Main {
    public static void main(String[] args) {
        try {
            // Only use CLI mode if there are any arguments or we have a headless JVM
            if (GraphicsEnvironment.isHeadless() || args.length != 0) {
                MethodHandles.lookup()
                        .findStatic(Class.forName("org.quiltmc.installer.CliInstaller"), "run", MethodType.methodType(void.class, String[].class))
                        .invoke();
            } else {
                MethodHandles.lookup()
                        .findStatic(Class.forName("org.quiltmc.installer.gui.swing.SwingInstaller"), "run", MethodType.methodType(void.class))
                        .invoke();
            }
        } catch (UnsupportedClassVersionError error) {
            if (GraphicsEnvironment.isHeadless() || args.length != 0) {
                System.out.println("Quilt Installer requires Java 17 to run.");
                System.exit(1);
            } else {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ClassNotFoundException | UnsupportedLookAndFeelException | IllegalAccessException |
                         InstantiationException e) {
                    // oh well
                    e.printStackTrace();
                }


                showPopup("Quilt Installer crashed!", "<html>Quilt Installer needs Java 17 to run.<br><br>" +
                        "Install the latest LTS release of Java from <a href=\"https://adoptium.net/\">Eclipse Adoptium</a> and try again." +
                        "<br><br>If you need help, ask on the <a href=\"https://forums.quiltmc.org/c/9/\">Quilt Forum</a>" +
                        " or in the <a href=\"discord.quiltmc.org\">Quilt Discord server</a>.</html>", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Copied from AbstractPanel
    protected static boolean showPopup(String title, String description, int optionType, int messageType) {
        JEditorPane pane = new JEditorPane("text/html",
                "<html><body style=\"" + buildEditorPaneStyle() + "\">" + description + "</body></html>");
        pane.setEditable(false);
        pane.addHyperlinkListener(e -> {
            try {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } else {
                        throw new UnsupportedOperationException("Failed to open " + e.getURL().toString());
                    }
                }
            } catch (Throwable throwable) {
                // Oh well
                throwable.printStackTrace();
            }
        });
        return JOptionPane.showOptionDialog(null, pane, title, optionType, messageType, null, null, null) == 0;
    }

    private static String buildEditorPaneStyle() {
        JLabel label = new JLabel();
        Font font = label.getFont();
        Color color = label.getBackground();

        return String.format(
                "font-family:%s;font-weight:%s;font-size:%dpt;background-color: rgb(%d,%d,%d);",
                font.getFamily(), (font.isBold() ? "bold" : "normal"), font.getSize(), color.getRed(), color.getGreen(), color.getBlue()
        );
    }
}
