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
package com.rabbitmq.stream.perf;

import static com.rabbitmq.stream.perf.Utils.commandLineMetrics;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.of;

import com.rabbitmq.stream.OffsetSpecification;
import com.rabbitmq.stream.perf.Utils.PatternNameStrategy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;

public class UtilsTest {

  static Stream<Arguments> streams() {
    Stream<Arguments> arguments =
        Stream.of(
            of("1", "stream", Collections.singletonList("stream")),
            of("5", "stream", IntStream.range(1, 6).mapToObj(i -> "stream-" + i).collect(toList())),
            of(
                "10",
                "stream",
                IntStream.range(1, 11).mapToObj(i -> format("stream-%02d", i)).collect(toList())),
            of(
                "1-10",
                "stream",
                IntStream.range(1, 11).mapToObj(i -> format("stream-%02d", i)).collect(toList())),
            of(
                "50-500",
                "stream",
                IntStream.range(50, 501).mapToObj(i -> format("stream-%03d", i)).collect(toList())),
            of(
                "1-10",
                "stream-%d",
                IntStream.range(1, 11).mapToObj(i -> format("stream-%d", i)).collect(toList())),
            of(
                "50-500",
                "stream-%d",
                IntStream.range(50, 501).mapToObj(i -> format("stream-%d", i)).collect(toList())));
    return arguments.flatMap(
        arg -> {
          String range = arg.get()[0].toString();
          return range.contains("-")
              ? Stream.of(
                  of(range, arg.get()[1], arg.get()[2]),
                  of(range.replace("-", ".."), arg.get()[1], arg.get()[2]))
              : Stream.of(arg);
        });
  }

  @ParameterizedTest
  @CsvSource({
    "%s-%d,s1-2",
    "stream-%s-consumer-%d,stream-s1-consumer-2",
    "consumer-%2$d-on-stream-%1$s,consumer-2-on-stream-s1"
  })
  void consumerNameStrategy(String pattern, String expected) {
    BiFunction<String, Integer, String> strategy = new PatternNameStrategy(pattern);
    assertThat(strategy.apply("s1", 2)).isEqualTo(expected);
  }

  @Test
  void writeReadLongInByteArray() {
    byte[] array = new byte[8];
    LongStream.of(
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            1,
            128,
            256,
            33_000,
            66_000,
            1_000_000,
            new Random().nextLong())
        .forEach(
            value -> {
              Utils.writeLong(array, value);
              assertThat(Utils.readLong(array)).isEqualTo(value);
            });
  }

  @Test
  void rotateList() {
    List<String> hosts = Arrays.asList("host1", "host2", "host3");
    Collections.rotate(hosts, -1);
    assertThat(hosts).isEqualTo(Arrays.asList("host2", "host3", "host1"));
    Collections.rotate(hosts, -1);
    assertThat(hosts).isEqualTo(Arrays.asList("host3", "host1", "host2"));
  }

  @ParameterizedTest
  @MethodSource
  void streams(String range, String input, List<String> expected) {
    assertThat(Utils.streams(range, Collections.singletonList(input)))
        .hasSameSizeAs(expected)
        .isEqualTo(expected);
  }

  @Test
  void streamsStreamListShouldBeUsedWhenStreamCountIsOne() {
    assertThat(Utils.streams("1", Arrays.asList("stream1", "stream2", "stream3")))
        .hasSize(3)
        .isEqualTo(Arrays.asList("stream1", "stream2", "stream3"));
  }

  @Test
  void streamsSpecifyOnlyOneStreamWhenStreamCountIsSpecified() {
    assertThatThrownBy(() -> Utils.streams("2", Arrays.asList("stream1", "stream2")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void assignValuesToCommand() throws Exception {
    TestCommand command = new TestCommand();
    Map<String, String> mappings = new HashMap<>();
    mappings.put("aaa", "42"); // takes only long options
    mappings.put("b", "true");
    mappings.put("offset", "first");
    Utils.assignValuesToCommand(command, option -> mappings.get(option));
    assertThat(command.a).isEqualTo(42);
    assertThat(command.b).isTrue();
    assertThat(command.c).isFalse();
    assertThat(command.offsetSpecification).isEqualTo(OffsetSpecification.first());
  }

  @Test
  void buildCommandSpec() {
    CommandSpec spec = Utils.buildCommandSpec(new TestCommand());
    assertThat(spec.optionsMap()).hasSize(4).containsKeys("AAA", "B", "C", "OFFSET");
  }

  @ParameterizedTest
  @CsvSource({
    "--uris,URIS",
    "--stream-count,STREAM_COUNT",
    "-sc,SC",
    "--sub-entry-size, SUB_ENTRY_SIZE",
    "-ses,SES"
  })
  void optionToEnvironmentVariable(String option, String envVariable) {
    assertThat(Utils.OPTION_TO_ENVIRONMENT_VARIABLE.apply(option)).isEqualTo(envVariable);
  }

  @Test
  void commandLineMetricsTest() {
    assertThat(
            commandLineMetrics(
                ("--uris rabbitmq-stream://default_user_kdId_cNrxfdolc5V7WJ:K8IYrRjh1NGqdVsaxfFa-r0KR1vGuPHB@cqv2 "
                        + "--prometheus "
                        + "--metrics-command-line-arguments "
                        + "-mt rabbitmq_cluster=cqv2,workload_name=test-new "
                        + "-x 1 -y 2")
                    .split(" ")))
        .isEqualTo("-x 1 -y 2");
    assertThat(
            commandLineMetrics(
                ("-u amqp://default_user_kdId_cNrxfdolc5V7WJ:K8IYrRjh1NGqdVsaxfFa-r0KR1vGuPHB@cqv2 "
                        + "-mcla "
                        + "-mt rabbitmq_cluster=cqv2,workload_name=test-new "
                        + "-x 1 -y 2")
                    .split(" ")))
        .isEqualTo("-x 1 -y 2");
  }

  @ParameterizedTest
  @CsvSource({"0,100000", "100,10", "1000,100", "50,5", "10,10", "11,1", "5,10"})
  void filteringPublishingCycle(int rate, int expected) {
    assertThat(Utils.filteringPublishingCycle(rate)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"3,1", "2,1", "4,1", "7,4", "10,7", "15,10"})
  void filteringSubSetSize(int setSize, int expected) {
    assertThat(Utils.filteringSubSetSize(setSize)).isEqualTo(expected);
  }

  @Command(name = "test-command")
  static class TestCommand {

    @Option(
        names = {"aaa", "a"},
        defaultValue = "10")
    private int a = 10;

    @Option(names = "b", defaultValue = "false")
    private boolean b = false;

    @Option(names = "c", defaultValue = "false")
    private boolean c = false;

    @CommandLine.Option(
        names = {"offset"},
        defaultValue = "next",
        converter = Converters.OffsetSpecificationTypeConverter.class)
    private final OffsetSpecification offsetSpecification = OffsetSpecification.next();

    public TestCommand() {}
  }
}
