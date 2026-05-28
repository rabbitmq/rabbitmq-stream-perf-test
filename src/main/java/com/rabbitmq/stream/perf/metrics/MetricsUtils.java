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

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

public final class MetricsUtils {

  private MetricsUtils() {}

  public static DropwizardMeterRegistry dropwizardMeterRegistry() {
    DropwizardConfig dropwizardConfig =
        new DropwizardConfig() {
          @Override
          public String prefix() {
            return "";
          }

          @Override
          public String get(String key) {
            return null;
          }
        };
    MetricRegistry metricRegistry = new MetricRegistry();
    DropwizardMeterRegistry dropwizardMeterRegistry =
        new DropwizardMeterRegistry(
            dropwizardConfig,
            metricRegistry,
            HierarchicalNameMapper.DEFAULT,
            io.micrometer.core.instrument.Clock.SYSTEM) {
          @Override
          protected Double nullGaugeValue() {
            return Double.NaN;
          }
        };
    return dropwizardMeterRegistry;
  }
}
