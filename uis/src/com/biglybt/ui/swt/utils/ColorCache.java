/*
 * Created on Jun 30, 2006 6:22:44 PM
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
package com.biglybt.ui.swt.utils;

import java.util.*;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.RGB;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.ConfigKeysSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;

/**
 * @author TuxPaper
 * @created Jun 30, 2006
 *
 */
public class ColorCache
{
	private final static boolean DEBUG = Constants.isCVSVersion();

	private final static Map<Long, Color> mapColors = new HashMap<>();

	private final static HashSet<Color> colorsToDispose = new HashSet<>();

	private final static int SYSTEMCOLOR_INDEXSTART = 17;
	private final static String[] systemColorNames = {
		"COLOR_WIDGET_DARK_SHADOW",
		"COLOR_WIDGET_NORMAL_SHADOW",
		"COLOR_WIDGET_LIGHT_SHADOW",
		"COLOR_WIDGET_HIGHLIGHT_SHADOW",
		"COLOR_WIDGET_FOREGROUND",
		"COLOR_WIDGET_BACKGROUND",
		"COLOR_WIDGET_BORDER",
		"COLOR_LIST_FOREGROUND",
		"COLOR_LIST_BACKGROUND",
		"COLOR_LIST_SELECTION",
		"COLOR_LIST_SELECTION_TEXT",
		"COLOR_INFO_FOREGROUND",
		"COLOR_INFO_BACKGROUND",
		"COLOR_TITLE_FOREGROUND",
		"COLOR_TITLE_BACKGROUND",
		"COLOR_TITLE_BACKGROUND_GRADIENT",
		"COLOR_TITLE_INACTIVE_FOREGROUND",
		"COLOR_TITLE_INACTIVE_BACKGROUND",
		"COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT",
		"COLOR_LINK_FOREGROUND",
	};
	
	private static TimerEventPeriodic timerColorCacheChecker;
	
	private static boolean forceNoColor;
	
	static {
		forceNoColor = COConfigurationManager.getBooleanParameter(
				ConfigKeysSWT.BCFG_FORCE_GRAYSCALE);
		COConfigurationManager.addParameterListener(
				ConfigKeysSWT.BCFG_FORCE_GRAYSCALE, name -> {
					if (forceNoColor == COConfigurationManager.getBooleanParameter(
							name)) {
						return;
					}
					forceNoColor = !forceNoColor;
					reset();
				});
	}


	public static void
	reset()
	{
		// Called when the scheme has changed and thus invalidated all the scheme-adjusted colours
		// We can't dispose of any of the existing colours as they are no doubt in use to we
		// have to accept some leakage here

		mapColors.clear();
	}

	public static void dispose() {
		Object[] disposeList = colorsToDispose.toArray();
		colorsToDispose.clear();
		Utils.disposeSWTObjects(disposeList);
		if (timerColorCacheChecker != null) {
			timerColorCacheChecker.cancel();
			timerColorCacheChecker = null;
		}
	}

	public static Color getSchemedColor(Device device, int red, int green, int blue) {
		ensureMapColorsInitialized(device);

		Long key = new Long(((long) red << 16) + (green << 8) + blue + 0x1000000l);

		Color color = mapColors.get(key);
		if (color == null || color.isDisposed()) {
			try {
				if (red < 0) {
					red = 0;
				} else if (red > 255) {
					red = 255;
				}
				if (green < 0) {
					green = 0;
				} else if (green > 255) {
					green = 255;
				}
				if (blue < 0) {
					blue = 0;
				} else if (blue > 255) {
					blue = 255;
				}

	      RGB rgb = new RGB(red, green, blue);
	      float[] hsb = rgb.getHSB();
	      hsb[0] += Colors.diffHue;
	      if (hsb[0] > 360) {
	      	hsb[0] -= 360;
	      } else if (hsb[0] < 0) {
	      	hsb[0] += 360;
	      }
	      hsb[1] *= Colors.diffSatPct;
	      //hsb[2] *= Colors.diffLumPct;

	      color = getColor(device, hsb);
	      mapColors.put(key, color);
			} catch (IllegalArgumentException e) {
				Debug.out("One Invalid: " + red + ";" + green + ";" + blue, e);
			}
		}

		return color;
	}

