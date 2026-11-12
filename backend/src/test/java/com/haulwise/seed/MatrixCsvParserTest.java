package com.haulwise.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MatrixCsvParserTest {

    /** Seed warehouse names (ARCHITECTURE 4.1). */
    private static final List<String> SEED_WAREHOUSES = List.of("WAW Central", "KRK South", "GDN Port", "POZ West",
            "WRO Hub", "LDZ Cross-dock", "KTW Silesia", "LUB East", "SZZ North-West");

    @Test
    void bundledMatrixCoversAll36PairsOfTheSeedWarehousesExactlyOnce() throws Exception {
        List<MatrixCsvRow> rows = parseBundledMatrix();

        Set<Set<String>> pairs = new HashSet<>();
        for (MatrixCsvRow row : rows) {
            assertThat(SEED_WAREHOUSES).contains(row.fromWarehouse(), row.toWarehouse());
            pairs.add(Set.of(row.fromWarehouse(), row.toWarehouse()));
        }
        assertThat(rows).hasSize(36);
        assertThat(pairs).hasSize(36);
    }

    @Test
    void bundledMatrixValuesArePlausibleTruckLegs() throws Exception {
        for (MatrixCsvRow row : parseBundledMatrix()) {
            double speedKmh = row.distanceKm() / (row.durationMinutes() / 60);
            assertThat(row.distanceKm()).as(row.toString()).isBetween(50.0, 800.0);
            assertThat(speedKmh).as(row.toString()).isBetween(55.0, 75.0);
        }
    }

    /** No pair is noticeably longer than a detour via any third city (small tolerance for rounding). */
    @Test
    void bundledMatrixIsRoughlyTriangleConsistent() throws Exception {
        Map<Set<String>, MatrixCsvRow> byPair = new HashMap<>();
        for (MatrixCsvRow row : parseBundledMatrix()) {
            byPair.put(Set.of(row.fromWarehouse(), row.toWarehouse()), row);
        }
        List<String> violations = new ArrayList<>();
        for (String a : SEED_WAREHOUSES) {
            for (String b : SEED_WAREHOUSES) {
                for (String c : SEED_WAREHOUSES) {
                    if (a.equals(b) || a.equals(c) || b.equals(c)) {
                        continue;
                    }
                    MatrixCsvRow direct = byPair.get(Set.of(a, b));
                    MatrixCsvRow first = byPair.get(Set.of(a, c));
                    MatrixCsvRow second = byPair.get(Set.of(c, b));
                    if (direct.durationMinutes() > first.durationMinutes() + second.durationMinutes() + 10
                            || direct.distanceKm() > first.distanceKm() + second.distanceKm() + 25) {
                        violations.add(a + " -> " + b + " via " + c);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void bundledMatrixHasTheDocumentedWarsawKrakowLeg() throws Exception {
        assertThat(parseBundledMatrix())
                .contains(new MatrixCsvRow("WAW Central", "KRK South", 295, 253));
    }

    @Test
    void wellFormedFileIsParsedWithTrimmingAndBlankLinesSkipped() {
        List<MatrixCsvRow> rows = MatrixCsvParser.parse(new StringReader("""
                from,to,distance_km,duration_minutes
                 A , B ,10.5,12

                B,C,20,30
                """));

        assertThat(rows).containsExactly(new MatrixCsvRow("A", "B", 10.5, 12), new MatrixCsvRow("B", "C", 20, 30));
    }

    @Test
    void leadingByteOrderMarkIsIgnored() {
        List<MatrixCsvRow> rows = MatrixCsvParser.parse(new StringReader("\uFEFF" + MatrixCsvParser.HEADER + "\nA,B,10,12\n"));

        assertThat(rows).containsExactly(new MatrixCsvRow("A", "B", 10, 12));
    }

    @Test
    void callersReaderIsNotClosed() throws Exception {
        StringReader reader = new StringReader(MatrixCsvParser.HEADER + "\nA,B,10,12\n");

        MatrixCsvParser.parse(reader);

        assertThatCode(reader::ready).doesNotThrowAnyException();
    }

    @Test
    void decimalCommaGetsASeparatorHint() {
        String csv = MatrixCsvParser.HEADER + "\nA,B,10,5,12\n";

        assertThatThrownBy(() -> MatrixCsvParser.parse(new StringReader(csv)))
                .isInstanceOf(SeedDataException.class)
                .hasMessage("seed/matrix.csv line 2: expected 4 columns but found 5 (use '.' as the decimal separator)");
    }

    @Test
    void reversedRowSwapsTheEndsAndKeepsTheValues() {
        assertThat(new MatrixCsvRow("A", "B", 10, 12).reversed()).isEqualTo(new MatrixCsvRow("B", "A", 10, 12));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "A,B,10           | expected 4 columns but found 3",
            "A,B,10,12,13     | expected 4 columns but found 5",
            ",B,10,12         | from is blank",
            "A,,10,12         | to is blank",
            "A,a,10,12        | from and to must differ",
            "A,B,ten,12       | distance_km is not a number: 'ten'",
            "A,B,10,          | duration_minutes is not a number: ''",
            "A,B,0,12         | distance_km must be a positive number",
            "A,B,10,-5        | duration_minutes must be a positive number",
            "A,B,NaN,12       | distance_km must be a positive number"
    })
    void badRowIsRejectedWithItsLineNumber(String row, String expectedMessage) {
        String csv = MatrixCsvParser.HEADER + "\nX,Y,1,1\n" + row + "\n";

        assertThatThrownBy(() -> MatrixCsvParser.parse(new StringReader(csv)))
                .isInstanceOf(SeedDataException.class)
                .hasMessageStartingWith("seed/matrix.csv line 3: ")
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void samePairInEitherDirectionIsRejectedAsDuplicate() {
        String csv = MatrixCsvParser.HEADER + "\nA,B,10,12\nb,a,11,13\n";

        assertThatThrownBy(() -> MatrixCsvParser.parse(new StringReader(csv)))
                .isInstanceOf(SeedDataException.class)
                .hasMessage("seed/matrix.csv line 3: duplicate pair b / a");
    }

    @Test
    void missingOrWrongHeaderIsRejected() {
        assertThatThrownBy(() -> MatrixCsvParser.parse(new StringReader("")))
                .isInstanceOf(SeedDataException.class)
                .hasMessageContaining("header");
        assertThatThrownBy(() -> MatrixCsvParser.parse(new StringReader("A,B,10,12\n")))
                .isInstanceOf(SeedDataException.class)
                .hasMessageContaining("header");
    }

    @Test
    void fileWithoutDataRowsIsRejected() {
        assertThatThrownBy(() -> MatrixCsvParser.parse(new StringReader(MatrixCsvParser.HEADER + "\n\n")))
                .isInstanceOf(SeedDataException.class)
                .hasMessage("seed/matrix.csv: no data rows");
    }

    private static List<MatrixCsvRow> parseBundledMatrix() throws Exception {
        try (InputStream in = MatrixCsvParserTest.class.getClassLoader().getResourceAsStream(MatrixCsvParser.RESOURCE)) {
            assertThat(in).as(MatrixCsvParser.RESOURCE).isNotNull();
            return MatrixCsvParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
