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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pifimpl.local.ui.config.FloatParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.utils.FontUtils;

import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;

/**
 * SWT Parameter representing a Float value
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 */
public class FloatSwtParameter
	extends BaseSwtParameter<FloatSwtParameter, Float>
{
	/**
	 * Value Processor that's parameterized for this class and Float values
	 */
	public interface ValueProcessor
		extends SwtParameterValueProcessor<FloatSwtParameter, Float>
	{
		public default Float
		getValue(
			List<Float>	values )
		{
			if ( values.isEmpty()){
				return( null );
			}else{
				float result = values.get(0);
				for ( float v: values.subList( 1, values.size())){
					if ( v != result ){
						return( null );
					}
				}
				return( result );
			}
		}
	}

	private final DecimalFormat df;

	private final Text inputField;

	private Label lblSuffix;

	private float fMinValue;

	private float fMaxValue;

	private boolean allowZero;

	public FloatSwtParameter(Composite composite,
			FloatParameterImpl pluginParam) {
		this(composite, pluginParam.getConfigKeyName(), pluginParam.getLabelKey(),
				pluginParam.getSuffixLabelKey(), pluginParam.getMinValue(),
				pluginParam.getMaxValue(), pluginParam.isAllowZero(),
				pluginParam.getNumDigitsAfterDecimal(), null);
		setPluginParameter(pluginParam);
	}

	/**
	 * Make a float value selecting ui.
	 * <p/>
	 * When parent composite is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Composite is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the text shown before text box.
	 *                 null for no label, "" to allocate blank label 
	 * @param suffixLabelKey Messagebundle key for text shown after the text box
	 *                 null for no suffix label, "" to allocate blank suffix label 
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public FloatSwtParameter(Composite composite, String paramID, String labelKey,
			String suffixLabelKey, float minValue, float maxValue, boolean allowZero,
			int digitsAfterDecimal,
			SwtParameterValueProcessor<FloatSwtParameter, Float> valueProcessor) {
		super(paramID);

		fMinValue = minValue;
		fMaxValue = maxValue;
		this.allowZero = allowZero;

		df = new DecimalFormat(digitsAfterDecimal > 0 ? "0.000000000000" : "0");
		df.setGroupingUsed(false);
		df.setMaximumFractionDigits(digitsAfterDecimal);

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

		inputField = new Text(parent, SWT.BORDER | SWT.RIGHT);
		setMainControl(inputField);

		int maxLen = df.format(fMaxValue > 0 && fMaxValue != Float.MAX_VALUE
				? fMaxValue : 9999).length();
		int w = (int) (FontUtils.getCharacterWidth(inputField.getFont()) * maxLen);
		if (doGridData(composite)) {
			GridData gridData = new GridData();
			gridData.widthHint = w;
			inputField.setLayoutData(gridData);
		} else if (composite.getLayout() instanceof RowLayout) {
			RowData rowData = new RowData();
			rowData.width = w;
			inputField.setLayoutData(rowData);
		}

		if (suffixLabelKey != null) {
			lblSuffix = new Label(parent, SWT.WRAP);
			Messages.setLanguageText(lblSuffix, suffixLabelKey);
			lblSuffix.setLayoutData(
					Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
			ClipboardCopy.addCopyToClipMenu(lblSuffix);
		}

		if (valueProcessor == null) {
			setConfigValueProcessor(Float.class);
		} else {
			setValueProcessor(valueProcessor);
		}

		inputField.addListener(SWT.Verify, e -> {
			// e.text is new chars, not string
			if (e.text == null || e.text.isEmpty()) {
				return;
			}
			char[] chars = new char[e.text.length()];
			e.text.getChars(0, chars.length, chars, 0);
			for (char aChar : chars) {
				if (!((aChar >= '0' && aChar <= '9') || aChar == DecimalFormatSymbols.getInstance().getDecimalSeparator())) {
					e.doit = false;
					return;
				}
			}
		});

		inputField.addListener(SWT.Modify, event -> {
			// setText and user input will call this -- don't put text changes here
			try {
				validate( DisplayFormatters.parseFloat(df, inputField.getText()));
			} catch (Throwable ignore) {
				ValidationInfo validationInfo = new ValidationInfo(false,
						MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
				updateControl(validationInfo);
			}
		});

		inputField.addListener(SWT.FocusOut, event -> {
			try {
				setValue(DisplayFormatters.parseFloat(df, inputField.getText()));
			} catch (Throwable ignore) {
			}
		});
	}

	@Override
	public ValidationInfo validate(Float newValue) {
		ValidationInfo validationInfo = null;

		if (!allowZero && newValue == 0) {
			validationInfo = new ValidationInfo(false,
					MessageText.getString("warning.zero.not.allowed"));
		}
		if (newValue < fMinValue) {
			validationInfo = new ValidationInfo(false,
					MessageText.getString("warning.min", new String[] {
						df.format(fMinValue)
					}));
		}
		if (newValue > fMaxValue && fMaxValue > -1) {
			validationInfo = new ValidationInfo(false,
					MessageText.getString("warning.max", new String[] {
						df.format(fMaxValue)
					}));
		}

		if (validationInfo == null) {
			return super.validate(newValue);
		}
		updateControl(validationInfo);
		return validationInfo;
	}
//		try {
//			val = BigDecimal.valueOf(val).setScale(iDigitsAfterDecimal,
//					RoundingMode.HALF_EVEN).floatValue();
//		} catch (Throwable t) {
//			Debug.out(t);
//		}

	@Override
	public void refreshControl() {
		super.refreshControl();
		Utils.execSWTThread(() -> {
			if (inputField.isDisposed()) {
				return;
			}

			refreshSuffixControl(lblSuffix);

			Float value = getValue();
			if (value == null) {
				inputField.setText("");
				return;
			}
			String correctText = df.format(value);
			if (!inputField.getText().equals(correctText)) {
				// Will trigger SWT.Modify which set the background
				inputField.setText(correctText);
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
}