	public static Color getColor(Device device, int red, int green, int blue) {
		if (Utils.isDisplayDisposed()) {
			return null;
		}
		ensureMapColorsInitialized(device);

		Long key = new Long(((long) red << 16) + (green << 8) + blue);

		Color color = mapColors.get(key);
		if (color == null || color.isDisposed()) {
			if ( device== null ){
				return( null );	// allow peeking of cache
			}
			try {
				if (red < 0) {
					red = 0;
				} else if (red > 255) {
					red = 255;
				}
				if (green < 0) {
					green = 0;
				} else if (green > 255) {
					green = 255;
				}
				if (blue < 0) {
					blue = 0;
				} else if (blue > 255) {
					blue = 255;
				}
				
				if (forceNoColor && (blue != red || blue != green)) {
					double brightness = Math.sqrt(
							red * red * 0.299 + green * green * 0.587 + blue * blue * 0.114);
					int grayscale = (int) brightness;
					color = getColor(device, grayscale, grayscale, grayscale);
				} else {
					color = new Color(device, red, green, blue);
					colorsToDispose.add(color);
				}

			} catch (IllegalArgumentException e) {
				Debug.out("One Invalid: " + red + ";" + green + ";" + blue, e);
			}
			addColor(key, color);
		}

		return color;
	}

	private static void ensureMapColorsInitialized(Device device) {
		if (device == null || device.isDisposed()) {
			return;
		}
		if (mapColors.size() == 0) {
			for (int i = 1; i <= 16; i++) {
				Color color = device.getSystemColor(i);
				Long key = new Long(((long) color.getRed() << 16)
						+ (color.getGreen() << 8) + color.getBlue());
				addColor(key, color);
			}
			if (DEBUG) {
				timerColorCacheChecker = SimpleTimer.addPeriodicEvent("ColorCacheChecker", 60000,
						new TimerEventPerformer() {
							@Override
							public void perform(TimerEvent event) {
								if (Utils.isDisplayDisposed()) {
									event.cancel();
									return;
								}
								Utils.execSWTThread(new AERunnable() {
									@Override
									public void runSupport() {
										for (Iterator<Long> iter = mapColors.keySet().iterator(); iter.hasNext(); ) {
											Long key = iter.next();
											Color color = mapColors.get(key);
											if (color.isDisposed()) {
												System.err.println("Someone disposed of color "
														+ Long.toHexString(key.longValue()));
												iter.remove();
											}
										}
									}
								});
							}
						});
			}
		}
	}

	public static Color getColor(Device device, String value) {
		return getColor(device, value, false);
	}

	public static Color getSchemedColor(Device device, String value) {
		return getColor(device, value, true);
	}

