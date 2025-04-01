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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

interface CompletionHandler {

  Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CompletionHandler.class);

  void waitForCompletion() throws InterruptedException;

  void countDown(String reason);

  final class DefaultCompletionHandler implements CompletionHandler {

    private static final String STOP_REASON_REACHED_TIME_LIMIT = "Reached time limit";

    private final Duration timeLimit;
    private final CountDownLatch latch;
    private final ConcurrentMap<String, Integer> reasons;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    DefaultCompletionHandler(
        int timeLimitSeconds, int countLimit, ConcurrentMap<String, Integer> reasons) {
      this.timeLimit = Duration.ofSeconds(timeLimitSeconds);
      int count = countLimit <= 0 ? 1 : countLimit;
      LOGGER.debug("Count completion limit is {}", count);
      this.latch = new CountDownLatch(count);
      this.reasons = reasons;
    }

    @Override
    public void waitForCompletion() throws InterruptedException {
      if (timeLimit.isNegative() || timeLimit.isZero()) {
        this.latch.await();
        completed.set(true);
      } else {
        boolean countedDown = this.latch.await(timeLimit.toMillis(), TimeUnit.MILLISECONDS);
        completed.set(true);
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Completed, counted down? {}", countedDown);
        }
        if (!countedDown) {
          recordReason(reasons, STOP_REASON_REACHED_TIME_LIMIT);
        }
      }
    }

    @Override
    public void countDown(String reason) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Counting down ({})", reason);
      }
      if (!completed.get()) {
        recordReason(reasons, reason);
        latch.countDown();
      }
    }
  }

  /**
   * This completion handler waits forever, but it can be counted down, typically when a producer or
   * a consumer fails. This avoids PerfTest hanging after a failure.
   */
  final class NoLimitCompletionHandler implements CompletionHandler {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final ConcurrentMap<String, Integer> reasons;

    NoLimitCompletionHandler(ConcurrentMap<String, Integer> reasons) {
      this.reasons = reasons;
    }

    @Override
    public void waitForCompletion() throws InterruptedException {
      latch.await();
    }

    @Override
    public void countDown(String reason) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Counting down ({})", reason);
      }
      recordReason(reasons, reason);
      latch.countDown();
    }
  }

  private static void recordReason(Map<String, Integer> reasons, String reason) {
    reasons.compute(reason, (keyReason, count) -> count == null ? 1 : count + 1);
  }
}
