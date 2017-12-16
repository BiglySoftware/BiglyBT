/*
 * Created on 04-Jun-2006
 * Created by Allan Crooks
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.ui.swt.mainwindow;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.shells.SpeedScaleShell;

public class SelectableSpeedMenu {

	private final static int[] increases = {
		5,
		10,
		35, // totals 50
		50, // 100
		50,
		50,
		100
	};

	public static void generateMenuItems(final Menu parent,
	                                     final Core core, final GlobalManager globalManager, final boolean up_menu)
	{
		final int kInB = 1024;	// can't currently do this properly as global limits stored as K not B :(

        final MenuItem[] oldItems = parent.getItems();
        for(int i = 0; i < oldItems.length; i++)
        {
            oldItems[i].dispose();
        }

        final String configKey =
        	up_menu?
        		TransferSpeedValidator.getActiveUploadParameter(globalManager):
        		"Max Download Speed KBs";

        final int speedPartitions = 12;

        int maxBandwidth = COConfigurationManager.getIntParameter(configKey);
        final boolean unlim = (maxBandwidth == 0);
        maxBandwidth = adjustMaxBandWidth(maxBandwidth, globalManager, up_menu, kInB );

        boolean	auto = false;

        if ( up_menu ){

            final String configAutoKey =
            		TransferSpeedValidator.getActiveAutoUploadParameter(globalManager);

	        auto = TransferSpeedValidator.isAutoSpeedActive(globalManager);

	        	// auto
	        final MenuItem auto_item = new MenuItem(parent,SWT.CHECK);
	        auto_item.setText(MessageText.getString("ConfigView.auto"));
	        auto_item.addListener(SWT.Selection,new Listener() {
	          @Override
	          public void handleEvent(Event e) {
	            COConfigurationManager.setParameter(configAutoKey,auto_item.getSelection());
	            COConfigurationManager.save();
	          }
	        });

	        if(auto)auto_item.setSelection(true);
	        auto_item.setEnabled(TransferSpeedValidator.isAutoUploadAvailable(core));

	        new MenuItem(parent,SWT.SEPARATOR);
        }

        MenuItem item = new MenuItem(parent, SWT.RADIO);
        item.setText(MessageText.getString("MyTorrentsView.menu.setSpeed.unlimited"));
        item.setData("maxkb", new Integer(0));
        item.setSelection(unlim && !auto);
        item.addListener(SWT.Selection, getLimitMenuItemListener(up_menu, parent, globalManager, configKey));

        Integer[] speed_limits = null;

        final String config_prefix = "config.ui.speed.partitions.manual." + ((up_menu) ? "upload": "download") + ".";
        if (COConfigurationManager.getBooleanParameter(config_prefix  + "enabled", false)) {
        	speed_limits = parseSpeedPartitionString(COConfigurationManager.getStringParameter(config_prefix + "values", ""));
        }

		if (speed_limits == null) {
			speed_limits = getGenericSpeedList(speedPartitions, maxBandwidth);
		}

        for (int i=0; i<speed_limits.length; i++) {
        	Integer i_value = speed_limits[i];
        	int value = i_value.intValue();
        	if (value < 5) {continue;} // Don't allow the user to easily select slow speeds.
            item = new MenuItem(parent, SWT.RADIO);
            item.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(value * kInB, true));
            item.setData("maxkb", i_value);
            item.addListener(SWT.Selection, getLimitMenuItemListener(up_menu, parent, globalManager, configKey));
            item.setSelection(!unlim && value == maxBandwidth && !auto);
        }

		new MenuItem(parent, SWT.SEPARATOR);

		final MenuItem itemDownSpeedManual = new MenuItem(parent, SWT.PUSH);
		Messages.setLanguageText(itemDownSpeedManual, "MyTorrentsView.menu.manual");
		itemDownSpeedManual.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String kbps_str = MessageText.getString("MyTorrentsView.dialog.setNumber.inKbps",
						new String[]{ DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB ) });

				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow();
				entryWindow.initTexts("MyTorrentsView.dialog.setSpeed.title",
						new String[] {
							MessageText.getString(up_menu
									? "MyTorrentsView.dialog.setNumber.upload"
									: "MyTorrentsView.dialog.setNumber.download")
						}, "MyTorrentsView.dialog.setNumber.text", new String[] {
							kbps_str,
							MessageText.getString(up_menu
									? "MyTorrentsView.dialog.setNumber.upload"
									: "MyTorrentsView.dialog.setNumber.download")
						});

				entryWindow.prompt(new UIInputReceiverListener() {
					@Override
					public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
						if (!entryWindow.hasSubmittedInput()) {
							return;
						}
						String sReturn = entryWindow.getSubmittedInput();

						if (sReturn == null)
							return;

						int newSpeed;
						try {
							newSpeed = (int) (Double.valueOf(sReturn).doubleValue());
						} catch (NumberFormatException er) {
							MessageBox mb = new MessageBox(parent.getShell(), SWT.ICON_ERROR
									| SWT.OK);
							mb.setText(MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
							mb.setMessage(MessageText.getString("MyTorrentsView.dialog.NumberError.text"));

							mb.open();
							return;
						}

						if (up_menu) {

							String configAutoKey = TransferSpeedValidator.getActiveAutoUploadParameter(globalManager);

							COConfigurationManager.setParameter(configAutoKey, false);
						}

						final int cValue = ((Integer) new TransferSpeedValidator(configKey,
								new Integer(newSpeed)).getValue()).intValue();

						COConfigurationManager.setParameter(configKey, cValue);

						COConfigurationManager.save();
					}
				});
			}
		});
    }

	  /**
	 * @param configKey
	 * @return
	 *
	 * @since 3.0.1.7
	 */
	private static int adjustMaxBandWidth(int maxBandwidth,
			GlobalManager globalManager, boolean up_menu, int kInB) {
    if(maxBandwidth == 0 && !up_menu )
    {
  		GlobalManagerStats stats = globalManager.getStats();
  		int dataReceive = stats.getDataReceiveRate();
  		if (dataReceive < kInB) {
        maxBandwidth = 275;
  		} else {
  			maxBandwidth = dataReceive / kInB;
  		}
    }
    return maxBandwidth;
	}

		private static java.util.Map parseSpeedPartitionStringCache = new java.util.HashMap();
	  private synchronized static Integer[] parseSpeedPartitionString(String s) {
		  Integer[] result = (Integer[])parseSpeedPartitionStringCache.get(s);
		  if (result == null) {
			  try {result = parseSpeedPartitionString0(s);}
			  catch (NumberFormatException nfe) {result = new Integer[0];}
			  parseSpeedPartitionStringCache.put(s, result);
		  }
		  if (result.length == 0) {return null;}
		  else {return result;}
	  }

	  private static Integer[] parseSpeedPartitionString0(String s) {
		  java.util.StringTokenizer tokeniser = new java.util.StringTokenizer(s.trim(), ",");
		  java.util.TreeSet values = new java.util.TreeSet(); // Filters duplicates out and orders the values.
		  while (tokeniser.hasMoreTokens()) {
			  values.add(new Integer(Integer.parseInt(tokeniser.nextToken().trim())));
		  }
		  return (Integer[])values.toArray(new Integer[values.size()]);
	  }

	    /**
	     * Gets the selection listener of a upload or download limit menu item (including unlimited)
	     * @param parent The parent menu
	     * @param configKey The configuration key
	     * @return The selection listener
	     */
	   private static final Listener getLimitMenuItemListener(final boolean up_menu,
			final Menu parent, final GlobalManager globalManager,
			final String configKey)
	   {
	       return new Listener() {
	           @Override
	           public void handleEvent(Event event) {
	               final MenuItem[] items = parent.getItems();
	               for(int i = 0; i < items.length; i++) {
	                    if(items[i] == event.widget)
	                    {
	                        items[i].setSelection(true);

	                        	// turn off auto speed first as this will revert the upload limit to
	                        	// what it was before it was turned on

	                        if ( up_menu ){

	                        	String configAutoKey =
	                        		TransferSpeedValidator.getActiveAutoUploadParameter(globalManager);

	                        	COConfigurationManager.setParameter( configAutoKey, false );
	                        }

	                        final int cValue = ((Integer)new TransferSpeedValidator(configKey, (Number)items[i].getData("maxkb")).getValue()).intValue();
	                        COConfigurationManager.setParameter(configKey, cValue);


	                        COConfigurationManager.save();
	                    }
	                    else {
	                        items[i].setSelection(false);
	                    }
	                }
	           }
	       };
	   }


	public static Integer[] getGenericSpeedList(int speedPartitions,
			int maxBandwidth) {
		java.util.List l = new java.util.ArrayList();
		int delta = 0;
		int increaseLevel = 0;
		for (int i = 0; i < speedPartitions; i++) {
			final int[] valuePair;
			if (delta == 0) {
				valuePair = new int[] {
					maxBandwidth
				};
			} else {
				valuePair = new int[] {
					maxBandwidth - delta * (maxBandwidth <= 1024 ? 1 : 1024),
					maxBandwidth + delta * (maxBandwidth < 1024 ? 1 : 1024)
				};
			}

			for (int j = 0; j < valuePair.length; j++) {
				if (j == 0) {
					l.add(0, new Integer(valuePair[j]));
				} else {
					l.add(new Integer(valuePair[j]));
				}
			}

			delta += increases[increaseLevel];
			if (increaseLevel < increases.length - 1) {
				increaseLevel++;
			}
		}
		return (Integer[]) l.toArray(new Integer[l.size()]);
	}

	/**
	 * @param cClickedFrom
	 * @since 3.0.1.7
	 */
	public static void invokeSlider(Control cClickedFrom, Core core, boolean isUpSpeed) {
		final String prefix = MessageText.getString(isUpSpeed
				? "GeneralView.label.maxuploadspeed"
				: "GeneralView.label.maxdownloadspeed");

		GlobalManager gm = core.getGlobalManager();

		final String configAutoKey = TransferSpeedValidator.getActiveAutoUploadParameter(gm);
		boolean auto = COConfigurationManager.getBooleanParameter(configAutoKey);

		final String configKey = isUpSpeed
				? TransferSpeedValidator.getActiveUploadParameter(gm)
				: "Max Download Speed KBs";
		int maxBandwidth = COConfigurationManager.getIntParameter(configKey);
		final boolean unlim = (maxBandwidth == 0);
		if (unlim && !isUpSpeed) {
			GlobalManagerStats stats = gm.getStats();
			int dataReceive = stats.getDataReceiveRate();
			if (dataReceive >= 1024) {
				maxBandwidth = dataReceive / 1024;
			}
		}

		SpeedScaleShell speedScale = new SpeedScaleShell() {
			@Override
			public String getStringValue(int value, String sValue) {
				if (sValue != null) {
					return prefix + ": " + sValue;
				}
				if (value == 0) {
					return MessageText.getString("MyTorrentsView.menu.setSpeed.unlimited");
				}
				if (value == -1) {
					return MessageText.getString("ConfigView.auto");
				}
				return prefix
						+ ": "
						+ DisplayFormatters.formatByteCountToKiBEtcPerSec( getValue() * 1024, true);
			}
		};
		int max = unlim ? (isUpSpeed ? 100 : 800) : maxBandwidth * 5;
		if (max < 50) {
			max = 50;
		}
		speedScale.setMaxValue(max);
		speedScale.setMaxTextValue(9999999);

		final String config_prefix = "config.ui.speed.partitions.manual."
				+ (isUpSpeed ? "upload" : "download") + ".";
		int lastValue = COConfigurationManager.getIntParameter(config_prefix
				+ "last", -10);

		Integer[] speed_limits;
		if (COConfigurationManager.getBooleanParameter(config_prefix + "enabled",
				false)) {
			speed_limits = parseSpeedPartitionString(COConfigurationManager.getStringParameter(
					config_prefix + "values", ""));
		} else {
			speed_limits = getGenericSpeedList(6, maxBandwidth);
		}
		if (speed_limits != null) {
			for (int i = 0; i < speed_limits.length; i++) {
				int value = speed_limits[i].intValue();
				if (value > 0) {
					speedScale.addOption(DisplayFormatters.formatByteCountToKiBEtcPerSec(
							value * 1024, true), value);
					if (value == lastValue) {
						lastValue = -10;
					}
				}
			}
		}
		speedScale.addOption(
				MessageText.getString("MyTorrentsView.menu.setSpeed.unlimited"), 0);
		speedScale.addOption(MessageText.getString("ConfigView.auto"), -1);

		if (lastValue > 0) {
			speedScale.addOption(DisplayFormatters.formatByteCountToKiBEtcPerSec(
					lastValue * 1024, true), lastValue);
		}

		if (speedScale.open(cClickedFrom, auto ? -1 : maxBandwidth, true)) {
			int value = speedScale.getValue();

			if (!speedScale.wasMenuChosen() || lastValue == value) {
				COConfigurationManager.setParameter(config_prefix + "last",
						maxBandwidth);
			}

			if (value >= 0) {
				if (auto) {
					COConfigurationManager.setParameter(configAutoKey, false);
				}
				COConfigurationManager.setParameter(configKey, value);
				COConfigurationManager.save();
			} else {
				// autospeed
				COConfigurationManager.setParameter(configAutoKey, true);
				COConfigurationManager.save();
			}
		}
	}

	public static void invokeSlider(Control cClickedFrom, Core core,
			DownloadManager[] dms, boolean isUpSpeed, Shell parentShell) {
		final String prefix = MessageText.getString(isUpSpeed
				? "GeneralView.label.maxuploadspeed"
				: "GeneralView.label.maxdownloadspeed");

		final int	kInB = DisplayFormatters.getKinB();

		int maxBandwidth_k = 0;
		boolean allDisabled = true;
		
		for (DownloadManager dm : dms) {
			int bandwidth = (isUpSpeed
					? dm.getStats().getUploadRateLimitBytesPerSecond()
					: dm.getStats().getDownloadRateLimitBytesPerSecond());
			
			int bw_k =  bandwidth / kInB;
			if (bw_k > maxBandwidth_k || bandwidth == 0) {
				maxBandwidth_k = bw_k;
				allDisabled = false;
			}else if ( bandwidth != -1 ) {
				allDisabled = false;
			}
		}
		boolean unlim = maxBandwidth_k == 0;
		final int num_entries = dms.length;

		SpeedScaleShell speedScale = new SpeedScaleShell() {
			@Override
			public String getStringValue(int value, String sValue) {
				if (sValue != null) {
					return prefix + ": " + sValue;
				}
				if (value == 0) {
					return MessageText.getString("MyTorrentsView.menu.setSpeed.unlimited");
				}else if (value == -1) {
					return MessageText.getString("ConfigView.auto");
				}else if (value == -2) {
					return MessageText.getString("label.disabled");
				}

				String speed = DisplayFormatters.formatByteCountToKiBEtcPerSec(
						value * kInB, true);
				if (num_entries > 1) {
					speed = MessageText.getString(
							"MyTorrentsView.menu.setSpeed.multi",
							new String[] {
								DisplayFormatters.formatByteCountToKiBEtcPerSec(value * kInB
										* num_entries),
								String.valueOf(num_entries),
								speed
							});
				}

				return prefix + ": " + speed;
			}
		};
		int max = unlim ? (isUpSpeed ? 100 : 800) : maxBandwidth_k * 5;
		if (max < 50) {
			max = 50;
		}
		speedScale.setMaxValue(max);
		speedScale.setMaxTextValue(9999999);
		speedScale.setParentShell(parentShell);

		final String config_prefix = "config.ui.speed.partitions.manual."
				+ (isUpSpeed ? "upload" : "download") + ".";
		int lastValue = COConfigurationManager.getIntParameter(config_prefix
				+ "last", -10);

		Integer[] speed_limits;
		if (COConfigurationManager.getBooleanParameter(config_prefix + "enabled",
				false)) {
			speed_limits = parseSpeedPartitionString(COConfigurationManager.getStringParameter(
					config_prefix + "values", ""));
		} else {
			speed_limits = getGenericSpeedList(6, maxBandwidth_k);
		}
		if (speed_limits != null) {
			for (int i = 0; i < speed_limits.length; i++) {
				int value = speed_limits[i].intValue();
				if (value > 0) {
					int total = value * num_entries;
					String speed = DisplayFormatters.formatByteCountToKiBEtcPerSec(
							total * kInB, true);
					if (num_entries > 1) {
						speed = MessageText.getString("MyTorrentsView.menu.setSpeed.multi",
								new String[] {
									speed,
									String.valueOf(num_entries),
									DisplayFormatters.formatByteCountToKiBEtcPerSec(value * kInB)
								});
					}

					speedScale.addOption(speed, value);
					if (value == lastValue) {
						lastValue = -10;
					}
				}
			}
		}
		speedScale.addOption(
				MessageText.getString("MyTorrentsView.menu.setSpeed.unlimited"), 0);
		speedScale.addOption(
				MessageText.getString("label.disabled"), -2);

		if (lastValue > 0) {
			speedScale.addOption(DisplayFormatters.formatByteCountToKiBEtcPerSec(
					lastValue * kInB, true), lastValue);
		}

		if (speedScale.open(cClickedFrom, allDisabled?-2:maxBandwidth_k, true)) {
			int value = speedScale.getValue();

			if (!speedScale.wasMenuChosen() || lastValue == value) {
				COConfigurationManager.setParameter(config_prefix + "last",
						maxBandwidth_k);
			}

			if (value >= 0) {
				for (DownloadManager dm : dms) {
					if (isUpSpeed) {
						dm.getStats().setUploadRateLimitBytesPerSecond(value * kInB);
					} else {
						dm.getStats().setDownloadRateLimitBytesPerSecond(value * kInB);
					}
				}
			}else if ( value == -2 ) {
				for (DownloadManager dm : dms) {
					if (isUpSpeed) {
						dm.getStats().setUploadRateLimitBytesPerSecond(-1);
					} else {
						dm.getStats().setDownloadRateLimitBytesPerSecond(-1);
					}
				}
			}
		}
	}

}
