package com.widget.her.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface FieldToSet {
    TargetFieldDefine[] targetFields();
    boolean errorWhenStringNull() default false;
}
