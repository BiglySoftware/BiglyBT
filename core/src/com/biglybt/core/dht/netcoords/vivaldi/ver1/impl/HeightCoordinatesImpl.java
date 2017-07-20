/*
 * Created on 22 juin 2005
 * Created by Olivier Chalouhi
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
package com.biglybt.core.dht.netcoords.vivaldi.ver1.impl;

import com.biglybt.core.dht.netcoords.vivaldi.ver1.Coordinates;

public class HeightCoordinatesImpl implements Coordinates {

  protected final float x;

  protected final float y;

  protected final float h;

  public HeightCoordinatesImpl(float x, float y, float h) {
    this.x = x;
    this.y = y;
    this.h = h;
  }

  public HeightCoordinatesImpl(HeightCoordinatesImpl copy) {
    this.x = copy.x;
    this.y = copy.y;
    this.h = copy.h;
  }

  @Override
  public Coordinates add(Coordinates other) {
    HeightCoordinatesImpl o = (HeightCoordinatesImpl) other;
    return new HeightCoordinatesImpl(x+o.x,y+o.y,Math.abs(h+o.h));
  }

  @Override
  public Coordinates sub(Coordinates other) {
    HeightCoordinatesImpl o = (HeightCoordinatesImpl) other;
    return new HeightCoordinatesImpl(x-o.x,y-o.y,Math.abs(h+o.h));
  }

  @Override
  public Coordinates scale(float scale) {
    return new HeightCoordinatesImpl(scale * x,scale * y ,scale * h);
  }

  @Override
  public float measure() {
    return (float) (Math.sqrt(x * x + y * y) + h);
  }

  @Override
  public boolean
  atOrigin()
  {
	  return( x==0&&y==0);
  }

  @Override
  public boolean
  isValid()
  {
	 return( valid(x) && valid(y) && valid(h) && Math.abs(x) <= MAX_X && Math.abs(y) <= MAX_Y && Math.abs(h) <= MAX_H);
  }

  private boolean
  valid(
	float	f )
  {
	  return( !(Float.isInfinite( f ) || Float.isNaN( f )));
  }

  @Override
  public float distance(Coordinates other) {
    return this.sub(other).measure();
  }

  @Override
  public Coordinates unity() {
    float measure = this.measure();
    if(measure == 0) {
      //Special Vivaldi Case, when u(0) = random unity vector
      float x = (float)Math.random();
      float y = (float)Math.random();
      float h = (float)Math.random();
      return new HeightCoordinatesImpl(x,y,h).unity();
    }
    return this.scale(1/measure);
  }

  @Override
  public double[] getCoordinates() {
	  return( new double[]{ x, y } );
  }

  public String toString() {
    return (int)x + "," + (int)y + "," + (int)h;
  }

  /**
   * @return Returns the h.
   */
  public float getH() {
    return h;
  }


  /**
   * @return Returns the x.
   */
  public float getX() {
    return x;
  }


  /**
   * @return Returns the y.
   */
  public float getY() {
    return y;
  }

  public boolean equals(Object arg0) {
   if(arg0 instanceof HeightCoordinatesImpl) {
     HeightCoordinatesImpl other = (HeightCoordinatesImpl) arg0;
     if(other.x != x || other.y != y || other.h != h) return false;
     return true;
   }
   return false;
  }


}
