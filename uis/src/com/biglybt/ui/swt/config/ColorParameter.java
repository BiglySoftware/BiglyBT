/*
 * File    : ColorParameter.java
 * Created : 12 nov. 2003
 * By      : Olivier
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

package com.biglybt.ui.swt.config;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.GCStringPrinter;

import com.biglybt.ui.swt.utils.ColorCache;


/**
 * @author Olivier
 *
 */
public class ColorParameter extends Parameter implements ParameterListener {


  private Button colorChooser;
  private Image img;

  private int r;
  private int g;
  private int b;

  public ColorParameter(final Composite composite,
          final String name,
          int _r, int _g, int _b) {
	  this( composite, name, _r, _g, _b, false );
  }
  
  public ColorParameter(final Composite composite,
                        String configID,
                        int _r, int _g, int _b, boolean hasDefault) {
  	super(configID);
    colorChooser = new Button(composite,SWT.PUSH);
    if (configID == null) {
    	r = _r;
    	g = _g;
    	b = _b;
    } else {
      r = COConfigurationManager.getIntParameter(configID+".red",_r);
      g = COConfigurationManager.getIntParameter(configID+".green",_g);
      b = COConfigurationManager.getIntParameter(configID+".blue",_b);
      COConfigurationManager.addParameterListener(this.configID, this);
    }
    updateButtonColor(composite.getDisplay(), r, g, b);

    if ( hasDefault ){
	    Menu menu = new Menu( colorChooser );
	    
	    colorChooser.setMenu( menu );
	    
	    MenuItem mi = new MenuItem( menu, SWT.PUSH );
	    
	    mi.setText( MessageText.getString( "ConfigView.section.style.colorOverrides.reset" ));
	    
	    mi.addSelectionListener(
	    	new SelectionAdapter(){
	    		@Override
	    		public void widgetSelected(SelectionEvent e){
	    			 newColorChosen( null );
	    		}	
			});
    }
    
    colorChooser.addListener(SWT.Dispose, new Listener() {
      @Override
      public void handleEvent(Event e) {
      	if (ColorParameter.this.configID != null) {
      		COConfigurationManager.removeParameterListener(ColorParameter.this.configID, ColorParameter.this);
      	}
        if(img != null && ! img.isDisposed()) {
          img.dispose();
        }
      }
    });

    colorChooser.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event e) {
        ColorDialog cd = new ColorDialog(composite.getShell());

        List<RGB> custom_colours = Utils.getCustomColors();

        if ( r >= 0 && g >= 0 && b >= 0 ){

        	RGB colour = new RGB(r,g,b);

        	custom_colours.remove( colour );

        	custom_colours.add( 0, colour );

        	cd.setRGB( colour );
        }

        cd.setRGBs( custom_colours.toArray( new RGB[0]));

        RGB newColor = cd.open();

        if ( newColor == null ){

        	return;
        }

        Utils.updateCustomColors( cd.getRGBs());

        newColorChosen(newColor);
        if (configID != null) {
        	COConfigurationManager.setRGBParameter(configID, newColor.red, newColor.green, newColor.blue);
        } else {
        	r = newColor.red;
        	g = newColor.green;
        	b = newColor.blue;

          updateButtonColor(colorChooser.getDisplay(), r, g, b);
        }
        newColorSet(newColor);
      }
    });

  }

  private void updateButtonColor(final Display display, final int rV, final int gV, final int bV) {
    Image oldImg = img;
    img = new Image(display,25,10);
    GC gc = new GC(img);
    if ( r >= 0 && g >= 0 && b >= 0 ){
    	Color color = ColorCache.getColor(display, rV, gV, bV);
        gc.setBackground(color);
       	gc.fillRectangle(0,0,25,10);
     }else{
    	Color color = colorChooser.getBackground();
    	gc.setBackground(color);
       	gc.fillRectangle(0,0,25,10);
       	new GCStringPrinter( gc, "-", new Rectangle( 0, 0, 25, 10 ), 0, SWT.CENTER ).printString();
    }

    gc.dispose();
    colorChooser.setImage(img);
    if(oldImg != null && ! oldImg.isDisposed())
      oldImg.dispose();
  }

  @Override
  public Control getControl() {
    return colorChooser;
  }

  @Override
	public void setLayoutData(Object layoutData) {
		colorChooser.setLayoutData(layoutData);
	}

  @Override
  public void parameterChanged(String parameterName) {
    r = COConfigurationManager.getIntParameter(configID +".red");
    g = COConfigurationManager.getIntParameter(configID +".green");
    b = COConfigurationManager.getIntParameter(configID +".blue");
    updateButtonColor(colorChooser.getDisplay(), r, g, b);
  }

  public void newColorChosen(RGB newColor) {
    // subclasses can write their own code
  }

  public void newColorSet(RGB newColor) {
	  // subclasses can write their own code
  }

  @Override
  public void setValue(Object value) {
  	// not needed, we already trap external changes
  }

  public void setColor(int _r, int _g, int _b) {
		r = _r;
		g = _g;
		b = _b;

		if (configID == null) {
    	updateButtonColor(colorChooser.getDisplay(), r, g, b);
		} else {
			COConfigurationManager.setRGBParameter(configID, r, g, b);
		}
  }
}
