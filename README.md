# BiglyBT

Source for BiglyBT, open source bittorrent client.

* [Ways to Contribute](CONTRIBUTING.md)
* [Translation Information](TRANSLATE.md)
* [Feature Voting Page](https://vote.biglybt.com)

## Donations
Bitcoin Cash (BCH)/Bitcoin Blockstream (BTC): 1BiGLYBT38ttJhvZkjGc5mCw5uKoRHcUmr

## Setting up Dev Environment

Getting the basics to compile from source is pretty straightforward:

1. Clone the repo into your favorite IDE
1. Mark `core/src` and `uis/src` as project roots (source modules)
1. To the uis module, add `core/lib/*` and one of the swt.jars at `/uis/lib/`:<br>
  `swt-win64.jar` on Windows<br>
  `swt-cocoa-64.jar` on OSX<br>
  `swt-linux-64.jar` on Linux (GTK)
1. To the core module, add `core/lib/*`
1. Make `uis` module depend on `core`.  `Core` should not depend on `uis`

Intellij will do all these steps for you with its wizard.

## Running in Dev Environment

Running is only few more steps:

* Main class is `com.biglybt.ui.Main` in module `uis`
* Working Directory should be a new folder, since the app will write things to it. Put the `aereg.dll` or `libOSXAccess.jnilib` in there.
  
  When a normal user is running the app, the working directory is where the jar, executable, and libraries (dll, so, jnilib) are.
* If you want a separate config dir from the default one, use VM Option `-Dazureus.config.path=<some config path>`
* Run it


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

Please consult the [android API](https://developer.android.com/reference/packages) for details on API levels.


### Known limitations and restrictions

 * `java.lang.Long#compare(long, long)` - android level 19 (restricted)
 * `try-with-resources` - android level 19 (restricted)
 * `java.nio.charset.StandardCharsets` - android level 19 (restricted)
 * `java.util.stream`, `java.util.function` etc - android level 24+ (unsupported)


## Release Installer Notes

We build our installers using [Install4j, multi-platform installer builder](https://www.ej-technologies.com/products/install4j/overview.html)

![Install4j Logo](https://www.ej-technologies.com/images/product_banners/install4j_large.png)
