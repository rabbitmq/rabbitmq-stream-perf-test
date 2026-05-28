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

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompactPrintWriterMetricsFormatterTest {

  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void testHeader() {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);

    Map<String, Counter> counters = new LinkedHashMap<>();
    Map<String, String> countersNamesAndLabels = new LinkedHashMap<>();
    countersNamesAndLabels.put("published", "sent");
    countersNamesAndLabels.put("confirmed", "confirmed");
    countersNamesAndLabels.put("consumed", "received");

    Map<String, AtomicLong> rates = new HashMap<>();
    rates.put("sent", new AtomicLong());
    rates.put("confirmed", new AtomicLong());
    rates.put("received", new AtomicLong());

    CompactPrintWriterMetricsFormatter formatter =
        new CompactPrintWriterMetricsFormatter(
            printWriter, counters, countersNamesAndLabels, rates, false, true);

    formatter.header();

    String output = stringWriter.toString().trim();
    assertThat(output)
        .contains("time")
        .contains("           sent")
        .contains("      confirmed")
        .contains("       received")
        .contains("   confirm latency")
        .contains("           latency")
        .contains("  chunk size");
  }

  @Test
  void testReportFormatting() {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);

    Counter publishedCounter = meterRegistry.counter("published");
    Counter confirmedCounter = meterRegistry.counter("confirmed");
    Counter consumedCounter = meterRegistry.counter("consumed");

    publishedCounter.increment(1000);
    confirmedCounter.increment(975);
    consumedCounter.increment(972);

    Map<String, Counter> counters = new LinkedHashMap<>();
    counters.put("sent", publishedCounter);
    counters.put("confirmed", confirmedCounter);
    counters.put("received", consumedCounter);

    Map<String, String> countersNamesAndLabels = new LinkedHashMap<>();
    countersNamesAndLabels.put("published", "sent");
    countersNamesAndLabels.put("confirmed", "confirmed");
    countersNamesAndLabels.put("consumed", "received");

    Map<String, AtomicLong> rates = new HashMap<>();
    rates.put("sent", new AtomicLong());
    rates.put("confirmed", new AtomicLong());
    rates.put("received", new AtomicLong());

    CompactPrintWriterMetricsFormatter formatter =
        new CompactPrintWriterMetricsFormatter(
            printWriter, counters, countersNamesAndLabels, rates, false, false);

    // Now increment counters again to simulate new messages
    publishedCounter.increment(1000);
    confirmedCounter.increment(975);
    consumedCounter.increment(972);

    Timer latencyTimer =
        Timer.builder("latency").publishPercentiles(0.5, 0.75, 0.95, 0.99).register(meterRegistry);

    for (int i = 0; i < 100; i++) {
      latencyTimer.record(Duration.ofNanos(2_000_000));
      latencyTimer.record(Duration.ofNanos(3_000_000));
      latencyTimer.record(Duration.ofNanos(12_000_000));
      latencyTimer.record(Duration.ofNanos(28_000_000));
    }

    DistributionSummary chunkSize = meterRegistry.summary("chunk.size");
    chunkSize.record(2.0);
    chunkSize.record(2.0);

    formatter.report(
        1, Duration.ofSeconds(1), counters, latencyTimer, null, null, chunkSize, () -> "");

    String output = stringWriter.toString().trim();
    assertThat(output)
        .matches(".*\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}.*")
        .contains("     1000 msg/s")
        .contains("      975 msg/s")
        .contains("      972 msg/s")
        .contains("     3/12/28/28 ms")
        .contains("           2");
  }

  @Test
  void testSummaryFormatting() {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);

    Counter publishedCounter = meterRegistry.counter("published");
    Counter confirmedCounter = meterRegistry.counter("confirmed");
    Counter consumedCounter = meterRegistry.counter("consumed");

    publishedCounter.increment(5130);
    confirmedCounter.increment(5125);
    consumedCounter.increment(5120);

    Map<String, Counter> counters = new LinkedHashMap<>();
    counters.put("sent", publishedCounter);
    counters.put("confirmed", confirmedCounter);
    counters.put("received", consumedCounter);

    Map<String, String> countersNamesAndLabels = new LinkedHashMap<>();
    countersNamesAndLabels.put("published", "sent");
    countersNamesAndLabels.put("confirmed", "confirmed");
    countersNamesAndLabels.put("consumed", "received");

    Map<String, AtomicLong> rates = new HashMap<>();
    rates.put("sent", new AtomicLong());
    rates.put("confirmed", new AtomicLong());
    rates.put("received", new AtomicLong());

    CompactPrintWriterMetricsFormatter formatter =
        new CompactPrintWriterMetricsFormatter(
            printWriter, counters, countersNamesAndLabels, rates, false, false);

    Timer latencyTimer =
        Timer.builder("latency").publishPercentiles(0.5, 0.75, 0.95, 0.99).register(meterRegistry);

    for (int i = 0; i < 100; i++) {
      latencyTimer.record(Duration.ofNanos(2_000_000));
    }

    DistributionSummary chunkSize = meterRegistry.summary("chunk.size");
    chunkSize.record(1.0);

    formatter.summary(Duration.ofSeconds(5), counters, latencyTimer, null, null, chunkSize);

    String output = stringWriter.toString();
    assertThat(output)
        .contains("Summary: ")
        .contains("sent 1026 msg/s")
        .contains("confirmed 1025 msg/s")
        .contains("received 1024 msg/s")
        .contains("latency 95th 2 ms")
        .contains("chunk size 1");
  }
}
