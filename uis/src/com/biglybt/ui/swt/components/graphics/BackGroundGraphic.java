/*
 * File    : ScaledGraphic.java
 * Created : 15 d�c. 2003}
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
package com.biglybt.ui.swt.components.graphics;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;

import com.biglybt.ui.swt.utils.ColorCache;

/**
 * @author Olivier
 *
 */
public class BackGroundGraphic implements Graphic {

  protected Canvas drawCanvas;

  protected Image bufferBackground;

  private Color lightGrey;
  private Color lightGrey2;
  
  protected Color colorWhite;
  protected Color colorBlack;
  protected Color colorGrey;

  protected AEMonitor	this_mon	= new AEMonitor( "BackGroundGraphic" );

  private boolean	isSIIECSensitive;

  public BackGroundGraphic() {
  }

  protected void
  setSIIECSensitive(
		boolean		b )
  {
	  isSIIECSensitive	= b;
  }

  @Override
  public void initialize(Canvas canvas) {
    this.drawCanvas = canvas;
    
    if ( Utils.isDarkAppearanceNative()){
    	lightGrey = ColorCache.getColor(canvas.getDisplay(), 5, 5, 5);
 	    lightGrey2 = ColorCache.getColor(canvas.getDisplay(), 12, 12, 12);
 	    colorWhite = ColorCache.getColor(canvas.getDisplay(), 110, 110, 110);
 	    colorBlack = ColorCache.getColor(canvas.getDisplay(), 160, 160, 160);
 	    colorGrey  = Colors.light_grey;
    }else{
	    lightGrey = ColorCache.getColor(canvas.getDisplay(), 250, 250, 250);
	    lightGrey2 = ColorCache.getColor(canvas.getDisplay(), 233, 233, 233);
	    colorWhite = Colors.white;
	    colorBlack = Colors.black;
	    colorGrey  = Colors.grey;
    }
    
	Menu menu = new Menu( canvas );

	final MenuItem mi_binary = new MenuItem( menu, SWT.CHECK );

	mi_binary.setText( MessageText.getString( "label.binary.scale.basis" ));

	mi_binary.setSelection( COConfigurationManager.getBooleanParameter( "ui.scaled.graphics.binary.based" ));

	mi_binary.addListener(SWT.Selection, new Listener() {
		@Override
		public void handleEvent(Event e) {
			COConfigurationManager.setParameter("ui.scaled.graphics.binary.based", mi_binary.getSelection());
		}
	});

	if ( isSIIECSensitive ){

		final MenuItem mi_iec = new MenuItem( menu, SWT.CHECK );

		mi_iec.setText(  MessageText.getString( "ConfigView.section.style.useSIUnits" ));

		mi_iec.setSelection( COConfigurationManager.getBooleanParameter( "config.style.useSIUnits" ));

		mi_iec.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				COConfigurationManager.setParameter("config.style.useSIUnits", mi_iec.getSelection());
			}
		});
	}
	
	addMenuItems( menu );
	
	canvas.setMenu( menu );
  }

  protected void
  addMenuItems(
	Menu	menu )
  {
  }
  
  @Override
  public void refresh(boolean force) {
  }

  protected void drawBackGround(boolean sizeChanged) {
    if(drawCanvas == null || drawCanvas.isDisposed())
      return;

    if(sizeChanged || bufferBackground == null) {
      Rectangle bounds = drawCanvas.getClientArea();
      if(bounds.height < 1 || bounds.width  < 1)
        return;

      if(bufferBackground != null && ! bufferBackground.isDisposed())
        bufferBackground.dispose();

      if(bounds.width > 10000 || bounds.height > 10000) return;

      bufferBackground = new Image(drawCanvas.getDisplay(),bounds);

      Color colors[] = new Color[4];
      colors[0] = colorWhite;
      colors[1] = lightGrey;
      colors[2] = lightGrey2;
      colors[3] = lightGrey;
      GC gcBuffer = new GC(bufferBackground);
      for(int i = 0 ; i < bounds.height - 2 ; i++) {
        gcBuffer.setForeground(colors[i%4]);
        gcBuffer.drawLine(1,i+1,bounds.width-1,i+1);
      }
      gcBuffer.setForeground(colorBlack);
      gcBuffer.drawLine(bounds.width-70,0,bounds.width-70,bounds.height-1);

      gcBuffer.drawRectangle(0,0,bounds.width-1,bounds.height-1);
      gcBuffer.dispose();
    }
  }

  public void dispose() {
    if(bufferBackground != null && ! bufferBackground.isDisposed())
      bufferBackground.dispose();
  }
}
