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
import static org.junit.jupiter.params.provider.Arguments.of;

import com.rabbitmq.stream.BackOffDelayPolicy;
import com.rabbitmq.stream.OffsetSpecification;
import com.rabbitmq.stream.compression.Compression;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.net.ssl.SNIHostName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

public class ConvertersTest {

  Converters.CompressionTypeConverter compressionTypeConverter =
      new Converters.CompressionTypeConverter();

  CommandLine.ITypeConverter<OffsetSpecification> offsetSpecificationConverter =
      new Converters.OffsetSpecificationTypeConverter();

  static Stream<Arguments> offsetSpecificationTypeConverterOkArguments() {
    return Stream.of(
        of("", OffsetSpecification.first()),
        of("first", OffsetSpecification.first()),
        of("FIRST", OffsetSpecification.first()),
        of("last", OffsetSpecification.last()),
        of("LAST", OffsetSpecification.last()),
        of("next", OffsetSpecification.next()),
        of("NEXT", OffsetSpecification.next()),
        of("0", OffsetSpecification.offset(0)),
        of("1000", OffsetSpecification.offset(1000)),
        of("9223372036854775817", OffsetSpecification.offset(Long.MAX_VALUE + 10)),
        of("2020-06-03T08:54:57Z", OffsetSpecification.timestamp(1591174497000L)),
        of("2020-06-03T10:54:57+02:00", OffsetSpecification.timestamp(1591174497000L)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"foo", "foo:bar", "0", "1:0", "-1", "1:-2", "1:foo"})
  void backOffDelayPolicyConverterKo(String input) {
    CommandLine.ITypeConverter<BackOffDelayPolicy> converter =
        new Converters.BackOffDelayPolicyTypeConverter();
    assertThatThrownBy(() -> converter.convert(input))
        .isInstanceOf(CommandLine.TypeConversionException.class);
  }

  @ParameterizedTest
  @CsvSource({"5,0:5|1:5|4:5", "5:10,0:5|1:10|4:10"})
  void backOffDelayPolicyConverterOk(String input, String expectations) throws Exception {
    CommandLine.ITypeConverter<BackOffDelayPolicy> converter =
        new Converters.BackOffDelayPolicyTypeConverter();
    BackOffDelayPolicy policy = converter.convert(input);
    Arrays.stream(expectations.split("\\|"))
        .map(s -> s.split(":"))
        .forEach(
            attemptDelay -> {
              int attempt = Integer.parseInt(attemptDelay[0]);
              Duration expectedDelay = Duration.ofSeconds(Long.parseLong(attemptDelay[1]));
              assertThat(policy.delay(attempt)).isEqualTo(expectedDelay);
            });
  }

  @ParameterizedTest
  @MethodSource("offsetSpecificationTypeConverterOkArguments")
  void offsetSpecificationTypeConverterOk(String value, OffsetSpecification expected)
      throws Exception {
    assertThat(offsetSpecificationConverter.convert(value)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"foo", "-1", "2020-06-03"})
  void offsetSpecificationTypeConverterKo(String value) {
    assertThatThrownBy(() -> offsetSpecificationConverter.convert(value))
        .isInstanceOf(CommandLine.TypeConversionException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "none", "gzip", "snappy", "lz4", "zstd",
        "NONE", "GZIP", "SNAPPY", "LZ4", "ZSTD"
      })
  void compressionTypeConverterOk(String value) {
    assertThat(compressionTypeConverter.convert(value))
        .isEqualTo(Compression.valueOf(value.toUpperCase(Locale.ENGLISH)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "foo", "bar"})
  void compressionTypeConverterKo(String value) {
    assertThatThrownBy(() -> compressionTypeConverter.convert(value))
        .isInstanceOf(CommandLine.TypeConversionException.class)
        .hasMessageContaining("Accepted values are none, gzip, snappy, lz4, zstd");
  }

  @Test
  void filterValueSetConverter() throws Exception {
    CommandLine.ITypeConverter<List<String>> converter = new Converters.FilterValueSetConverter();
    assertThat(converter.convert("one")).containsExactly("one");
    assertThat(converter.convert("one,two,three")).containsExactly("one", "two", "three");
    assertThat(converter.convert("1..10")).hasSize(10).contains("1", "2", "10");
    assertThat(converter.convert("5..10")).hasSize(6).contains("5", "6", "10");
  }

  @Test
  void metricsTagsConverter() {
    Converters.MetricsTagsTypeConverter converter = new Converters.MetricsTagsTypeConverter();
    assertThat(converter.convert(null)).isNotNull().isEmpty();
    assertThat(converter.convert("")).isNotNull().isEmpty();
    assertThat(converter.convert("  ")).isNotNull().isEmpty();
    assertThat(converter.convert("env=performance,datacenter=eu"))
        .hasSize(2)
        .contains(tag("env", "performance"))
        .contains(tag("datacenter", "eu"));
    assertThat(converter.convert("args=--queue-args \"x-max-length=100000\""))
        .hasSize(1)
        .contains(tag("args", "--queue-args \"x-max-length=100000\""));
  }

  @Test
  void producerConsumerNameStrategyConverterShouldReturnUuidWhenAskedForUuid() {
    Converters.NameStrategyConverter nameStrategyConverter = new Converters.NameStrategyConverter();
    BiFunction<String, Integer, String> nameStrategy = nameStrategyConverter.convert("uuid");
    String name = nameStrategy.apply("stream", 1);
    assertThat(nameStrategy.apply("stream", 1)).isNotEqualTo(name);
  }

  @Test
  void producerConsumerNameStrategyConverterShouldReturnEmptyStringWhenPatternIsEmptyString() {
    Converters.NameStrategyConverter nameStrategyConverter = new Converters.NameStrategyConverter();
    BiFunction<String, Integer, String> nameStrategy = nameStrategyConverter.convert("");
    assertThat(nameStrategy.apply("stream", 1)).isEmpty();
    assertThat(nameStrategy.apply("stream", 2)).isEmpty();
  }

  @Test
  void producerConsumerNameStrategyConverterShouldReturnPatternStrategyWhenAsked() {
    Converters.NameStrategyConverter nameStrategyConverter = new Converters.NameStrategyConverter();
    BiFunction<String, Integer, String> nameStrategy =
        nameStrategyConverter.convert("stream-%s-consumer-%d");
    assertThat(nameStrategy).isInstanceOf(Utils.PatternNameStrategy.class);
    assertThat(nameStrategy.apply("s1", 2)).isEqualTo("stream-s1-consumer-2");
  }

  @ParameterizedTest
  @ValueSource(strings = {"1..10", "1-10", "1", "10"})
  void rangeConverterOk(String value) {
    assertThat(new Converters.RangeTypeConverter().convert(value)).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0",
        "dummy",
        "-1",
        "-1..2",
        "-1-2",
        "10..1",
        "10-1",
        "1..bb",
        "1-bb",
        "bb..1",
        "bb-1",
        "1..10..15",
        "1-10-15"
      })
  void rangeConverterKo(String value) {
    assertThatThrownBy(() -> new Converters.RangeTypeConverter().convert(value))
        .isInstanceOf(CommandLine.TypeConversionException.class)
        .hasMessageContaining("not valid");
  }

  @Test
  void sniServerNamesConverter() {
    Converters.SniServerNamesConverter converter = new Converters.SniServerNamesConverter();
    assertThat(converter.convert("")).isEmpty();
    assertThat(converter.convert("localhost,dummy"))
        .hasSize(2)
        .contains(new SNIHostName("localhost"))
        .contains(new SNIHostName("dummy"));
  }

  @ParameterizedTest
  @CsvSource({"50ms,50", "0,0", "1s,1000", "10m30s,630000", "PT1M30S,90000"})
  void durationTypeConverterOk(String value, long expectedInMs) {
    Converters.DurationTypeConverter converter = new Converters.DurationTypeConverter();
    Duration duration = converter.convert(value);
    assertThat(duration).isNotNull();
    assertThat(duration).isEqualTo(Duration.ofMillis(expectedInMs));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "abc", "1.5"})
  void durationTypeConverterKo(String value) {
    Converters.DurationTypeConverter converter = new Converters.DurationTypeConverter();
    assertThatThrownBy(() -> converter.convert(value))
        .isInstanceOf(CommandLine.TypeConversionException.class)
        .hasMessageContaining("valid");
  }

  private static Tag tag(String key, String value) {
    return Tag.of(key, value);
  }
}
