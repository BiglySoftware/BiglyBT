# Coding Guidelines

With over 15 years of code, many developers with many different coding styles have come and gone, making it sometimes difficult to read or write code. This document will spell out some of the more common styling and standards.

The general rule is to code in the style of the code around it.  If there's one main commit rule to follow it's that the goal should be to make the commit diff as small as possible.

## Coding Standards

There are two main coding styles within BiglyBT for the simple reason that there are two grumpy main developers with their own style.

One of the styles is documented at [BiglyBT/PreferencesJavaCodeStyleFormatter.xml](BiglyBT/PreferencesJavaCodeStyleFormatter.xml). This is an Eclipse formatter configuration file, which can be used in IntelliJ with [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) plugin.

## Common Code Styles

* Tab Indent
* `!isFoo()` instead of `false == isFoo()` or `isFoo() == false`
* Constants over Enums
 
## Compatibility and API profiles

BiglyBT comes in several editions for different operating systems. 
Mac OSX, Linux and Windows uses the full BiglyBT-API based on Java 8.

Android edition is based on Java 8, but is limited to the minimum Android SDK version in use.
BiglyBT-API for Android is maintained on a dedicated branch [`android`](https://github.com/BiglySoftware/BiglyBT/tree/android).
This api is consumed by the [BiglyBT-Android](https://github.com/BiglySoftware/BiglyBT-Android) project.

For code portability and easy merging it is strongly advised to use JDK features only
covered by the minimum Android SDK level. 
The currently minimum SDK level is `15` (Android 4.0.3 - Ice Cream Sandwich)

Generally, Android embraces the Java 7 API as well as a subset of Java 8 features.

Please consult the [Android API](https://developer.android.com/reference/packages) for details on API levels.


### Known limitations and restrictions

 * `java.lang.Long#compare(long, long)` - android level 19 (restricted)
 * `try-with-resources` - android level 19 (restricted)
 * `java.nio.charset.StandardCharsets` - android level 19 (restricted)
 * `java.util.stream`, `java.util.function` etc - android level 24+ (unsupported)


