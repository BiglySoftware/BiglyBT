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

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntListParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;
import com.biglybt.pifimpl.local.ui.config.LabelParameterImpl;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

import static com.biglybt.core.config.ConfigKeys.File.*;

public class ConfigSectionFilePerformance
		extends ConfigSectionImpl {
	public static final String SECTION_ID = "file.perf";

	public ConfigSectionFilePerformance() {
		super(SECTION_ID, ConfigSection.SECTION_FILES);
	}

	@Override
	public void build() {
		add(new LabelParameterImpl("ConfigView.section.file.perf.explain"));

			// diskmanager.hashchecking.strategy
		
		String[]	strategyLabels = new String[3];
		
		for ( int i=0;i< strategyLabels.length;i++){
			strategyLabels[i] = MessageText.getString( "ConfigView.section.file.hashchecking.strategy." + i );
		}
		
		add(new IntListParameterImpl(
				ICFG_DISKMANAGER_HASHCHECKING_STRATEGY,
				"ConfigView.section.file.hashchecking.strategy",
				new int[]{0,1,2}, 
				strategyLabels));
		
		/* replaced by strategy
		add(new BooleanParameterImpl(BCFG_DISKMANAGER_FRIENDLY_HASHCHECKING,
				"ConfigView.section.file.friendly.hashchecking"));
		*/
		
			// diskmanager.hashchecking.smallestfirst
		add(new BooleanParameterImpl(
				BCFG_DISKMANAGER_HASHCHECKING_SMALLESTFIRST,
				"ConfigView.section.file.hashchecking.smallestfirst"));

			// diskmanager.hashchecking.maxactive
		
		IntParameterImpl recheck_max_active = new IntParameterImpl(
				BCFG_DISKMANAGER_HASHCHECKING_MAX_ACTIVE, "ConfigView.section.file.hashchecking.maxactive");
		add(recheck_max_active, Parameter.MODE_INTERMEDIATE);
		recheck_max_active.setMinValue(0);
		
		BooleanParameterImpl one_per_fs = 
			add(new BooleanParameterImpl(
				BCFG_DISKMANAGER_ONE_OP_PER_FS,
				"ConfigView.section.file.one.op.per.fs"));	
		
		BooleanParameterImpl one_per_fs_conc_read = 
				add(new BooleanParameterImpl(
					BCFG_DISKMANAGER_ONE_OP_PER_FS_CONC_READ,
					"ConfigView.section.file.one.op.per.fs.conc.read"));	
		one_per_fs_conc_read.setIndent( 1, true );
		
		one_per_fs.addEnabledOnSelection(one_per_fs_conc_read);
		
		// diskmanager.perf.cache.enable

		BooleanParameterImpl disk_cache = new BooleanParameterImpl(
				BCFG_DISKMANAGER_PERF_CACHE_ENABLE,
				"ConfigView.section.file.perf.cache.enable");
		add(disk_cache);

		// diskmanager.perf.cache.size

		long kinb = DisplayFormatters.getKinB();

		long max_mem_bytes = Runtime.getRuntime().maxMemory();
		long mb_1 = 1 * kinb * kinb;
		long mb_32 = 32 * mb_1;

		IntParameterImpl cache_size = new IntParameterImpl(
				ICFG_DISKMANAGER_PERF_CACHE_SIZE, "", 1,
				COConfigurationManager.CONFIG_CACHE_SIZE_MAX_MB);
		add(cache_size);
		// XXX Changed "DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB)"
		//      to getUnitBase10 for the release, since the # is  in MB.
		cache_size.setLabelText(MessageText.getString(
				"ConfigView.section.file.perf.cache.size", new String[]{
						DisplayFormatters.getUnitBase10(DisplayFormatters.UNIT_MB)
				}));
		cache_size.setSuffixLabelText(MessageText.getString(
				"ConfigView.section.file.perf.cache.size.explain", new String[]{
						DisplayFormatters.formatByteCountToKiBEtc(mb_32),
						DisplayFormatters.formatByteCountToKiBEtc(max_mem_bytes),
						Constants.URL_WIKI
				}));

		// don't cache smaller than

		IntParameterImpl cache_not_smaller_than = new IntParameterImpl(
				ICFG_DISKMANAGER_PERF_CACHE_NOTSMALLERTHAN, "");
		add(cache_not_smaller_than, Parameter.MODE_INTERMEDIATE);
		cache_not_smaller_than.setMinValue(0);
		// XXX Changed "DisplayFormatters.getUnit(DisplayFormatters.UNIT_KB)" to
		//     getUnitBase10 for the release, since the # is stored in KB.
		cache_not_smaller_than.setLabelText(MessageText.getString(
				"ConfigView.section.file.perf.cache.notsmallerthan", new String[]{
						DisplayFormatters.getUnitBase10(DisplayFormatters.UNIT_KB)
				}));

		// diskmanager.perf.cache.enable.read

		BooleanParameterImpl disk_cache_read = new BooleanParameterImpl(
				BCFG_DISKMANAGER_PERF_CACHE_ENABLE_READ,
				"ConfigView.section.file.perf.cache.enable.read");
		add(disk_cache_read, Parameter.MODE_INTERMEDIATE);

		// diskmanager.perf.cache.enable.write

		BooleanParameterImpl disk_cache_write = new BooleanParameterImpl(
				BCFG_DISKMANAGER_PERF_CACHE_ENABLE_WRITE,
				"ConfigView.section.file.perf.cache.enable.write");
		add(disk_cache_write, Parameter.MODE_INTERMEDIATE);

		// diskmanager.perf.cache.flushpieces

		BooleanParameterImpl disk_cache_flush = new BooleanParameterImpl(
				BCFG_DISKMANAGER_PERF_CACHE_FLUSHPIECES,
				"ConfigView.section.file.perf.cache.flushpieces");
		add(disk_cache_flush, Parameter.MODE_INTERMEDIATE);

		// diskmanager.perf.cache.trace

		BooleanParameterImpl disk_cache_trace = new BooleanParameterImpl(
				BCFG_DISKMANAGER_PERF_CACHE_TRACE,
				"ConfigView.section.file.perf.cache.trace");
		add(disk_cache_trace, Parameter.MODE_INTERMEDIATE);

		disk_cache.addEnabledOnSelection(cache_not_smaller_than, disk_cache_trace,
				disk_cache_read, disk_cache_write, disk_cache_flush, disk_cache_trace);

		// Max Open Files

		IntParameterImpl paramMaxOpenFiles = new IntParameterImpl(
				ICFG_FILE_MAX_OPEN, "ConfigView.section.file.max_open_files");
		add(paramMaxOpenFiles, Parameter.MODE_ADVANCED);
		paramMaxOpenFiles.setSuffixLabelKey(
				"ConfigView.section.file.max_open_files.explain");

		// write mb limit

		IntParameterImpl write_block_limit = new IntParameterImpl(
				ICFG_DISKMANAGER_PERF_WRITE_MAXMB, "");
		add(write_block_limit, Parameter.MODE_ADVANCED);
		write_block_limit.setLabelText(MessageText.getString(
				"ConfigView.section.file.writemblimit", new String[]{
						DisplayFormatters.getUnitBase10(DisplayFormatters.UNIT_MB)
				}));
		write_block_limit.setSuffixLabelKey(
				"ConfigView.section.file.writemblimit.explain");

		// read mb limit

		IntParameterImpl check_piece_limit = new IntParameterImpl(
				ICFG_DISKMANAGER_PERF_READ_MAXMB, "");
		add(check_piece_limit, Parameter.MODE_ADVANCED);
		check_piece_limit.setLabelText(MessageText.getString(
				"ConfigView.section.file.readmblimit", new String[]{
						DisplayFormatters.getUnitBase10(DisplayFormatters.UNIT_MB)
				}));
		check_piece_limit.setSuffixLabelKey(
				"ConfigView.section.file.readmblimit.explain");

		disk_cache.addEnabledOnSelection(cache_size);
	}
}
