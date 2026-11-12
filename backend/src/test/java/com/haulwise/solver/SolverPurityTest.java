package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard: the solver is plain Java and must not depend on Spring, JPA/Hibernate or the
 * JPA entities in {@code com.haulwise.domain} (services map entities to solver model records).
 */
class SolverPurityTest {

    private static final Path SOLVER_SOURCES = Path.of("src", "main", "java", "com", "haulwise", "solver");
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "org.springframework.", "jakarta.persistence.", "org.hibernate.", "com.haulwise.domain.");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;");

    @Test
    void solverSourcesHaveNoFrameworkOrEntityImports() throws IOException {
        assertThat(SOLVER_SOURCES).isDirectory();

        try (Stream<Path> files = Files.walk(SOLVER_SOURCES)) {
            List<String> violations = files
                    .filter(file -> file.toString().endsWith(".java"))
                    .flatMap(SolverPurityTest::forbiddenImports)
                    .toList();

            assertThat(violations).isEmpty();
        }
    }

    @Test
    void detectsPlainAndStaticForbiddenImports() {
        assertThat(isForbidden("import org.springframework.stereotype.Component;")).isTrue();
        assertThat(isForbidden("import static jakarta.persistence.FetchType.LAZY;")).isTrue();
        assertThat(isForbidden("import com.haulwise.domain.Priority;")).isTrue();
        assertThat(isForbidden("import static org.hibernate.Hibernate.*;")).isTrue();
        assertThat(isForbidden("import java.util.List;")).isFalse();
        assertThat(isForbidden("import static java.lang.Math.max;")).isFalse();
        assertThat(isForbidden("import com.haulwise.solver.model.Leg;")).isFalse();
    }

    private static Stream<String> forbiddenImports(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(SolverPurityTest::isForbidden)
                    .map(line -> file.getFileName() + ": " + line.trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isForbidden(String line) {
        Matcher matcher = IMPORT.matcher(line);
        return matcher.find() && FORBIDDEN_PACKAGES.stream().anyMatch(matcher.group(1)::startsWith);
    }
}
