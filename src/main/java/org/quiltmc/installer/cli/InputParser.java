/*
 * Copyright 2020 FabricMC
 * Copyright 2021 QuiltMC
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
 *
 * This file has been modified by the Quilt project (repackage, minor changes).
 */

package org.quiltmc.installer.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Original credit goes to sfPlayer1 from the Fabric Discord "thimble" prototype bot
final class InputParser {
	private static final int TOKEN_STRIDE = 2;

	private CharSequence input;
	private int[] tokens = new int[TOKEN_STRIDE * 20]; // [start,end[ position pairs
	private int tokenCount = 0; // total token array entries (2 per actual token)
	private final Map<String, String> floatingArgs = new HashMap<>();
	private final StringBuilder buffer = new StringBuilder();
	private final List<String> capturedArgs = new ArrayList<>();
	private final List<String> allowedFloatingArgs = new ArrayList<>();
	private int[] queue;
	int queueSize = 0;

	public boolean parse(CharSequence input, Node node, Map<String, String> out) {
		return this.parse(input, 0, input.length(), node, out);
	}

	/**
	 * Parse and validate a command parameter string into a map.
	 *
	 * <p>The input will split into tokens through whitespace unless escaped or enclosed in single or double quotes.
	 * Supported escape sequences are \r, \n, \t and \b with their usual Java meaning.
	 * Flag keys (--x) may not use quotes and don't tolerate whitespace between the first - and the end of their value.
	 *
	 * <p>Output map keying rules based on usage string element:
	 * <ul>
	 * <li> variable ({@literal <x>}): variable name (x)
	 * <li> floating arg (--x): flag key (x)
	 * <li> plain arg (x): unnamed_y where y = yth position dependent token, zero based
	 * </ul>
	 *
	 * @param input command parameter string
	 * @param inputStart start index in the command parameter string
	 * @param inputEnd end index in command parameter string (exclusive)
	 * @param node command usage tree's root Node as obtained from UsageParser.parse
	 * @param out map capturing parsed parameters as described above
	 * @return true whether the input was meeting the usage requirements and parsed successfully (wip)
	 * @throws IllegalArgumentException if the input has incorrect syntax (wip)
	 */
	public boolean parse(CharSequence input, int inputStart, int inputEnd, Node node, Map<String, String> out) {
		this.input = input;
		this.tokenCount = 0;
		this.floatingArgs.clear();

		for (int i = inputStart; i < inputEnd; i++) {
			char c = input.charAt(i);

			if (c == '"' || c == '\'') {
				int end = findBlockEndDelim(input, i + 1, inputEnd, c);
				if (end < 0) throw new IllegalArgumentException("unterminated " + c);

				this.addToken(i + 1, end);
				i = end;
			} else if (c == '-' && i + 1 < inputEnd && input.charAt(i + 1) == '-') {
				int start = i + 2;
				int end = start;

				while (end < inputEnd && !Character.isWhitespace(c = input.charAt(end)) && c != '=') {
					end++;
				}

				if (end == start) throw new IllegalArgumentException("-- not followed by key");

				String key = input.subSequence(start, end).toString();
				String value;

				if (c == '=') {
					start = end + 1;

					if (end < inputEnd && ((c = input.charAt(end + 1)) == '"' || c == '\'')) {
						start++;
						end = findBlockEndDelim(input, start, inputEnd, c);
						if (end < 0) throw new IllegalArgumentException("unterminated " + c);
					} else {
						end = findBlockEndWhitespace(input, start, inputEnd);
					}

					value = this.getValue(start, end);
				} else {
					value = null;
				}

				this.floatingArgs.put(key, value);
				i = end;
			} else if (!Character.isWhitespace(c)) {
				int end = findBlockEndWhitespace(input, i + 1, inputEnd);
				this.addToken(i, end);
				i = end;
			}
		}

		this.capturedArgs.clear();
		this.allowedFloatingArgs.clear();
		this.queueSize = 0;

		boolean ret = this.processNode(node, 0, true) >= 0;

		if (ret) {
			for (int i = 0; i < this.capturedArgs.size(); i += 2) {
				String key = this.capturedArgs.get(i);
				if (key == null) key = String.format("unnamed_%d", i >>> 1);
				String value = this.capturedArgs.get(i + 1);

				out.put(key, value);
			}

			out.putAll(this.floatingArgs);
		}

		this.input = null;

		return ret;
	}

	private static int findBlockEndWhitespace(CharSequence s, int start, int end) {
		char c;

		while (start < end && !Character.isWhitespace(c = s.charAt(start))) {
			start++;
			if (c == '\\') start++;
		}

		return Math.min(start, end); // start could be beyond end due to trailing \
	}

	private static int findBlockEndDelim(CharSequence s, int start, int end, char endChar) {
		char c;

		while (start < end && (c = s.charAt(start)) != endChar) {
			start++;
			if (c == '\\') start++;
		}

		return start < end ? start : -1;
	}

	private void addToken(int start, int end) {
		if (this.tokenCount == this.tokens.length) {
			this.tokens = Arrays.copyOf(this.tokens, this.tokens.length * TOKEN_STRIDE);
		}

		this.tokens[this.tokenCount++] = start;
		this.tokens[this.tokenCount++] = end;
	}

