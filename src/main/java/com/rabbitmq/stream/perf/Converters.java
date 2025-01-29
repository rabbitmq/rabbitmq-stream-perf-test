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

import static com.rabbitmq.stream.perf.Utils.RANGE_SEPARATOR_1;
import static com.rabbitmq.stream.perf.Utils.RANGE_SEPARATOR_2;
import static java.time.Duration.ofSeconds;

import com.rabbitmq.stream.BackOffDelayPolicy;
import com.rabbitmq.stream.ByteCapacity;
import com.rabbitmq.stream.OffsetSpecification;
import com.rabbitmq.stream.StreamCreator;
import com.rabbitmq.stream.compression.Compression;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import picocli.CommandLine;

final class Converters {

  private Converters() {}

  static void typeConversionException(String message) {
    throw new CommandLine.TypeConversionException(message);
  }

  static class BackOffDelayPolicyTypeConverter
      implements CommandLine.ITypeConverter<BackOffDelayPolicy> {

    @Override
    public BackOffDelayPolicy convert(String input) {
      if (input == null || input.trim().isEmpty()) {
        typeConversionException("Value for back-off delay policy cannot be empty");
      }
      String[] values = input.split(":");
      if (values.length != 1 && values.length != 2) {
        typeConversionException("Invalid value for back-off delay policy: " + input);
      }
      int firstAttempt = 0, nextAttempts = 0;
      try {
        firstAttempt = Integer.parseInt(values[0]);
        if (firstAttempt <= 0) {
          throw new IllegalArgumentException();
        }
      } catch (Exception e) {
        typeConversionException("Invalid value for back-off delay policy: " + input);
      }
      if (values.length == 2) {
        try {
          nextAttempts = Integer.parseInt(values[1]);
          if (nextAttempts <= 0) {
            throw new IllegalArgumentException();
          }
        } catch (Exception e) {
          typeConversionException("Invalid value for back-off delay policy: " + input);
        }
      } else {
        nextAttempts = firstAttempt;
      }
      return BackOffDelayPolicy.fixedWithInitialDelay(
          ofSeconds(firstAttempt), ofSeconds(nextAttempts));
    }
  }

  static class CompressionTypeConverter implements CommandLine.ITypeConverter<Compression> {

