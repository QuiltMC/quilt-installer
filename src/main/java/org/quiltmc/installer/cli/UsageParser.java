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

import java.util.Arrays;
import java.util.ListIterator;

// Original credit goes to sfPlayer1 from the Fabric Discord "thimble" prototype bot
final class UsageParser {
	private static final int TOKEN_STRIDE = 2;

	private CharSequence input;
	private int[] tokens = new int[TOKEN_STRIDE * 20]; // [start,end[ position pairs
	private int tokenCount = 0; // total token array entries (2 per actual token)

	/**
	 * Parse an usage string into a Node tree.
	 *
	 * <p>The usage string describes the acceptable parameters with their ordering and presence requirements for a
	 * command. Those parameters will be represented by a Node, then grouped appropriately with ListNode and OrNode to
	 * mirror the structure of the usage string. Optional and/or repeating Nodes will be flagged as such.
	 *
	 * <p>The resulting Node tree can then be used to parse and validate a command parameter string or further transformed
	 * into a graph structure.
	 *
	 * <p>Usage string format, a and b are any expression, x is a literal or name:
	 * <pre>
	 *   a b: a and b have to be supplied in this order
	 *   a|b: either a or b can be supplied, lowest precedence (a b|c is the same as (a b)|c)
	 *   [a]: a is optional
	 *  a...: a may be repeated, at least one instance, highest precedence
	 * (a b): a and b act as a common element in the surrounding context
	 *     x: literal input "x" required
	 * {@literal   <x>}: x is a variable capturing any input token
	 *   --x: position independent flag x
	 * --x=a: position independent flag x with mandatory value a (value may still be empty if a is e.g. (|b) or [b])
	 * --x[=a]: position independent flag x, optionally with value a
	 * </pre>
	 *
	 * @param usage usage string encoding the acceptable command parameters
	 * @param fixPositionDependence whether to ensure that there can be only one optional position dependent parameter
	 *        at a time by introducing the missing dependency between those parameters that removes ambiguity
	 * @return root node representing the usage string's tree form
	 */
	public Node parse(String usage, boolean fixPositionDependence) {
		this.input = usage;
		this.tokenCount = 0;

		/* parse usage string into the following tokens:
		 * - <..> where .. is anything
		 * - ...
		 * - (
		 * - )
		 * - [
		 * - ]
		 * - |
		 * - =
		 * - other consecutive non-whitespace strings
		 */

		for (int i = 0, max = usage.length(); i < max; i++) {
			char c = usage.charAt(i);

			if (c == '<') {
				int end = i + 1;

				while (end < max && usage.charAt(end) != '>') {
					end++;
				}

				if (usage.charAt(end) != '>') throw new IllegalArgumentException("unterminated < (missing >)");

				addToken(i, end + 1);
				i = end;
			} else if (c == '.' && i + 2 < max && usage.charAt(i + 1) == '.' && usage.charAt(i + 2) == '.') {
				if (i == 0 || Character.isWhitespace(usage.charAt(i - 1))) throw new IllegalArgumentException("... not directly after something");
				addToken(i, i + 3);
				i += 2;
			} else if ("()[]|=".indexOf(c) >= 0) {
				addToken(i, i + 1);
			} else if (!Character.isWhitespace(c)) {
				int end = i;

				while (end + 1 < max
						&& "()[]|=<>".indexOf(c = usage.charAt(end + 1)) < 0
						&& !Character.isWhitespace(c)
						&& (c != '.' || end + 3 >= max || usage.charAt(end + 2) != '.' || usage.charAt(end + 3) != '.')) {
					end++;
				}

				addToken(i, end + 1);
				i = end;
			}
		}

		// turn tokens into a node tree

		Node ret = toTree(0, tokenCount);

		if (fixPositionDependence) {
			ret = fixPositionDependence(ret);
		}

		return ret;
	}

	private void addToken(int start, int end) {
		if (tokenCount == tokens.length) tokens = Arrays.copyOf(tokens, tokens.length * TOKEN_STRIDE);
		tokens[tokenCount++] = start;
		tokens[tokenCount++] = end;
	}

