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

import com.biglybt.core.config.COConfigurationManager;

import com.biglybt.pif.ui.config.StringListParameter;

public class StringListParameterImpl extends ParameterImpl implements StringListParameter
{
	private String[] values;
	private String[] labels;
	private int listType = TYPE_DROPDOWN;
	private String suffixLabelKey;

	public StringListParameterImpl(String configKey, String labelKey,
			String[] values, String[] labels) {
		super(configKey, labelKey);
		this.values = values;
		this.labels = labels;
	}


	@Override
	public String[] getValues()
	{
	  return values;
	}

	@Override
	public String[] getLabels()
	{
	  return labels;
	}

	@Override
	public void
	setLabels(
		String[]	_labels )
	{
		labels = _labels;
	}

	@Override
	public String
	getValue()
	{
		return( COConfigurationManager.getStringParameter(getConfigKeyName()));
	}

	@Override
	public void
	setValue(
		String	s )
	{
		COConfigurationManager.setParameter(getConfigKeyName(), s);
	}

	@Override
	public void setListType(int listType) {
		this.listType = listType;
	}

	@Override
	public int getListType() {
		return listType;
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

	@Override
	public Object getValueObject() {
		return getValue();
	}
}
