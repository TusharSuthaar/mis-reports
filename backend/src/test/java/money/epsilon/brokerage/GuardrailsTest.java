package money.epsilon.brokerage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the code and the docs (docs/architecture, docs/product) in step. See docs/architecture/guardrails.md.
 * Runs as a plain unit test from backend/ with the repo root as its parent directory.
 */
class GuardrailsTest {

    private static final Path BACKEND = Path.of("").toAbsolutePath();
    private static final Path REPO = BACKEND.getParent();
    private static final Path MAIN = BACKEND.resolve("src/main/java/money/epsilon/brokerage");
    private static final Path TESTS = BACKEND.resolve("src/test/java");
    private static final Path MIGRATIONS = BACKEND.resolve("src/main/resources/db/migration");
    private static final Path MODULES_DOC = REPO.resolve("docs/architecture/modules.md");
    private static final Path RULES_DOC = REPO.resolve("docs/product/business-rules.md");

    /** G-02 — every table and view the migrations create has exactly one owning module in modules.md. */
    @Test
    void everyTableHasExactlyOneOwner() throws IOException {
        Map<String, String> owners = owners();
        Set<String> created = createdTables();
        assertThat(owners.keySet()).as("tables listed in docs/architecture/modules.md").containsExactlyInAnyOrderElementsOf(created);
        Set<String> modules = new TreeSet<>(owners.values());
        try (Stream<Path> dirs = Files.list(MAIN)) {
            Set<String> packages = new TreeSet<>(dirs.filter(Files::isDirectory).map(p -> p.getFileName().toString()).toList());
            assertThat(packages).as("modules in docs/architecture/modules.md vs packages").containsAll(modules);
        }
    }

