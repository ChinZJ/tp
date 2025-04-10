package seedu.tassist.logic.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tokenizes arguments string of the form: {@code preamble <prefix>value <prefix>value ...}<br>
 *     e.g. {@code some preamble text t/ 11.00 t/12.00 k/ m/ July}
 *     where prefixes are {@code t/ k/ m/}.<br>
 * 1. An argument's value can be an empty string
 *     e.g. the value of {@code k/} in the above example.<br>
 * 2. Leading and trailing whitespaces of an argument value will be discarded.<br>
 * 3. An argument may be repeated and all its values will be accumulated
 *     e.g. the value of {@code t/} in the above example.<br>
 */
public class ArgumentTokenizer {

    /**
     * Tokenizes an arguments string and returns an {@code ArgumentMultimap}
     * object that maps prefixes to their respective argument values.
     * Only the given prefixes will be recognized in the arguments string.
     *
     * @param argsString Arguments string of the form: {@code preamble <prefix>value ...}
     * @param prefixes   Prefixes to tokenize the arguments string with
     * @return           ArgumentMultimap object that maps prefixes to their arguments
     */
    public static ArgumentMultimap tokenize(String argsString, Prefix... prefixes) {
        List<QuotedSection> quotedSections = findQuotedSections(argsString);
        List<PrefixPosition> positions = findAllPrefixPositions(argsString,
                quotedSections, prefixes);
        return extractArguments(argsString, positions, quotedSections);
    }

    /**
     * Finds all sections enclosed in quotes in the given arguments string.
     * Assumes even number of quotes. (See {@code QuotePattern} class).
     *
     * @param argsString Arguments string to search for quoted sections
     * @return           List of quoted sections with their start and end positions
     */
    private static List<QuotedSection> findQuotedSections(String argsString) {
        List<QuotedSection> sections = new ArrayList<>();
        boolean inQuotes = false;
        int startQuote = -1;

        // Linear pairwise search.
        for (int i = 0; i < argsString.length(); i++) {
            if (argsString.charAt(i) == '"') {
                if (!inQuotes) {
                    // Start of quoted section
                    inQuotes = true;
                    startQuote = i;
                } else {
                    // End of quoted section
                    inQuotes = false;
                    sections.add(new QuotedSection(startQuote, i));
                }
            }
        }

        return sections;
    }

    /**
     * Finds all zero-based prefix positions in the given arguments string.
     *
     * @param argsString        Arguments string of the form: {@code preamble <prefix>value ...}
     * @param quotedSections    List of sections enclosed in quotes.
     * @param prefixes          Prefixes to find in the arguments string
     * @return                  List of zero-based prefix positions in the given arguments string
     */
    private static List<PrefixPosition> findAllPrefixPositions(String argsString,
                List<QuotedSection> quotedSections, Prefix... prefixes) {
        return Arrays.stream(prefixes)
                .flatMap(prefix -> findPrefixPositions(
                        argsString, quotedSections, prefix).stream())
                .collect(Collectors.toList());
    }

    /**
     * {@see findAllPrefixPositions}
     */
    private static List<PrefixPosition> findPrefixPositions(String argsString,
                List<QuotedSection> quotedSections, Prefix prefix) {
        List<PrefixPosition> positions = new ArrayList<>();

        int prefixPosition = findPrefixPosition(argsString, quotedSections, prefix.getPrefix(), 0);
        while (prefixPosition != -1) {
            PrefixPosition extendedPrefix = new PrefixPosition(prefix, prefixPosition);
            positions.add(extendedPrefix);
            prefixPosition = findPrefixPosition(argsString, quotedSections, prefix.getPrefix(),
                    prefixPosition + prefix.getLength());
        }

        return positions;
    }

