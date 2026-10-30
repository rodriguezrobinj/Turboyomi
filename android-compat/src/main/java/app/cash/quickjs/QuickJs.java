package app.cash.quickjs;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * QuickJS's API on GraalJS. The scripts often come from a source's site, so they see no host object (only the
 * interface methods handed to {@link #set}) and are stopped after {@link #DEFAULT_LIMIT}.
 */
public final class QuickJs implements Closeable {
    // Deobfuscating a page takes milliseconds; a script still running after this never ends
    private static final Duration DEFAULT_LIMIT = Duration.ofSeconds(30);

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "QuickJs watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private Context context;
    private final Duration limit;

    public static QuickJs create() {
        return new QuickJs();
    }

    public QuickJs() {
        this(DEFAULT_LIMIT);
    }

    QuickJs(Duration limit) {
        this.limit = limit;
        this.context = Context
                .newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowHostClassLoading(false)
                .build();
        // Not entered here: an interrupt waits for every thread to leave the context, and this one never would
    }

    public Object evaluate(String script, String ignoredFileName) {
        return this.evaluate(script);
    }

    public Object evaluate(String script) {
        Context running = context;
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
            try {
                running.interrupt(Duration.ofSeconds(30));
            } catch (TimeoutException | IllegalStateException ignored) {
                // Already done, or stuck in host code the interrupt can't reach
            }
        }, limit.toMillis(), TimeUnit.MILLISECONDS);
        try {
            Value value = context.eval("js", script);
            return translateType(value);
        } catch (Exception exception) {
            throw new QuickJsException(exception.getMessage(), exception);
        } finally {
            // Already firing: the interrupt lasts until it returns, and would stop the next script too
            if (!watchdog.cancel(false)) {
                try {
                    watchdog.get();
                } catch (Exception ignored) {
                    // It only interrupts
                }
            }
        }
    }

    private Object translateType(Value obj) {
        if (obj.isBoolean()) {
            return obj.asBoolean();
        } else if (obj.hasArrayElements()) {
            if (obj.getArraySize() == 0) {
                return new int[0];
            } else {
                Value element = obj.getArrayElement(0);
                if (element.isBoolean()) {
                    return obj.as(boolean[].class);
                } else if (element.isNumber()) {
                    if (element.fitsInInt()) {
                        return obj.as(int[].class);
                    } else if (element.fitsInBigInteger()) {
                        return Arrays.stream(obj.as(BigInteger[].class)).map(BigInteger::longValue).toArray();
                    } else {
                        return obj.as(double[].class);
                    }
                } else if (element.isHostObject()) {
                    return obj.as(Object[].class);
                } else if (element.isString()) {
                    return obj.as(String[].class);
                }
            }
        } else if (obj.isNumber()) {
            if (obj.fitsInInt()) {
                return obj.asInt();
            } else if (obj.fitsInBigInteger()) {
                return obj.asBigInteger().longValue();
            } else {
                return obj.asDouble();
            }
        } else if (obj.isHostObject()) {
            return obj.asHostObject();
        } else if (obj.isString()) {
            return obj.asString();
        }
        return obj;
    }

    public byte[] compile(String sourceCode, String ignoredFileName) {
        return sourceCode.getBytes();
    }

    public Object execute(byte[] bytecode) {
        return this.evaluate(new String(bytecode));
    }

    /** Hands the script [type]'s methods on [object], as QuickJS does: not the object, whose getClass() leads anywhere. */
    public <T> void set(String name, Class<T> type, T object) {
        Map<String, Object> members = new HashMap<>();
        for (Method method : type.getMethods()) {
            members.put(method.getName(), (ProxyExecutable) arguments -> invoke(method, object, arguments));
        }
        context.getBindings("js").putMember(name, ProxyObject.fromMap(members));
    }

    private static Object invoke(Method method, Object target, Value[] arguments) {
        Class<?>[] types = method.getParameterTypes();
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length && i < arguments.length; i++) {
            values[i] = arguments[i].as(types[i]);
        }
        try {
            return method.invoke(target, values);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        if (this.context != null) {
            this.context.close();
            this.context = null;
        }
    }
}
