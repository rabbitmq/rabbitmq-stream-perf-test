// Copyright (c) 2020-2023 Broadcom. All Rights Reserved.
// The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Stream Performance Testing Tool, is dual-licensed under the
// Mozilla Public License 2.0 ("MPL"), and the Apache License version 2 ("ASL").
// For the MPL, please see LICENSE-MPL-RabbitMQ. For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.
package com.rabbitmq.stream.perf.metrics;

import com.codahale.metrics.ConsoleReporter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultPerformanceMetrics implements PerformanceMetrics {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPerformanceMetrics.class);
  private static final int LATENCY_RECORDING_RATE_LIMIT = 100_000;
  private static final int LATENCY_DOWNSAMPLING = 100;
  private final String metricsPrefix;
  private final CompositeMeterRegistry meterRegistry;
  private final Timer latency, confirmLatency;
  private final boolean summaryFile;
  private final Supplier<String> memoryReportSupplier;
  private final AtomicLong lastConsumedRate = new AtomicLong(0);
  private final AtomicLong latencyCallCount = new AtomicLong(0);
  private final AtomicLong lastConfirmedRate = new AtomicLong(0);
  private final AtomicLong confirmLatencyCallCount = new AtomicLong(0);
  private final Map<String, Counter> counters;
  private final Set<String> allMetrics;
  private final MetricsFormatter formatter;
  private final HistogramSupport publishBatchSize;
  private final HistogramSupport chunkSize;

  private volatile Closeable closingSequence = () -> {};
  private volatile long lastPublishedCount = 0;
  private volatile long lastConsumedCount = 0;
  private volatile long offset;

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public DefaultPerformanceMetrics(
      CompositeMeterRegistry meterRegistry,
      String metricsPrefix,
      boolean summaryFile,
      boolean includeByteRates,
      boolean includePublishBatchSize,
      boolean confirmLatency,
      Supplier<String> memoryReportSupplier,
      MetricsFormatter.MetricsFormat format,
      PrintWriter out) {
    this.summaryFile = summaryFile;
    this.memoryReportSupplier = memoryReportSupplier;
    this.metricsPrefix = metricsPrefix;
    this.meterRegistry = meterRegistry;

    this.latency =
        Timer.builder(metricsPrefix + ".latency")
            .description("message latency")
            .publishPercentiles(0.5, 0.75, 0.95, 0.99)
            .distributionStatisticExpiry(Duration.ofSeconds(1))
            .serviceLevelObjectives()
            .register(meterRegistry);

    String metricPublished = metricsName("published");
    String metricPublishBatchSize = metricsName("publish_batch_size");
    String metricProducerConfirmed = metricsName("producer_confirmed");
    String metricConsumed = metricsName("consumed");
    String metricChunkSize = metricsName("chunk_size");
    String metricLatency = metricsName("latency");
    String metricConfirmLatency = metricsName("confirm_latency");
    String metricWrittenBytes = metricsName("written_bytes");
    String metricReadBytes = metricsName("read_bytes");

    this.allMetrics =
        new HashSet<>(
            Arrays.asList(
                metricPublished,
                metricProducerConfirmed,
                metricConsumed,
                metricChunkSize,
                metricLatency));

    if (includePublishBatchSize) {
      allMetrics.add(metricPublishBatchSize);
      publishBatchSize = meterRegistry.get(metricPublishBatchSize).summary();
    } else {
      publishBatchSize = null;
    }

    if (confirmLatency) {
      allMetrics.add(metricConfirmLatency);
      this.confirmLatency =
          Timer.builder(metricsPrefix + ".confirm_latency")
              .description("publish confirm latency")
              .publishPercentiles(0.5, 0.75, 0.95, 0.99)
              .distributionStatisticExpiry(Duration.ofSeconds(1))
              .serviceLevelObjectives()
              .register(meterRegistry);
    } else {
      this.confirmLatency = null;
    }

    this.chunkSize = meterRegistry.get(metricChunkSize).summary();

    Map<String, String> countersNamesAndLabels = new LinkedHashMap<>();
    countersNamesAndLabels.put(metricPublished, "published");
    countersNamesAndLabels.put(metricProducerConfirmed, "confirmed");
    countersNamesAndLabels.put(metricConsumed, "consumed");
    Map<String, AtomicLong> rates = new HashMap<>();
    rates.put("published", new AtomicLong(0));
    rates.put("confirmed", this.lastConfirmedRate);
    rates.put("consumed", this.lastConsumedRate);

    if (includeByteRates) {
      allMetrics.add(metricWrittenBytes);
      allMetrics.add(metricReadBytes);
      countersNamesAndLabels.put(metricWrittenBytes, "written bytes");
      countersNamesAndLabels.put(metricReadBytes, "read bytes");
    }

    this.counters = new LinkedHashMap<>(countersNamesAndLabels.size());
    countersNamesAndLabels.forEach(
        (key, value) -> counters.put(value, meterRegistry.get(key).counter()));

    if (format == MetricsFormatter.MetricsFormat.DEFAULT) {
      this.formatter =
          new DefaultPrintWriterMetricsFormatter(out, counters, countersNamesAndLabels, rates);
    } else {
      formatter =
          new CompactPrintWriterMetricsFormatter(
              out,
              counters,
              countersNamesAndLabels,
              rates,
              includePublishBatchSize,
              confirmLatency);
    }
  }

  private long getPublishedCount() {
    return (long) this.meterRegistry.get(metricsName("published")).counter().count();
  }

  private long getConsumedCount() {
    return (long) this.meterRegistry.get(metricsName("consumed")).counter().count();
  }

  @Override
  public void start(String description) throws Exception {
    long startTime = System.nanoTime();

    ScheduledExecutorService scheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor();

    Closeable summaryFileClosingSequence =
        maybeSetSummaryFile(description, allMetrics, scheduledExecutorService);

    AtomicInteger reportCount = new AtomicInteger(1);

    AtomicLong lastTick = new AtomicLong(startTime);

    ScheduledFuture<?> consoleReportingTask =
        scheduledExecutorService.scheduleAtFixedRate(
            () -> {
              try {
                if (checkActivity()) {
                  long currentTime = System.nanoTime();
                  Duration duration = Duration.ofNanos(currentTime - lastTick.get());
                  lastTick.set(currentTime);
                  formatter.report(
                      reportCount.get(),
                      duration,
                      counters,
                      latency,
                      confirmLatency,
                      publishBatchSize,
                      chunkSize,
                      memoryReportSupplier);
                }
                reportCount.incrementAndGet();
              } catch (Exception e) {
                LOGGER.warn("Error while computing metrics report: {}", e.getMessage());
              }
            },
            1,
            1,
            TimeUnit.SECONDS);

    this.closingSequence =
        () -> {
          consoleReportingTask.cancel(true);

          summaryFileClosingSequence.close();

          scheduledExecutorService.shutdownNow();

          Duration d = Duration.ofNanos(System.nanoTime() - startTime);
          Duration duration = d.getSeconds() <= 0 ? Duration.ofSeconds(1) : d;
          formatter.summary(
              duration, counters, latency, confirmLatency, publishBatchSize, chunkSize);
        };
    formatter.header();
  }

  private Closeable maybeSetSummaryFile(
      String description, Set<String> allMetrics, ScheduledExecutorService scheduledExecutorService)
      throws IOException {
    Closeable summaryFileClosingSequence;
    if (this.summaryFile) {
      String currentFilename = "stream-perf-test-current.txt";
      String finalFilename =
          "stream-perf-test-"
              + new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date())
              + ".txt";
      Path currentFile = Paths.get(currentFilename);
      if (Files.exists(currentFile)) {
        if (!Files.deleteIfExists(Paths.get(currentFilename))) {
          LOGGER.warn("Could not delete file {}", currentFilename);
        }
      }
      OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(currentFilename));
      PrintStream printStream = new PrintStream(outputStream, false, StandardCharsets.UTF_8);
      if (description != null && !description.trim().isEmpty()) {
        printStream.println(description);
      }

      DropwizardMeterRegistry dropwizardMeterRegistry =
          this.meterRegistry.getRegistries().stream()
              .filter(r -> r instanceof DropwizardMeterRegistry)
              .map(r -> (DropwizardMeterRegistry) r)
              .findAny()
              .orElseGet(MetricsUtils::dropwizardMeterRegistry);

      if (!this.meterRegistry.getRegistries().contains(dropwizardMeterRegistry)) {
        this.meterRegistry.add(dropwizardMeterRegistry);
      }

      ConsoleReporter fileReporter =
          ConsoleReporter.forRegistry(dropwizardMeterRegistry.getDropwizardRegistry())
              .filter((name, metric) -> allMetrics.contains(name))
              .convertRatesTo(TimeUnit.SECONDS)
              .convertDurationsTo(TimeUnit.MILLISECONDS)
              .outputTo(printStream)
              .scheduleOn(scheduledExecutorService)
              .shutdownExecutorOnStop(false)
              .build();
      fileReporter.start(1, TimeUnit.SECONDS);
      summaryFileClosingSequence =
          () -> {
            fileReporter.stop();
            printStream.close();
            Files.move(currentFile, currentFile.resolveSibling(finalFilename));
          };
    } else {
      summaryFileClosingSequence = () -> {};
    }
    return summaryFileClosingSequence;
  }

  boolean checkActivity() {
    long currentPublishedCount = getPublishedCount();
    long currentConsumedCount = getConsumedCount();
    boolean activity =
        this.lastPublishedCount != currentPublishedCount
            || this.lastConsumedCount != currentConsumedCount;
    LOGGER.debug(
        "Activity check: published {} vs {}, consumed {} vs {}, activity {}, offset {}",
        this.lastPublishedCount,
        currentPublishedCount,
        this.lastConsumedCount,
        currentConsumedCount,
        activity,
        this.offset);
    if (activity) {
      this.lastPublishedCount = currentPublishedCount;
      this.lastConsumedCount = currentConsumedCount;
    }
    return activity;
  }

  @Override
  public void latency(long latency, TimeUnit unit) {
    long count = this.latencyCallCount.incrementAndGet();
    if (this.lastConsumedRate.get() < LATENCY_RECORDING_RATE_LIMIT) {
      this.latency.record(latency, unit);
    } else if (count % LATENCY_DOWNSAMPLING == 0) {
      this.latency.record(latency, unit);
    }
  }

  @Override
  public void confirmLatency(long latency, TimeUnit unit) {
    long count = this.confirmLatencyCallCount.incrementAndGet();
    if (this.lastConfirmedRate.get() < LATENCY_RECORDING_RATE_LIMIT) {
      this.confirmLatency.record(latency, unit);
    } else if (count % LATENCY_DOWNSAMPLING == 0) {
      this.confirmLatency.record(latency, unit);
    }
  }

  @Override
  public void offset(long offset) {
    this.offset = offset;
  }

  @Override
  public void close() throws Exception {
    this.closingSequence.close();
  }

  private String metricsName(String name) {
    return this.metricsPrefix + "." + name;
  }
}
