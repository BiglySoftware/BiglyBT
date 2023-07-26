/*
 * Created on Mar 7, 2006 10:42:32 PM
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.ui.swt.shells;

import java.util.ArrayList;

import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.UISkinnableManagerSWT;
import com.biglybt.ui.swt.UISkinnableSWTListener;
import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.shells.GCStringPrinter.URLInfo;

import com.biglybt.ui.swt.utils.ColorCache;

/**
 *
 * +=====================================+
 * | +----+                              |
 * | |Icon| Big Bold Title               |
 * | +----+                              |
 * | Wrapping message text               |
 * | with optional URL links             |
 * | +-----+                             |
 * | |BGImg|           XX more slideys.. |
 * | | Icon|          Closing in XX secs |
 * | +-----+  [HideAll] [Details] [Hide] |
 * +=====================================+
 *
 * @author TuxPaper
 * @created Mar 7, 2006
 *
 */
public class MessageSlideShell
{
	private static final boolean DEBUG = false;

	/** Slide until there's this much gap between shell and edge of screen */
	private final static int EDGE_GAP = 0;

	/** Width used when BG image can't be loaded */
	private final static int SHELL_DEF_WIDTH = 280;

	/** Standard height of the shell.  Shell may grow depending on text */
	private final static int SHELL_MIN_HEIGHT = 150;

	/** Maximum height of popup.  If text is too long, the full text will be
	 * put into details.
	 */
	private final static int SHELL_MAX_HEIGHT = 330;

	/** Width of the details shell */
	private final static int DETAILS_WIDTH = 550;

	/** Height of the details shell */
	private final static int DETAILS_HEIGHT = 180;

	/** Synchronization for popupList */
	private final static AEMonitor monitor = new AEMonitor("slidey_mon");

	/** List of all popups ever created */
	private static ArrayList<PopupParams> historyList = new ArrayList<>();

	/** Current popup being displayed */
	private static int currentPopupIndex = -1;

	/** Index of first message which the user has not seen (index) - set to -1 if we don't care. :) **/
	private static int firstUnreadMessage = -1;

	/** Shell for popup */
	private Shell shell;

	/** Composite in shell */
	private Composite cShell;

	/** popup could and closing in xx seconds label */
	private Label lblCloseIn;

	/** Button that hides all slideys in the popupList.  Visible only when there's
	 * more than 1 slidey
	 */
	private Button btnHideAll;

	/** Button to move to next message.  Text changes from "Hide" to "Next"
	 * appropriately.
	 */
	private Button btnNext;

	/** paused state of auto-close delay */
	private boolean bDelayPaused = false;

	/** List of SWT objects needing disposal */
	private ArrayList<Object> disposeList = new ArrayList<>();

	/** Text to put into details popup */
	private String sDetails;

	/** Position this popup is in the history list */
	private int idxHistory;

	protected Color colorURL;

	private Color colorFG;

	/** Open a popup using resource keys for title/text
	 *
	 * @param display Display to create the shell on
	 * @param iconID SWT.ICON_* constant for icon in top left
	 * @param keyPrefix message bundle key prefix used to get title and text.
	 *         Title will be keyPrefix + ".title", and text will be set to
	 *         keyPrefix + ".text"
	 * @param details actual text for details (not a key)
	 * @param textParams any parameters for text
	 *
	 * @note Display moved to end to remove conflict in constructors
	 */
	public MessageSlideShell(Display display, int iconID, String keyPrefix,
			String details, String[] textParams, int timeoutSecs ) {
		this(display, iconID, MessageText.getString(keyPrefix + ".title"),
				MessageText.getString(keyPrefix + ".text", textParams), details, timeoutSecs);
	}

	/**
	 *
	 * @param display
	 * @param iconID
	 * @param keyPrefix
	 * @param details
	 * @param textParams
	 * @param relatedObjects
	 * @param timeoutSecs = -1 -> use default timeout, 0 -> no timeout, other -> timeout in secs
	 */
	public MessageSlideShell(Display display, int iconID, String keyPrefix,
			String details, String[] textParams, Object[] relatedObjects,
			int timeoutSecs ) {
		this(display, iconID, MessageText.getString(keyPrefix + ".title"),
				MessageText.getString(keyPrefix + ".text", textParams), details,
				relatedObjects, timeoutSecs);
	}

