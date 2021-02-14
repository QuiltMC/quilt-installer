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
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a parsed node for a command input.
 */
// Original credit goes to sfPlayer1 from the Fabric Discord "thimble" prototype bot
abstract class Node {
	static final Node EMPTY = new Node() {
		@Override
		protected Node copy() {
			return this;
		}

		@Override
		protected void setOptional() {
		}

		@Override
		protected void setRepeating() {
		}

		@Override
		protected boolean isPositionDependent() {
			return false;
		}

		@Override
		protected String toString0() {
			return "{empty}";
		}
	};

	/**
	 * Whether this node may be omitted.
	 */
	private boolean optional;
	/**
	 * Whether this node may be repeatedly specified.
	 */
	private boolean repeat;

	final boolean optional() {
		return this.optional;
	}

	void setOptional() {
		this.optional = true;
	}

	void clearOptional() {
		this.optional = false;
	}

	final boolean isRepeating() {
		return repeat;
	}

	void setRepeating() {
		this.repeat = true;
	}

	abstract Node copy();

	<T extends Node> T copyFlags(T to) {
		if (this.optional()) to.setOptional();
		if (this.isRepeating()) to.setRepeating();

		return to;
	}

	abstract boolean isPositionDependent();

	abstract String toString0();

	final String toString(boolean ignoreOptional) {
		String s = toString0();

		if (repeat) {
			return s.concat("...");
		} else if (optional && !ignoreOptional) {
			return String.format("[%s]", s);
		} else {
			return s;
		}
	}

	@Override
	public final String toString() {
		return toString(false);
	}

	/**
	 * Argument that isn't bound to a specific position.
	 */
	static final class Floating extends Node {
		public final String name;
		@Nullable
		public final Node value;

		Floating(String name, @Nullable Node value) {
			this.name = name;
			this.value = value;
		}

		@Override
		Floating copy() {
			return this.copyFlags(new Floating(this.name, this.value));
		}

		@Override
		boolean isPositionDependent() {
			return false;
		}

		@Override
		String toString0() {
			if (this.value == null) {
				return String.format("--%s", this.name);
			} else if (this.value.optional()) {
				return String.format("--%s[=%s]", this.name, this.value.toString(true));
			} else {
				return String.format("--%s=%s", this.name, this.value.toString());
			}
		}
	}

	/**
	 * List tree node with consecutive children (unless floating).
	 */
	static final class ListNode extends Node implements Iterable<Node> {
		private final List<Node> elements;

		ListNode(Node element) {
			this.elements = new ArrayList<>();
			this.elements.add(element);
			setOptional();
		}

		ListNode(int size) {
			this.elements = new ArrayList<>(size);
			setOptional();
		}

		@Override
		ListNode copy() {
			ListNode ret = new ListNode(this.elements.size());
			ret.elements.addAll(this.elements);

			return this.copyFlags(ret);
		}

		@Override
		boolean isPositionDependent() {
			for (Node element : this.elements) {
				if (element.isPositionDependent()) {
					return true;
				}
			}

			return false;
		}

		int size() {
			return this.elements.size();
		}

		Node get(int index) {
			return this.elements.get(index);
		}

		@Override
		public Iterator<Node> iterator() {
			return this.elements.iterator();
		}

		void add(Node node) {
			this.elements.add(node);

			if (!node.optional()) {
				this.clearOptional();
			}
		}

		@Override
		String toString0() {
			StringBuilder ret = new StringBuilder();
			ret.append('{');

			for (int i = 0; i < this.elements.size(); i++) {
				if (i != 0) ret.append(',');
				ret.append(this.elements.get(i).toString());
			}

			ret.append('}');

			return ret.toString();
		}
	}

	/**
	 * Plain text string.
	 */
	static final class Literal extends Node {
		final String name;

		Literal(String name) {
			this.name = name;
		}

		@Override
		Literal copy() {
			return this.copyFlags(new Literal(this.name));
		}

		@Override
		boolean isPositionDependent() {
			return true;
		}

		@Override
		String toString0() {
			return String.format("\"%s\"", this.name);
		}
	}

	/**
	 * Selection of mutually exclusive children.
	 */
	static final class Options extends Node implements Iterable<Node> {
		final List<Node> options;

		Options() {
			this.options = new ArrayList<>();
		}

		Options(int size) {
			this.options = new ArrayList<>(size);
		}

		@Override
		Options copy() {
			Options ret = new Options(this.options.size());
			ret.options.addAll(this.options);

			return this.copyFlags(ret);
		}

		@Override
		boolean isPositionDependent() {
			for (Node option : this.options) {
				if (option.isPositionDependent()) {
					return true;
				}
			}

			return false;
		}

		int size() {
			return options.size();
		}

		Node get(int index) {
			return options.get(index);
		}

		@Override
		public Iterator<Node> iterator() {
			return options.iterator();
		}

		void add(Node node) {
			if (node == null || node == Node.EMPTY) { // leading empty: (|x) or trailing empty: (x|)
				this.setOptional();
			} else { // regular: (x|y)
				this.options.add(node);

				if (node.optional()) {
					setOptional();
				}
			}
		}

		Node simplify() {
			if (this.options.isEmpty()) {
				return EMPTY;
			} else if (this.options.size() == 1) {
				Node ret = this.options.get(0);

				if (optional()) {
					ret.setOptional();
				}

				if (isRepeating()) {
					ret.setRepeating();
				}

				return ret;
			} else {
				return this;
			}
		}

		@Override
		String toString0() {
			StringBuilder ret = new StringBuilder();
			ret.append('{');

			for (int i = 0; i < this.options.size(); i++) {
				if (i != 0) ret.append('|');
				ret.append(this.options.get(i).toString());
			}

			ret.append('}');

			return ret.toString();
		}
	}

	/**
	 * Variable representing user input.
	 */
	static final class ValueParameter extends Node {
		public final String name;

		ValueParameter(String name) {
			this.name = name;
		}

		@Override
		protected ValueParameter copy() {
			return this.copyFlags(new ValueParameter(this.name));
		}

		@Override
		boolean isPositionDependent() {
			return true;
		}

		@Override
		String toString0() {
			return String.format("<%s>", this.name);
		}
	}
}