	private Node toTree(int startToken, int endToken) {
		if (startToken == endToken) {
			return Node.EMPTY;
		}

		Node.Options options = null; // overall Options for this token range
		Node cur = null; // node currently being assembled (excl. encompassing Options or Options siblings)
		boolean isListNode = false; // whether cur was already wrapped in ListNode
		boolean lastWasEmpty = false; // tracks whether the previous node was empty, thus not in the current list and not applicable for repeat (...)

		for (int token = startToken; token < endToken; token += TOKEN_STRIDE) {
			int startPos = tokens[token];
			char c = input.charAt(startPos);
			Node node;

			if (c == '(' || c == '[') { // precedence-grouping: (x) or optional: [x]
				char closeChar = c == '(' ? ')' : ']';
				int toFind = 1;
				int subStart = token + TOKEN_STRIDE;

				for (int subToken = subStart; subToken < endToken; subToken += TOKEN_STRIDE) {
					char c2 = input.charAt(tokens[subToken]);

					if (c2 == c) {
						toFind++;
					} else if (c2 == closeChar) {
						toFind--;

						if (toFind == 0) {
							token = subToken;
							break;
						}
					}
				}

				if (toFind != 0) throw new IllegalArgumentException("unterminated "+c);

				node = toTree(subStart, token);

				if (c == '[') node.setOptional();
			} else if (c == '|') { // alternative options: ..|..
				if (options == null) options = new Node.Options();

				if (cur instanceof Node.Options) {
					for (Node n : ((Node.Options) cur)) {
						options.add(n);
					}
				} else {
					options.add(cur);
				}

				// reset cur
				cur = null;
				isListNode = false;
				continue;
			} else if (c == '-' && tokens[token + 1] > startPos + 2 && input.charAt(startPos + 1) == '-') { // --key or --key=value or --key[=value] pos-independent arg
				String name = input.subSequence(startPos + 2, tokens[token + 1]).toString();
				int separatorToken = token + TOKEN_STRIDE;
				Node value;
				char next;

				if (separatorToken < endToken
						&& ((next = input.charAt(tokens[separatorToken])) == '='
						|| next == '[' && separatorToken + TOKEN_STRIDE < endToken && input.charAt(tokens[separatorToken + TOKEN_STRIDE]) == '=')) { // next token is = -> --key=value
					int valueToken = separatorToken + TOKEN_STRIDE;
					int lastToken = valueToken;

					if (next == '[') {
						valueToken += TOKEN_STRIDE;
						lastToken = valueToken + TOKEN_STRIDE;

						if (lastToken >= endToken || input.charAt(tokens[lastToken]) != ']') throw new IllegalArgumentException("missing ] in --key[=value]");
					}

					if (valueToken >= endToken) throw new IllegalArgumentException("missing value in --key=value");

					value = toTree(valueToken, valueToken + TOKEN_STRIDE);
					if (next == '[') value.setOptional();

					token = lastToken;
				} else { // just --key
					value = null;
				}

				node = new Node.Floating(name, value);
			} else if (c == '.' && tokens[token + 1] == startPos + 3) { // repeat: x...
				if (cur == null) throw new IllegalArgumentException("standalone ...");

				if (!lastWasEmpty) {
					if (isListNode) {
						Node.ListNode prev = (Node.ListNode) cur;
						prev.get(prev.size() - 1).setRepeating();
					} else {
						cur.setRepeating();
					}
				}

				continue;
			} else if (c == '<') { // variable <x>
				node = new Node.ValueParameter(input.subSequence(startPos + 1, tokens[token + 1] - 1).toString());
			} else { // plain string
				node = new Node.Literal(input.subSequence(startPos, tokens[token + 1]).toString());
			}

			lastWasEmpty = node == Node.EMPTY;

			if (cur == null || cur == Node.EMPTY) {
				cur = node;
			} else if (!lastWasEmpty) { // 2+ consecutive nodes not separated by |, use ListNode
				if (!isListNode) {
					cur = new Node.ListNode(cur);
					isListNode = true;
				}

				((Node.ListNode) cur).add(node);
			}
		}

		if (options != null) {
			// add last node before end
			options.add(cur);

			return options.simplify();
		} else {
			return cur;
		}
	}

	private Node fixPositionDependence(Node node) {
		if (node instanceof Node.ListNode) {
			// transform a list with multiple optional position dependent entries such that it will have only one position dependent entry
			// e.g. [a] [b] [c]  ->  [a], a [b], a b [c]  <-  [a [b [c]]]
			// or    a  [b] [c]  ->  a [b], a b [c]       <-   a [b [c]]
			// or   a [b] [c] d  ->  a [b] d, a b [c] d   <-   a [b [c]] d

			Node.ListNode list = (Node.ListNode) node;
			Node.Options options = null;
			int lastOptional = -1;

			for (int i = 0; i < list.size(); i++) {
				Node element = list.get(i);

				if (!element.optional() || !element.isPositionDependent()) {
					continue;
				}

				if (lastOptional >= 0) {
					if (options == null) {
						options = new Node.Options();

						// set first option to list[0..firstOptional+1] + filterOpt(list[firstOptional+1..])

						if (lastOptional > 0 || list.size() > lastOptional + 1 + 1) { // new list may have more than 1 element (2+ leading or 1+ trailing elements)
							Node.ListNode newList = new Node.ListNode(list.size() - 1);
							copyList(list, 0, lastOptional + 1, newList);
							copyListNoOptional(list, lastOptional + 1, newList);

							if (newList.size() > 1) {
								options.add(newList);
							} else {
								options.add(newList.get(0));
							}
						} else {
							options.add(list.get(0));
						}
					}

					Node.ListNode newList = new Node.ListNode(list.size());

					if (lastOptional > 0) {
						Node.ListNode prevList = (Node.ListNode) options.get(options.size() - 1); // previously produced list has already at most 1 optional position dependent element at lastOptional
						copyList(prevList, 0, lastOptional, newList);
					}

					Node opt = list.get(lastOptional).copy();
					opt.clearOptional();
					newList.add(opt);

					copyList(list, lastOptional + 1, i + 1, newList);
					copyListNoOptional(list, i + 1, newList);
					options.add(newList);
				}

				lastOptional = i;
			}

			return options != null ? options : node;
		} else if (node instanceof Node.Options) {
			for (ListIterator<Node> it = ((Node.Options) node).options.listIterator(); it.hasNext(); ) {
				Node option = it.next();
				it.set(fixPositionDependence(option));
			}

			return node;
		} else {
			return node;
		}
	}

	private static void copyList(Node.ListNode src, int start, int end, Node.ListNode dst) {
		for (int i = start; i < end; i++) {
			dst.add(src.get(i));
		}
	}

	private static void copyListNoOptional(Node.ListNode src, int start, Node.ListNode dst) {
		for (int i = start; i < src.size(); i++) {
			Node element = src.get(i);

			if (!element.optional() || !element.isPositionDependent()) {
				dst.add(element);
			}
		}
	}
}
