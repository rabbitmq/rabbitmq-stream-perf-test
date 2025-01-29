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

import static java.time.Duration.ofSeconds;

import com.codahale.metrics.MetricRegistry;
import com.rabbitmq.stream.metrics.MicrometerMetricsCollector;
import com.sun.management.OperatingSystemMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

class Utils {

  static final X509TrustManager TRUST_EVERYTHING_TRUST_MANAGER = new TrustEverythingTrustManager();
  static final Function<String, String> OPTION_TO_ENVIRONMENT_VARIABLE =
      option -> {
        if (option.startsWith("--")) {
          return option.replace("--", "").replace('-', '_').toUpperCase(Locale.ENGLISH);
        } else if (option.startsWith("-")) {
          return option.substring(1).replace('-', '_').toUpperCase(Locale.ENGLISH);
        } else {
          return option.replace('-', '_').toUpperCase(Locale.ENGLISH);
        }
      };
  static final Function<String, String> ENVIRONMENT_VARIABLE_PREFIX =
      name -> {
        String prefix = System.getenv("RABBITMQ_STREAM_PERF_TEST_ENV_PREFIX");
        if (prefix == null || prefix.trim().isEmpty()) {
          return name;
        }
        if (prefix.endsWith("_")) {
          return prefix + name;
        } else {
          return prefix + "_" + name;
        }
      };
  static final Function<String, String> ENVIRONMENT_VARIABLE_LOOKUP = name -> System.getenv(name);
  private static final LongSupplier TOTAL_MEMORY_SIZE_SUPPLIER;
  private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

  // this trick avoids a deprecation warning when compiling on Java 14+
  static {
    Method method;
    try {
      // Java 14+
      method = OperatingSystemMXBean.class.getDeclaredMethod("getTotalMemorySize");
    } catch (NoSuchMethodException nsme) {
      try {
        method = OperatingSystemMXBean.class.getDeclaredMethod("getTotalPhysicalMemorySize");
      } catch (Exception e) {
        throw new RuntimeException("Error while computing method to get total memory size");
      }
    }
    Method m = method;
    TOTAL_MEMORY_SIZE_SUPPLIER =
        () -> {
          OperatingSystemMXBean os =
              (OperatingSystemMXBean)
                  java.lang.management.ManagementFactory.getOperatingSystemMXBean();
          try {
            return (long) m.invoke(os);
          } catch (Exception e) {
            throw new RuntimeException("Could not retrieve total memory size", e);
          }
        };
  }

  static void writeLong(byte[] array, long value) {
    // from Guava Longs
    for (int i = 7; i >= 0; i--) {
      array[i] = (byte) (value & 0xffL);
      value >>= 8;
    }
  }

  static long readLong(byte[] array) {
    // from Guava Longs
    return (array[0] & 0xFFL) << 56
        | (array[1] & 0xFFL) << 48
        | (array[2] & 0xFFL) << 40
        | (array[3] & 0xFFL) << 32
        | (array[4] & 0xFFL) << 24
        | (array[5] & 0xFFL) << 16
        | (array[6] & 0xFFL) << 8
        | (array[7] & 0xFFL);
  }

  static final String RANGE_SEPARATOR_1 = "-";
  static final String RANGE_SEPARATOR_2 = "..";

  static List<String> streams(String range, List<String> streams) {
    if (range.contains(RANGE_SEPARATOR_2)) {
      range = range.replace(RANGE_SEPARATOR_2, RANGE_SEPARATOR_1);
    }
    int from, to;
    if (range.contains(RANGE_SEPARATOR_1)) {
      String[] fromTo = range.split(RANGE_SEPARATOR_1);
      from = Integer.parseInt(fromTo[0]);
      to = Integer.parseInt(fromTo[1]) + 1;
    } else {
      int count = Integer.parseInt(range);
      from = 1;
      to = count + 1;
    }
    if (from == 1 && to == 2) {
      return streams;
    } else {
      if (streams.size() != 1) {
        throw new IllegalArgumentException("Enter only 1 stream when --stream-count is specified");
      }
      String format = streams.get(0);
      String streamFormat;
      if (!format.contains("%")) {
        int digits = String.valueOf(to - 1).length();
        streamFormat = format + "-%0" + digits + "d";
      } else {
        streamFormat = format;
      }
      return IntStream.range(from, to)
          .mapToObj(i -> String.format(streamFormat, i))
          .collect(Collectors.toList());
    }
  }

