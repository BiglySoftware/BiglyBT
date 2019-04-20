/*
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
 * @author epall
 *
 */

import com.biglybt.core.config.COConfigurationManager;

import com.biglybt.pif.ui.config.DirectoryParameter;

public class
DirectoryParameterImpl
	extends		ParameterImpl
	implements 	DirectoryParameter
{
	private String keyDialogTitle;
	private String keyDialogMessage;
	private String hintKey;

	public DirectoryParameterImpl(String key, String labelKey) {
		super(key, labelKey);
	}

	@Override
	public String
	getValue()
	{
		return COConfigurationManager.getStringParameter(configKey);
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
		String		str )
	{
		COConfigurationManager.setParameter(configKey, str );
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
