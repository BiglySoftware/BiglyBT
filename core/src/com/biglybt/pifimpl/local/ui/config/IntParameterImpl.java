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
		return COConfigurationManager.getIntParameter(configKey);
	}

	@Override
	public void
	setValue(
		int	b )
	{
		COConfigurationManager.setParameter(configKey, b );
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
