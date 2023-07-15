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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonWriter;

/**
 * Utilities for reading and writing to/from json files from maps, lists, numbers, booleans and null.
 */
public final class Gsons {
	/**
	 * Read json and convert it into the base types.
	 *
	 * @param reader the reader
	 * @return one of the base types representing the json file
	 * @throws IOException if issues occurred while reading
	 */
	public static Object read(JsonReader reader) throws IOException {
		switch (reader.peek()) {
		case BEGIN_ARRAY:
			List<Object> list = new ArrayList<>();

			reader.beginArray();

			while (reader.hasNext()) {
				list.add(read(reader));
			}

			reader.endArray();

			return list;
		case BEGIN_OBJECT:
			Map<String, Object> object = new LinkedHashMap<>();

			reader.beginObject();

			while (reader.hasNext()) {
				String key = reader.nextName();
				object.put(key, read(reader));
			}

			reader.endObject();

			return object;
		case STRING:
			return reader.nextString();
		case NUMBER:
			return reader.nextDouble();
		case BOOLEAN:
			return reader.nextBoolean();
		case NULL:
			return null;
		// Unused, probably a sign of malformed json
		case NAME:
		case END_DOCUMENT:
		case END_ARRAY:
		case END_OBJECT:
		default:
			throw new IllegalStateException();
		}
	}

	public static void write(JsonWriter writer, @Nullable Object input) throws IOException {
		// Object
		if (input instanceof Map) {
			writer.beginObject();

			for (Map.Entry<?, ?> entry : ((Map<?, ?>) input).entrySet()) {
				writer.name(entry.getKey().toString());
				write(writer, entry.getValue());
			}

			writer.endObject();
		// Array
		} else if (input instanceof List) {
			writer.beginArray();

			for (Object element : ((List<?>) input)) {
				write(writer, element);
			}

			writer.endArray();
		} else if (input instanceof Number) {
			writer.value((Number) input);
		} else if (input instanceof String) {
			writer.value((String) input);
		} else if (input instanceof Boolean) {
			writer.value((boolean) input);
		} else if (input == null) {
			writer.nullValue();
		} else {
			throw new IllegalArgumentException(String.format("Don't know how to convert %s to json", input));
		}
	}

	private Gsons() {
	}
}
