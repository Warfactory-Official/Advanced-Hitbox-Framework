package com.norwood.ahf.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 26.1 stand-in for the removed {@code @GameTest}; registered by {@link AhfGameTests} on template {@code ahf:empty}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AhfGameTest {

    /** Test environment {@code ahf:<batch>}; tests of one environment run together. */
    String batch() default "default";

    boolean required() default true;

    int timeoutTicks() default 100;
}
