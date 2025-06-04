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

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.rabbitmq.stream.impl.Client;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TestUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);
  private static final Duration DEFAULT_CONDITION_TIMEOUT = Duration.ofSeconds(10);

  private TestUtils() {}

  public static Duration waitAtMost(CallableBooleanSupplier condition) throws Exception {
    return waitAtMost(DEFAULT_CONDITION_TIMEOUT, condition, null);
  }

  public static Duration waitAtMost(CallableBooleanSupplier condition, Supplier<String> message)
      throws Exception {
    return waitAtMost(DEFAULT_CONDITION_TIMEOUT, condition, message);
  }

  public static Duration waitAtMost(Duration timeout, CallableBooleanSupplier condition)
      throws Exception {
    return waitAtMost(timeout, condition, null);
  }

  public static Duration waitAtMost(int timeoutInSeconds, CallableBooleanSupplier condition)
      throws Exception {
    return waitAtMost(timeoutInSeconds, condition, null);
  }

  public static Duration waitAtMost(
      int timeoutInSeconds, CallableBooleanSupplier condition, Supplier<String> message)
      throws Exception {
    return waitAtMost(Duration.ofSeconds(timeoutInSeconds), condition, message);
  }

  public static Duration waitAtMost(
      Duration timeout, CallableBooleanSupplier condition, Supplier<String> message)
      throws Exception {
    if (condition.getAsBoolean()) {
      return Duration.ZERO;
    }
    int waitTime = 100;
    int waitedTime = 0;
    int timeoutInMs = (int) timeout.toMillis();
    Exception exception = null;
    while (waitedTime <= timeoutInMs) {
      Thread.sleep(waitTime);
      waitedTime += waitTime;
      try {
        if (condition.getAsBoolean()) {
          return Duration.ofMillis(waitedTime);
        }
        exception = null;
      } catch (Exception e) {
        exception = e;
      }
    }
    String msg;
    if (message == null) {
      msg = "Waited " + timeout.getSeconds() + " second(s), condition never got true";
    } else {
      msg = "Waited " + timeout.getSeconds() + " second(s), " + message.get();
    }
    if (exception == null) {
      fail(msg);
    } else {
      fail(msg, exception);
    }
    return Duration.ofMillis(waitedTime);
  }

  public interface CallableConsumer<T> {

    void accept(T t) throws Exception;
  }

  static <T> java.util.function.Consumer<T> wrap(CallableConsumer<T> action) {
    return t -> {
      try {
        action.accept(t);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  @FunctionalInterface
  public interface CallableBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }

  /**
   * https://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
   */
  static int versionCompare(String str1, String str2) {
    String[] vals1 = str1.split("\\.");
    String[] vals2 = str2.split("\\.");
    int i = 0;
    // set index to first non-equal ordinal or length of shortest version string
    while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
      i++;
    }
    // compare first non-equal ordinal number
    if (i < vals1.length && i < vals2.length) {
      int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
      return Integer.signum(diff);
    }
    // the strings are equal or one string is a substring of the other
    // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
    return Integer.signum(vals1.length - vals2.length);
  }

  static String currentVersion(String currentVersion) {
    // versions built from source: 3.7.0+rc.1.4.gedc5d96
    if (currentVersion.contains("+")) {
      currentVersion = currentVersion.substring(0, currentVersion.indexOf("+"));
    }
    // alpha (snapshot) versions: 3.7.0~alpha.449-1
    if (currentVersion.contains("~")) {
      currentVersion = currentVersion.substring(0, currentVersion.indexOf("~"));
    }
    // alpha (snapshot) versions: 3.7.1-alpha.40
    if (currentVersion.contains("-")) {
      currentVersion = currentVersion.substring(0, currentVersion.indexOf("-"));
    }
    return currentVersion;
  }

  static boolean atLeastVersion(String expectedVersion, String currentVersion) {
    if (currentVersion.contains("alpha-stream")) {
      return true;
    }
    try {
      currentVersion = currentVersion(currentVersion);
      return "0.0.0".equals(currentVersion) || versionCompare(currentVersion, expectedVersion) >= 0;
    } catch (RuntimeException e) {
      LoggerFactory.getLogger(TestUtils.class)
          .warn("Unable to parse broker version {}", currentVersion, e);
      throw e;
    }
  }

  public static String streamName(TestInfo info) {
    return streamName(info.getTestClass().get(), info.getTestMethod().get());
  }

  private static String streamName(ExtensionContext context) {
    return streamName(context.getTestInstance().get().getClass(), context.getTestMethod().get());
  }

  private static String streamName(Class<?> testClass, Method testMethod) {
    String uuid = UUID.randomUUID().toString();
    return format(
        "%s_%s%s",
        testClass.getSimpleName(), testMethod.getName(), uuid.substring(uuid.length() / 2));
  }

  public static class ClientFactory {

    private final EventLoopGroup eventLoopGroup;
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();

    public ClientFactory(EventLoopGroup eventLoopGroup) {
      this.eventLoopGroup = eventLoopGroup;
    }

    public Client get() {
      return get(new Client.ClientParameters());
    }

    public Client get(Client.ClientParameters parameters) {
      Client client = new Client(parameters.eventLoopGroup(eventLoopGroup));
      clients.add(client);
      return client;
    }

    private void close() {
      for (Client c : clients) {
        c.close();
      }
    }
  }

  public static class StreamTestInfrastructureExtension
      implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(StreamTestInfrastructureExtension.class);

    private static ExtensionContext.Store store(ExtensionContext extensionContext) {
      return extensionContext.getRoot().getStore(NAMESPACE);
    }

    static EventLoopGroup eventLoopGroup(ExtensionContext context) {
      return (EventLoopGroup) store(context).get("nettyEventLoopGroup");
    }

    @Override
    public void beforeAll(ExtensionContext context) {
      store(context)
          .put("nettyEventLoopGroup", new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()));
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
      Field field = field(context.getTestInstance().get().getClass(), "eventLoopGroup");
      if (field != null) {
        field.setAccessible(true);
        field.set(context.getTestInstance().get(), eventLoopGroup(context));
      }

      String brokerVersion = null;
      field = field(context.getTestInstance().get().getClass(), "stream");
      if (field != null) {
        field.setAccessible(true);
        String stream = streamName(context);
        field.set(context.getTestInstance().get(), stream);
        Client client =
            new Client(new Client.ClientParameters().eventLoopGroup(eventLoopGroup(context)));
        brokerVersion = currentVersion(client.brokerVersion());
        Client.Response response = client.create(stream);
        assertThat(response.isOk()).isTrue();
        store(context.getRoot()).put("filteringSupported", client.filteringSupported());
        client.close();
        store(context).put("testMethodStream", stream);
      }

      for (Field declaredField : context.getTestInstance().get().getClass().getDeclaredFields()) {
        if (declaredField.getType().equals(ClientFactory.class)) {
          declaredField.setAccessible(true);
          ClientFactory clientFactory = new ClientFactory(eventLoopGroup(context));
          declaredField.set(context.getTestInstance().get(), clientFactory);
          store(context).put("testClientFactory", clientFactory);
          break;
        }
      }

      field = field(context.getTestInstance().get().getClass(), "brokerVersion");
      if (field != null) {
        if (brokerVersion == null) {
          brokerVersion =
              context
                  .getRoot()
                  .getStore(ExtensionContext.Namespace.GLOBAL)
                  .get("brokerVersion", String.class);
        }
        if (brokerVersion == null) {
          Client client =
              new Client(new Client.ClientParameters().eventLoopGroup(eventLoopGroup(context)));
          brokerVersion = currentVersion(client.brokerVersion());
        }
        context
            .getRoot()
            .getStore(ExtensionContext.Namespace.GLOBAL)
            .put("brokerVersion", brokerVersion);
        field.setAccessible(true);
        field.set(context.getTestInstance().get(), brokerVersion);
      }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
      ClientFactory clientFactory = (ClientFactory) store(context).get("testClientFactory");
      if (clientFactory != null) {
        clientFactory.close();
      }

      try {
        Field streamField = context.getTestInstance().get().getClass().getDeclaredField("stream");
        streamField.setAccessible(true);
        String stream = (String) streamField.get(context.getTestInstance().get());
        Client client =
            new Client(new Client.ClientParameters().eventLoopGroup(eventLoopGroup(context)));
        Client.Response response = client.delete(stream);
        assertThat(response.isOk()).isTrue();
        client.close();
        store(context).remove("testMethodStream");
      } catch (NoSuchFieldException e) {

      }
    }

    @Override
    public void afterAll(ExtensionContext context) {
      EventLoopGroup eventLoopGroup = eventLoopGroup(context);
      ExecutorServiceCloseableResourceWrapper wrapper =
          context
              .getRoot()
              .getStore(ExtensionContext.Namespace.GLOBAL)
              .getOrComputeIfAbsent(ExecutorServiceCloseableResourceWrapper.class);
      wrapper.executorService.submit(
          () -> {
            try {
              eventLoopGroup.shutdownGracefully(0, 0, SECONDS).get(10, SECONDS);
            } catch (InterruptedException e) {
              // happens at the end of the test suite
              LOGGER.debug("Error while asynchronously closing Netty event loop group", e);
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              LOGGER.warn("Error while asynchronously closing Netty event loop group", e);
            }
          });
    }

    private static Field field(Class<?> cls, String name) {
      Field field = null;
      while (field == null && cls != null) {
        try {
          field = cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
          cls = cls.getSuperclass();
        }
      }
      return field;
    }

    private static class ExecutorServiceCloseableResourceWrapper implements AutoCloseable {

      private final ExecutorService executorService;

      private ExecutorServiceCloseableResourceWrapper() {
        this.executorService = Executors.newCachedThreadPool();
      }

      @Override
      public void close() {
        this.executorService.shutdownNow();
      }
    }
  }

  private static class BaseBrokerVersionAtLeastCondition implements ExecutionCondition {

    private final Function<ExtensionContext, String> versionProvider;

    private BaseBrokerVersionAtLeastCondition(Function<ExtensionContext, String> versionProvider) {
      this.versionProvider = versionProvider;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
      if (!context.getTestMethod().isPresent()) {
        return ConditionEvaluationResult.enabled("Apply only to methods");
      }
      String expectedVersion = versionProvider.apply(context);
      if (expectedVersion == null) {
        return ConditionEvaluationResult.enabled("No broker version requirement");
      } else {
        String brokerVersion =
            context
                .getRoot()
                .getStore(ExtensionContext.Namespace.GLOBAL)
                .getOrComputeIfAbsent(
                    "brokerVersion",
                    k -> {
                      EventLoopGroup eventLoopGroup =
                          StreamTestInfrastructureExtension.eventLoopGroup(context);
                      if (eventLoopGroup == null) {
                        throw new IllegalStateException(
                            "The event loop group must be in the test context to use "
                                + BrokerVersionAtLeast.class.getSimpleName()
                                + ", use the "
                                + StreamTestInfrastructureExtension.class.getSimpleName()
                                + " extension in the test");
                      }
                      try (Client client =
                          new Client(
                              new Client.ClientParameters().eventLoopGroup(eventLoopGroup))) {
                        return client.brokerVersion();
                      }
                    },
                    String.class);

        if (atLeastVersion(expectedVersion, brokerVersion)) {
          return ConditionEvaluationResult.enabled(
              "Broker version requirement met, expected "
                  + expectedVersion
                  + ", actual "
                  + brokerVersion);
        } else {
          return ConditionEvaluationResult.disabled(
              "Broker version requirement not met, expected "
                  + expectedVersion
                  + ", actual "
                  + brokerVersion);
        }
      }
    }
  }

  private static class AnnotationBrokerVersionAtLeastCondition
      extends BaseBrokerVersionAtLeastCondition {

    private AnnotationBrokerVersionAtLeastCondition() {
      super(
          context -> {
            BrokerVersionAtLeast annotation =
                context.getElement().get().getAnnotation(BrokerVersionAtLeast.class);
            return annotation == null ? null : annotation.value().toString();
          });
    }
  }

  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @ExtendWith(AnnotationBrokerVersionAtLeastCondition.class)
  public @interface BrokerVersionAtLeast {

    BrokerVersion value();
  }

  public enum BrokerVersion {
    RABBITMQ_3_11("3.11.0"),
    RABBITMQ_3_13_0("3.13.0");

    final String value;

    BrokerVersion(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  static boolean tlsAvailable() {
    try {
      Process process = Host.rabbitmqctl("status");
      String output = Host.capture(process.getInputStream());
      return output.contains("stream/ssl");
    } catch (Exception e) {
      throw new RuntimeException("Error while trying to detect TLS: " + e.getMessage());
    }
  }

  static class DisabledIfTlsNotEnabledCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
      if (tlsAvailable()) {
        return ConditionEvaluationResult.enabled("TLS is enabled");
      } else {
        return ConditionEvaluationResult.disabled("TLS is disabled");
      }
    }
  }

  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @ExtendWith(DisabledIfTlsNotEnabledCondition.class)
  public @interface DisabledIfTlsNotEnabled {}
}
