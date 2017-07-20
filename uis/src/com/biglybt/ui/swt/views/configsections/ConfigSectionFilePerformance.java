/*
 * File    : ConfigSection*.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.configsections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;

public class ConfigSectionFilePerformance implements UISWTConfigSection {
  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_FILES;
  }

  /* Name of section will be pulled from
   * ConfigView.section.<i>configSectionGetName()</i>
   */
	@Override
	public String configSectionGetName() {
		return "file.perf";
	}

  @Override
  public void configSectionSave() {
  }

  @Override
  public void configSectionDelete() {
  }

	@Override
	public int maxUserMode() {
		return 2;
	}



  @Override
  public Composite configSectionCreate(final Composite parent) {
    GridData gridData;
    GridLayout layout;
    Label label;
    int userMode = COConfigurationManager.getIntParameter("User Mode");

    Composite cSection = new Composite(parent, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 3;
    cSection.setLayout(layout);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(cSection, gridData);

    label = new Label(cSection, SWT.WRAP);
    Messages.setLanguageText(label, "ConfigView.section.file.perf.explain");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    Utils.setLayoutData(label,  gridData );

    // diskmanager.friendly.hashchecking
    final BooleanParameter friendly_hashchecking = new BooleanParameter(cSection, "diskmanager.friendly.hashchecking", "ConfigView.section.file.friendly.hashchecking");
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    friendly_hashchecking.setLayoutData(gridData);

    // diskmanager.friendly.hashchecking
    final BooleanParameter check_smallest = new BooleanParameter(cSection, "diskmanager.hashchecking.smallestfirst", "ConfigView.section.file.hashchecking.smallestfirst");
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    check_smallest.setLayoutData(gridData);






    // diskmanager.perf.cache.enable

    final BooleanParameter disk_cache = new BooleanParameter(cSection, "diskmanager.perf.cache.enable", "ConfigView.section.file.perf.cache.enable");
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    disk_cache.setLayoutData(gridData);

   	// diskmanager.perf.cache.size

    long max_mem_bytes 	= Runtime.getRuntime().maxMemory();
    long mb_1			= 1*1024*1024;
    long mb_32			= 32*mb_1;

    Label cache_size_label = new Label(cSection, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    cache_size_label.setLayoutData(gridData);
    // XXX Changed "DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB)"
    //      to getUnitBase10 for the release, since the # is  in MB.
    Messages.setLanguageText(cache_size_label,
				"ConfigView.section.file.perf.cache.size",
				new String[] {
    		DisplayFormatters.getUnitBase10(DisplayFormatters.UNIT_MB) });
    IntParameter cache_size = new IntParameter(cSection,
				"diskmanager.perf.cache.size", 1,
				COConfigurationManager.CONFIG_CACHE_SIZE_MAX_MB);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    cache_size.setLayoutData( gridData );


    Label cache_explain_label = new Label(cSection, SWT.WRAP);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
    gridData.widthHint = 300;
    Utils.setLayoutData(cache_explain_label, gridData);
    Messages.setLanguageText(
    		cache_explain_label,
			"ConfigView.section.file.perf.cache.size.explain",
			new String[]{
    			DisplayFormatters.formatByteCountToKiBEtc(mb_32),
    			DisplayFormatters.formatByteCountToKiBEtc(max_mem_bytes),
				Constants.AZUREUS_WIKI
			});

    if(userMode > 0) {

    // don't cache smaller than

    Label cnst_label = new Label(cSection, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    Utils.setLayoutData(cnst_label, gridData);
    // XXX Changed "DisplayFormatters.getUnit(DisplayFormatters.UNIT_KB)" to
    //     getUnitBase10 for the release, since the # is stored in KB.
    Messages.setLanguageText(cnst_label,
    		"ConfigView.section.file.perf.cache.notsmallerthan",
    		new String[] { DisplayFormatters.getUnitBase10(DisplayFormatters.UNIT_KB) });
    IntParameter cache_not_smaller_than= new IntParameter(cSection, "diskmanager.perf.cache.notsmallerthan" );
    cache_not_smaller_than.setMinimumValue(0);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    cache_not_smaller_than.setLayoutData( gridData );


    // diskmanager.perf.cache.enable.read

    final BooleanParameter disk_cache_read = new BooleanParameter(cSection, "diskmanager.perf.cache.enable.read", "ConfigView.section.file.perf.cache.enable.read");
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    disk_cache_read.setLayoutData(gridData);

    // diskmanager.perf.cache.enable.write

    final BooleanParameter disk_cache_write = new BooleanParameter(cSection, "diskmanager.perf.cache.enable.write", "ConfigView.section.file.perf.cache.enable.write");
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    disk_cache_write.setLayoutData(gridData);

    // diskmanager.perf.cache.flushpieces

    final BooleanParameter disk_cache_flush = new BooleanParameter(cSection, "diskmanager.perf.cache.flushpieces", "ConfigView.section.file.perf.cache.flushpieces");
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    disk_cache_flush.setLayoutData(gridData);

     // diskmanager.perf.cache.trace

    final BooleanParameter disk_cache_trace = new BooleanParameter(cSection, "diskmanager.perf.cache.trace", "ConfigView.section.file.perf.cache.trace");
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    disk_cache_trace.setLayoutData(gridData);

    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( new Control[]{ cnst_label }));
    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( cache_not_smaller_than.getControls() ));
    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( disk_cache_trace.getControls() ));
    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( disk_cache_read.getControls() ));
    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( disk_cache_write.getControls() ));
    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( disk_cache_flush.getControls() ));
    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( disk_cache_trace.getControls() ));

    if(userMode > 1) {

    // Max Open Files

    label = new Label(cSection, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    label.setLayoutData(gridData);
    Messages.setLanguageText(label, "ConfigView.section.file.max_open_files");
    IntParameter file_max_open = new IntParameter(cSection, "File Max Open");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    file_max_open.setLayoutData( gridData );
    label = new Label(cSection, SWT.WRAP);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
    gridData.widthHint = 300;
    Utils.setLayoutData(label, gridData);
    Messages.setLanguageText(label, "ConfigView.section.file.max_open_files.explain");

    	// write mb limit

    label = new Label(cSection, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    Utils.setLayoutData(label, gridData);
    String label_text =
    	MessageText.getString(
    		"ConfigView.section.file.writemblimit",
    		new String[] { DisplayFormatters.getUnitBase10(DisplayFormatters.UNIT_MB) });
    label.setText(label_text);
    IntParameter write_block_limit = new IntParameter(cSection, "diskmanager.perf.write.maxmb" );
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    write_block_limit.setLayoutData( gridData );
    label = new Label(cSection, SWT.WRAP);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
    gridData.widthHint = 300;
    Utils.setLayoutData(label, gridData);
    Messages.setLanguageText(label, "ConfigView.section.file.writemblimit.explain");

    	// read mb limit

    label = new Label(cSection, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    Utils.setLayoutData(label, gridData);
    label_text =
    	MessageText.getString(
    		"ConfigView.section.file.readmblimit",
    		new String[] { DisplayFormatters.getUnitBase10(DisplayFormatters.UNIT_MB) });
    label.setText(label_text);
    IntParameter check_piece_limit = new IntParameter(cSection, "diskmanager.perf.read.maxmb" );
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    check_piece_limit.setLayoutData( gridData );
    label = new Label(cSection, SWT.WRAP);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
    gridData.widthHint = 300;
    Utils.setLayoutData(label, gridData);
    Messages.setLanguageText(label, "ConfigView.section.file.readmblimit.explain");

    }
    }

    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( cache_size.getControls() ));
    disk_cache.setAdditionalActionPerformer(
    		new ChangeSelectionActionPerformer( new Control[]{ cache_size_label, cache_explain_label }));

    return cSection;
  }
}
