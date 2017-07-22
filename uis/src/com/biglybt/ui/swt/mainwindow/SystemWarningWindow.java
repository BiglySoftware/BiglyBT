/*
 * Created on Jan 4, 2010
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

package com.biglybt.ui.swt.mainwindow;

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Alerts;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.shells.GCStringPrinter.URLInfo;

import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.utils.ColorCache;

/**
 * @author TuxPaper
 * @created Jan 4, 2010
 *
 */
public class SystemWarningWindow
{
	private int WIDTH = 230;

	private int BORDER_X = 12;

	private int BORDER_Y0 = 10;

	private int BORDER_Y1 = 6;

	private int GAP_Y = 5;

	private int GAP_BUTTON_Y = 20;

	private int GAP_Y_TITLE_COUNT = 3;

	private final LogAlert logAlert;

	private final Point ptBottomRight;

	private final Shell parent;

	private Shell shell;

	private Image imgClose;

	private Rectangle boundsClose;

	private GCStringPrinter spText;

	private GCStringPrinter spTitle;

	private GCStringPrinter spCount;

	private Point sizeTitle;

	private Point sizeText;

	private Point sizeCount;

	private Font fontTitle;

	private Font fontCount;

	private int height;

	private Rectangle rectX;

	private int historyPosition;

	private String title;

	private String text;

	public static int numWarningWindowsOpen = 0;

