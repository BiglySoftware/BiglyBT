/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

package com.biglybt.pifimpl.local.ui.config;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pif.ui.config.DateTimeParameter;

public class 
DateTimeParameterImpl
	extends ParameterImpl
	implements DateTimeParameter
{
	private String keyDialogTitle;
	private String keyDialogMessage;
	private String hintKey;

	public 
	DateTimeParameterImpl(
		String key, String labelKey) 
	{
		super(key, labelKey);
	}

	@Override
	public long
	getValue()
	{
		return COConfigurationManager.getLongParameter(configKey);
	}

	@Override
	public Object getValueObject() {
		return getValue();
	}

	@Override
	public void setDialogTitleKey(String key) {
		keyDialogTitle = key;
	}

	public String getKeyDialogTitle() {
		return keyDialogTitle;
	}

	public void setDialogMessageKey(String key) {
		keyDialogMessage = key;
	}

	public String getKeyDialogMessage() {
		return keyDialogMessage;
	}

	@Override
	public void
	setValue(
		long		value )
	{
		COConfigurationManager.setParameter(configKey, value );
	}

	@Override
	public String getHintKey() {
		return hintKey;
	}

	@Override
	public void setHintKey(String hintKey) {
		this.hintKey = hintKey;
		refreshControl();
	}

	@Override
	public void setHintText(String text) {
		this.hintKey = text == null ? null : "!" + text + "!";
		refreshControl();
	}
}
