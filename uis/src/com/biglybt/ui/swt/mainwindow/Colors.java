/*
 * Created on 2 mai 2004 Created by Olivier Chalouhi
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details (
 * see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package com.biglybt.ui.swt.mainwindow;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.utils.ColorCache;
/**
 * @author Olivier Chalouhi
 * @author MjrTom
 *			2005/Dec/08: green
 *
 */
public class Colors implements ParameterListener {
	private static final LogIDs LOGID = LogIDs.GUI;
  private static Colors instance = null;
  public static final int BLUES_LIGHTEST = 0;
  public static final int BLUES_DARKEST = 9;
  public static final int BLUES_MIDLIGHT = (BLUES_DARKEST + 1) / 4;
  public static final int BLUES_MIDDARK = ((BLUES_DARKEST + 1) / 2)
      + BLUES_MIDLIGHT;
  public static final int FADED_LIGHTEST = 0;
  public static final int FADED_DARKEST = 9;

  public static Color[] blues = new Color[BLUES_DARKEST + 1];
  public static Color[] faded = new Color[FADED_DARKEST + 1];
  public static Color colorProgressBar;
  public static Color colorInverse;
  public static Color colorShiftLeft;
  public static Color colorShiftRight;
  public static Color colorError;
  public static Color colorErrorBG;
  public static Color colorAltRow;
  public static Color colorWarning;
  public static Color black;
  public static Color light_grey;
  public static Color dark_grey;
  public static Color blue;
  public static Color green;
  public static Color fadedGreen;
  public static Color grey;
  public static Color red;
  public static Color fadedRed;
  public static Color yellow;
  public static Color fadedYellow;
  public static Color white;
  public static Color background;
  public static Color red_ConsoleView;

  private static final AEMonitor	class_mon	= new AEMonitor( "Colors" );
	public static int diffHue;
	public static float diffSatPct;
	public static float diffLumPct;

	final static public Color[] alternatingColors = new Color[] {
			null,
			null
	};

	static List<ParameterListener>	listeners = new ArrayList<>();
	private static AEDiagnosticsEvidenceGenerator genColorCache;

	private final ParameterListener configListener;

