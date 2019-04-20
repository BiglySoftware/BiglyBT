/*
 * File    : GenericParameter.java
 * Created : Nov 21, 2003
 * By      : epall
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.ui.config;


/**
 * @author Olivier
 *
 */

import com.biglybt.core.config.COConfigurationManager;

import com.biglybt.pif.ui.config.IntParameter;
import com.biglybt.pif.ui.config.ParameterValidator;

public class IntParameterImpl
	extends ParameterImpl
	implements IntParameter
{
	private boolean limited;
	private int min_value;
	private int max_value = Integer.MAX_VALUE;
	private boolean storedAsString;
	private int valueWhenBlank;
	private String suffixLabelKey;

	public IntParameterImpl(String configKey, String labelKey) {
		super(configKey, labelKey);

		this.limited = false;
	}

	public IntParameterImpl(String configKey, String labelKey, int min_value,
			int max_value) {
		this(configKey, labelKey);
		this.min_value = min_value;
		this.max_value = max_value;
		this.limited = true;
	}


	@Override
	public int
	getValue()
	{
		if (!storedAsString) {
			return COConfigurationManager.getIntParameter(configKey);
		}

		String valFromConfig = COConfigurationManager.getStringParameter(configKey);
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
	public Object getValueObject() {
		return getValue();
	}

	@Override
	public void
	setValue(
		int	value )
	{
		if (!storedAsString) {
			COConfigurationManager.setParameter(configKey, value);
		} else {
			COConfigurationManager.setParameter(configKey, "" + value);
		}
	}

	@Override
	public boolean isLimited() {return limited;}
	@Override
	public int getMinValue() {return this.min_value;}
	@Override
	public int getMaxValue() {return this.max_value;}

	@Override
	public void setMinValue(int min_value) {
		limited = true;
		this.min_value = min_value;
		refreshControl();
	}

	@Override
	public void setMaxValue(int max_value) {
		limited = true;
		this.max_value = max_value;
		refreshControl();
	}

	@Override
	public void addIntegerValidator(ParameterValidator<Integer> validator) {
		super.addValidator(validator);
	}

	public boolean isStoredAsString() {
		return storedAsString;
	}

	/**
	 * Note: We can't easily propogate this to {@link IntParameter}, because
	 * {@link com.biglybt.pif.ui.model.BasicPluginConfigModel#addIntParameter2(String, String, int)}
	 * sets the default value to int before setStoredAsString can be called. This
	 * introducing a window where retrieving the parameter value may cause a cast
	 * error.
	 * <p/>
	 * Plus, we don't want to encourage plugins to store ints as strings.
	 */
	public void setStoredAsString(boolean storedAsString, int valueWhenBlank) {
		this.storedAsString = storedAsString;
		this.valueWhenBlank = valueWhenBlank;
	}

	public int getValueWhenBlank() {
		return valueWhenBlank;
	}

	@Override
	public String getSuffixLabelKey() {
		return suffixLabelKey;
	}

	@Override
	public void setSuffixLabelKey(String suffixLabelKey) {
		this.suffixLabelKey = suffixLabelKey;
		refreshControl();
	}

	@Override
	public void setSuffixLabelText(String text) {
		this.suffixLabelKey = "!" + text + "!";
		refreshControl();
	}
}
