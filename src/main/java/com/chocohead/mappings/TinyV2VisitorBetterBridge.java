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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.commons.Remapper;

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

public class TinyV2VisitorBetterBridge implements MappingsVisitor {
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
		TinyV2VisitorBetterBridge visitor = new TinyV2VisitorBetterBridge(deduplicator, keepParams, keepLocals, keepComments);
		TinyV2Visitor.read(firstLine, reader, visitor);
		return visitor.getMappings();
	}

	final MappedStringDeduplicator depuplicator;
	final boolean keepParams, keepLocals, keepComments;
	private final Collection<String[]> classes = new ArrayList<>();
	final Collection<EntryTriple[]> methods = new ArrayList<>();
	final Collection<EntryTriple[]> fields = new ArrayList<>();
	final Collection<MethodParameter[]> params;
	final Collection<LocalVariable[]> locals;
	final Comments comments = new CommentsImpl();
	private String[] namespaces;

	private TinyV2VisitorBetterBridge(MappedStringDeduplicator deduplicator, boolean keepParams, boolean keepLocals, boolean keepComments) {
		this.depuplicator = deduplicator;
		this.keepParams = keepParams;
		this.keepLocals = keepLocals;
		this.keepComments = keepComments;
		params = keepParams ? new ArrayList<>() : Collections.emptyList();
		locals = keepLocals ? new ArrayList<>() : Collections.emptyList();
	}

	@Override
	public void visitVersion(int major, int minor) {
	}

	@Override
	public void visitNamespaces(String... namespaces) {
		this.namespaces = namespaces;
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
		classes.add(classNames);

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
				TinyV2VisitorBetterBridge.this.methods.add(methods);

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
							TinyV2VisitorBetterBridge.this.params.add(params);

							if (keepComments) {
								return new ParameterVisitor() {
									private List<String> paramComments;

									@Override
									public void visitComment(String line) {
										assert keepParams;
										assert keepComments;

										if (paramComments == null) {
											comments.getMethodParameterComments().add(new Parameter(paramComments = new ArrayList<>(), params[0]));
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
							TinyV2VisitorBetterBridge.this.locals.add(locals);

							if (keepComments) {
								return new LocalVisitor() {
									private List<String> localComments;

									@Override
									public void visitComment(String line) {
										assert keepParams;
										assert keepComments;

										if (localComments == null) {
											comments.getLocalVariableComments().add(new LocalVariableComment(localComments = new ArrayList<>(), locals[0]));
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
							comments.getMethodComments().add(new Method(methodComments = new ArrayList<>(), methods[0]));
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
				TinyV2VisitorBetterBridge.this.fields.add(fields);

				return keepComments ? new FieldVisitor() {
					private List<String> fieldComments;

					@Override
					public void visitComment(String line) {
						assert keepComments;

						if (fieldComments == null) {
							comments.getFieldComments().add(new Field(fieldComments = new ArrayList<>(), fields[0]));
						}
						fieldComments.add(line);
					}
				} : null;
			}

			@Override
			public void visitComment(String line) {
				if (!keepComments) return;

				if (classComments == null) {
					comments.getClassComments().add(new Class(classComments = new ArrayList<>(), classNames[0]));
				}
				classComments.add(line);
			}
		};
	}

	public ExtendedMappings getMappings() {
		List<String> namespaces = Arrays.asList(this.namespaces);
		Collection<ClassEntry> classEntries = new ArrayList<>(classes.size());
		Collection<MethodEntry> methodEntries = new ArrayList<>(methods.size());
		Collection<FieldEntry> fieldEntries = new ArrayList<>(fields.size());
		Collection<MethodParameterEntry> methodParameterEntries = keepParams ? new ArrayList<>(params.size()) : Collections.emptyList();
		Collection<LocalVariableEntry> localVariableEntries = keepLocals ? new ArrayList<>(locals.size()) : Collections.emptyList();
		Comments comments = Comments.empty();

		if (this.namespaces.length > 1) {
			Remapper[] remappers = new Remapper[this.namespaces.length];

			@SuppressWarnings("unchecked") //We'll be careful Java, no accidents
			Map<String, String>[] classPools = new HashMap[this.namespaces.length];
			for (int i = 1; i < classPools.length; i++) classPools[i] = new HashMap<>();

			for (String[] names : classes) {
				assert names.length == this.namespaces.length;

				classEntries.add(namespace -> names[namespaces.indexOf(namespace)]);
				for (int i = 1; i < names.length; i++) {
					classPools[i].put(names[0], names[i]);
				}
			}

			for (int i = 1; i < classPools.length; i++) {
				Map<String, String> classPool = classPools[i]; //Unpack here so the anonymous class only needs capture the map

				remappers[i] = new Remapper() {
					@Override
					public String map(String type) {
						return classPool.getOrDefault(type, type);
					}
				};
			}

			Map<EntryTriple, EntryTriple> remappedMembers = keepParams || keepLocals || keepComments ? new IdentityHashMap<>() : null;

			for (EntryTriple[] names : methods) {
				assert names.length == this.namespaces.length;
				EntryTriple[] newNames = new EntryTriple[names.length];

				newNames[0] = names[0];
				for (int i = 1; i < names.length; i++) {
					EntryTriple triple = names[i];
					newNames[i] = new EntryTriple(triple.getOwner(), triple.getName(), remappers[i].mapMethodDesc(triple.getDesc()));
					if (remappedMembers != null) {
						remappedMembers.put(triple, newNames[i]);
					}
				}

				methodEntries.add(namespace -> newNames[namespaces.indexOf(namespace)]);
			}

			for (EntryTriple[] names : fields) {
				assert names.length == this.namespaces.length;
				EntryTriple[] newNames = new EntryTriple[names.length];

				newNames[0] = names[0];
				for (int i = 1; i < names.length; i++) {
					EntryTriple triple = names[i];
					newNames[i] = new EntryTriple(triple.getOwner(), triple.getName(), remappers[i].mapDesc(triple.getDesc()));
					if (remappedMembers != null) {
						remappedMembers.put(triple, newNames[i]);
					}
				}

				fieldEntries.add(namespace -> newNames[namespaces.indexOf(namespace)]);
			}

			Map<MethodParameter, MethodParameter> remappedParams = keepComments ? new IdentityHashMap<>() : null;
			if (keepParams) {
				for (MethodParameter[] names : params) {
					assert names.length == this.namespaces.length;
					MethodParameter[] newNames = new MethodParameter[names.length];

					newNames[0] = names[0];
					for (int i = 1; i < names.length; i++) {
						MethodParameter param = names[i];
						newNames[i] = new MethodParameter(remappedMembers.get(param.getMethod()), param.getName(), param.getLocalVariableIndex());
						if (remappedParams != null) {
							remappedParams.put(param, newNames[i]);
						}
					}

					methodParameterEntries.add(namespace -> newNames[namespaces.indexOf(namespace)]);
				}
			}

			Map<LocalVariable, LocalVariable> remappedLocals = keepComments ? new IdentityHashMap<>() : null;
			if (keepLocals) {
				for (LocalVariable[] names : locals) {
					assert names.length == this.namespaces.length;
					LocalVariable[] newNames = new LocalVariable[names.length];

					newNames[0] = names[0];
					for (int i = 1; i < names.length; i++) {
						LocalVariable local = names[i];
						newNames[i] = new LocalVariable(remappedMembers.get(local.getMethod()), local.getName(), local.getLocalVariableIndex(), local.getLocalVariableStartOffset(), local.getLocalVariableStartOffset());
						if (remappedLocals != null) {
							remappedLocals.put(local, newNames[i]);
						}
					}

					localVariableEntries.add(namespace -> newNames[namespaces.indexOf(namespace)]);
				}
			}

			if (keepComments) {
				List<Class> classComments = (List<Class>) this.comments.getClassComments();

				List<Method> methodComments = new ArrayList<>(this.comments.getMethodComments().size());
				for (Method method : this.comments.getMethodComments()) {
					methodComments.add(new Method(method.getComments(), remappedMembers.get(method.getMethod())));
				}

				List<Field> fieldComments = new ArrayList<>(this.comments.getFieldComments().size());
				for (Field field : this.comments.getFieldComments()) {
					fieldComments.add(new Field(field.getComments(), remappedMembers.get(field.getField())));
				}

				List<Parameter> methodParameterComments;
				if (keepParams) {
					methodParameterComments = new ArrayList<>(this.comments.getMethodParameterComments().size());
					for (Parameter param : this.comments.getMethodParameterComments()) {
						methodParameterComments.add(new Parameter(param.getComments(), remappedParams.get(param.getParameter())));
					}	
				} else {
					methodParameterComments = Collections.emptyList();
				}

				List<LocalVariableComment> localVariableComments;
				if (keepLocals) {
					localVariableComments = new ArrayList<>(this.comments.getLocalVariableComments().size());
					for (LocalVariableComment param : this.comments.getLocalVariableComments()) {
						localVariableComments.add(new LocalVariableComment(param.getComments(), remappedLocals.get(param.getLocalVariable())));
					}	
				} else {
					localVariableComments = Collections.emptyList();
				}

				comments = new CommentsImpl(classComments, fieldComments, methodComments, methodParameterComments, localVariableComments);
			}
		} else {
			for (String[] names : classes) {
				assert names.length == this.namespaces.length;

				classEntries.add(namespace -> names[namespaces.indexOf(namespace)]);
			}

			for (EntryTriple[] names : methods) {
				assert names.length == this.namespaces.length;

				methodEntries.add(namespace -> names[namespaces.indexOf(namespace)]);
			}

			for (EntryTriple[] names : fields) {
				assert names.length == this.namespaces.length;

				fieldEntries.add(namespace -> names[namespaces.indexOf(namespace)]);
			}

			for (MethodParameter[] names : params) {
				assert names.length == this.namespaces.length;

				methodParameterEntries.add(namespace -> names[namespaces.indexOf(namespace)]);
			}

			for (LocalVariable[] names : locals) {
				assert names.length == this.namespaces.length;

				localVariableEntries.add(namespace -> names[namespaces.indexOf(namespace)]);
			}

			comments = this.comments;
		}

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