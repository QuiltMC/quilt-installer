package org.quiltmc.installer;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

// Gui code MUST be in a separate class because not every JRE supports Swing
public class GuiMain {
    static void tryShowGuiError() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | IllegalAccessException | InstantiationException e) {
            // oh well
            e.printStackTrace();
        }

        String javaDownloadUrl = "https://adoptium.net/temurin/releases/?package=jdk&version=lts";

        // best-effort attempt to make the download URL more user friendly
        String osName = System.getProperty("os.name", "unknown");
        if(osName.contains("Windows")) {
            javaDownloadUrl += "&os=windows";
        }
        else if (osName.contains("Linux")) {
            javaDownloadUrl += "&os=linux";
        }
        else if (osName.contains("OS X")) {
            javaDownloadUrl += "&os=mac";
        }

        String osArch = System.getProperty("os.arch", "unknown");
        if (osArch.equals("x86_64") || osArch.equals("amd64") || osArch.equals("x64")) {
            javaDownloadUrl += "&arch=x64";
        }
        else if (osArch.equals("aarch64") || osArch.equals("arm64")) {
            javaDownloadUrl += "&arch=aarch64";
        }

        showPopup("Quilt Installer crashed!", String.format("Quilt Installer needs Java %s to run." +
                "<br><br>Install the latest LTS release of Java from <a href=\"%s\">Adoptium</a> and try again." +
                "<br><br>If you need help, ask in the <a href=\"discord.quiltmc.org\">Quilt Discord server</a>.", BuildConstants8.MIN_JAVA_VERSION, javaDownloadUrl), JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
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
