package com.widget.her.annotation;

import java.lang.reflect.Field;

public class BatchSetter {
    public static void batchSetter(Object fromObject, Class<?> targetClss,
            Object targetObject) {
        try {

            for (Field field : fromObject.getClass().getFields()) {
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
                    if (sameTypeWithEnclosingObj
                            && field.get(fromObject) != null) {
                        batchSetter(field.get(fromObject), targetClss,
                                targetObject);
                        System.out.println(field.getName());
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
            Class<?> injectorClass = Class.forName(packageLoc + "._"
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
