/*
 * Copyright 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chocohead.mappings.model;

import java.util.List;

import com.chocohead.mappings.EntryTriple;

public abstract class CommentEntry {
	public static class Class extends CommentEntry {
		private final String className;

		public Class(List<String> comments, String className) {
			super(comments);
			this.className = className;
		}

		public String getClassName() {
			return className;
		}		
	}

	public static class Method extends CommentEntry {
		private final EntryTriple method;

		public Method(List<String> comments, EntryTriple method) {
			super(comments);
			this.method = method;
		}

		public EntryTriple getMethod() {
			return method;
		}
	}

	public static class Field extends CommentEntry {
		private final EntryTriple field;

		public Field(List<String> comments, EntryTriple field) {
			super(comments);
			this.field = field;
		}

		public EntryTriple getField() {
			return field;
		}
	}

	public static class Parameter extends CommentEntry {
		private final MethodParameter parameter;

		public Parameter(List<String> comments, MethodParameter parameter) {
			super(comments);
			this.parameter = parameter;
		}

		public MethodParameter getParameter() {
			return parameter;
		}
	}

	public static class LocalVariableComment extends CommentEntry {
		private final LocalVariable localVariable;

		public LocalVariableComment(List<String> comments, LocalVariable localVariable) {
			super(comments);
			this.localVariable = localVariable;
		}

		public LocalVariable getLocalVariable() {
			return localVariable;
		}
	}

	private final List<String> comments;

	private CommentEntry(List<String> comments) {
		this.comments = comments;
	}

	public List<String> getComments() {
		return comments;
	}
}