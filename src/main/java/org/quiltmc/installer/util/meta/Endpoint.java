/*
 * Copyright 2025 QuiltMC
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

package org.quiltmc.installer.util.meta;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.quiltmc.installer.Connections;
import org.quiltmc.installer.util.Util;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.function.Function;

public final class Endpoint<T> {
    private final URI url;
    private final EndpointReader<T> deserializer;

    private Endpoint(String endpointPath, EndpointReader<T> deserializer) {
        this(URI.create(QuiltMeta.DEFAULT_META_URL + endpointPath), deserializer);
    }

    public Endpoint(URI url, EndpointReader<T> deserializer) {
        this.url = url;
        this.deserializer = deserializer;
    }

    public static Builder<JsonElement> builder(String endpointPath) {
        return new Builder<>(endpointPath);
    }

    @Override
    public String toString() {
        return "Endpoint{url=\"" + this.url + "\"}";
    }

    public URI getUrl() {
        return url;
    }

    public T get() throws IOException, JsonParseException {
        try (var reader = Connections.openReader(this.getUrl())) {
            return deserializer.apply(reader);
        }
    }

    public static class Builder<T> {

        private final String endpointPath;
        @SuppressWarnings("unchecked")
        private Function<Object, T> mapper = o -> (T) o;
        private Type type = JsonElement.class;

        private Builder(String endpointPath) {
            this.endpointPath = endpointPath;
        }

        @SuppressWarnings("unchecked")
        public <U> Builder<U> withType(TypeToken<U> typeToken) {
            this.type = typeToken.getType();
            return (Builder<U>) this;
        }

        @SuppressWarnings("unchecked")
        public <U> Builder<U> mappedTo(Function<T, U> mapper) {
            var cast = (Builder<U>) this;
            cast.mapper = (Function<Object, U>) mapper;
            return cast;
        }

        public Endpoint<T> build() {
            EndpointReader<T> reader = input -> mapper.apply(Util.GSON.fromJson(input, type));
            return new Endpoint<>(endpointPath, reader);
        }
    }
}
