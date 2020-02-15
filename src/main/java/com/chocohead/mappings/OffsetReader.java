/*
 * Copyright 2019, 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.chocohead.mappings;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

/**
 * Specialisation of {@link BufferedReader} designed to work out the number of
 * characters a given line is offset from the start of the file by.
 *
 * @see BufferedReader
 *
 * @author Chocohead
 */
final class OffsetReader implements Closeable {
	private final Reader in;
	private final char buffer[];
	private long lineStart, nextLine = -1;
	private int limit, position;
	private boolean skipLF;

	/**
	 * Creates a buffering character-input stream that uses a default-sized buffer.
	 *
	 * @param reader A {@link Reader} to be read
	 */
	public OffsetReader(Reader reader) {
		this(reader, 8192);
	}

	/**
	 * Creates a buffering character-input stream that uses a buffer of the given size.
	 *
	 * @param reader A {@link Reader} to be read
	 * @param bufferSize The desired size of the input buffer
	 */
	public OffsetReader(Reader reader, int bufferSize) {
		in = reader;
		buffer = new char[bufferSize];
	}

	private void fill() throws IOException {
		int read;
		do {
			read = in.read(buffer);
		} while (read == 0);

		if (read > 0) {
			limit = read;
			position = 0;
		}
	}

	/**
	 * Reads a line of text. A line is considered to be terminated by any one of a
	 * line feed ('\n'), a carriage return ('\r'), or a carriage return followed
	 * immediately by a linefeed.
	 *
	 * @return A String containing the contents of the line, not including any
	 *         line-termination characters, or null if the end of the stream has
	 *         been reached
	 *
	 * @exception IOException If an I/O error occurs whilst reading
	 *
	 * @see BufferedReader#readLine()
	 */
	public String readLine() throws IOException {
		StringBuffer line = null;

		while (true) {
			if (position >= limit) fill();

			if (position >= limit) {//Nothing left in the reader
				nextLine = position;
				return line != null && line.length() > 0 ? line.toString() : null;
			}

			if (skipLF) {
				if (buffer[position] == '\n') {
					position++;
					nextLine++;
				}

				skipLF = false;
			}

			int startPos = position;
			if (line == null) lineStart = ++nextLine;

			for (; position < limit; position++, nextLine++) {
				char lastChar = buffer[position];

				if (lastChar == '\n' || lastChar == '\r') {
					String out;
					if (line == null) {
						out = new String(buffer, startPos, position++ - startPos);
					} else {
						out = line.append(buffer, startPos, position++ - startPos).toString();
					}

					if (lastChar == '\r') {
						skipLF = true;
					}

					return out;
				}
			}

			if (line == null) {
				line = new StringBuffer(100);
			}

			line.append(buffer, startPos, position - startPos);
		}
	}

	/**
	 * The number of characters the last line returned by {@link #readLine()} is
	 * past from the start of the input reader. Returns 0 if {@link #readLine()} has
	 * not been called previously before this.
	 *
	 * <p>
	 * The returned value is such that {@link Reader#skip(long)} would move the
	 * reader's position to the start of the last returned line.
	 *
	 * @return The number of characters the last line returned by
	 *         {@link #readLine()} is past from the start of the file
	 *
	 * @see #lineEnd()
	 * @see BufferedReader#skip(long)
	 */
	public long lineStart() {
		return lineStart;
	}

	/**
	 * The number of characters the end of the last line returned by
	 * {@link #readLine()} is past from the start of the input reader.
	 *
	 * <p>
	 * The returned value is such that {@link Reader#skip(long)} would move the
	 * reader's position to the next character after the last returned line.
	 *
	 * @return The number of characters the end of the last line returned by
	 *         {@link #readLine()} is past from the start of the file
	 *
	 * @throws IllegalStateException If {@link #readLine()} has never been 
	 *                               called previously before this
	 *
	 * @see #lineStart()
	 * @see BufferedReader#skip(long)
	 */
	public long lineEnd() {
		if (nextLine < 0) throw new IllegalStateException("Must call readLine() at least once first");
		return nextLine;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}