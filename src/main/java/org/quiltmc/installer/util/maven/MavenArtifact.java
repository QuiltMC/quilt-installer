/*
 * Copyright 2026 QuiltMC
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

package org.quiltmc.installer.util.maven;

import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

public class MavenArtifact {
	private URL url;
	private InputStream in;

	public MavenArtifact(URL url) throws IOException {
		if (url == null) throw new IllegalArgumentException("URL is null");
		this.url = url;
		in = url.openStream();
	}

	public MavenArtifact(String urlString) throws IOException {
		try {
			url = URI.create(urlString).toURL();
			if (url == null) throw new IllegalArgumentException("URL is null");
			this.url = url;
			in = url.openStream();
		} catch (MalformedURLException ex) {
			throw new IllegalArgumentException("Incorrect URL");
		}
	}

	public URL getUrl() {
		return url;
	}

	public BufferedReader getReader() {
		return new BufferedReader(new InputStreamReader(in));
	}

	public String getContents() throws IOException {
		String line;
		BufferedReader reader = getReader();
		StringBuilder strBuilder = new StringBuilder();
		while ((line = reader.readLine()) != null) strBuilder.append(line);
		return strBuilder.toString();
	}
}
