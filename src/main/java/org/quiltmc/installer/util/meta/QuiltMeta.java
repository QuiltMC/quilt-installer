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

package org.quiltmc.installer.util.meta;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.quiltmc.installer.util.meta.model.v3.IntermediaryVersionV3;
import org.quiltmc.installer.util.meta.model.v3.QuiltLoaderVersionV3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class QuiltMeta {
	public static final String DEFAULT_META_URL = "https://meta.quiltmc.org";
	private final Map<Endpoint<?>, Object> endpoints;

	public static final Endpoint<List<String>> LOADER_VERSIONS_ENDPOINT = Endpoint.builder("/v3/versions/loader").withType(new TypeToken<List<QuiltLoaderVersionV3>>(){}).mappedTo(list -> list.stream().map(QuiltLoaderVersionV3::version).toList()).build();

	/**
	 * An endpoint for intermediary versions.
	 *
	 * <p>The returned map has the version as the key and the maven artifact as the value
	 */
	public static final Endpoint<Map<String, String>> INTERMEDIARY_VERSIONS_ENDPOINT = Endpoint.builder("/v3/versions/intermediary").withType(new TypeToken<List<IntermediaryVersionV3>>(){}).mappedTo(list -> list.stream().collect(Collectors.toMap(IntermediaryVersionV3::version, IntermediaryVersionV3::maven))).build();

	public static CompletableFuture<QuiltMeta> create(Endpoint<?>... endpoints) {
		return create(Set.of(endpoints));
	}

	public static CompletableFuture<QuiltMeta> create(Set<Endpoint<?>> endpoints) {
		if(endpoints.isEmpty())
			throw new IllegalArgumentException("No endpoints provided");

		Map<Endpoint<?>, CompletableFuture<?>> futures = new HashMap<>();
		for (Endpoint<?> endpoint : endpoints) {
			futures.put(endpoint, CompletableFuture.supplyAsync(() -> {
				try {
					return endpoint.get();
				} catch (IOException e) {
					throw new UncheckedIOException(e); // Handled via .exceptionally(...)
				} catch (JsonSyntaxException e) {
					throw new RuntimeException(e); // Handled via .exceptionally(...)
				}
			}));
		}

		CompletableFuture<Void> future = CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new));

		return future.thenApply(_v -> {
			Map<Endpoint<?>, Object> resolvedEndpoints = new HashMap<>();

			for (Map.Entry<Endpoint<?>, CompletableFuture<?>> entry : futures.entrySet()) {
				resolvedEndpoints.put(entry.getKey(), entry.getValue().join());
			}

			return new QuiltMeta(resolvedEndpoints);
		});
	}

	private QuiltMeta(Map<Endpoint<?>, Object> endpoints) {
		this.endpoints = endpoints;
	}

	public <T> T getEndpoint(Endpoint<T> endpoint) {
		Objects.requireNonNull(endpoint, "Endpoint cannot be null");

		@SuppressWarnings("unchecked")
		T result = (T) this.endpoints.get(endpoint);

		if (result == null)
			throw new IllegalArgumentException("Endpoint had no value!");

		return result;
	}

}
