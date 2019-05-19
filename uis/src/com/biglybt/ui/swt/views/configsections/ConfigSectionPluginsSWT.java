/*
 * File    : ConfigSectionPlguins.java
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.config.ConfigSectionPlugins;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.pif.UISWTParameterContext;

import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.installer.PluginInstallationListener;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.model.PluginConfigModel;

/**
 * Configuration Section that lists all the plugins and sets up
 * subsections for plugins that used the PluginConfigModel object.
 *
 * Moved from ConfigView
 *
 * @author TuxPaper
 *
 */
public class ConfigSectionPluginsSWT
	extends ConfigSectionPlugins
{
	private final static String HEADER_PREFIX = "ConfigView.pluginlist.column.";

	static class FilterComparator
		implements Comparator<PluginInterface>
	{
		boolean ascending = true;

		static final int FIELD_LOAD = 0;

		static final int FIELD_TYPE = 1;

		static final int FIELD_NAME = 2;

		static final int FIELD_VERSION = 3;

		static final int FIELD_DIRECTORY = 4;

		static final int FIELD_UNLOADABLE = 5;

		int field = FIELD_NAME;

		String sUserPluginDir;

		String sAppPluginDir;

		public FilterComparator() {
			String sep = File.separator;

			sUserPluginDir = FileUtil.getUserFile("plugins").toString();
			if (!sUserPluginDir.endsWith(sep))
				sUserPluginDir += sep;

			sAppPluginDir = FileUtil.getApplicationFile("plugins").toString();
			if (!sAppPluginDir.endsWith(sep))
				sAppPluginDir += sep;
		}

		@Override
		public int compare(PluginInterface if0, PluginInterface if1) {
			int result = 0;

			switch (field) {
				case FIELD_LOAD: {
					boolean b0 = if0.getPluginState().isLoadedAtStartup();
					boolean b1 = if1.getPluginState().isLoadedAtStartup();
					result = (b0 == b1 ? 0 : (b0 ? -1 : 1));

					// Use the plugin ID name to sort by instead.
					if (result == 0) {
						result = if0.getPluginID().compareToIgnoreCase(if1.getPluginID());
					}
					break;
				}

				case FIELD_TYPE:
				case FIELD_DIRECTORY: {
					result = getFieldValue(field, if0).compareToIgnoreCase(
							getFieldValue(field, if1));
					break;
				}

				case FIELD_VERSION: { // XXX Not really right..
					String s0 = if0.getPluginVersion();
					String s1 = if1.getPluginVersion();
					if (s0 == null)
						s0 = "";
					if (s1 == null)
						s1 = "";
					result = s0.compareToIgnoreCase(s1);
					break;
				}

				case FIELD_UNLOADABLE: {
					boolean b0 = if0.getPluginState().isUnloadable();
					boolean b1 = if1.getPluginState().isUnloadable();
					result = (b0 == b1 ? 0 : (b0 ? -1 : 1));
					break;
				}
			}

			if (result == 0)
				result = if0.getPluginName().compareToIgnoreCase(if1.getPluginName());

			if (!ascending)
				result *= -1;

			return result;
		}

		public boolean setField(int newField) {
			if (field == newField)
				ascending = !ascending;
			else
				ascending = true;
			field = newField;
			return ascending;
		}

		public String getFieldValue(int iField, PluginInterface pluginIF) {
			switch (iField) {
				case FIELD_LOAD: {
					return pluginIF.getPluginID();
				}

				case FIELD_DIRECTORY: {
					String sDirName = pluginIF.getPluginDirectoryName();

					if (sDirName.length() > sUserPluginDir.length()
							&& sDirName.substring(0, sUserPluginDir.length()).equals(
									sUserPluginDir)) {
						return sDirName.substring(sUserPluginDir.length());

					} else if (sDirName.length() > sAppPluginDir.length()
							&& sDirName.substring(0, sAppPluginDir.length()).equals(
									sAppPluginDir)) {
						return sDirName.substring(sAppPluginDir.length());
					}
					return sDirName;
				}

				case FIELD_NAME: {
					return pluginIF.getPluginName();
				}

				case FIELD_TYPE: {
					String sDirName = pluginIF.getPluginDirectoryName();
					String sKey;

					if (sDirName.length() > sUserPluginDir.length()
							&& sDirName.substring(0, sUserPluginDir.length()).equals(
									sUserPluginDir)) {
						sKey = "perUser";

					} else if (sDirName.length() > sAppPluginDir.length()
							&& sDirName.substring(0, sAppPluginDir.length()).equals(
									sAppPluginDir)) {
						sKey = "shared";
					} else {
						sKey = "builtIn";
					}

					return MessageText.getString(HEADER_PREFIX + "type." + sKey);
				}

				case FIELD_VERSION: {
					return pluginIF.getPluginVersion();
				}

				case FIELD_UNLOADABLE: {
					return MessageText.getString(
							"Button." + (pluginIF.getPluginState().isUnloadable() ? "yes"
									: "no")).replaceAll("&", "");
				}
			} // switch

			return "";
		}
	}

	public ConfigSectionPluginsSWT() {
		init(new PluginListContext());
	}

	@Override
	public void deleteConfigSection() {
		super.deleteConfigSection();

		ImageLoader imageLoader = ImageLoader.getInstance();
		imageLoader.releaseImage("redled");
		imageLoader.releaseImage("greenled");
	}

	public static class PluginListContext
		implements UISWTParameterContext, ParameterListener
	{
		private final static String[] COLUMN_HEADERS = {
			"loadAtStartup",
			"type",
			"name",
			"version",
			"directory",
			"unloadable"
		};

		private final static int[] COLUMN_SIZES = {
			180,
			70,
			250,
			100,
			100,
			50
		};

		private final static int[] COLUMN_ALIGNS = {
			SWT.CENTER,
			SWT.LEFT,
			SWT.LEFT,
			SWT.RIGHT,
			SWT.LEFT,
			SWT.CENTER
		};

		final FilterComparator comparator = new FilterComparator();

		List<PluginInterface> pluginIFs;

		private Table table;

		private Image imgRedLed;

		private Image imgGreenLed;

		@Override
		public void create(Composite infoGroup) {

			GridLayout gridLayout = new GridLayout();
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			infoGroup.setLayout(gridLayout);
			Layout parentLayout = infoGroup.getParent().getLayout();
			if (parentLayout instanceof GridLayout) {
				GridLayout gridParentLayout = (GridLayout) parentLayout;
				GridData gridData = new GridData(GridData.FILL_BOTH);
				gridData.horizontalSpan = gridParentLayout.numColumns;
				infoGroup.setLayoutData(gridData);
			}


			ImageLoader imageLoader = ImageLoader.getInstance();
			imgRedLed = imageLoader.getImage("redled");
			imgGreenLed = imageLoader.getImage("greenled");

			pluginIFs = rebuildPluginIFs();

			pluginIFs.sort((o1,
					o2) -> (o1.getPluginName().compareToIgnoreCase(o2.getPluginName())));

			table = new Table(infoGroup, SWT.BORDER | SWT.SINGLE | SWT.CHECK
					| SWT.VIRTUAL | SWT.FULL_SELECTION);
			GridData gridData = new GridData(GridData.FILL_BOTH);
			gridData.heightHint = 200;
			gridData.widthHint = 200;
			table.setLayoutData(gridData);
			for (int i = 0; i < COLUMN_HEADERS.length; i++) {
				final TableColumn tc = new TableColumn(table, COLUMN_ALIGNS[i]);
				tc.setWidth(COLUMN_SIZES[i]);
				tc.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						boolean ascending = comparator.setField(table.indexOf(tc));
						try {
							table.setSortColumn(tc);
							table.setSortDirection(ascending ? SWT.UP : SWT.DOWN);
						} catch (NoSuchMethodError ignore) {
							// Ignore Pre 3.0
						}
						pluginIFs.sort(comparator);
						table.clearAll();
					}
				});
				Messages.setLanguageText(tc, HEADER_PREFIX + COLUMN_HEADERS[i]);
			}
			table.setHeaderVisible(true);

			Composite cButtons = new Composite(infoGroup, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 5;
			cButtons.setLayout(layout);
			cButtons.setLayoutData(new GridData());

			final Button btnUnload = new Button(cButtons, SWT.PUSH);
			btnUnload.setLayoutData(new GridData());
			Messages.setLanguageText(btnUnload,
					"ConfigView.pluginlist.unloadSelected");
			btnUnload.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					final int[] items = table.getSelectionIndices();

					new AEThread2("unload") {
						@Override
						public void run() {
							for (int index : items) {
								if (index >= 0 && index < pluginIFs.size()) {
									PluginInterface pluginIF = pluginIFs.get(index);
									if (pluginIF == null) {
										continue;
									}
									if (pluginIF.getPluginState().isOperational()) {
										if (pluginIF.getPluginState().isUnloadable()) {
											try {
												pluginIF.getPluginState().unload();
											} catch (PluginException e1) {
												// TODO Auto-generated catch block
												e1.printStackTrace();
											}
										}
									}

									Utils.execSWTThread(() -> {
										pluginIFs = rebuildPluginIFs();
										table.setItemCount(pluginIFs.size());
										pluginIFs.sort(comparator);
										table.clearAll();
									});
								}
							}
						}
					}.start();
				}
			});
			btnUnload.setEnabled(false);

			final Button btnLoad = new Button(cButtons, SWT.PUSH);
			btnUnload.setLayoutData(new GridData());
			Messages.setLanguageText(btnLoad, "ConfigView.pluginlist.loadSelected");
			btnLoad.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					final int[] items = table.getSelectionIndices();

					new AEThread2("load") {
						@Override
						public void run() {
							for (int index : items) {
								if (index < 0 || index >= pluginIFs.size()) {
									continue;
								}

								PluginInterface pluginIF = pluginIFs.get(index);
								if (pluginIF == null) {
									continue;
								}

								if (pluginIF.getPluginState().isOperational()) {
									continue;
								} // Already loaded.

								// Re-enable disabled plugins, as long as they haven't failed on
								// initialise.
								if (pluginIF.getPluginState().isDisabled()) {
									if (pluginIF.getPluginState().hasFailed()) {
										continue;
									}
									pluginIF.getPluginState().setDisabled(false);
								}

								try {
									pluginIF.getPluginState().reload();
								} catch (PluginException e1) {
									// TODO Auto-generated catch block
									Debug.printStackTrace(e1);
								}

								Utils.execSWTThread(() -> {
									if (table == null || table.isDisposed()) {
										return;
									}
									pluginIFs = rebuildPluginIFs();
									table.setItemCount(pluginIFs.size());
									pluginIFs.sort(comparator);
									table.clearAll();
								});
							}
						}
					}.start();
				}
			});
			btnLoad.setEnabled(false);

			// scan

			final Button btnScan = new Button(cButtons, SWT.PUSH);
			btnScan.setLayoutData(new GridData());
			Messages.setLanguageText(btnScan, "ConfigView.pluginlist.scan");
			btnScan.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					CoreFactory.getSingleton().getPluginManager().refreshPluginList(
							false);
					pluginIFs = rebuildPluginIFs();
					table.setItemCount(pluginIFs.size());
					pluginIFs.sort(comparator);
					table.clearAll();
				}
			});

			// uninstall

			final Button btnUninstall = new Button(cButtons, SWT.PUSH);
			btnUninstall.setLayoutData(new GridData());
			Messages.setLanguageText(btnUninstall,
					"ConfigView.pluginlist.uninstallSelected");
			btnUninstall.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {

					btnUninstall.setEnabled(false);

					final int[] items = table.getSelectionIndices();

					new AEThread2("uninstall") {
						@Override
						public void run() {
							try {

								List<PluginInterface> pis = new ArrayList<>();

								for (int index : items) {
									if (index >= 0 && index < pluginIFs.size()) {
										PluginInterface pluginIF = pluginIFs.get(index);

										pis.add(pluginIF);
									}
								}

								if (pis.size() > 0) {

									PluginInterface[] ps = new PluginInterface[pis.size()];

									pis.toArray(ps);

									try {

										final AESemaphore wait_sem = new AESemaphore("unist:wait");

										ps[0].getPluginManager().getPluginInstaller().uninstall(ps,
												new PluginInstallationListener() {
													@Override
													public void completed() {
														wait_sem.release();
													}

													@Override
													public void cancelled() {
														wait_sem.release();
													}

													@Override
													public void failed(PluginException e) {
														wait_sem.release();
													}
												});

										wait_sem.reserve();

									} catch (Exception e) {

										Debug.printStackTrace(e);
									}
								}
							} finally {

								Utils.execSWTThread(() -> {
									pluginIFs = rebuildPluginIFs();
									table.setItemCount(pluginIFs.size());
									pluginIFs.sort(comparator);
									table.clearAll();
									table.setSelection(new int[0]);
								});
							}
						}
					}.start();
				}
			});
			btnUninstall.setEnabled(false);

			table.addListener(SWT.SetData, new Listener() {
				@Override
				public void handleEvent(Event event) {
					TableItem item = (TableItem) event.item;
					int index = table.indexOf(item);
					PluginInterface pluginIF = pluginIFs.get(index);

					for (int i = 0; i < COLUMN_HEADERS.length; i++) {
						if (i == FilterComparator.FIELD_NAME)
							item.setImage(i, pluginIF.getPluginState().isOperational()
									? imgGreenLed : imgRedLed);

						String sText = comparator.getFieldValue(i, pluginIF);
						if (sText == null)
							sText = "";
						item.setText(i, sText);
					}

					item.setGrayed(pluginIF.getPluginState().isMandatory());
					boolean bEnabled = pluginIF.getPluginState().isLoadedAtStartup();
					Utils.setCheckedInSetData(item, bEnabled);
					item.setData("PluginID", pluginIF.getPluginID());
					Utils.alternateRowBackground(item);
				}
			});

			table.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent e) {
					TableItem[] items = table.getSelection();

					if (items.length == 1) {

						int index = table.indexOf(items[0]);

						PluginInterface pluginIF = pluginIFs.get(index);

						PluginConfigModel[] models = pluginIF.getUIManager().getPluginConfigModels();

						for (PluginConfigModel model : models) {

							if (model.getPluginInterface() == pluginIF) {

								if (model instanceof BasicPluginConfigModel) {

									String id = ((BasicPluginConfigModel) model).getSection();

									UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

									if (uiFunctions != null) {

										uiFunctions.getMDI().showEntryByID(
												MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, id);
									}
								}
							}
						}
					}
				}
			});

			table.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					TableItem item = (TableItem) e.item;
					int index = table.indexOf(item);
					PluginInterface pluginIF = pluginIFs.get(index);
					if (pluginIF == null) {
						return;
					}

					if (e.detail == SWT.CHECK) {

						if (item.getGrayed()) {
							if (!item.getChecked())
								item.setChecked(true);
							return;
						}

						pluginIF.getPluginState().setDisabled(!item.getChecked());
						pluginIF.getPluginState().setLoadedAtStartup(item.getChecked());
					}

					btnUnload.setEnabled(pluginIF.getPluginState().isOperational()
							&& pluginIF.getPluginState().isUnloadable());
					btnLoad.setEnabled(!pluginIF.getPluginState().isOperational()
							&& !pluginIF.getPluginState().hasFailed());
					btnUninstall.setEnabled(!(pluginIF.getPluginState().isBuiltIn()
							|| pluginIF.getPluginState().isMandatory()));
				}
			});

			table.setItemCount(pluginIFs.size());
		}

		private List<PluginInterface> rebuildPluginIFs() {
			List<PluginInterface> pluginIFs = new ArrayList<>(Arrays.asList(
					CoreFactory.getSingleton().getPluginManager().getPlugins()));
			
			Iterator<PluginInterface>	it = pluginIFs.iterator();
				
			while( it.hasNext()){
				
				PluginInterface pi = it.next();
				
				Properties props = pi.getPluginProperties();
				
				String configurable = props.getProperty( "plugin.is.configurable" );
				
				if ( configurable != null && !((String)configurable).equals( "true" )){
					
					it.remove();
					
				}else{
				
					// COConfigurationManager will not add the same listener twice
					COConfigurationManager.addWeakParameterListener(this, false,
							"PluginInfo." + pi.getPluginID() + ".enabled");
				}
			}
			
			return pluginIFs;
		}

		@Override
		public void parameterChanged(String parameterName) {
			if (table != null) {
				Utils.execSWTThread(() -> {
					if (table != null && !table.isDisposed()) {
						table.clearAll();
					}
				});
			}
		}
	}

}
