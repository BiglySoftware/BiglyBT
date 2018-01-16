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
1. To the uis module, add `core/lib/*`, `uis/lib/log4j.jar` and one of the swt.jars at `/uis/lib/`:<br>
  swt-cocoa-64.jar on OSX<br>
  swt-win-64.jar on Windows
1. To the core module, add `core/lib/*`
1. Make `uis` module depend on `core`.  `Core` should not depend on `uis`

Intellij will do all these steps for you with its wizard, and the only change you need to make is to remove one of the swt.jar files it found.

## Running in Dev Environment

Running is only few more steps:

* Main class is `com.biglybt.ui.Main` in `uis`
* Working Directory should be a new folder, since the app will write things to it. Put the `aereg.dll` or `libOSXAccess.jnilib` in there.
  
  When a normal user is running the app, the working directory is where the jar, executable, and libraries (dll, so, jnilib) are.
* If you want a separate config dir from the default one, use VM Option `-Dazureus.config.path=<some config path>`
* Run it