    @Override
    public Compression convert(String input) {
      try {
        return Compression.valueOf(input.toUpperCase(Locale.ENGLISH));
      } catch (Exception e) {
        throw new CommandLine.TypeConversionException(
            input
                + " is not a valid compression value. "
                + "Accepted values are "
                + Arrays.stream(Compression.values())
                    .map(Compression::name)
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(", "))
                + ".");
      }
    }
  }

  private abstract static class RangeIntegerTypeConverter
      implements CommandLine.ITypeConverter<Integer> {

    private final int min, max;

    private RangeIntegerTypeConverter(int min, int max) {
      this.min = min;
      this.max = max;
    }

    @Override
    public Integer convert(String input) {
      int value = Integer.parseInt(input);
      if (value < this.min || value > this.max) {
        throw new CommandLine.TypeConversionException(
            input + " must an integer between " + this.min + " and " + this.max);
      }
      return value;
    }
  }

  static class OneTo255RangeIntegerTypeConverter extends RangeIntegerTypeConverter {

    OneTo255RangeIntegerTypeConverter() {
      super(1, 255);
    }
  }

  static class NotNegativeIntegerTypeConverter implements CommandLine.ITypeConverter<Integer> {

    @Override
    public Integer convert(String input) {
      try {
        Integer value = Integer.valueOf(input);
        if (value < 0) {
          throw new IllegalArgumentException();
        }
        return value;
      } catch (Exception e) {
        throw new CommandLine.TypeConversionException(input + " is not a non-negative integer");
      }
    }
  }

  static class ByteCapacityTypeConverter implements CommandLine.ITypeConverter<ByteCapacity> {

    @Override
    public ByteCapacity convert(String value) {
      try {
        return ByteCapacity.from(value);
      } catch (IllegalArgumentException e) {
        throw new CommandLine.TypeConversionException(
            "'" + value + "' is not valid, valid example values: 100gb, 50mb");
      }
    }
  }

  static class MetricsTagsTypeConverter implements CommandLine.ITypeConverter<Collection<Tag>> {

    @Override
    public Collection<Tag> convert(String value) {
      if (value == null || value.trim().isEmpty()) {
        return Collections.emptyList();
      } else {
        try {
          Collection<Tag> tags = new ArrayList<>();
          for (String tag : value.split(",")) {
            String[] keyValue = tag.split("=", 2);
            tags.add(Tag.of(keyValue[0], keyValue[1]));
          }
          return tags;
        } catch (Exception e) {
          throw new CommandLine.TypeConversionException(
              String.format("'%s' is not valid, use key/value pairs separated by commas"));
        }
      }
    }
  }

  static class NameStrategyConverter
      implements CommandLine.ITypeConverter<BiFunction<String, Integer, String>> {

    @Override
    public BiFunction<String, Integer, String> convert(String input) {
      if ("uuid".equals(input)) {
        return (stream, index) -> UUID.randomUUID().toString();
      } else {
        return new Utils.PatternNameStrategy(input);
      }
    }
  }

  static class FilterValueSetConverter implements CommandLine.ITypeConverter<List<String>> {

    @Override
    public List<String> convert(String value) {
      if (value == null || value.trim().isEmpty()) {
        return Collections.emptyList();
      }
      if (value.contains("..")) {
        String[] range = value.split("\\.\\.");
        String errorMessage = "'" + value + "' is not valid, valid example values: 1..10, 1..20";
        if (range.length != 2) {
          throw new CommandLine.TypeConversionException(errorMessage);
        }
        int start, end;
        try {
          start = Integer.parseInt(range[0]);
          end = Integer.parseInt(range[1]) + 1;
          return IntStream.range(start, end).mapToObj(String::valueOf).collect(Collectors.toList());
        } catch (NumberFormatException e) {
          throw new CommandLine.TypeConversionException(errorMessage);
        }
      } else {
        return Arrays.stream(value.split(",")).collect(Collectors.toList());
      }
    }
  }

  static class SniServerNamesConverter implements CommandLine.ITypeConverter<List<SNIServerName>> {

    @Override
    public List<SNIServerName> convert(String value) {
      if (value == null || value.trim().isEmpty()) {
        return Collections.emptyList();
      } else {
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .map(SNIHostName::new)
            .collect(Collectors.toList());
      }
    }
  }

  static class RangeTypeConverter implements CommandLine.ITypeConverter<String> {

    @Override
    public String convert(String input) {
      String value;
      if (input.contains(RANGE_SEPARATOR_2)) {
        value = input.replace(RANGE_SEPARATOR_2, RANGE_SEPARATOR_1);
      } else {
        value = input;
      }
      if (value.contains(RANGE_SEPARATOR_1)) {
        String[] fromTo = value.split(RANGE_SEPARATOR_1);
        if (fromTo.length != 2) {
          throwConversionException("'%s' is not valid, valid examples values: 10, 1-10", input);
        }
        Arrays.stream(fromTo)
            .forEach(
                v -> {
                  try {
                    int i = Integer.parseInt(v);
                    if (i <= 0) {
                      throwConversionException(
                          "'%s' is not valid, the value must be a positive integer", v);
                    }
                  } catch (NumberFormatException e) {
                    throwConversionException(
                        "'%s' is not valid, the value must be a positive integer", v);
                  }
                });
        int from = Integer.parseInt(fromTo[0]);
        int to = Integer.parseInt(fromTo[1]);
        if (from >= to) {
          throwConversionException("'%s' is not valid, valid examples values: 10, 1-10", input);
        }
      } else {
        try {
          int count = Integer.parseInt(value);
          if (count <= 0) {
            throwConversionException(
                "'%s' is not valid, the value must be a positive integer", input);
          }
        } catch (NumberFormatException e) {
          throwConversionException("'%s' is not valid, valid example values: 10, 1-10", input);
        }
      }
      return input;
    }
  }

  static class DurationTypeConverter implements CommandLine.ITypeConverter<Duration> {

    @Override
    public Duration convert(String value) {
      try {
        Duration duration = Duration.parse(value);
        if (duration.isNegative() || duration.isZero()) {
          throw new CommandLine.TypeConversionException(
              "'" + value + "' is not valid, it must be positive");
        }
        return duration;
      } catch (DateTimeParseException e) {
        throw new CommandLine.TypeConversionException(
            "'" + value + "' is not valid, valid example values: PT15M, PT10H");
      }
    }
  }

  static class MicroSecondsToDurationTypeConverter implements CommandLine.ITypeConverter<Duration> {

    @Override
    public Duration convert(String value) {
      try {
        Duration duration = Duration.ofNanos(Long.parseLong(value) * 1_000);
        if (duration.isNegative()) {
          throw new CommandLine.TypeConversionException(
              "'" + value + "' is not valid, it must be greater than or equal to 0");
        }
        return duration;
      } catch (NumberFormatException e) {
        throw new CommandLine.TypeConversionException("'" + value + "' is not a valid number");
      }
    }
  }

  static class LeaderLocatorTypeConverter
      implements CommandLine.ITypeConverter<StreamCreator.LeaderLocator> {

    @Override
    public StreamCreator.LeaderLocator convert(String value) {
      try {
        return StreamCreator.LeaderLocator.from(value);
      } catch (Exception e) {
        throw new CommandLine.TypeConversionException(
            "'"
                + value
                + "' is not valid, possible values: "
                + Arrays.stream(StreamCreator.LeaderLocator.values())
                    .map(ll -> ll.value())
                    .collect(Collectors.joining(", ")));
      }
    }
  }

  static class OffsetSpecificationTypeConverter
      implements CommandLine.ITypeConverter<OffsetSpecification> {

    private static final Map<String, OffsetSpecification> SPECS =
        Collections.unmodifiableMap(
            new HashMap<String, OffsetSpecification>() {
              {
                put("first", OffsetSpecification.first());
                put("last", OffsetSpecification.last());
                put("next", OffsetSpecification.next());
              }
            });

    @Override
    public OffsetSpecification convert(String value) throws Exception {
      if (value == null || value.trim().isEmpty()) {
        return OffsetSpecification.first();
      }

      if (SPECS.containsKey(value.toLowerCase())) {
        return SPECS.get(value.toLowerCase());
      }

      try {
        long offset = Long.parseUnsignedLong(value);
        return OffsetSpecification.offset(offset);
      } catch (NumberFormatException e) {
        // trying next
      }

      try {
        TemporalAccessor accessor = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value);
        return OffsetSpecification.timestamp(Instant.from(accessor).toEpochMilli());
      } catch (DateTimeParseException e) {
        throw new CommandLine.TypeConversionException(
            "'"
                + value
                + "' is not a valid offset value, valid values are 'first', 'last', 'next', "
                + "an unsigned long, or an ISO 8601 formatted timestamp (eg. 2020-06-03T07:45:54Z)");
      }
    }
  }

  static class PositiveIntegerTypeConverter implements CommandLine.ITypeConverter<Integer> {

    @Override
    public Integer convert(String input) {
      try {
        Integer value = Integer.valueOf(input);
        if (value <= 0) {
          throw new IllegalArgumentException();
        }
        return value;
      } catch (Exception e) {
        throw new CommandLine.TypeConversionException(input + " is not a positive integer");
      }
    }
  }

  static class GreaterThanOrEqualToZeroIntegerTypeConverter
      implements CommandLine.ITypeConverter<Integer> {

    @Override
    public Integer convert(String input) {
      try {
        Integer value = Integer.valueOf(input);
        if (value < 0) {
          throw new IllegalArgumentException();
        }
        return value;
      } catch (Exception e) {
        throw new CommandLine.TypeConversionException(input + " is not greater than or equal to 0");
      }
    }
  }

  private static void throwConversionException(String format, String... arguments) {
    throw new CommandLine.TypeConversionException(String.format(format, (Object[]) arguments));
  }
}
