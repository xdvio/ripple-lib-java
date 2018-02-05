package com.ripple.client;

import com.ripple.client.subscriptions.SubscriptionManager;
import com.ripple.client.transactions.ManagedTxn;
import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EventsTest {
    private ClassLoader classLoader = getClass().getClassLoader();
    private Reflections reflections = new Reflections("com.ripple.client", new SubTypesScanner(false));

     @Test
    public void testFluentBinders() throws Exception {
        Class<?> rootKlass = Client.class;
        String canonicalName = rootKlass.getCanonicalName();
        Class<?> eventsKlass = classLoader.loadClass(canonicalName + "$events");

        @SuppressWarnings("unchecked")
        Set<Class<?>> subTypesOf = (Set<Class<?>>) reflections.getSubTypesOf(eventsKlass);

        List<Class<?>> collect =
                subTypesOf.stream().filter(
                        aClass -> aClass.getName()
                                .startsWith(canonicalName + "$On"))
                        .collect(Collectors.toList());

        collect.forEach(eventKlass -> {
            Type genericInterface = eventKlass.getGenericInterfaces()[0];
            String eventName = eventKlass.getName().split("\\$")[1].substring(2);
            // System.out.println(eventName);
            String typeName = genericInterface.getTypeName();
            typeName = typeName.replace(rootKlass.getCanonicalName() + ".", "");
            typeName = typeName.replace(rootKlass.getCanonicalName() + "$events", "");
            typeName = typeName.replace("<", "");
            typeName = typeName.replace(">", "");
            // System.out.println("generic: " + typeName);

            checkEvent(rootKlass, eventKlass, eventName, typeName, "on");
            checkEvent(rootKlass, eventKlass, eventName, typeName, "once");
        });
    }

    private void checkEvent(Class<?> rootKlass, Class<?> eventKlass, String eventName, String typeName, String event) {
        String methodName = event + eventName;
        Method method = getMethod(rootKlass, methodName, eventKlass);
        if (method == null) {
            String returnValue = rootKlass.getSimpleName();
            String callBackName = (eventKlass.getSimpleName());
            String callBackNameCamel = camelize(callBackName);

            String format = String.format("public %s %s%s(%s %s) {\n" +
                            "   " + event + "(" + eventKlass.getSimpleName() + ".class, " + callBackNameCamel + ");\n" +
                            "   return this;" +
                    "\n}", returnValue, event, eventName, callBackName, callBackNameCamel);
            System.out.println(format);
        }
    }

    private String camelize(String typeNameSimple) {
        return typeNameSimple.substring(0, 1).toLowerCase() + typeNameSimple.substring(1);
    }

    private Method getMethod(Class<?> klass, String methodName, Class<?> parameterClass) {
        try {

            return klass.getDeclaredMethod(methodName, (parameterClass));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
