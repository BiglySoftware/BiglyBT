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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManager;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.UIParameterContext;

import static com.biglybt.core.config.ConfigKeys.IPFilter.*;

public class ConfigSectionIPFilter
		extends ConfigSectionImpl {
	public static final String SECTION_ID = "ipfilter";

	IpFilter filter;

	LabelParameterImpl percentage_blocked;

	private UIParameterContext paramContextIPEditor;

	public ConfigSectionIPFilter() {
		super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	public void init(UIParameterContext paramContextIPEditor) {
		this.paramContextIPEditor = paramContextIPEditor;
	}

	@Override
	public void build() {

		if (!CoreFactory.isCoreRunning()) {
			add(new LabelParameterImpl("core.not.available"));
			return;
		}

		final IpFilterManager ipFilterManager = CoreFactory.getSingleton().getIpFilterManager();
		filter = ipFilterManager.getIPFilter();

		// row: enable filter + allow/deny

		BooleanParameterImpl enabled = new BooleanParameterImpl(
				BCFG_IP_FILTER_ENABLED, "ConfigView.section.ipfilter.enable");
		add(enabled);

		BooleanParameterImpl deny = new BooleanParameterImpl(
				BCFG_IP_FILTER_ALLOW, "ConfigView.section.ipfilter.allow");
		add(deny);

		// row persist banning

		add(new BooleanParameterImpl(BCFG_IP_FILTER_BANNING_PERSISTENT,
				"ConfigView.section.ipfilter.persistblocking"));

		add(new BooleanParameterImpl(BCFG_IP_FILTER_DISABLE_FOR_UPDATES,
				"ConfigView.section.ipfilter.disable.for.updates"));

		List<Parameter> listBlockBanning = new ArrayList<>();

		// row block bad + group ban

		BooleanParameterImpl enable_bad_data_banning = new BooleanParameterImpl(
				BCFG_IP_FILTER_ENABLE_BANNING,
				"ConfigView.section.ipfilter.enablebanning");
		add(enable_bad_data_banning, listBlockBanning);

		FloatParameterImpl discard_ratio = new FloatParameterImpl(
				FCFG_IP_FILTER_BAN_DISCARD_RATIO,
				"ConfigView.section.ipfilter.discardbanning");
		add(discard_ratio, listBlockBanning);

		IntParameterImpl discard_min = new IntParameterImpl(
				ICFG_IP_FILTER_BAN_DISCARD_MIN_KB, "");
		add(discard_min, listBlockBanning);
		discard_min.setIndent(1, true);
		discard_min.setLabelText(MessageText.getString(
				"ConfigView.section.ipfilter.discardminkb", new String[]{
						DisplayFormatters.getUnit(DisplayFormatters.UNIT_KB)
				}));

		// block banning

		IntParameterImpl block_banning = new IntParameterImpl(
				ICFG_IP_FILTER_BAN_BLOCK_LIMIT,
				"ConfigView.section.ipfilter.blockbanning", 0, 256);
		add(block_banning, listBlockBanning);

		// don't ban LAN
		
		BooleanParameterImpl dont_ban_lan = new BooleanParameterImpl(
				BCFG_IP_FILTER_DONT_BAN_LAN,
				"ConfigView.section.ipfilter.dontbanlan");
		add(dont_ban_lan, listBlockBanning);

		
		// triggers

		enable_bad_data_banning.addEnabledOnSelection(block_banning, discard_ratio,
				discard_min);

		ParameterGroupImpl pgBlockBanning = new ParameterGroupImpl(
				"ConfigView.section.ipfilter.peerblocking.group", listBlockBanning);
		add("pgBlockBanning", pgBlockBanning);

		//////

		List<Parameter> listAutoLoad = new ArrayList<>();

		// Load from file

		// TODO: Check if it allows urls
		FileParameterImpl pathParameter = new FileParameterImpl(
				SCFG_IP_FILTER_AUTOLOAD_FILE,
				"ConfigView.section.ipfilter.autoload.file",
				"*.dat" + File.pathSeparator + "*.p2p" + File.pathSeparator + "*.p2b"
						+ File.pathSeparator + "*.txt",
				"*.*");
		add(pathParameter);
		pathParameter.setFileNameHint("ipfilter.dat");
		pathParameter.setDialogTitleKey(
				"ConfigView.section.ipfilter.autoload.file");

		ActionParameterImpl btnLoadNow = new ActionParameterImpl(null,
				"ConfigView.section.ipfilter.autoload.loadnow");
		add(btnLoadNow);
		btnLoadNow.addListener(p -> {
			btnLoadNow.setEnabled(false);
			COConfigurationManager.setParameter(
					ICFG_IP_FILTER_AUTOLOAD_LAST, 0);
			try {
				if (UrlUtils.isURL(pathParameter.getValue())) {
					// Note: We don't have a way to detect when async is done, sot the
					// button disabling sucks
					filter.reload();
				} else {
					filter.reloadSync();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			btnLoadNow.setEnabled(true);
		});

			// IPv6 File - really should just have one 'load' button as it reloads both v4+v6 but hey
		
		// TODO: Check if it allows urls
		FileParameterImpl pathV6Parameter = new FileParameterImpl(
				SCFG_IP_FILTER_V6_AUTOLOAD_FILE,
				"ConfigView.section.ipfilter.autoload.v6.file",
				"*.txt",
				"*.*");
		add(pathV6Parameter);
		pathV6Parameter.setDialogTitleKey(
				"ConfigView.section.ipfilter.autoload.v6.file");

		ActionParameterImpl btnV6LoadNow = new ActionParameterImpl(null,
				"ConfigView.section.ipfilter.autoload.loadnow");
		add(btnV6LoadNow);
		btnV6LoadNow.addListener(p -> {
			btnV6LoadNow.setEnabled(false);
			COConfigurationManager.setParameter(
					ICFG_IP_FILTER_AUTOLOAD_LAST, 0);
			try {
				if (UrlUtils.isURL(pathV6Parameter.getValue())) {
					// Note: We don't have a way to detect when async is done, sot the
					// button disabling sucks
					filter.reload();
				} else {
					filter.reloadSync();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			btnV6LoadNow.setEnabled(true);
		});
		
		
		
		ParameterGroupImpl pgPathAndLoad = new ParameterGroupImpl(null,
				pathParameter, btnLoadNow, pathV6Parameter, btnV6LoadNow );
		add("pgPathAndLoad", pgPathAndLoad, listAutoLoad);
		pgPathAndLoad.setNumberOfColumns(2);

		// reload period

		int initial_reload_period = COConfigurationManager.getIntParameter(
				ICFG_IP_FILTER_AUTOLOAD_DAYS);

		IntParameterImpl reload_period = new IntParameterImpl(
				ICFG_IP_FILTER_AUTOLOAD_DAYS,
				"ConfigView.section.ipfilter.autoload.period", 1, 31);
		add(reload_period, listAutoLoad);

		// reload info

		add(new LabelParameterImpl("ConfigView.section.ipfilter.autoload.info"),
				listAutoLoad);

		BooleanParameterImpl clear_on_reload = new BooleanParameterImpl(
				BCFG_IP_FILTER_CLEAR_ON_RELOAD,
				"ConfigView.section.ipfilter.clear.on.reload");
		add(clear_on_reload, listAutoLoad);

		ParameterGroupImpl pgAutoLoad = new ParameterGroupImpl(
				"ConfigView.section.ipfilter.autoload.group", listAutoLoad);
		add("pgAutoLoad", pgAutoLoad);

		// description scratch file

		BooleanParameterImpl enableDesc = new BooleanParameterImpl(
				BCFG_IP_FILTER_ENABLE_DESCRIPTION_CACHE,
				"ConfigView.section.ipfilter.enable.descriptionCache");
		add(enableDesc, Parameter.MODE_INTERMEDIATE);

		if (paramContextIPEditor != null) {
			add("IPEditor",
					new UIParameterImpl(paramContextIPEditor, null));
		}
	}
}
