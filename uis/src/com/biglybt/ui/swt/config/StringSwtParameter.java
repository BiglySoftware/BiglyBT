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

import org.eclipse.swt.widgets.Composite;

import com.biglybt.pifimpl.local.ui.config.StringParameterImpl;

/**
 * SWT widget representing a String Parameter
 */
public class StringSwtParameter
	extends BaseSwtStringParameter<StringSwtParameter>
{
	public StringSwtParameter(Composite parent, StringParameterImpl pluginParam) {
		super(parent, pluginParam.getConfigKeyName(), pluginParam.getLabelKey(),
				pluginParam.getSuffixLabelKey(), null);
		setPluginParameter(pluginParam);
		String validChars = pluginParam.getValidChars();
		if (validChars != null) {
			setValidChars(validChars, pluginParam.isValidCharsCaseSensitive());
		}
		int characters = pluginParam.getWidthInCharacters();
		if (characters > 0) {
			setWidthInCharacters(characters);
		}
		int textLimit = pluginParam.getTextLimit();
		if (textLimit > 0) {
			setTextLimit(textLimit);
		}
		String hintKey = pluginParam.getHintKey();
		if (hintKey != null) {
			setHintKey(hintKey);
		}
	}

	/**
	 * Make SWT components representing a String Parameter
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
	public StringSwtParameter(Composite composite, String paramID,
			String labelKey, String suffixLabelKey,
			SwtParameterValueProcessor<StringSwtParameter, String> valueProcessor) {
		super(composite, paramID, labelKey, suffixLabelKey, valueProcessor);
	}
}