    /**
     * Returns the index of the first occurrence of {@code prefix} in
     * {@code argsString} starting from index {@code fromIndex}. An occurrence
     * is valid if there is a whitespace before and after {@code prefix}. Returns -1 if no
     * such occurrence can be found.
     * E.g if {@code argsString} = "-e hi-p900", {@code prefix} = "-p" and
     * {@code fromIndex} = 0, this method returns -1 as there are no valid
     * occurrences of "-p" with whitespace before and after it. However, if
     * {@code argsString} = "-e hi -p 900", {@code prefix} = "-p" and
     * {@code fromIndex} = 0, this method returns 6.
     */
    private static int findPrefixPosition(String argsString,
                List<QuotedSection> quotedSections, String prefix, int fromIndex) {

        int currentIndex = fromIndex;

        while (true) {
            int prefixIndex = argsString.indexOf(" " + prefix + " ", currentIndex);
            if (prefixIndex == -1) {

                if (argsString.endsWith(" " + prefix) && (currentIndex < argsString.length())) {
                    prefixIndex = argsString.lastIndexOf(" " + prefix);

                    // Check if this position is inside a quoted section
                    if (!isInsideQuotedSection(prefixIndex + 1, quotedSections)) {
                        return prefixIndex + 1; // +1 as an offset for whitespace
                    }
                }
                return -1; // No more occurrences
            }

            // Check if this position is inside a quoted section
            if (!isInsideQuotedSection(prefixIndex + 1, quotedSections)) {
                return prefixIndex + 1; // +1 as an offset for whitespace
            }

            // Move past this occurrence and continue searching
            currentIndex = prefixIndex + prefix.length();
        }
    }

    /**
     * Checks if a position falls within any quoted section.
     */
    private static boolean isInsideQuotedSection(int position, List<QuotedSection> quotedSections) {
        for (QuotedSection section : quotedSections) {
            if (position > section.getStartPosition() && position < section.getEndPosition()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts prefixes and their argument values, and returns an {@code ArgumentMultimap}
     * object that maps the extracted prefixes to their respective arguments.
     * Prefixes are extracted based on their zero-based positions in
     * {@code argsString}.
     *
     * @param argsString      Arguments string of the form: {@code preamble <prefix>value ...}
     * @param prefixPositions Zero-based positions of all prefixes in {@code argsString}
     * @return                ArgumentMultimap object that maps prefixes to their arguments
     */
    private static ArgumentMultimap extractArguments(String argsString,
                List<PrefixPosition> prefixPositions, List<QuotedSection> quotedSections) {

        // Sort by start position
        prefixPositions.sort((
                prefix1, prefix2) -> prefix1.getStartPosition()
                - prefix2.getStartPosition());

        // Insert a PrefixPosition to represent the preamble
        PrefixPosition preambleMarker = new PrefixPosition(new Prefix(""), 0);
        prefixPositions.add(0, preambleMarker);

        // Add a dummy PrefixPosition to represent the end of the string
        PrefixPosition endPositionMarker = new PrefixPosition(new Prefix(""), argsString.length());
        prefixPositions.add(endPositionMarker);

        // Map prefixes to their argument values (if any)
        ArgumentMultimap argMultimap = new ArgumentMultimap();
        for (int i = 0; i < prefixPositions.size() - 1; i++) {
            // Extract and store prefixes and their arguments
            Prefix argPrefix = prefixPositions.get(i).getPrefix();
            String argValue = extractArgumentValue(argsString, prefixPositions.get(i),
                    prefixPositions.get(i + 1), quotedSections);
            argMultimap.put(argPrefix, argValue);
        }

        return argMultimap;
    }

    /**
     * Returns the trimmed value of the argument in the arguments string
     * specified by {@code currentPrefixPosition}.
     * The end position of the value is determined by {@code nextPrefixPosition}.
     */
    private static String extractArgumentValue(String argsString,
            PrefixPosition currentPrefixPosition, PrefixPosition nextPrefixPosition,
            List<QuotedSection> quoteSections) {
        Prefix prefix = currentPrefixPosition.getPrefix();

        int valueStartPos = currentPrefixPosition.getStartPosition() + prefix.getPrefix().length();
        String value = argsString.substring(valueStartPos, nextPrefixPosition.getStartPosition());

        String trimmedValue = value.trim();
        if (trimmedValue.startsWith("\"") && trimmedValue.endsWith("\"")) {
            return trimmedValue.substring(1, trimmedValue.length() - 1);
        }

        return trimmedValue;
    }

    /**
     * Represents a prefix's position in an arguments string.
     */
    private static class PrefixPosition {
        private int startPosition;
        private final Prefix prefix;

        PrefixPosition(Prefix prefix, int startPosition) {
            this.prefix = prefix;
            this.startPosition = startPosition;
        }

        int getStartPosition() {
            return startPosition;
        }

        Prefix getPrefix() {
            return prefix;
        }
    }

    /**
     * Represents a section of text enclosed in quotes.
     */
    private static class QuotedSection {
        private final int startPosition;
        private final int endPosition;

        private QuotedSection(int startPosition, int endPosition) {
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }

        private int getStartPosition() {
            return startPosition;
        }

        private int getEndPosition() {
            return endPosition;
        }
    }

}
