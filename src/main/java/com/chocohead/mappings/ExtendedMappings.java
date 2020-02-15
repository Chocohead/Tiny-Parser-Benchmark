package com.chocohead.mappings;

import java.util.Collection;

import com.chocohead.mappings.model.Comments;
import com.chocohead.mappings.model.LocalVariableEntry;
import com.chocohead.mappings.model.MethodParameterEntry;

public interface ExtendedMappings extends Mappings {
	Collection<? extends MethodParameterEntry> getMethodParameterEntries();
	Collection<? extends LocalVariableEntry> getLocalVariableEntries();
	Comments getComments();

	default boolean isExtended() {
		return !getMethodParameterEntries().isEmpty() || !getLocalVariableEntries().isEmpty() || !getComments().isEmpty();
	}

	static ExtendedMappings createEmpty() {
		return wrap(DummyMappings.INSTANCE);
	}

	static ExtendedMappings wrap(Mappings normal) {
		return new ExtendedWrapper(normal);
	}
}