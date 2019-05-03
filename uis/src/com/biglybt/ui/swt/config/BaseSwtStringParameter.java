/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.utils.FontUtils;

public class BaseSwtStringParameter<PARAMTYPE extends BaseSwtStringParameter<PARAMTYPE>>
	extends BaseSwtParameter<PARAMTYPE, String>
{
	protected final Text inputField;

	private Composite ourParent;

	private Label lblSuffix;

	private char[] validChars;

	private boolean validCharsCaseSensitive;

	private Listener verifyListener;

	public BaseSwtStringParameter(Composite composite, String paramID,
			String labelKey, String suffixKey,
			SwtParameterValueProcessor<PARAMTYPE, String> valueProcessor) {
		super(paramID);

		createStandardLabel(composite, labelKey);

		Composite parent;
		if (suffixKey == null) {
			parent = composite;
		} else {
			ourParent = parent = new Composite(composite, SWT.NONE);
			GridLayout gridLayout = new GridLayout(2, false);
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			parent.setLayout(gridLayout);
			if (doGridData(composite)) {
				parent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			}
		}

		inputField = new Textbox(parent);
		setMainControl(inputField);

		if (doGridData(composite)) {
			GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
			inputField.setLayoutData(gridData);
		}

		if (suffixKey != null) {
			lblSuffix = new Label(parent, SWT.WRAP);
			Messages.setLanguageText(lblSuffix, suffixKey);
			lblSuffix.setLayoutData(
					Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
			ClipboardCopy.addCopyToClipMenu(lblSuffix);
		}

		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (paramID != null) {
			setConfigValueProcessor(String.class);
		} else {
			refreshControl();
		}

		rebuildVerifyListener();

		inputField.addListener(SWT.Modify, event -> {
			// setText and user input will call this -- don't put text changes here
			validate(inputField.getText());
		});

		inputField.addListener(SWT.FocusOut, e -> setValue(inputField.getText()));
	}

	private void rebuildVerifyListener() {
		// SWT Bug: Windows: when Text has a Verify listener, Shift-Del (Cut) does 
		// not work, but Control-X does.
		boolean addVerifyListener = validChars != null;
		if (!addVerifyListener) {
			if (valueProcessor instanceof SwtConfigParameterValueProcessor) {
				List verifiers = ConfigurationDefaults.getInstance().getVerifiers(
						paramID);
				addVerifyListener = verifiers != null && verifiers.size() > 0;
			}
		}
		if (addVerifyListener) {
			if (verifyListener == null) {
				// Warning: e.text is not the full string, just what was added (paste or keypress)
				verifyListener = e -> {
					if (valueProcessor instanceof SwtConfigParameterValueProcessor) {
						e.doit = COConfigurationManager.verifyParameter(paramID, e.text);
						if (!e.doit) {
							return;
						}
					}

					if (validChars == null || e.text == null) {
						return;
					}
					int len = e.text.length();
					for (int i = 0; i < len; i++) {
						char c = e.text.charAt(i);
						if ((Arrays.binarySearch(validChars, c) < 0)
								|| (!validCharsCaseSensitive && (Arrays.binarySearch(validChars,
										Character.toLowerCase(c)) < 0))) {
							e.doit = false;
							break;
						}
					}
				};
			}
			inputField.addListener(SWT.Verify, verifyListener);
		} else {
			if (verifyListener != null) {
				inputField.removeListener(SWT.Verify, verifyListener);
				verifyListener = null;
			}
		}
	}

	public void setValidChars(String validChars, boolean caseSensitive) {
		validCharsCaseSensitive = caseSensitive;
		if (validChars == null) {
			this.validChars = null;
			rebuildVerifyListener();
			return;
		}
		if (caseSensitive) {
			validChars = validChars.toLowerCase();
		}
		this.validChars = new char[validChars.length()];
		validChars.getChars(0, validChars.length(), this.validChars, 0);
		Arrays.sort(this.validChars);
		rebuildVerifyListener();
	}

	@Override
	public void refreshControl() {
		super.refreshControl();

		Utils.execSWTThread(() -> {
			refreshSuffixControl(lblSuffix);

			String value = getValue();
			if (value == null) {
				return;
			}
			if (!inputField.isDisposed() && !inputField.getText().equals(value)) {
				inputField.setText(value);
			}
		});
	}

	@Override
	public Control[] getControls() {
		if (lblSuffix == null) {
			return super.getControls();
		}
		List<Control> list = new ArrayList<>(Arrays.asList(super.getControls()));
		list.add(lblSuffix);
		if (ourParent != null) {
			list.add(ourParent);
		}
		return list.toArray(new Control[0]);
	}

	public void setWidthInCharacters(int i) {
		Object data = inputField.getLayoutData();
		if (data instanceof GridData) {
			((GridData) data).widthHint = (int) Math.ceil(
					i * FontUtils.getCharacterWidth(inputField.getFont()));
			((GridData) data).horizontalAlignment = SWT.BEGINNING;
			((GridData) data).grabExcessHorizontalSpace = false;
			inputField.setLayoutData(data);
		}
	}

	public void setTextLimit(int textLimit) {
		Utils.execSWTThread(() -> {
			if (inputField.isDisposed()) {
				return;
			}
			inputField.setTextLimit(textLimit);
		});
	}

	public void setHintKey(String hintKey) {
		Utils.execSWTThread(() -> {
			if (inputField.isDisposed()) {
				return;
			}
			inputField.setMessage(
					hintKey == null ? "" : MessageText.getString(hintKey));
		});
	}

	private static class Textbox
		extends Text
	{
		public Textbox(Composite parent) {
			super(parent, SWT.BORDER);
		}

		// I know what I'm doing. Maybe ;)
		@SuppressWarnings("MethodDoesntCallSuperMethod")
		@Override
		public void checkSubclass() {
		}

		@Override
		public Point computeSize(int wHint, int hHint, boolean changed) {
			// Text widget, at least on Windows, forces the preferred width
			// to the width of the text inside of it
			// Fix this by forcing to LayoutData's minWidth
			if (hHint == 0 && !isVisible()) {

				return (new Point(0, 0));
			}
			Point pt = super.computeSize(wHint, hHint, changed);

			if (wHint == SWT.DEFAULT) {
				Object ld = getLayoutData();
				if (ld instanceof GridData) {
					if (((GridData) ld).grabExcessHorizontalSpace) {
						pt.x = 10;
					}
				}
			}

			return pt;
		}
	}
}
