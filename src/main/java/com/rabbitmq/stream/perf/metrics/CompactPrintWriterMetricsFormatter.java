// Copyright (c) 2026 Broadcom. All Rights Reserved.
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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

final class CompactPrintWriterMetricsFormatter extends BaseMetricsFormatter {

  private static final String DATE_TIME_FORMAT = "yyyy/MM/dd HH:mm:ss";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);

  private static final String TIME_FORMAT = "%-" + DATE_TIME_FORMAT.length() + "s";
  private static final String RATE_FORMAT = "%15s";
  private static final String LATENCY_FORMAT = "%18s";
  private static final String HISTOGRAM_FORMAT = "%12s";

  private final Map<String, String> countersNamesAndLabels;
  private final boolean batchSize;
  private final boolean confirmLatency;

  CompactPrintWriterMetricsFormatter(
      PrintWriter out,
      Map<String, Counter> counters,
      Map<String, String> countersNamesAndLabels,
      Map<String, AtomicLong> rates,
      boolean batchSize,
      boolean confirmLatency) {
    super(out, counters, countersNamesAndLabels, rates);
    this.countersNamesAndLabels = countersNamesAndLabels;
    this.batchSize = batchSize;
    this.confirmLatency = confirmLatency;
  }

  @Override
  protected String formatChunkSize(HistogramSupport histogram) {
    return String.format(HISTOGRAM_FORMAT, String.format("%.0f", histogram.takeSnapshot().mean()));
  }

  @Override
  protected String formatPublishBatchSize(HistogramSupport histogram) {
    return String.format(HISTOGRAM_FORMAT, String.format("%.0f", histogram.takeSnapshot().mean()));
  }

  @Override
  protected String formatNonByteCounter(String name, long value) {
    return String.format(RATE_FORMAT, value + " msg/s");
  }

  @Override
  protected String formatByteCounter(String name, long value) {
    return String.format(RATE_FORMAT, formatByteRate(null, value));
  }

  @Override
  protected String formatLatency(String name, Timer timer) {
    HistogramSnapshot snapshot = timer.takeSnapshot();
    String latency =
        String.format(
            "%.0f/%.0f/%.0f/%.0f ms",
            convertDuration.apply(percentile(snapshot, 0.5).value()).floatValue(),
            convertDuration.apply(percentile(snapshot, 0.75).value()).floatValue(),
            convertDuration.apply(percentile(snapshot, 0.95).value()).floatValue(),
            convertDuration.apply(percentile(snapshot, 0.99).value()).floatValue());
    if (latency.length() > 18 - 1) { // MAX_ALIGNED_LATENCY
      return " " + latency;
    }
    return String.format(LATENCY_FORMAT, latency);
  }

  @Override
  public void header() {
    StringBuilder builder = new StringBuilder().append(String.format(TIME_FORMAT, "time"));

    for (String label : countersNamesAndLabels.values()) {
      builder.append(String.format(RATE_FORMAT, label));
    }

    if (this.confirmLatency) {
      builder.append(String.format(LATENCY_FORMAT, "confirm latency"));
    }
    builder.append(String.format(LATENCY_FORMAT, "latency"));

    if (this.batchSize) {
      builder.append(String.format(HISTOGRAM_FORMAT, "batch size"));
    }
    builder.append(String.format(HISTOGRAM_FORMAT, "chunk size"));

    out.println(builder);
  }

  @Override
  public void report(
      int reportCount,
      Duration duration,
      Map<String, Counter> counters,
      Timer latency,
      Timer confirmLatency,
      HistogramSupport publishBatchSize,
      HistogramSupport chunkSize,
      Supplier<String> memoryReportSupplier) {
    StringBuilder builder =
        new StringBuilder().append(DATE_TIME_FORMATTER.format(LocalDateTime.now()));

    counters.forEach(
        (meterName, counter) -> {
          long lastValue = lastMetersValues.get(meterName);
          long currentValue = (long) counter.count();
          builder.append(formatCounter.get(meterName).compute(lastValue, currentValue, duration));
          lastMetersValues.put(meterName, currentValue);
        });

    if (this.confirmLatency && confirmLatency != null) {
      builder.append(formatLatency("confirm latency", confirmLatency));
    }
    builder.append(formatLatency("latency", latency));

    if (this.batchSize && publishBatchSize != null) {
      builder.append(formatPublishBatchSize(publishBatchSize));
    }
    builder.append(formatChunkSize(chunkSize));

    this.out.println(builder);

    String memoryReport = memoryReportSupplier.get();
    if (!memoryReport.isEmpty()) {
      this.out.println(memoryReport);
    }
  }
}
