/*
 * Created on Sep 21, 2008
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

package com.biglybt.ui.swt.skin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

/**
 * Native Toggle
 *
 * @author TuxPaper
 *
 */
public class SWTSkinObjectToggle
	extends SWTSkinObjectBasic
{
	private Button button;

	// stored so we can access it after button is disposed, and so we can
	// retrieve without being on SWT thread
	private boolean isToggled;

	private List<SWTSkinToggleListener> buttonListeners = new CopyOnWriteArrayList();

	public SWTSkinObjectToggle(SWTSkin skin, SWTSkinProperties properties,
			String id, String configID, SWTSkinObject parentSkinObject) {
		super(skin, properties, id, configID, "toggle", parentSkinObject);

		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}

		if (Constants.isWindows) {
			// WinXP Classic Theme will not bring though parent's background image
			// without FORCEing the background mode
			createOn.setBackgroundMode(SWT.INHERIT_FORCE);
		}

		button = new Button(createOn, SWT.TOGGLE);
		isToggled = false;

		button.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				isToggled = button.getSelection();
				for (SWTSkinToggleListener l : buttonListeners) {
					try {
						l.toggleChanged(SWTSkinObjectToggle.this, isToggled);
					} catch (Exception ex) {
						Debug.out(ex);
					}
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		setControl(button);
	}

	// @see SWTSkinObjectBasic#switchSuffix(java.lang.String, int, boolean)
	@Override
	public String switchSuffix(String suffix, int level, boolean walkUp,
	                           boolean walkDown) {
		suffix = super.switchSuffix(suffix, level, walkUp, walkDown);

		if (suffix == null) {
			return null;
		}

		String sPrefix = sConfigID + ".text";
		String text = properties.getStringValue(sPrefix + suffix);
		if (text != null) {
			setText(text);
		}

		return suffix;
	}

	public void addSelectionListener(SWTSkinToggleListener listener) {
		if (buttonListeners.contains(listener)) {
			return;
		}
		buttonListeners.add(listener);
	}

	public void setText(final String text) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (button != null && !button.isDisposed()) {
					button.setText(text);
				}
			}
		});

	}

	public boolean isToggled() {
		return isToggled;
	}

	public void setToggled(boolean b) {
		isToggled = b;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (button != null && !button.isDisposed()) {
					button.setSelection(isToggled);
				}
			}
		});
	}
}
