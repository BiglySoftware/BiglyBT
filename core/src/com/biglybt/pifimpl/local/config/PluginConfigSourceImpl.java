/*
 * Created on 8 May 2008
 * Created by Allan Crooks
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.pifimpl.local.config;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.LightHashSet;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.config.PluginConfigSource;

/**
 * @author Allan Crooks
 *
 */
public class PluginConfigSourceImpl implements COConfigurationListener, ParameterListener, PluginConfigSource {

	private PluginConfig plugin_config;
	private File source_file;
	private boolean initialised;
	private Map data_map;
	private String key_prefix = null;
	private boolean dirty = false;
	private boolean migrate_settings = false;
	private LightHashSet params_monitored;

	public PluginConfigSourceImpl(PluginConfig plugin_config, String plugin_id) {
		this.plugin_config = plugin_config;
		this.key_prefix = null; // Grab the value at initialising.
		this.initialised = false;
		this.params_monitored = new LightHashSet();
		setConfigFilename(plugin_id + ".config");
	}

	@Override
	public void initialize() {
		shouldBeInitialised(false);
		this.initialised = true;
		this.data_map = FileUtil.readResilientFile(this.source_file.getParentFile(), this.source_file.getName(), true);
		this.key_prefix = plugin_config.getPluginConfigKeyPrefix();

		Map.Entry entry;
		String key;
		Iterator itr = this.data_map.entrySet().iterator();

		ConfigurationManager config = ConfigurationManager.getInstance();
		while (itr.hasNext()) {
			entry = (Map.Entry)itr.next();
			key = this.key_prefix + (String)entry.getKey();
			this.params_monitored.add(key);
			config.registerTransientParameter(key);
			config.setParameterRawNoNotify(key, entry.getValue());
			config.addParameterListener(key, this);
		}

		config.addListener(this);
	}

	@Override
	public File getConfigFile() {
		return this.source_file;
	}

	@Override
	public void setConfigFilename(String filename) {
		shouldBeInitialised(false);
		this.source_file = plugin_config.getPluginUserFile(FileUtil.convertOSSpecificChars(filename, false));
	}

	@Override
	public void save(boolean force) {
		shouldBeInitialised(true);
		if (!force && !this.dirty) {return;}
		FileUtil.writeResilientFile(this.source_file.getParentFile(), this.source_file.getName(), this.data_map, true);
		this.dirty = false;
	}

	@Override
	public void configurationSaved() {
		save(false);
	}

	@Override
	public void parameterChanged(String full_param) {
		shouldBeInitialised(true);
		String plugin_param = toPluginName(full_param);
		if (COConfigurationManager.hasParameter(full_param, true)) {
			Object val = ConfigurationManager.getInstance().getParameter(full_param);
			this.data_map.put(plugin_param, val);
		}
		else {
			this.data_map.remove(plugin_param);
		}
		this.dirty = true;
	}

	// Not exposed in the plugin API...
	public void registerParameter(String full_param) {
		shouldBeInitialised(true);
		if (!this.params_monitored.add(full_param)) {return;}
		ConfigurationManager config = ConfigurationManager.getInstance();
		config.registerTransientParameter(full_param);
		config.addParameterListener(full_param, this);
		if (this.migrate_settings && COConfigurationManager.hasParameter(full_param, true)) {
			this.parameterChanged(full_param);
		}
	}

	// Not exposed in the public API.
	public String getUsedKeyPrefix() {
		return this.key_prefix;
	}

	private String toPluginName(String name) {

		// We won't expect this.
		if (!name.startsWith(this.key_prefix)) {
			throw new RuntimeException("mismatch key prefix: " + name + ", " + this.key_prefix);
		}

		return name.substring(this.key_prefix.length());
	}

	private void shouldBeInitialised(boolean yes) {
		if (yes && !this.initialised) {
			throw new RuntimeException("source not yet initialised");
		}
		else if (!yes && this.initialised) {
			throw new RuntimeException("source already initialised");
		}
	}

	@Override
	public void forceSettingsMigration() {
		shouldBeInitialised(false);
		this.migrate_settings = true;
	}

}
