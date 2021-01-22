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
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.pifimpl.local.ui.config.IntListParameterImpl;
import com.biglybt.ui.swt.Utils;

import com.biglybt.pif.ui.config.IntListParameter;

/**
 * SWT Parameter for selecting from a list of int values.
 * Displayed as Radio Buttons.
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 */
public class IntRadioListSwtParameter
	extends BaseSwtParameter<IntRadioListSwtParameter, Integer>
{

	private final Composite cHolder;

	private final Button[] radios;

	private final int[] values;

	public IntRadioListSwtParameter(Composite composite,
			IntListParameterImpl param) {
		this(composite, param.getConfigKeyName(), param.getLabelKey(),
				param.getValues(), param.getLabels(),
				param.getListType() == IntListParameter.TYPE_RADIO_COMPACT, null);
		setPluginParameter(param);
	}

	/**
	 * Make UI components for a list of in values, displayed as radio buttons
	 * <p/>
	 * When parent composite is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Composite is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the text shown before text box
	 * @param values list of values that can be stored
	 * @param displayStrings fancy words representing each value
	 * @param compact true - all in one wrappable row; false - one option per row 
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public IntRadioListSwtParameter(Composite composite, String paramID,
			String labelKey, int[] values, String[] displayStrings, boolean compact,
			SwtParameterValueProcessor<IntRadioListSwtParameter, Integer> valueProcessor) {
		super(paramID);
		this.values = values;

		boolean doGridData = doGridData(composite);

		createStandardLabel(composite, labelKey);

		cHolder = new Composite(composite, SWT.NONE);
		setMainControl(cHolder);
		RowLayout rowLayout = Utils.getSimpleRowLayout(false);
		rowLayout.type = compact ? SWT.HORIZONTAL : SWT.VERTICAL;
		cHolder.setLayout(rowLayout);

		if (doGridData) {
			GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			cHolder.setLayoutData(gridData);
		}

		radios = new Button[displayStrings.length];

		if (displayStrings.length != values.length) {
			return;
		}
		Listener listener = event -> {
			Button button = (Button) event.widget;
			if (button == null || button.isDisposed()) {
				return;
			}
			if (button.getSelection()) {
				int val = ((Number) button.getData("value")).intValue();
				setValue(val);
			}
		};

		for (int i = 0; i < displayStrings.length; i++) {
			radios[i] = new Button(cHolder, SWT.RADIO);
			radios[i].setText(displayStrings[i]);
			radios[i].setData("value", values[i]);
			radios[i].addListener(SWT.Selection, listener);
			radios[i].setLayoutData(new RowData());	// buttons need this on linux at least
		}

		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (this.paramID != null) {
			setConfigValueProcessor(Integer.class);
		}
	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		Utils.execSWTThread(() -> {
			if (cHolder.isDisposed()) {
				return;
			}

			Integer val = getValue();
			int index = -1;
			for (int i = 0; i < values.length; i++) {
				if (values[i] == val) {
					index = i;
					break;
				}
			}
			if (index >= 0) {
				radios[index].setSelection(true);
			}
		});
	}

	@Override
	public Control[] getControls() {
		List<Control> list = new ArrayList<>();
		list.add(cHolder);
		Control relatedControl = getRelatedControl();
		if (relatedControl != null) {
			list.add(relatedControl);
		}
		Collections.addAll(list, radios);
		return list.toArray(new Control[0]);
	}
}
