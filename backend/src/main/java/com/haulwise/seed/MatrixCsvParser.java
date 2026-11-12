package com.haulwise.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses {@code seed/matrix.csv}: a header {@value #HEADER}, then one row per unordered warehouse pair
 * ({@code name,name,km,minutes}). Plain comma-separated values without quoting; blank lines are skipped.
 * Persisting the rows (both directions) is the seed loader's job (T-08).
 */
public final class MatrixCsvParser {

    public static final String RESOURCE = "seed/matrix.csv";
    static final String HEADER = "from,to,distance_km,duration_minutes";
    private static final int COLUMNS = 4;
    private static final String BOM = "\uFEFF";

    private MatrixCsvParser() {
    }

    /**
     * Reads the whole input. The caller owns {@code reader} and closes it; it should decode UTF-8
     * (a leading byte-order mark is ignored).
     *
     * @throws SeedDataException naming the line of the first malformed row, a duplicate pair or a missing header
     */
    public static List<MatrixCsvRow> parse(Reader reader) {
        BufferedReader lines = new BufferedReader(reader);
        try {
            String header = lines.readLine();
            if (header == null || !HEADER.equals(stripBom(header).strip())) {
                throw new SeedDataException(RESOURCE + ": first line must be the header '" + HEADER + "'");
            }
            List<MatrixCsvRow> rows = new ArrayList<>();
            Set<String> seenPairs = new HashSet<>();
            int lineNumber = 1;
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                MatrixCsvRow row = parseRow(line, lineNumber);
                if (!seenPairs.add(pairKey(row))) {
                    throw error(lineNumber, "duplicate pair " + row.fromWarehouse() + " / " + row.toWarehouse());
                }
                rows.add(row);
            }
            if (rows.isEmpty()) {
                throw new SeedDataException(RESOURCE + ": no data rows");
            }
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    private static MatrixCsvRow parseRow(String line, int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != COLUMNS) {
            String hint = fields.length > COLUMNS ? " (use '.' as the decimal separator)" : "";
            throw error(lineNumber, "expected " + COLUMNS + " columns but found " + fields.length + hint);
        }
        String from = requireName(fields[0], "from", lineNumber);
        String to = requireName(fields[1], "to", lineNumber);
        if (from.equalsIgnoreCase(to)) {
            throw error(lineNumber, "from and to must differ (" + from + ")");
        }
        double km = requirePositive(fields[2], "distance_km", lineNumber);
        double minutes = requirePositive(fields[3], "duration_minutes", lineNumber);
        return new MatrixCsvRow(from, to, km, minutes);
    }

    private static String requireName(String field, String column, int lineNumber) {
        String name = field.strip();
        if (name.isEmpty()) {
            throw error(lineNumber, column + " is blank");
        }
        return name;
    }

    private static double requirePositive(String field, String column, int lineNumber) {
        double value;
        try {
            value = Double.parseDouble(field.strip());
        } catch (NumberFormatException e) {
            throw error(lineNumber, column + " is not a number: '" + field.strip() + "'");
        }
        if (!Double.isFinite(value) || value <= 0) {
            throw error(lineNumber, column + " must be a positive number, was " + field.strip());
        }
        return value;
    }

    private static String stripBom(String line) {
        return line.startsWith(BOM) ? line.substring(BOM.length()) : line;
    }

    /** Order-independent, case-insensitive key: A/B and B/A are the same pair. */
    private static String pairKey(MatrixCsvRow row) {
        String a = row.fromWarehouse().toLowerCase(Locale.ROOT);
        String b = row.toWarehouse().toLowerCase(Locale.ROOT);
        return a.compareTo(b) < 0 ? a + '\n' + b : b + '\n' + a;
    }

    private static SeedDataException error(int lineNumber, String message) {
        return new SeedDataException(RESOURCE + " line " + lineNumber + ": " + message);
    }
}
