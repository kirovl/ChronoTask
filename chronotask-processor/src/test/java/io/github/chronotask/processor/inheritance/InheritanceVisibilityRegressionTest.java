package io.github.chronotask.processor.inheritance;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.chronotask.processor.ChronoTaskProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InheritanceVisibilityRegressionTest {
    @Test void crossPackageInheritanceMustNotGenerateInaccessibleCalls() {
        var base = JavaFileObjects.forSourceString("base.Base", """
            package base;
            public class Base {
                @io.github.chronotask.annotation.Scheduled(intervalMs = 1000)
                void hidden() {}
            }
            """);
        var child = JavaFileObjects.forSourceString("child.Child", """
            package child;
            public class Child extends base.Base {
                @io.github.chronotask.annotation.Once(delayMs = 1000)
                public void own() {}
            }
            """);
        var compilation = Compiler.javac().withProcessors(new ChronoTaskProcessor()).compile(base, child);
        assertEquals(Compilation.Status.SUCCESS, compilation.status(), compilation.diagnostics().toString());
    }
}
