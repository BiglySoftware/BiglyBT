/*
 * Created on 10 juil. 2003
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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.ui.config.IntListParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;

/**
 * SWT Parameter for selecting from a list of int values
 * Displayed as Combo box.
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 */
public class IntListSwtParameter
	extends BaseSwtParameter<IntListSwtParameter, Integer>
{
	public interface ValueProcessor
		extends SwtParameterValueProcessor<IntListSwtParameter, Integer>
	{
	}

	private final Combo list;

	private Label lblSuffix;

	private final int[] values;

	public IntListSwtParameter(Composite composite, IntListParameterImpl param) {
		this(composite, param.getConfigKeyName(), param.getLabelKey(),
				param.getSuffixLabelKey(), param.getValues(), param.getLabels(), null);
		setPluginParameter(param);
	}

	/**
	 * Make UI components for a list of int values
	 * <p/>
	 * When parent composite is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Composite is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the text shown before text box
	 * @param suffixLabelKey Messagebundle key for text shown after the text box
	 * @param values list of values that can be stored
	 * @param displayStrings fancy words representing each value 
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public IntListSwtParameter(Composite composite, String configID,
			String labelKey, String suffixLabelKey, int[] values,
			String[] displayStrings,
			SwtParameterValueProcessor<IntListSwtParameter, Integer> valueProcessor) {
		super(configID);
		this.values = values;

		boolean doGridData = doGridData(composite);

		createStandardLabel(composite, labelKey);

		Composite parent;
		if (suffixLabelKey == null) {
			parent = composite;
		} else {
			parent = new Composite(composite, SWT.NONE);
			GridLayout gridLayout = new GridLayout(2, false);
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			parent.setLayout(gridLayout);
			if (doGridData(composite)) {
				parent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			}
		}

		list = new Combo(parent, SWT.SINGLE | SWT.READ_ONLY);
		setMainControl(list);
		if (doGridData) {
			GridData gridData = new GridData();
			list.setLayoutData(gridData);
		}

		if (suffixLabelKey != null) {
			lblSuffix = new Label(parent, SWT.WRAP);
			Messages.setLanguageText(lblSuffix, suffixLabelKey);
			lblSuffix.setLayoutData(
					Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
			ClipboardCopy.addCopyToClipMenu(lblSuffix);
		}

		if (displayStrings.length != values.length) {
			Debug.out("displayStrings.length != values.length");
			return;
		}
		for (String displayString : displayStrings) {
			if (Utils.isGTK) {
				displayString += " ";
			}
			list.add(displayString);
		}

		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (paramID != null) {
			setConfigValueProcessor(Integer.class);
		}

		list.addListener(SWT.Selection,
				e -> setValue(values[list.getSelectionIndex()]));

	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		Utils.execSWTThread(() -> {
			if (list.isDisposed()) {
				return;
			}
			refreshSuffixControl(lblSuffix);

			int index = findIndex(getValue(), values);
			if (list.getSelectionIndex() != index) {
				list.select(index);
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
		return list.toArray(new Control[0]);
	}

	private static int findIndex(Integer value, int[] values) {
		if (value == null) {
			return 0;
		}
		for (int i = 0; i < values.length; i++) {
			if (values[i] == value) {
				return i;
			}
		}
		return 0;
	}
}
