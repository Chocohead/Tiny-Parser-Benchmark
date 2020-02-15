/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.chocohead.mappings.visitor;

public interface MappingsVisitor {
	void visitVersion(int major, int minor);

	void visitNamespaces(String... namespaces);

	void visitProperty(String name);

	void visitProperty(String name, String value);

	ClassVisitor visitClass(long offset, String[] names);

	/**
	 * Finish visiting the mapping file
	 * 
	 * <p>Nothing else will be called after this.
	 */
	default void finish() {
	}
}