    /** G-03 — SQL inside a module touches only the tables that module owns. */
    @Test
    void modulesOnlyQueryTheirOwnTables() throws IOException {
        Map<String, String> owners = owners();
        Pattern table = Pattern.compile("(?i)\\b(?:from|join|update|into)\\s+([a-z_][a-z0-9_]*)");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String module = MAIN.relativize(f).getName(0).toString();
                if (module.endsWith(".java")) continue;
                for (String sql : stringLiterals(Files.readString(f))) {
                    Matcher m = table.matcher(sql);
                    while (m.find()) {
                        String t = m.group(1).toLowerCase();
                        String owner = owners.get(t);
                        if (owner != null && !owner.equals(module)) {
                            violations.add(MAIN.relativize(f) + ": module '" + module + "' uses table '" + t + "' owned by '" + owner + "'");
                        }
                    }
                }
            }
        }
        assertThat(violations).as("cross-module SQL (go through the owner's service instead)").isEmpty();
    }

    /** G-09 — each business rule names at least one test, and every named test exists. */
    @Test
    void everyBusinessRuleIsProvedByATestThatExists() throws IOException {
        Map<String, String> testSources = new HashMap<>();
        try (Stream<Path> files = Files.walk(TESTS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                testSources.put(f.getFileName().toString().replace(".java", ""), Files.readString(f));
            }
        }
        Pattern row = Pattern.compile("^\\|\\s*(R-\\d+)\\s*\\|.*\\|([^|]*)\\|\\s*$");
        Pattern ref = Pattern.compile("`([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)`");
        Set<String> ids = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (String line : Files.readAllLines(RULES_DOC)) {
            Matcher r = row.matcher(line);
            if (!r.matches()) continue;
            String id = r.group(1);
            if (!ids.add(id)) problems.add(id + " is used twice");
            Matcher m = ref.matcher(r.group(2));
            int found = 0;
            while (m.find()) {
                found++;
                String src = testSources.get(m.group(1));
                if (src == null || !Pattern.compile("void\\s+" + Pattern.quote(m.group(2)) + "\\s*\\(").matcher(src).find()) {
                    problems.add(id + ": test " + m.group(1) + "." + m.group(2) + " does not exist");
                }
            }
            if (found == 0) problems.add(id + " names no test");
        }
        assertThat(ids).as("rules found in docs/product/business-rules.md").isNotEmpty();
        assertThat(problems).isEmpty();
    }

    /**
     * G-12 — name/PAN cleaning helpers live once, in platform ({@code Text.key / nameKey / compact},
     * {@code PanCipher.normalise / normaliseOrNull}). Any copy elsewhere fails.
     */
    @Test
    void noNewCopiesOfSharedHelpers() throws IOException {
        Set<String> knownCopies = Set.of(); // Phase B removed the last copies: keep it empty
        Pattern helper = Pattern.compile("static\\s+String\\s+(nameKey|normalisePan|normalise|key|compact|pan|cleanName)\\s*\\(");
        Set<String> copies = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = MAIN.relativize(f).toString().replace('\\', '/');
                if (rel.startsWith("platform/")) continue;
                if (helper.matcher(Files.readString(f)).find()) copies.add(rel);
            }
        }
        assertThat(copies).as("name/PAN helpers outside platform (use platform.text.Text / PanCipher)").isSubsetOf(knownCopies);
        assertThat(knownCopies).as("copies already removed: take them off the list").isSubsetOf(copies);
    }

    /** G-16 — uploaded workbooks are read only through platform.excel.ExcelFile (one way to find headers and read cells). */
    @Test
    void onlyThePlatformExcelReaderOpensWorkbooks() throws IOException {
        List<String> readers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = MAIN.relativize(f).toString().replace('\\', '/');
                if (rel.startsWith("platform/excel/")) continue;
                String src = Files.readString(f);
                if (src.contains("WorkbookFactory") || Pattern.compile("new\\s+XSSFWorkbook\\s*\\(\\s*\\w").matcher(src).find()) readers.add(rel);
            }
        }
        assertThat(readers).as("read Excel with platform.excel.ExcelFile").isEmpty();
    }

    /**
     * G-17 — every background job outside platform has the one import shape ({@code TransactionalJob}), and every upload
     * endpoint checks its file the one way ({@code UploadedFile.of}).
     */
    @Test
    void importsUseTheSharedPipeline() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = MAIN.relativize(f).toString().replace('\\', '/');
                if (rel.startsWith("platform/")) continue;
                String src = Files.readString(f);
                if (src.contains("implements JobHandler")) problems.add(rel + ": extend TransactionalJob instead of implementing JobHandler");
                if (src.contains("MultipartFile file") && !src.contains("UploadedFile.of(")) problems.add(rel + ": check uploads with UploadedFile.of");
            }
        }
        assertThat(problems).isEmpty();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    /** table → owning module, from the table in docs/architecture/modules.md. */
    private static Map<String, String> owners() throws IOException {
        Pattern row = Pattern.compile("^\\|\\s*`([a-z]+)`\\s*\\|([^|]*)\\|");
        Pattern name = Pattern.compile("`([a-z_][a-z0-9_]*)`");
        Map<String, String> owners = new LinkedHashMap<>();
        for (String line : Files.readAllLines(MODULES_DOC)) {
            Matcher r = row.matcher(line);
            if (!r.find()) continue;
            Matcher n = name.matcher(r.group(2));
            while (n.find()) {
                String previous = owners.put(n.group(1), r.group(1));
                assertThat(previous).as("table " + n.group(1) + " has two owners").isNull();
            }
        }
        return owners;
    }

    private static Set<String> createdTables() throws IOException {
        Pattern create = Pattern.compile("(?i)CREATE\\s+(?:TABLE|VIEW)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)");
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                Matcher m = create.matcher(Files.readString(f));
                while (m.find()) tables.add(m.group(1).toLowerCase());
            }
        }
        return tables;
    }

    /** Text blocks and ordinary string literals of a Java source (where its SQL lives). */
    private static List<String> stringLiterals(String java) {
        List<String> out = new ArrayList<>();
        Matcher blocks = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL).matcher(java);
        while (blocks.find()) out.add(blocks.group(1));
        String rest = blocks.replaceAll(" ");
        Matcher strings = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"").matcher(rest);
        while (strings.find()) out.add(strings.group(1));
        return out;
    }
}
