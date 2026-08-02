package vn.trace.reportcli.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import vn.trace.reportcli.model.BatchJob;
import vn.trace.reportcli.render.JasperBatchRenderer;
import vn.trace.reportcli.render.TemplateRegistry;
import vn.trace.reportcli.util.JsonSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "jasper-cli",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Render one or many JasperReports from inline JSON batch jobs."
)
public final class RenderCommand implements Callable<Integer> {
    @Option(names = {"-i", "--input"}, required = true, description = "JSON array containing render jobs")
    private Path input;

    @Option(names = {"-t", "--templates"}, required = true, description = "Directory containing *.yaml configs and JRXML files")
    private Path templates;

    @Option(names = {"-a", "--assets"}, description = "Root directory for local images", defaultValue = ".")
    private Path assets;

    @Option(names = {"-o", "--output"}, description = "Directory for individual PDFs", defaultValue = "output")
    private Path output;

    @Option(names = "--merge", description = "Optional merged PDF path")
    private Path merge;

    @Option(names = "--fail-fast", description = "Stop on first failed item")
    private boolean failFast;

    @Override
    public Integer call() throws Exception {
        List<BatchJob> jobs = JsonSupport.JSON.readValue(
                input.toFile(), new TypeReference<List<BatchJob>>() {});
        if (jobs.isEmpty()) throw new IllegalArgumentException("Input array is empty");

        TemplateRegistry registry = new TemplateRegistry(templates);
        JasperBatchRenderer renderer = new JasperBatchRenderer(registry, assets);
        List<JasperBatchRenderer.RenderResult> successful = new ArrayList<>();
        int failed = 0;

        for (int i = 0; i < jobs.size(); i++) {
            BatchJob job = jobs.get(i);
            try {
                JasperBatchRenderer.RenderResult result = renderer.render(job);
                Path file = renderer.exportSingle(result, output);
                successful.add(result);
                System.out.printf("[%d/%d] OK %s -> %s%n", i + 1, jobs.size(), job.id(), file);
            } catch (Exception e) {
                failed++;
                System.err.printf("[%d/%d] FAILED %s: %s%n",
                        i + 1, jobs.size(), job.id(), errorMessage(e));
                if (failFast) throw e;
            }
        }

        if (merge != null && !successful.isEmpty()) {
            renderer.exportMerged(successful, merge);
            System.out.println("Merged PDF -> " + merge);
        }
        System.out.printf("Completed: total=%d, success=%d, failed=%d%n", jobs.size(), successful.size(), failed);
        return failed == 0 ? 0 : 2;
    }

    private static String errorMessage(Throwable error) {
        StringBuilder message = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            String detail = current.getMessage();
            if (detail != null && !detail.isBlank()
                    && (message.isEmpty() || !message.toString().endsWith(detail))) {
                if (!message.isEmpty()) message.append(": ");
                message.append(detail);
            }
            current = current.getCause();
        }
        return message.isEmpty() ? error.getClass().getSimpleName() : message.toString();
    }
}
