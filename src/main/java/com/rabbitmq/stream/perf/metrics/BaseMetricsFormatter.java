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
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.io.PrintWriter;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

abstract class BaseMetricsFormatter implements MetricsFormatter {

  protected final PrintWriter out;
  protected final Function<Number, Number> convertDuration = in -> in.doubleValue() / 1_000_000.0;
  protected final Map<String, Long> lastMetersValues;
  protected final Map<String, FormatCallback> formatCounter;

  BaseMetricsFormatter(
      PrintWriter out,
      Map<String, Counter> counters,
      Map<String, String> countersNamesAndLabels,
      Map<String, AtomicLong> rates) {
    this.out = out;

    this.lastMetersValues = new ConcurrentHashMap<>(counters.size());
    counters.forEach((key, value) -> lastMetersValues.put(key, (long) value.count()));

    this.formatCounter = new HashMap<>();
    countersNamesAndLabels.entrySet().stream()
        .filter(entry -> !entry.getKey().contains("bytes"))
        .forEach(
            entry -> {
              formatCounter.put(
                  entry.getValue(),
                  (lastValue, currentValue, duration) -> {
                    long rate = 1000 * (currentValue - lastValue) / duration.toMillis();
                    rates.get(entry.getValue()).set(rate);
                    return formatNonByteCounter(entry.getValue(), rate);
                  });
            });

    countersNamesAndLabels.entrySet().stream()
        .filter(entry -> entry.getKey().contains("bytes"))
        .forEach(
            entry -> {
              formatCounter.put(
                  entry.getValue(),
                  (lastValue, currentValue, duration) -> {
                    long rate = 1000 * (currentValue - lastValue) / duration.toMillis();
                    return formatByteCounter(entry.getValue(), rate);
                  });
            });
  }

  protected abstract String formatNonByteCounter(String name, long value);

  protected abstract String formatByteCounter(String name, long value);

  protected abstract String formatLatency(String name, Timer timer);

  protected abstract String formatChunkSize(HistogramSupport histogram);

  protected abstract String formatPublishBatchSize(HistogramSupport histogram);

  @Override
  public void summary(
      Duration duration,
      Map<String, Counter> counters,
      Timer latency,
      Timer confirmLatency,
      HistogramSupport publishBatchSize,
      HistogramSupport chunkSize) {
    Function<Map.Entry<String, Counter>, String> formatMeterSummary =
        entry -> {
          if (entry.getKey().contains("bytes")) {
            return formatByteRate(
                    entry.getKey(), 1000 * entry.getValue().count() / duration.toMillis())
                + ", ";
          } else {
            return String.format(
                "%s %d msg/s, ",
                entry.getKey(), (long) (1000 * entry.getValue().count() / duration.toMillis()));
          }
        };

    BiFunction<String, HistogramSupport, String> formatLatencySummary =
        (name, histogram) ->
            String.format(
                name + " 95th %.0f ms",
                convertDuration.apply(percentile(histogram.takeSnapshot(), 0.95).value()));

    StringBuilder builder = new StringBuilder("Summary: ");
    counters.entrySet().forEach(entry -> builder.append(formatMeterSummary.apply(entry)));
    if (confirmLatency != null) {
      builder.append(formatLatencySummary.apply("confirm latency", confirmLatency)).append(", ");
    }
    builder.append(formatLatencySummary.apply("latency", latency)).append(", ");
    if (publishBatchSize != null) {
      builder.append(formatPublishBatchSize(publishBatchSize)).append(", ");
    }
    builder.append(String.format("chunk size %.0f", chunkSize.takeSnapshot().mean()));
    this.out.println();
    this.out.println(builder);
  }

  protected static String formatByteRate(String label, double bytes) {
    // based on
    // https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
    if (-1000 < bytes && bytes < 1000) {
      return bytes + " B/s";
    }
    CharacterIterator ci = new StringCharacterIterator("kMGTPE");
    while (bytes <= -999_950 || bytes >= 999_950) {
      bytes /= 1000;
      ci.next();
    }
    if (label == null) {
      return String.format("%.1f %cB/s", bytes / 1000.0, ci.current());
    } else {
      return String.format("%s %.1f %cB/s", label, bytes / 1000.0, ci.current());
    }
  }

  protected static ValueAtPercentile percentile(HistogramSnapshot snapshot, double expected) {
    for (ValueAtPercentile percentile : snapshot.percentileValues()) {
      if (percentile.percentile() == expected) {
        return percentile;
      }
    }
    return null;
  }

  interface FormatCallback {

    String compute(long lastValue, long currentValue, Duration duration);
  }
}
