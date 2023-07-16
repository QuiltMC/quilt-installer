/*
 * Copyright 2021 QuiltMC
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

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public final class Localization {
	private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("lang/installer", Locale.getDefault());

	public static String get(String key) {
		try {
			return BUNDLE.getString(key);
		} catch (MissingResourceException ex) {
			ex.printStackTrace();
			return key;
		}

	}

	public static String createFrom(String key, Object... arguments) {
		try {
			return new MessageFormat(BUNDLE.getString(key)).format(arguments);
		} catch (MissingResourceException ex) {
			ex.printStackTrace();
			return key;
		}

	}

}
