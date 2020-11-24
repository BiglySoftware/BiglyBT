/*
 * Created on Jun 1, 2006 4:16:52 PM
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
package com.biglybt.ui.swt.skin;

import java.util.Map;

import com.biglybt.ui.skin.SkinPropertiesImpl;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.LightHashMap;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.FontUtils;

/**
 * @author TuxPaper
 * @created Jun 1, 2006
 *
 */
public class SWTSkinPropertiesImpl
	extends SkinPropertiesImpl
	implements SWTSkinProperties
{
	private static Map<String, SWTColorWithAlpha> colorMap = new LightHashMap<>();

	/**
	 * @param skinPath
	 * @param mainSkinFile
	 */
	public SWTSkinPropertiesImpl(ClassLoader classLoader, String skinPath, String mainSkinFile) {
		super(classLoader, skinPath, mainSkinFile);
		setEmHeight();
	}

	private void setEmHeight() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				setEmHeightPX(FontUtils.getFontHeightInPX(Display.getDefault().getSystemFont()));
			}
		});
	}

	/**
	 *
	 */
	public SWTSkinPropertiesImpl() {
		super();
		setEmHeight();
	}

	// @see SWTSkinProperties#getColor(java.lang.String)
	@Override
	public Color getColor(String sID) {
		return getColorWithAlpha(sID).color;
	}

	@Override
	public SWTColorWithAlpha getColorWithAlpha(String sID) {
		SWTColorWithAlpha colorInfo;
		synchronized( colorMap ){
			colorInfo = colorMap.get(sID);
		}
		if ( colorInfo == null ){
			Color color;
			int alpha = 255;
			try {
				int[] rgb = getColorValue(sID);
				if (rgb[0] > -1) {
					color = ColorCache.getSchemedColor(Utils.getDisplay(), rgb[0], rgb[1], rgb[2]);
					if (rgb.length > 3) {
						alpha = rgb[3];
					}
				} else {
					color = ColorCache.getColor(Utils.getDisplay(), getStringValue(sID));
				}
			} catch (Exception e) {
				//				IMP.getLogger().log(LoggerChannel.LT_ERROR,
				//						"Failed loading color : color." + colorNames[i]);
				color = null;
			}
	
			colorInfo = new SWTColorWithAlpha(color, alpha);
			synchronized( colorMap ){
				colorMap.put(sID, colorInfo);
			}
		}
		
		return colorInfo;
	}

	@Override
	public void clearCache() {
		super.clearCache();
		synchronized( colorMap ){
			colorMap.clear();
		}
	}

	// @see SWTSkinProperties#getColor(java.lang.String, org.eclipse.swt.graphics.Color)
	@Override
	public Color getColor(String name, Color def) {
		Color color = getColor(name);
		if (color == null) {
			return def;
		}
		return color;
	}

	// @see SWTSkinProperties#getPxValue(java.lang.String, int)
	@Override
	public int getPxValue(String name, int def) {
		String value = getValue(name, null);
		if (value == null) {
			return def;
		}

		int result = def;
		try {
			if (value.endsWith("rem")) {
				float em = Float.parseFloat(value.substring(0, value.length() - 3));

				result = (int) (getEmHeightPX() * em);
			} else {
				result = Integer.parseInt(value);
			}
		} catch (NumberFormatException e) {
			// ignore error.. it might be valid to store a non-numeric..
			//e.printStackTrace();
		}
		return result;
	}

	public static void destroyStatics() {
		synchronized( colorMap ){
			colorMap.clear();
		}
	}
}
