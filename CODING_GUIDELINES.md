# Coding Guidelines

With over 15 years of code, many developers with many different coding styles have come and gone, making it sometimes difficult to read or write code. This document will spell out some of the more common styling and standards.

The general rule is to code in the style of the code around it.  If there's one main commit rule to follow it's that the goal should be to make the commit diff as small as possible.

## Coding Standards

There are two main coding styles within BiglyBT for the simple reason that there are two grumpy main developers with their own style.

One of the styles is documented at [PreferencesJavaCodeStyleFormatter.xml](PreferencesJavaCodeStyleFormatter.xml). This is an Eclipse formatter configuration file, which can be used in IntelliJ with [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) plugin.

## Common Code Styles

* Tab indent
* Column limit is generally 80. We thought about increasing this once, but IDEs keep stealing width with docked windows, plus side-by-side diffs have less of a chance of wrapping.
* `!isFoo()` instead of `false == isFoo()` or `isFoo() == false`
* Constants over Enums
* `if`, `else`, `for`, `do`, and `while` statements should always have `{` braces `}`.  Opening brace typically should be on the same line (Egyptian Brackets)
* We have no standard for opening braces on class and method declarations :( Probably because the [K&R style](https://en.wikipedia.org/wiki/Indentation_style#K&R_style) wants a newline, but there's a common Java variant that wants same line.
* Prefer early `return` over nested `if` statements. This is a soft rule, so go with what looks easier to understand.
* Whitespace, variable names:  Don't ask ;)  Go with surrounding code or with what looks ok.
* If existing code isn't following the rules above, leave it unless you are modifying the code around it, aka "make commit diffs as small as possible" 
 
## Compatibility and API profiles

BiglyBT comes in several editions for different operating systems. 
Mac OSX, Linux and Windows uses the full BiglyBT-API based on Java 8.

Android edition is based on Java 8, but is limited to the minimum Android SDK version in use.
BiglyBT-API for Android is maintained on a dedicated branch [`android`](https://github.com/BiglySoftware/BiglyBT/tree/android).
This api is consumed by the [BiglyBT-Android](https://github.com/BiglySoftware/BiglyBT-Android) project.

For code portability and easy merging it is strongly advised to use JDK features only
covered by the minimum Android SDK level. 
The currently minimum SDK level is `15` (Android 4.0.3 - Ice Cream Sandwich)

Generally, Android embraces the Java 7 API as well as a growing subset of Java 8+ features.

The supported subset of Java 8+ features are listed at [Use  Java 8 language features and APIs](https://developer.android.com/studio/write/java8-support) and [Java 8+ APIs available through desugaring](https://developer.android.com/studio/write/java8-support-table)


### Known limitations and restrictions

 * `java.nio.file.*` - android level 26
 * `java.nio.charset.StandardCharsets` - android level 19 (restricted)
 * `java.util.Locale.Builder` - android level 21

