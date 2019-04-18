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

import com.biglybt.pif.ui.config.ParameterValidator;
import com.biglybt.pif.ui.config.StringParameter;

public class StringParameterImpl
	extends ParameterImpl
	implements StringParameter
{
	private int		line_count;

	private int widthInCharacters;

	private String validChars;

	private boolean validCharsCaseSensitive;

	private String suffixLabelKey;

	private int textLimit;

	private String hintKey;

	public StringParameterImpl(String coreConfigKey, String labelKey) {
		super(coreConfigKey, labelKey);
	}

	@Override
	public String
	getValue()
	{
		return COConfigurationManager.getStringParameter(configKey);
	}

	@Override
	public void
	setValue(
		String	s )
	{
		COConfigurationManager.setParameter(configKey, s);
	}

	@Override
	public void
	setMultiLine(
		int	visible_line_count )
	{
		line_count = visible_line_count;
	}

	public int
	getMultiLine()
	{
		return( line_count );
	}

	@Override
	public void setWidthInCharacters(int widthInCharacters) {
		this.widthInCharacters = widthInCharacters;
	}

	@Override
	public int getWidthInCharacters() {
		return widthInCharacters;
	}

	@Override
	public void setValidChars(String chars, boolean caseSensitive) {
		this.validChars = chars;
		this.validCharsCaseSensitive = caseSensitive;
	}

	@Override
	public void addStringValidator(
			ParameterValidator<String> stringParamValidator) {
		addValidator(stringParamValidator);
	}

	public String getValidChars() {
		return validChars;
	}

	public boolean isValidCharsCaseSensitive() {
		return validCharsCaseSensitive;
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
	public void setTextLimit(int textLimit) {
		this.textLimit = textLimit;
	}

	@Override
	public int getTextLimit() {
		return textLimit;
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

	@Override
	public Object getValueObject() {
		return getValue();
	}
}
