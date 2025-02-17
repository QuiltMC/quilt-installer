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

package org.quiltmc.installer.util.meta.model.v3;

// TODO once meta supports these other properties, validate hashes after downloading + display progress bar!
public record QuiltLoaderVersionV3(String maven, String version, int build, String separator/*, @SerializedName("file_size") long fileSize, Map<String, String> hashes*/) {
}
