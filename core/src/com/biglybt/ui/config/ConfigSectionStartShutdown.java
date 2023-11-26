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

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.AEJavaManagement;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

import static com.biglybt.core.config.ConfigKeys.StartupShutdown.*;

public class ConfigSectionStartShutdown
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "startstop";

	public ConfigSectionStartShutdown() {
		super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void build() {

		final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

		// ***** start group

		boolean can_ral = platform.hasCapability(
				PlatformManagerCapabilities.RunAtLogin);

		if (can_ral) {

			List<Parameter> listStartup = new ArrayList<>();

			BooleanParameterImpl start_on_login = new BooleanParameterImpl(
					BCFG_START_ON_LOGIN, "ConfigView.label.start.onlogin");
			add(start_on_login, listStartup);

			try {
				// Update the config with what the platform says
				start_on_login.setValue(platform.getRunAtLogin());

				start_on_login.addListener(p -> {
					try {
						platform.setRunAtLogin(((BooleanParameter) p).getValue());

					} catch (Throwable e) {

						Debug.out(e);
					}
				});

			} catch (Throwable e) {

				start_on_login.setEnabled(false);

				Debug.out(e);
			}

			BooleanParameterImpl start_in_lr_mode = new BooleanParameterImpl(
					BCFG_START_IN_LOW_RESOURCE_MODE, "ConfigView.label.start.inlrm");
			add(start_in_lr_mode, Parameter.MODE_INTERMEDIATE, listStartup);

			BooleanParameterImpl lr_ui = new BooleanParameterImpl(BCFG_LRMS_UI,
					"lrms.deactivate.ui");
			add(lr_ui, Parameter.MODE_INTERMEDIATE, listStartup);
			lr_ui.setIndent(1, false);
			BooleanParameterImpl lr_udp_net = new BooleanParameterImpl(
					BCFG_LRMS_UDP_PEERS, "lrms.udp.peers");
			add(lr_udp_net, Parameter.MODE_INTERMEDIATE, listStartup);
			lr_udp_net.setIndent(1, false);
			BooleanParameterImpl lr_dht_sleep = new BooleanParameterImpl(
					BCFG_LRMS_DHT_SLEEP, "lrms.dht.sleep");
			add(lr_dht_sleep, Parameter.MODE_INTERMEDIATE, listStartup);
			lr_dht_sleep.setIndent(1, false);

			//start_in_lr_mode.setAdditionalActionPerformer( new ChangeSelectionActionPerformer( lr_ui ));
			lr_ui.setEnabled(false); // this must always be selected as it is coming out of the deactivated UI mode that enable the others as well
			start_in_lr_mode.addEnabledOnSelection(lr_udp_net, lr_dht_sleep);

			add("pgStartup",
					new ParameterGroupImpl("ConfigView.label.start", listStartup));
		}

		//// Sleep

		if (platform.hasCapability(
				PlatformManagerCapabilities.PreventComputerSleep)) {
			List<Parameter> listSleep = new ArrayList<>();

			add(new LabelParameterImpl("ConfigView.label.sleep.info"), listSleep);

			add(new BooleanParameterImpl(BCFG_PREVENT_SLEEP_DOWNLOADING,
					"ConfigView.label.sleep.download"), listSleep);

			add(new BooleanParameterImpl(BCFG_PREVENT_SLEEP_FP_SEEDING,
					"ConfigView.label.sleep.fpseed"), listSleep);

			TagManager tm = TagManagerFactory.getTagManager();

			if (tm.isEnabled()) {

				List<Tag> tag_list = tm.getTagType(
						TagType.TT_DOWNLOAD_MANUAL).getTags();

				String[] tags = new String[tag_list.size() + 1];

				tags[0] = "";

				for (int i = 0; i < tag_list.size(); i++) {

					tags[i + 1] = tag_list.get(i).getTagName(true);
				}

				add(new StringListParameterImpl(SCFG_PREVENT_SLEEP_TAG,
						"ConfigView.label.sleep.tag", tags, tags), listSleep);
			}

			add("pgSleep",
					new ParameterGroupImpl("ConfigView.label.sleep", listSleep));
		}

		//// Auto-Pause/Resume

		List<Parameter> listPR = new ArrayList<>();

		add(new BooleanParameterImpl(BCFG_PAUSE_DOWNLOADS_ON_EXIT,
				"ConfigView.label.pause.downloads.on.exit"),
				Parameter.MODE_INTERMEDIATE, listPR);

		add(new BooleanParameterImpl(BCFG_RESUME_DOWNLOADS_ON_START,
				"ConfigView.label.resume.downloads.on.start"),
				Parameter.MODE_INTERMEDIATE, listPR);

		add("pgPR", new ParameterGroupImpl("ConfigView.label.pauseresume", listPR));

		///// Shutdown

		List<Parameter> listStop = new ArrayList<>();

		// done downloading

		addDoneDownloadingOption(listStop, true);

		// done seeding

		addDoneSeedingOption(listStop, true);

		// reset on trigger

		BooleanParameterImpl resetOnTrigger = new BooleanParameterImpl(
				BCFG_STOP_TRIGGERS_AUTO_RESET, "");
		add(resetOnTrigger, Parameter.MODE_INTERMEDIATE, listStop);
		resetOnTrigger.setLabelText(
				MessageText.getString("ConfigView.label.stop.autoreset", new String[] {
					MessageText.getString("ConfigView.label.stop.Nothing")
				}));

		// prompt to allow abort

		BooleanParameterImpl enablePrompt = new BooleanParameterImpl(
				BCFG_PROMPT_TO_ABORT_SHUTDOWN, "ConfigView.label.prompt.abort");
		enablePrompt.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(enablePrompt, Parameter.MODE_INTERMEDIATE, listStop);

		IntParameterImpl paramTerminateAfter = new IntParameterImpl(
				ICFG_STOP_FORCE_TERMINATE_AFTER, "ConfigView.label.stop.term.after", 2, 100000);
		paramTerminateAfter.setSuffixLabelKey("ConfigView.text.minutes");
		add(paramTerminateAfter, Parameter.MODE_INTERMEDIATE, listStop);
		
		add("pgStop", new ParameterGroupImpl("ConfigView.label.stop", listStop));
		
		
		//// Restart

		IntParameterImpl paramRestartWhenIdle = new IntParameterImpl(
				ICFG_AUTO_RESTART_WHEN_IDLE, "ConfigView.label.restart.auto", 0, 100000);
		paramRestartWhenIdle.setSuffixLabelKey("ConfigView.text.minutes");
		add(paramRestartWhenIdle);
		paramRestartWhenIdle.setMinimumRequiredUserMode(
				Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl paramRestartWhenIdlePrompt = new BooleanParameterImpl(
				BCFG_AUTO_RESTART_WHEN_IDLE_PROMPT, "label.prompt.when.restart" );
		paramRestartWhenIdlePrompt.setMinimumRequiredUserMode(Parameter.MODE_INTERMEDIATE);
		paramRestartWhenIdlePrompt.setAllowedUiTypes(UIInstance.UIT_SWT);
		paramRestartWhenIdlePrompt.setIndent(1, true);
		add(paramRestartWhenIdlePrompt);

		paramRestartWhenIdle.addAndFireListener((n)->{
			paramRestartWhenIdlePrompt.setEnabled(paramRestartWhenIdle.getValue() > 0 );
		});
		
		add("pgRestart",
				new ParameterGroupImpl("label.restart", paramRestartWhenIdle, paramRestartWhenIdlePrompt ));


			// JVM
		
		List<Parameter> listJVM = new ArrayList<>();

		if (platform.hasCapability(
				PlatformManagerCapabilities.AccessExplicitVMOptions)) {

			// wiki link

			add(new HyperlinkParameterImpl("ConfigView.label.please.visit.here",
					Wiki.JAVA_VM_MEMORY_USAGE),
					Parameter.MODE_INTERMEDIATE, listJVM);

			// info

			add(new LabelParameterImpl("jvm.info"), Parameter.MODE_INTERMEDIATE,
					listJVM);

			try {
				File option_file = platform.getVMOptionFile();

				buildOptions(platform, listJVM);

				// show option file

				ActionParameterImpl show_folder_button = new ActionParameterImpl(
						"jvm.show.file", "!" + option_file.getAbsolutePath() + "!");
				add(show_folder_button, Parameter.MODE_INTERMEDIATE, listJVM);
				show_folder_button.setAllowedUiTypes(UIInstance.UIT_SWT);
				show_folder_button.setStyle(ActionParameter.STYLE_LINK);
				show_folder_button.addListener(param -> UIFunctionsManager.getUIFunctions().showInExplorer(option_file));

				ActionParameterImpl reset_button = new ActionParameterImpl("jvm.reset",
						"Button.reset");
				add(reset_button, Parameter.MODE_INTERMEDIATE, listJVM);

				reset_button.addListener(param -> {
					try {
						platform.setExplicitVMOptions(new String[0]);

						requestRebuild();

					} catch (Throwable e) {

						Debug.out(e);
					}
				});

			} catch (Throwable e) {

				Debug.out(e);

				LabelParameterImpl paramErr = new LabelParameterImpl("");
				add(paramErr, Parameter.MODE_INTERMEDIATE, listJVM);
				paramErr.setLabelText(MessageText.getString("jvm.error", new String[] {
					Debug.getNestedExceptionMessage(e)
				}));
			}
		}
		
		ActionParameterImpl history_button = new ActionParameterImpl("jvm.mem.history",
				"label.view");
		history_button.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(history_button, Parameter.MODE_INTERMEDIATE, listJVM);

		history_button.addListener(param -> {
			try{

				UIFunctions uif = UIFunctionsManager.getUIFunctions();
				
				if ( uif == null ){
					
					Debug.out( "No UI Functions!" );
				}else{
					
					List<String>	lines = AEJavaManagement.getMemoryHistory();
					
					String content = "";
					
					for ( String line: lines ){
						
						content += line + "\n";
					}
					
					uif.showText( MessageText.getString( "GeneralView.section.info" ), content );
				}
			
			} catch (Throwable e) {

				Debug.out(e);
			}
		});

		add("pgJVM", new ParameterGroupImpl("ConfigView.label.jvm", listJVM));
	}

	private void buildOptions(final PlatformManager platform,
			List<Parameter> list)

			throws PlatformManagerException {
		String[] options = platform.getExplicitVMOptions();

		{
			// max mem

			long max_mem = AEJavaManagement.getJVMLongOption(options, "-Xmx");

			final int MIN_MAX_JVM = 32 * 1024 * 1024;

			StringParameterImpl max_vm = new StringParameterImpl("jvm.max.mem", "");
			add(max_vm, Parameter.MODE_INTERMEDIATE, list);
			max_vm.setWidthInCharacters(10);
			max_vm.setLabelText(MessageText.getString("jvm.max.mem", new String[] {
				encodeDisplayLong(MIN_MAX_JVM)
			}));

			long max_heap_mb = AEJavaManagement.getMaxHeapMB();
			if (max_heap_mb > 0) {
				max_vm.setSuffixLabelText(MessageText.getString("jvm.max.mem.current",
						new String[] {
							DisplayFormatters.formatByteCountToKiBEtc(
									max_heap_mb * 1024 * 1024, true)
						}));
			}

			max_vm.setValue(max_mem == -1 ? "" : encodeDisplayLong(max_mem));

			max_vm.addListener(param -> {

				String val = max_vm.getValue();

				try {
					long cur_max_mem = decodeDisplayLong(val);

					String[] cur_options = platform.getExplicitVMOptions();

					if ( cur_max_mem < MIN_MAX_JVM ){

						if ( cur_max_mem <= 0 ){
							
							cur_options = AEJavaManagement.removeJVMOption( cur_options, "-Xmx" );
							
						}else{
						
							throw (new Exception("Min=" + encodeDisplayLong(MIN_MAX_JVM)));
						}
					}else{

						cur_options = AEJavaManagement.setJVMLongOption(cur_options, "-Xmx", cur_max_mem);
	
						long min_mem = AEJavaManagement.getJVMLongOption(cur_options, "-Xms");
	
						if ( min_mem > cur_max_mem) {
	
							cur_options = AEJavaManagement.setJVMLongOption(cur_options, "-Xms", cur_max_mem);
						}
					}
					
					platform.setExplicitVMOptions(cur_options);

					requestRebuild();

				} catch (Throwable e) {

					String param_name = MessageText.getString("jvm.max.mem");

					int pos = param_name.indexOf('[');

					if (pos != -1) {

						param_name = param_name.substring(0, pos).trim();
					}

					UIFunctions uif = UIFunctionsManager.getUIFunctions();
					if (uif == null) {
						Debug.out(param_name, e);
					} else {
						UIFunctionsUserPrompter userPrompter = uif.getUserPrompter(
								MessageText.getString("ConfigView.section.invalid.value.title"),
								MessageText.getString(
										"ConfigView.section.invalid.value",
										new String[]{ val, param_name, Debug.getNestedExceptionMessage(e)}),
								new String[] {
									MessageText.getString("Button.ok")
						}, 0);
						userPrompter.setIconResource(UIFunctionsUserPrompter.ICON_ERROR);
						userPrompter.open(null);
					}
				}
			});
		}

		{
			// min mem

			final int MIN_MIN_JVM = 8 * 1024 * 1024;

			long min_mem = AEJavaManagement.getJVMLongOption(options, "-Xms");

			final StringParameterImpl min_vm = new StringParameterImpl("jvm.min.mem",
					"");
			add(min_vm, Parameter.MODE_INTERMEDIATE, list);
			min_vm.setWidthInCharacters(10);
			min_vm.setLabelText(MessageText.getString("jvm.min.mem", new String[] {
				encodeDisplayLong(MIN_MIN_JVM)
			}));

			min_vm.setValue(min_mem == -1 ? "" : encodeDisplayLong(min_mem));

			min_vm.addListener(param -> {

				String val = min_vm.getValue();
				try {
					long cur_min_mem = decodeDisplayLong(val);

					String[] cur_options = platform.getExplicitVMOptions();

					if ( cur_min_mem < MIN_MIN_JVM ){

						if ( cur_min_mem <= 0 ){
							
							cur_options = AEJavaManagement.removeJVMOption( cur_options, "-Xms" );
							
						}else{
						
							throw (new Exception("Min=" + encodeDisplayLong(MIN_MIN_JVM)));
							
						}
					}else{

						cur_options = AEJavaManagement.setJVMLongOption(cur_options, "-Xms", cur_min_mem);

						long max_mem = AEJavaManagement.getJVMLongOption(cur_options, "-Xmx");
	
						if (max_mem == -1 || max_mem < cur_min_mem) {
	
							cur_options = AEJavaManagement.setJVMLongOption(cur_options, "-Xmx", cur_min_mem );
						}
					}
					
					platform.setExplicitVMOptions(cur_options);

					requestRebuild();

				} catch (Throwable e) {

					String param_name = MessageText.getString("jvm.min.mem");

					int pos = param_name.indexOf('[');

					if (pos != -1) {

						param_name = param_name.substring(0, pos).trim();
					}

					UIFunctions uif = UIFunctionsManager.getUIFunctions();
					if (uif == null) {
						Debug.out(param_name, e);
					} else {
						UIFunctionsUserPrompter userPrompter = uif.getUserPrompter(
								MessageText.getString("ConfigView.section.invalid.value.title"),
								MessageText.getString(
									"ConfigView.section.invalid.value",
									new String[]{ val, param_name, Debug.getNestedExceptionMessage(e)}),
								new String[] {
									MessageText.getString("Button.ok")
						}, 0);
						userPrompter.setIconResource(UIFunctionsUserPrompter.ICON_ERROR);
						userPrompter.open(null);
					}
				}
			});
		}

		{
			// max DIRECT mem
			final int MIN_DIRECT_JVM = 32 * 1024 * 1024;

			final String OPTION_KEY = "-XX:MaxDirectMemorySize=";

			long max_direct = AEJavaManagement.getJVMLongOption(options, OPTION_KEY);

			StringParameterImpl max_direct_vm = new StringParameterImpl(
					"jvm.max.direct.mem", "");
			add(max_direct_vm, Parameter.MODE_INTERMEDIATE, list);
			max_direct_vm.setWidthInCharacters(10);
			max_direct_vm.setLabelText(
					MessageText.getString("jvm.max.direct.mem", new String[] {
						encodeDisplayLong(MIN_DIRECT_JVM)
					}));
			max_direct_vm.setSuffixLabelKey("jvm.max.direct.mem.info");

			max_direct_vm.setValue(
					max_direct == -1 ? "" : encodeDisplayLong(max_direct));

			max_direct_vm.addListener(param -> {
				String val = max_direct_vm.getValue();

				try {
					long cur_max_direct = decodeDisplayLong(val);

					String[] cur_options = platform.getExplicitVMOptions();

					if ( cur_max_direct < MIN_DIRECT_JVM ){

						if ( cur_max_direct <= 0 ){
							
							cur_options = AEJavaManagement.removeJVMOption( cur_options, OPTION_KEY );
							
						}else{
						
							throw (new Exception("Min=" + encodeDisplayLong(MIN_DIRECT_JVM)));
						}
					}else{

						cur_options = AEJavaManagement.setJVMLongOption(cur_options, OPTION_KEY, cur_max_direct);
					}

					platform.setExplicitVMOptions(cur_options);

					requestRebuild();

				} catch (Throwable e) {

					String param_name = MessageText.getString("jvm.max.direct.mem");

					int pos = param_name.indexOf('[');

					if (pos != -1) {

						param_name = param_name.substring(0, pos).trim();
					}

					UIFunctions uif = UIFunctionsManager.getUIFunctions();
					if (uif == null) {
						Debug.out(param_name, e);
					} else {
						UIFunctionsUserPrompter userPrompter = uif.getUserPrompter(
								MessageText.getString("ConfigView.section.invalid.value.title"),
								MessageText.getString(
									"ConfigView.section.invalid.value",
									new String[]{ val, param_name, Debug.getNestedExceptionMessage(e)}),
								new String[] {
									MessageText.getString("Button.ok")
						}, 0);
						userPrompter.setIconResource(UIFunctionsUserPrompter.ICON_ERROR);
						userPrompter.open(null);
					}
				}
			});
		}

		// all options

		add(new LabelParameterImpl("jvm.options.summary"),
				Parameter.MODE_INTERMEDIATE, list);

		if (options.length == 0) {
			LabelParameterImpl paramSummaryValue = new LabelParameterImpl(
					"label.none");
			add(paramSummaryValue, Parameter.MODE_INTERMEDIATE, list);
			paramSummaryValue.setIndent(1, false);
		} else {
			for (String option : options) {
				LabelParameterImpl paramSummaryValue = new LabelParameterImpl(
						"!" + option + "!");
				add(paramSummaryValue, Parameter.MODE_INTERMEDIATE, list);
				paramSummaryValue.setIndent(1, false);
			}
		}

	}

	private static String encodeDisplayLong(long val) {
		if (val < 1024) {

			return (String.valueOf(val));
		}

		val = val / 1024;

		if (val < 1024) {

			return (val + " KB");
		}

		val = val / 1024;

		if (val < 1024) {

			return (val + " MB");
		}

		if ( val %1024 == 0 ){
			
			val = val / 1024;
	
			return (val + " GB");
			
		}else{
			
			return (val + " MB");
		}
	}

	private static long 
	decodeDisplayLong(
		String val )

		throws Exception 
	{
		val = val.trim();
		
		if ( val.isEmpty()){
			
			return( 0 );
		}
		
		char[] chars = val.toCharArray();

		String digits = "";
		String units = "";

		for (char c : chars) {

			if (Character.isDigit(c)) {

				if (units.length() > 0) {

					throw (new Exception("Invalid unit"));
				}

				digits += c;

			} else {

				if (digits.length() == 0) {

					throw (new Exception("Missing digits"));

				} else if (units.length() == 0 && Character.isWhitespace(c)) {

				} else {

					units += c;
				}
			}
		}

		long value = Long.parseLong(digits);

		if (units.length() == 0) {

			units = "m";
		}

		if (units.length() > 0) {

			char c = Character.toLowerCase(units.charAt(0));

			if (c == 'k') {

				value = value * 1024;

			} else if (c == 'm') {

				value = value * 1024 * 1024;

			} else if (c == 'g') {

				value = value * 1024 * 1024 * 1024;

			} else {

				throw (new Exception("Invalid size unit '" + units + "'"));
			}
		}

		return (value);
	}

	public static String[][] getActionDetails() {
		final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

		int shutdown_types = platform.getShutdownTypes();

		List<String> l_action_values = new ArrayList<>();
		List<String> l_action_descs = new ArrayList<>();

		l_action_values.add("Nothing");
		l_action_values.add("QuitVuze");

		if ((shutdown_types & PlatformManager.SD_SLEEP) != 0) {

			l_action_values.add("Sleep");
		}
		if ((shutdown_types & PlatformManager.SD_HIBERNATE) != 0) {

			l_action_values.add("Hibernate");
		}
		if ((shutdown_types & PlatformManager.SD_SHUTDOWN) != 0) {

			l_action_values.add("Shutdown");
		}

		l_action_values.add("RunScript");
		l_action_values.add("RunScriptAndClose");

		String[] action_values = l_action_values.toArray(
				new String[l_action_values.size()]);

		for (String s : action_values) {

			l_action_descs.add(MessageText.getString("ConfigView.label.stop." + s));
		}

		String[] action_descs = l_action_descs.toArray(
				new String[l_action_descs.size()]);

		return (new String[][] {
			action_descs,
			action_values
		});
	}

	public void addDoneDownloadingOption(List<Parameter> listStop,
			boolean include_script_setting) {
		String[][] action_details = getActionDetails();

		StringListParameterImpl dc = new StringListParameterImpl(
				SCFG_ON_DOWNLOADING_COMPLETE_DO, "ConfigView.label.stop.downcomp",
				action_details[1], action_details[0]);
		add(dc, Parameter.MODE_INTERMEDIATE, listStop);

		if (include_script_setting) {

			FileParameterImpl dc_script = new FileParameterImpl(
					SCFG_ON_DOWNLOADING_COMPLETE_SCRIPT, "label.script.to.run");
			add(dc_script, Parameter.MODE_INTERMEDIATE, listStop);
			dc_script.setIndent(1, true);

			boolean is_script = dc.getValue().startsWith("RunScript");

			dc_script.setEnabled(is_script);

			dc.addListener(
					p -> dc_script.setEnabled(dc.getValue().startsWith("RunScript")));
		}
	}

	private void addDoneSeedingOption(List<Parameter> listStop,
			boolean include_script_setting) {
		String[][] action_details = getActionDetails();

		StringListParameterImpl sc = new StringListParameterImpl(
				SCFG_ON_SEEDING_COMPLETE_DO, "ConfigView.label.stop.seedcomp",
				action_details[1], action_details[0]);
		add(sc, Parameter.MODE_INTERMEDIATE, listStop);

		if (include_script_setting) {

			FileParameterImpl sc_script = new FileParameterImpl(
					SCFG_ON_SEEDING_COMPLETE_SCRIPT, "label.script.to.run");
			add(sc_script, Parameter.MODE_INTERMEDIATE, listStop);
			sc_script.setIndent(1, true);

			boolean is_script = sc.getValue().startsWith("RunScript");

			sc_script.setEnabled(is_script);

			sc.addListener(
					p -> sc_script.setEnabled(sc.getValue().startsWith("RunScript")));
		}
	}
}