  private void allocateBlues() {
	  int r = COConfigurationManager.getIntParameter("Color Scheme.red");
	  int g = COConfigurationManager.getIntParameter("Color Scheme.green");
	  int b = COConfigurationManager.getIntParameter("Color Scheme.blue");
    try {
      boolean bGrayScale = (r == b) && (b == g);

      HSLColor hslDefault = new HSLColor();
      hslDefault.initHSLbyRGB(0, 128, 255);

      HSLColor hslScheme = new HSLColor();
      hslScheme.initHSLbyRGB(r, g, b);

      diffHue = hslScheme.getHue() - hslDefault.getHue();
      diffSatPct = hslScheme.getSaturation() == 0 ? 0 : (float) hslDefault.getSaturation() / hslScheme.getSaturation();
      diffLumPct = hslScheme.getLuminence() == 0 ? 0 : (float) hslDefault.getLuminence() / hslScheme.getLuminence();

      HSLColor hslColor = new HSLColor();
      Color colorTables = Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND);
      int tR = colorTables.getRed();
      int tG = colorTables.getGreen();
      int tB = colorTables.getBlue();

      // 0 == window background (white)
      // [blues.length-1] == rgb
      // in between == blend
      for (int i = 0; i < blues.length; i++) {
        hslColor.initHSLbyRGB(r, g, b);
        float blendBy = (i == 0) ? 1 : (float) 1.0
            - ((float) i / (float) (blues.length - 1));
        hslColor.blend(tR, tG, tB, blendBy);
        blues[i] = ColorCache.getColor(display, hslColor.getRed(),
						hslColor.getGreen(), hslColor.getBlue());
        int iSat = hslColor.getSaturation();
        int luminence = hslColor.getLuminence();
        if (luminence < 20) {
          if (iSat > 10) {
            hslColor.setSaturation(iSat / 2);
            hslColor.brighten(1.25f);
          } else if (bGrayScale) {
          	// gray
          	hslColor.brighten(1.2f);
          }
        } else {
          if (iSat > 10) {
            hslColor.setSaturation(iSat / 2);
            hslColor.brighten(0.75f);
          } else if (bGrayScale) {
          	// gray
          	hslColor.brighten(0.8f);
          }
        }

        faded[i] = ColorCache.getColor(display, hslColor.getRed(),
						hslColor.getGreen(), hslColor.getBlue());
      }

      if (bGrayScale) {
      	if (b > 200)
      		b -= 20;
      	else
      		b += 20;
      }
      hslColor.initHSLbyRGB(r, g, b);
      hslColor.reverseColor();
      colorInverse = ColorCache.getColor(display, hslColor.getRed(),
					hslColor.getGreen(), hslColor.getBlue());

      hslColor.initHSLbyRGB(r, g, b);
      hslColor.setHue(hslColor.getHue() + 25);
      colorShiftRight = ColorCache.getColor(display, hslColor.getRed(),
					hslColor.getGreen(), hslColor.getBlue());

      hslColor.initHSLbyRGB(r, g, b);
      hslColor.setHue(hslColor.getHue() - 25);
      colorShiftLeft = ColorCache.getColor(display, hslColor.getRed(),
					hslColor.getGreen(), hslColor.getBlue());
    } catch (Exception e) {
    	Logger.log(new LogEvent(LOGID, "Error allocating colors", e));
    }
  }

  private void allocateColorProgressBar() {
		if (display == null || display.isDisposed())
			return;

		colorProgressBar = new AllocateColor("progressBar", colorShiftRight,
				colorProgressBar).getColor();
	}

  private void allocateColorErrorBG() {
		if (display == null || display.isDisposed())
			return;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				Color colorTables = Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND);
				HSLColor hslColor = new HSLColor();
				hslColor.initHSLbyRGB(colorTables.getRed(), colorTables.getGreen(),
						colorTables.getBlue());
				int lum = hslColor.getLuminence();
				int sat = hslColor.getSaturation();

				lum = (int)((lum > 127) ? lum * 0.8 : lum * 1.3);

				if (sat == 0) {
					sat = 80;
				}

				hslColor.initRGBbyHSL(0, sat, lum);

				colorErrorBG = new AllocateColor("errorBG", new RGB(hslColor.getRed(),
						hslColor.getGreen(), hslColor.getBlue()), colorErrorBG).getColor();
			}
		}, false);
	}

	private void allocateColorError() {
		if (display == null || display.isDisposed())
			return;

		colorError = new AllocateColor("error", new RGB(255, 68, 68), colorError)
				.getColor();
	}

	private void allocateColorWarning() {
		if (display == null || display.isDisposed())
			return;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				Color colorTables = Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND);
				HSLColor hslBG = new HSLColor();
				hslBG.initHSLbyRGB(colorTables.getRed(), colorTables.getGreen(),
						colorTables.getBlue());
				int lum = hslBG.getLuminence();

				HSLColor hslColor = new HSLColor();
				hslColor.initRGBbyHSL(25, 200, 128 + (lum < 160 ? 10 : -10));
				colorWarning = new AllocateColor("warning", new RGB(hslColor.getRed(),
						hslColor.getGreen(), hslColor.getBlue()), colorWarning).getColor();
			}
		}, false);
	}

	private void allocateColorAltRow() {
		if (display == null || display.isDisposed())
			return;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				Color colorTables = Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND);
				HSLColor hslColor = new HSLColor();
				hslColor.initHSLbyRGB(colorTables.getRed(), colorTables.getGreen(),
						colorTables.getBlue());

				int lum = hslColor.getLuminence();
				int sat = hslColor.getSaturation();
				int hue = hslColor.getHue();
				if (lum > 127) {
					lum -= 10;
					sat = 127;
					hue = 155;
				} else {
					lum += 30; // it's usually harder to see difference in darkness
				}
				hslColor.setLuminence(lum);
				hslColor.setHue(hue);
				hslColor.setSaturation(sat);

				//HSLColor blueHSL = new HSLColor();
				//RGB rgb = blues[BLUES_DARKEST].getRGB();
				//blueHSL.initHSLbyRGB(rgb.red, rgb.green, rgb.blue);
				//int blueHue = blueHSL.getHue();
				//int altHue = hslColor.getHue();
				//if (blueHue > altHue) {
				//	altHue += 11;
				//} else {
				//	altHue -= 11;
				//}
				//hslColor.setHue(blueHue);
				colorAltRow = new AllocateColor("altRow", new RGB(hslColor.getRed(),
						hslColor.getGreen(), hslColor.getBlue()), colorAltRow).getColor();

				alternatingColors[1] = colorAltRow;
			}
		}, false);
	}

  /** Allocates a color */
  private class AllocateColor extends AERunnable {
    private String sName;
    private RGB rgbDefault;
    private Color newColor;

    public AllocateColor(String sName, RGB rgbDefault, Color colorOld) {
      this.sName = sName;
      this.rgbDefault = rgbDefault;
    }

    public AllocateColor(String sName, final Color colorDefault, Color colorOld) {
			this.sName = sName;
			Utils.execSWTThread(() -> {
				if (!colorDefault.isDisposed()) {
					AllocateColor.this.rgbDefault = colorDefault.getRGB();
					COConfigurationManager.setRGBDefault("Colors." + sName,
							rgbDefault.red, rgbDefault.green, rgbDefault.blue);
				} else {
					AllocateColor.this.rgbDefault = new RGB(0, 0, 0);
				}
			}, false);
		}

    public Color getColor() {
    	Utils.execSWTThread(this, false);
      return newColor;
    }

    @Override
    public void runSupport() {
      if (COConfigurationManager.getBooleanParameter("Colors." + sName + ".override")) {
        newColor = ColorCache.getColor(display,
           COConfigurationManager.getIntParameter("Colors." + sName + ".red"),
           COConfigurationManager.getIntParameter("Colors." + sName + ".green"),
           COConfigurationManager.getIntParameter("Colors." + sName + ".blue"));
      } else {
        newColor = ColorCache.getColor(display, rgbDefault.red,
        		rgbDefault.green, rgbDefault.blue);
        // Since the color is not longer overriden, reset back to default
        // so that the user sees the correct color in Config.
        COConfigurationManager.setRGBParameter("Colors." + sName,
						rgbDefault.red, rgbDefault.green, rgbDefault.blue, null);
      }
    }
  }

  private void allocateDynamicColors( final boolean first_time ) {
    if(Utils.isDisplayDisposed())
      return;

    Utils.execSWTThread(new AERunnable(){
      @Override
      public void runSupport() {
        allocateBlues();
        allocateColorProgressBar();
        allocateColorErrorBG();

        if ( !first_time ){
        	ColorCache.reset();
        }
      }
    }, false);
  }

  private void allocateNonDynamicColors() {
		allocateColorWarning();
		allocateColorError();
		allocateColorAltRow();

		black = ColorCache.getColor(display, 0, 0, 0);
		light_grey = ColorCache.getColor(display, 192, 192, 192);
		dark_grey = ColorCache.getColor(display, 96, 96, 96);
		blue = ColorCache.getColor(display, 0, 0, 170);
		green = ColorCache.getColor(display, 0, 170, 0);
		fadedGreen = ColorCache.getColor(display, 96, 160, 96);
		grey = ColorCache.getColor(display, 170, 170, 170);
		red = ColorCache.getColor(display, 255, 0, 0);
		fadedRed = ColorCache.getColor(display, 160, 96, 96);
		yellow = ColorCache.getColor(display, 255, 255, 0);
		fadedYellow = ColorCache.getColor(display, 255, 255, 221);
		white = ColorCache.getColor(display, 255, 255, 255);
		background = ColorCache.getColor(display, 248, 248, 248);
		red_ConsoleView = ColorCache.getColor(display, 255, 192, 192);
	}

  private Display display;

  private Colors() {
    instance = this;

	  configListener =
			  new ParameterListener()
			  {
				  @Override
				  public void
				  parameterChanged(
						  String parameterName)
				  {
					  List<ParameterListener>	copy;

					  synchronized( listeners ){
						  copy = new ArrayList<>( listeners );
					  }

					  for ( ParameterListener l: copy ){
						  try{
							  l.parameterChanged(parameterName);
						  }catch( Throwable e ){
							  Debug.out( e );
						  }
					  }
				  }
			  };

	  COConfigurationManager.addParameterListener("Color Scheme", configListener);
	  COConfigurationManager.addParameterListener("Colors.progressBar", configListener);
	  COConfigurationManager.addParameterListener("Colors.error", configListener);
	  COConfigurationManager.addParameterListener("Colors.warning", configListener);
	  COConfigurationManager.addParameterListener("Colors.altRow", configListener);


	  display = Utils.getDisplay();
    allocateDynamicColors( true );
    allocateNonDynamicColors();

    addColorsChangedListener(this);
  }

  public static Colors getInstance() {
  	try{
  		class_mon.enter();
	    if (instance == null) {
		    if (Utils.isDisplayDisposed()) {
			    return null;
		    }
		    instance = new Colors();

		    genColorCache = new ColorCache.MyAEDiagnosticsEvidenceGenerator();
		    AEDiagnostics.addEvidenceGenerator(genColorCache);
	    }

	    return instance;
  	}finally{

  		class_mon.exit();
  	}
  }

  public static void disposeInstance() {
  	if (instance != null) {
  		instance.dipose();
  		instance = null;
	  }

	  AEDiagnostics.removeEvidenceGenerator(genColorCache);
  	genColorCache = null;

	  blues = new Color[BLUES_DARKEST + 1];
	  faded = new Color[FADED_DARKEST + 1];
	  colorProgressBar = null;
	  colorInverse = null;
	  colorShiftLeft = null;
	  colorShiftRight = null;
	  colorError = null;
	  colorErrorBG = null;
	  colorAltRow = null;
	  colorWarning = null;
	  black = null;
	  light_grey = null;
	  dark_grey = null;
	  blue = null;
	  green = null;
	  fadedGreen = null;
	  grey = null;
	  red = null;
	  fadedRed = null;
	  yellow = null;
	  fadedYellow = null;
	  white = null;
	  background = null;
	  red_ConsoleView = null;
	  alternatingColors[1] = null;

	  listeners.clear();
  }

	private void dipose() {
		COConfigurationManager.removeParameterListener("Color Scheme", configListener);
		COConfigurationManager.removeParameterListener("Colors.progressBar.override", configListener);
		COConfigurationManager.removeParameterListener("Colors.progressBar", configListener);
		COConfigurationManager.removeParameterListener("Colors.error.override", configListener);
		COConfigurationManager.removeParameterListener("Colors.error", configListener);
		COConfigurationManager.removeParameterListener("Colors.warning.override", configListener);
		COConfigurationManager.removeParameterListener("Colors.warning", configListener);
		COConfigurationManager.removeParameterListener("Colors.altRow.override", configListener);
		COConfigurationManager.removeParameterListener("Colors.altRow", configListener);
	}

	public void addColorsChangedListener(ParameterListener l) {
	  synchronized( listeners ){
		  listeners.add( l );
	  }
  }

  public void removeColorsChangedListener(ParameterListener l) {
	  synchronized( listeners ){
		  listeners.remove( l );
	  }
  }

  @Override
  public void parameterChanged(String parameterName) {
    if (parameterName.equals("Color Scheme")) {
      allocateDynamicColors( false );
    }

    if(parameterName.startsWith("Colors.progressBar")) {
      allocateColorProgressBar();
    }
    if(parameterName.startsWith("Colors.error")) {
      allocateColorError();
    }
    if(parameterName.startsWith("Colors.warning")) {
      allocateColorWarning();
    }
    if(parameterName.startsWith("Colors.altRow")) {
      allocateColorAltRow();
    }
  }

	public static Color getSystemColor (Device d, int id) {
		if (Utils.isGTK3) {
			if (id == SWT.COLOR_INFO_BACKGROUND) {
				return ColorCache.getColor(d, 0, 0,0 );
			}
			if (id == SWT.COLOR_INFO_FOREGROUND) {
				return ColorCache.getColor(d, 255, 255,255 );
			}
		}
		return d.getSystemColor(id);
	}
	
	public Color
	getFadedColor(
		Color	c )
	{
		HSLColor hslColor = new HSLColor();
		hslColor.initHSLbyRGB(c.getRed(), c.getGreen(), c.getBlue());
		
		int iSat = hslColor.getSaturation();
		int luminence = hslColor.getLuminence();
		if (luminence < 20) {
			if (iSat > 10) {
				hslColor.setSaturation(iSat / 2);
				hslColor.brighten(1.25f);
			}
		} else {
			if (iSat > 10) {
				hslColor.setSaturation(iSat / 2);
				hslColor.brighten(0.75f);
			} 
		}

		return( ColorCache.getColor(display, hslColor.getRed(),	hslColor.getGreen(), hslColor.getBlue()));
	}
	
	public Color
	getSlightlyFadedColor(
		Color	c )
	{
		HSLColor hslColor = new HSLColor();
		hslColor.initHSLbyRGB(c.getRed(), c.getGreen(), c.getBlue());
		
		int iSat = hslColor.getSaturation();
		int luminence = hslColor.getLuminence();
		if (luminence < 20) {
			if (iSat > 10) {
				hslColor.setSaturation((int)(iSat / 1.25));
				hslColor.brighten(1.12f);
			}
		} else {
			if (iSat > 10) {
				hslColor.setSaturation((int)(iSat / 1.25));
				hslColor.brighten(0.63f);
			} 
		}

		return( ColorCache.getColor(display, hslColor.getRed(),	hslColor.getGreen(), hslColor.getBlue()));
	}
	
	public Color
	getLighterColor(
		Color	c,
		int		percent )
	{
		HSLColor hslColor = new HSLColor();
		hslColor.initHSLbyRGB(c.getRed(), c.getGreen(), c.getBlue());
		
		int lum = hslColor.getLuminence();
		
		int new_lum = (int)( lum * (1.0f + ((float)percent)/100));
		
		if ( new_lum < 240){
			
			hslColor.setLuminence( new_lum );
			
		}else{
			
			hslColor.setLuminence( 240 );
			
			int sat = hslColor.getSaturation();
			
			hslColor.setSaturation( sat / 2);
		}

		return( ColorCache.getColor(display, hslColor.getRed(),	hslColor.getGreen(), hslColor.getBlue()));
	}

	public Color getReadableColor(Color forBG) {
		if (forBG == null || forBG.isDisposed()) {
			return black;
		}

		int red = forBG.getRed();
		int green = forBG.getGreen();
		int blue = forBG.getBlue();

		double brightness = Math.sqrt(
				red * red * 0.299 + green * green * 0.587 + blue * blue * 0.114);

		return brightness >= 130 ? Colors.black : Colors.white;
	}
}
