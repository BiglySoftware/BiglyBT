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

package com.biglybt.ui.config;

import com.biglybt.core.internat.LocaleUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.StringListParameterImpl;

import com.biglybt.pif.ui.UIInstance;

import static com.biglybt.core.config.ConfigKeys.File.*;
import static com.biglybt.pif.ui.config.Parameter.MODE_ADVANCED;

public class ConfigSectionFileTorrentsDecoding
		extends ConfigSectionImpl {

	private final static int REQUIRED_MODE = MODE_ADVANCED;

	public static final String SECTION_ID = "torrent.decoding";

	public ConfigSectionFileTorrentsDecoding() {
		super(SECTION_ID, ConfigSectionFileTorrents.SECTION_ID, REQUIRED_MODE);
	}

	@Override
	public void build() {

		setDefaultUserModeForAdd(REQUIRED_MODE);

		// locale decoder
		LocaleUtilDecoder[] decoders = LocaleUtil.getSingleton().getDecoders();

		String[] decoderLabels = new String[decoders.length + 1];
		String[] decoderValues = new String[decoders.length + 1];

		decoderLabels[0] = MessageText.getString(
				"ConfigView.section.file.decoder.nodecoder");
		decoderValues[0] = "";

		for (int i = 1; i <= decoders.length; i++) {
			decoderLabels[i] = decoderValues[i] = decoders[i - 1].getName();
		}
		add(new StringListParameterImpl(SCFG_FILE_DECODER_DEFAULT,
				"ConfigView.section.file.decoder.label", decoderValues, decoderLabels));

		// locale always prompt

		BooleanParameterImpl paramDecoderPrompt = new BooleanParameterImpl(BCFG_FILE_DECODER_PROMPT,
			"ConfigView.section.file.decoder.prompt");
		paramDecoderPrompt.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramDecoderPrompt);

		// show lax decodings

		BooleanParameterImpl paramShowLax = new BooleanParameterImpl(BCFG_FILE_DECODER_SHOW_LAX,
			"ConfigView.section.file.decoder.showlax");
		paramShowLax.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramShowLax);

		// show all decoders

		add(new BooleanParameterImpl(BCFG_FILE_DECODER_SHOW_ALL,
				"ConfigView.section.file.decoder.showall"));

	}

}
