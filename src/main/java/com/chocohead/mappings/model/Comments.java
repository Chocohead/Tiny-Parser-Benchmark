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

package com.chocohead.mappings.model;

import java.util.Collection;
import java.util.Collections;

import com.chocohead.mappings.model.CommentEntry.Class;
import com.chocohead.mappings.model.CommentEntry.Field;
import com.chocohead.mappings.model.CommentEntry.Method;
import com.chocohead.mappings.model.CommentEntry.Parameter;
import com.chocohead.mappings.model.CommentEntry.LocalVariableComment;

public interface Comments {
	Collection<Class> getClassComments();
	Collection<Field> getFieldComments();
	Collection<Method> getMethodComments();
	Collection<Parameter> getMethodParameterComments();
	Collection<LocalVariableComment> getLocalVariableComments();

	default boolean isEmpty() {
		return getClassComments().isEmpty() && getFieldComments().isEmpty() && getMethodComments().isEmpty() && getMethodParameterComments().isEmpty() && getLocalVariableComments().isEmpty();
	}

	static Comments empty() {
		return new CommentsImpl(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}
}