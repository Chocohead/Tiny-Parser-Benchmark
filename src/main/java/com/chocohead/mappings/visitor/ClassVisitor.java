/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.chocohead.mappings.visitor;

public interface ClassVisitor {
	MethodVisitor visitMethod(long offset, String[] names, String descriptor);

	FieldVisitor visitField(long offset, String[] names, String descriptor);

	void visitComment(String line);
}