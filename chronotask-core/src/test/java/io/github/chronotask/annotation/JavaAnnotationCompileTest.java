package io.github.chronotask.annotation;

import io.github.chronotask.cron.CronDialect;
import io.github.chronotask.model.QueueOverflowPolicy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class JavaAnnotationCompileTest {

    public static class JavaAnnotatedService {
        @Scheduled(intervalMs = 10000, mode = ScheduleMode.FIXED_DELAY, initialDelayMs = 2000,
                   id = "java-scheduled", name = "Java Task", concurrency = AnnotationConcurrency.QUEUE_ONE,
                   misfire = AnnotationMisfire.FIRE_ONCE, dispatcher = "io-pool", enabled = false,
                   serialQueueSize = 512, serialOverflow = QueueOverflowPolicy.DROP_LATEST)
        public void handleScheduled() {}

        @Cron(expression = "0 0 12 * * ?", zone = "Asia/Shanghai", dialect = CronDialect.QUARTZ,
              id = "java-cron", name = "Java Cron", concurrency = AnnotationConcurrency.ALLOW,
              misfire = AnnotationMisfire.CATCH_UP, catchUpLimit = 50)
        public void handleCron() {}

        @Once(delayMs = 5000, id = "java-once")
        public void handleOnce() {}

        @TickTask(intervalMs = 2000, initialDelayMs = 500, id = "java-tick")
        public void handleTick() {}
    }

    @Test
    public void testJavaAnnotationsReadableReflectively() throws Exception {
        Method m1 = JavaAnnotatedService.class.getMethod("handleScheduled");
        Scheduled s = m1.getAnnotation(Scheduled.class);
        assertNotNull(s);
        assertEquals(10000L, s.intervalMs());
        assertEquals(ScheduleMode.FIXED_DELAY, s.mode());
        assertEquals("java-scheduled", s.id());
        assertEquals(AnnotationConcurrency.QUEUE_ONE, s.concurrency());
        assertEquals(AnnotationMisfire.FIRE_ONCE, s.misfire());
        assertFalse(s.enabled());
        assertEquals(512, s.serialQueueSize());
        assertEquals(QueueOverflowPolicy.DROP_LATEST, s.serialOverflow());

        Method m2 = JavaAnnotatedService.class.getMethod("handleCron");
        Cron c = m2.getAnnotation(Cron.class);
        assertNotNull(c);
        assertEquals("0 0 12 * * ?", c.expression());
        assertEquals("Asia/Shanghai", c.zone());
        assertEquals(CronDialect.QUARTZ, c.dialect());
        assertEquals(AnnotationConcurrency.ALLOW, c.concurrency());
        assertEquals(AnnotationMisfire.CATCH_UP, c.misfire());
        assertEquals(50, c.catchUpLimit());

        Method m3 = JavaAnnotatedService.class.getMethod("handleOnce");
        Once o = m3.getAnnotation(Once.class);
        assertNotNull(o);
        assertEquals(5000L, o.delayMs());
        assertEquals("java-once", o.id());

        Method m4 = JavaAnnotatedService.class.getMethod("handleTick");
        TickTask t = m4.getAnnotation(TickTask.class);
        assertNotNull(t);
        assertEquals(2000L, t.intervalMs());
        assertEquals(500L, t.initialDelayMs());
        assertEquals("java-tick", t.id());
    }
}
