/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.stats;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

public class 
BasePanel
{
	static float def_minX = -1000;
	static float def_maxX = 1000;
	static float def_minY = -1000;
	static float def_maxY = 1000;
	static double def_rotation = 0;
	
	static class 
	Scale
		implements Cloneable
	{
		private float width;
		private float height;
		private float minX;
		private float maxX;
		private float minY;
		private float maxY;
		private double rotation;
		
		private float saveMinX;
		private float saveMaxX;
		private float saveMinY;
		private float saveMaxY;
		private double saveRotation;

		boolean  	disableAutoScale 	= false;
		long		lastAutoScale		= 0;

		boolean mouseLeftDown = false;
		boolean mouseRightDown = false;
		
		private int xDown;
		private int yDown;

		{
			reset();
		}
		
		public Scale
		clone()
		{
			try{
				Scale result = (Scale)super.clone();
				result.mouseLeftDown = false;
				result.mouseRightDown = false;
				return( result );
			}catch( CloneNotSupportedException e ){
				return( this );
			}
		}
		
		public void
		setSize(
			Rectangle	size )
		{
			width = size.width;
			height = size.height;
		}
		
		public void
		setScale(
			float		min_x,
			float		max_x,
			float		min_y,
			float		max_y )
		{
			minX = min_x;
			maxX = max_x;
			minY = min_y;
			maxY = max_y;
		}
		
		public void
		setScaleAndRotation(
			float		min_x,
			float		max_x,
			float		min_y,
			float		max_y,
			double		rot )
		{
			minX 		= min_x;
			maxX 		= max_x;
			minY 		= min_y;
			maxY 		= max_y;
			rotation 	= rot;
		}
		
		public float
		getMinX()
		{
			return( minX );
		}
		
		public float
		getMaxX()
		{
			return( maxX );
		}
		
		public float
		getMinY()
		{
			return( minY );
		}
		
		public float
		getMaxY()
		{
			return( maxY );
		}
		
		public void
		reset()
		{
			minX = def_minX;
			maxX = def_maxX;
			minY = def_minY;
			maxY = def_maxY;
			rotation = def_rotation;
			
			saveMinX	= 0;
			saveMaxX	= 0;
			saveMinY	= 0;
			saveMaxY	= 0;
			saveRotation	= 0;
			
	        disableAutoScale 	= false;
	        lastAutoScale		= 0;
		}
		
		public int getX(float x,float y) {
			return (int) (((x * Math.cos(rotation) + y * Math.sin(rotation))-minX)/(maxX - minX) * width);
		}

		public int getY(float x,float y) {
			return (int) (((y * Math.cos(rotation) - x * Math.sin(rotation))-minY)/(maxY-minY) * height);
		}
		
		public int[] getXY( float x, float y ){
			return( new int[]{getX(x,y), getY( x,y)});
		}
		
		/*
		public int getWidth( float w ){
			return( (int)(w/(maxX-minX)* width));
		}		
		
		public int getHeight( float w ){	
			return( (int)(w/(maxY-minY)* height));
		}
		*/
		
		public int getReverseWidth( float w ){
			return( (int)((w/width)* (maxX-minX)));
		}	
		
		public int getReverseHeight( float h ){
			return( (int)((h/height)* (maxY-minY)));
		}	
		
		public void
		mouseDown(
			MouseEvent	event )
		{
	        if(event.button == 1) mouseLeftDown = true;
	        if(event.button == 3) mouseRightDown = true;
	        xDown = event.x;
	        yDown = event.y;
	        saveMinX = minX;
	        saveMaxX = maxX;
	        saveMinY = minY;
	        saveMaxY = maxY;
	        saveRotation = rotation;
		}
		
		public void
		mouseUp(
			MouseEvent	event )
		{
			if(event.button == 1) mouseLeftDown = false;
	        if(event.button == 3) mouseRightDown = false;
		}
		public void
		mouseWheel(
			Event		event )
		{
			saveMinX = minX;
			saveMaxX = maxX;
			saveMinY = minY;
			saveMaxY = maxY;

			int deltaY = event.count * -5;
			// scaleFactor>1 means zoom in, this happens when
			// deltaY<0 which happens when the mouse is moved up.
			float scaleFactor = 1 - (float) deltaY / 300;
			if(scaleFactor <= 0) scaleFactor = 0.01f;

			// Scalefactor of e.g. 3 makes elements 3 times larger
			float moveFactor = 1 - 1/scaleFactor;

			Control control = ((Control) event.widget);
			Point controlSize = control.getSize();
			// event.x, event.y are relative to control
			float mouseXpct = (event.x + 1) / (float) controlSize.x;
			float mouseYpct = (event.y + 1) / (float) controlSize.y;
			float xOfs = (mouseXpct - 0.5f) * (saveMaxX - saveMinX);
			float yOfs = (mouseYpct - 0.5f) * (saveMaxY - saveMinY);

			float centerX = ((saveMinX + saveMaxX)/2) + xOfs;
			minX = saveMinX + moveFactor * (centerX - saveMinX);
			maxX = saveMaxX - moveFactor * (saveMaxX - centerX);

			float centerY = (saveMinY + saveMaxY)/2 + yOfs;
			minY = saveMinY + moveFactor * (centerY - saveMinY);
			maxY = saveMaxY - moveFactor * (saveMaxY - centerY);

			System.out.println( "wheel->" + minX + ", " + minY + ", " + maxX + ", " + maxY );
			
			disableAutoScale = true;
		}
		
		public boolean
		mouseMove(
			MouseEvent		event )
		{
	        if(mouseLeftDown && (event.stateMask & SWT.MOD4) == 0) {
	            int deltaX = event.x - xDown;
	            int deltaY = event.y - yDown;
	            float ratioX = (saveMaxX - saveMinX) / width;
	            float ratioY = (saveMaxY - saveMinY) / height;
	            float realDeltaX = deltaX * ratioX;
	            float realDeltaY  = deltaY * ratioY;
	            minX = saveMinX - realDeltaX;
	            maxX = saveMaxX - realDeltaX;
	            minY = saveMinY - realDeltaY;
	            maxY = saveMaxY - realDeltaY;
	            disableAutoScale = true;
	            return( true );
	          }
	          if(mouseRightDown || (mouseLeftDown && (event.stateMask & SWT.MOD4) > 0)) {
	            int deltaX = event.x - xDown;
	  	        int deltaY = event.y - yDown;
	  	        int diffX = Math.abs(deltaX);
	  	        int diffY = Math.abs(deltaY);
	  	        // Don't start rotating until a few px movement.  Helps when
	  	        // user just wants to zoom (move up/down) or rotate (move left/right) 
	  	        // and doesn't have steady hand
	            if (diffY > diffX && diffX <= 3) {
	            	deltaX = 0;
	            }
	  	        if (diffY > diffX && diffY <= 3) {
	  		        deltaY = 0;
	  	        }
	            rotation = saveRotation - (float) deltaX / 100;

	            // scaleFactor>1 means zoom in, this happens when
	            // deltaY<0 which happens when the mouse is moved up.
	            float scaleFactor = 1 - (float) deltaY / 300;
	            if(scaleFactor <= 0) scaleFactor = 0.01f;

	            // Scalefactor of e.g. 3 makes elements 3 times larger
	            float moveFactor = 1 - 1/scaleFactor;

	            Control control = ((Control) event.widget);
				Point controlSize = control.getSize();
	  	        // event.x, event.y are relative to control
	  	        float mouseXpct = (xDown + 1) / (float) controlSize.x;
	  	        float mouseYpct = (yDown + 1) / (float) controlSize.y;
	  	        float xOfs = (mouseXpct - 0.5f) * (saveMaxX - saveMinX);
	  	        float yOfs = (mouseYpct - 0.5f) * (saveMaxY - saveMinY);

	  	        float centerX = (saveMinX + saveMaxX)/2 + xOfs;
	            minX = saveMinX + moveFactor * (centerX - saveMinX);
	            maxX = saveMaxX - moveFactor * (saveMaxX - centerX);

	            float centerY = (saveMinY + saveMaxY)/2 + yOfs;
	            minY = saveMinY + moveFactor * (centerY - saveMinY);
	            maxY = saveMaxY - moveFactor * (saveMaxY - centerY);
	            disableAutoScale = true;
	            return( true );
	          }
	          return( false );
		}
	}
}
