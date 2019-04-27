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
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

/**
 * Native checkbox
 *
 * @author TuxPaper
 * @created Dec 24, 2008
 *
 */
public class SWTSkinObjectCheckbox
	extends SWTSkinObjectBasic
{
	private Button button;

	// stored so we can access it after button is disposed, and so we can
	// retrieve without being on SWT thread
	private boolean checked;

	private List<SWTSkinCheckboxListener> buttonListeners = new CopyOnWriteArrayList();

	public SWTSkinObjectCheckbox(SWTSkin skin, SWTSkinProperties properties,
			String id, String configID, SWTSkinObject parentSkinObject) {
		super(skin, properties, id, configID, "checkbox", parentSkinObject);

		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}

		int style = SWT.WRAP | SWT.CHECK;
		String[] styles = properties.getStringArray(configID + ".style");
		if (styles != null) {
			for (String s : styles) {
				if (s.toLowerCase().equals("radio")) {
					style = SWT.RADIO | (style & (~SWT.CHECK));
				}
			}
		}

		button = new Button(createOn, style);
		checked = false;

		button.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				checked = button.getSelection();
				for (SWTSkinCheckboxListener l : buttonListeners) {
					try {
						l.checkboxChanged(SWTSkinObjectCheckbox.this, checked);
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

	public void addSelectionListener(SWTSkinCheckboxListener listener) {
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

	public boolean isChecked() {
		return checked;
	}

	public void setChecked(boolean b) {
		checked = b;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (button != null && !button.isDisposed()) {
					button.setSelection(checked);
				}
			}
		});
	}
	
	public void setEnabled(boolean b) {
		
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (button != null && !button.isDisposed()) {
					button.setEnabled(b);
				}
			}
		});
	}
}
