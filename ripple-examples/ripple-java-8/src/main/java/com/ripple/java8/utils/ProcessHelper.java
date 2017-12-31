package com.ripple.java8.utils;

public class ProcessHelper {
    private static sun.management.VMManagement mgmt;
    private static java.lang.reflect.Method pid_method;

    static {
        try {
            java.lang.management.RuntimeMXBean runtime =
                    java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.lang.reflect.Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
            mgmt = (sun.management.VMManagement) jvm.get(runtime);
            pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int getPID() {
        try {
            return (int) (Integer) pid_method.invoke(mgmt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
