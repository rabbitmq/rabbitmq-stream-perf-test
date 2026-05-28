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
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

final class DefaultPrintWriterMetricsFormatter extends BaseMetricsFormatter {

  DefaultPrintWriterMetricsFormatter(
      PrintWriter out,
      Map<String, Counter> counters,
      Map<String, String> countersNamesAndLabels,
      Map<String, AtomicLong> rates) {
    super(out, counters, countersNamesAndLabels, rates);
  }

  @Override
  protected String formatChunkSize(HistogramSupport histogram) {
    return String.format("chunk size %.0f", histogram.takeSnapshot().mean());
  }

  @Override
  protected String formatPublishBatchSize(HistogramSupport histogram) {
    return String.format("publish batch size %.0f", histogram.takeSnapshot().mean());
  }

  @Override
  protected String formatNonByteCounter(String name, long value) {
    return String.format("%s %d msg/s, ", name, value);
  }

  @Override
  protected String formatByteCounter(String name, long value) {
    return formatByteRate(name, value) + ", ";
  }

  @Override
  protected String formatLatency(String name, Timer timer) {
    HistogramSnapshot snapshot = timer.takeSnapshot();

    return String.format(
        name + " median/75th/95th/99th %.0f/%.0f/%.0f/%.0f ms",
        convertDuration.apply(percentile(snapshot, 0.5).value()),
        convertDuration.apply(percentile(snapshot, 0.75).value()),
        convertDuration.apply(percentile(snapshot, 0.95).value()),
        convertDuration.apply(percentile(snapshot, 0.99).value()));
  }

  @Override
  public void header() {}

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
    StringBuilder builder = new StringBuilder();
    builder.append(reportCount).append(", ");
    counters.forEach(
        (meterName, counter) -> {
          long lastValue = lastMetersValues.get(meterName);
          long currentValue = (long) counter.count();
          builder.append(formatCounter.get(meterName).compute(lastValue, currentValue, duration));
          lastMetersValues.put(meterName, currentValue);
        });
    if (confirmLatency != null) {
      builder.append(formatLatency("confirm latency", confirmLatency)).append(", ");
    }
    builder.append(formatLatency("latency", latency)).append(", ");
    if (publishBatchSize != null) {
      builder.append(formatPublishBatchSize(publishBatchSize)).append(", ");
    }
    builder.append(formatChunkSize(chunkSize));
    this.out.println(builder);
    String memoryReport = memoryReportSupplier.get();
    if (!memoryReport.isEmpty()) {
      this.out.println(memoryReport);
    }
  }
}
