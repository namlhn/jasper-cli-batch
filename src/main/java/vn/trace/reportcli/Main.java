package vn.trace.reportcli;

import picocli.CommandLine;
import vn.trace.reportcli.cli.RenderCommand;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RenderCommand()).execute(args);
        System.exit(exitCode);
    }
}
