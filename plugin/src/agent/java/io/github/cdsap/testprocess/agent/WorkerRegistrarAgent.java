package io.github.cdsap.testprocess.agent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * Premain agent that runs inside every Gradle test worker JVM. At startup writes
 * {pid}.json with the worker's identity (PID, task path from the sentinel system
 * property, max heap, JVM args, start time, executor). On shutdown writes
 * {pid}.stats.json with final runtime stats sampled directly from JMX:
 * memory + GC + CPU + JIT + class loading + threads. The plugin does not need
 * to shell out to any external tool.
 */
public final class WorkerRegistrarAgent {

    private static final String TASK_PROPERTY = "io.github.cdsap.testprocess.task";

    private WorkerRegistrarAgent() {
    }

    public static void premain(final String agentArgs, Instrumentation inst) {
        try {
            if (agentArgs == null || agentArgs.isEmpty()) return;
            final File dir = new File(agentArgs);
            if (!dir.exists() && !dir.mkdirs() && !dir.exists()) return;

            final long pid = ProcessHandle.current().pid();
            writeIdentity(dir, pid);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> writeRuntimeStats(dir, pid), "info-test-process-stats"));
        } catch (Throwable ignored) {
            // Never break the worker because of our bookkeeping.
        }
    }

    private static void writeIdentity(File dir, long pid) {
        try {
            String task = System.getProperty(TASK_PROPERTY, "");
            long maxHeap = Runtime.getRuntime().maxMemory();
            long startMs = ManagementFactory.getRuntimeMXBean().getStartTime();
            List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            String executor = extractExecutor();

            File out = new File(dir, pid + ".json");
            try (FileWriter w = new FileWriter(out)) {
                w.write("{");
                w.write("\"pid\":" + pid);
                w.write(",\"task\":\"" + escape(task) + "\"");
                w.write(",\"executor\":\"" + escape(executor) + "\"");
                w.write(",\"maxHeapBytes\":" + maxHeap);
                w.write(",\"startMs\":" + startMs);
                w.write(",\"args\":[");
                for (int i = 0; i < jvmArgs.size(); i++) {
                    if (i > 0) w.write(",");
                    w.write("\"" + escape(jvmArgs.get(i)) + "\"");
                }
                w.write("]}");
            }
        } catch (IOException ignored) {
        }
    }

    private static void writeRuntimeStats(File dir, long pid) {
        try {
            RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
            long uptimeMs = rt.getUptime();
            long usedHeap = mem.getHeapMemoryUsage().getUsed();
            long maxHeap = mem.getHeapMemoryUsage().getMax();

            long collections = 0;
            long gcTimeMs = 0;
            String gcType = "Unknown";
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                long c = bean.getCollectionCount();
                long t = bean.getCollectionTime();
                if (c > 0) collections += c;
                if (t > 0) gcTimeMs += t;
                String detected = classifyGc(bean.getName());
                if (!"Unknown".equals(detected)) gcType = detected;
            }

            long peakHeap = 0;
            long peakMetaspace = 0;
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getPeakUsage() == null) continue;
                long pk = pool.getPeakUsage().getUsed();
                if (pool.getType() == MemoryType.HEAP) peakHeap += pk;
                if ("Metaspace".equals(pool.getName())) peakMetaspace = pk;
            }

            long cpuTimeMs = readCpuTimeMs();

            long jitTimeMs = -1;
            CompilationMXBean cb = ManagementFactory.getCompilationMXBean();
            if (cb != null && cb.isCompilationTimeMonitoringSupported()) {
                jitTimeMs = cb.getTotalCompilationTime();
            }

            ClassLoadingMXBean cl = ManagementFactory.getClassLoadingMXBean();
            long classesLoaded = cl != null ? cl.getTotalLoadedClassCount() : -1;

            ThreadMXBean tb = ManagementFactory.getThreadMXBean();
            int peakThreads = tb != null ? tb.getPeakThreadCount() : -1;

            File out = new File(dir, pid + ".stats.json");
            try (FileWriter w = new FileWriter(out)) {
                w.write("{");
                w.write("\"pid\":" + pid);
                w.write(",\"uptimeMs\":" + uptimeMs);
                w.write(",\"cpuTimeMs\":" + cpuTimeMs);
                w.write(",\"usedHeapBytes\":" + usedHeap);
                w.write(",\"peakHeapBytes\":" + peakHeap);
                w.write(",\"peakMetaspaceBytes\":" + peakMetaspace);
                w.write(",\"maxHeapBytes\":" + maxHeap);
                w.write(",\"gcCollections\":" + collections);
                w.write(",\"gcTimeMs\":" + gcTimeMs);
                w.write(",\"gcType\":\"" + escape(gcType) + "\"");
                w.write(",\"jitTimeMs\":" + jitTimeMs);
                w.write(",\"classesLoaded\":" + classesLoaded);
                w.write(",\"peakThreads\":" + peakThreads);
                w.write("}");
            }
        } catch (Throwable ignored) {
        }
    }

    private static long readCpuTimeMs() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                long nanos = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuTime();
                if (nanos >= 0) return nanos / 1_000_000L;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static String classifyGc(String name) {
        if (name == null) return "Unknown";
        if (name.startsWith("G1")) return "G1";
        if (name.startsWith("PS ")) return "PARALLEL";
        if (name.startsWith("ZGC") || name.equals("ZGC")) return "Z";
        if (name.startsWith("Shenandoah")) return "Shenandoah";
        if (name.equals("ParNew") || name.equals("ConcurrentMarkSweep")) return "CMS";
        if (name.equals("Copy") || name.equals("MarkSweepCompact")) return "Serial";
        return "Unknown";
    }

    private static String extractExecutor() {
        try {
            String info = ProcessHandle.current().info().toString();
            int idx = info.indexOf("Gradle Test Executor ");
            if (idx < 0) return "";
            String tail = info.substring(idx + "Gradle Test Executor ".length());
            int end = tail.indexOf('\'');
            if (end < 0) end = tail.indexOf(',');
            if (end < 0) end = Math.min(tail.length(), 16);
            return "Gradle Test Executor " + tail.substring(0, end).trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
