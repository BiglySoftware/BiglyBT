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
		int width;
		int height;
		float minX;
		float maxX;
		float minY;
		float maxY;
		double rotation;
		
		float saveMinX;
		float saveMaxX;
		float saveMinY;
		float saveMaxY;
		double saveRotation;

		boolean  	disableAutoScale 	= false;
		long		lastAutoScale		= 0;

		{
			reset();
		}
		
		public Scale
		clone()
		{
			try{
				return((Scale)super.clone());
			}catch( CloneNotSupportedException e ){
				return( this );
			}
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
	}
}