	public SystemWarningWindow(LogAlert logAlert, Point ptBottomRight,
			Shell parent, int historyPosition) {
		this.logAlert = logAlert;
		this.ptBottomRight = ptBottomRight;
		this.parent = parent;
		this.historyPosition = historyPosition;

		WIDTH = Utils.adjustPXForDPI(WIDTH);

		BORDER_X = Utils.adjustPXForDPI(BORDER_X);

		BORDER_Y0 = Utils.adjustPXForDPI(BORDER_Y0);

		BORDER_Y1 = Utils.adjustPXForDPI(BORDER_Y1);

		GAP_Y = Utils.adjustPXForDPI(GAP_Y);

		GAP_BUTTON_Y = Utils.adjustPXForDPI(GAP_BUTTON_Y);

		GAP_Y_TITLE_COUNT = Utils.adjustPXForDPI(GAP_Y_TITLE_COUNT);

		String amb_key_suffix;
		switch (logAlert.entryType) {
			case LogAlert.AT_ERROR:
				amb_key_suffix = "AlertMessageBox.error";
				break;
			case LogAlert.AT_INFORMATION:
				amb_key_suffix = "AlertMessageBox.information";
				break;
			case LogAlert.AT_WARNING:
				amb_key_suffix = "AlertMessageBox.warning";
				break;
			default:
				amb_key_suffix = null;
				break;
		}
		title = amb_key_suffix == null ? Constants.APP_NAME
				: MessageText.getString(amb_key_suffix);

		if (logAlert.text.startsWith("{")) {
			text = MessageText.expandValue(logAlert.text);
		} else {
			text = logAlert.text;
		}

		if (logAlert.err != null) {
			text += "\n" + Debug.getExceptionMessage(logAlert.err);
		}

		if (logAlert.details != null) {
			text += "\n<A HREF=\"details\">" + MessageText.getString("v3.MainWindow.button.viewdetails") + "</A>";
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				openWindow();
			}
		});
	}

	protected void openWindow() {
		Display display = parent.getDisplay();
		//shell = new Shell(parent, SWT.TOOL | SWT.TITLE | SWT.CLOSE);
		//shell.setText("Warning (X of X)");
		shell = new Shell(parent, SWT.TOOL);
		shell.setLayout(new FormLayout());
		shell.setBackground(Colors.getSystemColor(display, SWT.COLOR_INFO_BACKGROUND));
		shell.setForeground(Colors.getSystemColor(display, SWT.COLOR_INFO_FOREGROUND));

		Menu menu = new Menu(shell);
		MenuItem menuItem = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(menuItem, "MyTorrentsView.menu.thisColumn.toClipboard");
		menuItem.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ClipboardCopy.copyToClipBoard(logAlert.text
						+ (logAlert.details == null ? "" : "\n" + logAlert.details));
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		shell.setMenu(menu);


		ImageLoader imageLoader = ImageLoader.getInstance();
		imgClose = imageLoader.getImage("image.systemwarning.closeitem");
		boundsClose = imgClose.getBounds();

		GC gc = new GC(shell);

		FontData[] fontdata = gc.getFont().getFontData();
		fontdata[0].setHeight(fontdata[0].getHeight() + 1);
		fontdata[0].setStyle(SWT.BOLD);
		fontTitle = new Font(display, fontdata);

		fontdata = gc.getFont().getFontData();
		fontdata[0].setHeight(fontdata[0].getHeight() - 1);
		fontCount = new Font(display, fontdata);

		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				Utils.disposeSWTObjects(new Object[] {
					fontTitle,
					fontCount,
				});
				numWarningWindowsOpen--;
			}
		});

		Rectangle printArea = new Rectangle(BORDER_X, 0, WIDTH - (BORDER_X * 2),
				5000);
		spText = new GCStringPrinter(gc, text, printArea, true, false, SWT.WRAP);
		spText.setUrlColor(Colors.blues[Colors.FADED_DARKEST]);
		spText.calculateMetrics();

		gc.setFont(fontCount);
		String sCount = MessageText.getString("label.xOfTotal",
				new String[] {
					"" + historyPosition + 1,
					"" + getWarningCount()
				});
		spCount = new GCStringPrinter(gc, sCount, printArea, true, false, SWT.WRAP);
		spCount.calculateMetrics();

		gc.setFont(fontTitle);
		spTitle = new GCStringPrinter(gc, title, printArea, true, false, SWT.WRAP);
		spTitle.calculateMetrics();

		gc.dispose();
		sizeText = spText.getCalculatedSize();
		sizeTitle = spTitle.getCalculatedSize();
		sizeCount = spCount.getCalculatedSize();

		FormData fd;

		Button btnDismiss = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnDismiss, "Button.dismiss");
		final int btnHeight = btnDismiss.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;

		Button btnPrev = new Button(shell, SWT.PUSH);
		btnPrev.setText("<");

		Button btnNext = new Button(shell, SWT.PUSH);
		btnNext.setText(">");

		fd = new FormData();
		fd.bottom = new FormAttachment(100, -BORDER_Y1);
		fd.right = new FormAttachment(100, -BORDER_X);
		btnNext.setLayoutData(fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, -BORDER_Y1);
		fd.right = new FormAttachment(btnNext, -BORDER_X);
		btnPrev.setLayoutData(fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, -BORDER_Y1);
		fd.right = new FormAttachment(btnPrev, -BORDER_X);
		btnDismiss.setLayoutData(fd);

		height = BORDER_Y0 + sizeTitle.y + GAP_Y + sizeText.y + GAP_Y_TITLE_COUNT
				+ sizeCount.y + GAP_BUTTON_Y + btnHeight + BORDER_Y1;

		Rectangle area = shell.computeTrim(ptBottomRight.x - WIDTH, ptBottomRight.y
				- height, WIDTH, height);
		shell.setBounds(area);
		shell.setLocation(ptBottomRight.x - area.width, ptBottomRight.y
				- area.height - 2);

		rectX = new Rectangle(area.width - BORDER_X - boundsClose.width, BORDER_Y0,
				boundsClose.width, boundsClose.height);

		shell.addMouseMoveListener(new MouseMoveListener() {
			int lastCursor = SWT.CURSOR_ARROW;

			@Override
			public void mouseMove(MouseEvent e) {
				if (shell == null || shell.isDisposed()) {
					return;
				}
				URLInfo hitUrl = spText.getHitUrl(e.x, e.y);

				int cursor = (rectX.contains(e.x, e.y)) || hitUrl != null
						? SWT.CURSOR_HAND : SWT.CURSOR_ARROW;
				if (cursor != lastCursor) {
					lastCursor = cursor;
					shell.setCursor(e.display.getSystemCursor(cursor));
				}
			}
		});

		shell.addMouseListener(new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				if (shell == null || shell.isDisposed()) {
					return;
				}
				if (rectX.contains(e.x, e.y)) {
					shell.dispose();
				}
				URLInfo hitUrl = spText.getHitUrl(e.x, e.y);
				if (hitUrl != null) {
					if (hitUrl.url.equals("details")) {
						MessageBoxShell mb = new MessageBoxShell(Constants.APP_NAME,
								logAlert.details, new String[] {
									MessageText.getString("Button.ok")
								}, 0);
						mb.setUseTextBox(true);
						mb.setParent(Utils.findAnyShell());
						mb.open(null);
					} else {
						Utils.launch(hitUrl.url);
					}
				}
			}

			@Override
			public void mouseDown(MouseEvent e) {
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
			}
		});

		shell.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				e.gc.drawImage(imgClose, WIDTH - BORDER_X - boundsClose.width,
						BORDER_Y0);

				Rectangle printArea;
				printArea = new Rectangle(BORDER_X, BORDER_Y0 + sizeTitle.y + GAP_Y_TITLE_COUNT,
						WIDTH, 100);
				String sCount = MessageText.getString("label.xOfTotal",
						new String[] {
							"" + (historyPosition + 1),
							"" + getWarningCount()
						});
				e.gc.setAlpha(180);
				Font lastFont = e.gc.getFont();
				e.gc.setFont(fontCount);
				spCount = new GCStringPrinter(e.gc, sCount, printArea, true, false,
						SWT.WRAP | SWT.TOP);
				spCount.printString();
				e.gc.setAlpha(255);
				sizeCount = spCount.getCalculatedSize();

				e.gc.setFont(lastFont);
				spText.printString(e.gc, new Rectangle(BORDER_X, BORDER_Y0
						+ sizeTitle.y + GAP_Y_TITLE_COUNT + sizeCount.y + GAP_Y, WIDTH - BORDER_X
						- BORDER_X, 5000), SWT.WRAP | SWT.TOP);

				e.gc.setFont(fontTitle);

				e.gc.setForeground(ColorCache.getColor(e.gc.getDevice(), "#54728c"));
				spTitle.printString(e.gc, new Rectangle(BORDER_X, BORDER_Y0, WIDTH
						- BORDER_X - BORDER_X, 5000), SWT.WRAP | SWT.TOP);

				e.gc.setLineStyle(SWT.LINE_DOT);
				e.gc.setLineWidth(1);
				e.gc.setAlpha(180);
				e.gc.drawLine(BORDER_X, height - btnHeight - (GAP_BUTTON_Y / 2)
						- BORDER_Y1, WIDTH - BORDER_X, height - btnHeight
						- (GAP_BUTTON_Y / 2) - BORDER_Y1);

			}
		});

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					shell.dispose();
					return;
				}
			}
		});

		btnPrev.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ArrayList<LogAlert> alerts = Alerts.getUnviewedLogAlerts();
				int pos = historyPosition - 1;
				if (pos < 0 || pos >= alerts.size()) {
					return;
				}

				new SystemWarningWindow(alerts.get(pos), ptBottomRight, parent, pos);
				shell.dispose();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		btnPrev.setEnabled(historyPosition > 0);

		btnNext.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ArrayList<LogAlert> alerts = Alerts.getUnviewedLogAlerts();
				int pos = historyPosition + 1;
				if (pos >= alerts.size()) {
					return;
				}

				new SystemWarningWindow(alerts.get(pos), ptBottomRight, parent, pos);
				shell.dispose();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		ArrayList<LogAlert> alerts = Alerts.getUnviewedLogAlerts();
		btnNext.setEnabled(alerts.size() != historyPosition + 1);

		btnDismiss.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ArrayList<LogAlert> alerts = Alerts.getUnviewedLogAlerts();
				for (int i = 0; i < alerts.size() && i <= historyPosition; i++) {
					Alerts.markAlertAsViewed(alerts.get(i));
				}
				shell.dispose();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		shell.open();
		numWarningWindowsOpen++;
	}

	private int getWarningCount() {
		ArrayList<LogAlert> historyList = Alerts.getUnviewedLogAlerts();
		return historyList.size();
	}
}
