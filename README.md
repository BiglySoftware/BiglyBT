# BiglyBT

Source for BiglyBT, a feature-filled, open-source, ad-free, BitTorrent client. BiglyBT is forked from the original project and is maintained by two of the original developers and members of the community. With over 15 years of development, there’s a good chance we have the features you are looking for, as well as the decade-old bugs you aren’t looking for. :)

- [Official BiglyBT site](https://www.biglybt.com)
- [Ways to Contribute](CONTRIBUTING.md)
- [Translation Information](TRANSLATE.md)
- [Feature Voting Page](https://vote.biglybt.com)
- [Coding Guidelines](CODING_GUIDELINES.md)
- [Beta Program Changelog](https://biglybt.tumblr.com/)

## Donations

| Method | Address |
|:--|:--|
| PayPal | [BiglyBT's Donation Page](https://www.biglybt.com/donation/donate.php) |
| BCH/BTC/BSG/BSV | 1BiGLYBT38ttJhvZkjGc5mCw5uKoRHcUmr |
| DASH            | XjDwmSrDPQBaLzCkuRHZaFzHf7mTVxkW9K |
| DOGE | DMXWdEtPUJc5p2sbHGo77SvqFXKTR8Vff1 |
| ETH/ETC | 0x4e609B5EF88C8aA8Ab73945fD1ba68c9E27faC75 |
| LTC | LKGc2utCrGfojpYsX3naT9n1AxjLiZ5MMG |
| TRX/BTT | TAbsb7pjEEWNpXFvPf49rfVhFSB2e3dAM7 |
| XRP | rPFKfbx2tuzMMaz7Zy99M6CquHKgp9srSb |

## Prerequisites

Ensure you have the following installed before proceeding:

- IntelliJ IDEA (or your favorite IDE)
- Required dependencies: `core/lib/*`, one of the `swt.jars` based on your operating system:
  - `swt-win64.jar` for Windows
  - `swt-cocoa-64.jar` for macOS
  - `swt-linux-64.jar` for Linux (GTK)

## Installation Steps

### Manual Installation

1. Clone the repository into your favorite IDE.
2. Mark `core/src` and `uis/src` as project roots (source modules).
3. Add required dependencies:
   - For the `uis` module: `core/lib/*` and one of the `swt.jars` at `/uis/lib/`.
   - For the `core` module: `core/lib/*`.
4. Make the `uis` module depend on `core`. Note that `core` should not depend on `uis`.

### Running in Development Environment

1. Main class is `com.biglybt.ui.Main` in module `uis`.
2. Set the working directory to a new folder (the app will write files there). Place the [`aereg.dll`](core/lib/libWIN32Access/README.md) or `libOSXAccess.jnilib` in the working directory.
3. If you want a separate config directory from the default, use the VM option:
   ```
   -Dazureus.config.path=<some config path>
   ```
4. Run the application.

### External Annotations

To improve IntelliJ IDEA's handling of MessageBundle strings and reduce warnings:
- Use Project Settings > Modules > Paths > External Annotations.
- Alternatively, modify `<module>/<module>.iml`:
  ```xml
  <annotation-paths>
    <root url="file://$MODULE_DIR$/../external-annotations" />
  </annotation-paths>
  ```

Note: External Annotations definitions are a work in progress.

## Release Installer Notes

We build our installers using [Install4j, a multi-platform installer builder](https://www.ej-technologies.com/products/install4j/overview.html).

![Install4j Logo](https://www.ej-technologies.com/images/product_banners/install4j_large.png)

Our binaries and installers up to and including v3.4 are signed with the digital signature "Bigly Software." Releases after v3.4 will use an individual signing certificate, signed by "Arron Mogge (Open Source Developer)," denoting the team member responsible for signing.

## Help and Support

For FAQs and commonly encountered errors, please refer to the repository issue thread.
