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

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.regex.Pattern;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.pifimpl.local.ui.config.UIParameterImpl;

import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;

public abstract class ConfigSectionImpl
	implements BaseConfigSection
{
	public static final String L10N_SECTION_PREFIX = "ConfigView.section.";

	public interface ConfigDetailsCallback
	{
		void run(Map<String, ParameterImpl> mapPluginParams);
	}

	private final String sectionID;

	private final String parentSectionID;

	private final int minUserMode;

	private int defaultMode = Parameter.MODE_BEGINNER;

	private boolean isBuilt = false;

	private String[] defaultUITypes = null;

	private ConfigSectionRebuildRunner rebuildRunner;

	public ConfigSectionImpl(WeakReference<BasicPluginConfigModel> model_ref) {
		BasicPluginConfigModel model = model_ref.get();
		if (model == null) {
			sectionID = "DEAD" + Math.random();
			parentSectionID = ConfigSection.SECTION_ROOT;
		} else {
			String section = model.getSection();
			sectionID = section == null || section.isEmpty() ? "" + model : section;
			String parentSection = model.getParentSection();
			parentSectionID = parentSection == null || parentSection.isEmpty()
					? ConfigSection.SECTION_ROOT : parentSection;
		}
		minUserMode = Parameter.MODE_BEGINNER;
	}

	public ConfigSectionImpl(String sectionID, String parentSectionID) {
		this(sectionID, parentSectionID, Parameter.MODE_BEGINNER);
	}

	public ConfigSectionImpl(String sectionID, String parentSectionID,
			int minUserMode) {
		this.sectionID = sectionID;
		this.parentSectionID = parentSectionID;
		this.minUserMode = minUserMode;
	}

	@Override
	public final String getConfigSectionID() {
		return sectionID;
	}

	@Override
	public final String getParentSectionID() {
		return parentSectionID;
	}

	@Override
	public final void postBuild() {
		isBuilt = true;
	}

	@Override
	public final boolean isBuilt() {
		return isBuilt;
	}

	protected final Map<String, ParameterImpl> mapPluginParams = new LinkedHashMap<>();

	/**
	 * Any Parameters added after this call will be auto-assigned this user mode
	 */
	public final void setDefaultUserModeForAdd(int defaultMode) {
		this.defaultMode = defaultMode;
	}

	/**
	 * Any Parameters added after this call will be auto-assigned these UI Types
	 *
	 * @param defaultUITypes {@link com.biglybt.pif.ui.UIInstance}.UIT_SWT, UIInstance.UIT_CONSOLE, etc
	 */
	public final void setDefaultUITypesForAdd(String... defaultUITypes) {
		this.defaultUITypes = defaultUITypes;
	}

	@SafeVarargs
	protected final <T extends ParameterImpl> T add( T param, List<Parameter>... otherLists) {
		return(add(param, defaultMode, otherLists));
	}

	@SafeVarargs
	protected final <T extends ParameterImpl> T add(T param, int minMode,
			List<Parameter>... otherLists) {
		return(add(guessParamName(param, true), param, minMode, otherLists));
	}

	@SafeVarargs
	protected final <T extends ParameterImpl> T add(String key, T param,
			List<Parameter>... otherLists) {
		int minMode = Parameter.MODE_ADVANCED + 1;
		for (List<Parameter> otherList : otherLists) {
			for (Parameter child : otherList) {
				int childMinMode = child.getMinimumRequiredUserMode();
				if (childMinMode < minMode) {
					minMode = childMinMode;
					if (minMode == 0) {
						break;
					}
				}
			}
			if (minMode == 0) {
				break;
			}
		}
		if (minMode > Parameter.MODE_ADVANCED) {
			minMode = defaultMode;
		}
		return( add(key, param, minMode, otherLists));
	}

	@SafeVarargs
	protected final <T extends ParameterImpl> T add(String key, T param, int minMode,
			List<Parameter>... otherLists) {
		if (isBuilt) {
			Debug.out(getConfigSectionID() + "] can't add " + key + " after build()");
			return( param );
		}
		if (otherLists != null) {
			for (List<Parameter> list : otherLists) {
				list.add(param);
			}
		}
		if (minMode != Parameter.MODE_BEGINNER) {
			param.setMinimumRequiredUserMode(minMode);
		}
		if (defaultUITypes != null) {
			param.setAllowedUiTypes(defaultUITypes);
		}
		
		if (param instanceof ParameterGroupImpl) {
			((ParameterGroupImpl) param).setId(key);
		}

		mapPluginParams.put(key, param);
		
		return( param );
	}

	protected String guessParamName(Parameter param, boolean warnExists) {
		String name = param.getConfigKeyName();
		if (name == null || name.isEmpty()) {
			if (param instanceof HyperlinkParameter) {
				name = ((HyperlinkParameter) param).getHyperlink();
				if (mapPluginParams.containsKey(name)) {
					name += "/" + param.toString();
				}
			} else if (param instanceof ActionParameter) {
				name = ((ActionParameter) param).getActionResource() + ","
						+ param.toString();
			} else if (param instanceof ParameterGroupImpl) {
				name = ((ParameterGroupImpl) param).getGroupTitleKey();
				if (name == null || name.isEmpty()) {
					name = param.getLabelKey();
					if (name == null || name.isEmpty()) {
						name = param.toString();
					}
				}
			} else if (param instanceof UIParameterImpl) {
				name = param.toString();
			} else {
				name = param.getLabelKey();
			}
		}
		if (name == null || name.isEmpty()) {
			if (Constants.isCVSVersion()) {
				Logger.log(new LogEvent(LogIDs.PLUGIN, LogEvent.LT_WARNING,
						getConfigSectionID() + "] param (" + param
								+ ") missing config key, use #add(key, ...). Will make up name"));
			}
			name = param.toString();
		}
		if (warnExists && mapPluginParams.containsKey(name)) {
			if (Constants.isCVSVersion()) {
				Logger.log(new LogEvent(LogIDs.PLUGIN, LogEvent.LT_WARNING,
						getConfigSectionID() + "] Already have key '" + name + "' "
								+ mapPluginParams.get(name)));
			}
			name += "/";
		}
		return name;
	}

	@Override
	public final Parameter[] getParamArray() {
		return mapPluginParams.values().toArray(new Parameter[0]);
	}

	@Override
	public final ParameterImpl getPluginParam(String key) {
		return mapPluginParams.get(key);
	}

	public final String findPluginParamKey(Parameter param) {
		for (String key : mapPluginParams.keySet()) {
			if (param == mapPluginParams.get(key)) {
				return key;
			}
		}
		return null;
	}

	@Override
	public void saveConfigSection() {
	}

	@Override
	public void deleteConfigSection() {
		for (ParameterImpl parameter : mapPluginParams.values()) {
			parameter.destroy();
		}
		mapPluginParams.clear();
		isBuilt = false;
	}

	@Override
	public final int getMinUserMode() {
		return minUserMode;
	}

	@Override
	public void setRebuildRunner(ConfigSectionRebuildRunner rebuildRunner) {
		this.rebuildRunner = rebuildRunner;
	}

	@Override
	public void requestRebuild() {
		if (rebuildRunner != null) {
			rebuildRunner.rebuildSection(this);
		}
	}

	@Override
	public final int getMaxUserMode() {
		int maxMode = getMinUserMode();
		if (maxMode == Parameter.MODE_ADVANCED) {
			return maxMode;
		}

		for (ParameterImpl parameter : mapPluginParams.values()) {
			int minMode = parameter.getMinimumRequiredUserMode();
			if (minMode > maxMode) {
				maxMode = minMode;
				if (maxMode == Parameter.MODE_ADVANCED) {
					break;
				}
			}
		}
		return maxMode;
	}

	public String getSectionNameKey() {
		return getSectionNameKey(sectionID);
	}

	public static String getSectionNameKey(String sectionID) {
	
		String section_key;
		
		if ( sectionID.startsWith( "!" ) && sectionID.endsWith( "!" )){
			
			section_key = sectionID;
			
		}else{
			
			section_key = L10N_SECTION_PREFIX + sectionID;
		}

		// Plugins don't use prefix by default (via UIManager.createBasicPluginConfigModel).
		// However, when a plugin overrides the name via BasicPluginConfigModel.setLocalizedName(..)
		// it creates a message bundle key with the prefix.  Therefore,
		// key with prefix overrides name key.
		if (!MessageText.keyExists(section_key)
				&& MessageText.keyExists(sectionID)) {
			section_key = sectionID;
		}

		return section_key;
	}

	@Override
	public List<Parameter> search(Pattern regex) {
		List result = new ArrayList();
		for (ParameterImpl parameter : mapPluginParams.values()) {
			if (parameter.search(regex)) {
				result.add(parameter);
			}
		}
		return result;
	}
}