	public MessageSlideShell(Display display, int iconID, String title,
			String text, String details, int timeoutSecs) {
		this(display, iconID, title, text, details, null, timeoutSecs);
	}

	/**
	 * Open Mr Slidey
	 *
	 * @param display Display to create the shell on
	 * @param iconID SWT.ICON_* constant for icon in top left
	 * @param title Text to put in the title
	 * @param text Text to put in the body
	 * @param details Text displayed when the Details button is pressed.  Null
	 *                 for disabled Details button.
	 * @param timeoutSecs = -1 -> use default timeout, 0 -> no timeout, other -> timeout in secs
	 */
	public MessageSlideShell(Display display, int iconID, String title,
			String text, String details, Object[] relatedObjects, int timeoutSecs ) {
		try {
			monitor.enter();

			PopupParams popupParams = new PopupParams(iconID, title, text, details,
					relatedObjects, timeoutSecs );
			addToHistory(popupParams);
			if (currentPopupIndex < 0) {
				create(display, popupParams, true);
			}
		} catch (Exception e) {
			Logger.log(new LogEvent(LogIDs.GUI, "Mr. Slidey Init", e));
			disposeShell(shell);
			Utils.disposeSWTObjects(disposeList);
		} finally {
			monitor.exit();
		}
	}

	/**
	 * @param popupParams
	 *
	 * @since 4.1.0.5
	 */
	private static void addToHistory(PopupParams popupParams) {
		monitor.enter();
		try {
			historyList.add(popupParams);
		} finally {
			monitor.exit();
		}
	}

	private MessageSlideShell(Display display, PopupParams popupParams,
			boolean bSlide) {
		create(display, popupParams, bSlide);
	}

	public static void displayLastMessage(final Display display,
			final boolean last_unread) {
		display.asyncExec(new AERunnable() {
			@Override
			public void runSupport() {
				if (historyList.isEmpty()) {
					return;
				}
				if (currentPopupIndex >= 0) {
					return;
				} // Already being displayed.
				int msg_index = firstUnreadMessage;
				if (!last_unread || msg_index == -1) {
					msg_index = historyList.size() - 1;
				}
				new MessageSlideShell(display, historyList.get(msg_index), true);
			}
		});
	}

	/**
	 * Adds this message to the slide shell without forcing it to be displayed.
	 * @param relatedTo
	 */
	public static void recordMessage(int iconID, String title, String text,
			String details, Object[] relatedTo, int timeoutSecs ) {
		try {
			monitor.enter();
			addToHistory(new PopupParams(iconID, title, text, details, relatedTo, timeoutSecs));
			if (firstUnreadMessage == -1) {
				firstUnreadMessage = historyList.size() - 1;
			}
		} finally {
			monitor.exit();
		}
	}

