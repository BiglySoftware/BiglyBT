/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.shells.uiswitcher;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.UISwitcherUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

/**
 * @author TuxPaper
 * @created Feb 5, 2007
 *
 */
public class UISwitcherWindow
{
	private static String CFG_PREFIX = "window.uiswitcher.";

	private static String[] IDS = {
		"NewUI",
		"ClassicUI"
	};

	private Shell shell;

	private Button btnOk;

	private int ui = -1;

	private List<Object> disposeList = new ArrayList<>();

	public UISwitcherWindow() {
		this(false, true);
	}

	/**
	 *
	 */
	public UISwitcherWindow(boolean standalone, final boolean allowCancel) {
		final String originalUIMode = UISwitcherUtil.calcUIMode();
		try {
			final Button[] buttons = new Button[IDS.length];
			GridData gd;

			int style = SWT.BORDER | SWT.TITLE | SWT.RESIZE;
			if (allowCancel) {
				style |= SWT.CLOSE;
			}
			shell = standalone ? new Shell(Display.getDefault(), style)
					: ShellFactory.createShell((Shell) null, style);
			shell.setText(MessageText.getString(CFG_PREFIX + "title"));
			Utils.setShellIcon(shell);

			shell.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					Utils.disposeSWTObjects(disposeList);
					if (ui == 0) {
						// Full AZ3UI
						COConfigurationManager.setParameter("ui", "az3");
					} else if (ui == 1) {
						COConfigurationManager.setParameter("ui", "az2");
					}

					if (ui != -1) {
						COConfigurationManager.setParameter("ui.asked", true);
						UISwitcherUtil.triggerListeners(UISwitcherUtil.calcUIMode());
					}
				}
			});

			GridLayout layout = new GridLayout();
			layout.horizontalSpacing = 0;
			layout.marginWidth = 5;
			layout.marginHeight = 0;
			layout.verticalSpacing = 1;
			shell.setLayout(layout);

			Label title = new Label(shell, SWT.WRAP);
			gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.verticalIndent = 3;
			title.setLayoutData(gd);

			Messages.setLanguageText(title, CFG_PREFIX + "text");

			Listener radioListener = new Listener() {
				@Override
				public void handleEvent(Event event) {
					int idx;
					if (event.widget instanceof Composite) {
						Long l = (Long) event.widget.getData("INDEX");
						idx = l.intValue();
					} else {
						Composite c = ((Control) event.widget).getParent();
						Long l = (Long) c.getData("INDEX");
						idx = l.intValue();
					}
					for (int i = 0; i < buttons.length; i++) {
						boolean selected = idx == i;
						Composite c = buttons[i].getParent();
						c.setBackground(
								selected ? Colors.getSystemColor(c.getDisplay(), SWT.COLOR_LIST_SELECTION) : null);
						Color fg = selected ? Colors.getSystemColor(c.getDisplay(), SWT.COLOR_LIST_SELECTION_TEXT) : null;
						Control[] children = c.getChildren();
						for (int j = 0; j < children.length; j++) {
							Control control = children[j];
							control.setForeground(fg);

						}
						buttons[i].setSelection(selected);
					}
				}
			};

			FontData[] fontData = shell.getFont().getFontData();
			fontData[0].setHeight((int) (fontData[0].getHeight() * 1.5));
			fontData[0].setStyle(SWT.BOLD);
			final Font headerFont = new Font(shell.getDisplay(), fontData);
			disposeList.add(headerFont);

			Composite cCenter = new Composite(shell, SWT.NONE);
			cCenter.setLayout(new GridLayout());
			cCenter.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER));

			for (int i = 0; i < IDS.length; i++) {

				final Composite c = new Composite(cCenter, SWT.NONE);
				gd = new GridData(GridData.FILL_HORIZONTAL);
				gd.verticalIndent = 0;
				c.setLayoutData(gd);
				GridLayout gridLayout = new GridLayout(1, false);
				gridLayout.horizontalSpacing = 0;
				gridLayout.marginWidth = 5;
				gridLayout.marginHeight = 3;
				gridLayout.verticalSpacing = 0;
				c.setLayout(gridLayout);
				c.setData("INDEX", new Long(i));

				c.addListener(SWT.MouseDown, radioListener);

				buttons[i] = new Button(c, SWT.RADIO);
				buttons[i].setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
				Messages.setLanguageText(buttons[i], CFG_PREFIX + IDS[i] + ".title");
				buttons[i].setData("INDEX", new Long(i));
				buttons[i].addListener(SWT.Selection, radioListener);
				buttons[i].setFont(headerFont);

				buttons[i].addTraverseListener(new TraverseListener() {

					@Override
					public void keyTraversed(TraverseEvent e) {
						if (e.detail == SWT.TRAVERSE_ARROW_NEXT) {
							e.doit = true;
							e.detail = SWT.TRAVERSE_TAB_NEXT;
						} else if (e.detail == SWT.TRAVERSE_ARROW_PREVIOUS) {
							e.detail = SWT.TRAVERSE_TAB_PREVIOUS;
							e.doit = true;
						} else if (e.detail == SWT.TRAVERSE_TAB_NEXT
								|| e.detail == SWT.TRAVERSE_TAB_PREVIOUS) {
							btnOk.setFocus();
							e.doit = false;
						} else if (e.detail == SWT.TRAVERSE_RETURN) {
							e.doit = true;
						} else if (e.detail == SWT.TRAVERSE_ESCAPE) {
							e.doit = false;
							if (allowCancel) {
								ui = -1;
								shell.dispose();
							}
						} else {
							e.doit = false;
						}
					}

				});

				buttons[i].addListener(SWT.KeyDown, new Listener() {
					// @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
					@Override
					public void handleEvent(Event event) {
						if (event.keyCode == SWT.ARROW_UP) {
							shell.getDisplay().getFocusControl().traverse(
									SWT.TRAVERSE_ARROW_PREVIOUS);
						} else if (event.keyCode == SWT.ARROW_DOWN) {
							shell.getDisplay().getFocusControl().traverse(
									SWT.TRAVERSE_ARROW_NEXT);
						}
					}
				});

				Label info = new Label(c, SWT.WRAP);
				gd = new GridData(GridData.FILL_BOTH);
				gd.horizontalIndent = 20;
				gd.verticalAlignment = SWT.TOP;
				info.setLayoutData(gd);

				Messages.setLanguageText(info, CFG_PREFIX + IDS[i] + ".text");
				info.addListener(SWT.MouseDown, radioListener);
			}

			Event eventSelectFirst = new Event();
			eventSelectFirst.widget = buttons[0];
			radioListener.handleEvent(eventSelectFirst);

			Composite cBottom = new Composite(shell, SWT.NONE);
			layout = new GridLayout(1, false);
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			cBottom.setLayout(layout);
			cBottom.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER));

			btnOk = new Button(cBottom, SWT.PUSH);
			Messages.setLanguageText(btnOk, "Button.ok");
			shell.setDefaultButton(btnOk);
			btnOk.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					for (int i = 0; i < buttons.length; i++) {
						if (buttons[i].getSelection()) {
							ui = i;
							break;
						}
					}
					shell.dispose();
				}
			});
			gd = new GridData(GridData.HORIZONTAL_ALIGN_END);
			btnOk.setLayoutData(gd);

			shell.addTraverseListener(new TraverseListener() {
				@Override
				public void keyTraversed(TraverseEvent e) {
					if (e.detail == SWT.TRAVERSE_ESCAPE) {
						shell.dispose();
						e.doit = false;
						return;
					}
					e.doit = true;
				}
			});

			Point point = shell.computeSize(400, SWT.DEFAULT);
			shell.setSize(point);

			Utils.centreWindow(shell);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void open() {
		shell.open();
	}

	public static void main(String[] args) {
		Display display = Display.getDefault();
		UISwitcherWindow window = new UISwitcherWindow(true, true);
		window.open();
		Shell shell = window.shell;
		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch()) {
				shell.getDisplay().sleep();
			}
		}
		System.out.println(window.ui);
	}
}
