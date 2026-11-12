package com.haulwise.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal reader for the simple seed CSV files: a fixed header line, then plain comma-separated rows
 * (no quoting, '.' as decimal separator). Blank lines are skipped; a leading UTF-8 byte-order mark is ignored.
 */
final class SeedCsv {

    private static final String BOM = "\uFEFF";

    private SeedCsv() {
    }

    /**
     * Reads every row of {@code reader} (owned and closed by the caller).
     *
     * @param file   file name used in error messages, e.g. {@code seed/trucks.csv}
     * @param header expected column names, in order
     * @throws SeedDataException on a wrong header, a wrong column count or an empty file
     */
    static List<Row> parse(String file, Reader reader, List<String> header) {
        BufferedReader lines = new BufferedReader(reader);
        try {
            String first = lines.readLine();
            String expected = String.join(",", header);
            if (first == null || !expected.equals(stripBom(first).strip())) {
                throw new SeedDataException(file + ": first line must be the header '" + expected + "'");
            }
            List<Row> rows = new ArrayList<>();
            int lineNumber = 1;
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                lineNumber++;
                if (!line.isBlank()) {
                    rows.add(toRow(file, lineNumber, line, header));
                }
            }
            if (rows.isEmpty()) {
                throw new SeedDataException(file + ": no data rows");
            }
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static Row toRow(String file, int lineNumber, String line, List<String> header) {
        String[] fields = line.split(",", -1);
        Row row = new Row(file, lineNumber, new LinkedHashMap<>());
        if (fields.length != header.size()) {
            throw row.error("expected " + header.size() + " columns but found " + fields.length);
        }
        for (int i = 0; i < fields.length; i++) {
            row.values().put(header.get(i), fields[i].strip());
        }
        return row;
    }

    private static String stripBom(String line) {
        return line.startsWith(BOM) ? line.substring(BOM.length()) : line;
    }

    /** One data row; the typed accessors fail with the file and line number. */
    record Row(String file, int lineNumber, Map<String, String> values) {

        /** The stripped value; may be empty. */
        String text(String column) {
            return values.get(column);
        }

        String requiredText(String column) {
            String value = text(column);
            if (value.isEmpty()) {
                throw error(column + " is blank");
            }
            return value;
        }

        int integer(String column) {
            String value = requiredText(column);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw error(column + " is not a whole number: '" + value + "'");
            }
        }

        double decimal(String column) {
            String value = requiredText(column);
            try {
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number)) {
                    throw error(column + " is not a finite number: '" + value + "'");
                }
                return number;
            } catch (NumberFormatException e) {
                throw error(column + " is not a number: '" + value + "'");
            }
        }

        boolean bool(String column) {
            String value = requiredText(column);
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw error(column + " must be true or false, was '" + value + "'");
        }

        SeedDataException error(String message) {
            return new SeedDataException(file + " line " + lineNumber + ": " + message);
        }
    }
}
