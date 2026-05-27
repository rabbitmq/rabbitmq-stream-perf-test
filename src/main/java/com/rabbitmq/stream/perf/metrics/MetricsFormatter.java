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
import io.micrometer.core.instrument.distribution.HistogramSupport;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

public interface MetricsFormatter {

  void header();

  void report(
      int reportCount,
      Duration duration,
      Map<String, Counter> counters,
      Timer latency,
      Timer confirmLatency,
      HistogramSupport publishBatchSize,
      HistogramSupport chunkSize,
      Supplier<String> memoryReportSupplier);

  void summary(
      Duration duration,
      Map<String, Counter> counters,
      Timer latency,
      Timer confirmLatency,
      HistogramSupport publishBatchSize,
      HistogramSupport chunkSize);

  enum MetricsFormat {
    DEFAULT,
    COMPACT
  }
}
