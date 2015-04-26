package com.widget.her.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
public @interface TargetFieldDefine {
    String fieldname();
    int classId();
}