	private int processNode(Node node, int token, boolean last) {
		// TODO: repeat
		boolean matched;

		if (node instanceof Node.Floating) {
			Node.Floating faNode = (Node.Floating) node;

			if (this.floatingArgs.containsKey(faNode.name)) {
				String value = this.floatingArgs.get(faNode.name);

				if (value == null) {
					if (faNode.value != null && !faNode.value.optional()) {
						return -1; // missing value
					}
				} else if (faNode.value == null) {
					return -1; // excess value
				} else {
					// TODO: check if value is compliant with whatever faNode.value requires
				}

				this.allowedFloatingArgs.add(faNode.name);
				matched = true;
			} else {
				matched = false;
			}
		} else if (node instanceof Node.ListNode) {
			Node.ListNode list = (Node.ListNode) node;
			int initialCapturedArgsSize = this.capturedArgs.size();
			int initialAllowedFloatingArgsSize = this.allowedFloatingArgs.size();
			long ignore = 0; // FIXME: this works only up to size 64
			int queueStart = this.queueSize;
			matched = true;

			for (int i = 0, max = list.size(); i < max; i++) {
				if ((ignore & 1L << i) != 0) continue;

				Node subNode = list.get(i);

				if (subNode.optional() && subNode.isPositionDependent()) { // we're trying with the node present, but this may fail -> queue without for later
					long newIgnore = ignore | 1L << i;
					this.ensureQueueSpace(6);
					this.queue[this.queueSize++] = i;
					this.queue[this.queueSize++] = token;
					this.queue[this.queueSize++] = this.capturedArgs.size();
					this.queue[this.queueSize++] = this.allowedFloatingArgs.size();
					this.queue[this.queueSize++] = (int) (newIgnore >>> 32);
					this.queue[this.queueSize++] = (int) newIgnore;
				}

				int newToken = this.processNode(subNode, token, last && i + 1 == max);

				if (newToken < 0) {
					if (this.queueSize > queueStart) {
						int idx = this.queueSize - 6;
						i = this.queue[idx] - 1;
						token = this.queue[idx + 1];
						trimList(this.capturedArgs, this.queue[idx + 2]);
						trimList(this.allowedFloatingArgs, this.queue[idx + 3]);
						ignore = (long) this.queue[idx + 4] << 32 | this.queue[idx + 5] & 0xffffffffL;
						this.queueSize = idx;
					} else {
						// undo the whole list node, for when it gets skipped entirely as optional
						trimList(this.capturedArgs, initialCapturedArgsSize);
						trimList(this.allowedFloatingArgs, initialAllowedFloatingArgsSize);
						matched = false;
						break;
					}
				} else {
					token = newToken;
				}
			}

			if (this.queueSize > queueStart) {
				if (matched) {
					// TODO: queue globally to restart from outside this list
				}

				this.queueSize = queueStart;
			}
		} else if (node instanceof Node.Options) {
			Node.Options options = (Node.Options) node;
			int initialCapturedArgsSize = this.capturedArgs.size();
			int initialAllowedFloatingArgsSize = this.allowedFloatingArgs.size();

			for (Node option : options) {
				int newToken = this.processNode(option, token, last);

				if (newToken >= 0) {
					return newToken; // FIXME: later options need to be preserved for backtracking to them in case the current one fails later (for niche cases..)
				} else {
					trimList(this.capturedArgs, initialCapturedArgsSize);
					trimList(this.allowedFloatingArgs, initialAllowedFloatingArgsSize);
				}
			}

			matched = false;
		} else if (token >= this.tokenCount) {
			matched = false;
		} else if (node instanceof Node.Literal) {
			Node.Literal literal = (Node.Literal) node;

			if (literal.name.equals(this.getValue(token))) {
				this.capturedArgs.add(null);
				this.capturedArgs.add(literal.name);

				token += TOKEN_STRIDE;
				matched = true;
			} else {
				matched = false;
			}
		} else if (node instanceof Node.ValueParameter) {
			Node.ValueParameter valueParameter = (Node.ValueParameter) node;

			this.capturedArgs.add(valueParameter.name);
			this.capturedArgs.add(this.getValue(token));

			token += TOKEN_STRIDE;
			matched = true;
		} else {
			throw new IllegalStateException();
		}

		if (!matched && !node.optional() || last && token < this.tokenCount) {
			return -1;
		}

		if (last && !this.floatingArgs.isEmpty()) { // check if all floating args have been provided on the path taken
			for (String arg : this.floatingArgs.keySet()) {
				if (!this.allowedFloatingArgs.contains(arg)) {
					return -1;
				}
			}
		}

		return token;
	}

	private static void trimList(List<?> list, int size) {
		for (int i = list.size() - 1; i >= size; i--) {
			list.remove(i);
		}
	}

	private String getValue(int token) {
		assert token < this.tokenCount;

		return this.getValue(this.tokens[token], this.tokens[token + 1]);
	}

	private String getValue(int start, int end) {
		if (start == end) return "";

		int escapeStart = start;

		while (escapeStart < end && this.input.charAt(escapeStart) != '\\') {
			escapeStart++;
		}

		if (escapeStart == end) return this.input.subSequence(start, end).toString();

		this.buffer.setLength(0);
		this.buffer.append(this.input, start, escapeStart);
		start = escapeStart;

		while (start < end) {
			char c = this.input.charAt(start);

			if (c == '\\' && start + 1 < end) {
				c = this.input.charAt(++start);

				int idx = "nrtb".indexOf(c);

				if (idx >= 0) {
					this.buffer.append("\n\r\t\b").charAt(idx);
				} else {
					this.buffer.append(c);
				}
			} else {
				this.buffer.append(c);
			}
		}

		return this.buffer.toString();
	}

	private void ensureQueueSpace(int space) {
		if (this.queue == null) {
			this.queue = new int[Math.max(space, 30)];
		} else if (this.queue.length - this.queueSize < space) {
			this.queue = Arrays.copyOf(this.queue, Math.max(this.queue.length * 2, this.queue.length - this.queueSize + space));
		}
	}
}
