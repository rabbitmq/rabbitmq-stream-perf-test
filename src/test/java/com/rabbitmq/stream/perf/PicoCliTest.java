// Copyright (c) 2025 Broadcom. All Rights Reserved.
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
package com.rabbitmq.stream.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class PicoCliTest {

  @Test
  void parseConfirmLatency() {
    assertThat(exec("").confirmLatency).isFalse();
    assertThat(exec("--confirm-latency").confirmLatency).isTrue();
    assertThat(exec("--confirm-latency true").confirmLatency).isTrue();
    assertThat(exec("--confirm-latency false").confirmLatency).isFalse();
    assertThatThrownBy(() -> exec("--confirm-latency 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseDeleteStreams() {
    assertThat(exec("").deleteStreams).isFalse();
    assertThat(exec("--delete-streams").deleteStreams).isTrue();
    assertThat(exec("--delete-streams true").deleteStreams).isTrue();
    assertThat(exec("--delete-streams false").deleteStreams).isFalse();
    assertThatThrownBy(() -> exec("--delete-streams 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseDynamicBatchSize() {
    assertThat(exec("").dynamicBatch).isTrue();
    assertThat(exec("--dynamic-batch-size").dynamicBatch).isTrue();
    assertThat(exec("--dynamic-batch-size true").dynamicBatch).isTrue();
    assertThat(exec("--dynamic-batch-size false").dynamicBatch).isFalse();
    assertThatThrownBy(() -> exec("--dynamic-batch-size 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseEnvironmentVariables() {
    assertThat(exec("").environmentVariables).isFalse();
    assertThat(exec("--environment-variables").environmentVariables).isTrue();
    assertThat(exec("--environment-variables true").environmentVariables).isTrue();
    assertThat(exec("--environment-variables false").environmentVariables).isFalse();
    assertThatThrownBy(() -> exec("--environment-variables 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseForceReplicaForConsumers() {
    assertThat(exec("").forceReplicaForConsumers).isFalse();
    assertThat(exec("--force-replica-for-consumers").forceReplicaForConsumers).isTrue();
    assertThat(exec("--force-replica-for-consumers true").forceReplicaForConsumers).isTrue();
    assertThat(exec("--force-replica-for-consumers false").forceReplicaForConsumers).isFalse();
    assertThatThrownBy(() -> exec("--force-replica-for-consumers 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseNoDevMode() {
    assertThat(exec("").noDevMode).isFalse();
    assertThat(exec("--no-dev-mode").noDevMode).isTrue();
    assertThat(exec("--no-dev-mode true").noDevMode).isTrue();
    assertThat(exec("--no-dev-mode false").noDevMode).isFalse();
    assertThatThrownBy(() -> exec("--no-dev-mode 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseBatchSizeMetric() {
    assertThat(exec("").includeBatchSizeMetric).isFalse();
    assertThat(exec("--batch-size-metric").includeBatchSizeMetric).isTrue();
    assertThat(exec("--batch-size-metric true").includeBatchSizeMetric).isTrue();
    assertThat(exec("--batch-size-metric false").includeBatchSizeMetric).isFalse();
    assertThatThrownBy(() -> exec("--batch-size-metric 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseMetricsByteRates() {
    assertThat(exec("").includeByteRates).isFalse();
    assertThat(exec("--metrics-byte-rates").includeByteRates).isTrue();
    assertThat(exec("--metrics-byte-rates true").includeByteRates).isTrue();
    assertThat(exec("--metrics-byte-rates false").includeByteRates).isFalse();
    assertThatThrownBy(() -> exec("--metrics-byte-rates 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseLoadBalancer() {
    assertThat(exec("").loadBalancer).isFalse();
    assertThat(exec("--load-balancer").loadBalancer).isTrue();
    assertThat(exec("--load-balancer true").loadBalancer).isTrue();
    assertThat(exec("--load-balancer false").loadBalancer).isFalse();
    assertThatThrownBy(() -> exec("--load-balancer 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseMemoryReport() {
    assertThat(exec("").memoryReport).isFalse();
    assertThat(exec("--memory-report").memoryReport).isTrue();
    assertThat(exec("--memory-report true").memoryReport).isTrue();
    assertThat(exec("--memory-report false").memoryReport).isFalse();
    assertThatThrownBy(() -> exec("--memory-report 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseMetricsCommandLineArguments() {
    assertThat(exec("").metricsCommandLineArguments).isFalse();
    assertThat(exec("--metrics-command-line-arguments").metricsCommandLineArguments).isTrue();
    assertThat(exec("--metrics-command-line-arguments true").metricsCommandLineArguments).isTrue();
    assertThat(exec("--metrics-command-line-arguments false").metricsCommandLineArguments)
        .isFalse();
    assertThatThrownBy(() -> exec("--metrics-command-line-arguments 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseNativeEpoll() {
    assertThat(exec("").nativeEpoll).isFalse();
    assertThat(exec("--native-epoll").nativeEpoll).isTrue();
    assertThat(exec("--native-epoll true").nativeEpoll).isTrue();
    assertThat(exec("--native-epoll false").nativeEpoll).isFalse();
    assertThatThrownBy(() -> exec("--native-epoll 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseSingleActiveConsumer() {
    assertThat(exec("").singleActiveConsumer).isFalse();
    assertThat(exec("--single-active-consumer").singleActiveConsumer).isTrue();
    assertThat(exec("--single-active-consumer true").singleActiveConsumer).isTrue();
    assertThat(exec("--single-active-consumer false").singleActiveConsumer).isFalse();
    assertThatThrownBy(() -> exec("--single-active-consumer 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseSummaryFile() {
    assertThat(exec("").summaryFile).isFalse();
    assertThat(exec("--summary-file").summaryFile).isTrue();
    assertThat(exec("--summary-file true").summaryFile).isTrue();
    assertThat(exec("--summary-file false").summaryFile).isFalse();
    assertThatThrownBy(() -> exec("--summary-file 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseSuperStreams() {
    assertThat(exec("").superStreams).isFalse();
    assertThat(exec("--super-streams").superStreams).isTrue();
    assertThat(exec("--super-streams true").superStreams).isTrue();
    assertThat(exec("--super-streams false").superStreams).isFalse();
    assertThatThrownBy(() -> exec("--super-streams 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseTcpNoDelay() {
    assertThat(exec("").tcpNoDelay).isTrue();
    assertThat(exec("--tcp-no-delay").tcpNoDelay).isTrue();
    assertThat(exec("--tcp-no-delay true").tcpNoDelay).isTrue();
    assertThat(exec("--tcp-no-delay false").tcpNoDelay).isFalse();
    assertThatThrownBy(() -> exec("--tcp-no-delay 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  void parseVersion() {
    assertThat(exec("").version).isFalse();
    assertThat(exec("--version").version).isTrue();
    assertThat(exec("--version true").version).isTrue();
    assertThat(exec("--version false").version).isFalse();
    assertThatThrownBy(() -> exec("--version 10ms"))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  StreamPerfTest exec(String line) {
    StreamPerfTest app = new StreamPerfTest();
    CommandLine commandLine = new CommandLine(app);
    commandLine.parseArgs(line.isBlank() ? new String[0] : args(line));
    return app;
  }

  private static String[] args(String line) {
    return line.split(" ");
  }
}
