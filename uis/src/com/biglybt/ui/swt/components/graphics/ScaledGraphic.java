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

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.mainwindow.Colors;

/**
 * @author Olivier
 *
 */
public class ScaledGraphic extends BackGroundGraphic {

  protected Scale scale;
  protected ValueFormater formater;

  protected Image bufferScale;
  private int lastMax;

  private int update_divider_width = 0;

  public ScaledGraphic(Scale scale,ValueFormater formater) {
    this.scale = scale;
    this.formater = formater;

    	// should really move this to ValueFormatter rather than Scale but I'd have to update the
    	// monitoring plugin as it uses this stuff :(

    setSIIECSensitive( scale.isSIIECSensitive());
  }

  public void setUpdateDividerWidth(int width) {
	  this.update_divider_width = width;
  }

  protected void drawScale(boolean sizeChanged) {
    if(drawCanvas == null || drawCanvas.isDisposed() || !drawCanvas.isVisible())
      return;

    drawBackGround(sizeChanged);
  	if (bufferBackground == null || bufferBackground.isDisposed()) {
  		return;
  	}

    boolean scaleChanged = lastMax != scale.getMax();

    if(sizeChanged || scaleChanged || bufferScale == null) {
      Rectangle bounds = drawCanvas.getClientArea();
      if(bounds.height < 1  || bounds.width < 1)
        return;

      if(bufferScale != null && ! bufferScale.isDisposed())
        bufferScale.dispose();

      bufferScale = new Image(drawCanvas.getDisplay(),bounds);

      GC gcBuffer = new GC(bufferScale);
      try {
      gcBuffer.drawImage(bufferBackground,0,0);
      gcBuffer.setForeground(colorBlack);
      //gcImage.setBackground(null);
      scale.setNbPixels(bounds.height - 16);
      int[] levels = scale.getScaleValues();
      for(int i = 0 ; i < levels.length ; i++) {
        int height = bounds.height - scale.getScaledValue(levels[i]) - 2;
        gcBuffer.drawLine(1,height,bounds.width - 70 ,height);
        gcBuffer.drawText(formater.format(levels[i]),bounds.width - 65,height - 12,true);
      }
      if (this.update_divider_width > 0) {
    	  for (int i=bounds.width - 70; i > 0; i-=this.update_divider_width) {
    		  gcBuffer.setForeground(colorGrey);
    		  gcBuffer.drawLine(i, 0, i, bounds.height);
    	  }
      }
      } catch (Exception e) {
      	Debug.out(e);
      } finally {
      	gcBuffer.dispose();
      }
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    if(bufferScale != null && ! bufferScale.isDisposed())
      bufferScale.dispose();
    if ( scale != null ) {
    	scale.dispose();
    }
  }

}
