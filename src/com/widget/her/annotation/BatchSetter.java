package com.widget.her.annotation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class BatchSetter {
    /** 批量将 fromObject 中的数据使用 targetClass的set方法，传入targetObject中
     * @param fromObject
     * @param targetClss
     * @param targetObject
     */
    public static void batchSetter(Object fromObject, Class<?> targetClss,
            Object targetObject) {
        try {

            for (Field field : fromObject.getClass().getDeclaredFields()) {
                GenerateClassSetter nestSetter = field.getType().getAnnotation(
                        GenerateClassSetter.class);
                if (nestSetter != null) {
                    boolean sameTypeWithEnclosingObj = false;
                    for (TargetClassDefine targetClass : nestSetter
                            .targetType()) {
                        sameTypeWithEnclosingObj = targetClass.name().equals(
                                targetClss.getCanonicalName());
                        if (sameTypeWithEnclosingObj) {
                            break;
                        }
                    }
                    if (sameTypeWithEnclosingObj) {
                        if (! Modifier.isPublic(field.getModifiers())) {
                            System.out.println(fromObject.getClass().getSimpleName() + "." + field.getName() + ", not public, ignored");
                        } else {
                            if (field.get(fromObject) != null) {
                                batchSetter(field.get(fromObject), targetClss,
                                        targetObject);
                                System.out.println("recursive invoke: " + field.getName());
                            }
                        }
                    }
                }
            }

            String packageLoc = fromObject.getClass().getPackage().getName();
            String enclosingClassPrefix = "";
            Class<?> enclosingClass = fromObject.getClass().getEnclosingClass();
            while (enclosingClass != null) {
                enclosingClassPrefix = enclosingClass.getSimpleName() + "_"
                        + enclosingClassPrefix;
                enclosingClass = enclosingClass.getEnclosingClass();
            }
            Class<?> injectorClass = Class.forName(packageLoc + "."
                    + enclosingClassPrefix
                    + fromObject.getClass().getSimpleName() + "Setter");
            injectorClass.getMethod("set", fromObject.getClass(),
                    targetClss).invoke(null, fromObject,
                    targetObject);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
