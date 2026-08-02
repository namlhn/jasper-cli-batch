package vn.trace.reportcli;

import picocli.CommandLine;
import vn.trace.reportcli.cli.JasperCliCommand;
import vn.trace.reportcli.cli.RenderCommand;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        int exitCode = execute(args);
        System.exit(exitCode);
    }

    public static int execute(String... args) {
        if (args.length == 0 || args[0].startsWith("-")) {
            return new CommandLine(new RenderCommand()).execute(args);
        }
        return new CommandLine(new JasperCliCommand()).execute(args);
    }
}
