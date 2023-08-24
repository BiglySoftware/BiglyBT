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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;

import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;

import com.biglybt.pif.ui.config.IntParameter;
import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;

/**
 * SWT widget representing an Int Parameter
 */
public class IntSwtParameter
	extends BaseSwtParameter<IntSwtParameter, Integer>
{
	public interface ValueProcessor
		extends SwtParameterValueProcessor<IntSwtParameter, Integer>
	{
		public default Integer
		getValue(
			List<Integer>	values )
		{
			if ( values.isEmpty()){
				return( null );
			}else{
				int result = values.get(0);
				for ( int v: values.subList( 1, values.size())){
					if ( v != result ){
						return( null );
					}
				}
				return( result );
			}
		}
	}

	private int valueWhenBlank;

	private Color colorHidden;

	private Label lblSuffix;

	private int iMinValue;

	private int iMaxValue;

	private boolean bTriggerOnFocusOut = false;

	private final Spinner spinner;

	private TimerEvent timedSaveEvent = null;

	private TimerEventPerformer timerEventSave;

	private boolean isZeroHidden = false;

	private boolean disableTimedSave = false;

	public IntSwtParameter(Composite composite, IntParameterImpl param) {
		this(composite, param.getConfigKeyName(), param.getLabelKey(),
				param.getSuffixLabelKey(),
				param.isLimited() ? param.getMinValue() : Integer.MIN_VALUE,
				param.isLimited() ? param.getMaxValue() : Integer.MAX_VALUE,
				param.isStoredAsString() ? new ValueProcessor() {
					@Override
					public Integer getValue(IntSwtParameter p) {
						return null;
					}

					@Override
					public boolean setValue(IntSwtParameter p, Integer value) {
						return false;
					}
				} : null);
		boolean storedAsString = param.isStoredAsString();
		if (storedAsString) {
			this.isZeroHidden = true;
			valueWhenBlank = param.getValueWhenBlank();
			//noinspection unchecked
			SwtParameterValueProcessor vp = new SwtConfigParameterValueProcessor(this,
					paramID, String.class) {
				@Override
				public Object getValue(SwtParameter p) {
					//noinspection unchecked
					String valFromConfig = (String) super.getValue(p);
					int val = valueWhenBlank;
					try {
						if (!valFromConfig.isEmpty()) {
							val = Integer.parseInt(valFromConfig);
						}
					} catch (Exception ignore) {
					}
					return val;
				}

				@Override
				public boolean setValue(SwtParameter p, Object value) {
					int newVal = ((Number) value).intValue();
					//noinspection unchecked
					return super.setValue(p, "" + newVal);
				}
			};
			//noinspection unchecked
			setValueProcessor(vp);
		}
		setPluginParameter(param);
	}

	public IntSwtParameter(Composite composite, String paramID, String labelKey,
			String suffixKey,
			SwtParameterValueProcessor<IntSwtParameter, Integer> valueProcessor) {
		this(composite, paramID, labelKey, suffixKey, Integer.MIN_VALUE,
				Integer.MAX_VALUE, valueProcessor);
	}

	/**
	 * Make a int value selecting ui.
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
	public IntSwtParameter(Composite composite, String paramID, String labelKey,
			String suffixLabelKey, int minValue, int maxValue,
			SwtParameterValueProcessor<IntSwtParameter, Integer> valueProcessor) {
		super(paramID);

		if (maxValue < minValue) {
			debug("max < min, not good");
			// common mistake to use -1 to indicate no-limit

			maxValue = Integer.MAX_VALUE;
		}
		iMaxValue = maxValue;
		iMinValue = minValue;

		timerEventSave = new TimerEventPerformer() {
			@Override
			public void perform(TimerEvent event) {
				Utils.execSWTThread(() -> {
					if (spinner.isDisposed()) {
						return;
					}
					if (DEBUG) {
						debug("setIntValue to " + spinner.getSelection()
								+ " via timeEventSave");
					}
					setValue(spinner.getSelection());
				});
			}
		};

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

		spinner = new Spinner(parent, SWT.BORDER | SWT.RIGHT);
		setMainControl(spinner);
		Color cFG = spinner.getForeground();
		Color cBG = spinner.getBackground();
		colorHidden = new Color(cFG.getDevice(), //
				(cFG.getRed() + cBG.getRed() * 2) / 3,
				(cFG.getGreen() + cBG.getGreen() * 2) / 3,
				(cFG.getBlue() + cBG.getBlue() * 2) / 3);
		spinner.addListener(SWT.Dispose, (event) -> colorHidden.dispose());
		spinner.setMinimum(minValue);
		spinner.setMaximum(maxValue);

		if (doGridData(composite)) {
			GridData gridData = new GridData();
			spinner.setLayoutData(gridData);
		}

		if (suffixLabelKey != null) {
			lblSuffix = new Label(parent, SWT.WRAP);
			Messages.setLanguageText(lblSuffix, suffixLabelKey);
			GridData gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			gridData.widthHint = SWT.DEFAULT;	// without this the suffix is invisible :(
			lblSuffix.setLayoutData( gridData );
			ClipboardCopy.addCopyToClipMenu(lblSuffix);
		}

		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (paramID != null) {
			setConfigValueProcessor(Integer.class);
		}

		spinner.addListener(SWT.Selection, event -> {
			if (!spinner.isFocusControl()) {
				setValue(spinner.getSelection());
			} else {
				bTriggerOnFocusOut = true;
				cancelTimedSaveEvent();

				if (DEBUG) {
					debug("create timeSaveEvent (" + spinner.getSelection() + ") ");
				}
				if (!disableTimedSave) {
					timedSaveEvent = SimpleTimer.addEvent("IntParam Saver",
							SystemTime.getOffsetTime(750), timerEventSave);
				}
			}
		});

		spinner.addListener(SWT.Modify, event -> {
			// setText and user input will call this -- don't put text changes here
			String text = spinner.getText();
			try {
				int val = text.isEmpty() ? valueWhenBlank : Integer.parseInt(text);
				validate(val);
			} catch (Throwable t) {
				updateControl(new ValidationInfo(false, t.getMessage()));
			}
		});

		spinner.addListener(SWT.FocusOut, event -> {
			// Note: If user blanks text field, OS will have refilled it with last
			//       getSelection() before this event is triggered
			if (bTriggerOnFocusOut) {
				if (DEBUG) {
					debug("focus out setIntValue(" + spinner.getSelection() + "/trigger");
				}
				cancelTimedSaveEvent();
				setValue(spinner.getSelection());
			}
		});
	}

	private void cancelTimedSaveEvent() {
		if (timedSaveEvent != null
				&& (!timedSaveEvent.hasRun() || !timedSaveEvent.isCancelled())) {
			if (DEBUG) {
				debug("cancel timeSaveEvent");
			}
			timedSaveEvent.cancel();
		}
	}

	public void setMinimumValue(int value) {
		if (iMinValue == value) {
			return;
		}
		Utils.execSWTThread(() -> {
			iMinValue = value;
			// Will not invoke SWT.Selection event
			spinner.setMinimum(value);

			if (iMinValue != Integer.MIN_VALUE && getIntValue() < iMinValue) {
				setValue(iMinValue);
			}
		});
	}

	public void setMaximumValue(int value) {
		if (iMaxValue == value) {
			return;
		}
		Utils.execSWTThread(() -> {
			iMaxValue = value;
			spinner.setMaximum(value);

			if (iMaxValue != Integer.MAX_VALUE && getIntValue() > iMaxValue) {
				setValue(iMaxValue);
			}
		});
	}

	@Override
	public ValidationInfo validate(Integer newValue) {
		ValidationInfo validationInfo = null;

		if (iMaxValue != Integer.MAX_VALUE && newValue > iMaxValue) {
			validationInfo = new ValidationInfo(false, "Max " + iMaxValue);
		} else if (iMinValue != Integer.MIN_VALUE && newValue < iMinValue) {
			validationInfo = new ValidationInfo(false, "Min " + iMinValue);
		}
		if (validationInfo == null) {
			return super.validate(newValue);
		}
		updateControl(validationInfo);
		return validationInfo;
	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		Utils.execSWTThread(() -> {
			if (spinner.isDisposed()) {
				return;
			}

			refreshSuffixControl(lblSuffix);

			if (pluginParam instanceof IntParameter) {
				IntParameter param = (IntParameter) this.pluginParam;
				setMinimumValue(
						param.isLimited() ? param.getMinValue() : Integer.MIN_VALUE);
				setMaximumValue(
						param.isLimited() ? param.getMaxValue() : Integer.MAX_VALUE);
			}

			Integer value = getValue();
			if (value == null) {
				return;	// indeterminate, should do something with this???
			}

			if (spinner.getSelection() != value) {
				if (DEBUG) {
					debug("spinner.setSelection(" + value + ") was "
							+ spinner.getSelection());
				}
				spinner.setSelection(value);
			}

			if (isZeroHidden) {
				Utils.setSkinnedForeground( spinner, value == 0 ? colorHidden : null );
				//spinner.setForeground( value == 0 ? colorHidden : null );
			}
		});
	}

	public int getIntValue() {
		Integer value = getValue();
		if (value == null) {
			return 0;
		}
		return value;
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

	public void disableTimedSave() {
		disableTimedSave = true;
	}

	public boolean isZeroHidden() {
		return isZeroHidden;
	}

	public void setZeroHidden(boolean isZeroHidden) {
		if (this.isZeroHidden == isZeroHidden) {
			return;
		}
		this.isZeroHidden = isZeroHidden;
		refreshControl();
	}

	public void setSuffixLabelKey(String suffixLabelKey) {
		if (lblSuffix == null) {
			return;
		}
		Utils.execSWTThread(() -> {
			if (lblSuffix.isDisposed()) {
				return;
			}
			Messages.updateLanguageKey(lblSuffix, suffixLabelKey);
		});
	}

	public void setSuffixLabelText(String text) {
		setSuffixLabelKey("!" + text + "!");
	}
}