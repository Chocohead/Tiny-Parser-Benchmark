/*
 * Copyright 2019, 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.chocohead.mappings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

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

class TinyV2VisitorBridge implements MappingsVisitor {
	static Mappings read(String firstLine, OffsetReader reader, MappedStringDeduplicator deduplicator) throws IOException {
		return read(firstLine, reader, deduplicator, false, false, false);
	}

	static ExtendedMappings fullyRead(String firstLine, OffsetReader reader, MappedStringDeduplicator deduplicator) throws IOException {
		return read(firstLine, reader, deduplicator, true, true, true);
	}

	private static ExtendedMappings read(String firstLine, OffsetReader reader, MappedStringDeduplicator deduplicator, boolean keepParams, boolean keepLocals, boolean keepComments) throws IOException {
		TinyV2VisitorBridge visitor = new TinyV2VisitorBridge(deduplicator, keepParams, keepLocals, keepComments);
		TinyV2Visitor.read(firstLine, reader, visitor);
		return visitor.getMappings();
	}

	private enum TinyState {
		CLASS, FIELD, METHOD, PARAMETER, LOCAL_VARIABLE;
	}

	private class ClassBits implements ClassVisitor, MethodVisitor, ParameterVisitor, LocalVisitor, FieldVisitor {
		final String[] names;
		final Collection<BiFunction<UnaryOperator<String>[], ToIntFunction<String>, FieldEntry>> fields = new ArrayList<>();
		final Collection<BiFunction<UnaryOperator<String>[], ToIntFunction<String>, MethodEntry>> methods = new ArrayList<>();
		final Map<String, Collection<BiFunction<EntryTriple[], ToIntFunction<String>, MethodParameterEntry>>> parameters = new HashMap<>();
		final Map<String, Collection<BiFunction<EntryTriple[], ToIntFunction<String>, LocalVariableEntry>>> locals = new HashMap<>();

		private final String currentClassName;
		private EntryTriple currentMemberName;
		private MethodParameter currentParameterName;
		private LocalVariable currentLocalVariableName;
		private List<String> currentComments;
		private TinyState currentCommentType;

		private String[] deduplicate(Category category, String[] strings) {
			String[] out = new String[strings.length];

			for (int i = 0; i < out.length; i++) {
				out[i] = depuplicator.deduplicate(category, strings[i]);
			}

			return out;
		}

		public ClassBits(String... names) {
			assert Arrays.stream(names).filter(Objects::nonNull).noneMatch(String::isEmpty);
			this.names = deduplicate(Category.CLASS_NAME, names);

			currentClassName = this.names[0];
			assert currentClassName != null && !currentClassName.isEmpty();
			setNewCommentType(TinyState.CLASS);
		}

		
		private void setNewCommentType(TinyState type) {
			currentCommentType = type;
			currentComments = null;
		}

		private void updateCurrentMember(String name, String descriptor) {
			assert name != null && !name.isEmpty();
			currentMemberName = new EntryTriple(currentClassName, name, descriptor);
		}

		public Function<ToIntFunction<String>, ClassEntry> classFactory() {
			return namespaceIndex -> namespace -> names[namespaceIndex.applyAsInt(namespace)];
		}

		private <T> BiFunction<UnaryOperator<String>[], ToIntFunction<String>, T> memberFactory(BiFunction<EntryTriple[], ToIntFunction<String>, T> memberEntryFactory, String descriptor, String... names) {
			assert !descriptor.isEmpty();
			assert this.names.length == names.length;

			return (remapper, namespaceIndex) -> {
				assert remapper.length == names.length;
				EntryTriple[] members = new EntryTriple[names.length];

				for (int i = 0; i < names.length; i++) {
					if (names[i] == null) continue;

					assert !names[i].isEmpty();
					members[i] = new EntryTriple(this.names[i], names[i], remapper[i].apply(descriptor));
				}

				return memberEntryFactory.apply(members, namespaceIndex);
			};
		}

		@Override
		public FieldVisitor visitField(long offset, String[] names, String descriptor) {
			assert names.length > 0;
			names = deduplicate(Category.NAME, names);
			descriptor = depuplicator.deduplicate(Category.FIELD_DESCRIPTOR, descriptor);
			updateCurrentMember(names[0], descriptor);

			fields.add(memberFactory((fields, namespaceIndex) -> namespace -> fields[namespaceIndex.applyAsInt(namespace)], descriptor, names));

			setNewCommentType(TinyState.FIELD);
			return keepComments ? this : null;
		}

		@Override
		public MethodVisitor visitMethod(long offset, String[] names, String descriptor) {
			assert names.length > 0;
			names = deduplicate(Category.NAME, names);
			descriptor = depuplicator.deduplicate(Category.METHOD_DESCRIPTOR, descriptor);
			updateCurrentMember(names[0], descriptor);

			methods.add(memberFactory((methods, namespaceIndex) -> namespace -> methods[namespaceIndex.applyAsInt(namespace)], descriptor, names));

			setNewCommentType(TinyState.METHOD);
			return keepParams || keepLocals || keepComments ? this : null;
		}

		@Override
		public ParameterVisitor visitParameter(long offset, String[] names, int localVariableIndex) {
			assert names.length > 0;
			
			if (keepParams) {
				assert Arrays.stream(names).filter(Objects::nonNull).noneMatch(String::isEmpty);
				names = deduplicate(Category.NAME, names);
				currentParameterName = new MethodParameter(currentMemberName, names[0], localVariableIndex);

				EntryTriple method = currentParameterName.getMethod();
				assert this.names[0].equals(method.getOwner());
				addParameter(method.getName() + method.getDesc(), localVariableIndex, names);
			}

			setNewCommentType(TinyState.PARAMETER);
			return keepParams && keepComments ? this : null;
		}

		private void addParameter(String method, int localVariableIndex, String... names) {
			assert this.names.length == names.length;

			parameters.computeIfAbsent(method, k -> new ArrayList<>()).add((methods, namespaceIndex) -> {
				assert methods.length == names.length;
				MethodParameter[] parameters = new MethodParameter[names.length];

				for (int i = 0; i < names.length; i++) {
					if (names[i] == null) continue;

					assert !names[i].isEmpty();
					parameters[i] = new MethodParameter(methods[i], names[i], localVariableIndex);
				}

				return namespace -> parameters[namespaceIndex.applyAsInt(namespace)];
			});
		}

		@Override
		public LocalVisitor visitLocalVariable(long offset, String[] names, int lvIndex, int startOffset, int lvtIndex) {
			assert names.length > 0;

			if (keepLocals) {
				assert Arrays.stream(names).filter(Objects::nonNull).noneMatch(String::isEmpty);
				names = deduplicate(Category.NAME, names);
				currentLocalVariableName = new LocalVariable(currentMemberName, names[0], lvIndex, startOffset, lvtIndex);

				EntryTriple method = currentLocalVariableName.getMethod();
				assert this.names[0].equals(method.getOwner());
				addLocal(method.getName() + method.getDesc(), lvIndex, startOffset, lvtIndex, names);
			}

			setNewCommentType(TinyState.LOCAL_VARIABLE);
			return keepLocals && keepComments ? this : null;
		}

		private void addLocal(String method, int localVariableIndex, int indexStartOffset, int lvtIndex, String... names) {
			assert this.names.length == names.length;

			locals.computeIfAbsent(method, k -> new ArrayList<>()).add((methods, namespaceIndex) -> {
				assert methods.length == names.length;
				LocalVariable[] locals = new LocalVariable[names.length];

				for (int i = 0; i < names.length; i++) {
					if (names[i] == null) continue;

					assert !names[i].isEmpty();
					locals[i] = new LocalVariable(methods[i], names[i], localVariableIndex, indexStartOffset, lvtIndex);
				}

				return namespace -> locals[namespaceIndex.applyAsInt(namespace)];
			});
		}

		@Override
		public void visitComment(String line) {
			if (!keepComments) return;

			if (currentComments == null) {
				switch (currentCommentType) {
				case CLASS:
					comments.getClassComments().add(new CommentEntry.Class(currentComments = new ArrayList<>(), currentClassName));
					break;
				case FIELD:
					comments.getFieldComments().add(new CommentEntry.Field(currentComments = new ArrayList<>(), currentMemberName));
					break;
				case METHOD:
					comments.getMethodComments().add(new CommentEntry.Method(currentComments = new ArrayList<>(), currentMemberName));
					break;
				case PARAMETER:
					assert keepParams;
					comments.getMethodParameterComments().add(new CommentEntry.Parameter(currentComments = new ArrayList<>(), currentParameterName));
					break;
				case LOCAL_VARIABLE:
					assert keepLocals;
					comments.getLocalVariableComments().add(new CommentEntry.LocalVariableComment(currentComments = new ArrayList<>(), currentLocalVariableName));
					break;
				default:
					throw new RuntimeException("Unexpected comment without parent: " + line);
				}
			}

			currentComments.add(line);
		}
	}

	final MappedStringDeduplicator depuplicator;
	final boolean keepParams, keepLocals, keepComments;
	private final Collection<ClassBits> classes = new ArrayList<>();
	final Comments comments = new CommentsImpl();
	private String[] namespaces;

	private TinyV2VisitorBridge(MappedStringDeduplicator deduplicator, boolean keepParams, boolean keepLocals, boolean keepComments) {
		this.depuplicator = deduplicator;
		this.keepParams = keepParams;
		this.keepLocals = keepLocals;
		this.keepComments = keepComments;
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

	@Override
	public ClassVisitor visitClass(long offset, String[] names) {
		assert names.length > 0;

		ClassBits currentClass = new ClassBits(names);
		classes.add(currentClass);
		return currentClass;
	}

	public ExtendedMappings getMappings() {
		@SuppressWarnings("unchecked") //We'll be careful Java
		UnaryOperator<String>[] remappers = new UnaryOperator[namespaces.length];
		remappers[0] = UnaryOperator.identity();

		if (remappers.length > 1) {
			@SuppressWarnings("unchecked") //Super careful, no accidents
			Map<String, String>[] classPools = new HashMap[namespaces.length - 1];
			for (int i = 0; i < classPools.length; i++) classPools[i] = new HashMap<>();

			for (ClassBits clazz : classes) {
				assert clazz.names.length == namespaces.length;

				for (int i = 1; i < clazz.names.length; i++) {
					classPools[i - 1].put(clazz.names[0], clazz.names[i]);
				}
			}

			for (int i = 0; i < classPools.length; i++) {
				Map<String, String> classPool = classPools[i]; //Unpack here so the anonymous class only needs capture the map

				remappers[i + 1] = new Remapper() {
					@Override
					public String map(String type) {
						return classPool.getOrDefault(type, type);
					}
				}::mapDesc;
			}
		}

		Collection<ClassEntry> classEntries = new ArrayList<>(classes.size());
		Collection<MethodEntry> methodEntries = new ArrayList<>();
		Collection<FieldEntry> fieldEntries = new ArrayList<>();
		Collection<MethodParameterEntry> methodParameterEntries = keepParams ? new ArrayList<>() : Collections.emptyList();
		Collection<LocalVariableEntry> localVariableEntries = keepLocals ? new ArrayList<>() : Collections.emptyList();

		ToIntFunction<String> namespaceIndex = Arrays.asList(namespaces)::indexOf;
		String rootNamespace = namespaces[0];

		for (ClassBits clazz : classes) {
			classEntries.add(clazz.classFactory().apply(namespaceIndex));

			for (BiFunction<UnaryOperator<String>[], ToIntFunction<String>, FieldEntry> factory : clazz.fields) {
				fieldEntries.add(factory.apply(remappers, namespaceIndex));
			}

			Collection<MethodEntry> methods = new ArrayList<>();
			for (BiFunction<UnaryOperator<String>[], ToIntFunction<String>, MethodEntry> factory : clazz.methods) {
				methods.add(factory.apply(remappers, namespaceIndex));
			}
			methodEntries.addAll(methods);

			if (keepParams || keepLocals) {
				for (MethodEntry methodEntry : methods) {
					EntryTriple method = methodEntry.get(rootNamespace);
					String methodName = method.getName() + method.getDesc();

					EntryTriple[] triples = null; //Lazily fill when needed

					if (keepParams) {
						Collection<BiFunction<EntryTriple[], ToIntFunction<String>, MethodParameterEntry>> paramFactories = clazz.parameters.get(methodName);
						if (paramFactories != null) {
							triples = new EntryTriple[namespaces.length];

							triples[0] = method;
							for (int i = 1; i < namespaces.length; i++) {
								triples[i] = methodEntry.get(namespaces[i]);
							}

							for (BiFunction<EntryTriple[], ToIntFunction<String>, MethodParameterEntry> factory : paramFactories) {
								methodParameterEntries.add(factory.apply(triples, namespaceIndex));
							}
						}
					}

					if (keepLocals) {
						Collection<BiFunction<EntryTriple[], ToIntFunction<String>, LocalVariableEntry>> localFactories = clazz.locals.get(methodName);
						if (localFactories != null) {
							if (triples == null) triples = Arrays.stream(namespaces).map(methodEntry::get).toArray(EntryTriple[]::new);

							for (BiFunction<EntryTriple[], ToIntFunction<String>, LocalVariableEntry> factory : localFactories) {
								localVariableEntries.add(factory.apply(triples, namespaceIndex));
							}
						}
					}
				}
			}
		}

		((ArrayList<?>) methodEntries).trimToSize();
		((ArrayList<?>) fieldEntries).trimToSize();
		if (keepParams) ((ArrayList<?>) methodParameterEntries).trimToSize();
		if (keepLocals) ((ArrayList<?>) localVariableEntries).trimToSize();
		return new MappingsImpl(classEntries, methodEntries, fieldEntries, methodParameterEntries, localVariableEntries, Arrays.asList(namespaces), keepComments ? trim(comments) : Comments.empty());
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