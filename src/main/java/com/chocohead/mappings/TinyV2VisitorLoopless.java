/*
 * Copyright 2019, 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.chocohead.mappings;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

import com.chocohead.mappings.visitor.ClassVisitor;
import com.chocohead.mappings.visitor.FieldVisitor;
import com.chocohead.mappings.visitor.LocalVisitor;
import com.chocohead.mappings.visitor.MappingsVisitor;
import com.chocohead.mappings.visitor.MethodVisitor;
import com.chocohead.mappings.visitor.ParameterVisitor;

public final class TinyV2VisitorLoopless {
	private static final String HEADER_MARKER = "tiny";
	private static final char INDENT = '\t';

	public static void read(Reader reader, MappingsVisitor visitor) throws IOException {
		try (OffsetReader or = new OffsetReader(reader)) {
			read(or.readLine(), or, visitor);
		}
	}

	static void read(String firstLine, OffsetReader reader, MappingsVisitor visitor) throws IOException {
		visit(firstLine, reader, visitor);
	}

	private TinyV2VisitorLoopless() {
	}

	private static abstract class LineReader<T extends LineReader<?>> {
		protected final T parent;

		public LineReader(T parent) {
			this.parent = parent;
		}

		public abstract LineReader<?> readLine(long offset, int indent, String line);

		public void endFile() {
			parent.endFile();
		}
	}

	private static class MetadataReader extends LineReader<MetadataReader> {
		private static final String ESCAPED_NAMES_PROPERTY = "escaped-names";
		private final MappingsVisitor visitor;
		private final int namespaces;
		private boolean escapedNames;

		public MetadataReader(MappingsVisitor visitor, int namespaces) {
			super(null);

			this.visitor = visitor;
			this.namespaces = namespaces;
		}

		@Override
		public LineReader<?> readLine(long offset, int indent, String line) {
			switch (indent) {
			case 0:
				return new ClassReader(visitor, namespaces, escapedNames).readLine(offset, indent, line);

			case 1:
				String[] bits = splitIndents(line, 1, 2);
				if (bits[2] == null) {
					visitor.visitProperty(bits[1]);
					if (!escapedNames) escapedNames = ESCAPED_NAMES_PROPERTY.equals(bits[1]); 
				} else {
					visitor.visitProperty(bits[1], bits[2]);
				}
				return this;

			default:
				throw new IllegalArgumentException("Invalid indent in header: \"" + line + '"');
			}
		}

		@Override
		public void endFile() {
			visitor.finish(); //No mappings in the file if this happens
		}
	}

	private static class ClassReader extends LineReader<MetadataReader> {
		private final MethodReader methodReader = new MethodReader(this);
		private final FieldReader fieldReader = new FieldReader(this);
		protected final MappingsVisitor visitor;
		public final boolean escapedNames;
		public final int namespaces;
		private ClassVisitor currentClass;
		private boolean inClass;

		public ClassReader(MappingsVisitor visitor, int namespaces, boolean escapedNames) {
			super(null);

			this.visitor = visitor;
			this.namespaces = namespaces;
			this.escapedNames = escapedNames;
		}

		@Override
		public LineReader<?> readLine(long offset, int indent, String line) {
			out: switch (indent) {
			case 0:
				if (line.charAt(0) == 'c' && line.charAt(1) == '\t') {
					inClass = true;

					String[] names = splitIndents(line, 2, namespaces);					
					currentClass = visitor.visitClass(offset, escapedNames ? unescapeNames(names, 0) : names);

					return this;
				}
				break;

			case 1: {
				if (line.charAt(2) == '\t') {
					switch (line.charAt(1)) {
					case 'm':
						if (!inClass) break;

						if (currentClass != null) {
							String[] parts = splitIndents(line, 3, namespaces + 1);

							if (parts.length != namespaces + 1) {
								throw new IllegalArgumentException("Invalid method declaration: \"" + line.substring(1) + '"');
							}

							String desc = escapedNames ? unescape(parts[0]) : parts[0];
							String[] names = escapedNames ? unescapeNames(parts, 1) : Arrays.copyOfRange(parts, 1, parts.length);
							methodReader.giveVisitor(currentClass.visitMethod(offset, names, desc));
						}

						return methodReader;

					case 'f':
						if (!inClass) break;

						if (currentClass != null) {
							String[] parts = splitIndents(line, 3, namespaces + 1);

							if (parts.length != namespaces + 1) {
								throw new IllegalArgumentException("Invalid field declaration: \"" + line.substring(1) + '"');
							}

							String desc = escapedNames ? unescape(parts[0]) : parts[0];
							String[] names = escapedNames ? unescapeNames(parts, 1) : Arrays.copyOfRange(parts, 1, parts.length);
							fieldReader.giveVisitor(currentClass.visitField(offset, names, desc));
						}

						return fieldReader;

					case 'c':
						if (!inClass) break;

						if (currentClass != null) currentClass.visitComment(unescape(line.substring(3))); //Apparently always escaped
						return this;

					default:
						break out;
					}

					throw new IllegalArgumentException("Class member definition without class on line containing \"" + line + '"');
				}
				break out;
			}

			default:
				throw new IllegalArgumentException("Broken indent! Expected 0 or 1, found " + indent + " from line containing " + line.substring(indent));
			}

			throw new IllegalArgumentException("Invalid identifier on line \"" + line + '"');
		}

		void reset() {
			currentClass = null;
			inClass = false;
		}

		@Override
		public void endFile() {
			visitor.finish();
		}
	}

	private static class MethodReader extends LineReader<ClassReader> {
		private final ParameterReader paramReader = new ParameterReader(this);
		private final LocalReader localVarReader = new LocalReader(this);
		private MethodVisitor visitor;

		public MethodReader(ClassReader parent) {
			super(parent);
		}

		void giveVisitor(MethodVisitor visitor) {
			this.visitor = visitor;
		}

		@Override
		public LineReader<?> readLine(long offset, int indent, String line) {
			switch (indent) {
			case 2:
				if (line.charAt(3) == '\t') {
					switch (line.charAt(2)) {
					case 'p':
						if (visitor != null) {
							String[] parts = splitIndents(line, 4, parent.namespaces + 1);

							if (parts.length != parent.namespaces + 1) {
								throw new IllegalArgumentException("Invalid parameter declaration: \"" + line.substring(2) + '"');
							}

							int lvIndex = Integer.parseInt(parts[0]);
							String[] names = parent.escapedNames ? unescapeNames(parts, 1) : Arrays.copyOfRange(parts, 1, parts.length);
							paramReader.giveVisitor(visitor.visitParameter(offset, names, lvIndex));
						}

						return paramReader;

					case 'v':
						if (visitor != null) {
							String[] parts = splitIndents(line, 4, parent.namespaces + 3);

							if (parts.length != parent.namespaces + 3) {
								throw new IllegalArgumentException("Invalid local variable declaration: \"" + line.substring(2) + '"');
							}

							int lvIndex = Integer.parseInt(parts[0]);
							int startOffset = Integer.parseInt(parts[1]);
							int lvtIndex = Integer.parseInt(parts[2]);
							String[] names = parent.escapedNames ? unescapeNames(parts, 3) : Arrays.copyOfRange(parts, 3, parts.length);
							localVarReader.giveVisitor(visitor.visitLocalVariable(offset, names, lvIndex, startOffset, lvtIndex));
						}

						return localVarReader;

					case 'c':
						if (visitor != null) visitor.visitComment(unescape(line.substring(4)));
						return this;
					}
				}

				throw new IllegalArgumentException("Invalid identifier on line \"" + line + '"');

			case 0:
				parent.reset();
			case 1:
				visitor = null;
				return parent.readLine(offset, indent, line);

			default:
				throw new IllegalArgumentException("Broken indent! Expected 0 to 2, found " + indent + " from line containing " + line.substring(indent));
			}
		}
	}

	private static class ParameterReader extends LineReader<MethodReader> {
		private ParameterVisitor visitor;

		public ParameterReader(MethodReader parent) {
			super(parent);
		}

		void giveVisitor(ParameterVisitor visitor) {
			this.visitor = visitor;
		}

		@Override
		public LineReader<?> readLine(long offset, int indent, String line) {
			switch (indent) {
			case 3:
				if (line.charAt(3) == 'c' && line.charAt(4) == '\t') {
					if (visitor != null) visitor.visitComment(unescape(line.substring(5)));
					return this;
				}

				throw new IllegalArgumentException("Invalid identifier on line \"" + line + '"');

			case 0:
			case 1:
			case 2:
				visitor = null;
				return parent.readLine(offset, indent, line);

			default:
				throw new IllegalArgumentException("Broken indent! Expected 0 to 3, found " + indent + " from line containing " + line.substring(indent));
			}
		}
	}

	private static class LocalReader extends LineReader<MethodReader> {
		private LocalVisitor visitor;

		public LocalReader(MethodReader parent) {
			super(parent);
		}

		void giveVisitor(LocalVisitor visitor) {
			this.visitor = visitor;
		}

		@Override
		public LineReader<?> readLine(long offset, int indent, String line) {
			switch (indent) {
			case 3:
				if (line.charAt(3) == 'c' && line.charAt(4) == '\t') {
					if (visitor != null) visitor.visitComment(unescape(line.substring(5)));
					return this;
				}

				throw new IllegalArgumentException("Invalid identifier on line \"" + line + '"');

			case 0:
			case 1:
			case 2:
				visitor = null;
				return parent.readLine(offset, indent, line);

			default:
				throw new IllegalArgumentException("Broken indent! Expected 0 to 3, found " + indent + " from line containing " + line.substring(indent));
			}
		}
	}

	private static class FieldReader extends LineReader<ClassReader> {
		private FieldVisitor visitor;

		public FieldReader(ClassReader parent) {
			super(parent);
		}

		void giveVisitor(FieldVisitor visitor) {
			this.visitor = visitor;
		}

		@Override
		public LineReader<?> readLine(long offset, int indent, String line) {
			switch (indent) {
			case 2:
				if (line.charAt(2) == 'c' && line.charAt(3) == '\t') {
					if (visitor != null) visitor.visitComment(unescape(line.substring(4)));
					return this;
				}

				throw new IllegalArgumentException("Invalid identifier on line \"" + line + '"');

			case 0:
				parent.reset();
			case 1:
				visitor = null;
				return parent.readLine(offset, indent, line);

			default:
				throw new IllegalArgumentException("Broken indent! Expected 0 to 2, found " + indent + " from line containing " + line.substring(indent));
			}
		}
	}

	private static void visit(String firstLine, OffsetReader reader, MappingsVisitor visitor) throws IOException {
		if (firstLine == null) throw new IllegalArgumentException("Empty reader!");

		LineReader<?> lineReader; {
			String[] parts = splitIndents(firstLine, 0, 5);
			if (parts.length < 5 || !HEADER_MARKER.equals(parts[0])) {
				throw new IllegalArgumentException("Unsupported format!");
			}

			int majorVersion;
			try {
				majorVersion = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid major version!", e);
			}

			int minorVersion;
			try {
				minorVersion = Integer.parseInt(parts[2]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid minor version!", e);
			}

			visitor.visitVersion(majorVersion, minorVersion);
			visitor.visitNamespaces(Arrays.copyOfRange(parts, 3, parts.length));

			lineReader = new MetadataReader(visitor, parts.length - 3); //Number of namespaces
		}

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			try {
				lineReader = lineReader.readLine(reader.lineStart(), countIndent(line), line);
			} catch (RuntimeException e) {
				throw new IOException("Error reading line \"" + line + '"', e);
			}
		}

		lineReader.endFile();
	}

	private static int countIndent(String line) {
		int length = line.length(), out = 0;

		while (out < length && line.charAt(out) == INDENT) {
			out++;
		}

		return out;
	}

	static String[] splitIndents(String line, int offset, int partCountHint) {
		String[] out = new String[Math.max(1, partCountHint)];

		int split, parts = 0;
		while ((split = line.indexOf(INDENT, offset)) >= 0) {
			if (parts == out.length) out = Arrays.copyOf(out, out.length * 2);
			if (split - offset > 0) {
				out[parts++] = line.substring(offset, split);
			} else parts++;
			offset = split + 1;
		}

		if (parts == out.length) out = Arrays.copyOf(out, out.length + 1);
		out[parts++] = line.substring(offset);

		return parts == out.length ? out : Arrays.copyOf(out, parts);
	}

	static String[] unescapeNames(String[] parts, int skip) {
		String[] out = new String[parts.length - skip];

		for (int i = 0; i < out.length; i++) {
			out[i] = unescape(parts[i + skip]);
		}

		return out;
	}

	private static final String TO_ESCAPE = "\\\n\r\0\t";
	private static final String ESCAPED = "\\nr0t";
	static String unescape(String part) {
		int split = part.indexOf('\\');
		if (split < 0) return part;

		StringBuilder out = new StringBuilder(part.length() - 1);
		int start = 0;

		do {
			out.append(part, start, split++);

			int type;
			if (split >= part.length()) {
				throw new RuntimeException("incomplete escape sequence at the end");
			} else if ((type = ESCAPED.indexOf(part.charAt(split))) < 0) {
				throw new RuntimeException("invalid escape character: \\" + part.charAt(split));
			} else {
				out.append(TO_ESCAPE.charAt(type));
			}

			start = split + 1;
		} while ((split = part.indexOf('\\', start)) >= 0);

		return out.append(part, start, part.length()).toString();
	}
}