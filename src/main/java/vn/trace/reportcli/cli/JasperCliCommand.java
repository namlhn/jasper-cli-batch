package vn.trace.reportcli.cli;

import picocli.CommandLine.Command;

@Command(
        name = "jasper-cli",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Render JasperReports and sign or validate PDF documents.",
        subcommands = {
                RenderCommand.class,
                SignCommand.class,
                VerifyCommand.class,
                GenerateKeyCommand.class
        }
)
public final class JasperCliCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use --help to list available commands.");
    }
}
