/*
 * Copyright 2019 FabricMC
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

package com.chocohead.mappings;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class MappingsProvider {
	private MappingsProvider() {
	}

	public static Mappings createEmptyMappings() {
		return DummyMappings.INSTANCE;
	}

	public static Mappings readTinyMappings(InputStream stream) throws IOException {
		return readTinyMappings(stream, true);
	}

	public static Mappings readTinyMappings(InputStream stream, boolean saveMemoryUsage) throws IOException {
		try (OffsetReader reader = new OffsetReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String headerLine = reader.readLine();

			if (headerLine == null) {
				throw new EOFException();
			} else if (headerLine.startsWith("v1\t")) {
				return new TinyMappings(headerLine, reader,
						saveMemoryUsage ? new MappedStringDeduplicator.MapBased() : MappedStringDeduplicator.EMPTY
				);
			} else if (headerLine.startsWith("tiny\t2\t")) {
				return TinyV2VisitorBridge.read(headerLine, reader,
						saveMemoryUsage ? new MappedStringDeduplicator.MapBased() : MappedStringDeduplicator.EMPTY
				);
			} else {
				throw new IOException("Invalid mapping version!");
			}
		}
	}

	public static ExtendedMappings readFullTinyMappings(InputStream stream, boolean saveMemoryUsage) throws IOException {
		try (OffsetReader reader = new OffsetReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String headerLine = reader.readLine();

			if (headerLine == null) {
				throw new EOFException();
			} else if (headerLine.startsWith("v1\t")) {
				return ExtendedMappings.wrap(new TinyMappings(headerLine, reader,
						saveMemoryUsage ? new MappedStringDeduplicator.MapBased() : MappedStringDeduplicator.EMPTY
				));
			} else if (headerLine.startsWith("tiny\t2\t")) {
				return TinyV2VisitorBridge.fullyRead(headerLine, reader,
						saveMemoryUsage ? new MappedStringDeduplicator.MapBased() : MappedStringDeduplicator.EMPTY
				);
			} else {
				throw new IOException("Invalid mapping version!");
			}
		}
	}
}