	private static Color getColor(Device device, String c_value, boolean useScheme) {
		int[] colors = new int[3];

		if (c_value == null || c_value.length() == 0) {
			return null;
		}

		try {
			if (c_value.charAt(0) == '#') {
				// hex color string
				if (c_value.length() == 4) {
					long l = Long.parseLong(c_value.substring(1), 16);
					colors[0] = (int) ((l >> 8) & 15) << 4;
					colors[1] = (int) ((l >> 4) & 15) << 4;
					colors[2] = (int) (l & 15) << 4;
				} else {
					long l = Long.parseLong(c_value.substring(1), 16);
					colors[0] = (int) ((l >> 16) & 255);
					colors[1] = (int) ((l >> 8) & 255);
					colors[2] = (int) (l & 255);
				}
			} else if (c_value.indexOf(',') > 0) {
				StringTokenizer st = new StringTokenizer(c_value, ",");
				colors[0] = Integer.parseInt(st.nextToken());
				colors[1] = Integer.parseInt(st.nextToken());
				colors[2] = Integer.parseInt(st.nextToken());
			} else {
				String u_value = c_value.toUpperCase();
				if (u_value.startsWith("COLOR_")) {
					for (int i = 0; i < systemColorNames.length; i++) {
						String name = systemColorNames[i];
						if (name.equals(u_value) && device != null && !device.isDisposed()) {
							if ( Utils.isDarkAppearanceNativeWindows()){
								return Colors.getSystemColor(device,i + SYSTEMCOLOR_INDEXSTART);
							}else{
								return device.getSystemColor(i + SYSTEMCOLOR_INDEXSTART);
							}
						}
					}
				} else if (u_value.startsWith("BLUE.FADED.")) {
					int idx = Integer.parseInt(u_value.substring(11));
					return Colors.faded[idx];
				} else if (u_value.startsWith("BLUE.")) {
					int idx = Integer.parseInt(u_value.substring(5));
					return Colors.blues[idx];
				} else if (u_value.equals("ALTROW")) {
					return Colors.colorAltRow;
				}else if ( c_value.startsWith( "config." )){
					int	def_pos = c_value.indexOf( ':' );

					String	config_name;
					String	def_value;

					if ( def_pos != -1 ){

						config_name = c_value.substring( 0, def_pos );
						def_value	= c_value.substring( def_pos+1 );
					}else{
						config_name = c_value;
						def_value	= null;
					}

					String x_value = COConfigurationManager.getStringParameter( config_name, def_value );
					
					if ("null".equals(x_value)) {
						// c_value might explicely request null as default, such as
						// "config.sking.color.foo:null"
						return null;
					}

						// default values get scheme adjustments applied, explicit values don't as the
						// user has selected them

					useScheme = x_value == def_value;

					Color result = getColor( device, x_value, useScheme );

					if ( result == null ){

						Debug.out( "No color found for '" + c_value + "'" );

						result = Colors.white;
					}

					return( result );
				}
				return null;
			}
		} catch (Exception e) {
			Debug.out(c_value, e);
			return null;
		}

		if (!useScheme) {
			return getColor(device, colors[0], colors[1], colors[2]);
		}
		return getSchemedColor(device, colors[0], colors[1], colors[2]);
	}

	private static void addColor(Long key, Color color) {
		mapColors.put(key, color);
	}

	/**
	 *
	 * @since 3.0.4.3
	 */
	public static Color getColor(Device device, int[] rgb) {
		if (rgb == null || rgb.length < 3) {
			return null;
		}
		return getColor(device, rgb[0], rgb[1], rgb[2]);
	}

	public static Color getRandomColor() {
		if (mapColors.size() == 0) {
			return Colors.black;
		}
		int r = (int) (Math.random() * mapColors.size());
		return (Color) mapColors.values().toArray()[r];
	}

	/**
	 *
	 * @since 3.1.1.1
	 */
	public static Color getColor(Device device, float[] hsb) {
		if (hsb[0] < 0) {
			hsb[0] = 0;
		} else if (hsb[0] > 360) {
			hsb[0] = 360;
		}
		if (hsb[1] < 0) {
			hsb[1] = 0;
		} else if (hsb[1] > 1) {
			hsb[1] = 1;
		}
		if (hsb[2] < 0) {
			hsb[2] = 0;
		} else if (hsb[2] > 1) {
			hsb[2] = 1;
		}
		RGB rgb = new RGB(hsb[0], hsb[1], hsb[2]);
		return getColor(device, rgb.red, rgb.green, rgb.blue);
	}

	/**
	 * @param device
	 * @param rgb
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public static Color getColor(Device device, RGB rgb) {
		return getColor(device, rgb.red, rgb.green, rgb.blue);
	}

	public static class MyAEDiagnosticsEvidenceGenerator implements AEDiagnosticsEvidenceGenerator {
		@Override
		public void generate(IndentWriter writer) {
			writer.println("Colors:");
			writer.indent();
			writer.println("# cached: " + mapColors.size());
			writer.exdent();
		}
	}
}