	private void create(final Display display, final PopupParams popupParams,
			boolean bSlide) {

		firstUnreadMessage = -1; // Reset the last read message counter.

		GridData gridData;
		int style = SWT.ON_TOP;

		boolean bDisableSliding = COConfigurationManager.getBooleanParameter("GUI_SWT_DisableAlertSliding");
		if (bDisableSliding) {
			bSlide = false;
			style = SWT.NONE;
		}

		if (DEBUG)
			System.out.println("create " + (bSlide ? "SlideIn" : "") + ";"
					+ historyList.indexOf(popupParams) + ";");

		idxHistory = historyList.indexOf(popupParams);

		// 2 Assertions
		if (idxHistory < 0) {
			System.err.println("Not in popup history list");
			return;
		}

		if (currentPopupIndex == idxHistory) {
			System.err.println("Trying to open already opened!! " + idxHistory);
			return;
		}

		try {
			monitor.enter();
			currentPopupIndex = idxHistory;
		} finally {
			monitor.exit();
		}

		if (DEBUG)
			System.out.println("set currIdx = " + idxHistory);

		sDetails = popupParams.details;

		// Load Images
		Image imgIcon = popupParams.iconID <= 0 ? null
				: display.getSystemImage(popupParams.iconID);

		/*
		 * If forceTimer is true then we always show the counter for auto-closing the shell;
		 * otherwise proceed to the more fine-grained logic
		 */
			// if there's a link, or the info is non-information,
			// disable timer and mouse watching

		bDelayPaused = popupParams.iconID != SWT.ICON_INFORMATION || !bSlide;

		// Pause the auto-close delay when mouse is over slidey
		// This will be applies to every control
		final MouseTrackAdapter mouseAdapter = bDelayPaused ? null
				: new MouseTrackAdapter() {
					@Override
					public void mouseEnter(MouseEvent e) {
						bDelayPaused = true;
					}

					@Override
					public void mouseExit(MouseEvent e) {
						bDelayPaused = false;
					}
				};

		// Create shell & widgets
		if (bDisableSliding) {
			UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
			if (uiFunctions != null) {
				Shell mainShell = uiFunctions.getMainShell();
				if (mainShell != null) {
					shell = new Shell(mainShell, style);
				}
			}
		}
		if (shell == null) {
			shell = new Shell(display, style);
		}
		Utils.setShellIcon(shell);
		if (popupParams.title != null) {
			shell.setText(popupParams.title);
		}

		UISkinnableSWTListener[] listeners = UISkinnableManagerSWT.getInstance().getSkinnableListeners(
				MessageSlideShell.class.toString());
		for (int i = 0; i < listeners.length; i++) {
			try {
				listeners[i].skinBeforeComponents(shell, this, popupParams.relatedTo);
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		if (colorFG == null) {
			//colorFG = Colors.getSystemColor(display, SWT.COLOR_BLACK);
		}

		FormLayout shellLayout = new FormLayout();
		shell.setLayout(shellLayout);

		cShell = new Composite(shell, SWT.NULL);
		GridLayout layout = new GridLayout(3, false);
		cShell.setLayout(layout);

		FormData formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		cShell.setLayoutData(formData);

		Label lblIcon = new Label(cShell, SWT.NONE);
		lblIcon.setImage(imgIcon);
		lblIcon.setLayoutData(new GridData());

		if (popupParams.title != null) {
  		Label lblTitle = new Label(cShell, SWT.getVersion() < 3100 ? SWT.NONE
  				: SWT.WRAP);
  		gridData = new GridData(GridData.FILL_HORIZONTAL);
  		if (SWT.getVersion() < 3100)
  			gridData.widthHint = 140;
  		lblTitle.setLayoutData(gridData);
  		lblTitle.setForeground(colorFG);
  		lblTitle.setText(popupParams.title);
  		FontData[] fontData = lblTitle.getFont().getFontData();
  		fontData[0].setStyle(SWT.BOLD);
  		fontData[0].setHeight((int) (fontData[0].getHeight() * 1.5));
  		Font boldFont = new Font(display, fontData);
  		disposeList.add(boldFont);
  		lblTitle.setFont(boldFont);
		}

		final Button btnDetails = new Button(cShell, SWT.TOGGLE);
		btnDetails.setForeground(colorFG);
		Messages.setLanguageText(btnDetails, "label.details");
		gridData = new GridData();
		btnDetails.setLayoutData(gridData);
		btnDetails.addListener(SWT.MouseUp, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				try {
					boolean bShow = btnDetails.getSelection();
					if (bShow) {
						Shell detailsShell = new Shell(display, SWT.BORDER | SWT.ON_TOP);
						Utils.setShellIcon(detailsShell);
						detailsShell.setLayout(new FillLayout());
						StyledText textDetails = new StyledText(detailsShell, SWT.READ_ONLY
								| SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
						textDetails.setBackground(Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND));
						textDetails.setForeground(Colors.getSystemColor(display, SWT.COLOR_LIST_FOREGROUND));
						textDetails.setWordWrap(true);
						textDetails.setText(sDetails);
						detailsShell.layout();
						Rectangle shellBounds = shell.getBounds();
						int detailsWidth = DETAILS_WIDTH;
						int detailsHeight = DETAILS_HEIGHT;
						detailsShell.setBounds(shellBounds.x + shellBounds.width
								- detailsWidth, shellBounds.y - detailsHeight, detailsWidth,
								detailsHeight);
						detailsShell.open();
						shell.setData("detailsShell", detailsShell);
						shell.addDisposeListener(new DisposeListener() {
							@Override
							public void widgetDisposed(DisposeEvent e) {
								Shell detailsShell = (Shell) shell.getData("detailsShell");
								if (detailsShell != null && !detailsShell.isDisposed()) {
									detailsShell.dispose();
								}
							}
						});

						// disable auto-close on opening of details
						bDelayPaused = true;
						removeMouseTrackListener(shell, mouseAdapter);
					} else {
						Shell detailsShell = (Shell) shell.getData("detailsShell");
						if (detailsShell != null && !detailsShell.isDisposed()) {
							detailsShell.dispose();
						}
					}
				} catch (Exception e) {
					Logger.log(new LogEvent(LogIDs.GUI, "Mr. Slidey DetailsButton", e));
				}
			}
		});

		createLinkLabel(cShell, popupParams);

		lblCloseIn = new Label(cShell, SWT.TRAIL);
		lblCloseIn.setForeground(colorFG);
		// Ensure computeSize computes for 2 lined label
		lblCloseIn.setText(" \n ");
		gridData = new GridData(SWT.FILL, SWT.TOP, true, false);
		gridData.horizontalSpan = 3;
		lblCloseIn.setLayoutData(gridData);

		final Composite cButtons = new Composite(cShell, SWT.NULL);
		GridLayout gridLayout = new GridLayout();
		gridLayout.marginHeight = 0;
		gridLayout.marginWidth = 0;
		gridLayout.verticalSpacing = 0;
		if (Constants.isOSX)
			gridLayout.horizontalSpacing = 0;
		gridLayout.numColumns = (idxHistory > 0) ? 3 : 2;
		cButtons.setLayout(gridLayout);
		gridData = new GridData(GridData.HORIZONTAL_ALIGN_END
				| GridData.VERTICAL_ALIGN_CENTER);
		gridData.horizontalSpan = 3;
		cButtons.setLayoutData(gridData);

		btnHideAll = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnHideAll, "popup.error.hideall");
		btnHideAll.setVisible(false);
		btnHideAll.setForeground(Colors.getSystemColor(display, SWT.COLOR_BLACK));
		// XXX SWT.Selection doesn't work on latest GTK (2.8.17) & SWT3.2 for ON_TOP
		btnHideAll.addListener(SWT.MouseUp, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				cButtons.setEnabled(false);

				shell.dispose();
			}
		});

		if (idxHistory > 0) {
			final Button btnPrev = new Button(cButtons, SWT.PUSH);
			btnPrev.setForeground(Colors.getSystemColor(display, SWT.COLOR_BLACK));
			btnPrev.setText(MessageText.getString("popup.previous", new String[] {
				"" + idxHistory
			}));
			btnPrev.addListener(SWT.MouseUp, new Listener() {
				@Override
				public void handleEvent(Event arg0) {
					disposeShell(shell);
					int idx = historyList.indexOf(popupParams) - 1;
					if (idx >= 0) {
						PopupParams item = historyList.get(idx);
						showPopup(display, item, false);
						disposeShell(shell);
					}
				}
			});
		}

		btnNext = new Button(cButtons, SWT.PUSH);
		btnNext.setForeground(Colors.getSystemColor(display, SWT.COLOR_BLACK));
		int numAfter = historyList.size() - idxHistory - 1;
		setButtonNextText(numAfter);

		btnNext.addListener(SWT.MouseUp, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				if (DEBUG)
					System.out.println("Next Pressed");

				if (idxHistory + 1 < historyList.size()) {
					showPopup(display, historyList.get(idxHistory + 1), false);
				}

				disposeShell(shell);
			}
		});

		// Image has gap for text at the top (with image at bottom left)
		// trim top to height of shell
		Point bestSize = cShell.computeSize(SHELL_DEF_WIDTH, SWT.DEFAULT);
		if (bestSize.y < SHELL_MIN_HEIGHT)
			bestSize.y = SHELL_MIN_HEIGHT;
		else if (bestSize.y > SHELL_MAX_HEIGHT) {
			bestSize.y = SHELL_MAX_HEIGHT;
			if (sDetails == null) {
				sDetails = popupParams.text;
			} else {
				sDetails = popupParams.text + "\n===============\n" + sDetails;
			}
		}

		Rectangle bounds = null;
		try {
			UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
			if (uiFunctions != null) {
				Shell mainShell = uiFunctions.getMainShell();
				if (mainShell != null) {
					bounds = mainShell.getMonitor().getClientArea();
				}
			} else {
				Shell shell = display.getActiveShell();
				if (shell != null) {
					bounds = shell.getMonitor().getClientArea();
				}
			}
			if (bounds == null) {
				bounds = shell.getMonitor().getClientArea();
			}
		} catch (Exception e) {
		}
		if (bounds == null) {
			bounds = display.getClientArea();
		}

		Rectangle endBounds;
		if (bDisableSliding) {
			endBounds = new Rectangle(((bounds.x + bounds.width) / 2)
					- (bestSize.x / 2), ((bounds.y + bounds.height) / 2)
					- (bestSize.y / 2), bestSize.x, bestSize.y);
		} else {
			int boundsX2 = bounds.x + bounds.width;
			int boundsY2 = bounds.y + bounds.height;
			endBounds = shell.computeTrim(boundsX2 - bestSize.x, boundsY2
					- bestSize.y, bestSize.x, bestSize.y);

			// bottom and right trim will be off the edge, calculate this trim
			// and adjust it up and left (trim may not be the same size on all sides)
			int diff = (endBounds.x + endBounds.width) - boundsX2;
			if (diff >= 0)
				endBounds.x -= diff + EDGE_GAP;
			diff = (endBounds.y + endBounds.height) - boundsY2;
			if (diff >= 0) {
				endBounds.y -= diff + EDGE_GAP;
			}
			//System.out.println("best" + bestSize + ";mon" + bounds + ";end" + endBounds);
		}

		FormData data = new FormData(bestSize.x, bestSize.y);
		cShell.setLayoutData(data);

		btnDetails.setVisible(sDetails != null);
		if (sDetails == null) {
			gridData = new GridData();
			gridData.widthHint = 0;
			btnDetails.setLayoutData(gridData);
		}
		shell.layout();

		btnNext.setFocus();
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				Utils.disposeSWTObjects(disposeList);

				if (currentPopupIndex == idxHistory) {
					if (DEBUG)
						System.out.println("Clear #" + currentPopupIndex + "/" + idxHistory);
					try {
						monitor.enter();
						currentPopupIndex = -1;
					} finally {
						monitor.exit();
					}
				}
			}
		});

		shell.addListener(SWT.Traverse, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (event.detail == SWT.TRAVERSE_ESCAPE) {
					disposeShell(shell);
					event.doit = false;
				}
			}
		});

		if (mouseAdapter != null)
			addMouseTrackListener(shell, mouseAdapter);

		for (int i = 0; i < listeners.length; i++) {
			try {
				listeners[i].skinAfterComponents(shell, this, popupParams.relatedTo);
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		int	timeoutSecs;

		if ( popupParams.timeoutSecs < 0 ){

			timeoutSecs = COConfigurationManager.getIntParameter("Message Popup Autoclose in Seconds");

		}else{

			timeoutSecs = popupParams.timeoutSecs;
		}

		runPopup(endBounds, idxHistory, bSlide, timeoutSecs );
	}

	/**
	 * @param shell2
	 * @param b
	 *
	 * @since 3.0.0.9
	 */
	private void createLinkLabel(Composite shell, final PopupParams popupParams) {

		final Canvas canvas = new Canvas(shell, SWT.None) {
			@Override
			public Point computeSize(int wHint, int hHint, boolean changed) {
				Rectangle area = new Rectangle(0, 0, SHELL_DEF_WIDTH, 5000);
				GC gc = new GC(this);
				GCStringPrinter sp = new GCStringPrinter(gc, popupParams.text, area,
						true, false, SWT.WRAP | SWT.TOP);
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
				if (e.type == SWT.Paint) {
					Rectangle area = canvas.getClientArea();
					sp = new GCStringPrinter(e.gc, popupParams.text, area, true, false,
							SWT.WRAP | SWT.TOP);
					sp.setUrlColor(e.gc.getDevice().getSystemColor(SWT.COLOR_LINK_FOREGROUND));
					if (colorURL != null) {
						sp.setUrlColor(colorURL);
					}
					if (colorFG != null) {
						e.gc.setForeground(colorFG);
					}
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
		canvas.addListener(SWT.MouseMove, l);
		canvas.addListener(SWT.MouseUp, l);

		ClipboardCopy.addCopyToClipMenu(canvas,
				new ClipboardCopy.copyToClipProvider() {
					@Override
					public String getText() {
						return (popupParams.title + "\n\n" + popupParams.text);
					}
				});

		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 3;
		canvas.setLayoutData(gridData);
	}

	/**
	 * @param numAfter
	 */
	private void setButtonNextText(int numAfter) {
		if (numAfter <= 0)
			Messages.setLanguageText(btnNext, "popup.error.hide");
		else
			Messages.setLanguageText(btnNext, "popup.next", new String[] {
				"" + numAfter
			});
		cShell.layout(true);
	}

	/**
	 * Show the popup with the specified parameters.
	 *
	 * @param display Display to show on
	 * @param item popup to display.  Must already exist in historyList
	 * @param bSlide Whether to slide in or show immediately
	 */
	private void showPopup(final Display display, final PopupParams item,
			final boolean bSlide) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				new MessageSlideShell(display, item, bSlide);
			}
		});
	}

	/**
	 * Adds mousetracklistener to composite and all it's children
	 *
	 * @param parent Composite to start at
	 * @param listener Listener to add
	 */
	private void addMouseTrackListener(Composite parent,
			MouseTrackListener listener) {
		if (parent == null || listener == null || parent.isDisposed())
			return;

		parent.addMouseTrackListener(listener);
		Control[] children = parent.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control control = children[i];
			if (control instanceof Composite)
				addMouseTrackListener((Composite) control, listener);
			else
				control.addMouseTrackListener(listener);
		}
	}

	/**
	 * removes mousetracklistener from composite and all it's children
	 *
	 * @param parent Composite to start at
	 * @param listener Listener to remove
	 */
	private void removeMouseTrackListener(Composite parent,
			MouseTrackListener listener) {
		if (parent == null || listener == null || parent.isDisposed())
			return;

		Control[] children = parent.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control control = children[i];
			control.removeMouseTrackListener(listener);
			if (control instanceof Composite)
				removeMouseTrackListener((Composite) control, listener);
		}
	}

	/**
	 * Start the slid in, wait specified time while notifying user of impending
	 * auto-close, then slide out.  Run on separate thread, so this method
	 * returns immediately
	 *
	 * @param endBounds end location and size wanted
	 * @param idx Index in historyList of popup (Used to calculate # prev, next)
	 * @param bSlide Whether to slide in, or show immediately
	 */
	private void runPopup(final Rectangle endBounds, final int idx,
			final boolean bSlide, final int timeoutSecs ) {
		if (shell == null || shell.isDisposed())
			return;

		final Display display = shell.getDisplay();

		if (DEBUG)
			System.out.println("runPopup " + idx + ((bSlide) ? " Slide" : " Instant"));

		AEThread thread = new AEThread("Slidey", true) {
			private final static int PAUSE = 500;

			@Override
			public void runSupport() {
				if (shell == null || shell.isDisposed())
					return;

				if (bSlide) {
					new ShellSlider(shell, SWT.UP, endBounds).run();
				} else {
					Utils.execSWTThread(new AERunnable() {

						@Override
						public void runSupport() {
							shell.setBounds(endBounds);
							shell.open();
						}
					});
				}

				int delayLeft = timeoutSecs * 1000;
				final boolean autohide = (delayLeft != 0);

				long lastDelaySecs = 0;
				int lastNumPopups = -1;
				while ((!autohide || bDelayPaused || delayLeft > 0)
						&& !shell.isDisposed()) {
					int delayPausedOfs = (bDelayPaused ? 1 : 0);
					final long delaySecs = Math.round(delayLeft / 1000.0)
							+ delayPausedOfs;
					final int numPopups = historyList.size();
					if (lastDelaySecs != delaySecs || lastNumPopups != numPopups) {
						lastDelaySecs = delaySecs;
						lastNumPopups = numPopups;
						shell.getDisplay().asyncExec(new AERunnable() {
							@Override
							public void runSupport() {
								String sText = "";

								if (lblCloseIn == null || lblCloseIn.isDisposed())
									return;

								lblCloseIn.setRedraw(false);
								if (!bDelayPaused && autohide)
									sText += MessageText.getString("popup.closing.in",
											new String[] {
												String.valueOf(delaySecs)
											});

								int numPopupsAfterUs = numPopups - idx - 1;
								boolean bHasMany = numPopupsAfterUs > 0;
								if (bHasMany) {
									sText += "\n";
									sText += MessageText.getString("popup.more.waiting",
											new String[] {
												String.valueOf(numPopupsAfterUs)
											});
								}

								lblCloseIn.setText(sText);

								if (btnHideAll.getVisible() != bHasMany) {
									cShell.setRedraw(false);
									btnHideAll.setVisible(bHasMany);
									lblCloseIn.getParent().layout(true);
									cShell.setRedraw(true);
								}

								setButtonNextText(numPopupsAfterUs);

								// Need to redraw to cause a paint
								lblCloseIn.setRedraw(true);
							}
						});
					}

					if (!bDelayPaused)
						delayLeft -= PAUSE;
					try {
						Thread.sleep(PAUSE);
					} catch (InterruptedException e) {
						delayLeft = 0;
					}
				}

				if (this.isInterrupted()) {
					// App closedown likely, boot out ASAP
					disposeShell(shell);
					return;
				}

				// Assume that if the shell was disposed during loop, it's on purpose
				// and that it has handled whether to show the next popup or not
				if (shell != null && !shell.isDisposed()) {
					if (idx + 1 < historyList.size()) {
						showPopup(display, historyList.get(idx + 1), true);
					}

					// slide out current popup
					if (bSlide)
						new ShellSlider(shell, SWT.RIGHT).run();

					disposeShell(shell);
				}
			}
		};
		thread.start();
	}

	private void disposeShell(final Shell shell) {
		if (shell == null || shell.isDisposed())
			return;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				shell.dispose();
			}
		});
	}

	/**
	 * Waits until all slideys are closed before returning to caller.
	 */
	public static void waitUntilClosed() {

		Utils.readAndDispatchLoop(()->currentPopupIndex < 0);
	}

	public static class PopupParams
	{
		public int iconID;

		public String title;

		public String text;

		public String details;

		public long addedOn;

		public Object[] relatedTo;

		public int	timeoutSecs;

		/**
		 * @param iconID
		 * @param title
		 * @param text
		 * @param details
		 */
		public PopupParams(int iconID, String title, String text, String details, int timeoutSecs ) {
			this.iconID = iconID;
			this.title = title;
			this.text = text;
			this.details = details;
			this.timeoutSecs = timeoutSecs;
			addedOn = System.currentTimeMillis();
		}

		/**
		 * @param iconID2
		 * @param title2
		 * @param text2
		 * @param details2
		 * @param relatedTo
		 */
		public PopupParams(int iconID, String title, String text, String details,
				Object[] relatedTo, int timeoutSecs ) {
			this(iconID, title, text, details, timeoutSecs );
			this.relatedTo = relatedTo;
		}
	}

	/**
	 * Test
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		final Display display = Display.getDefault();

		Shell shell = new Shell(display, SWT.DIALOG_TRIM);
		shell.setLayout(new FillLayout());
		Button btn = new Button(shell, SWT.PUSH);
		btn.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				test(display);
			}
		});
		shell.open();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	public static void test(Display display) {

		String title = "This is the title that never ends, never ends!";
		String text = "This is a very long message with lots of information and "
				+ "stuff you really should read.  Are you still reading? Good, because "
				+ "reading <a href=\"http://moo.com\">stimulates</a> the mind and grows "
				+ "hair on your chest.\n\n  Unless you are a girl, then it makes you want "
				+ "to read more.  It's an endless cycle of reading that will never "
				+ "end.  Cursed is the long text that is in this test and may it fill"
				+ "every last line of the shell until there is no more.";

		// delay before running, to give eclipse time to finish up it's work
		// Otherwise, Mr Slidey is jumpy
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//		MessagePopupShell shell = new MessagePopupShell(display,
		//				MessagePopupShell.ICON_INFO, "Title", text, "Details");

		new MessageSlideShell(display, SWT.ICON_INFORMATION,
				"Simple. . . . . . . . . . . . . . . . . . .", "Simple", (String) null, -1);

		new MessageSlideShell(display, SWT.ICON_INFORMATION, title + "1", text,
				"Details: " + text, -1);

		new MessageSlideShell(display, SWT.ICON_INFORMATION, "ShortTitle2",
				"ShortText", "Details", -1);
		MessageSlideShell.waitUntilClosed();

		new MessageSlideShell(display, SWT.ICON_INFORMATION, "ShortTitle3",
				"ShortText", (String) null, -1);
		for (int x = 0; x < 10; x++)
			text += "\n\n\n\n\n\n\n\nWow";
		new MessageSlideShell(display, SWT.ICON_INFORMATION, title + "4", text,
				"Details", -1);

		new MessageSlideShell(display, SWT.ICON_ERROR, title + "5", text,
				(String) null, -1);

		MessageSlideShell.waitUntilClosed();
	}

	public Color getUrlColor() {
		return colorURL;
	}

	public void setUrlColor(Color urlColor) {
		this.colorURL = urlColor;
	}

	public Color getColorFG() {
		return colorFG;
	}

	public void setColorFG(Color colorFG) {
		this.colorFG = colorFG;
	}
}
