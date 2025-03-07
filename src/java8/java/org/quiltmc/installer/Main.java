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

import java.awt.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// We enter in java 8 so we can display a nice error message if things don't work
public class Main {

    public static void main(String[] args) {

        // Only use CLI mode if there are any arguments or we have a headless JVM
        boolean cliMode = GraphicsEnvironment.isHeadless() || args.length != 0;
        if(!cliMode) {
            try {
                Class.forName("javax.swing.UnsupportedLookAndFeelException");
            } catch (ClassNotFoundException e) {
                System.err.println("Swing is not available, falling back to CLI mode.");
                cliMode = true;
            }
        }

        try {
            if (cliMode) {
                //noinspection ConfusingArgumentToVarargsMethod
                MethodHandles.lookup()
                        .findStatic(Class.forName("org.quiltmc.installer.CliInstaller"), "run", MethodType.methodType(void.class, String[].class))
                        .invokeExact(args);
            } else {
                MethodHandles.lookup()
                        .findStatic(Class.forName("org.quiltmc.installer.gui.swing.SwingInstaller"), "run", MethodType.methodType(void.class))
                        .invokeExact();
            }
        } catch (UnsupportedClassVersionError error) {
            System.err.printf("Quilt Installer requires Java %s or greater to run.%n", BuildConstants8.MIN_JAVA_VERSION);
            if (!cliMode) {
                GuiMain.tryShowGuiError();
            }

            System.exit(1);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