  static String formatByte(double bytes) {
    // based on
    // https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
    if (-1000 < bytes && bytes < 1000) {
      return String.valueOf(bytes);
    }
    CharacterIterator ci = new StringCharacterIterator("kMGTPE");
    while (bytes <= -999_950 || bytes >= 999_950) {
      bytes /= 1000;
      ci.next();
    }
    return String.format("%.1f %cB", bytes / 1000.0, ci.current());
  }

  static long physicalMemory() {
    try {
      return TOTAL_MEMORY_SIZE_SUPPLIER.getAsLong();
    } catch (Throwable e) {
      // we can get NoClassDefFoundError, so we catch from Throwable and below
      LOGGER.warn("Could not get physical memory", e);
      return 0;
    }
  }

  static void assignValuesToCommand(Object command, Function<String, String> optionMapping)
      throws Exception {
    LOGGER.debug("Assigning values to command {}", command.getClass());
    Collection<String> arguments = new ArrayList<>();
    Collection<Field> fieldsToAssign = new ArrayList<>();
    for (Field field : command.getClass().getDeclaredFields()) {
      Option option = field.getAnnotation(Option.class);
      if (option == null) {
        LOGGER.debug("No option annotation for field {}", field.getName());
        continue;
      }
      String longOption =
          Arrays.stream(option.names())
              .sorted(Comparator.comparingInt(String::length).reversed())
              .findFirst()
              .get();
      LOGGER.debug("Looking up new value for option {}", longOption);
      String newValue = optionMapping.apply(longOption);

      LOGGER.debug(
          "New value found for option {} (field {}): {}", longOption, field.getName(), newValue);
      if (newValue == null) {
        continue;
      }
      fieldsToAssign.add(field);
      if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
        if (Boolean.parseBoolean(newValue)) {
          arguments.add(longOption);
        }
      } else {
        arguments.add(longOption + " " + newValue);
      }
    }
    if (fieldsToAssign.size() > 0) {
      Constructor<?> defaultConstructor = command.getClass().getConstructor();
      Object commandBuffer = defaultConstructor.newInstance();
      String argumentsLine = String.join(" ", arguments);
      LOGGER.debug("Arguments line with extra values: {}", argumentsLine);
      String[] args = argumentsLine.split(" ");
      commandBuffer = CommandLine.populateCommand(commandBuffer, args);
      for (Field field : fieldsToAssign) {
        field.setAccessible(true);
        field.set(command, field.get(commandBuffer));
      }
    }
  }

  static CommandSpec buildCommandSpec(Object... commands) {
    Object mainCommand = commands[0];
    Command commandAnnotation = mainCommand.getClass().getAnnotation(Command.class);
    CommandSpec spec = CommandSpec.create();
    spec.name(commandAnnotation.name());
    spec.mixinStandardHelpOptions(commandAnnotation.mixinStandardHelpOptions());
    for (Object command : commands) {
      for (Field f : command.getClass().getDeclaredFields()) {
        Option annotation = f.getAnnotation(Option.class);
        if (annotation == null) {
          continue;
        }
        String name =
            Arrays.stream(annotation.names())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .findFirst()
                .map(OPTION_TO_ENVIRONMENT_VARIABLE::apply)
                .get();
        spec.addOption(
            OptionSpec.builder(name)
                .type(f.getType())
                .description(annotation.description())
                .paramLabel("<" + name.replace("_", "-") + ">")
                .defaultValue(annotation.defaultValue())
                .showDefaultValue(annotation.showDefaultValue())
                .build());
      }
    }

    return spec;
  }

  static String commandLineMetrics(String[] args) {
    Map<String, Boolean> filteredOptions = new HashMap<>();
    filteredOptions.put("--uris", true);
    filteredOptions.put("-u", true);
    filteredOptions.put("--prometheus", false);
    filteredOptions.put("--amqp-uri", true);
    filteredOptions.put("--au", true);
    filteredOptions.put("--metrics-command-line-arguments", false);
    filteredOptions.put("-mcla", false);
    filteredOptions.put("--metrics-tags", true);
    filteredOptions.put("-mt", true);

    Collection<String> filtered = new ArrayList<>();
    Iterator<String> iterator = Arrays.stream(args).iterator();
    while (iterator.hasNext()) {
      String option = iterator.next();
      if (filteredOptions.containsKey(option)) {
        if (filteredOptions.get(option)) {
          iterator.next();
        }
      } else {
        filtered.add(option);
      }
    }
    return String.join(" ", filtered);
  }

  static class NamedThreadFactory implements ThreadFactory {

    private final ThreadFactory backingThreaFactory;

    private final String prefix;

    private final AtomicLong count = new AtomicLong(0);

    public NamedThreadFactory(String prefix) {
      this(Executors.defaultThreadFactory(), prefix);
    }

    public NamedThreadFactory(ThreadFactory backingThreadFactory, String prefix) {
      this.backingThreaFactory = backingThreadFactory;
      this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = this.backingThreaFactory.newThread(r);
      thread.setName(prefix + count.getAndIncrement());
      return thread;
    }
  }

  private static class TrustEverythingTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  static final class PatternNameStrategy implements BiFunction<String, Integer, String> {

    private final String pattern;

    PatternNameStrategy(String pattern) {
      this.pattern = pattern;
    }

    @Override
    public String apply(String stream, Integer index) {
      return String.format(pattern, stream, index);
    }
  }

  static class PerformanceMicrometerMetricsCollector extends MicrometerMetricsCollector {

    private final IntConsumer publisherCallback;

    public PerformanceMicrometerMetricsCollector(
        MeterRegistry registry, String prefix, boolean batchSize) {
      super(registry, prefix);
      if (batchSize) {
        DistributionSummary publishBatchSize =
            DistributionSummary.builder(prefix + ".publish_batch_size")
                .description("publish batch size")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .distributionStatisticExpiry(ofSeconds(1))
                .serviceLevelObjectives()
                .register(registry);
        this.publisherCallback = publishBatchSize::record;
      } else {
        this.publisherCallback = ignored -> {};
      }
    }

    @Override
    protected Counter createChunkCounter(
        MeterRegistry registry, String prefix, Iterable<Tag> tags) {
      return null;
    }

    @Override
    protected DistributionSummary createChunkSizeDistributionSummary(
        MeterRegistry registry, String prefix, Iterable<Tag> tags) {
      return DistributionSummary.builder(prefix + ".chunk_size")
          .tags(tags)
          .description("chunk size")
          .publishPercentiles(0.5, 0.75, 0.95, 0.99)
          .distributionStatisticExpiry(ofSeconds(1))
          .serviceLevelObjectives()
          .register(registry);
    }

    @Override
    public void publish(int count) {
      super.publish(count);
      this.publisherCallback.accept(count);
    }

    @Override
    public void chunk(int entriesCount) {
      this.chunkSize.record(entriesCount);
    }
  }

  static DropwizardMeterRegistry dropwizardMeterRegistry() {
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

  @SuppressWarnings("unchecked")
  static InstanceSynchronization defaultInstanceSynchronization(
      String id, int expectedInstances, String namespace, Duration timeout, PrintWriter out) {
    try {
      Class<InstanceSynchronization> defaultClass =
          (Class<InstanceSynchronization>)
              Class.forName("com.rabbitmq.stream.perf.DefaultInstanceSynchronization");
      Constructor<InstanceSynchronization> constructor =
          defaultClass.getDeclaredConstructor(
              String.class, int.class, String.class, Duration.class, PrintWriter.class);
      return constructor.newInstance(id, expectedInstances, namespace, timeout, out);
    } catch (ClassNotFoundException e) {
      return () -> {
        if (expectedInstances > 1) {
          throw new IllegalArgumentException("Multi-instance synchronization is not available");
        }
      };
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  static int filteringPublishingCycle(int rate) {
    if (rate == 0) {
      return 100_000;
    } else if (rate <= 10) {
      return 10;
    } else {
      return rate / 10;
    }
  }

  static int filteringSubSetSize(int setSize) {
    if (setSize <= 3) {
      return 1;
    } else if (setSize > 10) {
      return (int) (setSize * 0.70);
    } else {
      return setSize - 3;
    }
  }

  static Runnable latencyWorker(Duration latency) {
    if (latency.isZero()) {
      return () -> {};
    } else if (latency.toMillis() >= 1) {
      long latencyInMs = latency.toMillis();
      return () -> latencySleep(latencyInMs);
    } else {
      long latencyInNs = latency.toNanos();
      return () -> latencyBusyWait(latencyInNs);
    }
  }

  private static void latencySleep(long delayInMs) {
    try {
      Thread.sleep(delayInMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void latencyBusyWait(long delayInNs) {
    long start = System.nanoTime();
    while (System.nanoTime() - start < delayInNs)
      ;
  }
}
