/*
 * Created on 9 juil. 2003
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
 *
 */
package com.biglybt.ui.swt.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.pifimpl.local.ui.config.StringParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;

/**
 * SWT widget representing a multiline String Parameter
 */
public class StringAreaSwtParameter
	extends BaseSwtParameter<StringAreaSwtParameter, String>
{

	private final Text inputField;

	private Label lblSuffix;
	private Label lblSuffixGap;

	public StringAreaSwtParameter(Composite curComposite,
			StringParameterImpl pluginParam) {
		this(curComposite, pluginParam.getConfigKeyName(),
				pluginParam.getLabelKey(), pluginParam.getSuffixLabelKey(),
				pluginParam.getMultiLine(), null);
		setPluginParameter(pluginParam);
	}

	/**
	 * Make SWT components representing a String Parameter
	 * <p/>
	 * When parent composite is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Composite is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the text shown before text box.
	 *                 null for no label, "" to allocate blank label 
	 * @param suffixKey Messagebundle key for text shown after the text box
	 *                 null for no suffix label, "" to allocate blank suffix label 
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public StringAreaSwtParameter(Composite composite, String configID,
			String labelKey, String suffixKey, int numLinesToShow,
			SwtParameterValueProcessor<StringAreaSwtParameter, String> valueProcessor) {
		super(configID);

		createStandardLabel(composite, labelKey);

		inputField = new Text(composite,
				SWT.BORDER | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL) {
			// I know what I'm doing. Maybe ;)
			@Override
			public void checkSubclass() {
			}

			// @see org.eclipse.swt.widgets.Text#computeSize(int, int, boolean)
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
		};
		setMainControl(inputField);

		if (suffixKey != null) {
			lblSuffixGap = new Label(composite, SWT.NONE);
			lblSuffix = new Label(composite, SWT.WRAP);
			Messages.setLanguageText(lblSuffix, suffixKey);
			lblSuffix.setLayoutData(
				Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
			ClipboardCopy.addCopyToClipMenu(lblSuffix);
		}
		

		if (doGridData(composite)) {
			GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = labelKey == null ? 2 : 1;
			gridData.heightHint = getPreferredHeight(numLinesToShow);
			inputField.setLayoutData(gridData);
		}

		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (paramID != null) {
			setConfigValueProcessor(String.class);
		}

		if (valueProcessor instanceof SwtConfigParameterValueProcessor) {
			List verifiers = ConfigurationDefaults.getInstance().getVerifiers(
					paramID);
			if (verifiers != null && verifiers.size() > 0) {
				// Warning: e.text is not the full string, just what was added (paste or keypress)
				inputField.addListener(SWT.Verify,
						e -> e.doit = COConfigurationManager.verifyParameter(configID,
								e.text));
			}
		}

		inputField.addListener(SWT.FocusOut, e -> setValue(inputField.getText()));

		inputField.addListener(SWT.KeyDown, event -> {
			int key = event.character;

			if (key <= 26 && key > 0) {

				key += 'a' - 1;
			}

			if (key == 'a' && event.stateMask == SWT.MOD1) {

				event.doit = false;

				inputField.selectAll();
			}
		});
	}

	public int getPreferredHeight(int line_count) {
		return (inputField.getLineHeight() * line_count);
	}

	@Override
	public Control[] getControls() {
		if (lblSuffix == null) {
			return super.getControls();
		}
		List<Control> list = new ArrayList<>(Arrays.asList(super.getControls()));
		list.add(lblSuffix);
		list.add(lblSuffixGap);
		return list.toArray(new Control[0]);
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
}
