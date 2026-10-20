package com.bidr.platform.schedule;

import org.junit.After;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Title: TimingWheelTest
 * Description: 时间轮离线单测（纯 JUnit4）——注入假钟＋直驱 {@code advanceOnce()}
 * 的确定性断言，不起真实步进线程、不睡真实时钟。
 *
 * <p>参数形态：tick=100ms、ring=4（小参数放大 rounds 溢出路径，几步之内走完全部分支）。
 * 收割执行体用直接执行器（Runnable::run），fire 同步可见、无并发竞态。
 * 步进语义＝<b>先拨钟再步进</b>：deadline 落在步进后时刻的件在该次步进收割；
 * 若步进先于拨钟，件落进"rounds=0 但未到期"的留槽腿、等 cursor 绕一圈再扫（竞态边界语义）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-20
 */
public class TimingWheelTest {

    /** 可拨动的假钟：测试逐步推进 millis，轮子的"现在"完全受控 */
    private static final class MutableClock extends Clock {
        private long millis;

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        void advance(long deltaMillis) {
            millis += deltaMillis;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final List<String> fired = new ArrayList<String>();

    private TimingWheel newWheel() {
        return new TimingWheel(clock, Runnable::run, 100L, 4, false);
    }

    @After
    public void tearDown() {
        clock.millis = 0;
        fired.clear();
    }

    @Test
    public void oneShotFiresOnce() {
        TimingWheel wheel = newWheel();
        wheel.schedule("a", 100L, () -> fired.add("a"));

        clock.advance(100L);
        wheel.advanceOnce();
        assertEquals(java.util.Collections.singletonList("a"), fired);

        clock.advance(100L);
        wheel.advanceOnce();
        wheel.advanceOnce();
        assertEquals(java.util.Collections.singletonList("a"), fired);
    }

    @Test
    public void longDelaySpendsRounds() {
        TimingWheel wheel = newWheel();
        // 延迟 500ms＝5 ticks；ring=4 ⇒ rounds=1，与 near 件同落槽 1
        wheel.schedule("far", 500L, () -> fired.add("far"));
        wheel.schedule("near", 100L, () -> fired.add("near"));

        clock.advance(100L);
        wheel.advanceOnce();
        // 近件第 1 tick 触发；远件减圈（rounds 1→0）
        assertEquals(java.util.Collections.singletonList("near"), fired);

        // 第 2~4 tick：远件 rounds=0 但时刻未到 ⇒ 留槽不触发
        for (int i = 2; i <= 4; i++) {
            clock.advance(100L);
            wheel.advanceOnce();
            assertEquals(java.util.Collections.singletonList("near"), fired);
        }
        // 第 5 tick：cursor 绕回落槽、时刻恰到 ⇒ 触发（rounds 溢出计数正确）
        clock.advance(100L);
        wheel.advanceOnce();
        assertEquals(Arrays.asList("near", "far"), fired);
    }

    @Test
    public void cancelledTicketNeverFires() {
        TimingWheel wheel = newWheel();
        TimingWheel.Ticket ticket = wheel.schedule("x", 100L, () -> fired.add("x"));
        ticket.cancel();
        ticket.cancel();

        clock.advance(100L);
        wheel.advanceOnce();
        wheel.advanceOnce();
        assertTrue(fired.isEmpty());
    }

    @Test
    public void startStopIdempotent() {
        TimingWheel wheel = newWheel();
        wheel.start();
        wheel.start();
        wheel.stop();
        wheel.stop();
        wheel.schedule("post", 100L, () -> fired.add("post"));
        clock.advance(100L);
        wheel.advanceOnce();
        // stop 只停线程，轮子本体仍可直驱
        assertEquals(java.util.Collections.singletonList("post"), fired);
    }

    @Test
    public void rejectsInvalidConstructorArgs() {
        try {
            new TimingWheel(clock, Runnable::run, 0L, 4, false);
            fail("tickMillis<=0 必须拒");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("tickMillis"));
        }
        try {
            new TimingWheel(clock, Runnable::run, 100L, 0, false);
            fail("ringSize<=0 必须拒");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("ringSize"));
        }
        try {
            newWheel().schedule("null-task", 100L, null);
            fail("null 执行体必须拒");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("null"));
        }
    }
}
