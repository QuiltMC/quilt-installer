/*
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
 */

package org.quiltmc.installer;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.action.Action;

/**
 * The main entrypoint when installing from the command line.
 */
public final class CliInstaller {
	// The value in this variable will be set by blossom at compile time.
	public static final String INSTALLER_VERSION = "__INSTALLER_VERSION";

	public static void run(String[] args) {
		// Assemble the array of args back into a single string
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < args.length; i++) {
			if (i != 0) {
				builder.append(' ');
			}

			builder.append(args[i]);
		}

		Action<?> action = parse(builder.toString());

		action.run(msg -> {
			if (action != Action.DISPLAY_HELP) {
				// TODO: Do we want to add some sort of hanging carriage percentage tracker?
				// TODO: Implement CLI tracker
			}

			// Help shouldn't need a progress bar
		});
	}

	/**
	 * Parse the inputted arguments and return the appropriate action that should be run.
	 *
	 * @param input the input
	 * @return the action, defaulting to {@link Action#DISPLAY_HELP} if the arguments were improperly parsed.
	 */
	private static Action<?> parse(String input) {
		Queue<String> split = splitQuoted(input);

		String arg = split.remove();

		switch (arg) {
		case "help":
			return Action.DISPLAY_HELP;
		case "listVersions":
			if (split.size() == 0) {
				return Action.listVersions(false);
			}

			arg = split.remove();

			if (arg.equals("--snapshots")) {
				return Action.listVersions(true);
			} else if (arg.startsWith("--")) {
				System.err.printf("Invalid option \"%s\"%n", arg);
			} else {
				System.err.printf("Unexpected additional argument \"%s\"%n", arg);
			}

			return Action.DISPLAY_HELP;
		case "install":
			if (split.size() == 0) {
				System.err.println("Side is required: \"client\" or \"server\"");
				return Action.DISPLAY_HELP;
			}

			arg = split.remove();

			switch (arg) {
			case "client": {
				if (split.size() == 0) {
					System.err.println("Minecraft version is required");
					return Action.DISPLAY_HELP;
				}

				String minecraftVersion = split.remove();

				// At this point all the require arguments have been parsed
				if (split.size() == 0) {
					return Action.installClient(minecraftVersion, null, null, false);
				}

				// Try to parse loader version first
				@Nullable
				String loaderVersion = null;
				arg = split.peek();

				// Loader option is set
				if (!arg.startsWith("-")) {
					loaderVersion = split.remove();
				}

				// No more arguments, just loader version
				if (split.size() == 0) {
					return Action.installClient(minecraftVersion, loaderVersion, null, false);
				}

				// There are some additional options
				Map<String, String> options = new LinkedHashMap<>();

				while (split.peek() != null) {
					String option = split.remove();

					// Just two -- is not enough
					if (!option.startsWith("--")) {
						System.err.printf("Invalid option %s%n", option);
						return Action.DISPLAY_HELP;
					}

					if (option.equals("--no-profile")) {
						if (options.containsKey("--no-profile")) {
							System.err.println("Encountered duplicate option \"--no-profile\", This shouldn't affect anything");
						}

						options.put("--no-profile", null);
					// Common option
					} else if (option.startsWith("--install-dir")) {
						if (options.containsKey("--install-dir")) {
							System.err.println("Encountered duplicate option \"--install-dir\"");
							return Action.DISPLAY_HELP;
						}

						if (option.indexOf('=') == -1) {
							System.err.println("Option \"--install-dir\" must specify a value");
							return Action.DISPLAY_HELP;
						}

						if (!option.startsWith("--install-dir=")) {
							System.err.println("Option \"--install-dir\" must have a equals sign (=) right after the option name to get the value");
							return Action.DISPLAY_HELP;
						}

						String value = unqoute(option.substring(14));

						if (value == null) {
							System.err.println("Option \"--install-dir\" must have value quoted at start and end of value");
							return Action.DISPLAY_HELP;
						}

						options.put("--install-dir", value);
					} else {
						System.err.printf("Invalid option %s%n", option);
						return Action.DISPLAY_HELP;
					}
				}

				return Action.installClient(minecraftVersion, loaderVersion, options.get("--install-dir"), !options.containsKey("--no-profile"));
			}
			case "server": {
				if (split.size() == 0) {
					System.err.println("Minecraft version is required");
					return Action.DISPLAY_HELP;
				}

				String minecraftVersion = split.remove();

				// At this point all the require arguments have been parsed
				if (split.size() == 0) {
					return Action.installServer(minecraftVersion, null, null, false, false);
				}

				// Try to parse loader version first
				@Nullable
				String loaderVersion = null;
				arg = split.peek();

				// Loader option is set
				if (!arg.startsWith("-")) {
					loaderVersion = split.remove();
				}

				// No more arguments, just loader version
				if (split.size() == 0) {
					return Action.installServer(minecraftVersion, loaderVersion, null, false, false);
				}

				// There are some additional options
				Map<String, String> options = new LinkedHashMap<>();

				while (split.peek() != null) {
					String option = split.remove();

					// Just two -- is not enough
					if (!option.startsWith("--")) {
						System.err.printf("Invalid option %s%n", option);
					}

					if (option.equals("--create-scripts")) {
						if (options.containsKey("--create-scripts")) {
							System.err.println("Encountered duplicate option \"--create-scripts\", This shouldn't affect anything");
						}

						options.put("--create-scripts", null);
					} else if (option.equals("--download-server")) {
						if (options.containsKey("--download-server")) {
							System.err.println("Encountered duplicate option \"--download-server\", This shouldn't affect anything");
						}

						options.put("--download-server", null);
					// Common option
					} else if (option.startsWith("--install-dir")) {
						if (options.containsKey("--install-dir")) {
							System.err.println("Encountered duplicate option \"--install-dir\"");
							return Action.DISPLAY_HELP;
						}

						if (option.indexOf('=') == -1) {
							System.err.println("Option \"--install-dir\" must specify a value");
							return Action.DISPLAY_HELP;
						}

						if (!option.startsWith("--install-dir=")) {
							System.err.println("Option \"--install-dir\" must have a equals sign (=) right after the option name to get the value");
							return Action.DISPLAY_HELP;
						}

						String value = unqoute(option.substring(14));

						if (value == null) {
							System.err.println("Option \"--install-dir\" must have value quoted at start and end of value");
							return Action.DISPLAY_HELP;
						}

						options.put("--install-dir", value);
					} else {
						System.err.printf("Invalid option %s%n", option);
						return Action.DISPLAY_HELP;
					}
				}

				return Action.installServer(minecraftVersion, loaderVersion, options.get("--install-dir"), options.containsKey("--create-scripts"), options.containsKey("--download-server"));
			}
			default:
				System.err.printf("Invalid side \"%s\", expected \"client\" or \"server\"%n", arg);
				return Action.DISPLAY_HELP;
			}

		default:
			System.err.printf("Invalid argument \"%s\"%n", arg);
			return Action.DISPLAY_HELP;
		}
	}

	/**
	 * Takes a string and splits it at spaces while leaving quoted segements unsplit.
	 *
	 * @param input the input
	 * @return the split input
	 */
	private static Queue<String> splitQuoted(String input) {
		LinkedList<String> ret = new LinkedList<>();
		boolean inQuote = false;
		int lastEnd = 0;

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);

			if (c == '"') {
				inQuote = !inQuote;
			} else if (c == ' ') {
				if (!inQuote) {
					// Terminate word and add to list
					String word = input.substring(lastEnd, i);
					ret.add(word);
					lastEnd = i + 1;
				}
			}
		}

		// Terminate last thing being parsed
		String word = input.substring(lastEnd);
		ret.add(word);

		if (inQuote) {
			throw new IllegalArgumentException("Unterminated \" found");
		}

		return ret;
	}

	/**
	 * Takes a quoted string and removes quotes on the end of the input.
	 * If there is no quoting then the string is returned
	 *
	 * @param input the input to unquote
	 * @return the unquoted string or null if the string is improperly quoted.
	 */
	private static String unqoute(String input) {
		// Nothing to unquote
		if (input.indexOf('"') == -1) {
			return input;
		}

		if (input.indexOf('"') != 0) {
			return null; // Improper quoting, beginning of value must be quoted
		}

		if (input.charAt(input.length() - 1) != '"') {
			return null; // Improper quoting, end of value must be quoted
		}

		return input.substring(1, input.length() - 1);
	}
}
