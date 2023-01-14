/*
 * Created on Jan 5, 2010
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.shells;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.ui.swt.skin.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.installer.*;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.CoreWaiterSWT.TriggerInThread;
import com.biglybt.ui.swt.shells.GCStringPrinter.URLInfo;

import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.pairing.*;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.SkinnedDialog.SkinnedDialogClosedListener;
import com.biglybt.util.StringCompareUtils;

/**
 * @author TuxPaper
 * @created Jan 5, 2010
 *
 */
public class RemotePairingWindow
	implements PairingManagerListener
{
	private static final String PLUGINID_WEBUI = "xmwebui";

	private static final boolean SHOW_SPEW = false;

	private static final boolean DEBUG = false;

	private static final boolean USE_OUR_QR = false;

	static RemotePairingWindow instance = null;

	private SkinnedDialog skinnedDialog;

	private SWTSkin skin;

	private SWTSkinObjectButton soEnablePairing;

	private PairingManager pairingManager;

	private SWTSkinObject soCodeArea;

	private Font fontCode;

	private String accessCode;

	private Control control;

	private SWTSkinObjectText soStatusText;

	private SWTSkinObject soFTUX;

	private SWTSkinObject soCode;

	private SWTSkinObjectText soToClipboard;

	private boolean hideCode = true;

	private String fallBackStatusText = "";

	private static testPairingClass testPairingClass;

	private PairingTest pairingTest;

	private boolean alreadyTested;

	private String storedToClipboardText;

	private String lastPairingTestError;

	private SWTSkinObjectImage soQR;

	public static void open() {
		if (DEBUG) {
			if (testPairingClass == null) {
				testPairingClass = new testPairingClass();
			} else {
				testPairingClass.inc();
			}
		}

		final RemotePairingWindow inst;

		synchronized (RemotePairingWindow.class) {
			if (instance == null) {
				instance = new RemotePairingWindow();
			}

			inst = instance;
		}

		CoreWaiterSWT.waitForCore(TriggerInThread.SWT_THREAD,
				new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						inst._open();
					}
				});
	}

	private PluginInterface getWebUI() {
		return CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(
				PLUGINID_WEBUI, true);
	}

	private void _open() {
		alreadyTested = false;

		pairingManager = PairingManagerFactory.getSingleton();
		PluginInterface piWebUI = getWebUI();

		boolean showFTUX = piWebUI == null || !pairingManager.isEnabled();

		if (skinnedDialog == null || skinnedDialog.isDisposed()) {
			skinnedDialog = new SkinnedDialog("skin3_dlg_remotepairing", "shell",
					SWT.DIALOG_TRIM);

			skin = skinnedDialog.getSkin();

			soCodeArea = skin.getSkinObject("code-area");
			control = soCodeArea.getControl();

			soEnablePairing = (SWTSkinObjectButton) skin.getSkinObject("enable-pairing");
			soEnablePairing.addSelectionListener(new ButtonListenerAdapter() {
				// @see SWTSkinButtonUtility.ButtonListenerAdapter#pressed(SWTSkinButtonUtility, SWTSkinObject, int)
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					skinObject.getControl().setEnabled(false);

					if (!pairingManager.isEnabled()) {
						// enabling will automatically get access code and trigger
						// somethingChanged
						pairingManager.setEnabled(true);
						if (SHOW_SPEW) {
							System.out.println("PAIR] SetEnabled");
						}
					} else {
						// fire something changed ourselves, so that accesscode gets
						// picked up
						if (SHOW_SPEW) {
							System.out.println("PAIR] AlreadyEnabled");
						}
						somethingChanged(pairingManager);
					}

					if (getWebUI() == null) {
						installWebUI();
					} else {
						switchToCode();
					}
				}
			});

			soFTUX = skin.getSkinObject("pairing-ftux");
			soCode = skin.getSkinObject("pairing-code");
			soQR = (SWTSkinObjectImage) skin.getSkinObject("pairing-qr");
			if (accessCode != null) {
				setupQR(accessCode);
			}

			soStatusText = (SWTSkinObjectText) skin.getSkinObject("status-text");
			soStatusText.addUrlClickedListener(new SWTSkinObjectText_UrlClickedListener() {
				@Override
				public boolean urlClicked(URLInfo urlInfo) {
					if (urlInfo.url.equals("retry")) {
						if (DEBUG) {
							testPairingClass.inc();
						}
						alreadyTested = false;
						testPairing(false);
						return true;
					}
					return false;
				}
			});

			pairingManager.addListener(this);

			Font font = control.getFont();
			GC gc = new GC(control);
			fontCode = FontUtils.getFontWithStyle(font,  SWT.BOLD, 1.33f);
			gc.dispose();
			control.setFont(fontCode);

			control.addPaintListener(new PaintListener() {
				@Override
				public void paintControl(PaintEvent e) {
					Color oldColor = e.gc.getForeground();

					Rectangle printArea = ((Composite) e.widget).getClientArea();
					printArea.y += 10;
					printArea.height -= 20;
					int fullWidth = printArea.width;
					int fullHeight = printArea.height;
					GCStringPrinter sp = new GCStringPrinter(e.gc,
							MessageText.getString("remote.pairing.accesscode"), printArea,
							false, false, SWT.NONE);
					sp.calculateMetrics();
					Point sizeAccess = sp.getCalculatedSize();

					String drawAccessCode = accessCode == null ? "      " : accessCode;

					int numBoxes = drawAccessCode.length();
					int boxSize = 25;
					int boxSizeAndPadding = 30;
					int allBoxesWidth = numBoxes * boxSizeAndPadding;
					int textPadding = 15;
					printArea.y = (fullHeight - boxSizeAndPadding - sizeAccess.y + textPadding) / 2;

					sp.printString(e.gc, printArea, SWT.CENTER | SWT.TOP);
					
					if ( Utils.isDarkAppearanceNative()) {
						e.gc.setBackground(Colors.getSystemColor(control.getDisplay(), SWT.COLOR_WIDGET_BACKGROUND));
						e.gc.setForeground(Colors.getSystemColor(control.getDisplay(), SWT.COLOR_WIDGET_FOREGROUND));
						
						if ( Utils.isDarkAppearanceNativeWindows()){
							oldColor = Colors.getSystemColor(control.getDisplay(), SWT.COLOR_WIDGET_FOREGROUND);
						}
					}else {
						e.gc.setBackground(Colors.white);
						e.gc.setForeground(Colors.blue);
					}

					int xStart = (fullWidth - allBoxesWidth) / 2;
					int yStart = printArea.y + sizeAccess.y + textPadding;
					for (int i = 0; i < numBoxes; i++) {
						Rectangle r = new Rectangle(xStart + (i * boxSizeAndPadding),
								yStart, boxSize, boxSize);
						e.gc.fillRectangle(r);
						e.gc.setForeground(Colors.blues[Colors.BLUES_DARKEST]);
						e.gc.drawRectangle(r);
						if (isCodeVisible()) {
							e.gc.setForeground(oldColor);
							GCStringPrinter.printString(e.gc, "" + drawAccessCode.charAt(i),
									r, false, false, SWT.CENTER);
						}
					}
				}
			});

			soToClipboard = (SWTSkinObjectText) skin.getSkinObject("pair-clipboard");

			soToClipboard.addUrlClickedListener(new SWTSkinObjectText_UrlClickedListener() {
				@Override
				public boolean urlClicked(URLInfo urlInfo) {
					if (urlInfo.url.equals("new")) {
						try {
							accessCode = pairingManager.getReplacementAccessCode();
						} catch (PairingException e) {
							// ignore.. if error, lastErrorUpdates will trigger
						}
						control.redraw();
						setupQR(accessCode);

						String s = soToClipboard.getText();
						int i = s.indexOf("|");
						if (i > 0) {
							soToClipboard.setText(s.substring(0, i - 1));
						}
					} else if (urlInfo.url.equals("clip")) {
						ClipboardCopy.copyToClipBoard(accessCode);
					}
					return true;
				}
			});
			SWTSkinButtonUtility btnToClipboard = new SWTSkinButtonUtility(
					soToClipboard);
			btnToClipboard.addSelectionListener(new ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
				}
			});

			skinnedDialog.addCloseListener(new SkinnedDialogClosedListener() {
				@Override
				public void skinDialogClosed(SkinnedDialog dialog) {
					skinnedDialog = null;
					pairingManager.removeListener(RemotePairingWindow.this);
					Utils.disposeSWTObjects(new Object[] {
						fontCode
					});
					if (pairingTest != null) {
						pairingTest.cancel();
					}
				}
			});

			if (showFTUX) {
				soFTUX.getControl().moveAbove(null);
			}
		}
		setCodeVisible(false);
		skinnedDialog.open();

		if (showFTUX) {
			switchToFTUX();
		} else {
			switchToCode();
		}
	}

	private void setupQR(final String ac) {
		if (soQR == null || soQR.isDisposed()) {
			return;
		}

		if (USE_OUR_QR) {
			new AEThread2("QRCodeGetter", true) {
				@Override
				public void run() {
					final File qrCode = pairingManager.getQRCode();
					if (qrCode == null) {
						setupQR_URL(ac);
						return;
					}
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							try {
								Display display = Display.getCurrent();

								InputStream is = new FileInputStream(qrCode);
								Image image = new Image(display, is);
								is.close();
								String id = "RemotePairing.qrCode";
								ImageLoader imageLoader = skin.getImageLoader(skin.getSkinProperties());
								imageLoader.addImage(id, image);

								soQR.setImageByID(id, null);
							} catch (Exception e) {
								setupQR_URL(ac);
								Debug.out(e);
							}
						}
					});
				}
			}.start();
		} else {
			setupQR_URL(ac);
		}
	}

	private void setupQR_URL(String ac) {
		
		URL server_url = PairingManagerFactory.getSingleton().getWebRemoteURL();
		
		String url = "https://chart.googleapis.com/chart?chs=150x150&cht=qr&chl="
				+ UrlUtils.encode( server_url.toExternalForm() + "?ac=" + ac + "&ref=1")
				+ "&choe=UTF-8&chld=|0";
		soQR.setImageUrl(url);
	}


	public void switchToFTUX() {
		SWTSkinObject soPairInstallArea = skin.getSkinObject("pair-install");
		if (soPairInstallArea != null) {
			soPairInstallArea.getControl().moveAbove(null);
		}
		soFTUX.setVisible(true);
		soCode.setVisible(false);
	}

	public void switchToCode() {
		// use somethingChanged to trigger testPairing if needed
		somethingChanged(pairingManager);

		Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {

				if (skinnedDialog == null || skinnedDialog.isDisposed()) {
					return;
				}

				SWTSkinObjectImage soImage = (SWTSkinObjectImage) skin.getSkinObject("status-image");
				if (soImage != null) {
					soImage.setImageByID("icon.spin", null);
				}

				SWTSkinObject soPairArea = skin.getSkinObject("reset-pair-area");
				if (soPairArea != null) {
					soPairArea.getControl().moveAbove(null);
				}
				soFTUX.setVisible(false);
				soCode.setVisible(true);
			}
		});
	}

	protected void testPairing(boolean delay) {
		if (SHOW_SPEW) {
			System.out.println("PAIR] Want testPairing; alreadyTested="
					+ alreadyTested + ";Delay?" + delay + ";"
					+ Debug.getCompressedStackTrace());
		}
		if (alreadyTested) {
			return;
		}

		lastPairingTestError = "";
		alreadyTested = true;

		storedToClipboardText = soToClipboard.getText();
		try {
			setCodeVisible(false);
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					control.redraw();
					SWTSkinObjectImage soImage = (SWTSkinObjectImage) skin.getSkinObject("status-image");
					if (soImage != null) {
						soImage.setImageByID("icon.spin", null);
					}
				}
			});

			String defColorID = Utils.isDarkAppearanceNative()?"#c0c0c0":"#000000";
			
			soStatusText.setTextID("remote.pairing.test.running");
			
			soStatusText.setTextColor(ColorCache.getColor(control.getDisplay(), defColorID ));
			soToClipboard.setText(" ");

			final PairingTestListener testListener = new PairingTestListener() {
				@Override
				public void testStarted(PairingTest test) {
				}

				@Override
				public void testComplete(PairingTest test) {
					if ( skinnedDialog == null || skinnedDialog.isDisposed() || control.isDisposed()) {
						return;
					}

					int outcome = test.getOutcome();
					String iconID = null;
					String colorID = defColorID;
					switch (outcome) {
						case PairingTest.OT_SUCCESS:
							fallBackStatusText = MessageText.getString("remote.pairing.test.success");
							iconID = "icon.success";
							colorID = "#007305";
							break;

						case PairingTest.OT_CANCELLED:
							fallBackStatusText = test.getErrorMessage();
							iconID = "icon.warning";
							colorID = "#A97000";
							break;

						case PairingTest.OT_SERVER_FAILED:
						case PairingTest.OT_SERVER_OVERLOADED:
						case PairingTest.OT_SERVER_UNAVAILABLE:
							fallBackStatusText = MessageText.getString(
									"remote.pairing.test.unavailable", new String[] {
										test.getErrorMessage()
									});
							iconID = "icon.warning";
							colorID = "#C98000";
							break;

						default:
							fallBackStatusText = MessageText.getString(
									"remote.pairing.test.fail", new String[] {
										test.getErrorMessage()
									});
							iconID = "icon.failure";
							colorID = "#c90000";
							break;
					}

					setCodeVisible(true);
					final String fIconID = iconID;
					somethingChanged(pairingManager);
					lastPairingTestError = pairingTest.getErrorMessage();
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							if ( !control.isDisposed()){
								control.redraw();
								SWTSkinObjectImage soImage = (SWTSkinObjectImage) skin.getSkinObject("status-image");
								if (soImage != null) {
									soImage.setImageByID(fIconID, null);
								}
							}
						}
					});
					updateToolTip();
					soStatusText.setText(fallBackStatusText);
					soStatusText.setTextColor(ColorCache.getColor(control.getDisplay(),
							colorID));
					soToClipboard.setText(storedToClipboardText);
				}
			};
			SimpleTimer.addEvent("testPairing", SystemTime.getOffsetTime(delay ? 5000
					: 0), new TimerEventPerformer() {
				@Override
				public void perform(TimerEvent event) {
					try {
						pairingTest = pairingManager.testService(PLUGINID_WEBUI,
								testListener);
					} catch (PairingException e) {
						finishFailedTest();

						setStatusToException(e);
						Debug.out(e);
					}

					if (pairingTest == null) {
						finishFailedTest();
					}
				}
			});

			if (DEBUG) {
				testListener.testComplete(testPairingClass);
				return;
			}
		} catch (Exception e) {
			finishFailedTest();

			setStatusToException(e);
			Debug.out(e);
		}
	}

	protected void setStatusToException(Exception e) {

		soStatusText.setText(Debug.getNestedExceptionMessage(e) + ". <A HREF=\"retry\">Try again</A>");
		soStatusText.setTextColor(ColorCache.getColor(control.getDisplay(),
				"#c90000"));

		SWTSkinObjectImage soImage = (SWTSkinObjectImage) skin.getSkinObject("status-image");
		if (soImage != null) {
			soImage.setImageByID("icon.failure", null);
		}
	}

	/**
	 *
	 *
	 * @since 4.1.0.5
	 */
	protected void updateToolTip() {
		SWTSkinObjectImage soImage = (SWTSkinObjectImage) skin.getSkinObject("status-image");
		if (soImage != null) {
			String s = lastPairingTestError;
			if (s == null) {
				s = "";
			}

			String status = pairingManager.getStatus();
			if (status != null && status.length() > 0) {
				if (s.length() > 0) {
					s += "\n";
				}
				s += "Pairing Status: " + status;
			}
			String lastPairingErr = pairingManager.getLastServerError();
			if (lastPairingErr != null && lastPairingErr.length() > 0) {
				if (s.length() > 0) {
					s += "\n";
				}
				s += "Pairing Error: " + lastPairingErr;
			}
			soImage.setTooltipID("!" + s + "!");
		}
	}

	private void finishFailedTest() {
		setCodeVisible(true);
		somethingChanged(pairingManager);
		if (storedToClipboardText != null && storedToClipboardText.length() > 0) {
			soToClipboard.setText(storedToClipboardText);
		}
	}

	protected void installWebUI() {
		final PluginInstaller installer = CoreFactory.getSingleton().getPluginManager().getPluginInstaller();

		StandardPlugin vuze_plugin = null;

		try {
			vuze_plugin = installer.getStandardPlugin(PLUGINID_WEBUI);

		} catch (Throwable e) {
		}

		if (vuze_plugin == null) {
			return;
		}

		if (vuze_plugin.isAlreadyInstalled()) {
			PluginInterface plugin = vuze_plugin.getAlreadyInstalledPlugin();
			plugin.getPluginState().setDisabled(false);
			return;
		}

		try {
			switchToFTUX();

			final SWTSkinObject soInstall = skin.getSkinObject("pairing-install");
			final SWTSkinObject soLearnMore = skin.getSkinObject("learn-more");
			if (soLearnMore != null) {
				soLearnMore.setVisible(false);
			}

			Map<Integer, Object> properties = new HashMap<>();

			properties.put(UpdateCheckInstance.PT_UI_STYLE,
					UpdateCheckInstance.PT_UI_STYLE_SIMPLE);

			properties.put(UpdateCheckInstance.PT_UI_PARENT_SWT_COMPOSITE,
					soInstall.getControl());

			properties.put(UpdateCheckInstance.PT_UI_DISABLE_ON_SUCCESS_SLIDEY, true);

			installer.install(new InstallablePlugin[] {
				vuze_plugin
			}, false, properties, new PluginInstallationListener() {
				@Override
				public void completed() {
					if (soLearnMore != null) {
						soLearnMore.setVisible(true);
					}
					switchToCode();
				}

				@Override
				public void cancelled() {
					Utils.execSWTThread(new AERunnable() {

						@Override
						public void runSupport() {

							if ( skinnedDialog != null && !skinnedDialog.isDisposed()){

								skinnedDialog.close();

								skinnedDialog = null;
							}
						}
					});
				}

				@Override
				public void failed(PluginException e) {

					Debug.out(e);
					//Utils.openMessageBox(Utils.findAnyShell(), SWT.OK, "Error",
					//		e.toString());
				}
			});

		} catch (Throwable e) {

			Debug.printStackTrace(e);
		}
	}

	// @see com.biglybt.core.pairing.PairingManagerListener#somethingChanged(com.biglybt.core.pairing.PairingManager)
	@Override
	public void somethingChanged(PairingManager pm) {
		if (skinnedDialog.isDisposed()) {
			return;
		}

		updateToolTip();

		String lastAccessCode = accessCode;

		accessCode = pairingManager.peekAccessCode();
		boolean newAccessCode = !StringCompareUtils.equals(lastAccessCode, accessCode);
		if (accessCode != null && getWebUI() != null && !alreadyTested
				&& !pm.hasActionOutstanding()) {
			if (newAccessCode) {
				// pause while registering..
				testPairing(true);
			} else {
				testPairing(false);
			}
		}else{
			String last_error = pm.getLastServerError();

			if ( last_error != null && last_error.length() > 0 ){

				soStatusText.setText(last_error);
				soStatusText.setTextColor(ColorCache.getColor(control.getDisplay(),
						"#c90000"));
			}
		}

		if (newAccessCode) {
			setupQR(accessCode);

			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					control.redraw();
				}
			});
		}
	}

	public boolean isCodeVisible() {
		return hideCode;
	}

	public void setCodeVisible(boolean hideCode) {
		this.hideCode = hideCode;

		if (soQR != null && !soQR.isDisposed()) {
			soQR.setVisible(hideCode);
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (control != null && !control.isDisposed()) {
					control.redraw();
					control.update();
				}
			}
		});
	}

	public static class testPairingClass
		implements PairingTest
	{
		int curOutcome = 0;

		int[] testOutcomes = {
			OT_SUCCESS,
			OT_FAILED,
			OT_CANCELLED,
			OT_SERVER_FAILED,
			OT_SERVER_OVERLOADED,
			OT_SERVER_UNAVAILABLE
		};

		String[] testErrs = {
			"Success",
			"Could Not Connect blah blah technical stuff",
			"You Cancelled (unpossible!)",
			"Server Failed",
			"Server Overloaded",
			"Server Unavailable",
		};

		public void inc() {
			curOutcome++;
			if (curOutcome == testOutcomes.length) {
				curOutcome = 0;
			}
		}

		@Override
		public int getOutcome() {
			return testOutcomes[curOutcome];
		}

		@Override
		public String getErrorMessage() {
			return testErrs[curOutcome];
		}

		@Override
		public void cancel() {
		}
	}
}
