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
package com.biglybt.core.dht.netcoords.vivaldi.ver1;

public interface Coordinates {

  public static final float MAX_X = 30000f;
  public static final float MAX_Y = 30000f;
  public static final float MAX_H = 30000f;

  public Coordinates add(Coordinates other);

  public Coordinates sub(Coordinates other);

  public Coordinates scale(float scale);

  public float measure();

  public float distance(Coordinates other);

  public Coordinates unity();

  public double[]
  getCoordinates();

  public boolean
  atOrigin();

  public boolean
  isValid();
}
