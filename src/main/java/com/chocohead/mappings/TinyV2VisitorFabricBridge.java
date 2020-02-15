/*
 * Copyright 2019, 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.chocohead.mappings;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.chocohead.mappings.MappedStringDeduplicator.Category;
import com.chocohead.mappings.model.CommentEntry;
import com.chocohead.mappings.model.CommentEntry.Class;
import com.chocohead.mappings.model.CommentEntry.Field;
import com.chocohead.mappings.model.CommentEntry.LocalVariableComment;
import com.chocohead.mappings.model.CommentEntry.Method;
import com.chocohead.mappings.model.CommentEntry.Parameter;
import com.chocohead.mappings.model.Comments;
import com.chocohead.mappings.model.CommentsImpl;
import com.chocohead.mappings.model.LocalVariable;
import com.chocohead.mappings.model.LocalVariableEntry;
import com.chocohead.mappings.model.MethodParameter;
import com.chocohead.mappings.model.MethodParameterEntry;
import com.chocohead.mappings.visitor.ClassVisitor;
import com.chocohead.mappings.visitor.FieldVisitor;
import com.chocohead.mappings.visitor.LocalVisitor;
import com.chocohead.mappings.visitor.MappingsVisitor;
import com.chocohead.mappings.visitor.MethodVisitor;
import com.chocohead.mappings.visitor.ParameterVisitor;

public class TinyV2VisitorFabricBridge implements MappingsVisitor {
	public static Mappings read(InputStream stream, boolean saveMemoryUsage) throws IOException {
		try (OffsetReader reader = new OffsetReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			return read(reader.readLine(), reader, saveMemoryUsage ? new MappedStringDeduplicator.MapBased() : MappedStringDeduplicator.EMPTY, false, false, false);
		}
	}

	public static ExtendedMappings fullyRead(InputStream stream, boolean saveMemoryUsage) throws IOException {
		try (OffsetReader reader = new OffsetReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			return read(reader.readLine(), reader, saveMemoryUsage ? new MappedStringDeduplicator.MapBased() : MappedStringDeduplicator.EMPTY, true, true, true);
		}
	}

	private static ExtendedMappings read(String firstLine, OffsetReader reader, MappedStringDeduplicator deduplicator, boolean keepParams, boolean keepLocals, boolean keepComments) throws IOException {
		TinyV2VisitorFabricBridge visitor = new TinyV2VisitorFabricBridge(deduplicator, keepParams, keepLocals, keepComments);
		TinyV2Visitor.read(firstLine, reader, visitor);
		return visitor.getMappings();
	}

	final MappedStringDeduplicator depuplicator;
	final boolean keepParams, keepLocals, keepComments;
	final Collection<ClassEntry> classEntries = new ArrayList<>();
	final Collection<MethodEntry> methodEntries = new ArrayList<>();
	final Collection<FieldEntry> fieldEntries = new ArrayList<>();
	final Collection<MethodParameterEntry> methodParameterEntries;
	final Collection<LocalVariableEntry> localVariableEntries;
	final Comments comments = new CommentsImpl();
	private List<String> namespaces;

	private TinyV2VisitorFabricBridge(MappedStringDeduplicator deduplicator, boolean keepParams, boolean keepLocals, boolean keepComments) {
		this.depuplicator = deduplicator;
		this.keepParams = keepParams;
		this.keepLocals = keepLocals;
		this.keepComments = keepComments;
		methodParameterEntries = keepParams ? new ArrayList<>() : Collections.emptyList();
		localVariableEntries = keepLocals ? new ArrayList<>() : Collections.emptyList();
	}

	@Override
	public void visitVersion(int major, int minor) {
	}

	@Override
	public void visitNamespaces(String... namespaces) {
		this.namespaces = Arrays.asList(namespaces);
	}

	@Override
	public void visitProperty(String name) {
	}

	@Override
	public void visitProperty(String name, String value) {
	}

	String[] deduplicate(Category category, String[] strings) {
		String[] out = new String[strings.length];

		for (int i = 0; i < out.length; i++) {
			out[i] = depuplicator.deduplicate(category, strings[i]);
		}

		return out;
	}

	@Override
	public ClassVisitor visitClass(long offset, String[] names) {
		assert names.length > 0;
		assert Arrays.stream(names).filter(Objects::nonNull).noneMatch(String::isEmpty);
		String[] classNames = deduplicate(Category.CLASS_NAME, names);
		classEntries.add(namespace -> classNames[namespaces.indexOf(namespace)]);

		return new ClassVisitor() {
			private List<String> classComments;

			@Override
			public MethodVisitor visitMethod(long offset, String[] names, String descriptor) {
				assert names.length > 0;
				names = deduplicate(Category.NAME, names);
				descriptor = depuplicator.deduplicate(Category.METHOD_DESCRIPTOR, descriptor);

				EntryTriple[] methods = new EntryTriple[names.length];
				for (int i = 0; i < methods.length; i++) {
					methods[i] = new EntryTriple(classNames[i], names[i], descriptor);
				}
				methodEntries.add(namespace -> methods[namespaces.indexOf(namespace)]);

				return keepParams || keepLocals || keepComments ? new MethodVisitor() {
					private List<String> methodComments;

					@Override
					public ParameterVisitor visitParameter(long offset, String[] names, int localVariableIndex) {
						assert names.length > 0;

						if (keepParams) {
							assert Arrays.stream(names).filter(Objects::nonNull).noneMatch(String::isEmpty);
							names = deduplicate(Category.NAME, names);

							MethodParameter[] params = new MethodParameter[names.length];
							for (int i = 0; i < params.length; i++) {
								params[i] = new MethodParameter(methods[i], names[i], localVariableIndex);
							}
							methodParameterEntries.add(namespace -> params[namespaces.indexOf(namespace)]);

							if (keepComments) {
								return new ParameterVisitor() {
									private List<String> paramComments;

									@Override
									public void visitComment(String line) {
										assert keepParams;
										assert keepComments;

										if (paramComments == null) {
											comments.getMethodParameterComments().add(new CommentEntry.Parameter(paramComments = new ArrayList<>(), params[0]));
										}
										paramComments.add(line);
									}
								};
							}
						}

						return null;
					}

					@Override
					public LocalVisitor visitLocalVariable(long offset, String[] names, int localVariableIndex, int localVariableStartOffset, int localVariableTableIndex) {
						assert names.length > 0;

						if (keepParams) {
							assert Arrays.stream(names).filter(Objects::nonNull).noneMatch(String::isEmpty);
							names = deduplicate(Category.NAME, names);

							LocalVariable[] locals = new LocalVariable[names.length];
							for (int i = 0; i < locals.length; i++) {
								locals[i] = new LocalVariable(methods[i], names[i], localVariableIndex, localVariableStartOffset, localVariableTableIndex);
							}
							localVariableEntries.add(namespace -> locals[namespaces.indexOf(namespace)]);

							if (keepComments) {
								return new LocalVisitor() {
									private List<String> localComments;

									@Override
									public void visitComment(String line) {
										assert keepParams;
										assert keepComments;

										if (localComments == null) {
											comments.getLocalVariableComments().add(new CommentEntry.LocalVariableComment(localComments = new ArrayList<>(), locals[0]));
										}
										localComments.add(line);
									}
								};
							}
						}

						return null;
					}

					@Override
					public void visitComment(String line) {
						if (!keepComments) return;

						if (methodComments == null) {
							comments.getMethodComments().add(new CommentEntry.Method(methodComments = new ArrayList<>(), methods[0]));
						}
						methodComments.add(line);
					}
				} : null;
			}

			@Override
			public FieldVisitor visitField(long offset, String[] names, String descriptor) {
				assert names.length > 0;
				names = deduplicate(Category.NAME, names);
				descriptor = depuplicator.deduplicate(Category.FIELD_DESCRIPTOR, descriptor);

				EntryTriple[] fields = new EntryTriple[names.length];
				for (int i = 0; i < fields.length; i++) {
					fields[i] = new EntryTriple(classNames[i], names[i], descriptor);
				}
				fieldEntries.add(namespace -> fields[namespaces.indexOf(namespace)]);

				return keepComments ? new FieldVisitor() {
					private List<String> fieldComments;

					@Override
					public void visitComment(String line) {
						assert keepComments;

						if (fieldComments == null) {
							comments.getFieldComments().add(new CommentEntry.Field(fieldComments = new ArrayList<>(), fields[0]));
						}
						fieldComments.add(line);
					}
				} : null;
			}

			@Override
			public void visitComment(String line) {
				if (!keepComments) return;

				if (classComments == null) {
					comments.getClassComments().add(new CommentEntry.Class(classComments = new ArrayList<>(), classNames[0]));
				}
				classComments.add(line);
			}
		};
	}

	public ExtendedMappings getMappings() {
		((ArrayList<?>) methodEntries).trimToSize();
		((ArrayList<?>) fieldEntries).trimToSize();
		if (keepParams) ((ArrayList<?>) methodParameterEntries).trimToSize();
		if (keepLocals) ((ArrayList<?>) localVariableEntries).trimToSize();
		return new MappingsImpl(classEntries, methodEntries, fieldEntries, methodParameterEntries, localVariableEntries, namespaces, keepComments ? trim(comments) : Comments.empty());
	}

	private static class MappingsImpl implements ExtendedMappings {
		private final Collection<ClassEntry> classEntries;
		private final Collection<MethodEntry> methodEntries;
		private final Collection<FieldEntry> fieldEntries;
		private final Collection<MethodParameterEntry> methodParameterEntries;
		private final Collection<LocalVariableEntry> localVariableEntries;
		private final Collection<String> namespaces;
		private final Comments comments;

		public MappingsImpl(Collection<ClassEntry> classEntries, Collection<MethodEntry> methodEntries, Collection<FieldEntry> fieldEntries, 
				Collection<MethodParameterEntry> methodParameterEntries, Collection<LocalVariableEntry> localVariableEntries, Collection<String> namespaces, Comments comments) {
			this.classEntries = classEntries;
			this.methodEntries = methodEntries;
			this.fieldEntries = fieldEntries;
			this.methodParameterEntries = methodParameterEntries;
			this.localVariableEntries = localVariableEntries;
			this.namespaces = namespaces;
			this.comments = comments;
		}

		@Override
		public Collection<ClassEntry> getClassEntries() {
			return classEntries;
		}

		@Override
		public Collection<MethodEntry> getMethodEntries() {
			return methodEntries;
		}

		@Override
		public Collection<FieldEntry> getFieldEntries() {
			return fieldEntries;
		}

		@Override
		public Collection<MethodParameterEntry> getMethodParameterEntries() {
			return methodParameterEntries;
		}

		@Override
		public Collection<LocalVariableEntry> getLocalVariableEntries() {
			return localVariableEntries;
		}

		@Override
		public Collection<String> getNamespaces() {
			return namespaces;
		}

		@Override
		public Comments getComments() {
			return comments;
		}
	}

	private static Comments trim(Comments comments) {
		List<Class> classComments = trim(comments.getClassComments());
		List<Field> fieldComments = trim(comments.getFieldComments());
		List<Method> methodComments = trim(comments.getMethodComments());
		List<Parameter> methodParameterComments = trim(comments.getMethodParameterComments());
		List<LocalVariableComment> localVariableComments = trim(comments.getLocalVariableComments());

		return classComments.isEmpty() && fieldComments.isEmpty() && methodComments.isEmpty() && methodParameterComments.isEmpty() && localVariableComments.isEmpty() ? 
				Comments.empty() : new CommentsImpl(classComments, fieldComments, methodComments, methodParameterComments, localVariableComments);
	}

	private static <T extends CommentEntry> List<T> trim(Collection<T> comments) {
		List<T> shorterComments = new ArrayList<>(comments);

		for (Iterator<T> it = shorterComments.iterator(); it.hasNext();) {
			T comment = it.next();

			if (comment.getComments().stream().allMatch(line -> {
				int strLen;
		        if (line == null || (strLen = line.length()) == 0) {
		            return true;
		        }

		        for (int i = 0; i < strLen; i++) {
		            if (!Character.isWhitespace(line.charAt(i))) {
		                return false;
		            }
		        }

		        return true;
			})) {
				it.remove();
			}
		}

		if (shorterComments.isEmpty()) {
			return Collections.emptyList();
		} else {
			((ArrayList<?>) shorterComments).trimToSize();
			return shorterComments;
		}
	}
}