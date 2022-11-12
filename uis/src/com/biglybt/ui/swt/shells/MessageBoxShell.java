/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.shells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.UIFunctionsUserPrompter;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.RememberedDecisionsManager;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter.URLInfo;
import com.biglybt.ui.swt.utils.ColorCache;

/**
 * A messagebox that allows you config the button
 *
 * @todo When key is pressed, cancel auto close timer
 */
public class MessageBoxShell
	implements UIFunctionsUserPrompter
{

	public static final String STATUS_TEXT_CLOSE = "__VUZE__MessageBoxShell__CLOSE";

	private final static int MIN_SIZE_X_DEFAULT = 300;

	private final static int MIN_SIZE_Y_DEFAULT = 120;

	private final static int MAX_SIZE_X_DEFAULT = 600;

	private static final int MIN_BUTTON_SIZE = 70;

	private static int numOpen = 0;

	private Shell parent;

	private int min_size_x 	= MIN_SIZE_X_DEFAULT;
	private int min_size_y 	= MIN_SIZE_Y_DEFAULT;
	private int max_size_x	= MAX_SIZE_X_DEFAULT;

	private final String title;

	private final String text;

	private String[] buttons;

	private Integer[] buttonVals;

	private int defaultButtonPos;

	private String rememberID = null;

	private String rememberText = null;

	private boolean rememberByDefault = false;

	private int rememberOnlyIfButtonPos = -1;

	private int autoCloseInMS = 0;

	private String html;

	private String url;

	private boolean	squish;

	private boolean autoClosed = false;

	private Object[] relatedObjects;

	private Image imgLeft;

	protected Color urlColor;

	private boolean handleHTML = true;

	private Image iconImage;

	private boolean	browser_follow_links;

	protected boolean isRemembered;

	private boolean supportsApplyToAll;
	private boolean	applyToAll;
	
	private String iconImageID;

	private UserPrompterResultListener resultListener;

	private int result;

	private Listener filterListener;

	private Shell shell;

	private boolean opened;

	private boolean useTextBox;

	private String cbMessageID;

	private int cbMinUserMode;

	private boolean cbEnabled;

	private String instanceID;

	private boolean	modal;

	private static Map<String, MessageBoxShell> mapInstances = new HashMap<>(1);

	public static void open(Shell parent, String title, String text,
			String[] buttons, int defaultOption, String rememberID,
			String rememberText, boolean bRememberByDefault, int autoCloseInMS,
			UserPrompterResultListener l) {

		MessageBoxShell messageBoxShell = new MessageBoxShell(title, text,
				buttons, defaultOption);
		messageBoxShell.setRemember(rememberID, bRememberByDefault, rememberText);
		messageBoxShell.setAutoCloseInMS(autoCloseInMS);
		messageBoxShell.setParent(parent);
		messageBoxShell.open(l);
	}

	public static boolean isOpen() {
		return numOpen > 0;
	}

	/**
	 * @param shellForChildren
	 * @param string
	 * @param string2
	 * @param strings
	 */
	public MessageBoxShell(final String title,
			final String text, final String[] buttons, final int defaultOption) {
		this.title = title;
		this.text = text;
		this.buttons = buttons == null ? new String[0] : buttons;
		this.defaultButtonPos = defaultOption;
	}

	/**
	 * ONLY FOR OLD EMP. DO NOT USE
	 */
	@Deprecated
	public MessageBoxShell(Shell parent, final String title,
			final String text, final String[] buttons, final int defaultOption) {
		this(title, text, buttons, defaultOption);
		this.parent = parent;
	}

	public MessageBoxShell(String title, String text) {
		this(title, text, null, 0);
	}

	/** Open a messagebox using resource keys for title/text
	 *
	 * @param parent Parent shell for messagebox
	 * @param style SWT styles for messagebox
	 * @param keyPrefix message bundle key prefix used to get title and text.
	 *         Title will be keyPrefix + ".title", and text will be set to
	 *         keyPrefix + ".text"
	 * @param textParams any parameters for text
	 */
	public MessageBoxShell(int style, String keyPrefix,
			String[] textParams) {
		if ((style & (0x7f << 5)) == 0) {
			// need at least one button
			style |= SWT.OK;
		}
		final Object[] buttonInfo = swtButtonStylesToText(style);

		this.title = MessageText.getString(keyPrefix + ".title");
		this.text = MessageText.getString(keyPrefix + ".text", textParams);
		this.buttons = (String[]) buttonInfo[0];
		this.defaultButtonPos = 0;
		this.rememberID = null;
		this.rememberText = null;
		this.rememberByDefault = false;
		this.autoCloseInMS = -1;
		this.buttonVals = (Integer[]) buttonInfo[1];

		setLeftImage(style & 0x1f);
	}

	/** Open a messagebox with actual title and text
	 *
	 * @param parent
	 * @param style
	 * @param title
	 * @param text
	 * @return
	 */
	public MessageBoxShell(int style, String title, String text) {
		if ((style & (0x7f << 5)) == 0) {
			// need at least one button
			style |= SWT.OK;
		}

		final Object[] buttonInfo = swtButtonStylesToText(style);

		this.title = title;
		this.text = text;
		this.buttons = (String[]) buttonInfo[0];
		this.defaultButtonPos = 0;
		this.rememberID = null;
		this.rememberText = null;
		this.rememberByDefault = false;
		this.autoCloseInMS = -1;
		this.buttonVals = (Integer[]) buttonInfo[1];

		setLeftImage(style & 0x1f);
	}

	public void setDefaultButtonUsingStyle(int defaultStyle) {
		Object[] defaultButtonInfo = swtButtonStylesToText(defaultStyle);

		int defaultIndex = 0;
		if (defaultButtonInfo.length > 0) {
			String name = ((String[]) defaultButtonInfo[0])[0];

			for (int i = 0; i < buttons.length; i++) {
				if (buttons[i].equals(name)) {
					defaultIndex = i;
					break;
				}
			}
		}
		defaultButtonPos = defaultIndex;
	}

	/**
	 * ONLY FOR OLD EMP.  DO NOT USE.
	 * <P>
	 * Use {@link #open(UserPrompterResultListener)}
	 * @return
	 */
	@Deprecated
	public int open() {
		open(false);
		return waitUntilClosed();
	}

	@Override
	public void open(UserPrompterResultListener l) {
		this.resultListener = l;
		open(false);
	}

	private void triggerResultListener(final int returnVal) {
		// TODO: use Utils.getOffOfSWTThread, ensure call listener implimentations
		// can handle non-SWT thread
		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				if (resultListener == null) {
					return;
				}
				int realResult = getButtonVal(returnVal);
				resultListener.prompterClosed(realResult);
			}
		});
	}

	private int getButtonVal(int buttonPos) {
		if (buttonVals == null) {
			return buttonPos;
		}
		if (buttonPos < 0 || buttonPos >= buttonVals.length) {
			return SWT.CANCEL;
		}
		return buttonVals[buttonPos].intValue();
	}

	private int getButtonPos(int buttonVal) {
		if (buttonVals == null) {
			return buttonVal;
		}
		for (int i = 0; i < buttonVals.length; i++) {
			if (buttonVals[i] == buttonVal) {
				return i;
			}
		}
		return -1;
	}

	private void open(final boolean useCustomShell) {
		if (rememberID != null) {
			int rememberedDecision = RememberedDecisionsManager.getRememberedDecision(rememberID);
			if (rememberedDecision >= 0
					&& (rememberOnlyIfButtonPos == -1 || rememberOnlyIfButtonPos == getButtonPos(rememberedDecision))) {
				result = getButtonPos(rememberedDecision);
				triggerResultListener(result);
				return;
			}
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				_open();
			}
		});

		return;
	}

	private void _open() {
		if (instanceID != null) {
			if (mapInstances.containsKey(instanceID)) {
				MessageBoxShell mb = mapInstances.get(instanceID);
				if (mb.shell != null && !mb.shell.isDisposed()) {
					mb.shell.open();
					return;
				}
			}
			mapInstances.put(instanceID, this);
		}

		result = -1;

		boolean ourParent = false;
		if (parent == null || parent.isDisposed()) {
			parent = Utils.findAnyShell();
			ourParent = true;
			if (parent == null || parent.isDisposed()) {
				triggerResultListener(result);
				return;
			}
		}

		final Display display = parent.getDisplay();

		//APPLICATION_MODAL causes some crazy sht to happen on Windows.
		// Example: 5 windows open in APPLICATION MODAL mode,
		// and somehow none of them show until you do a "Window->Bring To Front"
		// which only makes ONE visible

		int	shell_style = SWT.DIALOG_TRIM | SWT.RESIZE;

		if ( modal ){

			shell_style |= SWT.APPLICATION_MODAL;
		}

		shell = ShellFactory.createShell(parent, shell_style);
		if (title != null) {
			shell.setText(title);
		}

		shell.addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				mapInstances.remove(instanceID);

				if (iconImageID != null) {
					ImageLoader.getInstance().releaseImage(iconImageID);
				}
				triggerResultListener(result);
				if (display != null && !display.isDisposed() && filterListener != null) {
					display.removeFilter(SWT.Traverse, filterListener);
				}

				numOpen--;
			}
		});

		GridLayout gridLayout = new GridLayout();

		if ( squish ){
			gridLayout.verticalSpacing 		= 0;
			gridLayout.horizontalSpacing	= 0;
			gridLayout.marginLeft			= 0;
			gridLayout.marginRight			= 0;
			gridLayout.marginTop			= 0;
			gridLayout.marginBottom			= 0;
			gridLayout.marginWidth			= 0;
			gridLayout.marginHeight			= 0;
		}

		shell.setLayout(gridLayout);
		Utils.setShellIcon(shell);

		UISkinnableSWTListener[] listeners = UISkinnableManagerSWT.getInstance().getSkinnableListeners(
				MessageBoxShell.class.toString());
		for (int i = 0; i < listeners.length; i++) {
			listeners[i].skinBeforeComponents(shell, this, relatedObjects);
		}

		FormData formData;
		GridData gridData;

		Composite textComposite = shell;
		if (imgLeft != null) {
			textComposite = new Composite(shell, SWT.NONE);
			textComposite.setForeground(shell.getForeground());
			GridLayout gl = new GridLayout(2, false);
			gl.horizontalSpacing = 10;
			textComposite.setLayout(gl);
			textComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
			Label lblImage = new Label(textComposite, SWT.NONE);
			lblImage.setImage(imgLeft);
			lblImage.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		} else if (!squish) {
			textComposite = new Composite(shell, SWT.NONE);
			GridLayout gl = new GridLayout(2, false);
			gl.marginWidth = 5;
			textComposite.setLayout(gl);
			textComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
		}

		Control linkControl;
		if ( text != null && text.length() > 0 ){
			if (useTextBox()) {
				linkControl = createTextBox(textComposite, text);
			} else {
				linkControl = createLinkLabel(textComposite, text);
			}
		}else{
			linkControl = null;
		}

		if ((html != null && html.length() > 0)
				|| (url != null && url.length() > 0)) {
			try {
				final BrowserWrapper browser = Utils.createSafeBrowser(shell, SWT.NONE);
				if (url != null && url.length() > 0) {
					browser.setUrl(url);
				} else {
					browser.setText(html);
				}
				GridData gd = new GridData(GridData.FILL_BOTH);
				gd.heightHint = 200;

				browser.getControl().setLayoutData(gd);
				browser.addProgressListener(new ProgressListener() {
					@Override
					public void completed(ProgressEvent event) {
						if (shell == null || shell.isDisposed()) {
							return;
						}
						browser.addLocationListener(new LocationListener() {
							@Override
							public void changing(LocationEvent event) {
								event.doit = browser_follow_links;
							}

							@Override
							public void changed(LocationEvent event) {
							}
						});
						browser.addOpenWindowListener(new BrowserWrapper.OpenWindowListener() {
							@Override
							public void open(BrowserWrapper.WindowEvent event) {
								event.setRequired( true );
							}
						});
					}

					@Override
					public void changed(ProgressEvent event) {
					}
				});


				browser.addStatusTextListener(new StatusTextListener() {
					@Override
					public void changed(StatusTextEvent event) {
						if(STATUS_TEXT_CLOSE.equals(event.text)) {
							//For some reason disposing the shell / browser in the same Thread makes
							//ieframe.dll crash on windows.
							Utils.execSWTThreadLater(0, new Runnable() {
								@Override
								public void run() {
									if(!browser.isDisposed() && ! shell.isDisposed()) {
										shell.close();
									}
								}
							});
						}
					}

				});

			} catch (Exception e) {
				Debug.out(e);
				if (html != null) {
					Text text = new Text(shell, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
					text.setText(html);
					GridData gd = new GridData(GridData.FILL_BOTH);
					gd.heightHint = 200;
					text.setLayoutData(gd);
				}
			}

			if ( linkControl != null ){
				gridData = new GridData(GridData.FILL_HORIZONTAL);
				linkControl.setLayoutData(gridData);
			}
		} else {
			if ( linkControl != null ){
				gridData = new GridData(GridData.FILL_BOTH);
				linkControl.setLayoutData(gridData);
			}
		}


		if (!squish
				&& (autoCloseInMS > 0 || rememberID != null || (cbMessageID != null && Utils.getUserMode() >= cbMinUserMode))) {
			Label lblPadding = new Label(shell, SWT.NONE);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.heightHint = 5;
			lblPadding.setLayoutData(gridData);
		}

		// Closing in..
		if (autoCloseInMS > 0) {
			final BufferedLabel lblCloseIn = new BufferedLabel(shell, SWT.WRAP | SWT.DOUBLE_BUFFERED);
			lblCloseIn.setForeground(shell.getForeground());
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			if ( !squish ){
				gridData.horizontalIndent = 5;
			}
			lblCloseIn.setText(
				MessageText.getString("popup.closing.in",
					new String[] {
						String.valueOf(autoCloseInMS/1000)
					}));

			lblCloseIn.setLayoutData(gridData);
			long endOn = SystemTime.getCurrentTime() + autoCloseInMS;
			lblCloseIn.setData("CloseOn", new Long(endOn));
			SimpleTimer.addPeriodicEvent("autoclose", 500, new TimerEventPerformer() {
				@Override
				public void perform(TimerEvent event) {
					if (shell.isDisposed()) {
						event.cancel();
						return;
					}
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							if (!shell.isDisposed()) {
								boolean bDelayPaused = lblCloseIn.getData("DelayPaused") != null;
								if (bDelayPaused) {
									return;
								}

								long endOn = ((Long) lblCloseIn.getData("CloseOn")).longValue();
								if (SystemTime.getCurrentTime() > endOn) {
									result = defaultButtonPos;
									autoClosed = true;
									shell.dispose();
								} else {
									String sText = "";

									if (lblCloseIn.isDisposed())
										return;

									if (!bDelayPaused) {
										long delaySecs = (endOn - SystemTime.getCurrentTime()) / 1000;
										sText = MessageText.getString("popup.closing.in",
												new String[] {
													String.valueOf(delaySecs)
												});
									}

									lblCloseIn.setText(sText);
								}
							}
						}
					});
				}
			});

			SimpleTimer.addPeriodicEvent("OverPopup", 100, new TimerEventPerformer() {
				boolean wasOver = true;

				long lEnterOn = 0;

				@Override
				public void perform(final TimerEvent event) {
					if (shell.isDisposed()) {
						event.cancel();
						return;
					}
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							if (shell.isDisposed()) {
								event.cancel();
								return;
							}
							boolean isOver = shell.getBounds().contains(
									shell.getDisplay().getCursorLocation());
							if (isOver != wasOver) {
								wasOver = isOver;
								if (isOver) {
									lblCloseIn.setData("DelayPaused", "");
									lEnterOn = SystemTime.getCurrentTime();
									lblCloseIn.setText("");
								} else {
									lblCloseIn.setData("DelayPaused", null);
									if (lEnterOn > 0) {
										long diff = SystemTime.getCurrentTime() - lEnterOn;
										long endOn = ((Long) lblCloseIn.getData("CloseOn")).longValue()
												+ diff;
										lblCloseIn.setData("CloseOn", new Long(endOn));
									}
								}
							}
						}
					});
				}
			});
		}

		boolean needSpacer = true;
		if (cbMessageID != null && Utils.getUserMode() >= cbMinUserMode) {
			needSpacer = false;
			Button cb = new Button(shell, SWT.CHECK);
			cb.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					cbEnabled = ((Button) e.widget).getSelection();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
			Messages.setLanguageText(cb, cbMessageID);
			cb.setSelection(cbEnabled);
		}


		if (rememberID != null) {
			Button checkRemember = new Button(shell, SWT.CHECK);
			checkRemember.setText(rememberText);
			checkRemember.setSelection(rememberByDefault);
			isRemembered = rememberByDefault;
			checkRemember.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					Button checkRemember = (Button) event.widget;
					isRemembered = checkRemember.getSelection();
				}
			});

			checkRemember.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					Button checkRemember = (Button) e.widget;
					isRemembered = checkRemember != null && checkRemember.getSelection();
					if (rememberID != null
							&& isRemembered
							&& (rememberOnlyIfButtonPos == -1 || rememberOnlyIfButtonPos == result)) {
						RememberedDecisionsManager.setRemembered(rememberID, getButtonVal(result));
					}
				}
			});
			
		}else if ( supportsApplyToAll ) {
			Button appkyToAll = new Button(shell, SWT.CHECK);
			appkyToAll.setText(MessageText.getString( "label.apply.to.all" ));
			
			appkyToAll.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					Button button = (Button) event.widget;
					applyToAll = button.getSelection();
				}
			});

		} else if (needSpacer)  {
			Button spacer = new Button(shell, SWT.CHECK);
			spacer.setVisible(false);
		}


		// Buttons

		if ( buttons.length > 0 ){
			Canvas line = new Canvas(shell,SWT.NO_BACKGROUND);
			line.addListener(SWT.Paint, new Listener() {
				@Override
				public void handleEvent(Event e) {
					Rectangle clientArea = ((Canvas) e.widget).getClientArea();
					e.gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_NORMAL_SHADOW));
					e.gc.drawRectangle(clientArea);
					clientArea.y++;
					e.gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
					e.gc.drawRectangle(clientArea);
				}
			});
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.heightHint = 2;
			line.setLayoutData(gridData);

			Composite cButtons = new Composite(shell, SWT.NONE);
			FormLayout layout = new FormLayout();

			cButtons.setLayout(layout);
			gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
			cButtons.setLayoutData(gridData);

			Control lastButton = null;

			Listener buttonListener = new Listener() {

				@Override
				public void handleEvent(Event event) {
					result = ((Integer) event.widget.getData()).intValue();
					shell.dispose();
				}

			};

			int buttonWidth = 0;
			Button[] swtButtons = new Button[buttons.length];
			for (int i = 0; i < buttons.length; i++) {
				Button button = new Button(cButtons, SWT.PUSH);
				swtButtons[i] = button;
				button.setData(Integer.valueOf(i));
				button.setText(buttons[i]);
				button.addListener(SWT.Selection, buttonListener);

				formData = new FormData();
				if (lastButton != null) {
					formData.left = new FormAttachment(lastButton, 5);
				}

				button.setLayoutData(formData);

				Point size = button.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				if (size.x > buttonWidth) {
					buttonWidth = size.x;
				}

				if (i == defaultButtonPos) {
					button.setFocus();
					shell.setDefaultButton(button);
				}

				lastButton = button;
			}

			if (buttonWidth > 0) {
				if (buttonWidth < MIN_BUTTON_SIZE) {
					buttonWidth = MIN_BUTTON_SIZE;
				}
				for (int i = 0; i < buttons.length; i++) {
					Point size = swtButtons[i].computeSize(buttonWidth, SWT.DEFAULT);
					swtButtons[i].setSize(size);
					formData = (FormData) swtButtons[i].getLayoutData();
					formData.width = buttonWidth;
				}
			}
		}

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent event) {
				if (event.detail == SWT.TRAVERSE_ESCAPE) {
					shell.dispose();
				}
			}
		});

		filterListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (event.detail == SWT.TRAVERSE_ARROW_NEXT) {
					event.detail = SWT.TRAVERSE_TAB_NEXT;
					event.doit = true;
				} else if (event.detail == SWT.TRAVERSE_ARROW_PREVIOUS) {
					event.detail = SWT.TRAVERSE_TAB_PREVIOUS;
					event.doit = true;
				}
			}
		};
		display.addFilter(SWT.Traverse, filterListener);

		shell.pack();
		Point size = shell.getSize();
		if (size.x < min_size_x) {
			size.x = min_size_x;
			shell.setSize(size);
		} else if (size.x > max_size_x) {
			size = shell.computeSize(max_size_x, SWT.DEFAULT);
			shell.setSize(size);
		}

		if (size.y < min_size_y) {
			size.y = min_size_y;
			shell.setSize(size);
		}

		Shell centerRelativeToShell = parent;
		if (ourParent) {
			Control cursorControl = display.getCursorControl();
			if (cursorControl != null) {
				centerRelativeToShell = cursorControl.getShell();
			}
		}
		Utils.centerWindowRelativeTo(shell, centerRelativeToShell);

		for (int i = 0; i < listeners.length; i++) {
			listeners[i].skinAfterComponents(shell, this, relatedObjects);
		}

		shell.open();
		opened = true;
		numOpen++;


		return;
	}

	/**
	 * @param textComposite
	 * @param text2
	 * @return
	 */
	private Control createTextBox(Composite textComposite, String text2) {
		Text tb = new Text(textComposite, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL
				| SWT.V_SCROLL | SWT.READ_ONLY);
		tb.setText(text2);

		return tb;
	}

	private Canvas createLinkLabel(final Composite shell, final String text) {

		final Canvas canvas = new Canvas(shell, SWT.None) {
			@Override
			public Point computeSize(int wHint, int hHint, boolean changed) {
				Rectangle area = new Rectangle(0, 0, wHint < 0 ? max_size_x : wHint,
						5000);
				GC gc = new GC(this);
				GCStringPrinter sp = new GCStringPrinter(gc, text, area, true, false,
						SWT.WRAP | SWT.TOP);
				sp.calculateMetrics();
				gc.dispose();
				Point size = sp.getCalculatedSize();
				return size;
			}
		};

		Listener l = new Listener() {
			GCStringPrinter sp;

			@Override
			public void handleEvent(Event e) {
				if (!handleHTML) {
					if (e.type == SWT.Paint) {
						Rectangle area = canvas.getClientArea();
						e.gc.setForeground(shell.getForeground());
						GCStringPrinter.printString(e.gc, text, area, true, false, SWT.WRAP
								| SWT.TOP);
					}
					return;
				}

				if (e.type == SWT.Paint) {
					Rectangle area = canvas.getClientArea();
					sp = new GCStringPrinter(e.gc, text, area, true, false, SWT.WRAP
							| SWT.TOP);
					sp.setUrlColor(e.gc.getDevice().getSystemColor(SWT.COLOR_LINK_FOREGROUND));
					if (urlColor != null) {
						sp.setUrlColor(urlColor);
					}
					e.gc.setForeground(shell.getForeground());
					sp.printString();
				} else if (e.type == SWT.MouseMove) {
					if (sp != null) {
						URLInfo hitUrl = sp.getHitUrl(e.x, e.y);
						if (hitUrl != null) {
							canvas.setCursor(canvas.getDisplay().getSystemCursor(
									SWT.CURSOR_HAND));
							Utils.setTT(canvas,hitUrl.url);
						} else {
							canvas.setCursor(canvas.getDisplay().getSystemCursor(
									SWT.CURSOR_ARROW));
							Utils.setTT(canvas,null);
						}
					}
				} else if (e.type == SWT.MouseUp) {
					if (sp != null) {
						URLInfo hitUrl = sp.getHitUrl(e.x, e.y);
						if (hitUrl != null && !hitUrl.url.startsWith(":")) {
							Utils.launch(hitUrl.url);
						}
					}
				}
			}
		};
		canvas.addListener(SWT.Paint, l);
		if (handleHTML) {
			canvas.addListener(SWT.MouseMove, l);
			canvas.addListener(SWT.MouseUp, l);
		}

		ClipboardCopy.addCopyToClipMenu(canvas,
				new ClipboardCopy.copyToClipProvider() {
					@Override
					public String getText() {
						return (text);
					}
				});

		return canvas;
	}

	@Override
	public String getHtml() {
		return html;
	}

	@Override
	public void setHtml(String html) {
		this.html = html;
	}

	@Override
	public void setUrl(String url) {
		this.url = url;
	}

	public void
	setSize(
		int		width,
		int		height )
	{
		min_size_x	= width;
		max_size_x	= width;
		min_size_y	= height;
	}
	/**
	 * @return the rememberID
	 */
	@Override
	public String getRememberID() {
		return rememberID;
	}

	/**
	 *
	 * @param rememberID
	 * @param rememberByDefault
	 * @param rememberText null if you want the default
	 */
	@Override
	public void setRemember(String rememberID, boolean rememberByDefault,
	                        String rememberText) {
		this.rememberID = rememberID;
		this.rememberByDefault = rememberByDefault;
		this.rememberText = rememberText;
		if (this.rememberText == null) {
			this.rememberText = MessageText.getString("MessageBoxWindow.rememberdecision");
		}
	}

	/**
	 * @return the rememberText
	 */
	@Override
	public String getRememberText() {
		return rememberText;
	}

	/**
	 * @param rememberText the rememberText to set
	 */
	@Override
	public void setRememberText(String rememberText) {
		this.rememberText = rememberText;
	}

	/**
	 * @return the autoCloseInMS
	 */
	@Override
	public int getAutoCloseInMS() {
		return autoCloseInMS;
	}

	/**
	 * @param autoCloseInMS the autoCloseInMS to set
	 */
	@Override
	public void setAutoCloseInMS(int autoCloseInMS) {
		this.autoCloseInMS = autoCloseInMS;
	}

	public void setSquish( boolean b ){ squish = b; }

	/**
	 * @return the autoClosed
	 */
	@Override
	public boolean isAutoClosed() {
		return autoClosed;
	}

	/**
	 * Only use this if you REALLY know what you're doing as in general it is a bad thing - check
	 * comments in this class
	 * @param m
	 */

	public void
	setModal(
		boolean		m )
	{
		modal = m;
	}

	// @see UIFunctionsUserPrompter#setRelatedObject(java.lang.Object)
	@Override
	public void setRelatedObject(Object relatedObject) {
		this.relatedObjects = new Object[] {
			relatedObject
		};
	}

	// @see UIFunctionsUserPrompter#setRelatedObjects(java.lang.Object[])
	@Override
	public void setRelatedObjects(Object[] relatedObjects) {
		this.relatedObjects = relatedObjects;
	}

	public Object[] getRelatedObjects() {
		return relatedObjects;
	}

	/**
	 * @return
	 *
	 * @since 4.0.0.1
	 */
	public Object getLeftImage() {
		return imgLeft == iconImage ? null : imgLeft;
	}

	public void setLeftImage(Image imgLeft) {
		this.imgLeft = imgLeft;
	}

	/**
	 * Replaces Image on left with icon
	 *
	 * @param icon SWT.ICON_ERROR, ICON_INFORMATION, ICON_QUESTION, ICON_WARNING, ICON_WORKING
	 *
	 * @since 3.0.1.7
	 */
	public void setLeftImage(final int icon) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				setLeftImage(Display.getDefault().getSystemImage(icon));
				iconImage = Display.getDefault().getSystemImage(icon);
			}
		});
	}

	@Override
	public void setIconResource(String resource) {
		if (Utils.runIfNotSWTThread(() -> setIconResource(resource))) {
			return;
		}

		iconImageID = null;
		if (resource.equals("info")) {
			iconImage = Display.getDefault().getSystemImage(SWT.ICON_INFORMATION);

		} else if (resource.equals("warning")) {
			iconImage = Display.getDefault().getSystemImage(SWT.ICON_WARNING);

		} else if (resource.equals("error")) {
			iconImage = Display.getDefault().getSystemImage(SWT.ICON_ERROR);

		} else {
			iconImage = ImageLoader.getInstance().getImage(resource);
			iconImageID = resource;
		}
		setLeftImage(iconImage);
	}

	public static void main(String[] args) {
		Display display = Display.getDefault();
		Shell shell = new Shell(display, SWT.SHELL_TRIM);
		shell.open();

		MessageBoxShell messageBoxShell = new MessageBoxShell(
				"Title",
				"Test\n"
						+ "THis is a very long line that tests whether the box gets really wide which is something we don't want.\n"
						+ "A <A HREF=\"Link\">link</A> for <A HREF=\"http://moo.com\">you</a>",
				new String[] {
					"Okay",
					"Cancyyyyyy",
					"Maybe"
				}, 1);
		messageBoxShell.setRemember("test2", false,
				MessageText.getString("MessageBoxWindow.nomoreprompting"));
		messageBoxShell.setAutoCloseInMS(15000);
		messageBoxShell.setParent(shell);
		messageBoxShell.setHtml("<b>Moo</b> goes the cow<p><hr>");
		messageBoxShell.open(new UserPrompterResultListener() {

			@Override
			public void prompterClosed(int returnVal) {
				System.out.println(returnVal);
			}
		});
		while (!shell.isDisposed()) {
			if (!display.isDisposed() && !display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	public int getRememberOnlyIfButton() {
		return rememberOnlyIfButtonPos;
	}

	@Override
	public void setRememberOnlyIfButton(int rememberOnlyIfButton) {
		this.rememberOnlyIfButtonPos = rememberOnlyIfButton;
	}

	public Color getUrlColor() {
		return urlColor;
	}

	public void
	setBrowserFollowLinks(
		boolean		follow )
	{
		browser_follow_links = follow;
	}

	public void setUrlColor(Color colorURL) {
		this.urlColor = colorURL;
	}

	/**
	 * @param b
	 *
	 * @since 3.0.5.3
	 */
	public void setHandleHTML(boolean handleHTML) {
		this.handleHTML = handleHTML;
	}

	public boolean isRemembered() {
		return isRemembered;
	}

	/**
	 * NOT RECOMMENDED!
	 * <P>
	 * TODO: Occasionaly inspect list of callers and make them use
	 *       {@link UserPrompterResultListener} if possible
	 */
	@Override
	public int waitUntilClosed() {
		final AESemaphore sem = new AESemaphore("waitUntilClosed");

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				try {
					if (shell == null) {
						return;
					}
					if (!opened) {
						shell.open();
					}
					Utils.readAndDispatchLoop(shell);
					return;
				} finally {
					sem.releaseForever();
				}
			}
		});

		sem.reserve();

		int realResult = getButtonVal(result);

		return realResult;
	}

	public int getResult() {
		return result;
	}

	private static Object[] swtButtonStylesToText(int style) {
		List<String> buttons = new ArrayList<>(2);
		List<Integer> buttonVal = new ArrayList<>(2);
		int buttonCount = 0;
		if ((style & SWT.OK) > 0) {
			buttons.add(MessageText.getString("Button.ok"));
			buttonVal.add(Integer.valueOf(SWT.OK));
			buttonCount++;
		}
		if ((style & SWT.YES) > 0) {
			buttons.add(MessageText.getString("Button.yes"));
			buttonVal.add(Integer.valueOf(SWT.YES));
			buttonCount++;
		}
		if ((style & SWT.NO) > 0) {
			buttons.add(MessageText.getString("Button.no"));
			buttonVal.add(Integer.valueOf(SWT.NO));
			buttonCount++;
		}
		if ((style & SWT.CANCEL) > 0) {
			buttons.add(MessageText.getString("Button.cancel"));
			buttonVal.add(Integer.valueOf(SWT.CANCEL));
			buttonCount++;
		}
		if ((style & SWT.ABORT) > 0) {
			buttons.add(MessageText.getString("Button.abort"));
			buttonVal.add(Integer.valueOf(SWT.ABORT));
			buttonCount++;
		}
		if ((style & SWT.RETRY) > 0) {
			buttons.add(MessageText.getString("Button.retry"));
			buttonVal.add(Integer.valueOf(SWT.RETRY));
			buttonCount++;
		}
		if ((style & SWT.IGNORE) > 0) {
			buttons.add(MessageText.getString("Button.ignore"));
			buttonVal.add(Integer.valueOf(SWT.IGNORE));
			buttonCount++;
		}
		return new Object[] {
			buttons.toArray(new String[buttonCount]),
			buttonVal.toArray(new Integer[buttonCount])
		};
	}

	public String[] getButtons() {
		return buttons;
	}

	public void setButtons(String[] buttons) {
		this.buttons = buttons;
	}

	public void setButtons(int defaltButtonPos, String[] buttons, Integer[] buttonVals) {
		this.defaultButtonPos = defaltButtonPos;
		this.buttons = buttons;
		this.buttonVals = buttonVals;
	}

	/**
	 * Adds a checkbox to the message box. Currently only one checkbox can be
	 * made via this method.
	 */
	public void addCheckBox(String cbMessageID, int cbMinUserMode, boolean defaultOn) {
		this.cbMessageID = cbMessageID;
		this.cbMinUserMode = cbMinUserMode;
		this.cbEnabled = defaultOn;
	}

	public boolean getCheckBoxEnabled() {
		return cbEnabled;
	}

	public void
	setApplyToAllEnabled()
	{
		supportsApplyToAll = true;
	}
	
	public boolean
	getApplyToAll()
	{
		return( applyToAll );
	}
	
	public Shell getParent() {
		return parent;
	}

	public void setParent(Shell parent) {
		this.parent = parent;
	}

	public void close() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (shell != null && !shell.isDisposed()) {
					shell.dispose();
				}
			}
		});
	}

	/**
	 * @param useTextBox The useTextBox to set.
	 */
	public void setUseTextBox(boolean useTextBox) {
		this.useTextBox = useTextBox;
	}

	/**
	 * @return Returns the useTextBox.
	 */
	public boolean useTextBox() {
		return useTextBox;
	}

	public void setLeftImage(final String id) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				setLeftImage(ImageLoader.getInstance().getImage(id));
			}
		});
	}

	@Override
	public void setOneInstanceOf(String instanceID) {
		this.instanceID = instanceID;
	}
}
