package vn.trace.reportcli.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import vn.trace.reportcli.Main;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliCompatibilityTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    @Test
    void supportsLegacyAndExplicitRenderSyntax(@TempDir Path tempDir) {
        Path legacyOutput = tempDir.resolve("legacy");
        Path explicitOutput = tempDir.resolve("explicit");

        assertEquals(0, Main.execute(renderArguments(legacyOutput)));

        String[] explicitArguments = new String[renderArguments(explicitOutput).length + 1];
        explicitArguments[0] = "render";
        System.arraycopy(renderArguments(explicitOutput), 0,
                explicitArguments, 1, explicitArguments.length - 1);
        assertEquals(0, Main.execute(explicitArguments));

        assertTrue(Files.isRegularFile(legacyOutput.resolve("cert-nguyen-van-an.pdf")));
        assertTrue(Files.isRegularFile(explicitOutput.resolve("cert-nguyen-van-an.pdf")));
    }

    @Test
    void exposesSigningSubcommands() {
        CommandLine commandLine = new CommandLine(new JasperCliCommand());
        assertTrue(commandLine.getSubcommands().keySet()
                .containsAll(java.util.Set.of("render", "sign", "verify", "generate-key")));
        assertEquals(0, commandLine.execute("sign", "--help"));
        assertEquals(0, commandLine.execute("verify", "--help"));
        assertEquals(0, commandLine.execute("generate-key", "--help"));
    }

    private static String[] renderArguments(Path output) {
        return new String[]{
                "--input", PROJECT_ROOT.resolve("examples/batch-inline.json").toString(),
                "--templates", PROJECT_ROOT.resolve("config/templates").toString(),
                "--assets", PROJECT_ROOT.toString(),
                "--output", output.toString()
        };
    }
}
