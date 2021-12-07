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

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;
import com.biglybt.ui.common.RememberedDecisionsManager;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.IntListParameter;
import com.biglybt.pif.ui.config.Parameter;

import static com.biglybt.core.config.ConfigKeys.*;

public class ConfigSectionMode
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "mode";

	public ConfigSectionMode() {
		super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void build() {

//    final String[] messTexts =
//    	{	"ConfigView.section.mode.beginner.wiki.definitions",
//    		"ConfigView.section.mode.intermediate.wiki.host",
//    		"ConfigView.section.mode.advanced.wiki.main",
//    };

		final String[] links = {
				Wiki.MODE_BEGINNER,
				Wiki.MODE_INTERMEDIATE,
				Wiki.MODE_ADVANCED,
		};

		int userMode = COConfigurationManager.getIntParameter(ICFG_USER_MODE);

		int[] values = {
			Parameter.MODE_BEGINNER,
			Parameter.MODE_INTERMEDIATE,
			Parameter.MODE_ADVANCED
		};
		String[] labels = {
			MessageText.getString("ConfigView.section.mode.beginner"),
			MessageText.getString("ConfigView.section.mode.intermediate"),
			MessageText.getString("ConfigView.section.mode.advanced")
		};
		Map<Integer, String> mapInfos = new HashMap<>();
		mapInfos.put(Parameter.MODE_BEGINNER,
				"ConfigView.section.mode.beginner.text");
		mapInfos.put(Parameter.MODE_INTERMEDIATE,
				"ConfigView.section.mode.intermediate.text");
		mapInfos.put(Parameter.MODE_ADVANCED,
				"ConfigView.section.mode.advanced.text");

		IntListParameterImpl paramUserMode = new IntListParameterImpl(ICFG_USER_MODE,
				null, values, labels);
		paramUserMode.setListType(IntListParameter.TYPE_RADIO_COMPACT);
		add(paramUserMode);

		LabelParameterImpl paramInfo = new LabelParameterImpl("");
		add("mode.info", paramInfo);

		HyperlinkParameterImpl paramInfoLink = new HyperlinkParameterImpl(
				"ConfigView.label.please.visit.here", links[userMode]);
		add(paramInfoLink);

		paramUserMode.addListener(param -> {
			int newUserMode = ((IntListParameter) param).getValue();
			paramInfoLink.setHyperlink(links[newUserMode]);
			String key = mapInfos.get(newUserMode);

			if (MessageText.keyExists(key + "1")) {
				key = key + "1";
			}

			paramInfo.setLabelText("-> " + MessageText.getString(key));
		});
		paramUserMode.fireParameterChanged();

		ParameterGroupImpl pgRadio = new ParameterGroupImpl(
				"ConfigView.section.mode.title", paramUserMode, paramInfo,
				paramInfoLink);
		add("pgRadio", pgRadio);

		add("gap1", new LabelParameterImpl(""));
		add("gap2", new LabelParameterImpl(""));
		add("gap3", new LabelParameterImpl(""));

		// reset to defaults

		ActionParameterImpl reset_button = new ActionParameterImpl(
				"ConfigView.section.mode.resetdefaults", "Button.reset");
		add(reset_button);
		reset_button.addListener(param -> {
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			if (uiFunctions == null) {
				resetAll();
				return;
			}
			UIFunctionsUserPrompter prompter = uiFunctions.getUserPrompter(
					MessageText.getString("resetconfig.warn.title"),
					MessageText.getString("resetconfig.warn"), new String[] {
						MessageText.getString("Button.ok"),
						MessageText.getString("Button.cancel")
			}, 1);
			if (prompter == null) {
				resetAll();
				return;
			}
			prompter.open(result -> {
				if (result != 0) {
					return;
				}
				resetAll();
			});
		});
	}

	private static void resetAll() {
		RememberedDecisionsManager.ensureLoaded();
		COConfigurationManager.resetToDefaults();
	}
}
